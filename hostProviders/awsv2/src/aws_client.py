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
from typing import Dict, List, Any
from botocore.exceptions import ClientError
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

logger = logging.getLogger(__name__)

class AWSClient:
    def __init__(self):
        try:
            logger.debug("Initializing AWSClient...")
            # Get region using the config manager instance
            self.region = config_manager.get_region()
            logger.info(f"Using AWS region: {self.region}")
            logger.debug(f"Region configuration loaded: {self.region}")
            
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
            
            # Create clients from the session
            self.ec2 = self.session.client('ec2')
            self.ec2_resource = self.session.resource('ec2')
            logger.debug("EC2 client and resource created")
            
            # Handle custom endpoint if configured
            endpoint_url = config_manager.get_aws_endpoint_url()
            if endpoint_url:
                logger.debug(f"Using custom endpoint URL: {endpoint_url}")
                self.ec2 = self.session.client('ec2', endpoint_url=endpoint_url)
                self.ec2_resource = self.session.resource('ec2', endpoint_url=endpoint_url)
                logger.debug("EC2 clients reconfigured with custom endpoint")
            
            self._test_connection()
            logger.debug("AWS connection test completed successfully")
            
            self.requests = {}
            self.vm_pool = None
            self.min_vm_workers = int(os.getenv('AWS_MIN_WORKERS', '10'))
            self.max_vm_workers = int(os.getenv('AWS_MAX_WORKERS', '50'))
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


    def create_instances(self, template: Dict, count: int) -> str:
            """Create EC2 instances using multithreading"""
            logger.debug(f"Starting create_instances for template {template.get('templateId')}, count: {count}")
            # Check credentials once at the beginning of bulk operation
            self._refresh_credentials_if_needed()
            
            try:
                # Check for Spot Fleet configuration (template-based)
                if template.get('fleetRole'):
                    logger.info(f"Using Spot Fleet for template {template.get('templateId')}")
                    logger.debug(f"Spot Fleet configuration found: fleetRole={template.get('fleetRole')}")
                    result = self._create_spot_fleet(template, count)
                
                # Check for EC2 Fleet configuration - simple boolean check
                elif template.get('ec2FleetConfig'):
                    logger.info(f"Using EC2 Fleet for template {template.get('templateId')}")
                    logger.debug(f"EC2 Fleet configuration found: {template.get('ec2FleetConfig')}")
                    result = self._create_ec2_fleet(template, count)
                
                # Basic template or launch template
                else:
                    logger.info(f"Using Basic configuration for template {template.get('templateId')}")
                    logger.debug("Using basic instance creation method")
                    result = self._create_instances(template, count)
                
                logger.info(f"Result: {result}")
                logger.debug(f"Creation result details: success={result.get('success')}, request_id={result.get('request_id')}")
                
                # Common result processing
                if result and result['success']:
                    request_id = result['request_id']
                    # Store request information
                    self.requests[request_id] = {
                        'type': 'create',
                        'instance_ids': result['instance_ids'],
                        'failed_instances': result.get('failed_instances', []),
                        'status': 'running',
                        'created_at': time.time(),
                        'total_requested': count,
                        'successful': len(result['instance_ids']),
                        'failed': len(result.get('failed_instances', []))
                    }
                    logger.info(f"Request {request_id}: Created {len(result['instance_ids'])}/{count} instances")
                    logger.debug(f"Request {request_id} stored in memory cache")
                    return request_id
                else:
                    if result is None:
                        error_msg = 'Creation method returned None'
                        logger.error("Creation method returned None result")
                    else:
                        error_msg = result.get('error', 'Unknown error')
                        logger.error(f"Creation failed with error: {error_msg}")
                    
                    logger.error(f"Instance creation failed: {error_msg}")
                    raise Exception(f"Instance creation failed: {error_msg}")
            except Exception as e:
                logger.error(f"Unexpected error in create_instances: {e}")
                logger.debug(f"create_instances stack trace:", exc_info=True)
                raise Exception(f"Instance creation failed: {str(e)}")
 
    def _create_instances(self, template: Dict, count: int) -> Dict[str, Any]:
        """Create instances"""
        logger.debug(f"Starting _create_instances for {count} instances")
        
        # Basic ec2 instance creation and launch template 
        # This is the only request where we need to create an request id
        request_id = f"dir-{os.getpid()}-{int(time.time())}"
        logger.debug(f"Starting instance creation request {request_id} for {count} instances")
        # Create request in database first
        db_manager.create_request(
            request_id=request_id,
            template_id=template['templateId'],
            host_allocation_type="direct"
        )
        logger.debug(f"Created request {request_id} in database")
        
        # Initialize thread pool
        self._init_vm_pool()
        
        instance_results = []
        instance_ids = []
        failed_instances = []
        
        bulk_operation_failed = False
        bulk_exception = None
        
        try:
            if self.vm_pool:
                # Use thread pool for concurrent instance creation
                logger.debug(f"Using thread pool for {count} instance creations")
                futures = []
                for i in range(count):
                    future = self.vm_pool.submit(self._create_single_instance, template, i, request_id)
                    futures.append(future)
                    logger.debug(f"Submitted future for instance {i}")
                
                # Wait for all futures to complete
                logger.debug("Waiting for all instance creation futures to complete")
                for future in as_completed(futures):
                    result = future.result()
                    instance_results.append(result)
                    logger.debug(f"Future completed with result: success={result.get('success')}")
                    
                    if result['success']:
                        instance_ids.append(result['instance_id'])
                        logger.info(f"Successfully created instance: {result['instance_id']}")
                    else:
                        failed_instances.append(result)
                        logger.error(f"Failed to create instance: {result['error']}")
            
            else:
                # Fallback to sequential creation
                logger.warning("Using sequential instance creation (thread pool not available). Starting sequential instance creation")
                for i in range(count):
                    logger.debug(f"Sequentially creating instance {i+1}/{count}")
                    result = self._create_single_instance(template, i, request_id)
                    instance_results.append(result)
                    
                    if result['success']:
                        instance_ids.append(result['instance_id'])
                        logger.info(f"Successfully created instance: {result['instance_id']}")
                        logger.debug(f"Sequential instance {result['instance_id']} creation succeeded")
                    else:
                        failed_instances.append(result)
                        logger.error(f"Failed to create instance: {result['error']}")                      
                        logger.debug(f"Sequential instance creation failed - index: {result.get('instance_index')}, error: {result.get('error')}")
        
        except Exception as e:
            logger.error(f"Error during instance creation: {e}")
            logger.debug(f"Bulk creation exception - type: {type(e).__name__}, args: {e.args}")
            logger.debug("Bulk creation stack trace:", exc_info=True)
            bulk_operation_failed = True
            bulk_exception = e
        
        finally:
            # Clean up thread pool for this operation
            if self.vm_pool:
                logger.debug("Shutting down VM pool after instance creation")
                self.vm_pool.shutdown(wait=False)
            
        # Collect any instances that were created before the error
        for result in instance_results:
            if result['success']:
                instance_ids.append(result['instance_id'])

        logger.debug(f"_create_instances completed - successful: {len(instance_ids)}, failed: {len(failed_instances)}")
        return {
            'success': len(instance_ids) > 0,
            'request_id': request_id,
            'instance_ids': instance_ids,
            'failed_instances': failed_instances
        }
                        
    def _create_single_instance(self, template: Dict, instance_index: int, request_id: str) -> Dict[str, Any]:
        """Create a single EC2 instance (thread-safe)"""
        logger.debug(f"Starting _create_single_instance for index {instance_index}, request {request_id}")
        try:                  
            logger.debug(f"Creating instance {instance_index} for request {request_id} with template id: {template.get('templateId')}")
            
            # Build the base parameters
            instances_params = {
                'MinCount': 1,
                'MaxCount': 1
            }
            logger.debug("Base instance parameters set: MinCount=1, MaxCount=1")
            
            # Add launch template OR individual parameters
            launch_template_id = template.get('launchTemplateId')
            if launch_template_id:
                instances_params['LaunchTemplate'] = {
                    'LaunchTemplateId': launch_template_id,
                    'Version': template.get('launchTemplateVersion', '$Default')
                }
                logger.debug(f"Using launch template: {launch_template_id}")
            else:
                instances_params['ImageId'] = template['imageId']
                logger.debug(f"Using ImageId: {template['imageId']}")
            
            # Use helper functions for common parameters
            logger.debug("Building network interfaces...")
            network_interfaces = self._build_network_interfaces(template)
            logger.debug("Building user data...")
            user_data = self._build_user_data(template)
            logger.debug("Building instance tags...")
            instance_tags = self._build_instance_tags(template)
            
            # Build IAM instance profile
            iam_profile = {}
            instance_profile = template.get('instanceProfile')
            if instance_profile:
                iam_profile = {
                    'Arn' if instance_profile.startswith('arn:aws:iam:') else 'Name': instance_profile
                }
                logger.debug(f"Attaching IAM instance profile: {instance_profile}")
                
            # Build placement
            placement = {}
            placement_group = template.get('placementGroupName')
            if placement_group:
                placement['GroupName'] = placement_group
                logger.debug(f"Using placement group: {placement_group}")
                
            tenancy = template.get('tenancy')
            if tenancy and tenancy in ['default', 'dedicated', 'host']:
                placement['Tenancy'] = tenancy
                logger.debug(f"Using tenancy: {tenancy}")
            
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
                logger.debug(f"Using spot instance with max price: {spot_price}")

            # Add optional parameters only if they have values
            if network_interfaces:
                instances_params['NetworkInterfaces'] = network_interfaces
                logger.debug(f"Using NetworkInterfaces parameter: {network_interfaces}")
            else:
                # Fallback to individual network parameters
                security_groups = template.get('securityGroupIds')
                if security_groups:
                    instances_params['SecurityGroupIds'] = security_groups
                    logger.debug(f"Using SecurityGroupIds: {security_groups}")
                
                subnet_id = template.get('subnetId')
                if subnet_id:
                    instances_params['SubnetId'] = subnet_id
                    logger.debug(f"Using SubnetId: {subnet_id}")
                        
            if template.get('keyName'):
                instances_params['KeyName'] = template['keyName']
                logger.debug(f"Using key pair: {template['keyName']}")
            
            if template.get('vmType'):
                instances_params['InstanceType'] = template['vmType']
                logger.debug(f"Using Instance Type: {template['vmType']}")
                
            if template.get('ebsOptimized'):
                instances_params['EbsOptimized'] = template['ebsOptimized']
                logger.debug(f"Setting EBS optimized to: {template['ebsOptimized']}")
                
            if user_data:
                instances_params['UserData'] = user_data
                logger.debug("Using User Data")    
                
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
                logger.debug(f"Added {len(instance_tags)} tags to TagSpecifications")

            if market_options:
                instances_params['InstanceMarketOptions'] = market_options
                logger.debug("Added InstanceMarketOptions")

            if placement:
                instances_params['Placement'] = placement
                logger.debug("Added Placement configuration")

            if iam_profile:
                instances_params['IamInstanceProfile'] = iam_profile
                logger.debug("Added IAM instance profile")

            logger.debug(f"About to call run_instances for instance {instance_index}")
            logger.debug(f"Final instance params keys: {list(instances_params.keys())}")
            response = self.ec2.run_instances(**instances_params)
            instance = response['Instances'][0]
            instance_id = instance.get('InstanceId')
            logger.debug(f"Successfully created instance {instance_id}")
            
            # Use helper for machine data creation
            machine_data = self._create_machine_data(
                instance_id=instance_id,
                template=template,
                request_id=request_id,
                name=instance.get('PrivateDnsName'),
                private_ip=instance.get('PrivateIpAddress', ''),
                public_ip=instance.get('PublicIpAddress', ''),
                public_dns=instance.get('PublicDnsName', '')
            )
            
            db_manager.add_machine_to_request(request_id, machine_data)
            logger.debug(f"Added machine {instance_id} to database")
            
            return {
                'success': True,
                'instance_id': instance_id,
                'instance': instance
            }
            
        except ClientError as e:
            logger.error(f"Failed to create instance {instance_index}: {e}")
            logger.debug(f"ClientError details - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            return {
                'success': False,
                'error': str(e),
                'instance_index': instance_index
            }
        except Exception as e:
            logger.error(f"Unexpected error creating instance {instance_index}: {e}")
            logger.debug(f"Unexpected error stack trace:", exc_info=True)
            return {
                'success': False,
                'error': str(e),
                'instance_index': instance_index
            }

    def _build_user_data(self, template: Dict) -> str:
        """Build user data from template - reusable across all instance types"""
        logger.debug("Building user data from template...")
        user_data = ""
        script_dir = os.path.dirname(os.path.abspath(__file__))
        user_data_file = os.path.join(script_dir, "../scripts/user_data.sh")
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
                        
                        # Always include template ID for identification
                        if template.get('templateId'):
                            exports.append(f"export LSF_TEMPLATE_ID={template.get('templateId')}")
                            logger.debug(f"Added template ID export: {template.get('templateId')}")
                        
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

    def _get_encoded_user_data(self, template: Dict) -> str:
        """Get base64 encoded user data for fleet requests"""
        logger.debug("Getting base64 encoded user data...")
        user_data = self._build_user_data(template)
        if user_data:
            encoded = base64.b64encode(user_data.encode('utf-8')).decode('utf-8')
            logger.debug(f"User data encoded, length: {len(encoded)}")
            return encoded
        logger.debug("No user data to encode")
        return ""
    
    def _build_instance_tags(self, template: Dict) -> List[Dict]:
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
        
        logger.debug(f"Built {len(instance_tags)} instance tags")
        return instance_tags
    
    def _build_network_config(self, template: Dict) -> Dict[str, Any]:
        """Build network configuration from template - reusable across all instance types"""
        logger.debug("Building network configuration...")
        network_config = {}
        
        subnet_id = template.get('subnetId')
        security_groups = template.get('securityGroupIds', [])
        
        if subnet_id:
            network_config = {
                'SubnetId': subnet_id,
                'Groups': security_groups
            }
            logger.debug(f"Network config: SubnetId={subnet_id}, Groups={security_groups}")
        else:
            logger.debug("No subnet ID found in template")
        
        return network_config

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
        
    def _build_machine_data_template(self, template: Dict, request_id: str) -> Dict[str, Any]:
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
            "rcAccount": "default",
            "lifeCycleType": "",
            "reqId": request_id,
            "retId": "",
            "message": "Instance creation initiated",
            "launchtime": int(time.time())
        }
        logger.debug(f"Machine data template: {template_data}")
        return template_data

    def _create_machine_data(self, instance_id: str, template: Dict, request_id: str, 
                            name: str = None, private_ip: str = "", 
                            public_ip: str = "", public_dns: str = "") -> Dict[str, Any]:
        """Create complete machine data for database entry"""
        logger.debug(f"Creating machine data for instance {instance_id}")
        base_data = self._build_machine_data_template(template, request_id)
        
        base_data.update({
            "machineId": instance_id,
            "name": name or f"host-{instance_id}",
            "privateIpAddress": private_ip,
            "publicIpAddress": public_ip,
            "publicDnsName": public_dns
        })        
        logger.debug(f"Complete machine data: {base_data}")
        return base_data

    def _create_spot_fleet(self, template: Dict, count: int) -> Dict[str, Any]:
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
                    'AllocationStrategy': template.get('allocationStrategy', 'lowestPrice'),
                    'LaunchSpecifications': self._build_spot_fleet_launch_specs(template)
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
                host_allocation_type="spotFleet"
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
            logger.error(f"Spot Fleet creation failed: {e}")
            logger.debug(f"Spot Fleet ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            error_request_id = f"sfr-error-{int(time.time())}"            
            return {
                'success': False,
                'error': f"Spot Fleet creation failed: {str(e)}",
                'request_id': error_request_id,
                'instance_ids': [],
                'failed_instances': [{'error_code': e.response['Error']['Code'], 'error_message': e.response['Error']['Message']}]
            }
        except Exception as e:
            logger.error(f"Unexpected error in Spot Fleet creation: {e}")
            logger.debug(f"Spot Fleet unexpected error stack trace:", exc_info=True)
            error_request_id = f"sfr-error-{int(time.time())}"
            return {
                'success': False,
                'error': f"Unexpected error in Spot Fleet creation: {str(e)}",
                'request_id': error_request_id,
                'instance_ids': [],
                'failed_instances': []
            }

    def _build_spot_fleet_launch_specs(self, template: Dict) -> List[Dict]:
        """Build Spot Fleet launch specifications from template parameters"""
        logger.debug("Building Spot Fleet launch specifications...")
        # Use helper functions
        network_config = self._build_network_config(template)
        instance_tags = self._build_instance_tags(template)
        encoded_user_data = self._get_encoded_user_data(template)
        
        launch_spec = {
            'ImageId': template.get('imageId', ''),
            'InstanceType': template.get('vmType', ''),
            'KeyName': template.get('keyName'),
            'UserData': encoded_user_data
        }
        logger.debug("Base launch spec built")
        
        # Add optional parameters
        if network_config.get('SubnetId'):
            launch_spec['NetworkInterfaces'] = [{
                'DeviceIndex': 0,
                'SubnetId': network_config['SubnetId'],
                'Groups': network_config['Groups']
            }]
            logger.debug("Added network interfaces to launch spec")
        
        if instance_tags:
            launch_spec['TagSpecifications'] = [{
                'ResourceType': 'instance',
                'Tags': instance_tags
            }]
            logger.debug("Added tag specifications to launch spec")
        
        # Remove None values
        launch_spec = {k: v for k, v in launch_spec.items() if v is not None}
        logger.debug(f"Final launch spec: {launch_spec}")
        return [launch_spec]

    def _poll_spot_fleet_instances(self, fleet_id: str) -> List[str]:
        """Poll Spot Fleet to launch instances and return instance IDs"""
        logger.debug(f"Polling Spot Fleet instances for {fleet_id}")
        try:
            # Describe spot fleet instances
            response = self.ec2.describe_spot_fleet_instances(
                SpotFleetRequestId=fleet_id
            )
            logger.debug(f"Spot Fleet describe response received")
            
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
                    template_manager = TemplateManager()
                    template = template_manager.get_template(template_id)
                    logger.debug(f"Retrieved template {template_id} for new instances")
                    
                    for instance_id in new_instance_ids:
                        # Use helper for machine data creation
                        machine_data = self._create_machine_data(
                            instance_id=instance_id,
                            template=template,
                            request_id=fleet_id,
                            name=f"host-{instance_id}"
                        )
                        db_manager.add_machine_to_request(fleet_id, machine_data)
                        logger.info(f"Added new Spot Fleet instance {instance_id} to database")
                
                return active_instance_ids
            else:
                logger.debug(f"No active instances found for Spot Fleet {fleet_id}")
                return []
                
        except ClientError as e:
            if e.response['Error']['Code'] == 'InvalidSpotFleetRequestId.NotFound':
                logger.warning(f"Spot Fleet {fleet_id} not found yet")
            else:
                logger.error(f"Error describing spot fleet instances: {e}")
                logger.debug(f"Spot Fleet polling ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            return []

  
    def _create_ec2_fleet(self, template: Dict, count: int) -> Dict[str, Any]:
        """Create instances using EC2 Fleet API - supports both instant and request types"""
        logger.debug(f"Creating EC2 Fleet for {count} instances")
        try:
            logger.info(f"Creating EC2 Fleet for template {template.get('templateId')}")
            
            # Load configuration
            fleet_config = self._load_ec2_fleet_config(template)
            logger.debug("EC2 Fleet configuration loaded successfully")
            
            # Determine fleet type from configuration
            fleet_type = fleet_config.get('Type', 'instant')  # Default to instant if not specified
            logger.info(f"EC2 Fleet type: {fleet_type}")
            
            # Get encoded user data from user_data.sh script
            encoded_user_data = self._get_encoded_user_data(template)
            logger.debug("Encoded user data retrieved")
            
            # Calculate slot-based capacity
            max_number = template.get('maxNumber', 1)
            attributes = template.get('attributes', {})
            ncpus = int(attributes.get('ncpus', ['Numeric', '1'])[1])
            
            total_slots = min(count, max_number * ncpus)
            logger.debug(f"Capacity calculation - max_number: {max_number}, ncpus: {ncpus}, total_slots: {total_slots}")
            
            # Handle on-demand ratio
            ratio = template.get('onDemandTargetCapacityRatio')
            if ratio is not None:
                try:
                    on_demand_slots = int(total_slots * float(ratio))
                    spot_slots = total_slots - on_demand_slots
                    logger.debug(f"On-demand ratio applied: {ratio}, on_demand_slots: {on_demand_slots}, spot_slots: {spot_slots}")
                except (ValueError, TypeError):
                    on_demand_slots = 0
                    spot_slots = total_slots
                    logger.warning(f"Invalid on-demand ratio: {ratio}, using all spot instances")
            else:
                on_demand_slots = 0
                spot_slots = total_slots
                logger.debug("No on-demand ratio specified, using all spot instances")
            
            # Replace placeholder variables in fleet config
            if 'TargetCapacitySpecification' in fleet_config:
                target_spec = fleet_config['TargetCapacitySpecification']
                
                target_spec['TotalTargetCapacity'] = total_slots
                target_spec['OnDemandTargetCapacity'] = on_demand_slots  
                target_spec['SpotTargetCapacity'] = spot_slots
                
                logger.info(f"Set fleet capacity: Total={total_slots}, OnDemand={on_demand_slots}, Spot={spot_slots}")
            
            # Use helper functions for tags
            instance_tags = self._build_instance_tags(template)
            
            # Add fleet-level tags only (instance tags must be in LaunchTemplate)
            if instance_tags:
                fleet_config['TagSpecifications'] = [{
                    'ResourceType': 'fleet',
                    'Tags': instance_tags
                }]
                logger.debug("Added fleet-level tags")
            
            # ALWAYS create temporary launch template versions with all overrides
            if 'LaunchTemplateConfigs' in fleet_config:
                logger.info("Creating temporary launch template versions for EC2 Fleet with all overrides")
                success = self._create_temp_launch_template_versions(fleet_config, template, encoded_user_data)
                if success:
                    logger.info("Successfully created temporary launch template versions with all overrides")
                else:
                    logger.warning("Failed to create temporary launch template versions")
            
            # Create the fleet
            logger.debug(f"Creating EC2 Fleet with config: {fleet_config}")
            response = self.ec2.create_fleet(**fleet_config)
            
            fleet_id = response.get('FleetId')
            logger.info(f"EC2 Fleet created: {fleet_id}, type: {fleet_type}")
            
            # Create request in database with fleet type information
            db_manager.create_request(
                request_id=fleet_id,
                template_id=template['templateId'],
                host_allocation_type="ec2Fleet",
                fleet_type=fleet_type  # Store fleet type for later reference
            )
            logger.debug(f"Created request {fleet_id} in database with fleet type: {fleet_type}")
            
            # Handle different fleet types
            successful_instances = []
            failed_instances = []
            
            if fleet_type == 'instant':
                # Instant fleet - instances are returned immediately
                logger.info("Processing instant fleet instances immediately")
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
                            name=f"host-{instance_id}"
                        )
                        db_manager.add_machine_to_request(fleet_id, machine_data)
                        logger.info(f"Added instant fleet instance {instance_id} to database")
                
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
                logger.info("Request fleet created - instances will be launched asynchronously")
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
            logger.error(f"EC2 Fleet creation failed: {e}")
            logger.debug(f"EC2 Fleet ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            error_request_id = f"fleet-error-{int(time.time())}"
            
            return {
                'success': False,
                'error': str(e),
                'request_id': error_request_id,
                'instance_ids': [],
                'failed_instances': [{'error_code': e.response['Error']['Code'], 'error_message': e.response['Error']['Message']}]
            }
        except Exception as e:
            logger.error(f"Unexpected error in EC2 Fleet creation: {e}")
            logger.debug(f"EC2 Fleet unexpected error stack trace:", exc_info=True)
            error_request_id = f"fleet-error-{int(time.time())}"
            
            return {
                'success': False,
                'error': str(e),
                'request_id': error_request_id,
                'instance_ids': [],
                'failed_instances': []
            }

    def _create_temp_launch_template_versions(self, fleet_config: Dict, template: Dict, encoded_user_data: str) -> bool:
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
                    if template.get('keyName'):
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
                    instance_tags = self._build_instance_tags(template)
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
                    logger.info(f"Creating new version of launch template for config {i}")
                    create_response = self.ec2.create_launch_template_version(**create_version_kwargs)
                    new_version_number = create_response['LaunchTemplateVersion']['VersionNumber']
                    
                    # Update the fleet config to use the specific new version
                    spec['Version'] = str(new_version_number)
                    
                    # Ensure we're using ID for consistency
                    if original_template_id:
                        spec['LaunchTemplateId'] = original_template_id
                        if 'LaunchTemplateName' in spec:
                            del spec['LaunchTemplateName']
                    
                    logger.info(f"Successfully created version {new_version_number} of launch template for config {i}")
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
        
        logger.info(f"Successfully created temporary versions for {successful_creations}/{total_configs} LaunchTemplateConfigs")
        return successful_creations > 0

    def _load_ec2_fleet_config(self, template: Dict) -> Dict[str, Any]:
        """Load EC2 Fleet configuration from file - reuse existing config loading pattern"""
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
                config = json.load(f)
                logger.debug("EC2 Fleet configuration loaded successfully")
                return config
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON in EC2 Fleet configuration: {e}")
            raise ValueError(f"Invalid JSON in EC2 Fleet configuration: {e}")

    def _cleanup_launch_template_versions_for_fleet(self, request_id: str):
        """Clean up temporary launch template versions created for an EC2 Fleet request"""
        logger.debug(f"Starting launch template version cleanup for fleet: {request_id}")
        try:
            logger.info(f"Cleaning up launch template versions for fleet request: {request_id}")
            
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
                                    logger.info(f"Deleted launch template version {version_num} from {lt_name} (was for template {template_id})")
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
            
            logger.info(f"Cleaned up {versions_cleaned} launch template versions for fleet request {request_id}")
            
        except Exception as e:
            logger.error(f"Error during launch template version cleanup for fleet {request_id}: {e}")
            logger.debug(f"Launch template cleanup stack trace:", exc_info=True)
            
    def _poll_ec2_fleet_instances(self, fleet_id: str) -> List[str]:
        """Poll EC2 Fleet to get launched instances - works for request fleets only"""
        logger.debug(f"Polling EC2 Fleet instances for {fleet_id}")
        try:
            # This method is only called for request fleets, so we don't need fleet type checks
            logger.debug(f"Polling request fleet instances for {fleet_id}")
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
                    logger.info(f"Found {len(new_instance_ids)} new instances for EC2 Request Fleet {fleet_id}")
                    
                    # Get template attributes from request data
                    template_id = request_data.get('templateId', 'unknown')
                    template_manager = TemplateManager()
                    template = template_manager.get_template(template_id)
                    logger.debug(f"Retrieved template {template_id} for new instances")
                    
                    for instance_id in new_instance_ids:
                        # Use helper for machine data creation
                        machine_data = self._create_machine_data(
                            instance_id=instance_id,
                            template=template,
                            request_id=fleet_id,
                            name=f"host-{instance_id}"
                        )
                        db_manager.add_machine_to_request(fleet_id, machine_data)
                        logger.info(f"Added new EC2 Request Fleet instance {instance_id} to database")
                
                return active_instance_ids
            else:
                logger.debug(f"No active instances found for EC2 Request Fleet {fleet_id}")
                return []
                
        except ClientError as e:
            if e.response['Error']['Code'] == 'InvalidFleetId.NotFound':
                logger.warning(f"EC2 Fleet {fleet_id} not found yet")
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
       
    def terminate_instances(self, instance_ids: List[str]) -> str:
        """Terminate EC2 instances using multithreading"""
        logger.debug(f"Starting terminate_instances for {len(instance_ids)} instances: {instance_ids}")
        # Check credentials once at the beginning of bulk operation
        self._refresh_credentials_if_needed()
        
        request_id = f"remove-{os.getpid()}-{int(time.time())}"
        logger.debug(f"Starting instance termination request {request_id} for instances: {instance_ids}")
        
        # Initialize thread pool
        self._init_vm_pool()
        
        termination_results = []
        successful_terminations = []
        failed_terminations = []
        
        try:
            if self.vm_pool:
                logger.debug(f"Using thread pool for {len(instance_ids)} instance terminations")
                # Use thread pool for concurrent termination
                futures = [self.vm_pool.submit(self._terminate_single_instance, instance_id, request_id) 
                          for instance_id in instance_ids]
                logger.debug(f"Submitted {len(futures)} termination futures")
                
                for future in as_completed(futures):
                    result = future.result()
                    termination_results.append(result)
                    logger.debug(f"Termination future completed with result: success={result.get('success')}")
                    
                    if result['success']:
                        successful_terminations.append(result['instance_id'])
                        logger.debug(f"Instance {result['instance_id']} termination succeeded")
                    else:
                        failed_terminations.append(result)
                        logger.debug(f"Instance termination failed - id: {result.get('instance_id')}, error: {result.get('error')}")
            
            else:
                # Fallback to sequential termination
                logger.warning("Using sequential instance termination")
                logger.debug("Starting sequential instance termination")
                for instance_id in instance_ids:
                    logger.debug(f"Sequentially terminating instance {instance_id}")
                    result = self._terminate_single_instance(instance_id, request_id)
                    termination_results.append(result)
                    
                    if result['success']:
                        successful_terminations.append(instance_id)
                        logger.debug(f"Sequential termination of {instance_id} succeeded")
                    else:
                        failed_terminations.append(result)
                        logger.debug(f"Sequential termination failed - id: {result.get('instance_id')}, error: {result.get('error')}")
        
        except Exception as e:
            logger.error(f"Error during instance termination: {e}")
            logger.debug(f"Bulk termination exception - type: {type(e).__name__}, args: {e.args}")
            logger.debug("Bulk termination stack trace:", exc_info=True)
        
        # Store request information
        self.requests[request_id] = {
            'type': 'terminate',
            'instance_ids': instance_ids,
            'successful_terminations': successful_terminations,
            'failed_terminations': failed_terminations,
            'status': 'running',
            'created_at': time.time()
        }
        
        logger.info(f"Request {request_id}: Terminated {len(successful_terminations)}/{len(instance_ids)} instances")
        logger.debug(f"Termination request {request_id} completed - successful: {len(successful_terminations)}, failed: {len(failed_terminations)}")
        return request_id
    
    def _terminate_single_instance(self, instance_id: str, return_id: str) -> Dict[str, Any]:
        """Terminate a single instance (thread-safe) - only update status, don't remove"""
        logger.debug(f"Starting _terminate_single_instance for {instance_id} with return_id {return_id}")
        try:
            logger.debug(f"Terminating instance {instance_id} with return_id {return_id}")
            response = self.ec2.terminate_instances(InstanceIds=[instance_id])
            logger.debug(f"Termination response for {instance_id}: {response}")
            
            # Update machine status to shutting-down but don't remove from DB
            machine_info = db_manager.get_request_for_machine(instance_id)
            if machine_info:
                logger.debug(f"Found machine info for {instance_id}, updating status to shutting-down")
                db_manager.update_machine_status(
                    machine_info['request']['requestId'],
                    instance_id,
                    "shutting-down",
                    "executing",
                    "Instance termination initiated",
                    return_id
                )
            else:
                logger.warning(f"No machine info found for instance {instance_id}")
            
            return {
                'success': True,
                'instance_id': instance_id,
                'response': response
            }
        except ClientError as e:
            logger.error(f"Failed to terminate instance {instance_id}: {e}")
            logger.debug(f"Termination ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            return {
                'success': False,
                'instance_id': instance_id,
                'error': str(e)
            }
        except Exception as e:
            logger.error(f"Unexpected error terminating instance {instance_id}: {e}")
            logger.debug(f"Termination exception - type: {type(e).__name__}, args: {e.args}")
            logger.debug("Termination stack trace:", exc_info=True)
            return {
                'success': False,
                'instance_id': instance_id,
                'error': str(e)
            }
    
    def check_terminated_instances(self, instance_ids: List[str]) -> Dict[str, Any]:
        """Check if instances are terminated - return consistent format"""
        logger.debug(f"Starting check_terminated_instances for {len(instance_ids)} instances")
        try:
            logger.debug(f"Checking terminated instances: {instance_ids}")
            terminated_ids = self._find_terminated_instances(instance_ids)
            logger.debug(f"Found terminated instances: {terminated_ids}")
            
            requests = []
            for instance_id in terminated_ids:
                machine_info = db_manager.get_request_for_machine(instance_id)
                machine_name = machine_info['machine'].get('name', f'host-{instance_id}') if machine_info and machine_info.get('machine') else f'unknown-{instance_id}'
                
                requests.append({
                    "machine": machine_name,
                    "machineId": instance_id
                })
                logger.debug(f"Added terminated instance to results: {instance_id}")
            
            # Return consistent format with other methods
            result = {
                "status": "complete",
                "message": f"Found {len(requests)} terminated instances" if requests else "No terminated instances found",
                "requests": requests,
                "requestId": f"check-terminated-{os.getpid()}-{int(time.time())}"  # Add requestId for consistency
            }
            logger.debug(f"check_terminated_instances result: {result}")
            return result
            
        except Exception as e:
            logger.error(f"Error in check_terminated_instances: {e}")
            logger.debug(f"check_terminated_instances exception - type: {type(e).__name__}, args: {e.args}")
            logger.debug("check_terminated_instances stack trace:", exc_info=True)
            return {
                "status": "complete_with_error",
                "message": str(e),
                "requests": [],
                "requestId": ""
            }

    def _find_terminated_instances(self, instance_ids: List[str]) -> List[str]:
        """Internal method to find terminated instances"""
        logger.debug(f"_find_terminated_instances called with: {instance_ids}")
        terminated = []
        logger.debug(f"_find_terminated_instances called with: {instance_ids}")
        
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
                    
                    # Update database
                    machine_info = db_manager.get_request_for_machine(instance_id)
                    if machine_info and machine_info.get('request'):
                        state = details.get('state')
                        logger.debug(f"Updating database for instance {instance_id} to state: {state}")
                        db_manager.update_machine_status(
                            machine_info['request']['requestId'], 
                            instance_id, 
                            state,
                            "succeed" if state == 'terminated' else "executing",
                            f"Instance {state} by cloud provider"
                        )
        
        except Exception as e:
            logger.error(f"Error in _find_terminated_instances: {e}")
            logger.debug(f"_find_terminated_instances exception - type: {type(e).__name__}, args: {e.args}")
            logger.debug("_find_terminated_instances stack trace:", exc_info=True)
            # Fallback to individual checks
            logger.debug("Falling back to individual instance checks")
            for instance_id in instance_ids:
                try:
                    details = self.get_instance_details(instance_id, retries=0)
                    logger.debug(f"Individual check for {instance_id}: {details.get('state')}")
                    if details.get('state') == 'terminated':
                        terminated.append(instance_id)
                        logger.debug(f"Added {instance_id} to terminated list via fallback")
                except Exception:
                    logger.debug(f"Individual check failed for {instance_id}")
                    continue
        
        logger.debug(f"_find_terminated_instances returning: {terminated}")
        return terminated

    def get_instance_details(self, instance_id: str, retries: int = 2) -> Dict[str, Any]:
        """Get instance details with retry logic for unknown states"""
        logger.debug(f"get_instance_details called for {instance_id} with {retries} retries")
        for attempt in range(retries + 1):
            try:
                logger.debug(f"Attempt {attempt + 1} for instance {instance_id}")
                instances = self.ec2_resource.instances.filter(InstanceIds=[instance_id])
                instance = list(instances)[0]
                logger.debug(f"Instance details: {instance.meta.data}")
                state = instance.state['Name']
                logger.debug(f"Instance {instance_id} state: {state}")
                lifecycle = 'on-demand'  # Default to on-demand
                try:
                    # Check if instance_lifecycle attribute exists and has a value
                    if hasattr(instance, 'instance_lifecycle') and instance.instance_lifecycle:
                        lifecycle = instance.instance_lifecycle
                    # Alternative check for spot instances
                    elif instance.instance_type.startswith('spot') or getattr(instance, 'spot_instance_request_id', None):
                        lifecycle = 'spot'
                    logger.debug(f"Instance {instance_id} lifecycle: {lifecycle}")
                except Exception as e:
                    logger.debug(f"Could not determine lifecycle for {instance_id}: {e}")
                    lifecycle = 'on-demand'
                
                if state != 'unknown' or attempt == retries:
                    result = {
                        'state': state,
                        'privateIpAddress': instance.private_ip_address,
                        'publicIpAddress': instance.public_ip_address,
                        'name': instance.private_dns_name,
                        'publicDnsName': instance.public_dns_name,
                        'launchtime': instance.launch_time.timestamp() if instance.launch_time else None,
                        'lifecycle': lifecycle
                    }
                    logger.debug(f"Returning instance details: {result}")
                    return result
                
                # Wait before retrying for unknown state
                wait_time = 2 ** attempt
                logger.debug(f"Unknown state, waiting {wait_time}s before retry")
                time.sleep(wait_time)
                
            except ClientError as e:
                if attempt == retries:
                    logger.error(f"Failed to get instance details after {retries} retries: {e}")
                    logger.debug(f"Final ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
                    return {
                        'state': 'unknown'
                    }
                wait_time = 2 ** attempt
                logger.debug(f"ClientError, waiting {wait_time}s before retry")
                time.sleep(wait_time)
        
        logger.debug(f"get_instance_details returning unknown state for {instance_id}")
        return {
            'state': 'unknown'
        }
        
    def get_instance_details_bulk(self, instance_ids: List[str], chunk_size: int = 100, max_retries: int = 2) -> Dict[str, Dict[str, Any]]:
        """Get details for multiple instances with built-in retry logic"""
        logger.debug(f"get_instance_details_bulk called for {len(instance_ids)} instances, chunk_size={chunk_size}, max_retries={max_retries}")
        if not instance_ids:
            logger.debug("No instance IDs provided to get_instance_details_bulk")
            return {}
        
        result = {}
        instances_to_process = set(instance_ids)
        logger.debug(f"Instances to process: {instances_to_process}")
        
        # Process with retries
        for attempt in range(max_retries + 1):
            if not instances_to_process:
                logger.debug("No more instances to process")
                break
                
            logger.debug(f"Bulk attempt {attempt + 1}/{max_retries + 1}, instances remaining: {len(instances_to_process)}")
            # Process in chunks
            current_batch = list(instances_to_process)
            for i in range(0, len(current_batch), chunk_size):
                chunk = current_batch[i:i + chunk_size]
                logger.debug(f"Processing chunk {i//chunk_size + 1}, size: {len(chunk)}")
                
                try:
                    instances = self.ec2_resource.instances.filter(InstanceIds=chunk)
                    found_instance_ids = set()
                    
                    for instance in instances:
                        found_instance_ids.add(instance.id)
                        state = instance.state['Name']
                        lifecycle = 'on-demand'  # Default to on-demand
                        try:
                            if hasattr(instance, 'instance_lifecycle') and instance.instance_lifecycle:
                                lifecycle = instance.instance_lifecycle
                            elif instance.instance_type.startswith('spot') or getattr(instance, 'spot_instance_request_id', None):
                                lifecycle = 'spot'
                            logger.debug(f"Instance {instance.id} lifecycle: {lifecycle}")
                        except Exception as e:
                            logger.debug(f"Could not determine lifecycle for {instance.id}: {e}")
                            lifecycle = 'on-demand'
                        logger.debug(f"Instance details: {instance.meta.data}")
                        result[instance.id] = {
                            'state': state,
                            'privateIpAddress': instance.private_ip_address,
                            'publicIpAddress': instance.public_ip_address,
                            'name': instance.private_dns_name,
                            'publicDnsName': instance.public_dns_name,
                            'launchtime': instance.launch_time.timestamp() if instance.launch_time else None,
                            'lifecycle': lifecycle,
                            'source': f'bulk-attempt-{attempt}'
                        }
                        logger.debug(f"Result dictionary contents: {result}")
                        
                        # Remove from processing if not unknown (or if final attempt)
                        if state != 'unknown' or attempt == max_retries:
                            instances_to_process.discard(instance.id)
                            logger.debug(f"Removed {instance.id} from processing (state: {state})")
                    
                    # Handle missing instances (terminated)
                    missing_in_chunk = set(chunk) - found_instance_ids
                    logger.debug(f"Missing instances in chunk: {missing_in_chunk}")
                    for missing_id in missing_in_chunk:
                        result[missing_id] = {
                            'state': 'terminated',
                            'privateIpAddress': None,
                            'publicIpAddress': None,
                            'name': None,
                            'publicDnsName': None,
                            'launchtime': None,
                            'lifecycle': None,
                            'source': f'bulk-missing-attempt-{attempt}'
                        }
                        instances_to_process.discard(missing_id)
                        logger.debug(f"Marked missing instance {missing_id} as terminated")
                        
                except ClientError as e:
                    logger.warning(f"Bulk attempt {attempt} failed for chunk: {e}")
                    logger.debug(f"Bulk ClientError - code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
                    # Keep these instances in processing for retry
            
            # Exponential backoff before next retry
            if instances_to_process and attempt < max_retries:
                sleep_time = 2 ** attempt
                logger.debug(f"Waiting {sleep_time}s before bulk retry {attempt + 1}")
                time.sleep(sleep_time)
        
        # Final fallback for any remaining instances
        if instances_to_process:
            logger.debug(f"Final fallback for {len(instances_to_process)} instances")
            for instance_id in instances_to_process:
                individual_result = self.get_instance_details(instance_id, retries=0)
                result[instance_id] = {
                    'state': individual_result['state'],
                    'privateIpAddress': individual_result['privateIpAddress'],
                    'publicIpAddress': individual_result['publicIpAddress'],
                    'name': individual_result['name'],
                    'publicDnsName': individual_result['publicDnsName'],
                    'launchtime': individual_result['launchtime'],
                    'lifecycle': individual_result.get('lifecycle', 'unknown'),
                    'source': 'individual-fallback'
                }
                logger.debug(f"Fallback result for {instance_id}: {individual_result['state']}")
        
        logger.debug(f"get_instance_details_bulk returning {len(result)} results")
        return result

    def get_request_status(self, request_id: str) -> Dict[str, Any]:
        """Get request status with proper state transition handling"""
        logger.debug(f"get_request_status called for request: {request_id}")
        
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
            
        # Get machines based on request type
        machines = []
        request_data = None
        
        if is_creation:
            # For creation requests, get the full request object
            request_data = db_manager.get_request(request_id)
            if request_data:
                machines = request_data.get('machines', [])
                logger.debug(f"Found {len(machines)} machines for creation request {request_id}")
        else:
            # For deletion requests, get machines by retId
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
        
        # BULK OPERATION: Get all instance details at once
        instance_ids = [machine.get('machineId', '') for machine in machines if machine.get('machineId')]
        logger.debug(f"Getting bulk details for {len(instance_ids)} instances")
        bulk_details = self.get_instance_details_bulk(instance_ids, max_retries=3)
        logger.debug(f"Bulk details retrieved: {list(bulk_details.keys())}")
        
        # Process each machine - preserve AWS state values
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
            
            # Handle unknown state first
            if current_aws_state == 'unknown':
                updated_machine['status'] = 'unknown'
                
                if is_creation:
                    # For creation requests, check if we should timeout unknown instances
                    current_time = int(time.time())
                    launch_time = machine.get('launchtime', 0)
                    if launch_time == 0:
                        # Use current time as fallback (shouldn't happen, but safety first)
                        launch_time = current_time
                        logger.warning(f"Missing launch time for instance {instance_id}, using current time")
                    unknown_duration_minutes = (current_time - launch_time) / 60
                    logger.debug(f"Instance {instance_id} unknown for {unknown_duration_minutes:.1f} minutes")
                    
                    if unknown_duration_minutes > 30:  # Shorter timeout for unknown state
                        updated_machine['result'] = 'fail'
                        updated_machine['message'] = f'Instance in unknown state for {unknown_duration_minutes:.1f} minutes - assuming failed'
                        updated_machine['status'] = 'failed'
                        any_failed = True
                        
                        # Try to terminate to clean up
                        try:
                            self.ec2.terminate_instances(InstanceIds=[instance_id])
                            updated_machine['message'] += ' - termination attempted'
                            logger.warning(f"Attempted to terminate unknown instance {instance_id}")
                        except Exception as e:
                            logger.error(f"Failed to terminate unknown instance {instance_id}: {e}")
                    else:
                        updated_machine['result'] = 'executing'
                        updated_machine['message'] = 'Instance state unknown - retrying'
                        all_complete = False
                        
                else:  # deletion request
                    # For deletion, unknown state usually means already terminated or never existed
                    updated_machine['result'] = 'succeed'  # Assume successful termination
                    updated_machine['message'] = 'Instance not found - assumed terminated'
                    updated_machine['status'] = 'terminated'
            
            else:
                # Normal state handling - use AWS state as status
                updated_machine['status'] = current_aws_state
                
                if is_creation:
                    # Handle creation request result determination
                    if current_aws_state == 'pending':
                        # Check if instance has been pending for too long (timeout)
                        current_time = int(time.time())
                        launch_time = machine.get('launchtime', 0)
                        if launch_time == 0:
                            # Use current time as fallback (shouldn't happen, but safety first)
                            launch_time = current_time
                            logger.warning(f"Missing launch time for instance {instance_id}, using current time")
                        pending_duration_minutes = (current_time - launch_time) / 60
                        logger.debug(f"Instance {instance_id} pending for {pending_duration_minutes:.1f} minutes")
                        
                        if pending_duration_minutes > 60:  # More than 60 minutes
                            # Instance is stuck in pending state - mark as failed
                            updated_machine['result'] = 'fail'
                            updated_machine['message'] = f'Instance stuck in pending state for {pending_duration_minutes:.1f} minutes - timeout exceeded'
                            updated_machine['status'] = 'failed'  # Override AWS state since it's stuck
                            any_failed = True
                            
                            # Automatically terminate the stuck instance
                            try:
                                self.ec2.terminate_instances(InstanceIds=[instance_id])
                                updated_machine['message'] += ' - instance terminated due to timeout'
                                logger.warning(f"Terminated stuck instance {instance_id} after {pending_duration_minutes:.1f} minutes in pending state")
                            except Exception as e:
                                logger.error(f"Failed to terminate stuck instance {instance_id}: {e}")
                                updated_machine['message'] += ' - failed to terminate instance'
                                
                        else:
                            # Still within timeout window, keep as executing
                            updated_machine['result'] = 'executing'
                            updated_machine['message'] = f'Instance is pending ({pending_duration_minutes:.1f} minutes)'
                            all_complete = False
                            
                    elif current_aws_state == 'running':
                        updated_machine['result'] = 'succeed'
                        updated_machine['message'] = 'Instance running successfully'
                        # Update machine properties if available
                        if instance_details.get('name'):
                            updated_machine['name'] = instance_details['name']
                        if instance_details.get('privateIpAddress'):
                            updated_machine['privateIpAddress'] = instance_details['privateIpAddress']
                        if instance_details.get('publicIpAddress'):
                            updated_machine['publicIpAddress'] = instance_details['publicIpAddress']
                        if instance_details.get('publicDnsName'):
                            updated_machine['publicDnsName'] = instance_details['publicDnsName']
                        if instance_details.get('lifecycle'):
                            updated_machine['lifeCycleType'] = instance_details['lifecycle']
                        logger.debug(f"Instance {instance_id} is running successfully")
                    else:  # stopping, stopped, shutting-down, terminated, etc.
                        updated_machine['result'] = 'fail'
                        updated_machine['message'] = f'Instance creation failed: {current_aws_state}'
                        any_failed = True
                        logger.debug(f"Instance {instance_id} creation failed with state: {current_aws_state}")
                        
                else:  # deletion request
                    # Handle deletion request result determination
                    if current_aws_state == 'shutting-down':
                        updated_machine['result'] = 'executing'
                        updated_machine['message'] = 'Instance is being terminated'
                        all_complete = False
                        logger.debug(f"Instance {instance_id} is shutting down")
                        
                    elif current_aws_state == 'terminated':
                        updated_machine['result'] = 'succeed'
                        updated_machine['message'] = 'Instance terminated successfully'
                        # Mark for removal from database
                        machines_to_remove.append({
                            'request_id': updated_machine.get('reqId', ''),
                            'machine_id': instance_id
                        })
                        logger.debug(f"Instance {instance_id} terminated successfully, marked for removal")
                            
                    elif current_aws_state == 'running':
                        # Instance still running - termination may have failed or not started
                        updated_machine['result'] = 'fail'
                        updated_machine['message'] = 'Instance still running - termination may have failed'
                        any_failed = True
                        all_complete = False
                        logger.debug(f"Instance {instance_id} still running after termination request")
                        
                    else:  # pending, stopping, stopped, etc
                        updated_machine['result'] = 'fail'
                        updated_machine['message'] = f'Instance termination failed: {current_aws_state}'
                        any_failed = True
                        all_complete = False
                        logger.debug(f"Instance {instance_id} termination failed with state: {current_aws_state}")
            
            # Update database if status result, or network info changed
            status_changed = updated_machine['status'] != machine.get('status')
            result_changed = updated_machine['result'] != machine.get('result')
            lifecycle_changed = updated_machine.get('lifeCycleType') != machine.get('lifeCycleType')
            network_info_changed = (
                updated_machine.get('privateIpAddress') != machine.get('privateIpAddress') or
                updated_machine.get('publicIpAddress') != machine.get('publicIpAddress') or
                updated_machine.get('publicDnsName') != machine.get('publicDnsName') or
                updated_machine.get('name') != machine.get('name')
            )
            
            if (status_changed or result_changed or network_info_changed or lifecycle_changed):
                logger.debug(f"Updating database for instance {instance_id} - status_changed: {status_changed}, result_changed: {result_changed}, network_info_changed: {network_info_changed}, lifecycle_changed: {lifecycle_changed}")
                db_manager.update_machine_status(
                    updated_machine.get('reqId', ''),
                    instance_id,
                    updated_machine['status'],
                    updated_machine['result'],
                    updated_machine['message'],
                    updated_machine.get('retId', '')
                )
                # Also update the network-specific fields if they changed
                if network_info_changed or lifecycle_changed:
                    logger.debug(f"Updating network info for instance {instance_id}")
                    db_manager.update_machine_network_info(
                        updated_machine.get('reqId', ''),
                        instance_id,
                        updated_machine.get('privateIpAddress'),
                        updated_machine.get('publicIpAddress'),
                        updated_machine.get('publicDnsName'),
                        updated_machine.get('name'),
                        updated_machine.get('lifeCycleType')
                    )
            
            updated_machines.append(updated_machine)
        
        # Handle request status update for creation requests
        if is_creation and request_data:
            # Update the overall request status in database
            final_status = 'complete_with_error' if any_failed else 'complete' if all_complete else 'running'
        
        # Handle machine removal for deletion requests
        if is_deletion and machines_to_remove:
            logger.debug(f"Removing {len(machines_to_remove)} machines from database")
            for machine_info in machines_to_remove:
                db_manager.remove_machine_from_request(
                    machine_info['request_id'],
                    machine_info['machine_id']
                )
            logger.info(f"Removed {len(machines_to_remove)} terminated machines from database")
        
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
        
        return {
            'status': final_status,
            'machines': updated_machines,
            'message': message,
            'requestId': request_id
        }

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