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
import multiprocessing
import math
import time
import os
import threading
import random
from datetime import datetime, timedelta, timezone
from typing import Dict, List, Any, Optional
from botocore.exceptions import ClientError
from botocore.config import Config
import subprocess
import logging
import json
import base64
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import contextmanager
from utils import get_data_path
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
                key_id = self.credentials.get('aws_access_key_id') or ''
                logger.debug(f"Creating session with credentials - Access Key: {key_id[:8]}")
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
                self.ec2 = self.session.client('ec2', endpoint_url=endpoint_url, config=self.config)
                self.ec2_resource = self.session.resource('ec2', endpoint_url=endpoint_url, config=self.config)
                logger.debug("EC2 clients reconfigured with custom endpoint")

            # Get AWS key file configuration
            self.aws_key_file = config_manager.get_aws_key_file()
            logger.debug(f"AWS_KEY_FILE directory configured: {self.aws_key_file}")

            # Get spot instance termination reclaim configuration
            self.spot_terminate_on_reclaim = config_manager.get_spot_terminate_on_reclaim()
            logger.debug(f"AWS_SPOT_TERMINATE_ON_RECLAIM configured: {self.spot_terminate_on_reclaim}")
            if self.spot_terminate_on_reclaim:
                self.start_spot_reclaim_monitor()

            # Get InstanceID tagging configuration
            self.instance_id_tag_enabled = config_manager.get_instance_id_tag()
            logger.debug(f"AWS_TAG_InstanceID enabled: {self.instance_id_tag_enabled}")

            # Get instance-creation timeout for orphan cleanup
            self.instance_creation_timeout = config_manager.get_instance_creation_timeout()
            logger.debug(f"INSTANCE_CREATION_TIMEOUT configured: {self.instance_creation_timeout} minutes")

            self._test_connection()
            logger.debug("AWS connection test completed successfully")

            self.batch_size = int(os.getenv('AWS_BATCH_SIZE', '200'))

            self.cleanup_interval = int(os.getenv('CLEANUP_INTERVAL_MINUTES', '30')) * 60  # Convert to seconds
            self.max_request_age = int(os.getenv('MAX_REQUEST_AGE_MINUTES', '60'))
            self.last_cleanup = 0
            logger.debug(f"Cleanup configured: interval={self.cleanup_interval}s, max_age={self.max_request_age} minutes")

            # Template cache — keyed by template_id; populated lazily by
            # _get_template_for_request. Prevents TemplateManager from being
            # re-instantiated (and the template JSON re-read) on every fleet poll
            # within the same process. Safe because each script invocation creates a
            # fresh AWSClient, so the cache never survives across LSF poll cycles.
            self._template_cache: Dict[str, Any] = {}

            # Thread pool — initialised eagerly so _init_vm_pool callers are race-free
            self._pool_lock = threading.Lock()
            self.min_vm_workers = int(os.getenv('AWS_MIN_WORKERS', '10'))
            self.max_vm_workers = int(os.getenv('AWS_MAX_WORKERS', '200'))
            cpu_count = multiprocessing.cpu_count()
            workers = max(self.min_vm_workers, min(cpu_count, self.max_vm_workers))
            self.vm_pool = ThreadPoolExecutor(
                max_workers=workers,
                thread_name_prefix='aws_vm_'
            )
            logger.debug(f"VM thread pool initialised: {workers} workers (cpu_count={cpu_count})")

        except Exception as e:
            logger.error(f"AWSClient initialization failed: {e}")
            logger.debug(f"AWSClient initialization stack trace:", exc_info=True)
            raise

    def _refresh_credentials(self) -> Dict[str, str]:
        """Refresh credentials and update cache"""
        logger.debug("Refreshing AWS credentials...")
        with self.credentials_lock:
            credentials = config_manager.get_aws_credentials()
            # Guard against None return from config_manager (e.g. missing credentials file)
            if credentials is None:
                credentials = {}
            logger.debug(f"Retrieved credentials - has_access_key: {'aws_access_key_id' in credentials}, has_secret: {'aws_secret_access_key' in credentials}")

            # Extract expiration from credentials if available
            if credentials and 'Expiration' in credentials:
                expiration = credentials['Expiration']
                if hasattr(expiration, 'timestamp'):          # datetime object
                    self.credentials_expiry = expiration.timestamp()
                elif isinstance(expiration, str):              # ISO format string
                    self.credentials_expiry = datetime.fromisoformat(expiration.replace('Z', '+00:00')).timestamp()
                else:
                    # Assume it's already a number (int or float)
                    self.credentials_expiry = float(expiration)
                logger.debug(f"Credentials expire at: {self.credentials_expiry}")
            else:
                # File-based and IAM role credentials have no real expiry
                self.credentials_expiry = None
                logger.debug("No expiry set for file-based or IAM credentials")

            self.credentials = credentials
            logger.debug("AWS credentials refreshed successfully")
            return credentials.copy()

    def _refresh_credentials_if_needed(self):
        logger.debug("Checking if credentials need refresh...")
        # All reads and writes of credentials_expiry must be done inside the lock
        # to prevent a race where the outer check reads a non-None value that
        # another thread concurrently sets to None, causing TypeError on subtraction.
        with self.credentials_lock:
            if not self.credentials_expiry:
                logger.debug("No credentials expiry set, skipping refresh")
                return

            current_time = time.time()
            logger.debug(f"Current time: {current_time}, expiry: {self.credentials_expiry}, buffer: 300s")
            if current_time >= self.credentials_expiry - 300:
                logger.debug("Credentials need refresh, refreshing...")
                old_credentials = self.credentials
                new_credentials = self._refresh_credentials()

                # Recreate session if credentials changed
                if old_credentials != new_credentials:
                    logger.debug("Credentials changed, recreating clients...")
                    self._recreate_clients()
                else:
                    logger.debug("Credentials unchanged, keeping existing clients")
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
            self.ec2 = self.session.client('ec2', endpoint_url=endpoint_url, config=self.config)
            self.ec2_resource = self.session.resource('ec2', endpoint_url=endpoint_url, config=self.config)
            logger.debug(f"Clients recreated with custom endpoint: {endpoint_url}")
        else:
            self.ec2 = self.session.client('ec2', config=self.config)
            self.ec2_resource = self.session.resource('ec2', config=self.config)
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

    def _get_template_for_request(self, request_id: str) -> Optional[Dict[str, Any]]:
        """Return the template for a fleet request, using a per-process cache.

        TemplateManager reads and validates the template JSON file on every
        instantiation. Caching here means the file is read at most once per
        AWSClient lifetime (i.e. once per script invocation), regardless of how
        many fleet poll calls happen in the same process.
        """
        request_data = db_manager.get_request(request_id)
        if not request_data:
            return None
        template_id = request_data.get('templateId', 'unknown')
        if template_id not in self._template_cache:
            self._template_cache[template_id] = TemplateManager().get_template(template_id)
            logger.debug(f"Cached template '{template_id}' for request {request_id}")
        return self._template_cache[template_id]

    def _init_vm_pool(self):
        """No-op — pool is initialised eagerly in __init__. Kept for call-site compatibility."""
        pass

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
        error_message = ""
        if hasattr(error, 'response') and 'Error' in getattr(error, 'response', {}):
            error_code = error.response['Error'].get('Code', 'UnknownError')
            error_message = error.response['Error'].get('Message', '')
            # Log full AWS response for debugging (doesn't affect return value)
            logger.error(f"AWS API response details: {error.response}")
        else:
            # Log exception details for non-AWS errors
            logger.error(f"AWS API exception details: {str(error)}")

        if error_message:
            return f"{context}: {error_message}. Error Code: {error_code}"
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
            # Partial success: (lsf-L3-tracker/issues/1688)
            has_instances = bool(result and result.get('instance_ids'))
            has_aws_error = bool(result and any(
                failed_instance.get('aws_error_code') or failed_instance.get('error_code')
                for failed_instance in result.get('failed_instances', [])
            ))
            if result and result['success'] and (has_instances or not has_aws_error):
                request_id = result['request_id']
                launched = len(result.get('instance_ids', []))
                logger.info(f"Request {request_id}: launched {launched} of {count} requested instances/slots")

                # Log any partial-failure errors as warnings so operators can see them
                for failed in result.get('failed_instances', []):
                    logger.warning(f"Request {request_id} partial failure: {failed.get('error', failed)}")

                if 'warning' in result:
                    logger.warning(f"Request {request_id} completed with warning: {result['warning']}")

                return request_id
            else:
                if result is None:
                    error_msg = 'Failed to create instances. Error Code: InternalError'
                    logger.debug("Creation method returned None result")
                else:
                    # Zero instances launched — extract the first error for LSF
                    if result.get('failed_instances'):
                        first_error = result['failed_instances'][0].get('error', 'Unknown error')
                        error_msg = f"Failed to create instances. {first_error}"
                    else:
                        error_msg = result.get('error', 'Failed to create instances. Error Code: UnknownError')
                    logger.debug(f"Creation failed with error: {error_msg}")

                raise Exception(error_msg)

        except ClientError as e:
            error_msg = self._format_error_message("Failed to create instances on AWS", e)
            logger.error(f"AWS API error for templateId={template.get('templateId')}: {error_msg}")
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
                logger.error(f"Unexpected error for templateId={template.get('templateId')}: {error_msg}")
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

        network_interfaces = self._build_network_interfaces(template)
        user_data = self._get_encoded_user_data(template, rc_account)
        instance_tags = self._build_instance_tags(template, rc_account)
        launch_template_id = template.get('launchTemplateId')
        logger.debug(f"Pre-resolved network_interfaces, user_data, instance_tags, launch_template_id for {batches_needed} batch(es)")

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
            if launch_template_id:
                instances_params['LaunchTemplate'] = {
                    'LaunchTemplateId': launch_template_id,
                    'Version': template.get('launchTemplateVersion', '$Default')
                }
                logger.debug(f"Batch {batch_num + 1}: Using launch template: {launch_template_id}")
            else:
                instances_params['ImageId'] = template['imageId']
                logger.debug(f"Batch {batch_num + 1}: Using ImageId: {template['imageId']}")

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
                    'Arn' if instance_profile.startswith('arn:') else 'Name': instance_profile
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
                network_config = self._build_network_config(template)
                if network_config.get('SubnetId'):
                    instances_params['SubnetId'] = network_config['SubnetId']
                    logger.debug(f"Batch {batch_num + 1}: Using SubnetId: {network_config['SubnetId']}")
                if network_config.get('Groups'):
                    instances_params['SecurityGroupIds'] = network_config['Groups']
                    logger.debug(f"Batch {batch_num + 1}: Using SecurityGroupIds: {network_config['Groups']}")

            self._apply_key_name_if_valid(instances_params, template, f"Batch {batch_num + 1}")

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

                # Persist machines to DB immediately — before moving to the next
                # batch — so that instances are never orphaned if the process dies
                # between a successful run_instances call and the end of the loop.
                batch_machines_data = []
                for instance in instances:
                    instance_id = instance.get('InstanceId')
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
                    batch_machines_data.append(machine_data)

                db_result = db_manager.add_machines_to_request(request_id, batch_machines_data)
                if db_result['success_count'] > 0:
                    logger.info(f"Batch {batch_num + 1}: Added {db_result['success_count']} machines to database for request {request_id}")
                if db_result['failed_count'] > 0:
                    logger.warning(f"Batch {batch_num + 1}: Failed to add {db_result['failed_count']} machines to database for request {request_id}: {db_result.get('errors')}")

                # If every machine in this batch failed to persist, terminate them
                # immediately so they do not become untracked orphans.  The orphan
                # cleanup scans the DB, so instances that were never written there
                # would otherwise run indefinitely.
                if db_result['success_count'] == 0:
                    untracked_ids = [m['machineId'] for m in batch_machines_data]
                    logger.error(
                        f"Batch {batch_num + 1}: DB write failed for all {len(untracked_ids)} "
                        f"instances — terminating to prevent orphans: {untracked_ids}"
                    )
                    try:
                        self.ec2.terminate_instances(InstanceIds=untracked_ids)
                    except Exception as term_err:
                        logger.error(
                            f"Batch {batch_num + 1}: Could not terminate untracked instances "
                            f"{untracked_ids}: {term_err}"
                        )
                    # Do not add these IDs to instance_ids; treat the batch as failed
                    failed_instances.append({
                        'error': f"DB write failed — batch instances terminated to prevent orphaning. Errors: {db_result.get('errors')}",
                        'aws_error_code': 'InternalError',
                        'batch_index': batch_num,
                        'batch_size': batch_size,
                        'instances_failed': batch_size
                    })
                    continue

                instance_ids.extend(batch_instance_ids)

            except ClientError as e:
                # Extract AWS error code for better error reporting
                error_code = e.response['Error']['Code'] if hasattr(e, 'response') else 'UnknownError'
                error_msg = self._format_error_message(f"Failed to launch EC2 instances in batch {batch_num + 1}", e)
                logger.error(f"Batch {batch_num + 1} creation failed for templateId={template.get('templateId')} with requestId={request_id}: {error_msg}")
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
                logger.error(f"Batch {batch_num + 1} unexpected error for templateId={template.get('templateId')} with requestId={request_id}: {error_msg}")
                failed_instances.append({
                    'error': error_msg,
                    'aws_error_code': 'InternalError',
                    'batch_index': batch_num,
                    'batch_size': batch_size,
                    'instances_failed': batch_size
                })
                # Continue with next batch even if this one fails
                continue

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
            # The DB entry was written before the batch loop so that any
            # instances created by earlier batches are never orphaned on process death.
            # If all batches failed the entry is an empty shell that would persist for
            # up to 60 min until periodic_cleanup ages it out.  Remove it now.
            db_manager.remove_request(request_id)
            logger.info(f"Removed empty request {request_id} from database after all batches failed")

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
                                kv = key_eq_val.strip()
                                if kv:
                                    if '=' in kv:
                                        k, v = kv.split('=', 1)
                                        exports.append(f"export {k.strip()}='{v}'")
                                    else:
                                        exports.append(f"export {kv}")

                        # Add template ID for identification
                        if template.get('templateId'):
                            exports.append(f"export template_id='{template.get('templateId')}'")
                            logger.debug(f"Added template_id export: {template.get('templateId')}")

                        # Add providerName
                        provider_name = os.getenv('PROVIDER_NAME')
                        if provider_name:
                            exports.append(f"export providerName='{provider_name}'")
                            logger.debug(f"Added providerName export: {provider_name}")
                        else:
                            logger.warning("PROVIDER_NAME environment variable not set")

                        # Add clusterName
                        script_options = os.getenv('SCRIPT_OPTIONS', '')
                        if 'clusterName=' in script_options:
                            try:
                                # Split and take only the part before any potential next parameter
                                cluster_name = script_options.split('clusterName=', 1)[1].split()[0]
                                exports.append(f"export clustername='{cluster_name}'")
                                logger.debug(f"Added clustername export: {cluster_name}")
                            except (IndexError, AttributeError):
                                cluster_name = None

                        # Add rc_account
                        if rc_account:
                            exports.append(f"export rc_account='{rc_account}'")
                            logger.debug(f"Added rc_account export: {rc_account}")

                        # Combine and replace placeholder
                        if exports:
                            export_cmd = "\n".join(exports)
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
            tag_pairs = [pair.strip() for pair in tags_string.split(';') if pair.strip()]
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
            is_efa = template.get('interfaceType', '').lower() == 'efa'

            # Determine how many NICs to attach. efaCount only applies to EFA;
            # for all other interface types a single NIC is always used.
            nic_count = 1
            if is_efa:
                try:
                    nic_count = max(1, int(template.get('efaCount', 1)))
                except (ValueError, TypeError):
                    logger.warning(f"Invalid efaCount value '{template.get('efaCount')}' — defaulting to 1 EFA NIC")
                    nic_count = 1
                if nic_count > 1:
                    logger.debug(f"Configuring {nic_count} EFA NICs (efaCount={nic_count})")

            for network_card_index in range(nic_count):
                interface_config = {
                    # First NIC uses DeviceIndex=0; additional NICs use DeviceIndex=1
                    # with an incrementing NetworkCardIndex (AWS requirement).
                    'DeviceIndex': 0 if network_card_index == 0 else 1,
                    'SubnetId': network_config['SubnetId'],
                    'Groups': network_config['Groups'],
                    'DeleteOnTermination': True
                }
                if is_efa:
                    interface_config['InterfaceType'] = 'efa'
                    interface_config['NetworkCardIndex'] = network_card_index
                    logger.debug(f"Configuring EFA NIC {network_card_index}: DeviceIndex={interface_config['DeviceIndex']}, NetworkCardIndex={network_card_index}")

                network_interfaces.append(interface_config)

            logger.debug(f"Built {len(network_interfaces)} network interface(s)")
        else:
            logger.debug("No subnet ID available for network interface")

        return network_interfaces

    def _validate_or_create_key_pair(self, key_name: str) -> bool:
        """Validate key pair exists in AWS or create it if needed"""
        try:
            key_file_dir = self.aws_key_file if self.aws_key_file else get_data_path()
            if not key_file_dir:
                return False

            key_file_path = os.path.join(key_file_dir, f"{key_name}.pem")
            local_key_exists = os.path.exists(key_file_path)

            if local_key_exists:
                logger.debug(f"Local key file exists: {key_file_path}")

            # Always check if key pair exists in AWS (even if local file exists)
            try:
                self.ec2.describe_key_pairs(KeyNames=[key_name])
                logger.debug(f"Key pair '{key_name}' exists in AWS")
                return True
            except ClientError as e:
                if e.response['Error']['Code'] != 'InvalidKeyPair.NotFound':
                    logger.warning(f"Error checking key pair: {e}")
                    return False
                # Key pair not found in AWS
                if local_key_exists:
                    logger.info(f"Local key file exists but key pair '{key_name}' not found in AWS. Attempting to import public key.")
                    return self._import_key_pair_from_local(key_name, key_file_path)

            # Create new key pair (only if no local file exists)
            logger.debug(f"Creating new key pair '{key_name}' in AWS")
            response = self.ec2.create_key_pair(KeyName=key_name)

            # Save key material
            os.makedirs(key_file_dir, exist_ok=True)
            with open(key_file_path, 'w') as f:
                f.write(response['KeyMaterial'])
            os.chmod(key_file_path, 0o400)

            logger.debug(f"The new key pair {key_name} is created and stored at {key_file_dir}.")
            return True

        except ClientError as e:
            if e.response.get('Error', {}).get('Code') == 'InvalidKeyPair.Duplicate':
                logger.debug(f"Key pair '{key_name}' already exists")
                return True
            logger.error(f"Failed to create key pair: {e}")
            return False
        except Exception as e:
            logger.error(f"Key pair validation error: {e}")
            return False

    def _import_key_pair_from_local(self, key_name: str, key_file_path: str) -> bool:
        """Import public key to AWS from local PEM file using ssh-keygen"""
        try:
            # Extract public key from private key file
            # Use ssh-keygen to extract public key from private key
            result = subprocess.run(
                ['ssh-keygen', '-y', '-f', key_file_path],
                capture_output=True,
                text=True,
                timeout=10
            )

            if result.returncode != 0:
                logger.warning(f"Failed to extract public key from {key_file_path}")
                if result.stderr:
                    logger.warning(f"ssh-keygen error: {result.stderr.strip()}")
                logger.warning(f"Instance will be created without key pair. You will not be able to SSH into the instance.")
                return False

            public_key_material = result.stdout.strip()

            if not public_key_material:
                logger.warning(f"No public key material extracted from {key_file_path}")
                logger.warning(f"Instance will be created without key pair. You will not be able to SSH into the instance.")
                return False

            # Import the public key to AWS
            logger.info(f"Importing public key for '{key_name}' to AWS")
            self.ec2.import_key_pair(
                KeyName=key_name,
                PublicKeyMaterial=public_key_material
            )

            logger.info(f"Successfully imported key pair '{key_name}' to AWS from local file")
            return True

        except subprocess.TimeoutExpired:
            logger.warning(f"Timeout while extracting public key from {key_file_path} (exceeded 10 seconds)")
            logger.warning(f"Instance will be created without key pair. You will not be able to SSH into the instance.")
            return False
        except FileNotFoundError:
            logger.warning(f"ssh-keygen command not found. Cannot automatically import key pair '{key_name}'.")
            logger.warning(f"Instance will be created without key pair. You will not be able to SSH into the instance.")
            logger.info(f"To manually import the key pair, run: aws ec2 import-key-pair --key-name {key_name} --public-key-material fileb://<(ssh-keygen -y -f {key_file_path})")
            return False
        except ClientError as e:
            error_code = e.response.get('Error', {}).get('Code', '')
            if error_code == 'InvalidKeyPair.Duplicate':
                logger.debug(f"Key pair '{key_name}' already exists in AWS")
                return True
            logger.warning(f"Failed to import key pair '{key_name}' to AWS: {e}")
            logger.warning(f"Instance will be created without key pair. You will not be able to SSH into the instance.")
            return False
        except Exception as e:
            logger.warning(f"Unexpected error importing key pair from local file: {e}")
            logger.warning(f"Instance will be created without key pair. You will not be able to SSH into the instance.")
            return False

    def _apply_key_name_if_valid(self, params_dict: Dict, template: Dict, context: str = "") -> None:
        """Get key name from template and apply to params dict if validation succeeds"""
        key_name = template.get('keyName')
        if not key_name:
            return

        if self._validate_or_create_key_pair(key_name):
            params_dict['KeyName'] = key_name
            logger.debug(f"{context}: Using key pair: {key_name}" if context else f"Using key pair: {key_name}")
        else:
            prefix = f"{context}: " if context else ""
            logger.warning(f"{prefix}Key pair '{key_name}' validation/creation failed - proceeding without KeyName")
            logger.warning(f"{prefix}Instance will be created without SSH key pair access")

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

    def _build_machine_data_template(self, template: Dict, request_id: str, rc_account: str = 'default', ncores: int = None, nthreads: int = None) -> Dict[str, Any]:
        """Build base machine data template - reusable for all instance types"""
        logger.debug(f"Building machine data template for request {request_id}")
        common_params = self._get_common_instance_params(template)

        # Use actual values from AWS if provided, otherwise fall back to template values
        actual_ncores = ncores if ncores is not None else common_params['ncores']
        actual_nthreads = nthreads if nthreads is not None else common_params['nthreads']
        logger.debug(f"Using ncores={actual_ncores} (provided={ncores}, template={common_params['ncores']})")
        logger.debug(f"Using nthreads={actual_nthreads} (provided={nthreads}, template={common_params['nthreads']})")

        template_data = {
            "template": common_params['template_id'],
            "result": "executing",
            "status": "pending",
            "privateIpAddress": "",
            "publicIpAddress": "",
            "publicDnsName": "",
            "ncores": actual_ncores,
            "nthreads": actual_nthreads,
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
                            public_ip: str = "", public_dns: str = "", ncores: int = None, nthreads: int = None) -> Dict[str, Any]:
        """Create complete machine data for database entry"""
        logger.debug(f"Creating machine data for instance {instance_id}")
        base_data = self._build_machine_data_template(template, request_id, rc_account, ncores, nthreads)

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

        # Track any temporary LT versions created so they can be cleaned up on failure.
        created_lt_versions: List[Dict] = []  # [{'lt_id': str, 'version': int}, ...]

        try:
            logger.info(f"Creating Spot Fleet from template")

            # Validate required Spot Fleet parameters
            fleet_role = template.get('fleetRole')
            if not fleet_role:
                logger.error(f"fleetRole is required for Spot Fleet template with templateId={template.get('templateId')}")
                raise ValueError("fleetRole is required for Spot Fleet templates")

            # Build Spot Fleet request using template parameters
            # Limitation: Only Type=request is supported for spot fleet for LSF
            # allocationStrategy is already normalized during template loading
            allocation_strategy = template.get('allocationStrategy', 'capacityOptimized')

            # Set request validity window: ValidFrom=now, ValidUntil=now+30min.
            valid_from = datetime.now(timezone.utc).replace(microsecond=0)
            valid_until = (valid_from + timedelta(minutes=30))

            fleet_config_data: Dict[str, Any] = {
                'Type': 'request',
                'TargetCapacity': count,
                'IamFleetRole': fleet_role,
                'AllocationStrategy': allocation_strategy,
                'ValidFrom': valid_from,
                'ValidUntil': valid_until,
                'TerminateInstancesWithExpiration': False,
            }

            launch_template_id = template.get('launchTemplateId')
            if launch_template_id:
                # When a launchTemplateId is configured, honour all settings stored in
                # that template (block-device mappings, metadata options, capacity
                # reservations, etc.) by using LaunchTemplateConfigs instead of flat
                # LaunchSpecifications.  A temporary version is created so that LSF
                # user-data and other template-level overrides are injected without
                # altering the customer's base template.
                logger.debug(f"launchTemplateId={launch_template_id}: creating temporary LT version for Spot Fleet")
                encoded_user_data = self._get_encoded_user_data(template, rc_account)
                lt_stub: Dict[str, Any] = {
                    'LaunchTemplateConfigs': [{
                        'LaunchTemplateSpecification': {
                            'LaunchTemplateId': launch_template_id,
                            'Version': template.get('launchTemplateVersion', '$Default'),
                        }
                    }]
                }
                success, created_lt_versions = self._create_temp_launch_template_versions(
                    lt_stub, template, encoded_user_data, rc_account
                )
                if success:
                    # The version number has already been written back into lt_stub by the helper.
                    pinned_version = lt_stub['LaunchTemplateConfigs'][0]['LaunchTemplateSpecification']['Version']
                    lt_id_used = created_lt_versions[0]['lt_id']
                    logger.debug(f"Using temporary LT version {pinned_version} of {lt_id_used} for Spot Fleet")

                    spot_price = template.get('spotPrice')
                    vm_type    = template.get('vmType', '')
                    vm_types   = [v.strip() for v in vm_type.split(',') if v.strip()] if vm_type else []
                    subnet_id  = template.get('subnetId', '')

                    # Build per-instance-type overrides so AWS can pick the best AZ.
                    overrides: List[Dict] = []
                    if vm_types:
                        for inst_type in vm_types:
                            override: Dict[str, Any] = {'InstanceType': inst_type}
                            if subnet_id:
                                override['SubnetId'] = subnet_id
                            if spot_price:
                                override['SpotPrice'] = str(spot_price)
                            overrides.append(override)
                    elif subnet_id:
                        override = {'SubnetId': subnet_id}
                        if spot_price:
                            override['SpotPrice'] = str(spot_price)
                        overrides.append(override)

                    lt_config_entry: Dict[str, Any] = {
                        'LaunchTemplateSpecification': {
                            'LaunchTemplateId': lt_id_used,
                            'Version': pinned_version,
                        }
                    }
                    if overrides:
                        lt_config_entry['Overrides'] = overrides

                    fleet_config_data['LaunchTemplateConfigs'] = [lt_config_entry]
                else:
                    # Version creation failed — fall back to flat LaunchSpecifications
                    # so the fleet request can still proceed.
                    logger.warning(
                        "Failed to create temporary LT version for Spot Fleet "
                        f"(launchTemplateId={launch_template_id}) — falling back to LaunchSpecifications"
                    )
                    fleet_config_data['LaunchSpecifications'] = self._build_spot_fleet_launch_specs(template, rc_account)
            else:
                fleet_config_data['LaunchSpecifications'] = self._build_spot_fleet_launch_specs(template, rc_account)

            spot_fleet_config = {'SpotFleetRequestConfig': fleet_config_data}
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

            # If a temporary LT version was created (launchTemplateId path), deferred cleanup
            # will delete it when the fleet terminates via _delete_lt_versions_by_description.
            if created_lt_versions:
                logger.debug(
                    f"Spot Fleet {fleet_id} created with {len(created_lt_versions)} temporary"
                    f" LT version(s) — deferred cleanup will run at termination"
                )

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
            error_code = e.response['Error']['Code'] if hasattr(e, 'response') else 'UnknownError'
            error_msg = self._format_error_message("Spot Fleet request failed", e)
            logger.error(f"Spot Fleet creation failed for templateId={template.get('templateId')}: {error_msg}")
            # Fleet was never created — clean up the temporary LT version immediately.
            if created_lt_versions:
                logger.warning(f"request_spot_fleet failed — cleaning up temporary LT version(s) {created_lt_versions}")
                self._delete_lt_versions_by_id(created_lt_versions)
            return {
                'success': False,
                'error': error_msg,
                'aws_error_code': error_code,
                # request_id set None on failure, omitted from JSON.
                'request_id': None,
                'instance_ids': [],
                'failed_instances': []
            }
        except Exception as e:
            error_msg = self._format_error_message("Unexpected error creating Spot Fleet", e)
            logger.error(f"Unexpected error for templateId={template.get('templateId')}: {error_msg}")
            if created_lt_versions:
                logger.warning(f"Unexpected error — cleaning up temporary LT version(s) {created_lt_versions}")
                self._delete_lt_versions_by_id(created_lt_versions)
            return {
                'success': False,
                'error': error_msg,
                'aws_error_code': 'InternalError',
                'request_id': None,
                'instance_ids': [],
                'failed_instances': []
            }

    def _build_spot_fleet_launch_specs(self, template: Dict, rc_account: str = 'default') -> List[Dict]:
        """Build Spot Fleet launch specifications from template parameters"""
        logger.debug("Building Spot Fleet launch specifications...")

        # Pass the raw subnet string directly. AWS accepts comma-separated values in SubnetId field and selects the AZ itself.
        subnet_id = template.get('subnetId')

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
            logger.debug(f"Multiple VM types for Spot Fleet: {vm_types}")
        elif vm_type:
            vm_types = [vm_type.strip()]
            logger.debug(f"Single VM type for Spot Fleet: {vm_types}")
        else:
            logger.error(f"No VM type specified for Spot Fleet for templateId={template.get('templateId')}")
            return []

        launch_specs = []
        for instance_type in vm_types:
            launch_spec = self._build_single_spot_fleet_launch_spec(
                template, instance_type, subnet_id, security_groups, placement,
                encoded_user_data, instance_tags, rc_account
            )
            if launch_spec:
                launch_specs.append(launch_spec)
                logger.debug(f"Added launch spec for {instance_type} (subnetId='{subnet_id}') with placement: {placement}")

        logger.debug(f"Built {len(launch_specs)} launch specifications for Spot Fleet")
        return launch_specs

    def _build_single_spot_fleet_launch_spec(self, template: Dict, instance_type: str,
                                        subnet_id: Optional[str], security_groups: List[str], placement: Dict,
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
            self._apply_key_name_if_valid(launch_spec, template, "Spot Fleet")

            is_efa = template.get('interfaceType', '').lower() == 'efa'

            if is_efa:
                # EFA requires NetworkInterfaces — a single subnet must be specified.
                # Take the first subnet from the (possibly comma-separated) list.
                first_subnet = subnet_id.split(',')[0].strip() if subnet_id else None
                if first_subnet:
                    launch_spec['NetworkInterfaces'] = [{
                        'DeviceIndex': 0,
                        'SubnetId': first_subnet,
                        'Groups': security_groups,
                        'InterfaceType': 'efa',
                        'DeleteOnTermination': True
                    }]
                    logger.debug(f"EFA spec: using first subnet {first_subnet} in NetworkInterfaces")
            else:
                # Standard path: top-level SubnetId accepts a comma-separated list.
                # AWS capacity optimizer picks the best AZ at fulfillment time.
                if subnet_id:
                    launch_spec['SubnetId'] = subnet_id
                if security_groups:
                    launch_spec['SecurityGroups'] = [
                        {'GroupId': sg} for sg in security_groups
                    ]

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
                    'Arn' if instance_profile.startswith('arn:') else 'Name': instance_profile
                }

            # Remove None values
            launch_spec = {k: v for k, v in launch_spec.items() if v is not None}
            return launch_spec

        except Exception as e:
            logger.error(f"Failed to build launch spec for {instance_type} subnet '{subnet_id}' of templateId={template.get('templateId')}: {e}")
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

                # Get template via the per-process cache — avoids re-reading the
                # template JSON file on every poll cycle within this process.
                request_data = db_manager.get_request(fleet_id)
                rc_account = request_data.get('rcAccount', 'default') if request_data else 'default'
                template = self._get_template_for_request(fleet_id)
                template_id = template.get('templateId', 'unknown') if template else 'unknown'
                logger.debug(f"Retrieved template {template_id} for fleet instances")

                # Build machine data for all active instances. add_machines_to_request
                # deduplicates inside its own lock, which closes the race window between
                # concurrent getRequestStatus calls both discovering the same new instances.
                batch_machine_data = []
                for instance_id in active_instance_ids:
                    logger.debug(f"Spot Fleet instance {instance_id} - CPU info will be retrieved when running")
                    machine_data = self._create_machine_data(
                        instance_id=instance_id,
                        template=template,
                        request_id=fleet_id,
                        rc_account=rc_account,
                        name=f"host-{instance_id}",
                        ncores=0,
                        nthreads=0
                    )
                    batch_machine_data.append(machine_data)

                result = db_manager.add_machines_to_request(fleet_id, batch_machine_data)
                if result['success_count'] > 0:
                    logger.info(f"Batch added {result['success_count']} new Spot Fleet instances to database")
                if result['failed_count'] > 0:
                    logger.debug(f"{result['failed_count']} Spot Fleet instances already in database (concurrent poll or re-poll)")

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

            # For request-type fleets, inject ValidUntil = now + 30 min so AWS enforces the same
            # timeout window that LSF uses. Without this, a timed-out request stays active on AWS
            # and can launch orphaned instances after LSF has already given up on it.
            if fleet_type == 'request' and 'ValidUntil' not in fleet_config:
                valid_until = datetime.now(timezone.utc) + timedelta(minutes=30)
                valid_until = valid_until.replace(microsecond=0)
                fleet_config['ValidUntil'] = valid_until
                logger.debug(f"EC2 Fleet request-type: set ValidUntil={valid_until.isoformat()}")

            # Get encoded user data from user_data.sh script
            encoded_user_data = self._get_encoded_user_data(template, rc_account)
            logger.debug("Encoded user data retrieved")

            # LSF has already converted machineCount → slots before calling request_machines,
            # so 'count' is the exact TotalTargetCapacity to pass to the fleet API.
            total_slots = count
            logger.debug(f"EC2 Fleet TotalTargetCapacity={total_slots} (as received from LSF)")

            if 'TargetCapacitySpecification' in fleet_config:
                target_spec = fleet_config['TargetCapacitySpecification']

                # Always update TotalTargetCapacity to what LSF actually requested
                target_spec['TotalTargetCapacity'] = total_slots

                # onDemandTargetCapacityRatio is optional (spec: positive float 0.0–1.0). When set, compute and apply the on-demand / spot split.
                # When NOT set, preserve whatever OnDemandTargetCapacity / SpotTargetCapacity values the user wrote in the ec2FleetConfig file and
                # let AWS use DefaultTargetCapacityType as the tiebreaker
                ratio = template.get('onDemandTargetCapacityRatio')
                if ratio is not None:
                    try:
                        on_demand_slots = math.ceil(total_slots * float(ratio))
                        spot_slots = total_slots - on_demand_slots

                        # Neither value may exceed totalTargetCapacity
                        if on_demand_slots > total_slots:
                            logger.warning(
                                f"onDemandTargetCapacity {on_demand_slots} exceeds "
                                f"totalTargetCapacity {total_slots} — clamping to {total_slots}"
                            )
                            on_demand_slots = total_slots
                        if spot_slots > total_slots:
                            logger.warning(
                                f"spotTargetCapacity {spot_slots} exceeds "
                                f"totalTargetCapacity {total_slots} — clamping to {total_slots}"
                            )
                            spot_slots = total_slots

                        target_spec['OnDemandTargetCapacity'] = on_demand_slots
                        target_spec['SpotTargetCapacity'] = spot_slots
                        logger.debug(f"onDemandTargetCapacityRatio={ratio}: on_demand_slots={on_demand_slots}, spot_slots={spot_slots}")
                    except (ValueError, TypeError):
                        logger.warning(f"Invalid onDemandTargetCapacityRatio value {ratio} - preserving config file on-demand/spot split")
                else:
                    logger.debug("onDemandTargetCapacityRatio not set - preserving config file on-demand/spot split, DefaultTargetCapacityType governs")

                logger.info(
                    f"Set fleet capacity: Total={total_slots}, "
                    f"OnDemand={target_spec.get('OnDemandTargetCapacity', 'from-config')}, "
                    f"Spot={target_spec.get('SpotTargetCapacity', 'from-config')}"
                )

            # Use helper functions for tags
            instance_tags = self._build_instance_tags(template, rc_account)

            # Add fleet-level tags only (instance tags must be in LaunchTemplate)
            if instance_tags:
                fleet_config['TagSpecifications'] = [{
                    'ResourceType': 'fleet',
                    'Tags': instance_tags
                }]
                logger.debug("Added fleet-level tags")

            # ALWAYS create temporary launch template versions with all overrides.
            # The returned list of (lt_id, version) pairs is used to clean up immediately
            # if create_fleet fails, avoiding an untracked version leak
            created_lt_versions: List[Dict] = []
            if 'LaunchTemplateConfigs' in fleet_config:
                logger.debug("Creating temporary launch template versions for EC2 Fleet with all overrides")
                success, created_lt_versions = self._create_temp_launch_template_versions(fleet_config, template, encoded_user_data, rc_account)
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
                        # CPU info will be retrieved later when instance is running with IP assignment
                        logger.debug(f"Instant EC2 Fleet instance {instance_id} created - CPU info will be retrieved when running")
                        # Use helper for machine data creation with default CPU values
                        machine_data = self._create_machine_data(
                            instance_id=instance_id,
                            template=template,
                            request_id=fleet_id,
                            rc_account=rc_account,
                            name=f"host-{instance_id}",
                            ncores=0,
                            nthreads=0
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
                    error_code = error.get('ErrorCode', 'Unknown')
                    error_message = error.get('ErrorMessage', 'Unknown error')
                    failed_instances.append({
                        'error': f"EC2 Fleet has errors: {error_message}. Error Code: {error_code}",
                        'error_code': error_code,
                        'error_message': error_message,
                        'aws_error_code': error_code
                    })
                    logger.debug(f"Fleet error: {error}")

                # EC2 fleet is created but AWS fulfilled zero instances (all
                # capacity errors).  The exception handlers only clean up created_lt_versions
                # when fleet_id is None (i.e. create_fleet itself failed).  When create_fleet
                # succeeds but returns no instances the versions would otherwise leak until
                # the 30-60 min periodic_cleanup fires.  Clean them up immediately here using
                # the exact version IDs we already have — no describe call needed.
                if not successful_instances and created_lt_versions:
                    logger.info(
                        f"Instant fleet {fleet_id} returned no instances — "
                        f"cleaning up {len(created_lt_versions)} temporary LT version(s) immediately"
                    )
                    self._delete_lt_versions_by_id(created_lt_versions)

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
            logger.error(f"EC2 Fleet creation failed for templateId={template.get('templateId')}: {error_msg}")

            # Fleet never created — delete the exact versions we created before the
            # failure. Version numbers are known directly from created_lt_versions so
            # no describe call is needed.
            if not fleet_id and created_lt_versions:
                logger.warning(f"create_fleet failed — cleaning up {len(created_lt_versions)} orphaned launch template versions")
                self._delete_lt_versions_by_id(created_lt_versions)

            if fleet_id:
                logger.warning(f"Fleet {fleet_id} was created but encountered error: {error_msg}")
                return {
                    'success': True,  # Still return success since fleet was created
                    'request_id': fleet_id,
                    'instance_ids': successful_instances,
                    'failed_instances': failed_instances,
                    'fleet_type': fleet_type,
                    'warning': error_msg
                }
            else:
                return {
                    'success': False,
                    'error': error_msg,
                    'aws_error_code': error_code,
                    # request_id set None on failure, omitted from JSON.
                    'request_id': None,
                    'instance_ids': [],
                    'failed_instances': []
                }
        except Exception as e:
            error_msg = self._format_error_message("Unexpected error creating EC2 Fleet", e)
            logger.error(f"Unexpected error for templateId={template.get('templateId')}: {error_msg}")

            if not fleet_id and created_lt_versions:
                logger.warning(f"Unexpected error after version creation — cleaning up {len(created_lt_versions)} orphaned launch template versions")
                self._delete_lt_versions_by_id(created_lt_versions)

            if fleet_id:
                logger.warning(f"Fleet {fleet_id} was created but encountered unexpected error: {error_msg}")
                return {
                    'success': True,
                    'request_id': fleet_id,
                    'instance_ids': successful_instances,
                    'failed_instances': failed_instances,
                    'fleet_type': fleet_type,
                    'warning': error_msg
                }
            else:
                return {
                    'success': False,
                    'error': error_msg,
                    'aws_error_code': 'InternalError',
                    # request_id set None on failure, omitted from JSON.
                    'request_id': None,
                    'instance_ids': [],
                    'failed_instances': []
                }

    # Prefix stamped into VersionDescription for every temporary LT version created
    # by this automation. Both _create_temp_launch_template_versions (writer) and
    # _delete_lt_versions_by_description (reader) reference this constant so the
    # two stay in sync if the prefix ever changes.
    _LT_AUTOMATION_DESC_PREFIX = "Temporary LSF version for "

    # AWS limit for DeleteLaunchTemplateVersions — max versions per API call.
    _LT_VERSION_MAX_BATCH_SIZE = 200

    def _create_temp_launch_template_versions(self, fleet_config: Dict, template: Dict, encoded_user_data: str, rc_account: str = 'default'):
        """
        ALWAYS create temporary launch template versions with all template overrides including user data.

        Returns (success: bool, created_versions: List[Dict]) where each entry is
        {'lt_id': ..., 'version': ...} — allows the caller to delete orphaned versions
        immediately when create_fleet fails, before any DB request exists.
        """
        logger.debug("Creating temporary launch template versions...")
        if 'LaunchTemplateConfigs' not in fleet_config:
            logger.debug("No LaunchTemplateConfigs found - skipping temporary version creation")
            return False, []

        template_id = template.get('templateId', 'unknown')
        created_versions: List[Dict] = []
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
                    version_description = f'{self._LT_AUTOMATION_DESC_PREFIX}{template_id} with all overrides - created {time.ctime()}'

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
                    # Guard against comma-separated multi-type strings — AWS only accepts a single
                    # InstanceType per launch template version; skip the override when multiple
                    # types are listed (the EC2 Fleet overrides field handles per-type selection).
                    vm_type = template.get('vmType', '')
                    if vm_type and ',' not in vm_type:
                        version_data['InstanceType'] = vm_type.strip()
                        logger.debug(f"Applied instance type override: {vm_type.strip()} for config {i}")
                    elif vm_type and ',' in vm_type:
                        logger.debug(f"Skipping InstanceType override for config {i}: multi-value vmType '{vm_type}' is handled by EC2 Fleet overrides")

                    # 4. Apply key pair override if specified
                    self._apply_key_name_if_valid(version_data, template, f"EC2 Fleet config {i}")

                    # 5. Apply IAM instance profile override if specified
                    instance_profile = template.get('instanceProfile')
                    if instance_profile:
                        version_data['IamInstanceProfile'] = {
                            'Arn' if instance_profile.startswith('arn:') else 'Name': instance_profile
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
                    lt_id_used = create_response['LaunchTemplateVersion']['LaunchTemplateId']
                    created_versions.append({'lt_id': lt_id_used, 'version': new_version_number})

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
                    logger.error(f"Failed to create temporary launch template version for config {i} for templateId={template.get('templateId')}: {error_code} - {error_message}")
                    continue
                except Exception as e:
                    logger.error(f"Unexpected error creating temporary launch template version for config {i} for templateId={template.get('templateId')}: {e}")
                    logger.debug(f"Launch template version creation stack trace:", exc_info=True)
                    continue

        logger.debug(f"Successfully created temporary versions for {successful_creations}/{total_configs} LaunchTemplateConfigs")
        return successful_creations > 0, created_versions

    def _load_ec2_fleet_config(self, template: Dict) -> Dict[str, Any]:
        """Load EC2 Fleet configuration from file - support both valid JSON and legacy format with placeholders"""
        logger.debug("Loading EC2 Fleet configuration...")
        ec2_fleet_config_path = template.get('ec2FleetConfig')
        if not ec2_fleet_config_path:
            raise ValueError(f"EC2 Fleet configuration path not provided for templateId={template.get('templateId')}")

        # Use existing config path resolution pattern
        if not os.path.isabs(ec2_fleet_config_path):
            from utils import get_config_path
            config_dir = get_config_path()
            ec2_fleet_config_path = os.path.join(config_dir, ec2_fleet_config_path)
        logger.debug(f"Resolved EC2 Fleet config path: {ec2_fleet_config_path}")

        if not os.path.exists(ec2_fleet_config_path):
            logger.error(f"EC2 Fleet configuration file not found: {ec2_fleet_config_path} for templateId={template.get('templateId')}")
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
                    logger.error(f"Failed to parse even after cleaning legacy placeholders for templateId={template.get('templateId')}: {second_error}")
                    logger.debug(f"Cleaned content that failed to parse: {cleaned_content}")
                    raise ValueError(f"Invalid JSON in EC2 Fleet configuration even after legacy placeholder cleaning: {second_error}")

        except Exception as e:
            logger.error(f"Unexpected error loading EC2 Fleet configuration for templateId={template.get('templateId')}: {e}")
            logger.debug(f"Config loading stack trace:", exc_info=True)
            raise

    def _delete_lt_versions_by_id(self, created_lt_versions: List[Dict]) -> None:
        """Delete a specific set of launch template versions by their known
        (lt_id, version-number) pairs."""
        # Group by lt_id so we can batch per template.
        versions_by_lt: Dict[str, List[str]] = {}
        for entry in created_lt_versions:
            versions_by_lt.setdefault(entry['lt_id'], []).append(str(entry['version']))

        for lt_id, version_strs in versions_by_lt.items():
            for i in range(0, len(version_strs), self._LT_VERSION_MAX_BATCH_SIZE):
                batch = version_strs[i:i + self._LT_VERSION_MAX_BATCH_SIZE]
                try:
                    response = self.ec2.delete_launch_template_versions(
                        LaunchTemplateId=lt_id,
                        Versions=batch
                    )
                    successful = response.get('SuccessfullyDeletedLaunchTemplateVersions', [])
                    logger.debug(f"Deleted {len(successful)} launch template version(s) from {lt_id}: {[e['VersionNumber'] for e in successful]}")
                    for failure in response.get('UnsuccessfullyDeletedLaunchTemplateVersions', []):
                        err = failure.get('ResponseError', {})
                        code = err.get('Code', '')
                        version_num = failure.get('VersionNumber', '?')
                        if code == 'InvalidLaunchTemplateId.VersionNotFound':
                            logger.debug(f"Launch template version {version_num} from {lt_id} already deleted")
                        else:
                            logger.warning(f"Failed to delete version {version_num} from {lt_id}: {code} — {err.get('Message', '')}")
                except ClientError as e:
                    logger.warning(f"Failed to delete batch of versions {batch} from {lt_id}: {e}")

    def _delete_lt_versions_by_description(self, request_id: str, template_id: str = '') -> None:
        """Periodic recovery cleanup for launch template versions created by automation"""
        logger.debug(f"Starting launch template version cleanup for fleet: {request_id}")
        try:
            # Resolve templateId — use the caller-supplied value when available
            # so this function works even after the DB row has been deleted.
            if not template_id:
                request_data = db_manager.get_request(request_id)
                if not request_data:
                    logger.warning(f"Request {request_id} not found in database, cannot cleanup launch template versions")
                    return
                template_id = request_data.get('templateId', '')

            if not template_id:
                logger.warning(f"No template ID found for request {request_id}, cannot cleanup launch template versions")
                return

            # Resolve the launch template IDs to scan for automation-created versions.
            lt_ids_to_check = []
            try:
                template_manager = TemplateManager()
                template = template_manager.get_template(template_id)

                if template.get('ec2FleetConfig'):
                    # EC2 Fleet: LT IDs are listed inside the external JSON config file.
                    fleet_config = self._load_ec2_fleet_config(template)
                    for lt_config in fleet_config.get('LaunchTemplateConfigs', []):
                        spec = lt_config.get('LaunchTemplateSpecification', {})
                        lt_id = spec.get('LaunchTemplateId')
                        if lt_id:
                            lt_ids_to_check.append(lt_id)

                elif template.get('launchTemplateId'):
                    # Spot Fleet with launchTemplateId: the LT ID is directly on the template
                    # dict — no external file exists.  A temporary version was created during
                    # fleet submission and must be cleaned up here.
                    lt_ids_to_check.append(template['launchTemplateId'])
                    logger.debug(
                        f"Spot Fleet {request_id} uses launchTemplateId={template['launchTemplateId']}"
                        f" — scanning for automation-created versions"
                    )

                else:
                    # Spot Fleet with flat LaunchSpecifications: no launchTemplateId means no
                    # temporary LT version was ever created.  Nothing to clean up.
                    logger.debug(
                        f"Template {template_id} uses LaunchSpecifications (no launchTemplateId),"
                        f" no LT version cleanup needed for {request_id}"
                    )
                    return

            except Exception as e:
                logger.warning(f"Could not resolve launch template IDs for {request_id}: {e} — skipping cleanup")
                return

            if not lt_ids_to_check:
                logger.debug(f"No launch template IDs found in fleet config for {request_id}, nothing to clean up")
                return

            logger.debug(f"Checking {len(lt_ids_to_check)} launch template(s) for automation-created versions: {lt_ids_to_check}")

            # Paginate through all versions for each LT and collect those that:
            #   1. Start with the automation description prefix (template_id excluded —
            #      operators may rename templates)
            #   2. Are not the current default version (IsDefaultVersion == False)
            # Collecting first and bulk-deleting in one pass is far more efficient
            # than one DeleteLaunchTemplateVersions call per version.
            versions_to_delete: List[Dict] = []

            for lt_id in lt_ids_to_check:
                try:
                    next_token = None
                    while True:
                        kwargs: Dict[str, Any] = {'LaunchTemplateId': lt_id}
                        if next_token:
                            kwargs['NextToken'] = next_token
                        versions_response = self.ec2.describe_launch_template_versions(**kwargs)

                        for version in versions_response.get('LaunchTemplateVersions', []):
                            version_desc = version.get('VersionDescription', '')
                            version_num = version['VersionNumber']
                            # IsDefaultVersion is the correct guard — the default is
                            # not always version 1; it can be changed at any time.
                            is_default = version.get('IsDefaultVersion', False)

                            if version_desc.startswith(self._LT_AUTOMATION_DESC_PREFIX) and not is_default:
                                versions_to_delete.append({'lt_id': lt_id, 'version': version_num})

                        next_token = versions_response.get('NextToken')
                        if not next_token:
                            break

                except ClientError as e:
                    logger.warning(f"Failed to describe versions for launch template {lt_id}: {e}")
                    continue

            if not versions_to_delete:
                logger.debug(f"No automation-created launch template versions found for fleet request {request_id}")
                return

            logger.debug(f"Deleting {len(versions_to_delete)} automation-created launch template version(s) for fleet {request_id}")
            self._delete_lt_versions_by_id(versions_to_delete)
            logger.debug(f"Completed launch template version cleanup for fleet request {request_id}")

        except Exception as e:
            logger.error(f"Error during launch template version cleanup for fleet {request_id}: {e}")
            logger.debug(f"Launch template cleanup stack trace:", exc_info=True)

    def _get_instance_cpu_info(self, instance_id: str) -> Dict[str, int]:
        """Get actual CPU information from instance - returns ncores and nthreads"""
        try:
            response = self.ec2.describe_instances(InstanceIds=[instance_id])
            if response['Reservations'] and response['Reservations'][0]['Instances']:
                instance = response['Reservations'][0]['Instances'][0]
                cpu_options = instance.get('CpuOptions', {})
                core_count = cpu_options.get('CoreCount', 1)
                threads_per_core = cpu_options.get('ThreadsPerCore', 1)
                nthreads = core_count * threads_per_core

                logger.debug(f"Instance {instance_id}: CoreCount={core_count}, ThreadsPerCore={threads_per_core}, nthreads={nthreads}")
                return {
                    'ncores': core_count,
                    'nthreads': nthreads
                }
        except Exception as e:
            logger.error(f"Error getting CPU info for instance {instance_id}: {e}")

        return {'ncores': 1, 'nthreads': 1}

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

                # Get template via the per-process cache — avoids re-reading the
                # template JSON file on every poll cycle within this process.
                request_data = db_manager.get_request(fleet_id)
                rc_account = request_data.get('rcAccount', 'default') if request_data else 'default'
                template = self._get_template_for_request(fleet_id)
                template_id = template.get('templateId', 'unknown') if template else 'unknown'
                logger.debug(f"Retrieved template {template_id} for fleet instances")

                # Build machine data for all active instances. add_machines_to_request
                # deduplicates inside its own lock, which closes the race window between
                # concurrent getRequestStatus calls both discovering the same new instances.
                batch_machine_data = []
                for instance_id in active_instance_ids:
                    logger.debug(f"EC2 Fleet instance {instance_id} - CPU info will be retrieved when running")
                    machine_data = self._create_machine_data(
                        instance_id=instance_id,
                        template=template,
                        request_id=fleet_id,
                        rc_account=rc_account,
                        name=f"host-{instance_id}",
                        ncores=0,
                        nthreads=0
                    )
                    batch_machine_data.append(machine_data)

                result = db_manager.add_machines_to_request(fleet_id, batch_machine_data)
                if result['success_count'] > 0:
                    logger.info(f"Batch added {result['success_count']} new EC2 Request Fleet instances to database")
                if result['failed_count'] > 0:
                    logger.debug(f"{result['failed_count']} EC2 Fleet instances already in database (concurrent poll or re-poll)")

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

    def request_return_machines(self, instance_ids: List[str]) -> str:
        """Terminate EC2 instances using AWS batch API"""
        logger.debug(f"Starting request_return_machines for {len(instance_ids)} instances")

        # Check credentials once at the beginning
        self._refresh_credentials_if_needed()

        request_id = f"remove-{os.getpid()}-{int(time.time())}"
        logger.info(f"Starting batch instance termination request {request_id} for {len(instance_ids)} instances")

        # Spot reclaim pre-check - When AWS_SPOT_TERMINATE_ON_RECLAIM is False, spot instances that already have a
        # reclaim notice should NOT be sent an explicit terminate — AWS is handling that.
        # We bulk-describe all instances first and remove already-reclaiming spot instances
        # from the terminate list, marking them as done in the DB directly.
        if not self.spot_terminate_on_reclaim and instance_ids:
            ids_to_skip: List[str] = []
            skip_updates: List[Dict] = []
            try:
                bulk = self.get_instance_details_bulk(instance_ids)
                for iid, details in bulk.items():
                    if details.get('lifecycle') == 'spot':
                        if self._has_spot_termination_notice(iid, details):
                            ids_to_skip.append(iid)
                            logger.info(
                                f"Instance {iid} is a spot instance with a reclaim notice — "
                                "skipping explicit terminate (AWS will terminate it)"
                            )
                            machine_info = db_manager.get_request_for_machine(iid)
                            if machine_info and machine_info.get('request'):
                                skip_updates.append({
                                    'request_id': machine_info['request']['requestId'],
                                    'machine_id': iid,
                                    'status': 'shutting-down',
                                    'result': 'executing',
                                    'message': 'Spot instance termination deferred — AWS reclaim in progress',
                                    'return_id': request_id,
                                })
            except Exception as pre_check_err:
                logger.warning(f"Spot reclaim pre-check failed — proceeding to terminate all instances: {pre_check_err}")
                ids_to_skip = []
                skip_updates = []

            if ids_to_skip:
                instance_ids = [iid for iid in instance_ids if iid not in set(ids_to_skip)]
                if skip_updates:
                    db_manager.update_machines(skip_updates)
                logger.info(
                    f"Skipped {len(ids_to_skip)} spot-reclaiming instance(s); "
                    f"{len(instance_ids)} remaining for explicit termination"
                )

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
                                self.ec2.terminate_instances(InstanceIds=existing_instances)

                                # Update successful terminations
                                successful_terminations.extend(existing_instances)

                                # Collect DB updates into all_updates so the single
                                # consolidated batch write at the end of the loop covers
                                # both the normal path and this retry path.
                                for instance_id in existing_instances:
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

    def _cleanup_orphan_instances(self, lsf_machine_ids: set) -> None:
        """Terminate AWS instances that LSF has never accepted and have exceeded INSTANCE_CREATION_TIMEOUT"""
        current_time = int(time.time())
        timeout_seconds = self.instance_creation_timeout * 60
        orphan_ids: List[str] = []

        try:
            for request in db_manager.get_all_requests():
                for machine in request.get('machines', []):
                    machine_id = machine.get('machineId')
                    if not machine_id:
                        continue
                    # LSF tracks this machine — not an orphan
                    if machine_id in lsf_machine_ids:
                        continue
                    # No return request already in flight
                    if machine.get('retId'):
                        continue
                    # Only running/pending instances need cleanup; terminated are already done
                    if machine.get('status') not in ('running', 'pending'):
                        continue
                    launch_time = machine.get('launchtime', 0)
                    if not launch_time:
                        continue
                    age_seconds = current_time - int(launch_time)
                    if age_seconds > timeout_seconds:
                        logger.warning(f"Orphan instance {machine_id} has been {machine.get('status')} "
                            f"for {age_seconds // 60} minute(s) without being accepted into LSF "
                            f"(timeout={self.instance_creation_timeout} min) — scheduling termination"
                        )
                        orphan_ids.append(machine_id)
        except Exception as e:
            logger.error(f"Error scanning for orphan instances: {e}")
            return

        if not orphan_ids:
            logger.debug("No orphan instances found")
            return

        logger.info(f"Terminating {len(orphan_ids)} orphan instance(s): {orphan_ids}")
        try:
            for i in range(0, len(orphan_ids), self.batch_size):
                chunk = orphan_ids[i:i + self.batch_size]
                self.ec2.terminate_instances(InstanceIds=chunk)
                logger.debug(f"Orphan termination chunk sent: {chunk}")
        except Exception as e:
            logger.error(f"Failed to terminate orphan instances {orphan_ids}: {e}")

    def _get_spot_grace_period(self, spot_request_id: str) -> int:
        """Return remaining seconds in the 2-minute spot termination window"""
        try:
            sir_response = self.ec2.describe_spot_instance_requests(
                SpotInstanceRequestIds=[spot_request_id]
            )
            requests = sir_response.get('SpotInstanceRequests', [])
            if not requests:
                return 0
            update_time = requests[0].get('Status', {}).get('UpdateTime')
            if not update_time:
                return 0
            # update_time is a datetime object from boto3
            elapsed = int(time.time()) - int(update_time.timestamp())
            return max(0, 120 - elapsed)
        except Exception as e:
            logger.debug(f"Could not compute grace period for SIR {spot_request_id}: {e}")
            return 0

    def _check_spot_reclaim_for_return(self, machines: List[Dict[str, str]]) -> List[Dict]:
        """Return reclaim/closed entries for spot instances that are still running"""
        if not machines:
            return []

        machine_map = {m['machineId']: m.get('name', f"host-{m['machineId']}") for m in machines}
        instance_ids = list(machine_map.keys())

        # Single bulk describe for all candidate machines
        bulk = self.get_instance_details_bulk(instance_ids)

        # Only check running spot instances
        spot_candidates = {
            iid: details for iid, details in bulk.items()
            if details.get('lifecycle') == 'spot' and details.get('state') == 'running'
        }

        if not spot_candidates:
            logger.debug("No running spot instances to check for reclaim notices")
            return []

        reclaim_entries: List[Dict] = []
        for instance_id, details in spot_candidates.items():
            spot_request_id = details.get('SpotInstanceRequestId')
            if not spot_request_id:
                continue
            try:
                sir_response = self.ec2.describe_spot_instance_requests(
                    SpotInstanceRequestIds=[spot_request_id]
                )
                sirs = sir_response.get('SpotInstanceRequests', [])
                if not sirs:
                    continue
                sir = sirs[0]
                status_code = sir.get('Status', {}).get('Code', '')
                sir_state = sir.get('State', '')

                # Stage 1: 2-minute reclaim notice
                if status_code == 'marked-for-termination':
                    grace = self._get_spot_grace_period(spot_request_id)
                    logger.info(
                        f"Instance {instance_id} has reclaim notice "
                        f"(marked-for-termination), gracePeriod={grace}s"
                    )
                    reclaim_entries.append({
                        "machineId": instance_id,
                        "machine": machine_map[instance_id],
                        "gracePeriod": grace
                    })
                # Stage 3: SIR closed (capacity/schedule/etc.)
                elif sir_state == 'closed':
                    logger.warning(
                        f"Instance {instance_id} Spot request {spot_request_id} "
                        f"is closed (status={status_code}) — adding to return list"
                    )
                    reclaim_entries.append({
                        "machineId": instance_id,
                        "machine": machine_map[instance_id],
                        "gracePeriod": 0
                    })
            except ClientError as e:
                logger.debug(
                    f"Could not describe SIR {spot_request_id} for {instance_id}: "
                    f"{e.response['Error']['Code']}"
                )

        logger.debug(f"_check_spot_reclaim_for_return found {len(reclaim_entries)} reclaiming instance(s)")
        return reclaim_entries

    def get_return_requests(self, machines: List[Dict[str, str]]) -> Dict[str, Any]:
        """Check if instances are terminated - return consistent format"""
        instance_ids = [machine['machineId'] for machine in machines]
        logger.debug(f"Starting get_return_requests for {len(instance_ids)} instances")

        try:
            # Terminate instances that have been running/pending beyond INSTANCE_CREATION_TIMEOUT
            # without being accepted into LSF's hosts.json
            lsf_machine_ids = {m['machineId'] for m in machines}
            self._cleanup_orphan_instances(lsf_machine_ids)

            logger.debug(f"Checking terminated instances: {instance_ids}")
            terminated_instance_ids = self._find_terminated_instances(instance_ids)
            logger.debug(f"Found terminated instances: {terminated_instance_ids}")

            # Build response entries for terminated instances (no gracePeriod needed)
            machine_map = {machine['machineId']: machine['name'] for machine in machines}
            requests = []
            seen_ids: set = set()
            for instance_id in terminated_instance_ids:
                requests.append({
                    "machineId": instance_id,
                    "machine": machine_map.get(instance_id, f'host-{instance_id}')
                })
                seen_ids.add(instance_id)
                logger.debug(f"Added terminated instance to results: {instance_id}")

            # Add running spot instances with reclaim/closed notices, including gracePeriod.
            reclaim_entries = self._check_spot_reclaim_for_return(machines)
            for entry in reclaim_entries:
                if entry['machineId'] not in seen_ids:
                    requests.append(entry)
                    seen_ids.add(entry['machineId'])
                    logger.debug(
                        f"Added reclaiming instance to results: {entry['machineId']} "
                        f"gracePeriod={entry.get('gracePeriod', 0)}s"
                    )

            result = {
                "status": "complete",
                "message": f"Found {len(requests)} instance(s) to return" if requests else "No instances to return",
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
                    if machine_info and machine_info.get('request') and machine_info.get('machine', {}).get('status') != 'terminated':
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
            instances_list = list(instances)
            # Instance may already be gone (fully terminated) — treat as terminated
            # rather than raising IndexError or returning the misleading 'unknown' state.
            if not instances_list:
                logger.debug(f"Instance {instance_id} not found — treating as terminated")
                return {'state': 'terminated', 'state_reason': None}
            instance = instances_list[0]
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
        """Get details for multiple instances.

        Chunks of up to `chunk_size` IDs are described in parallel via the
        shared thread pool, eliminating the serial sleep between chunks and
        reducing wall-clock latency proportionally to the number of chunks.
        """
        logger.debug(f"get_instance_details_bulk called for {len(instance_ids)} instances")
        if not instance_ids:
            return {}

        def _describe_chunk(chunk: List[str]) -> Dict[str, Dict[str, Any]]:
            chunk_result = {}
            try:
                instances = self.ec2_resource.instances.filter(InstanceIds=chunk)
                found_ids = set()

                for instance in instances:
                    found_ids.add(instance.id)
                    state = instance.state['Name']

                    state_reason = None
                    if hasattr(instance, 'state_reason') and instance.state_reason:
                        state_reason = {
                            'Code': instance.state_reason.get('Code'),
                            'Message': instance.state_reason.get('Message')
                        }

                    spot_request_id = getattr(instance, 'spot_instance_request_id', None)
                    lifecycle = 'ondemand'
                    try:
                        if hasattr(instance, 'instance_lifecycle') and instance.instance_lifecycle:
                            lifecycle = instance.instance_lifecycle
                        elif spot_request_id:
                            lifecycle = 'spot'
                    except Exception:
                        lifecycle = 'ondemand'

                    chunk_result[instance.id] = {
                        'state': state,
                        'privateIpAddress': instance.private_ip_address,
                        'publicIpAddress': instance.public_ip_address,
                        'name': instance.private_dns_name,
                        'publicDnsName': instance.public_dns_name,
                        'launchtime': instance.launch_time.timestamp() if instance.launch_time else None,
                        'lifecycle': lifecycle,
                        'state_reason': state_reason,
                        # Stored so _has_spot_termination_notice can use it without
                        # a redundant per-instance describe_instances call.
                        'SpotInstanceRequestId': spot_request_id,
                        'source': 'bulk'
                    }

                # Instances not returned by AWS are already terminated
                for missing_id in set(chunk) - found_ids:
                    chunk_result[missing_id] = {
                        'state': 'terminated',
                        'privateIpAddress': None, 'publicIpAddress': None,
                        'name': None, 'publicDnsName': None,
                        'launchtime': None, 'lifecycle': None,
                        'state_reason': None, 'source': 'bulk-missing'
                    }

            except ClientError as e:
                logger.warning(f"Bulk describe failed for chunk of {len(chunk)}: {e}")
                for instance_id in chunk:
                    chunk_result[instance_id] = {
                        'state': 'unknown',
                        'privateIpAddress': None, 'publicIpAddress': None,
                        'name': None, 'publicDnsName': None,
                        'launchtime': None, 'lifecycle': None,
                        'state_reason': None, 'source': 'bulk-error'
                    }

            return chunk_result

        chunks = [instance_ids[i:i + chunk_size] for i in range(0, len(instance_ids), chunk_size)]

        # Single chunk — skip pool overhead
        if len(chunks) == 1:
            return _describe_chunk(chunks[0])

        # Multiple chunks — describe in parallel
        self._init_vm_pool()
        result = {}
        futures = {self.vm_pool.submit(_describe_chunk, chunk): chunk for chunk in chunks}
        for future in as_completed(futures):
            try:
                result.update(future.result())
            except Exception as e:
                logger.error(f"Unexpected error in parallel describe chunk: {e}")
                for instance_id in futures[future]:
                    result[instance_id] = {
                        'state': 'unknown',
                        'privateIpAddress': None, 'publicIpAddress': None,
                        'name': None, 'publicDnsName': None,
                        'launchtime': None, 'lifecycle': None,
                        'state_reason': None, 'source': 'bulk-error'
                    }

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
                    return {'status': 'complete_with_error', 'message': 'Spot Fleet not found. Error Code: InternalError'}

                fleet_config = response['SpotFleetRequestConfigs'][0]
                fleet_state = fleet_config.get('SpotFleetRequestState', '')
                activity_status = fleet_config.get('ActivityStatus', '')

                logger.debug(f"Spot Fleet {request_id} - State: {fleet_state}, Activity Status: {activity_status}")

                # Only shortcut on states where we are certain no live instances remain.
                # cancelled             — fleet cancelled AND all instances terminated → safe shortcut
                # cancelled_terminating — fleet cancelled, instances mid-termination → signal complete
                # cancelled_running     — fleet cancelled but instances STILL RUNNING (TerminateInstances=false)
                #                        → must fall through to instance-level polling so LSF sees the machines
                if fleet_state == 'cancelled':
                    return {'status': 'complete', 'message': 'Spot Fleet cancelled'}
                elif fleet_state == 'cancelled_terminating':
                    return {'status': 'complete', 'message': 'Spot Fleet cancelled (terminating)'}
                elif fleet_state == 'failed':
                    return {'status': 'complete_with_error', 'message': 'Spot Fleet failed. Error Code: InternalError'}
                elif fleet_state == 'active' and activity_status in ['fulfilled', 'fulfilled_partial']:
                    # Fleet fully/partially fulfilled — no more instances will be added.
                    # Fall through to instance-level check so machines[] is populated.
                    return None
                # For cancelled_running, active/pending, submitted, etc.
                # return None → fall through to _process_creation_machines for full instance detail

            elif request_id.startswith("fleet-"):
                # EC2 Fleet status check
                response = self.ec2.describe_fleets(FleetIds=[request_id])
                if not response.get('Fleets'):
                    logger.warning(f"EC2 Fleet {request_id} not found")
                    return {'status': 'complete_with_error', 'message': 'EC2 Fleet not found. Error Code: InternalError'}

                fleet = response['Fleets'][0]
                fleet_state = fleet.get('State', '')
                fleet_errors = fleet.get('Errors', [])

                logger.debug(f"EC2 Fleet {request_id} - State: {fleet_state}, Errors: {len(fleet_errors)}")

                # deleted                — fleet deleted AND instances terminated → safe shortcut
                # deleted_running        — request-type fleet expired; instances never launched → InsufficientCapacity
                # deleted_terminating    — request-type fleet expired; instances never launched → InsufficientCapacity
                if fleet_state == 'deleted':
                    return {'status': 'complete', 'message': 'EC2 Fleet deleted'}
                elif fleet_state == 'failed':
                    return {'status': 'complete_with_error', 'message': 'EC2 Fleet failed. Error Code: InternalError'}
                elif fleet_state in ('deleted_running', 'deleted_terminating'):
                    # Request-type fleet timed out without fulfilling target capacity.
                    # Signal InsufficientCapacity so LSF temporarily disables the template if configured.
                    logger.warning(f"EC2 Fleet {request_id} expired without full fulfillment with state "
                        f"(state={fleet_state}) — signalling InsufficientCapacity to LSF"
                    )
                    return {'status': 'complete', 'message': 'Error Code: InsufficientCapacity'}
                # Log any partial capacity errors for diagnostics, then fall through
                if fleet_errors:
                    error_messages = [e.get('ErrorMessage', 'Unknown error') for e in fleet_errors]
                    logger.warning(f"EC2 Fleet {request_id} partial capacity errors (informational): {'; '.join(error_messages)}")
                # For 'submitted', 'active', or any other non-terminal state, return None

            # If we can't definitively determine status from fleet state, return None
            return None

        except ClientError as e:
            error_msg = self._format_error_message(f"Fleet status check failed for {request_id}", e)
            logger.warning(f"Error checking {request_id} fleet status: {error_msg}")
            if 'NotFound' in e.response['Error']['Code']:
                return {'status': 'complete_with_error', 'message': error_msg}
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
                fleet_msg = fleet_status['message']

                # Terminal error — raise so the caller (request_manager) can surface it
                # as a top-level "message" in the JSON response that ebrokerd reads.
                if fleet_status['status'] == 'complete_with_error':
                    logger.warning(f"Fleet {request_id} terminal error: {fleet_msg}")
                    raise Exception(fleet_msg)

                # Spot Fleet cancelled with zero instances = ValidUntil expired unfulfilled.
                # Treat as InsufficientCapacity so the template is disabled if configured.
                if (fleet_status['status'] == 'complete'
                        and fleet_msg == 'Spot Fleet cancelled'
                        and not machines):
                    msg = 'Spot Fleet expired without fulfilling capacity. Error Code: InsufficientCapacity'
                    logger.warning(f"Fleet {request_id}: {msg}")
                    raise Exception(msg)

                # Normal completion (cancelled with instances, deleted, etc.) — return as-is.
                return {
                    'status': fleet_status['status'],
                    'message': fleet_msg,
                    'machines': [],
                    'requestId': request_id
                }

            # Check for fleet requests with no machines and apply timeout
            if not machines:
                request_creation_time = request_data.get('time', 0)
                current_time = int(datetime.now().timestamp() * 1000)

                # Validate request_creation_time before calculating timeout
                if not request_creation_time or request_creation_time == 0:
                    logger.warning(f"Invalid or missing creation time for request {request_id}, using current time")
                    request_creation_time = current_time

                request_age_minutes = (current_time - request_creation_time) / 60000

                # If request is too old without any machines, mark as failed — raise so the
                # caller surfaces it as a top-level message ebrokerd can parse.
                if request_age_minutes > 30:  # 30-minute timeout
                    msg = (f'Fleet request timed out after {request_age_minutes:.1f} minutes '
                           f'with no instances launched. Error Code: InsufficientCapacity')
                    logger.warning(f"Fleet {request_id}: {msg}")
                    raise Exception(msg)
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
                # Check if hostname is valid (not placeholder)
                instance_name = instance_details.get('name', '')
                current_machine_name = machine.get('name', '')
                has_valid_hostname = (
                    instance_name and
                    not instance_name.startswith('host-i-') and
                    instance_name != f"host-{instance_id}"
                )

                if has_valid_hostname:
                    # Instance fully ready with valid hostname
                    update['result'] = 'succeed'
                    update['message'] = 'Instance running successfully'
                    update['name'] = instance_name
                    updated_machine['name'] = instance_name

                    # Get actual CPU info once — sentinel value 0 means not yet fetched
                    if machine.get('ncores', 0) == 0:
                        cpu_info = self._get_instance_cpu_info(instance_id)
                        logger.debug(f"Instance {instance_id} CPU info fetched: ncores={cpu_info['ncores']}, nthreads={cpu_info['nthreads']}")
                        update['ncores'] = cpu_info['ncores']
                        update['nthreads'] = cpu_info['nthreads']
                        updated_machine['ncores'] = cpu_info['ncores']
                        updated_machine['nthreads'] = cpu_info['nthreads']
                else:
                    # Instance running but hostname not yet resolved — apply a timeout
                    # so the request does not stay in 'running' forever when all other
                    # machines have already failed.
                    current_time = int(time.time())
                    launch_time = machine.get('launchtime', 0)
                    if launch_time == 0:
                        launch_time = current_time
                    no_hostname_duration_minutes = (current_time - launch_time) / 60

                    if no_hostname_duration_minutes > 60:
                        update['result'] = 'fail'
                        update['message'] = (
                            f'Instance running without valid hostname for '
                            f'{no_hostname_duration_minutes:.1f} minutes - timeout exceeded'
                        )
                        update['status'] = 'failed'
                        any_failed = True
                        logger.warning(
                            f"Instance {instance_id} timed out waiting for hostname "
                            f"({no_hostname_duration_minutes:.1f} minutes)"
                        )
                    else:
                        update['result'] = 'executing'
                        update['message'] = 'Instance running - waiting for hostname resolution'
                        all_complete = False  # Keep request in 'running' state
                        logger.debug(
                            f"Instance {instance_id} is running but hostname not yet resolved "
                            f"(current: {instance_name or current_machine_name}, "
                            f"{no_hostname_duration_minutes:.1f} minutes elapsed)"
                        )

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
                if instance_details.get('lifecycle'):
                    update['lifecycle'] = instance_details['lifecycle']
                    updated_machine['lifeCycleType'] = instance_details['lifecycle']

                # InstanceId tagging for running instances (only if hostname is valid).
                # Submitted to the pool so it does not block the status-check response.
                if has_valid_hostname and self.instance_id_tag_enabled and not machine.get('tagInstanceId', False):
                    self._init_vm_pool()
                    self.vm_pool.submit(self._tag_instance_with_instance_id, instance_id)
                    update['tag_instance_id'] = True
                    updated_machine['tagInstanceId'] = True

            elif current_aws_state in ('stopping', 'stopped'):
                # Transient states: AWS is stopping the instance before it terminates
                # Give it one more poll cycle rather than permanently failing it here.
                update['result'] = 'executing'
                update['message'] = f'Instance is {current_aws_state} - waiting for terminal state'
                all_complete = False

            else:  # shutting-down, terminated, and any other unexpected state
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
                # In-progress termination — normal path
                update['result'] = 'executing'
                update['message'] = 'Instance is being terminated'
                update['return_id'] = request_id
                all_complete = False

            elif current_aws_state == 'stopping':
                # AWS stops the instance before terminating it in some configurations.
                update['result'] = 'executing'
                update['message'] = 'Instance is stopping before termination'
                update['return_id'] = request_id
                all_complete = False

            elif current_aws_state == 'terminated':
                # Successfully gone
                update['result'] = 'succeed'
                update['message'] = 'Instance terminated successfully'
                update['return_id'] = request_id
                machines_to_remove.append({
                    'request_id': machine_request_id,
                    'machine_id': instance_id
                })

            elif current_aws_state == 'stopped':
                # AWS will transition stopped → shutting-down → terminated after a terminate call.
                update['result'] = 'executing'
                update['message'] = 'Instance stopped - waiting for terminated confirmation'
                update['return_id'] = request_id
                all_complete = False

            elif current_aws_state == 'running':
                # Still running after terminate was issued — genuine failure
                update['result'] = 'fail'
                update['message'] = 'Instance still running - termination may have failed'
                update['return_id'] = request_id
                any_failed = True
                all_complete = False

            elif current_aws_state == 'unknown':
                # Produced by get_instance_details_bulk when describe_instances hits a
                # transient API error (ClientError). The real AWS state is not known.
                # Retry on next poll — do not mark as failed permanently.
                update['result'] = 'executing'
                update['message'] = 'Instance state temporarily unknown - retrying'
                update['return_id'] = request_id
                all_complete = False

            else:  # pending or any other unexpected state
                # pending during deletion is a race condition; retry rather than fail permanently.
                update['result'] = 'executing'
                update['message'] = f'Instance in unexpected state during termination: {current_aws_state}'
                update['return_id'] = request_id
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

        # Periodic cleanup — submitted to the pool so it does not block the LSF response
        if self.vm_pool:
            self.vm_pool.submit(self.periodic_cleanup)
        else:
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

        # Cleanup launch template versions BEFORE removing fleet requests.
        # Both EC2 Fleet ('fleet-') and Spot Fleet ('sfr-') requests may hold
        # temporary LT versions that must be deleted before the DB row is gone.
        fleet_requests_to_cleanup = set()
        for req_id in removals_by_request.keys():
            if req_id.startswith(('fleet-', 'sfr-')):
                fleet_requests_to_cleanup.add(req_id)

        # Cleanup BEFORE removing the requests
        for fleet_request_id in fleet_requests_to_cleanup:
            self._delete_lt_versions_by_description(fleet_request_id)

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

            # Clean up temporary launch template versions for any EC2 fleet requests that were pruned from the DB.
            # db_manager.cleanup_old_data() returns {requestId, templateId} dicts so we can pass templateId
            # directly — the DB rows are already gone at this point and a DB lookup would always miss.
            for entry in stats.get('removed_fleet_requests', []):
                logger.debug(f"Running launch template version cleanup for pruned fleet: {entry['requestId']}")
                self._delete_lt_versions_by_description(entry['requestId'], entry['templateId'])

            return stats

        logger.debug("Cleanup interval not reached, skipping cleanup")
        return {'empty_requests_removed': 0, 'terminated_machines_removed': 0, 'removed_fleet_requests': []}

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
            # Get all active Spot instances together with their already-fetched bulk
            # details so _has_spot_termination_notice can skip the redundant
            # per-instance describe_instances call (eliminating the N+1 pattern).
            active_instances_map = self._get_active_spot_instances()

            if not active_instances_map:
                return

            instances_to_terminate = []
            updates = []  # Collect batch updates

            # Check all active instances for termination notices in parallel.
            # Only the SIR-specific describe_spot_instance_requests call (Stage 1)
            # is made per-instance; the describe_instances data is reused from the
            # bulk fetch performed inside _get_active_spot_instances.
            self._init_vm_pool()
            notice_futures = {
                self.vm_pool.submit(
                    self._has_spot_termination_notice, iid, details
                ): iid
                for iid, details in active_instances_map.items()
            }
            for future in as_completed(notice_futures):
                instance_id = notice_futures[future]
                try:
                    has_notice = future.result()
                except Exception as e:
                    logger.error(f"Error checking termination notice for {instance_id}: {e}")
                    has_notice = False

                if has_notice:
                    logger.info(f"Spot instance {instance_id} has reclaim notice, marking for termination")
                    instances_to_terminate.append(instance_id)

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
                termination_request_id = self.request_return_machines(instances_to_terminate)

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

    def _get_active_spot_instances(self) -> Dict[str, Dict[str, Any]]:
        """Return a {instance_id: bulk_details} map for all active Spot instances.

        Performing a single bulk describe_instances here means callers can reuse
        the already-fetched data and avoid a second per-instance describe call.
        Returns an empty dict when no Spot instances are found.
        """
        try:
            all_requests = db_manager.get_all_requests()

            candidate_instance_ids = []
            for request in all_requests:
                if 'machines' in request:
                    for machine in request['machines']:
                        if (machine.get('status') in ['running', 'pending'] and
                                machine.get('machineId')):
                            candidate_instance_ids.append(machine['machineId'])

            if not candidate_instance_ids:
                logger.debug("No candidate instances found for Spot check")
                return {}

            logger.debug(f"Checking {len(candidate_instance_ids)} candidate instances for Spot lifecycle")

            # Single bulk API call — details reused by _has_spot_termination_notice
            details_map = self.get_instance_details_bulk(candidate_instance_ids)

            active = {
                iid: details
                for iid, details in details_map.items()
                if details.get('lifecycle') == 'spot'
            }

            logger.debug(f"Found {len(active)} active Spot instances")
            return active

        except Exception as e:
            logger.error(f"Error getting active Spot instances: {e}")
            return {}

    def _has_spot_termination_notice(self, instance_id: str,
                                     prefetched_details: Optional[Dict[str, Any]] = None) -> bool:
        """Check if a Spot instance has a termination notice.

        Three-stage check:

        Stage 1 — ADVANCE NOTICE (the 2-minute warning window):
          AWS attaches a SpotInstanceRequestId to every Spot instance regardless
          of how it was launched — run_instances with InstanceMarketOptions='spot'
          (one-time SIR), request_spot_instances, Spot Fleet, or EC2 Fleet.
          During the ~2 minutes before termination, AWS sets the SIR Status.Code
          to 'marked-for-termination'. Querying describe_spot_instance_requests
          with that ID is the only control-plane API that surfaces the advance
          notice from outside the instance.

        Stage 2 — FALLBACK (post-hoc confirmation):
          StateTransitionReason in describe_instances only populates after the
          termination sequence has already started (instance is shutting-down or
          terminated). Kept as a backstop for instances already past the 2-minute
          window.

        Stage 3 — CLOSED FOR NON-PRICE REASONS:
          When the SIR State is 'closed' (capacity exhausted, schedule,
          experiment, etc.) the instance will never start or has already been
          terminated by AWS for a non-reclaim reason. Surface these to LSF so it
          can reschedule the job rather than waiting indefinitely.

        prefetched_details: when provided (from _get_active_spot_instances bulk
          call) the describe_instances round-trip is skipped entirely, saving one
          AWS API call per instance per monitor cycle.
        """
        try:
            if prefetched_details is not None:
                # Reuse the data already fetched by get_instance_details_bulk.
                # The bulk response exposes state_reason and spot_instance_request_id
                # via the ec2_resource attributes stored in the detail dict.
                # We still need the raw SpotInstanceRequestId for Stage 1, so fall
                # through to a targeted describe_instances only if it is absent.
                spot_request_id = prefetched_details.get('SpotInstanceRequestId')
                state_reason = prefetched_details.get('state_reason') or {}
                state_reason_str = state_reason.get('Message', '') if isinstance(state_reason, dict) else str(state_reason)

                # If Stage 1 data is available from the bulk response, use it directly.
                if spot_request_id:
                    try:
                        sir_response = self.ec2.describe_spot_instance_requests(
                            SpotInstanceRequestIds=[spot_request_id]
                        )
                        requests = sir_response.get('SpotInstanceRequests', [])
                        if requests:
                            sir = requests[0]
                            status_code = sir.get('Status', {}).get('Code', '')
                            sir_state = sir.get('State', '')
                            logger.debug(f"Instance {instance_id} SpotInstanceRequest "
                                         f"{spot_request_id} status: {status_code}, state: {sir_state}")
                            # Stage 1: 2-minute reclaim advance notice
                            if status_code == 'marked-for-termination':
                                logger.info(f"Instance {instance_id} has advance Spot "
                                            f"termination notice (marked-for-termination)")
                                return True
                            # Stage 3: SIR closed for non-price/non-reclaim reasons
                            # (no-capacity, capacity-oversubscribed, schedule, experiment, etc.)
                            if sir_state == 'closed':
                                logger.warning(f"Instance {instance_id} Spot request {spot_request_id} is closed (status={status_code})")
                                return True
                    except ClientError as e:
                        logger.debug(f"Could not describe SpotInstanceRequest {spot_request_id} "
                                     f"for {instance_id}: {e.response['Error']['Code']}")

                # Stage 2 from prefetched data
                logger.debug(f"Instance {instance_id} StateTransitionReason (prefetched): {state_reason_str}")
                spot_indicators = [
                    'spot instance termination',
                    'server.spotinstanceterminationnotice',
                    'marked for termination',
                    'instance-termination'
                ]
                if any(indicator in state_reason_str.lower() for indicator in spot_indicators):
                    logger.info(f"Instance {instance_id} has Spot termination notice "
                                f"(StateTransitionReason, prefetched): {state_reason_str}")
                    return True

                # SpotInstanceRequestId not in bulk details — fall through to full describe.
                # When spot_request_id IS present the SIR check above already ran and
                # returned True on a match; reaching here means no termination was found,
                # so Stage 2 (StateTransitionReason) has already been evaluated and also
                # returned False.  We can safely return False and skip the full describe.
                if not spot_request_id:
                    logger.debug(f"SpotInstanceRequestId absent in prefetched data for "
                                 f"{instance_id}, falling back to describe_instances")
                else:
                    # Stage 2 already checked from prefetched state_reason above; nothing
                    # more to do in the prefetched path.
                    return False

            # ── Full describe_instances path (no prefetched data, or SIR ID missing) ──
            response = self.ec2.describe_instances(InstanceIds=[instance_id])

            if not response['Reservations']:
                return False

            instance = response['Reservations'][0]['Instances'][0]

            # Stage 1: advance notice via SpotInstanceRequest status
            spot_request_id = instance.get('SpotInstanceRequestId')
            if spot_request_id:
                try:
                    sir_response = self.ec2.describe_spot_instance_requests(
                        SpotInstanceRequestIds=[spot_request_id]
                    )
                    requests = sir_response.get('SpotInstanceRequests', [])
                    if requests:
                        sir = requests[0]
                        status_code = sir.get('Status', {}).get('Code', '')
                        sir_state = sir.get('State', '')
                        logger.debug(f"Instance {instance_id} SpotInstanceRequest "
                                     f"{spot_request_id} status: {status_code}, state: {sir_state}")
                        # Stage 1: 2-minute reclaim advance notice
                        if status_code == 'marked-for-termination':
                            logger.info(f"Instance {instance_id} has advance Spot "
                                        f"termination notice (marked-for-termination)")
                            return True
                        # Stage 3: SIR closed for non-price/non-reclaim reasons
                        # (no-capacity, capacity-oversubscribed, schedule, experiment, etc.)
                        if sir_state == 'closed':
                            logger.warning(f"Instance {instance_id} Spot request {spot_request_id} is closed (status={status_code})")
                            return True
                except ClientError as e:
                    # SIR may already be gone if termination is already in progress
                    logger.debug(f"Could not describe SpotInstanceRequest {spot_request_id} "
                                 f"for {instance_id}: {e.response['Error']['Code']}")

            # Stage 2: fallback — StateTransitionReason after termination starts
            state_reason = instance.get('StateTransitionReason', '')
            logger.debug(f"Instance {instance_id} StateTransitionReason: {state_reason}")

            spot_indicators = [
                'spot instance termination',
                'server.spotinstanceterminationnotice',
                'marked for termination',
                'instance-termination'
            ]

            if any(indicator in state_reason.lower() for indicator in spot_indicators):
                logger.info(f"Instance {instance_id} has Spot termination notice "
                            f"(StateTransitionReason): {state_reason}")
                return True

            return False

        except ClientError as e:
            if e.response['Error']['Code'] == 'InvalidInstanceID.NotFound':
                logger.debug(f"Instance {instance_id} not found during termination check")
            else:
                logger.error(f"Error checking termination notice for {instance_id}: {e}")
            return False
        except Exception as e:
            logger.error(f"Unexpected error checking termination notice for {instance_id}: {e}")
            return False
