# Copyright International Business Machines Corp, 2025
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import boto3
import time
import os
import threading
import random
import botocore
from datetime import datetime
from typing import Dict, List, Any, Optional
from botocore.exceptions import ClientError
from botocore.config import Config
import logging
import json
import base64
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
import multiprocessing
from contextlib import contextmanager
from db_manager import db_manager
from config_manager import config_manager
from template_manager import TemplateManager
from collections import defaultdict

logger = logging.getLogger(__name__)

class AWSClient:
    def __init__(self):
        try:
            logger.debug("Initializing AWSClient...")
            # Get region using the config manager instance
            self.region = config_manager.get_region()
            logger.debug(f"Using AWS region: {self.region}")
            
            # Credential caching
            self.credentials = None
            self.credentials_expiry = None
            self.credentials_lock = threading.RLock()
            logger.debug("Credential caching initialized")
            
            # Get and cache credentials
            self._refresh_credentials()
            
            # Create session with cached credentials
            if self.credentials:
                logger.debug(f"Creating session with credentials - Access Key: {self.credentials.get('aws_access_key_id')[:8]}")
                self.session = boto3.Session(
                    region_name=self.region,
                    aws_access_key_id=self.credentials.get('aws_access_key_id'),
                    aws_secret_access_key=self.credentials.get('aws_secret_access_key'),
                    aws_session_token=self.credentials.get('aws_session_token')
                )
                logger.debug("Session created with explicit credentials")
            else:
                logger.debug("Creating session without explicit credentials (using IAM role)")
                self.session = boto3.Session(region_name=self.region)
                logger.debug("Session created using IAM role")
            
            # Validate credentials only if not using IAM role
            if self.credentials and not config_manager.validate_aws_credentials(self.credentials):
                logger.error("AWS credentials validation failed")
                raise ValueError("AWS credentials validation failed")
            else:
                logger.debug("AWS credentials validation passed")
            
            # Configure retry strategy
            self.config = Config(
                retries={
                    'max_attempts': 10,  # Total attempts
                    'mode': 'adaptive'  # Adaptive retry mode
                }
            )
            
            # Create clients from the session
            self.ec2 = self.session.client('ec2', config=self.config)
            self.ec2_resource = self.session.resource('ec2', config=self.config)
            logger.debug("EC2 client and resource created")
            
            # Handle custom endpoint if configured
            endpoint_url = config_manager.get_aws_endpoint_url()
            if endpoint_url:
                logger.debug(f"Using custom endpoint URL: {endpoint_url}")
                self.ec2 = self.session.client('ec2', endpoint_url=endpoint_url)
                self.ec2_resource = self.session.resource('ec2', endpoint_url=endpoint_url)
                logger.debug("EC2 clients reconfigured with custom endpoint")
                
            # Get AWS key file configuration
            self.aws_key_file = config_manager.get_aws_key_file()
            logger.debug(f"AWS_KEY_FILE configured: {self.aws_key_file}")
            
            # Get spot instance termination reclaim configuration
            self.spot_terminate_on_reclaim = config_manager.get_spot_terminate_on_reclaim()
            logger.debug(f"AWS_SPOT_TERMINATE_ON_RECLAIM configured: {self.spot_terminate_on_reclaim}")
            if self.spot_terminate_on_reclaim:
                self.start_spot_reclaim_monitor()
        
            # Get InstanceID tagging configuration
            self.instance_id_tag_enabled = config_manager.get_instance_id_tag()
            logger.debug(f"AWS_TAG_InstanceID enabled: {self.instance_id_tag_enabled}")
            
            self._test_connection()
            logger.debug("AWS connection test completed successfully")
            
            self.vm_pool = None
            self.min_vm_workers = int(os.getenv('AWS_MIN_WORKERS', '10'))
            self.max_vm_workers = int(os.getenv('AWS_MAX_WORKERS', '200'))
            self.batch_size = int(os.getenv('AWS_BATCH_SIZE', '200'))
            logger.debug(f"AWSClient initialized with min_workers={self.min_vm_workers}, max_workers={self.max_vm_workers}")
            
            self.cleanup_interval = int(os.getenv('CLEANUP_INTERVAL_MINUTES', '30')) * 60  # Convert to seconds
            self.max_request_age = int(os.getenv('MAX_REQUEST_AGE_MINUTES', '60'))
            self.last_cleanup = 0
            logger.debug(f"Cleanup configured: interval={self.cleanup_interval}s, max_age={self.max_request_age} minutes")
    
        except Exception as e:
            logger.error(f"AWSClient initialization failed: {e}")
            logger.debug(f"AWSClient initialization stack trace:", exc_info=True)
            raise

    def _get_cached_credentials(self) -> Dict[str, str]:
        """Get cached credentials or fetch new ones if expired"""
        logger.debug("Checking cached credentials...")
        with self.credentials_lock:
            current_time = time.time()
            logger.debug(f"Current time: {current_time}, credentials_expiry: {self.credentials_expiry}")
            
            # Return cached credentials if still valid (with 5-minute buffer)
            if (self.credentials and self.credentials_expiry and 
                current_time < self.credentials_expiry - 300):
                logger.debug("Using cached credentials")
                return self.credentials.copy()
            
            # Fetch new credentials
            logger.debug("Credentials expired or not available, refreshing...")
            return self._refresh_credentials()
    
    def _refresh_credentials(self) -> Dict[str, str]:
        """Refresh credentials and update cache"""
        logger.debug("Refreshing AWS credentials...")
        with self.credentials_lock:
            credentials = config_manager.get_aws_credentials()
            logger.debug(f"Retrieved credentials - has_access_key: {'aws_access_key_id' in credentials}, has_secret: {'aws_secret_access_key' in credentials}")
            
            # Extract expiration from credentials if available
            if credentials and 'Expiration' in credentials:
                self.credentials_expiry = credentials['Expiration']
                logger.debug(f"Credentials expire at: {self.credentials_expiry}")
            else:
                # Default to 1 hour for file-based or IAM credentials
                self.credentials_expiry = time.time() + 3600
                logger.debug(f"Set default credentials expiry: {self.credentials_expiry}")
            
            self.credentials = credentials
            logger.debug("AWS credentials refreshed successfully")
            return credentials.copy()
    
    def _refresh_credentials_if_needed(self):
        logger.debug("Checking if credentials need refresh...")
        if not self.credentials_expiry:
            logger.debug("No credentials expiry set, skipping refresh")
            return
        
        current_time = time.time()
        logger.debug(f"Current time: {current_time}, expiry: {self.credentials_expiry}, buffer: 300s")
        if current_time >= self.credentials_expiry - 300:
            logger.debug("Credentials need refresh, refreshing...")
            with self.credentials_lock:
                if current_time >= self.credentials_expiry - 300:
                    old_credentials = self.credentials
                    self._refresh_credentials()
                    
                    # Recreate session if credentials changed
                    if old_credentials != self.credentials:
                        logger.debug("Credentials changed, recreating clients...")
                        self._recreate_clients()
                    else:
                        logger.debug("Credentials unchanged, keeping existing clients")
                else:
                    logger.debug("Credentials already refreshed by another thread")
        else:
            logger.debug("Credentials still valid, no refresh needed")

    def _recreate_clients(self):
        """Recreate clients with new credentials"""
        logger.debug("Recreating AWS clients with new credentials...")
        if self.credentials:
            self.session = boto3.Session(
                region_name=self.region,
                aws_access_key_id=self.credentials.get('aws_access_key_id'),
                aws_secret_access_key=self.credentials.get('aws_secret_access_key'),
                aws_session_token=self.credentials.get('aws_session_token')
            )
            logger.debug("New session created with credentials")
        else:
            self.session = boto3.Session(region_name=self.region)
            logger.debug("New session created with IAM role")
        
        # Recreate clients
        endpoint_url = config_manager.get_aws_endpoint_url()
        if endpoint_url:
            self.ec2 = self.session.client('ec2', endpoint_url=endpoint_url)
            self.ec2_resource = self.session.resource('ec2', endpoint_url=endpoint_url)
            logger.debug(f"Clients recreated with custom endpoint: {endpoint_url}")
        else:
            self.ec2 = self.session.client('ec2')
            self.ec2_resource = self.session.resource('ec2')
            logger.debug("Clients recreated with default endpoint")

    def _test_connection(self):
        """Test AWS connection by making a simple API call"""
        logger.debug("Testing AWS connection...")
        try:
            # Check credentials first
            self._refresh_credentials_if_needed()
            
            # Try to describe regions to test connectivity
            logger.debug("Calling describe_regions to test connection...")
            regions = self.ec2.describe_regions()
            region_count = len(regions['Regions'])
            logger.debug(f"AWS connection test successful - found {region_count} regions")
        except Exception as e:
            logger.error(f"AWS connection test failed: {e}")
            logger.debug(f"Connection test stack trace:", exc_info=True)
            raise

    def _init_vm_pool(self):
        """Initialize thread pool for VM operations"""
        if self.vm_pool is None:
            try:
                cpu_count = multiprocessing.cpu_count()
                workers = max(self.min_vm_workers, min(cpu_count, self.max_vm_workers))
                
                self.vm_pool = ThreadPoolExecutor(
                    max_workers=workers,
                    thread_name_prefix='aws_vm_'
                )
                logger.debug(f"Initialized VM thread pool with {workers} workers and CPU count: {cpu_count}")
                
            except Exception as e:
                logger.error(f"Failed to initialize VM pool: {str(e)}")
                logger.debug(f"VM pool initialization stack trace:", exc_info=True)
                # Fallback to sequential execution
                logger.debug("Falling back to sequential execution due to VM pool initialization failure")
                
    @contextmanager
    def resource_context(self):
        """Context manager for resource cleanup"""
        logger.debug("Entering AWSClient resource context")
        try:
            yield self
        finally:
            logger.debug("Exiting AWSClient resource context, cleaning up...")
            self.cleanup()
            
    def cleanup(self):
        """Clean up thread pool resources"""
        logger.debug("Starting AWSClient cleanup...")
        if self.vm_pool:
            try:
                logger.debug("Shutting down VM thread pool")
                self.vm_pool.shutdown(wait=True)
                self.vm_pool = None
                logger.debug("VM thread pool shutdown complete")
            except Exception as e:
                logger.error(f"Error shutting down VM thread pool: {str(e)}")
                logger.debug(f"Thread pool shutdown stack trace:", exc_info=True)
        else:
            logger.debug("No VM thread pool to clean up")
            
    def _format_error_message(self, context: str, error: Exception) -> str:
        """Format error message with context and AWS error code"""
        # Extract AWS error code from ClientError
        error_code = "UnknownError"
        if hasattr(error, 'response') and 'Error' in getattr(error, 'response', {}):
            error_code = error.response['Error']['Code']
        
        return f"{context}. Error Code: {error_code}"

    def request_machines(self, template: Dict, count: int, rc_account: str = 'default') -> str:
        """Create EC2 instances using multithreading"""
        logger.debug(f"Starting request_machines for template {template.get('templateId')}, count: {count}")
        # Check credentials once at the beginning of bulk operation
        self._refresh_credentials_if_needed()
        
        try:
            # Check for Spot Fleet configuration (template-based)
            if template.get('fleetRole'):
                logger.info(f"Using Spot Fleet for template {template.get('templateId')}")
                logger.debug(f"Spot Fleet configuration found: fleetRole={template.get('fleetRole')}")
                result = self._create_spot_fleet(template, count, rc_account)
            
            # Check for EC2 Fleet configuration - simple boolean check
            elif template.get('ec2FleetConfig'):
                logger.info(f"Using EC2 Fleet for template {template.get('templateId')}")
                logger.debug(f"EC2 Fleet configuration found: {template.get('ec2FleetConfig')}")
                result = self._create_ec2_fleet(template, count, rc_account)
            
            # Basic template or launch template
            else:
                logger.info(f"Using Basic configuration for template {template.get('templateId')}")
                logger.debug("Using basic instance creation method")
                result = self._create_instances(template, count, rc_account)
            
            logger.info(f"Result: {result}")
            logger.debug(f"Creation result details: success={result.get('success')}, request_id={result.get('request_id')}")
            
            # Common result processing
            if result and result['success']:
                request_id = result['request_id']
                logger.info(f"Request {request_id}: Creating {count} instances/slots")
                
                # FIX: Check if there's a warning in the result
                if 'warning' in result:
                    logger.warning(f"Request {request_id} completed with warning: {result['warning']}")
                
                return request_id
            else:
                if result is None:
                    error_msg = 'Failed to create instances. Error Code: InternalError'
                    logger.debug("Creation method returned None result")
                else:
                    # Extract error from failed_instances if available
                    if result.get('failed_instances'):
                        # Get the first error from failed instances
                        first_error = result['failed_instances'][0].get('error', 'Unknown error')
                        error_msg = f"Failed to create instances. {first_error}"
                    else:
                        error_msg = result.get('error', 'Failed to create instances. Error Code: UnknownError')
                    logger.debug(f"Creation failed with error: {error_msg}")
                
                # FIX: Don't wrap this in Exception - just raise the string directly
                # This prevents it from being caught by the general Exception handler below
                raise Exception(error_msg)
                
        except ClientError as e:
            error_msg = self._format_error_message("Failed to create instances on AWS", e)
            logger.error(f"AWS API error: {error_msg}")
            logger.debug(f"AWS ClientError details:", exc_info=True)
            raise Exception(error_msg)
            
        except Exception as e:
            # Only handle truly unexpected exceptions here
            # If the error message already contains an AWS error code, don't reformat it
            error_str = str(e)
            if "Error Code:" in error_str:
                # This is already a formatted error with AWS code, just re-raise it
                logger.debug(f"Creation failed with AWS error: {error_str}")
                raise Exception(error_str)
            else:
                # This is an unexpected error, format it
                error_msg = self._format_error_message("Unexpected error while creating instances", e)
                logger.error(f"Unexpected error: {error_msg}")
                logger.debug(f"Unexpected error details:", exc_info=True)
                raise Exception(error_msg)
 
    def _create_instances(self, template: Dict, count: int, rc_account: str = 'default') -> Dict[str, Any]:
        """Create instances with batching for large counts"""
        logger.debug(f"Starting _create_instances for {count} instances")
        
        # AWS has a limit of 1000 instances per run_instances API call
        batches_needed = (count + self.batch_size - 1) // self.batch_size  # Ceiling division
        logger.debug(f"Will create {count} instances in {batches_needed} batch(es) of up to {self.batch_size} each")
        
        # This is the only request where we need to create a request id
        request_id = f"dir-{os.getpid()}-{int(time.time())}"
        logger.debug(f"Starting instance creation request {request_id} for {count} instances")
        
        # Create request in database first
        db_manager.create_request(
            request_id=request_id,
            template_id=template['templateId'],
            host_allocation_type="direct",
            rc_account=rc_account
        )
        logger.debug(f"Created request {request_id} in database")
        
        instance_ids = []
        failed_instances = []
        
        # Collect all machine data for batch addition
        all_machines_data = []
        
        for batch_num in range(batches_needed):
            batch_start_idx = batch_num * self.batch_size
            batch_remaining = count - batch_start_idx
            batch_size = min(self.batch_size, batch_remaining)
            
            logger.info(f"Processing batch {batch_num + 1}/{batches_needed}: {batch_size} instances")
            
            # Build the base parameters for this batch
            # Set MinCount=1 instead of batch_size to allow partial fulfillment
            instances_params = {
                'MinCount': 1,
                'MaxCount': batch_size
            }
            logger.debug(f"Batch {batch_num + 1}: MinCount=1, MaxCount={batch_size}")
            
            # Add launch template OR individual parameters
            launch_template_id = template.get('launchTemplateId')
            if launch_template_id:
                instances_params['LaunchTemplate'] = {
                    'LaunchTemplateId': launch_template_id,
                    'Version': template.get('launchTemplateVersion', '$Default')
                }
                logger.debug(f"Batch {batch_num + 1}: Using launch template: {launch_template_id}")
            else:
                instances_params['ImageId'] = template['imageId']
                logger.debug(f"Batch {batch_num + 1}: Using ImageId: {template['imageId']}")
            
            # Use helper functions for common parameters
            network_interfaces = self._build_network_interfaces(template)
            user_data = self._build_user_data(template, rc_account)
            instance_tags = self._build_instance_tags(template, rc_account)
            
            # Handle multiple VM types for direct instance creation (not using launch template)
            vm_type = template.get('vmType')
            selected_vm_type = None
            if vm_type and ',' in vm_type and not launch_template_id:
                # Multiple VM types available - choose one for this batch
                # Random selection ensures distribution across batches
                vm_types = [v.strip() for v in vm_type.split(',') if v.strip()]
                if vm_types:
                    selected_vm_type = random.choice(vm_types)
                    logger.debug(f"Batch {batch_num + 1}: Multiple VM types available: {vm_types}, chosen: {selected_vm_type}")
                else:
                    logger.warning(f"Batch {batch_num + 1}: No valid VM types found in vmType string")
            elif vm_type:
                # Single VM type
                selected_vm_type = vm_type
                logger.debug(f"Batch {batch_num + 1}: Single VM type: {selected_vm_type}")
            
            # Build IAM instance profile
            iam_profile = {}
            instance_profile = template.get('instanceProfile')
            if instance_profile:
                iam_profile = {
                    'Arn' if instance_profile.startswith('arn:aws:iam:') else 'Name': instance_profile
                }
                logger.debug(f"Batch {batch_num + 1}: Attaching IAM instance profile: {instance_profile}")
                
            # Build placement
            placement = {}
            placement_group = template.get('placementGroupName')
            if placement_group:
                placement['GroupName'] = placement_group
                logger.debug(f"Batch {batch_num + 1}: Using placement group: {placement_group}")
                
            tenancy = template.get('tenancy')
            if tenancy and tenancy in ['default', 'dedicated']:
                placement['Tenancy'] = tenancy
                logger.debug(f"Batch {batch_num + 1}: Using tenancy: {tenancy}")
            
            # Build market options
            market_options = {}
            spot_price = template.get('spotPrice')
            if spot_price:
                market_options = {
                    'MarketType': 'spot',
                    'SpotOptions': {
                        'SpotInstanceType': 'one-time',
                        'InstanceInterruptionBehavior': 'terminate',
                        'MaxPrice': str(spot_price)
                    }
                }
                logger.debug(f"Batch {batch_num + 1}: Using spot instance with max price: {spot_price}")

            # Add optional parameters only if they have values
            if network_interfaces:
                # For batch creation, AWS will create network interfaces for each instance
                # using the same configuration
                instances_params['NetworkInterfaces'] = network_interfaces
                logger.debug(f"Batch {batch_num + 1}: Using NetworkInterfaces parameter")
            else:
                # Fallback to individual network parameters
                security_groups = template.get('securityGroupIds')
                if security_groups:
                    instances_params['SecurityGroupIds'] = security_groups
                    logger.debug(f"Batch {batch_num + 1}: Using SecurityGroupIds: {security_groups}")
                
                subnet_id = template.get('subnetId')
                if subnet_id:
                    # For multiple subnets, choose the one with max capacity
                    if ',' in subnet_id:
                        subnets = [s.strip() for s in subnet_id.split(',') if s.strip()]
                        if subnets:
                            try:
                                # Get capacity for all subnets
                                response = self.ec2.describe_subnets(SubnetIds=subnets)

                                # Find subnet with maximum available IPs
                                best_subnet = None
                                max_capacity = 0
                                for subnet in response['Subnets']:
                                    capacity = subnet['AvailableIpAddressCount']
                                    if capacity > max_capacity:
                                        max_capacity = capacity
                                        best_subnet = subnet['SubnetId']

                                chosen_subnet = best_subnet or random.choice(subnets)
                                instances_params['SubnetId'] = chosen_subnet
                                logger.debug(f"Batch {batch_num + 1}: Multiple subnets available. Chose {chosen_subnet} with {max_capacity} IPs")

                            except Exception as e:
                                # Fallback to random if capacity check fails
                                logger.warning(f"Batch {batch_num + 1}: Failed to check subnet capacity: {e}")
                                chosen_subnet = random.choice(subnets)
                                instances_params['SubnetId'] = chosen_subnet
                                logger.debug(f"Batch {batch_num + 1}: Fallback to random subnet: {chosen_subnet}")
                        else:
                            logger.warning(f"Batch {batch_num + 1}: No valid subnets found in subnetId string")
                    else:
                        # Single subnet
                        instances_params['SubnetId'] = subnet_id
                        logger.debug(f"Batch {batch_num + 1}: Using SubnetId: {subnet_id}")
                            
            key_name = self._get_key_name(template)
            if key_name:
                instances_params['KeyName'] = template['keyName']
                logger.debug(f"Batch {batch_num + 1}: Using key pair: {template['keyName']}")
            
            # Use selected VM type (could be from multiple choices or single)
            if selected_vm_type:
                instances_params['InstanceType'] = selected_vm_type
                logger.debug(f"Batch {batch_num + 1}: Using Instance Type: {selected_vm_type}")
                
            if template.get('ebsOptimized'):
                instances_params['EbsOptimized'] = template['ebsOptimized']
                logger.debug(f"Batch {batch_num + 1}: Setting EBS optimized to: {template['ebsOptimized']}")
                
            if user_data:
                instances_params['UserData'] = user_data
                logger.debug(f"Batch {batch_num + 1}: Using User Data")    
                
            if instance_tags:
                instances_params['TagSpecifications'] = [
                    {
                        'ResourceType': 'instance',
                        'Tags': instance_tags
                    },
                    {
                        'ResourceType': 'volume',
                        'Tags': instance_tags
                    }
                ]
                logger.debug(f"Batch {batch_num + 1}: Added {len(instance_tags)} tags to TagSpecifications")

            if market_options:
                instances_params['InstanceMarketOptions'] = market_options
                logger.debug(f"Batch {batch_num + 1}: Added InstanceMarketOptions")

            if placement:
                instances_params['Placement'] = placement
                logger.debug(f"Batch {batch_num + 1}: Added Placement configuration")

            if iam_profile:
                instances_params['IamInstanceProfile'] = iam_profile
                logger.debug(f"Batch {batch_num + 1}: Added IAM instance profile")

            logger.debug(f"Creating Instance with config: {instances_params}")
            logger.debug(f"Batch {batch_num + 1}: About to call run_instances for {batch_size} instances")
            
            try:
                # Single API call for this batch
                # AWS will create between MinCount (1) and MaxCount (batch_size) instances
                # based on available resources (IP addresses, capacity, etc.)
                response = self.ec2.run_instances(**instances_params)
                instances = response['Instances']
                batch_instance_ids = [instance.get('InstanceId') for instance in instances]
                
                # Log how many instances were actually created
                actual_count = len(batch_instance_ids)
                logger.info(f"Batch {batch_num + 1}: Successfully created {actual_count} out of requested {batch_size}")
                logger.debug(f"Batch {batch_num + 1}: Successfully created instances: {batch_instance_ids}")
                
                # Check if we got fewer instances than requested (due to resource constraints)
                if actual_count < batch_size:
                    logger.warning(f"Batch {batch_num + 1}: Created only {actual_count} out of {batch_size} requested instances due to resource constraints.")
                
                # Collect machine data for this batch - don't update DB yet
                for instance in instances:
                    instance_id = instance.get('InstanceId')
                    # Use helper for machine data creation
                    machine_data = self._create_machine_data(
                        instance_id=instance_id,
                        template=template,
                        request_id=request_id,
                        rc_account=rc_account,
                        name=instance.get('PrivateDnsName'),
                        private_ip=instance.get('PrivateIpAddress', ''),
                        public_ip=instance.get('PublicIpAddress', ''),
                        public_dns=instance.get('PublicDnsName', '')
                    )
                    all_machines_data.append(machine_data)
                    logger.debug(f"Added machine {instance_id} to database")
                
                instance_ids.extend(batch_instance_ids)
                time.sleep(1)
                
            except ClientError as e:
                # Extract AWS error code for better error reporting
                error_code = e.response['Error']['Code'] if hasattr(e, 'response') else 'UnknownError'
                error_msg = self._format_error_message(f"Failed to launch EC2 instances in batch {batch_num + 1}", e)
                logger.error(f"Batch {batch_num + 1} creation failed: {error_msg}")
                failed_instances.append({
                    'error': error_msg,
                    'aws_error_code': error_code,
                    'batch_index': batch_num,
                    'batch_size': batch_size,
                    'instances_failed': batch_size
                })
                # Continue with next batch even if this one fails
                continue
                
            except Exception as e:
                error_msg = self._format_error_message(f"Unexpected error launching instances in batch {batch_num + 1}", e)
                logger.error(f"Batch {batch_num + 1} unexpected error: {error_msg}")
                failed_instances.append({
                    'error': error_msg,
                    'aws_error_code': 'InternalError',
                    'batch_index': batch_num,
                    'batch_size': batch_size,
                    'instances_failed': batch_size
                })
                # Continue with next batch even if this one fails
                continue
        
        # BATCH ADD: Add all machines to database in one operation
        if all_machines_data:
            result = db_manager.add_machines_to_request(request_id, all_machines_data)
            if result['success_count'] > 0:
                logger.info(f"Batch added {result['success_count']} machines to database for request {request_id}")
            if result['failed_count'] > 0:
                logger.warning(f"Failed to add {result['failed_count']} machines to database for request {request_id}: {result.get('errors')}")
            
        logger.debug(f"_create_instances completed - successful: {len(instance_ids)}, failed batches: {len(failed_instances)}")
        result = {
            'success': len(instance_ids) > 0,
            'request_id': request_id,
            'instance_ids': instance_ids,
            'failed_instances': failed_instances
        }
        
        # If all instances failed, include a summary error
        if not result['success'] and failed_instances:
            first_error = failed_instances[0].get('error', 'All batch creations failed')
            result['error'] = f"All {count} instance creations failed across {batches_needed} batches. {first_error}"
            logger.debug(f"All instances failed: {result['error']}")
        
        return result

    def _build_user_data(self, template: Dict, rc_account: str = 'default') -> str:
        """Build user data from template - reusable across all instance types"""
        logger.debug("Building user data from template...")
        user_data = ""
        script_dir = os.path.dirname(os.path.abspath(__file__))
        user_data_file = os.path.join(script_dir, "user_data.sh")
        logger.debug(f"Looking for user data file: {user_data_file}")
        
        if os.path.exists(user_data_file):
            try:
                with open(user_data_file, "r") as f:
                    user_data = f.read().strip()
                    logger.debug(f"Read user data file, length: {len(user_data)} characters")
                    
                    if user_data:
                        # Build export commands from template userData if provided
                        exports = []
                        template_user_data = template.get('userData')
                        if template_user_data:
                            logger.debug(f"Processing template user data: {template_user_data}")
                            for key_eq_val in template_user_data.split(';'):
                                if key_eq_val.strip():
                                    exports.append(f"export {key_eq_val.strip()}")
                        
                        # Add template ID for identification
                        if template.get('templateId'):
                            exports.append(f"export template_id={template.get('templateId')}")
                            logger.debug(f"Added template_id export: {template.get('templateId')}")
                            
                        # Add providerName
                        provider_name = os.getenv('PROVIDER_NAME')
                        if provider_name:
                            exports.append(f"export providerName={provider_name}")
                            logger.debug(f"Added providerName export: {provider_name}")
                        else:
                            logger.warning("PROVIDER_NAME environment variable not set")
                            
                        # Add clusterName
                        script_options = os.getenv('SCRIPT_OPTIONS', '')
                        if 'clusterName=' in script_options:
                            try:
                                # Split and take only the part before any potential next parameter
                                cluster_name = script_options.split('clusterName=', 1)[1].split()[0]
                                exports.append(f"export clustername={cluster_name}")
                                logger.debug(f"Added clustername export: {cluster_name}")
                            except (IndexError, AttributeError):
                                cluster_name = None
                            
                        # Add rc_account 
                        if rc_account:
                            exports.append(f"export rc_account={rc_account}")
                            logger.debug(f"Added rc_account export: {rc_account}")
                        
                        # Combine and replace placeholder
                        if exports:
                            export_cmd = ";".join(exports) + ";"
                            user_data = user_data.replace("%EXPORT_USER_DATA%", export_cmd)
                            logger.debug(f"Replaced EXPORT_USER_DATA placeholder with {len(exports)} exports")
                        else:
                            # If no exports, remove the placeholder line
                            user_data = user_data.replace("%EXPORT_USER_DATA%", "")
                            logger.debug("Removed EXPORT_USER_DATA placeholder (no exports)")
                                            
            except Exception as e:
                logger.warning(f"Failed to read user data file {user_data_file}: {e}")
                logger.debug(f"User data file read error stack trace:", exc_info=True)
        else:
            logger.debug(f"User data file not found: {user_data_file}")
        
        logger.debug(f"Final user data length: {len(user_data)} characters")
        return user_data

    def _get_encoded_user_data(self, template: Dict, rc_account: str = 'default') -> str:
        """Get base64 encoded user data for fleet requests"""
        logger.debug("Getting base64 encoded user data...")
        user_data = self._build_user_data(template, rc_account)
        if user_data:
            encoded = base64.b64encode(user_data.encode('utf-8')).decode('utf-8')
            logger.debug(f"User data encoded, length: {len(encoded)}")
            return encoded
        logger.debug("No user data to encode")
        return ""
    
    def _build_instance_tags(self, template: Dict, rc_account: str = 'default') -> List[Dict]:
        """Build instance tags from template - reusable across all instance types"""
        logger.debug("Building instance tags from template...")
        instance_tags = []
        tags_string = template.get('instanceTags')
        
        if tags_string:
            logger.debug(f"Processing instance tags: {tags_string}")
            tag_pairs = [pair.strip() for pair in tags_string.split(',') if pair.strip()]
            logger.debug(f"Found {len(tag_pairs)} tag pairs")
            
            for pair in tag_pairs:
                if '=' in pair:
                    key, value = pair.split('=', 1)
                    key = key.strip()
                    value = value.strip()
                    
                    # Skip tags that start with 'aws:' as per AWS restrictions
                    if key.lower().startswith('aws:'):
                        logger.warning(f"Skipping reserved tag '{key}' - tags cannot start with 'aws:'")
                        continue
                        
                    instance_tags.append({'Key': key, 'Value': value})
                    logger.debug(f"Added tag: {key}={value}")
                else:
                    logger.warning(f"Invalid tag format '{pair}', expected 'Key=Value'")
        
        # Add RC_ACCOUNT tag
        rc_account_tag_exists = any(tag.get('Key') == 'RC_ACCOUNT' for tag in instance_tags)
        if not rc_account_tag_exists:
            instance_tags.append({'Key': 'RC_ACCOUNT', 'Value': rc_account})
            logger.debug(f"Added tag: RC_ACCOUNT={rc_account}")
        
        logger.debug(f"Built {len(instance_tags)} instance tags")
        return instance_tags
    
    def _tag_instance_with_instance_id(self, instance_id: str):
        """InstanceId tagging to ec2 instances and ebs volumes"""
            
        try:
            # Tag instance
            self.ec2.create_tags(
                Resources=[instance_id], 
                Tags=[{'Key': 'InstanceID', 'Value': instance_id}]
            )
            
            # Tag volumes
            response = self.ec2.describe_volumes(
                Filters=[{'Name': 'attachment.instance-id', 'Values': [instance_id]}]
            )
            
            volume_ids = [vol['VolumeId'] for vol in response.get('Volumes', [])]
            if volume_ids:
                self.ec2.create_tags(Resources=volume_ids, Tags=[{'Key': 'InstanceID', 'Value': instance_id}])
                
        except ClientError as e:
            logger.error(f"Tagging failed for {instance_id}: {e.response['Error']['Code']}")
        except Exception as e:
            logger.error(f"Unexpected tagging error for {instance_id}: {e}")
    
    def _build_network_config(self, template: Dict) -> Dict[str, Any]:
        """Build network configuration from template - reusable across all instance types"""
        logger.debug("Building network configuration...")
        
        subnet_id = template.get('subnetId')
        security_groups = template.get('securityGroupIds', [])
        
        # Early return if no subnet
        if not subnet_id:
            logger.debug("No subnet ID found in template")
            return {}
        
        # Parse and validate subnets
        subnets = [s.strip() for s in subnet_id.split(',') if s.strip()]
        if not subnets:
            logger.warning("No valid subnets found in subnetId string")
            return {}
        
        # If multiple subnets, choose the one with maximum available IPs
        if len(subnets) > 1:
            logger.debug(f"Multiple subnets available: {subnets}")
            try:
                # Get capacity for all subnets
                response = self.ec2.describe_subnets(SubnetIds=subnets)

                # Find subnet with maximum available IPs
                best_subnet = None
                max_capacity = 0
                for subnet in response['Subnets']:
                    capacity = subnet['AvailableIpAddressCount']
                    if capacity > max_capacity:
                        max_capacity = capacity
                        best_subnet = subnet['SubnetId']

                chosen_subnet = best_subnet or random.choice(subnets)
                logger.debug(f"Multiple subnets available. Chose {chosen_subnet} with {max_capacity} available IPs")

            except Exception as e:
                # Fallback to random if capacity check fails
                logger.warning(f"Failed to check subnet capacity: {e}")
                chosen_subnet = random.choice(subnets)
                logger.debug(f"Fallback to random subnet: {chosen_subnet}")
        else:
            chosen_subnet = subnets[0]
            logger.debug(f"Single subnet config: SubnetId={chosen_subnet}, Groups={security_groups}")
        
        return {
            'SubnetId': chosen_subnet,
            'Groups': security_groups
        }

    def _build_network_interfaces(self, template: Dict) -> List[Dict]:
        """Build network interfaces configuration - for direct instance creation"""
        logger.debug("Building network interfaces...")
        network_interfaces = []
        network_config = self._build_network_config(template)
        
        if network_config.get('SubnetId'):
            interface_config = {
                'DeviceIndex': 0,
                'SubnetId': network_config['SubnetId'],
                'Groups': network_config['Groups']
            }
            
            if template.get('interfaceType', '').lower() == 'efa':
                interface_config['InterfaceType'] = 'efa'
                logger.debug("Configuring EFA interface")
                
            network_interfaces.append(interface_config)
            logger.debug(f"Built network interface: {interface_config}")
        else:
            logger.debug("No subnet ID available for network interface")
        
        return network_interfaces

    def _get_key_name(self, template: Dict) -> Optional[str]:
        """Get key name to use - template takes precedence, then AWS_KEY_FILE"""
        # First check if template specifies a keyName
        if template.get('keyName'):
            return template['keyName']
        
        # Then check if AWS_KEY_FILE is configured
        if self.aws_key_file:
            return self._get_key_name_from_file(self.aws_key_file)
        
        # No key specified
        return None

    def _get_key_name_from_file(self, key_file_path: str) -> str:
        """Extract key name from key file path"""
        try:
            # Common pattern: keyname.pem -> keyname
            base_name = os.path.basename(key_file_path)
            key_name = os.path.splitext(base_name)[0]
            logger.debug(f"Extracted key name '{key_name}' from file: {key_file_path}")
            return key_name
        except Exception as e:
            logger.warning(f"Failed to extract key name from {key_file_path}: {e}. Using default.")
            return "lsf-key"
        
    def _get_common_instance_params(self, template: Dict) -> Dict[str, Any]:
        """Get common instance parameters used across all creation methods"""
        logger.debug("Getting common instance parameters...")
        attributes = template.get('attributes', {})
        
        params = {
            'ncores': int(attributes.get('ncores', ['Numeric', '1'])[1]),
            'nthreads': int(attributes.get('ncpus', ['Numeric', '1'])[1]),
            'template_id': template.get('templateId', 'unknown')
        }
        logger.debug(f"Common params: {params}")
        return params
        
    def _build_machine_data_template(self, template: Dict, request_id: str, rc_account: str = 'default') -> Dict[str, Any]:
        """Build base machine data template - reusable for all instance types"""
        logger.debug(f"Building machine data template for request {request_id}")
        common_params = self._get_common_instance_params(template)
        
        template_data = {
            "template": common_params['template_id'],
            "result": "executing",
            "status": "pending",
            "privateIpAddress": "",
            "publicIpAddress": "", 
            "publicDnsName": "",
            "ncores": common_params['ncores'],
            "nthreads": common_params['nthreads'],
            "rcAccount": rc_account,
            "lifeCycleType": "",
            "tagInstanceId": False,
            "reqId": request_id,
            "retId": "",
            "message": "Instance creation initiated",
            "launchtime": int(time.time())
        }
        logger.debug(f"Machine data template: {template_data}")
        return template_data

    def _create_machine_data(self, instance_id: str, template: Dict, request_id: str, 
                            rc_account: str = "default", name: str = None, private_ip: str = "", 
                            public_ip: str = "", public_dns: str = "") -> Dict[str, Any]:
        """Create complete machine data for database entry"""
        logger.debug(f"Creating machine data for instance {instance_id}")
        base_data = self._build_machine_data_template(template, request_id, rc_account)
        
        base_data.update({
            "machineId": instance_id,
            "name": name or f"host-{instance_id}",
            "privateIpAddress": private_ip,
            "publicIpAddress": public_ip,
            "publicDnsName": public_dns
        })        
        logger.debug(f"Complete machine data: {base_data}")
        return base_data

    def _create_spot_fleet(self, template: Dict, count: int, rc_account: str = 'default') -> Dict[str, Any]:
        """Create Spot Fleet using template parameters (not external config file)"""
        logger.debug(f"Creating Spot Fleet for {count} instances")
        try:
            logger.info(f"Creating Spot Fleet from template")
            
            # Validate required Spot Fleet parameters
            fleet_role = template.get('fleetRole')
            if not fleet_role:
                logger.error("fleetRole is required for Spot Fleet templates")
                raise ValueError("fleetRole is required for Spot Fleet templates")
            
            # Build Spot Fleet request using template parameters
            # Limitation: Only Type=request is supported for spot fleet for LSF
            spot_fleet_config = {
                'SpotFleetRequestConfig': {
                    'Type': 'request',
                    'TargetCapacity': count,
                    'IamFleetRole': fleet_role,
                    'AllocationStrategy': template.get('allocationStrategy', 'capacityOptimized'),
                    'LaunchSpecifications': self._build_spot_fleet_launch_specs(template, rc_account)
                }
            }
            logger.debug("Spot Fleet config structure built")
            
            # Add spot price if specified
            spot_price = template.get('spotPrice')
            if spot_price:
                spot_fleet_config['SpotFleetRequestConfig']['SpotPrice'] = str(spot_price)
                logger.debug(f"Added spot price: {spot_price}")
            
            logger.debug(f"Creating Spot Fleet with config: {spot_fleet_config}")
            response = self.ec2.request_spot_fleet(**spot_fleet_config)
            
            fleet_id = response.get('SpotFleetRequestId')
            logger.info(f"Spot Fleet created: {fleet_id}")
            
            # Create request in database first
            db_manager.create_request(
                request_id=fleet_id,
                template_id=template['templateId'],
                host_allocation_type="spotFleet",
                rc_account=rc_account
            )
            logger.debug(f"Created request {fleet_id} in database")
            
            # Ideally, templates with spotPrice > marketPrice will be chosen, so it should create the spot instances immidiately
            # but it takes time so no point of polling the instance data
            # self._poll_spot_fleet_instances(fleet_id)
            
            # Just return the fleet_id, i.e.,request_id and let the get_request_status take care of machines
            logger.debug("Spot Fleet creation completed successfully")
            return {
                'success': True,
                'request_id': fleet_id,
                'instance_ids': [],
                'failed_instances': []
            }
        
        except ClientError as e:
            # Extract AWS error code for better error reporting
            error_code = e.response['Error']['Code'] if hasattr(e, 'response') else 'UnknownError'
            error_msg = self._format_error_message("Spot Fleet request failed", e)
            logger.error(f"Spot Fleet creation failed: {error_msg}")
            return {
                'success': False,
                'error': error_msg,
                'aws_error_code': error_code,  # Add AWS error code
                'request_id': f"sfr-error-{int(time.time())}",
                'instance_ids': [],
                'failed_instances': []
            }
        except Exception as e:
            error_msg = self._format_error_message("Unexpected error creating Spot Fleet", e)
            logger.error(f"Unexpected error: {error_msg}")
            return {
                'success': False,
                'error': error_msg,
                'aws_error_code': 'InternalError',  # Add internal error code
                'request_id': f"sfr-error-{int(time.time())}",
                'instance_ids': [],
                'failed_instances': []
            }

    def _build_spot_fleet_launch_specs(self, template: Dict, rc_account: str = 'default') -> List[Dict]:
        """Build Spot Fleet launch specifications from template parameters"""
        logger.debug("Building Spot Fleet launch specifications...")
        
        # Get all subnets (not just one)
        subnet_id = template.get('subnetId')
        subnets = []
        if subnet_id:
            subnets = [s.strip() for s in subnet_id.split(',') if s.strip()]
            logger.debug(f"Multiple subnets available: {subnets}")
        
        security_groups = template.get('securityGroupIds', [])
        instance_tags = self._build_instance_tags(template, rc_account)
        encoded_user_data = self._get_encoded_user_data(template, rc_account)
        
        # Get placement configuration
        placement = {}
        placement_group = template.get('placementGroupName')
        if placement_group:
            placement['GroupName'] = placement_group
            
        tenancy = template.get('tenancy')
        if tenancy and tenancy in ['default', 'dedicated']:
            placement['Tenancy'] = tenancy
        
        # Handle multiple VM types
        vm_type = template.get('vmType', '')
        vm_types = []
        
        if vm_type and ',' in vm_type:
            vm_types = [v.strip() for v in vm_type.split(',') if v.strip()]
            logger.debug(f"Multiple VM types available: {vm_types}")
        elif vm_type:
            vm_types = [vm_type.strip()]
            logger.debug(f"Single VM type: {vm_types}")
        else:
            logger.error("No VM type specified for Spot Fleet")
            return []
        
        launch_specs = []
        
        # Create combinations: each vmType × each subnet
        for instance_type in vm_types:
            # If we have multiple subnets, create a launch spec for each
            if subnets:
                for subnet in subnets:
                    launch_spec = self._build_single_spot_fleet_launch_spec(
                        template, instance_type, subnet, security_groups, placement,
                        encoded_user_data, instance_tags, rc_account
                    )
                    if launch_spec:
                        launch_specs.append(launch_spec)
                        logger.debug(f"Added launch spec for {instance_type} in {subnet} with placement: {placement}")
            else:
                # No subnets specified, create one launch spec per instance type
                launch_spec = self._build_single_spot_fleet_launch_spec(
                    template, instance_type, None, security_groups, placement,
                    encoded_user_data, instance_tags, rc_account
                )
                if launch_spec:
                    launch_specs.append(launch_spec)
                    logger.debug(f"Added launch spec for {instance_type} (no subnet) with placement: {placement}")
        
        logger.debug(f"Built {len(launch_specs)} launch specifications for Spot Fleet")
        return launch_specs

    def _build_single_spot_fleet_launch_spec(self, template: Dict, instance_type: str, 
                                        subnet: Optional[str], security_groups: List[str], placement: Dict, 
                                        encoded_user_data: str, instance_tags: List[Dict],
                                        rc_account: str) -> Optional[Dict]:
        """Build a single Spot Fleet launch specification"""
        try:
            launch_spec = {
                'ImageId': template.get('imageId', ''),
                'InstanceType': instance_type,
                'UserData': encoded_user_data
            }

            # Add EbsOptimized if specified
            if 'ebsOptimized' in template:
                launch_spec['EbsOptimized'] = template['ebsOptimized']

            # Add key pair if specified
            key_name = self._get_key_name(template)
            if key_name:
                launch_spec['KeyName'] = key_name
                
            # Add network configuration if subnet is provided
            if subnet:
                network_interface = {
                    'DeviceIndex': 0,
                    'SubnetId': subnet,
                    'Groups': security_groups
                }
                
                # Add EFA if specified
                if template.get('interfaceType', '').lower() == 'efa':
                    network_interface['InterfaceType'] = 'efa'
                    
                launch_spec['NetworkInterfaces'] = [network_interface]
            
            # Add placement if configured
            if placement:
                launch_spec['Placement'] = placement
            
            # Add tags if available
            if instance_tags:
                launch_spec['TagSpecifications'] = [{
                    'ResourceType': 'instance',
                    'Tags': instance_tags
                }]

            # Add IAM instance profile if specified
            instance_profile = template.get('instanceProfile')
            if instance_profile:
                launch_spec['IamInstanceProfile'] = {
                    'Arn' if instance_profile.startswith('arn:aws:iam:') else 'Name': instance_profile
                }

            # Remove None values
            launch_spec = {k: v for k, v in launch_spec.items() if v is not None}
            return launch_spec
            
        except Exception as e:
            logger.error(f"Failed to build launch spec for {instance_type} in {subnet}: {e}")
            return None

    def _poll_spot_fleet_instances(self, fleet_id: str) -> List[str]:
        """Poll Spot Fleet to launch instances and return instance IDs - no retry logic"""
        logger.debug(f"Polling Spot Fleet instances for {fleet_id}")
        
        try:
            # Describe spot fleet instances
            response = self.ec2.describe_spot_fleet_instances(
                SpotFleetRequestId=fleet_id
            )
            logger.debug("Spot Fleet describe response received")
            
            active_instances = response.get('ActiveInstances', [])
            active_instance_ids = [instance['InstanceId'] for instance in active_instances]
            logger.debug(f"Found {len(active_instance_ids)} active instances in Spot Fleet")
            
            if active_instance_ids:
                logger.info(f"Spot Fleet {fleet_id} launched instances: {active_instance_ids}")

                # Get existing instance IDs from database
                request_data = db_manager.get_request(fleet_id)
                existing_instance_ids = set()
                if request_data and 'machines' in request_data:
                    existing_instance_ids = {machine['machineId'] for machine in request_data['machines'] if 'machineId' in machine}
                logger.debug(f"Found {len(existing_instance_ids)} existing instances in database")
                
                # Find new instances
                new_instance_ids = set(active_instance_ids) - existing_instance_ids
                logger.debug(f"Found {len(new_instance_ids)} new instances not in database")
                
                if new_instance_ids:
                    logger.info(f"Found {len(new_instance_ids)} new instances for Spot Fleet {fleet_id}")
                    
                    # Get template attributes from request data
                    template_id = request_data.get('templateId', 'unknown') if request_data else 'unknown'
                    rc_account = request_data.get('rcAccount', 'default') if request_data else 'default'
                    template_manager = TemplateManager()
                    template = template_manager.get_template(template_id)
                    logger.debug(f"Retrieved template {template_id} for new instances")
                    
                    # Collect all machine data for batch addition
                    batch_machine_data = []
                    for instance_id in new_instance_ids:
                        machine_data = self._create_machine_data(
                            instance_id=instance_id,
                            template=template,
                            request_id=fleet_id,
                            rc_account=rc_account,
                            name=f"host-{instance_id}"
                        )
                        batch_machine_data.append(machine_data)
                        logger.debug(f"Prepared new Spot Fleet instance {instance_id} for batch add")
                    
                    # BATCH ADD: Add all new machines in one operation
                    if batch_machine_data:
                        result = db_manager.add_machines_to_request(fleet_id, batch_machine_data)
                        if result['success_count'] > 0:
                            logger.info(f"Batch added {result['success_count']} new Spot Fleet instances to database")
                        if result['failed_count'] > 0:
                            logger.warning(f"Failed to add {result['failed_count']} new Spot Fleet instances to database: {result.get('errors')}")
                    
                    return active_instance_ids
                else:
                    # No new instances found - return existing ones
                    logger.debug(f"No new instances found for Spot Fleet {fleet_id}")
                    return active_instance_ids
            else:
                # No active instances found - return empty list
                logger.debug(f"No active instances found for Spot Fleet {fleet_id}")
                return []
                
        except ClientError as e:
            if e.response['Error']['Code'] == 'InvalidSpotFleetRequestId.NotFound':
                logger.warning(f"Spot Fleet {fleet_id} not found")
            else:
                logger.error(f"Error describing spot fleet instances: {e}")
                logger.debug(f"Spot Fleet polling ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            return []
        except Exception as e:
            logger.error(f"Unexpected error polling spot fleet instances: {e}")
            logger.debug(f"Spot Fleet polling stack trace:", exc_info=True)
            return []
        
        return []
              
    def _create_ec2_fleet(self, template: Dict, count: int, rc_account: str = 'default') -> Dict[str, Any]:
        """Create instances using EC2 Fleet API - supports both instant and request types"""
        logger.debug(f"Creating EC2 Fleet for {count} instances")
        
        fleet_id = None
        fleet_type = None
        successful_instances = []
        failed_instances = []
        
        try:
            logger.debug(f"Creating EC2 Fleet for template {template.get('templateId')}")
            
            # Load configuration
            fleet_config = self._load_ec2_fleet_config(template)
            logger.debug("EC2 Fleet configuration loaded successfully")
            
            # Determine fleet type from configuration
            fleet_type = fleet_config.get('Type', 'instant')  # Default to instant if not specified
            logger.debug(f"EC2 Fleet type: {fleet_type}")
            
            # Get encoded user data from user_data.sh script
            encoded_user_data = self._get_encoded_user_data(template, rc_account)
            logger.debug("Encoded user data retrieved")
            
            # Calculate slot-based capacity
            max_number = template.get('maxNumber', 1)
            attributes = template.get('attributes', {})
            ncpus = int(attributes.get('ncpus', ['Numeric', '1'])[1])
            
            total_slots = min(count, max_number * ncpus)
            logger.debug(f"Capacity calculation - max_number: {max_number}, ncpus: {ncpus}, total_slots: {total_slots}")
            
            # Handle ondemand ratio
            ratio = template.get('onDemandTargetCapacityRatio')
            if ratio is not None:
                try:
                    on_demand_slots = int(total_slots * float(ratio))
                    spot_slots = total_slots - on_demand_slots
                    logger.debug(f"On-demand ratio applied: {ratio}, on_demand_slots: {on_demand_slots}, spot_slots: {spot_slots}")
                except (ValueError, TypeError):
                    on_demand_slots = 0
                    spot_slots = total_slots
                    logger.warning(f"Invalid ondemand ratio: {ratio}, using all spot instances")
            else:
                on_demand_slots = 0
                spot_slots = total_slots
                logger.debug("No ondemand ratio specified, using all spot instances")
            
            # Replace placeholder variables in fleet config
            if 'TargetCapacitySpecification' in fleet_config:
                target_spec = fleet_config['TargetCapacitySpecification']
                
                target_spec['TotalTargetCapacity'] = total_slots
                target_spec['OnDemandTargetCapacity'] = on_demand_slots  
                target_spec['SpotTargetCapacity'] = spot_slots
                
                logger.info(f"Set fleet capacity (slot): Total={total_slots}, OnDemand={on_demand_slots}, Spot={spot_slots}")
            
            # Use helper functions for tags
            instance_tags = self._build_instance_tags(template, rc_account)
            
            # Add fleet-level tags only (instance tags must be in LaunchTemplate)
            if instance_tags:
                fleet_config['TagSpecifications'] = [{
                    'ResourceType': 'fleet',
                    'Tags': instance_tags
                }]
                logger.debug("Added fleet-level tags")
            
            # ALWAYS create temporary launch template versions with all overrides
            if 'LaunchTemplateConfigs' in fleet_config:
                logger.debug("Creating temporary launch template versions for EC2 Fleet with all overrides")
                success = self._create_temp_launch_template_versions(fleet_config, template, encoded_user_data, rc_account)
                if success:
                    logger.debug("Successfully created temporary launch template versions with all overrides")
                else:
                    logger.warning("Failed to create temporary launch template versions")
            
            # Create the fleet
            logger.debug(f"Creating EC2 Fleet with config: {fleet_config}")
            response = self.ec2.create_fleet(**fleet_config)
            
            fleet_id = response.get('FleetId')
            logger.debug(f"EC2 Fleet created: {fleet_id}, type: {fleet_type}")
            
            # Create request in database with fleet type information
            db_manager.create_request(
                request_id=fleet_id,
                template_id=template['templateId'],
                host_allocation_type="ec2Fleet",
                rc_account=rc_account,
                fleet_type=fleet_type  # Store fleet type for later reference
            )
            logger.debug(f"Created request {fleet_id} in database with fleet type: {fleet_type}")
            
            # Handle different fleet types
            if fleet_type == 'instant':
                # Instant fleet - instances are returned immediately
                logger.debug("Processing instant fleet instances immediately")
                all_machine_data = []  # Collect all machine data for batch addition
                
                for instance in response.get('Instances', []):
                    instance_ids = instance.get('InstanceIds', [])
                    successful_instances.extend(instance_ids)
                    logger.debug(f"Found {len(instance_ids)} instances in fleet response")
                    
                    for instance_id in instance_ids:
                        # Use helper for machine data creation
                        machine_data = self._create_machine_data(
                            instance_id=instance_id,
                            template=template,
                            request_id=fleet_id,
                            rc_account=rc_account,
                            name=f"host-{instance_id}"
                        )
                        all_machine_data.append(machine_data)
                        logger.debug(f"Prepared instant fleet instance {instance_id} for batch add")
                
                # BATCH ADD: Add all machines in one operation
                if all_machine_data:
                    result = db_manager.add_machines_to_request(fleet_id, all_machine_data)
                    if result['success_count'] > 0:
                        logger.info(f"Batch added {result['success_count']} instant fleet instances to database")
                    if result['failed_count'] > 0:
                        logger.warning(f"Failed to add {result['failed_count']} instant fleet instances to database: {result.get('errors')}")
                
                # Handle any errors in the response
                for error in response.get('Errors', []):
                    failed_instances.append({
                        'error_code': error.get('ErrorCode', 'Unknown'),
                        'error_message': error.get('ErrorMessage', 'Unknown error')
                    })
                    logger.debug(f"Fleet error: {error}")
                    
            else:  # request fleet
                # Request fleet - instances will be launched asynchronously
                # We don't get instances immediately, so we'll poll for them later
                logger.debug("Request fleet created - instances will be launched asynchronously")
                # No instances to process immediately for request fleets
            
            logger.debug(f"EC2 Fleet creation completed - successful_instances: {len(successful_instances)}, failed_instances: {len(failed_instances)}")
            return {
                'success': True,
                'request_id': fleet_id,
                'instance_ids': successful_instances,  # Empty for request fleets, populated for instant fleets
                'failed_instances': failed_instances,
                'fleet_type': fleet_type
            }
            
        except ClientError as e:
            # Extract AWS error code for better error reporting
            error_code = e.response['Error']['Code'] if hasattr(e, 'response') else 'UnknownError'
            error_msg = self._format_error_message("EC2 Fleet request failed", e)
            logger.error(f"EC2 Fleet creation failed: {error_msg}")
            
            # FIX: If fleet was created, return the fleet_id, not an error ID
            if fleet_id:
                logger.warning(f"Fleet {fleet_id} was created but encountered error: {error_msg}")
                return {
                    'success': True,  # Still return success since fleet was created
                    'request_id': fleet_id,
                    'instance_ids': successful_instances,
                    'failed_instances': failed_instances,
                    'fleet_type': fleet_type,
                    'warning': error_msg  # Include error as warning
                }
            else:
                return {
                    'success': False,
                    'error': error_msg,
                    'aws_error_code': error_code,
                    'request_id': f"fleet-error-{int(time.time())}",
                    'instance_ids': [],
                    'failed_instances': []
                }
        except Exception as e:
            error_msg = self._format_error_message("Unexpected error creating EC2 Fleet", e)
            logger.error(f"Unexpected error: {error_msg}")
            
            # FIX: If fleet was created, return the fleet_id, not an error ID
            if fleet_id:
                logger.warning(f"Fleet {fleet_id} was created but encountered unexpected error: {error_msg}")
                return {
                    'success': True,  # Still return success since fleet was created
                    'request_id': fleet_id,
                    'instance_ids': successful_instances,
                    'failed_instances': failed_instances,
                    'fleet_type': fleet_type,
                    'warning': error_msg  # Include error as warning
                }
            else:
                return {
                    'success': False,
                    'error': error_msg,
                    'aws_error_code': 'InternalError',
                    'request_id': f"fleet-error-{int(time.time())}",
                    'instance_ids': [],
                    'failed_instances': []
                }

    def _create_temp_launch_template_versions(self, fleet_config: Dict, template: Dict, encoded_user_data: str, rc_account: str = 'default') -> bool:
        """
        ALWAYS create temporary launch template versions with all template overrides including user data
        
        Returns True if at least one temporary version was successfully created and used
        """
        logger.debug("Creating temporary launch template versions...")
        if 'LaunchTemplateConfigs' not in fleet_config:
            logger.debug("No LaunchTemplateConfigs found - skipping temporary version creation")
            return False
        
        template_id = template.get('templateId', 'unknown')
        successful_creations = 0
        total_configs = len(fleet_config['LaunchTemplateConfigs'])
        
        logger.info(f"Processing {total_configs} LaunchTemplateConfigs for template {template_id}")
        
        for i, lt_config in enumerate(fleet_config['LaunchTemplateConfigs']):
            if 'LaunchTemplateSpecification' in lt_config:
                spec = lt_config['LaunchTemplateSpecification']
                original_template_id = spec.get('LaunchTemplateId')
                original_template_name = spec.get('LaunchTemplateName')
                
                if not original_template_id and not original_template_name:
                    logger.warning(f"LaunchTemplateConfig {i} missing both ID and Name - skipping")
                    continue
                
                try:
                    # Get the original launch template to copy its configuration
                    describe_kwargs = {}
                    if original_template_id:
                        describe_kwargs['LaunchTemplateId'] = original_template_id
                        logger.debug(f"Processing LaunchTemplate ID: {original_template_id}")
                    else:
                        describe_kwargs['LaunchTemplateName'] = original_template_name
                        logger.debug(f"Processing LaunchTemplate Name: {original_template_name}")
                    
                    # Get the specific version or default
                    version = spec.get('Version', '$Default')
                    describe_kwargs['Versions'] = [version]
                    
                    # Get the original launch template data
                    logger.debug(f"Describing launch template versions for config {i}")
                    original_response = self.ec2.describe_launch_template_versions(**describe_kwargs)
                    
                    if not original_response['LaunchTemplateVersions']:
                        logger.warning(f"Could not find original launch template version: {describe_kwargs}")
                        continue
                    
                    original_version = original_response['LaunchTemplateVersions'][0]
                    original_data = original_version['LaunchTemplateData']
                    logger.debug(f"Retrieved original launch template data for config {i}")
                    
                    # Create a new version of the existing launch template with ALL overrides
                    timestamp = int(time.time())
                    version_description = f'Temporary LSF version for {template_id} with all overrides - created {time.ctime()}'
                    
                    # Start with original data and apply ALL template overrides
                    version_data = original_data.copy()
                    
                    # 1. Inject user data (highest priority)
                    if encoded_user_data:
                        version_data['UserData'] = encoded_user_data
                        logger.debug(f"Injected user data into version for config {i}")
                    
                    # 2. Apply network configuration overrides
                    network_config = self._build_network_config(template)
                    if network_config.get('SubnetId'):
                        if 'NetworkInterfaces' not in version_data:
                            version_data['NetworkInterfaces'] = []
                        
                        # Add or update primary network interface
                        if version_data['NetworkInterfaces']:
                            # Update existing first interface
                            version_data['NetworkInterfaces'][0]['SubnetId'] = network_config['SubnetId']
                            version_data['NetworkInterfaces'][0]['Groups'] = network_config['Groups']
                        else:
                            # Create new network interface
                            version_data['NetworkInterfaces'] = [{
                                'DeviceIndex': 0,
                                'SubnetId': network_config['SubnetId'],
                                'Groups': network_config['Groups']
                            }]
                        logger.debug(f"Applied network overrides for config {i}")
                    
                    # 3. Apply instance type override if specified in template
                    if template.get('vmType'):
                        version_data['InstanceType'] = template['vmType']
                        logger.debug(f"Applied instance type override: {template['vmType']} for config {i}")
                    
                    # 4. Apply key pair override if specified
                    key_name = self._get_key_name(template)
                    if key_name:
                        version_data['KeyName'] = template['keyName']
                        logger.debug(f"Applied key pair override: {template['keyName']} for config {i}")
                    
                    # 5. Apply IAM instance profile override if specified
                    instance_profile = template.get('instanceProfile')
                    if instance_profile:
                        version_data['IamInstanceProfile'] = {
                            'Arn' if instance_profile.startswith('arn:aws:iam:') else 'Name': instance_profile
                        }
                        logger.debug(f"Applied IAM instance profile override: {instance_profile} for config {i}")
                    
                    # 6. Apply EBS optimized override if specified
                    if template.get('ebsOptimized') is not None:
                        version_data['EbsOptimized'] = template['ebsOptimized']
                        logger.debug(f"Applied EBS optimized override: {template['ebsOptimized']} for config {i}")
                    
                    # 7. Apply instance tags
                    instance_tags = self._build_instance_tags(template, rc_account)
                    if instance_tags:
                        version_data['TagSpecifications'] = [
                            {
                                'ResourceType': 'instance',
                                'Tags': instance_tags
                            },
                            {
                                'ResourceType': 'volume', 
                                'Tags': instance_tags
                            }
                        ]
                        logger.debug(f"Applied {len(instance_tags)} instance tags for config {i}")
                    
                    # Create the new version
                    create_version_kwargs = {
                        'LaunchTemplateId' if original_template_id else 'LaunchTemplateName': original_template_id or original_template_name,
                        'LaunchTemplateData': version_data,
                        'VersionDescription': version_description
                    }
                    
                    # Create the new version of the launch template
                    logger.debug(f"Creating new version of launch template for config {i}")
                    create_response = self.ec2.create_launch_template_version(**create_version_kwargs)
                    new_version_number = create_response['LaunchTemplateVersion']['VersionNumber']
                    
                    # Update the fleet config to use the specific new version
                    spec['Version'] = str(new_version_number)
                    
                    # Ensure we're using ID for consistency
                    if original_template_id:
                        spec['LaunchTemplateId'] = original_template_id
                        if 'LaunchTemplateName' in spec:
                            del spec['LaunchTemplateName']
                    
                    logger.debug(f"Successfully created version {new_version_number} of launch template for config {i}")
                    successful_creations += 1
                    
                except ClientError as e:
                    error_code = e.response['Error']['Code']
                    error_message = e.response['Error']['Message']
                    logger.error(f"Failed to create temporary launch template version for config {i}: {error_code} - {error_message}")
                    continue
                except Exception as e:
                    logger.error(f"Unexpected error creating temporary launch template version for config {i}: {e}")
                    logger.debug(f"Launch template version creation stack trace:", exc_info=True)
                    continue
        
        logger.debug(f"Successfully created temporary versions for {successful_creations}/{total_configs} LaunchTemplateConfigs")
        return successful_creations > 0

    def _load_ec2_fleet_config(self, template: Dict) -> Dict[str, Any]:
        """Load EC2 Fleet configuration from file - support both valid JSON and legacy format with placeholders"""
        logger.debug("Loading EC2 Fleet configuration...")
        ec2_fleet_config_path = template.get('ec2FleetConfig')
        if not ec2_fleet_config_path:
            logger.error("EC2 Fleet configuration path not provided")
            raise ValueError("EC2 Fleet configuration path not provided")
        
        # Use existing config path resolution pattern
        if not os.path.isabs(ec2_fleet_config_path):
            from utils import get_config_path
            config_dir = get_config_path()
            ec2_fleet_config_path = os.path.join(config_dir, ec2_fleet_config_path)
        logger.debug(f"Resolved EC2 Fleet config path: {ec2_fleet_config_path}")
        
        if not os.path.exists(ec2_fleet_config_path):
            logger.error(f"EC2 Fleet configuration file not found: {ec2_fleet_config_path}")
            raise FileNotFoundError(f"EC2 Fleet configuration file not found: {ec2_fleet_config_path}")
        
        try:
            with open(ec2_fleet_config_path, 'r') as f:
                content = f.read()
                logger.debug(f"Raw config file content length: {len(content)}")
            
            # First, try to parse as regular JSON
            try:
                config = json.loads(content)
                logger.debug("EC2 Fleet configuration loaded as valid JSON")
                return config
            except json.JSONDecodeError as json_error:
                logger.warning(
                    f"EC2 Fleet configuration file is not valid JSON, attempting legacy format parsing: {json_error}. "
                    f"Note: The use of variables like $LSF_TOTAL_TARGET_CAPACITY, $LSF_ONDEMAND_TARGET_CAPACITY, "
                    f"and $LSF_SPOT_TARGET_CAPACITY in EC2 Fleet configuration file is DEPRECATED. "
                    f"Please remove these variables and use valid JSON format as they are no longer supported."
                )
                
                # For legacy format, replace the placeholder variables with 0 since they will be overridden anyway
                # This handles the specific placeholders mentioned in the issue
                legacy_placeholders = {
                    '$LSF_TOTAL_TARGET_CAPACITY': '0',
                    '$LSF_ONDEMAND_TARGET_CAPACITY': '0', 
                    '$LSF_SPOT_TARGET_CAPACITY': '0'
                }
                
                cleaned_content = content
                for placeholder, replacement in legacy_placeholders.items():
                    if placeholder in cleaned_content:
                        logger.debug(f"Replacing legacy placeholder {placeholder} with {replacement}")
                        cleaned_content = cleaned_content.replace(placeholder, replacement)
                
                # Try parsing the cleaned content
                try:
                    config = json.loads(cleaned_content)
                    logger.debug("EC2 Fleet configuration loaded from legacy format")
                    return config
                except json.JSONDecodeError as second_error:
                    logger.error(f"Failed to parse even after cleaning legacy placeholders: {second_error}")
                    logger.debug(f"Cleaned content that failed to parse: {cleaned_content}")
                    raise ValueError(f"Invalid JSON in EC2 Fleet configuration even after legacy placeholder cleaning: {second_error}")
                    
        except Exception as e:
            logger.error(f"Unexpected error loading EC2 Fleet configuration: {e}")
            logger.debug(f"Config loading stack trace:", exc_info=True)
            raise

    def _cleanup_launch_template_versions_for_fleet(self, request_id: str):
        """Clean up temporary launch template versions created for an EC2 Fleet request"""
        logger.debug(f"Starting launch template version cleanup for fleet: {request_id}")
        try:
            logger.debug(f"Cleaning up launch template versions for fleet request: {request_id}")
            
            # Get the request data to find template information
            request_data = db_manager.get_request(request_id)
            if not request_data:
                logger.warning(f"Request {request_id} not found in database, cannot cleanup launch template versions")
                return
            
            template_id = request_data.get('templateId')
            if not template_id:
                logger.warning(f"No template ID found for request {request_id}, cannot cleanup launch template versions")
                return
            
            # We need to identify which launch template versions were created for this fleet
            # Since we don't store version info in DB, we'll use a naming pattern to identify them
            versions_cleaned = 0
            
            # Describe all launch templates to find ones with our pattern
            try:
                # Get all launch templates
                launch_templates = self.ec2.describe_launch_templates()
                logger.debug(f"Found {len(launch_templates.get('LaunchTemplates', []))} launch templates to check")
                
                for lt in launch_templates.get('LaunchTemplates', []):
                    lt_id = lt['LaunchTemplateId']
                    lt_name = lt['LaunchTemplateName']
                    
                    # Check if this template has versions created for our template ID
                    try:
                        # Describe all versions of this launch template
                        versions_response = self.ec2.describe_launch_template_versions(
                            LaunchTemplateId=lt_id
                        )
                        logger.debug(f"Found {len(versions_response.get('LaunchTemplateVersions', []))} versions for {lt_name}")
                        
                        for version in versions_response.get('LaunchTemplateVersions', []):
                            version_desc = version.get('VersionDescription', '')
                            version_num = version['VersionNumber']
                            
                            # Check if this version was created for our template ID
                            if (f"Temporary LSF version for {template_id}" in version_desc and 
                                version_num > 1):  # Don't delete default version (1)
                                
                                try:
                                    # Delete the version
                                    self.ec2.delete_launch_template_versions(
                                        LaunchTemplateId=lt_id,
                                        Versions=[str(version_num)]
                                    )
                                    logger.debug(f"Deleted launch template version {version_num} from {lt_name} (was for template {template_id})")
                                    versions_cleaned += 1
                                except ClientError as e:
                                    if e.response['Error']['Code'] == 'InvalidLaunchTemplateVersion.NotFound':
                                        logger.debug(f"Launch template version {version_num} already deleted from {lt_name}")
                                    else:
                                        logger.warning(f"Failed to delete version {version_num} from {lt_name}: {e}")
                                        logger.debug(f"Version deletion ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
                                        
                    except ClientError as e:
                        logger.warning(f"Failed to describe versions for launch template {lt_name}: {e}")
                        logger.debug(f"Version description ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
                        continue
                        
            except ClientError as e:
                logger.error(f"Failed to describe launch templates for cleanup: {e}")
                logger.debug(f"Launch template description ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            
            logger.debug(f"Cleaned up {versions_cleaned} launch template versions for fleet request {request_id}")
            
        except Exception as e:
            logger.error(f"Error during launch template version cleanup for fleet {request_id}: {e}")
            logger.debug(f"Launch template cleanup stack trace:", exc_info=True)
            
    def _poll_ec2_fleet_instances(self, fleet_id: str) -> List[str]:
        """Poll EC2 Fleet to get launched instances - no retry logic"""
        logger.debug(f"Polling EC2 Fleet instances for {fleet_id}")
        
        try:
            # This method is only called for request fleets, so we don't need fleet type checks
            response = self.ec2.describe_fleet_instances(FleetId=fleet_id)
            logger.debug("EC2 Fleet describe response received")    
            
            active_instances = response.get('ActiveInstances', [])
            active_instance_ids = [instance['InstanceId'] for instance in active_instances]
            logger.debug(f"Found {len(active_instance_ids)} active instances in EC2 Fleet")
            
            if active_instance_ids:
                logger.info(f"EC2 Request Fleet {fleet_id} launched instances: {active_instance_ids}")

                # Get existing instance IDs from database
                request_data = db_manager.get_request(fleet_id)
                existing_instance_ids = set()
                if request_data and 'machines' in request_data:
                    existing_instance_ids = {machine['machineId'] for machine in request_data['machines'] if 'machineId' in machine}
                logger.debug(f"Found {len(existing_instance_ids)} existing instances in database")
                
                # Find new instances
                new_instance_ids = set(active_instance_ids) - existing_instance_ids
                logger.debug(f"Found {len(new_instance_ids)} new instances not in database")
                
                if new_instance_ids:
                    logger.debug(f"Found {len(new_instance_ids)} new instances for EC2 Request Fleet {fleet_id}")
                    
                    # Get template attributes from request data
                    template_id = request_data.get('templateId', 'unknown')
                    rc_account = request_data.get('rcAccount', 'default')
                    template_manager = TemplateManager()
                    template = template_manager.get_template(template_id)
                    logger.debug(f"Retrieved template {template_id} for new instances")
                    
                    # Collect all machine data for batch addition
                    batch_machine_data = []
                    for instance_id in new_instance_ids:
                        # Use helper for machine data creation
                        machine_data = self._create_machine_data(
                            instance_id=instance_id,
                            template=template,
                            request_id=fleet_id,
                            rc_account=rc_account,
                            name=f"host-{instance_id}"
                        )
                        batch_machine_data.append(machine_data)
                        logger.debug(f"Prepared new EC2 Request Fleet instance {instance_id} for batch add")
                
                # BATCH ADD: Add all new machines in one operation
                    if batch_machine_data:
                        result = db_manager.add_machines_to_request(fleet_id, batch_machine_data)
                        if result['success_count'] > 0:
                            logger.info(f"Batch added {result['success_count']} new EC2 Request Fleet instances to database")
                        if result['failed_count'] > 0:
                            logger.warning(f"Failed to add {result['failed_count']} new EC2 Request Fleet instances to database: {result.get('errors')}")
                    
                    return active_instance_ids
                else:
                    # No new instances found - return existing ones
                    logger.debug(f"No new instances found for EC2 Request Fleet {fleet_id}")
                    return active_instance_ids
                
            else:
                # No active instances found - return empty list
                logger.debug(f"No active instances found for EC2 Request Fleet {fleet_id}")
                return []

        except ClientError as e:
            if e.response['Error']['Code'] == 'InvalidFleetId.NotFound':
                logger.warning(f"EC2 Fleet {fleet_id} not found")
            elif e.response['Error']['Code'] == 'Unsupported':
                # This should not happen since we only call this for request fleets
                logger.error(f"Unexpected: DescribeFleetInstances not supported for fleet {fleet_id} - this should be a request fleet")
            else:
                logger.error(f"Error describing EC2 fleet instances: {e}")
                logger.debug(f"EC2 Fleet polling ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            return []
        except Exception as e:
            logger.error(f"Unexpected error polling EC2 fleet instances: {e}")
            logger.debug(f"EC2 Fleet polling stack trace:", exc_info=True)
            return []
        
        return []
       
    def request_return_machines(self, instance_ids: List[str]) -> str:
        """Terminate EC2 instances using AWS batch API"""
        logger.debug(f"Starting request_return_machines for {len(instance_ids)} instances")
        
        # Check credentials once at the beginning
        self._refresh_credentials_if_needed()
        
        request_id = f"remove-{os.getpid()}-{int(time.time())}"
        logger.info(f"Starting batch instance termination request {request_id} for {len(instance_ids)} instances")
        
        # AWS has a limit of 1000 instances per request_return_machines call
        chunks = []
        
        # Break into chunks of batch_size
        for i in range(0, len(instance_ids), self.batch_size):
            chunk = instance_ids[i:i + self.batch_size]
            chunks.append(chunk)
            logger.debug(f"Created chunk {i//self.batch_size + 1}: {len(chunk)} instances")
        
        successful_terminations = []
        failed_terminations = []
        all_updates = []  # Collect all updates for batch processing
        
        try:
            # Process each chunk
            for chunk_idx, chunk in enumerate(chunks):
                logger.debug(f"Processing chunk {chunk_idx + 1}/{len(chunks)} with {len(chunk)} instances")
                
                try:
                    # Single AWS API call for the entire chunk
                    response = self.ec2.terminate_instances(InstanceIds=chunk)
                    logger.debug(f"Chunk {chunk_idx + 1}: Terminate API call successful.  Response: {response}")
                    
                    # Get the instance states from response
                    for instance in response.get('TerminatingInstances', []):
                        instance_id = instance.get('InstanceId')
                        
                        # Try to find the request for this machine
                        machine_info = db_manager.get_request_for_machine(instance_id)
                        if machine_info and machine_info.get('request'):
                            all_updates.append({
                                'request_id': machine_info['request']['requestId'],
                                'machine_id': instance_id,
                                'status': 'shutting-down',
                                'result': 'executing',
                                'message': 'Instance termination initiated',
                                'return_id': request_id
                            })
                        else:
                            # Try legacy fallback - search all requests
                            logger.debug(f"No machine info found for {instance_id}, trying legacy lookup")
                            all_requests = db_manager.get_all_requests()
                            for request in all_requests:
                                for machine in request.get('machines', []):
                                    if machine.get('machineId') == instance_id:
                                        all_updates.append({
                                            'request_id': request['requestId'],
                                            'machine_id': instance_id,
                                            'status': 'shutting-down',
                                            'result': 'executing',
                                            'message': 'Instance termination initiated (legacy)',
                                            'return_id': request_id
                                        })
                                        break
                                if any(u['machine_id'] == instance_id for u in all_updates):
                                    break
                    
                    successful_terminations.extend(chunk)
                    logger.info(f"Chunk {chunk_idx + 1}: Successfully terminated {len(chunk)} instances")
                    time.sleep(1)
                    
                except ClientError as e:
                    error_code = e.response['Error']['Code'] if hasattr(e, 'response') else 'UnknownError'
                    error_message = e.response['Error']['Message'] if hasattr(e, 'response') else str(e)
                    
                    logger.error(f"Chunk {chunk_idx + 1}: Failed to terminate instances: {error_code} - {error_message}")
                    
                    # Handle specific error cases
                    if error_code == 'InvalidInstanceID.NotFound':
                        # Some instances don't exist - try to identify which ones
                        try:
                            # Get details of instances in this chunk to see which exist
                            existing_instances = []
                            details = self.get_instance_details_bulk(chunk)
                            for instance_id, detail in details.items():
                                if detail.get('state') != 'terminated':
                                    existing_instances.append(instance_id)
                            
                            if existing_instances:
                                # Retry with only existing instances
                                logger.debug(f"Retrying chunk {chunk_idx + 1} with {len(existing_instances)} existing instances")
                                retry_response = self.ec2.terminate_instances(InstanceIds=existing_instances)
                                
                                # Update successful terminations
                                successful_terminations.extend(existing_instances)
                                
                                # Update database for successful terminations
                                updates = []
                                for instance_id in existing_instances:
                                    machine_info = db_manager.get_request_for_machine(instance_id)
                                    if machine_info and machine_info.get('request'):
                                        updates.append({
                                            'request_id': machine_info['request']['requestId'],
                                            'machine_id': instance_id,
                                            'status': 'shutting-down',
                                            'result': 'executing',
                                            'message': 'Instance termination initiated',
                                            'return_id': request_id
                                        })
                                
                                if updates:
                                    db_manager.update_machines(updates)
                                
                                # Add the non-existent instances to failed list
                                non_existent = set(chunk) - set(existing_instances)
                                for instance_id in non_existent:
                                    failed_terminations.append({
                                        'instance_id': instance_id,
                                        'error': f'Instance not found - may already be terminated',
                                        'error_code': error_code
                                    })
                                    logger.warning(f"Instance {instance_id} not found - may be already terminated")
                            else:
                                # All instances are already terminated
                                logger.info(f"Chunk {chunk_idx + 1}: All instances already terminated")
                                successful_terminations.extend(chunk)
                                
                        except Exception as retry_error:
                            # If retry fails, mark all instances in chunk as failed
                            for instance_id in chunk:
                                failed_terminations.append({
                                    'instance_id': instance_id,
                                    'error': f'{error_code}: {error_message}',
                                    'error_code': error_code
                                })
                    else:
                        # Other errors - mark all instances in chunk as failed
                        for instance_id in chunk:
                            failed_terminations.append({
                                'instance_id': instance_id,
                                'error': f'{error_code}: {error_message}',
                                'error_code': error_code
                            })
                
                except Exception as e:
                    logger.error(f"Chunk {chunk_idx + 1}: Unexpected error: {e}")
                    for instance_id in chunk:
                        failed_terminations.append({
                            'instance_id': instance_id,
                            'error': str(e),
                            'error_code': 'InternalError'
                        })
            
            # BATCH UPDATE: Apply all database updates in one operation
            if all_updates:
                batch_result = db_manager.update_machines(all_updates)
                logger.debug(f"Batch updated {batch_result['success_count']} machines for termination request {request_id}")
            
            # Log overall results
            logger.info(f"Request {request_id}: Terminated {len(successful_terminations)}/{len(instance_ids)} instances")
            
            if failed_terminations:
                logger.warning(f"Request {request_id}: {len(failed_terminations)} instances failed to terminate")
            
            return request_id
            
        except Exception as e:
            logger.error(f"Fatal error in request_return_machines: {e}")
            # Return request_id anyway for tracking
            return request_id
    
    def get_return_requests(self, machines: List[Dict[str, str]]) -> Dict[str, Any]:
        """Check if instances are terminated - return consistent format"""
        instance_ids = [machine['machineId'] for machine in machines]
        logger.debug(f"Starting get_return_requests for {len(instance_ids)} instances")

        try:
            logger.debug(f"Checking terminated instances: {instance_ids}")
            terminated_instance_ids = self._find_terminated_instances(instance_ids)
            logger.debug(f"Found terminated instances: {terminated_instance_ids}")
            
            # Create a map from machineId to name for easy lookup
            machine_map = {machine['machineId']: machine['name'] for machine in machines}
        
            requests = []
            for instance_id in terminated_instance_ids:
                machine_name = machine_map.get(instance_id, f'host-{instance_id}')
                requests.append({
                    "machineId": instance_id,
                    "machine": machine_name
                })
                logger.debug(f"Added terminated instance to results: {instance_id} with name {machine_name}")
            
            # Return consistent format with other methods
            result = {
                "status": "complete",
                "message": f"Found {len(requests)} terminated instances" if requests else "No terminated instances found",
                "requests": requests
            }
            logger.debug(f"get_return_requests result: {result}")
            return result
            
        except Exception as e:
            logger.error(f"Error in get_return_requests: {e}")
            logger.debug(f"get_return_requests exception - type: {type(e).__name__}, args: {e.args}")
            logger.debug("get_return_requests stack trace:", exc_info=True)
            return {
                "status": "complete_with_error",
                "message": str(e),
                "requests": []
            }

    def _find_terminated_instances(self, instance_ids: List[str]) -> List[Dict[str, str]]:
        """Internal method to find terminated instances"""
        logger.debug(f"_find_terminated_instances called with: {instance_ids}")
        terminated = []
        updates = []  # Collect batch updates
        
        if not instance_ids:
            logger.debug("No instance IDs provided to _find_terminated_instances")
            return terminated
        
        try:
            instance_details = self.get_instance_details_bulk(instance_ids)
            logger.debug(f"Bulk instance details: {instance_details}")
            
            for instance_id, details in instance_details.items():
                if details.get('state') == 'terminated':
                    terminated.append(instance_id)
                    logger.debug(f"Instance {instance_id} is terminated")
                    
                    # Update database - collect for batch operation
                    machine_info = db_manager.get_request_for_machine(instance_id)
                    if machine_info and machine_info.get('request') and machine_info.get('state') != 'terminated':
                        state = details.get('state')
                        logger.debug(f"Will update database for instance {instance_id} to state: {state}")
                        updates.append({
                            'request_id': machine_info['request']['requestId'],
                            'machine_id': instance_id,
                            'status': state,
                            'result': "succeed" if state == 'terminated' else "executing",
                            'message': f"Instance {state} by cloud provider"
                        })
            
            # Apply batch update if we have updates
            if updates:
                batch_result = db_manager.update_machines(updates)
                logger.debug(f"Batch updated {batch_result['success_count']} terminated instances, failed: {batch_result['failed_count']}")
        
        except Exception as e:
            logger.error(f"Error in _find_terminated_instances: {e}")
            logger.debug(f"_find_terminated_instances exception - type: {type(e).__name__}, args: {e.args}")
            logger.debug("_find_terminated_instances stack trace:", exc_info=True)
            # Asynchronous process - if we can't get details, just return empty list
            # ebrokerd will retry later
            logger.debug(f"Failed to get instance details, returning empty list for ebrokerd to retry later")
            return []
        
        logger.debug(f"_find_terminated_instances returning: {terminated}")
        return terminated

    def get_instance_details(self, instance_id: str) -> Dict[str, Any]:
        """Get instance details - no retry logic"""
        logger.debug(f"get_instance_details called for {instance_id}")
        try:
            logger.debug(f"Getting details for instance {instance_id}")
            instances = self.ec2_resource.instances.filter(InstanceIds=[instance_id])
            instance = list(instances)[0]
            logger.debug(f"Instance details: {instance.meta.data}")
            state = instance.state['Name']
            logger.debug(f"Instance {instance_id} state: {state}")
            
            # Get state reason if available
            state_reason = None
            if hasattr(instance, 'state_reason') and instance.state_reason:
                state_reason = {
                    'Code': instance.state_reason.get('Code'),
                    'Message': instance.state_reason.get('Message')
                }
                logger.debug(f"Instance {instance_id} state_reason: {state_reason}")
            
            lifecycle = 'ondemand'
            try:
                if hasattr(instance, 'instance_lifecycle') and instance.instance_lifecycle:
                    lifecycle = instance.instance_lifecycle
                elif instance.instance_type.startswith('spot') or getattr(instance, 'spot_instance_request_id', None):
                    lifecycle = 'spot'
                logger.debug(f"Instance {instance_id} lifecycle: {lifecycle}")
            except Exception as e:
                logger.debug(f"Could not determine lifecycle for {instance_id}: {e}")
                lifecycle = 'ondemand'
            
            result = {
                'state': state,
                'privateIpAddress': instance.private_ip_address,
                'publicIpAddress': instance.public_ip_address,
                'name': instance.private_dns_name,
                'publicDnsName': instance.public_dns_name,
                'launchtime': instance.launch_time.timestamp() if instance.launch_time else None,
                'lifecycle': lifecycle,
                'state_reason': state_reason
            }
            logger.debug(f"Returning instance details: {result}")
            return result
                
        except ClientError as e:
            logger.error(f"Failed to get instance details: {e}")
            return {
                'state': 'unknown',
                'state_reason': None
            }
        except Exception as e:
            logger.error(f"Unexpected error getting instance details: {e}")
            return {
                'state': 'unknown',
                'state_reason': None
            }

    def get_instance_details_bulk(self, instance_ids: List[str], chunk_size: int = 100) -> Dict[str, Dict[str, Any]]:
        """Get details for multiple instances - no retry logic"""
        logger.debug(f"get_instance_details_bulk called for {len(instance_ids)} instances")
        if not instance_ids:
            return {}
        
        result = {}
        
        # Process in chunks
        for i in range(0, len(instance_ids), chunk_size):
            chunk = instance_ids[i:i + chunk_size]
            
            try:
                instances = self.ec2_resource.instances.filter(InstanceIds=chunk)
                found_instance_ids = set()
                
                for instance in instances:
                    found_instance_ids.add(instance.id)
                    state = instance.state['Name']
                    
                    # Get state reason
                    state_reason = None
                    if hasattr(instance, 'state_reason') and instance.state_reason:
                        state_reason = {
                            'Code': instance.state_reason.get('Code'),
                            'Message': instance.state_reason.get('Message')
                        }
                    
                    lifecycle = 'ondemand'
                    try:
                        if hasattr(instance, 'instance_lifecycle') and instance.instance_lifecycle:
                            lifecycle = instance.instance_lifecycle
                        elif instance.instance_type.startswith('spot') or getattr(instance, 'spot_instance_request_id', None):
                            lifecycle = 'spot'
                    except Exception:
                        lifecycle = 'ondemand'
                    
                    result[instance.id] = {
                        'state': state,
                        'privateIpAddress': instance.private_ip_address,
                        'publicIpAddress': instance.public_ip_address,
                        'name': instance.private_dns_name,
                        'publicDnsName': instance.public_dns_name,
                        'launchtime': instance.launch_time.timestamp() if instance.launch_time else None,
                        'lifecycle': lifecycle,
                        'state_reason': state_reason,
                        'source': 'bulk'
                    }
                    
                # Handle missing instances
                missing_in_chunk = set(chunk) - found_instance_ids
                for missing_id in missing_in_chunk:
                    result[missing_id] = {
                        'state': 'terminated',
                        'privateIpAddress': None,
                        'publicIpAddress': None,
                        'name': None,
                        'publicDnsName': None,
                        'launchtime': None,
                        'lifecycle': None,
                        'state_reason': None,
                        'source': 'bulk-missing'
                    }
                time.sleep(1)
                    
            except ClientError as e:
                logger.warning(f"Bulk operation failed for chunk: {e}")
                # Mark all instances in chunk as unknown
                for instance_id in chunk:
                    result[instance_id] = {
                        'state': 'unknown',
                        'privateIpAddress': None,
                        'publicIpAddress': None,
                        'name': None,
                        'publicDnsName': None,
                        'launchtime': None,
                        'lifecycle': None,
                        'state_reason': None,
                        'source': 'bulk-error'
                    }
                time.sleep(1)
        
        return result

    def _get_fleet_based_status(self, request_id: str) -> Optional[Dict[str, Any]]:
        """
        Check fleet state and activity status to determine request status.
        Only returns status when we can definitively determine it from fleet states.
        Otherwise returns None (meaning use instance-level status checking).
        """
        try:
            if request_id.startswith("sfr-"):
                # Spot Fleet status check
                response = self.ec2.describe_spot_fleet_requests(SpotFleetRequestIds=[request_id])
                if not response.get('SpotFleetRequestConfigs'):
                    logger.warning(f"Spot Fleet {request_id} not found")
                    return {'status': 'complete_with_error', 'message': 'Spot Fleet not found'}
                
                fleet_config = response['SpotFleetRequestConfigs'][0]
                fleet_state = fleet_config.get('SpotFleetRequestState', '')
                activity_status = fleet_config.get('ActivityStatus', '')
                
                logger.debug(f"Spot Fleet {request_id} - State: {fleet_state}, Activity Status: {activity_status}")
                
                # Check for terminal states
                if fleet_state in ['cancelled', 'cancelled_running', 'cancelled_terminating']:
                    return {'status': 'complete', 'message': f'Spot Fleet {fleet_state}'}
                elif fleet_state == 'failed':
                    return {'status': 'complete_with_error', 'message': 'Spot Fleet failed'}
                elif fleet_state == 'active' and activity_status in ['fulfilled', 'fulfilled_partial']:
                    # Fleet is active but fully/partially fulfilled - no more instances will be added
                    return {'status': 'complete', 'message': f'Spot Fleet {activity_status}'}
                # For 'active' with 'pending_fulfillment' or other states, return None (fleet still active)
                
            elif request_id.startswith("fleet-"):
                # EC2 Fleet status check
                response = self.ec2.describe_fleets(FleetIds=[request_id])
                if not response.get('Fleets'):
                    logger.warning(f"EC2 Fleet {request_id} not found")
                    return {'status': 'complete_with_error', 'message': 'EC2 Fleet not found'}
                
                fleet = response['Fleets'][0]
                fleet_state = fleet.get('State', '')
                fleet_errors = fleet.get('Errors', [])
                
                logger.debug(f"EC2 Fleet {request_id} - State: {fleet_state}, Errors: {len(fleet_errors)}")
                
                # Check for terminal states
                if fleet_state in ['deleted', 'deleted_running', 'deleted_terminating']:
                    return {'status': 'complete', 'message': f'EC2 Fleet {fleet_state}'}
                elif fleet_state == 'failed':
                    return {'status': 'complete_with_error', 'message': 'EC2 Fleet failed'}
                elif fleet_errors:
                    # Fleet has errors but might still be active
                    error_messages = [e.get('ErrorMessage', 'Unknown error') for e in fleet_errors]
                    return {'status': 'complete_with_error', 'message': f'EC2 Fleet has errors: {", ".join(error_messages)}'}
                # For 'submitted', 'active', etc., return None (fleet still active)
            
            # If we can't definitively determine status from fleet state, return None
            return None
            
        except ClientError as e:
            error_code = e.response['Error']['Code']
            logger.warning(f"Error checking {request_id} fleet status: {error_code}")
            if 'NotFound' in error_code:
                return {'status': 'complete_with_error', 'message': 'Fleet not found'}
            # For other errors, continue with instance-level checking
            return None
        
        except Exception as e:
            logger.warning(f"Unexpected error checking fleet status for {request_id}: {e}")
            # Continue with instance-level checking
            return None
    
    def get_request_status(self, request_id: str) -> Dict[str, Any]:
        """Get request status with proper state transition handling"""
        logger.debug(f"get_request_status called for request: {request_id}")
        
        # Determine request type
        is_creation = request_id.startswith(("dir-", "sfr-", "fleet-"))
        is_deletion = request_id.startswith("remove-")
        logger.debug(f"Request type - creation: {is_creation}, deletion: {is_deletion}")
        
        if not (is_creation or is_deletion):
            logger.error(f"Request should start with 'dir-', 'sfr-', 'fleet-' or 'remove-'. Unable to process request {request_id}.")
            return {
                'status': 'complete_with_error',
                'message': f'Invalid request format: {request_id}',
                'machines': [],
                'requestId': request_id
            }
        
        # Route to appropriate handler
        if is_creation:
            return self._handle_creation_request(request_id)
        else:
            return self._handle_deletion_request(request_id)

    def _handle_creation_request(self, request_id: str) -> Dict[str, Any]:
        """Handle status checking for creation requests"""
        logger.debug(f"Processing creation request: {request_id}")
        
        # For spot fleet requests (sfr) - poll for instances before checking status
        if request_id.startswith("sfr-"):
            logger.debug(f"Polling Spot Fleet instances for {request_id}")
            self._poll_spot_fleet_instances(request_id)
            
        # For EC2 fleet requests (fleet-) - poll only for request type fleets
        elif request_id.startswith("fleet-"):
            logger.debug(f"Checking EC2 Fleet type for {request_id}")
            # Get fleet type from database to determine if we need to poll
            request_data = db_manager.get_request(request_id)
            if request_data:
                fleet_type = request_data.get('fleet_type', 'instant')
                if fleet_type == 'request':
                    logger.debug(f"Polling EC2 Request Fleet instances for {request_id}")
                    self._poll_ec2_fleet_instances(request_id)
                else:
                    logger.debug(f"Skipping polling for EC2 Instant Fleet {request_id}")
            else:
                logger.warning(f"Request data not found for {request_id}")
        
        # Get request data
        request_data = db_manager.get_request(request_id)
        if not request_data:
            logger.error(f"No request data found for {request_id}")
            return {
                'status': 'complete_with_error',
                'message': f'Request not found: {request_id}',
                'machines': [],
                'requestId': request_id
            }
        
        machines = request_data.get('machines', [])
        logger.debug(f"Found {len(machines)} machines for creation request {request_id}")
        
        # Check if we can determine status from fleet state alone
        if (request_id.startswith("sfr-") or request_id.startswith("fleet-")):
            fleet_status = self._get_fleet_based_status(request_id)
        
            # If fleet state gives us a definitive answer, use it
            if fleet_status:
                logger.debug(f"Using fleet-level status for {request_id}: {fleet_status['status']}")
                return {
                    'status': fleet_status['status'],
                    'message': fleet_status['message'],
                    'machines': [],  # No machine details when using fleet-level status
                    'requestId': request_id
                }
            
            # Check for fleet requests with no machines and apply timeout
            if not machines:
                request_creation_time = request_data.get('time', 0)
                current_time = int(datetime.now().timestamp() * 1000)
                request_age_minutes = (current_time - request_creation_time) / 60000
                
                # If request is too old without any machines, mark as failed
                if request_age_minutes > 30:  # 30-minute timeout
                    logger.warning(f"Fleet request {request_id} is {request_age_minutes:.1f} minutes old with no machines - marking as failed")
                    return {
                        'status': 'complete_with_error',
                        'message': f'Fleet request timed out after {request_age_minutes:.1f} minutes with no instances launched',
                        'machines': [],
                        'requestId': request_id
                    }
                else:
                    # Still within timeout window
                    logger.debug(f"Fleet request {request_id} is {request_age_minutes:.1f} minutes old with no machines - keeping as running")
                    return {
                        'status': 'running',
                        'message': f'Fleet request processing ({request_age_minutes:.1f} minutes) - no instances launched yet',
                        'machines': [],
                        'requestId': request_id
                    }
        
        # Process creation machines
        return self._process_creation_machines(request_id, machines)

    def _handle_deletion_request(self, request_id: str) -> Dict[str, Any]:
        """Handle status checking for deletion requests"""
        logger.debug(f"Processing deletion request: {request_id}")
        
        # Get machines for deletion request
        machines = db_manager.get_machines_for_return(request_id)
        logger.debug(f"Found {len(machines)} machines for deletion request {request_id}")
        
        if not machines:
            logger.debug(f"No machines found for request {request_id}")
            return {
                'status': 'complete',
                'message': f'No machines found for request {request_id}',
                'machines': [],
                'requestId': request_id
            }
        
        # Process deletion machines
        return self._process_deletion_machines(request_id, machines)

    def _process_creation_machines(self, request_id: str, machines: List[Dict]) -> Dict[str, Any]:
        """Core function to process machine statuses for creation requests"""
        # BULK OPERATION: Get all instance details at once
        instance_ids = [machine.get('machineId', '') for machine in machines if machine.get('machineId')]
        logger.debug(f"Getting bulk details for {len(instance_ids)} instances")
        bulk_details = self.get_instance_details_bulk(instance_ids)
        logger.debug(f"Bulk details retrieved: {list(bulk_details.keys())}")
        
        # Process each machine
        updates = []
        all_complete = True
        any_failed = False
        updated_machines = []
        
        for machine in machines:
            instance_id = machine.get('machineId', '')
            if not instance_id:
                logger.debug("Skipping machine without instance ID")
                continue
                
            instance_details = bulk_details.get(instance_id, {})
            current_aws_state = instance_details.get('state', 'unknown')
            
            logger.debug(f"Instance {instance_id}: Current DB status={machine.get('status')}, Current AWS state={current_aws_state}")
            
            # Clone machine for updates - preserve AWS state as status
            updated_machine = machine.copy()
            updated_machine['status'] = current_aws_state
            
            # Determine request_id for this machine
            machine_request_id = updated_machine.get('reqId', updated_machine.get('requestId', request_id))
            
            # Prepare update
            update = {
                'request_id': machine_request_id,
                'machine_id': instance_id,
                'status': current_aws_state
            }
            
            # Handle creation request
            if current_aws_state == 'unknown':
                # Check if we should timeout unknown instances
                current_time = int(time.time())
                launch_time = machine.get('launchtime', 0)
                if launch_time == 0:
                    launch_time = current_time
                unknown_duration_minutes = (current_time - launch_time) / 60
                
                if unknown_duration_minutes > 30:  # Shorter timeout for unknown state
                    update['result'] = 'fail'
                    update['message'] = f'Instance in unknown state for {unknown_duration_minutes:.1f} minutes - assuming failed'
                    update['status'] = 'failed'  # Override AWS state
                    any_failed = True
                else:
                    update['result'] = 'executing'
                    update['message'] = 'Instance state unknown - retrying'
                    all_complete = False
                    
            elif current_aws_state == 'pending':
                # Check if instance has been pending for too long
                current_time = int(time.time())
                launch_time = machine.get('launchtime', 0)
                if launch_time == 0:
                    launch_time = current_time
                pending_duration_minutes = (current_time - launch_time) / 60
                
                if pending_duration_minutes > 60:  # More than 60 minutes
                    update['result'] = 'fail'
                    update['message'] = f'Instance stuck in pending state for {pending_duration_minutes:.1f} minutes - timeout exceeded'
                    update['status'] = 'failed'
                    any_failed = True
                else:
                    update['result'] = 'executing'
                    update['message'] = f'Instance is pending ({pending_duration_minutes:.1f} minutes)'
                    all_complete = False
                    
            elif current_aws_state == 'running':
                update['result'] = 'succeed'
                update['message'] = 'Instance running successfully'
                
                # Add network info if available
                if instance_details.get('privateIpAddress'):
                    update['private_ip'] = instance_details['privateIpAddress']
                    updated_machine['privateIpAddress'] = instance_details['privateIpAddress']
                if instance_details.get('publicIpAddress'):
                    update['public_ip'] = instance_details['publicIpAddress']
                    updated_machine['publicIpAddress'] = instance_details['publicIpAddress']
                if instance_details.get('publicDnsName'):
                    update['public_dns'] = instance_details['publicDnsName']
                    updated_machine['publicDnsName'] = instance_details['publicDnsName']
                if instance_details.get('name'):
                    update['name'] = instance_details['name']
                    updated_machine['name'] = instance_details['name']
                if instance_details.get('lifecycle'):
                    update['lifecycle'] = instance_details['lifecycle']
                    updated_machine['lifeCycleType'] = instance_details['lifecycle']
                
                # InstanceId tagging for running instances
                if self.instance_id_tag_enabled and not machine.get('tagInstanceId', False):
                    # Schedule tagging in background
                    self._tag_instance_with_instance_id(instance_id)
                    update['tag_instance_id'] = True
                    updated_machine['tagInstanceId'] = True
                    
            else:  # stopping, stopped, shutting-down, terminated, etc.
                update['result'] = 'fail'
                update['message'] = f'Instance creation failed: {current_aws_state}'
                any_failed = True
            
            # Add to updates list
            updates.append(update)
            
            # Update response machine object
            updated_machine['result'] = update.get('result', machine.get('result'))
            updated_machine['message'] = update.get('message', machine.get('message'))
            
            updated_machines.append(updated_machine)
        
        # Apply updates to database
        if updates:
            logger.debug(f"Performing batch update for {len(updates)} machines")
            batch_result = db_manager.update_machines(updates)
            logger.debug(f"Batch update result: {batch_result}")
        
        # Build final response
        return self._build_final_response(request_id, updated_machines, all_complete, any_failed)

    def _process_deletion_machines(self, request_id: str, machines: List[Dict]) -> Dict[str, Any]:
        """Core function to process machine statuses for deletion requests"""
        # BULK OPERATION: Get all instance details at once
        instance_ids = [machine.get('machineId', '') for machine in machines if machine.get('machineId')]
        logger.debug(f"Getting bulk details for {len(instance_ids)} instances")
        bulk_details = self.get_instance_details_bulk(instance_ids)
        logger.debug(f"Bulk details retrieved: {list(bulk_details.keys())}")
        
        # Process each machine
        updates = []
        all_complete = True
        any_failed = False
        updated_machines = []
        machines_to_remove = []  # Track machines to remove for deletion requests
        
        for machine in machines:
            instance_id = machine.get('machineId', '')
            if not instance_id:
                logger.debug("Skipping machine without instance ID")
                continue
                
            instance_details = bulk_details.get(instance_id, {})
            current_aws_state = instance_details.get('state', 'unknown')
            
            logger.debug(f"Instance {instance_id}: Current DB status={machine.get('status')}, Current AWS state={current_aws_state}")
            
            # Clone machine for updates - preserve AWS state as status
            updated_machine = machine.copy()
            updated_machine['status'] = current_aws_state
            
            # Determine request_id for this machine
            machine_request_id = updated_machine.get('reqId', updated_machine.get('requestId', request_id))
            
            # Prepare update
            update = {
                'request_id': machine_request_id,
                'machine_id': instance_id,
                'status': current_aws_state
            }
            
            # Handle deletion request
            if current_aws_state == 'shutting-down':
                update['result'] = 'executing'
                update['message'] = 'Instance is being terminated'
                update['return_id'] = request_id
                all_complete = False
                
            elif current_aws_state == 'terminated':
                update['result'] = 'succeed'
                update['message'] = 'Instance terminated successfully'
                update['return_id'] = request_id
                # Mark for removal from database
                machines_to_remove.append({
                    'request_id': machine_request_id,
                    'machine_id': instance_id
                })
                    
            elif current_aws_state == 'running':
                update['result'] = 'fail'
                update['message'] = 'Instance still running - termination may have failed'
                update['return_id'] = request_id
                any_failed = True
                all_complete = False
                
            else:  # pending, stopping, stopped, etc
                update['result'] = 'fail'
                update['message'] = f'Instance termination failed: {current_aws_state}'
                update['return_id'] = request_id
                any_failed = True
                all_complete = False
            
            # Add to updates list
            updates.append(update)
            
            # Update response machine object
            updated_machine['result'] = update.get('result', machine.get('result'))
            updated_machine['message'] = update.get('message', machine.get('message'))
            if 'return_id' in update:
                updated_machine['retId'] = update['return_id']
            
            updated_machines.append(updated_machine)
        
        # Apply updates to database
        if updates:
            logger.debug(f"Performing batch update for {len(updates)} machines")
            batch_result = db_manager.update_machines(updates)
            logger.debug(f"Batch update result: {batch_result}")
        
        # Handle machine removal for deletion requests
        if machines_to_remove:
            self._remove_terminated_machines(machines_to_remove)
        
        # Build final response
        return self._build_final_response(request_id, updated_machines, all_complete, any_failed)

    def _build_final_response(self, request_id: str, updated_machines: List[Dict], 
                            all_complete: bool, any_failed: bool) -> Dict[str, Any]:
        """Common function to build final response for both creation and deletion"""
        # Determine overall request status for response
        if all_complete:
            final_status = 'complete_with_error' if any_failed else 'complete'
            message = 'Request completed' + (' with errors' if any_failed else ' successfully')
        else:
            final_status = 'running'
            message = 'Request still in progress'
        
        logger.debug(f"Request {request_id} final status: {final_status}, all_complete: {all_complete}, any_failed: {any_failed}")
        
        # Periodic cleanup call
        self.periodic_cleanup()
        
        logger.debug(f"Request: {request_id}, status: {final_status}, total machines: {len(updated_machines)}, machines: {updated_machines}")
        return {
            'status': final_status,
            'machines': updated_machines,
            'message': message,
            'requestId': request_id
        }

    def _remove_terminated_machines(self, machines_to_remove: List[Dict]) -> None:
        """Remove terminated machines from database"""
        logger.debug(f"Removing {len(machines_to_remove)} machines from database")
        # Group removals by request_id for efficiency
        removals_by_request = defaultdict(list)
        
        for removal in machines_to_remove:
            removals_by_request[removal['request_id']].append(removal['machine_id'])
        
        # Cleanup launch template versions BEFORE removing fleet requests
        fleet_requests_to_cleanup = set()
        for req_id in removals_by_request.keys():
            if req_id.startswith('fleet-'):
                fleet_requests_to_cleanup.add(req_id)
        
        # Cleanup BEFORE removing the requests
        for fleet_request_id in fleet_requests_to_cleanup:
            self._cleanup_launch_template_versions_for_fleet(fleet_request_id)
        
        # Remove machines
        for req_id, machine_ids in removals_by_request.items():
            for machine_id in machine_ids:
                db_manager.remove_machine_from_request(req_id, machine_id)
        
        logger.info(f"Removed {len(machines_to_remove)} terminated machines from database")

    def periodic_cleanup(self):
        """Call this periodically to perform cleanup if needed"""
        logger.debug("Starting periodic cleanup check...")
        current_time = time.time()
        
        # Check if it's time for cleanup
        if current_time - self.last_cleanup >= self.cleanup_interval:
            logger.debug("Cleanup interval reached, performing cleanup...")
            stats = db_manager.cleanup_old_data(self.max_request_age)
            self.last_cleanup = current_time
            
            if stats['empty_requests_removed'] > 0 or stats['terminated_machines_removed'] > 0:
                logger.info(f"Periodic cleanup completed: {stats}")
            else:
                logger.debug("Periodic cleanup completed - no data removed")
            
            return stats
        
        logger.debug("Cleanup interval not reached, skipping cleanup")
        return {'empty_requests_removed': 0, 'terminated_machines_removed': 0}
    
    def start_spot_reclaim_monitor(self):
        """Start background monitoring for Spot instance reclaims"""
        logger.debug("Starting Spot instance reclaim monitor")
        
        def monitor_loop():
            while True:
                try:
                    self._check_and_terminate_spot_reclaims()
                    # Check every 2 minutes to catch the 2-minute warning window
                    time.sleep(120)
                except Exception as e:
                    logger.error(f"Error in Spot reclaim monitor: {e}")
                    time.sleep(60)
        
        monitor_thread = threading.Thread(
            target=monitor_loop,
            name='spot_reclaim_monitor',
            daemon=True
        )
        monitor_thread.start()

    def _check_and_terminate_spot_reclaims(self):
        """Main method to check for Spot reclaims and terminate instances"""
        logger.debug("Checking for Spot instance reclaims...")
        
        try:
            # Get all active instances from database
            active_instances = self._get_active_spot_instances()
            
            if not active_instances:
                return
            
            instances_to_terminate = []
            updates = []  # Collect batch updates
            
            for instance_id in active_instances:
                if self._has_spot_termination_notice(instance_id):
                    logger.info(f"Spot instance {instance_id} has reclaim notice, marking for termination")
                    instances_to_terminate.append(instance_id)
                    
                    # Collect update for batch operation
                    machine_info = db_manager.get_request_for_machine(instance_id)
                    if machine_info and machine_info.get('request'):
                        updates.append({
                            'request_id': machine_info['request']['requestId'],
                            'machine_id': instance_id,
                            'status': 'shutting-down',
                            'result': 'executing',
                            'message': 'Spot instance terminated due to AWS reclaim notice',
                            'return_id': f"spot-reclaim-{int(time.time())}"
                        })
            
            # Terminate all instances with reclaim notices
            if instances_to_terminate:
                logger.info(f"Terminating {len(instances_to_terminate)} Spot instances with reclaim notices")
                termination_request_id = self.terminate_instances(instances_to_terminate)
                
                # Update return_id in our collected updates
                for update in updates:
                    update['return_id'] = termination_request_id
                
                # Apply batch update if we have updates
                if updates:
                    batch_result = db_manager.update_machines(updates)
                    logger.debug(f"Batch updated {batch_result['success_count']} spot reclaim instances, failed: {batch_result['failed_count']}")
                        
        except Exception as e:
            logger.error(f"Error in Spot reclaim check: {e}")
            logger.debug(f"Spot reclaim check stack trace:", exc_info=True)

    def _get_active_spot_instances(self) -> List[str]:
        """Get all active Spot instances from database"""
        active_instances = []
        
        try:
            # Get all running instances
            all_requests = db_manager.get_all_requests()
            
            for request in all_requests:
                if 'machines' in request:
                    for machine in request['machines']:
                        if (machine.get('status') in ['running', 'pending'] and 
                            machine.get('machineId')):
                            # Check if it's a Spot instance via EC2 API
                            instance_id = machine['machineId']
                            details = self.get_instance_details(instance_id)
                            if details.get('lifecycle') == 'spot':
                                active_instances.append(instance_id)
            
            logger.debug(f"Found {len(active_instances)} active Spot instances")
            return active_instances
            
        except Exception as e:
            logger.error(f"Error getting active Spot instances: {e}")
            return []

    def _has_spot_termination_notice(self, instance_id: str) -> bool:
        """Check if Spot instance has termination notice using EC2 API"""
        try:
            # Use describe_instances to check for termination indicators
            response = self.ec2.describe_instances(InstanceIds=[instance_id])
            
            if not response['Reservations']:
                return False
                
            instance = response['Reservations'][0]['Instances'][0]
            
            # Check state transition reason for Spot termination
            state_reason = instance.get('StateTransitionReason', '')
            logger.debug(f"Instance {instance_id} state reason: {state_reason}")
            
            # Look for Spot termination indicators
            spot_indicators = [
                'spot instance termination',
                'server.spotinstanceterminationnotice', 
                'marked for termination',
                'instance-termination'
            ]
            
            if any(indicator in state_reason.lower() for indicator in spot_indicators):
                logger.info(f"Instance {instance_id} has Spot termination notice: {state_reason}")
                return True
                
            return False
            
        except ClientError as e:
            if e.response['Error']['Code'] == 'InvalidInstanceID.NotFound':
                logger.debug(f"Instance {instance_id} not found")
            else:
                logger.error(f"Error checking termination notice for {instance_id}: {e}")
            return False
        except Exception as e:
            logger.error(f"Unexpected error checking termination notice for {instance_id}: {e}")
            return False