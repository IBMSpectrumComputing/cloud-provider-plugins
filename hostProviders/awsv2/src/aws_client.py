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
from concurrent.futures import ThreadPoolExecutor, as_completed
import multiprocessing
from contextlib import contextmanager
from db_manager import db_manager
from config_manager import config_manager

logger = logging.getLogger(__name__)

class AWSClient:
    def __init__(self):
        try:
            # Get region using the config manager instance
            self.region = config_manager.get_region()
            logger.info(f"Using AWS region: {self.region}")
            
            # Credential caching
            self.credentials = None
            self.credentials_expiry = None
            self.credentials_lock = threading.RLock()
            
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
            else:
                logger.debug("Creating session without explicit credentials (using IAM role)")
                self.session = boto3.Session(region_name=self.region)
            
            # Validate credentials only if not using IAM role
            if self.credentials and not config_manager.validate_aws_credentials(self.credentials):
                raise ValueError("AWS credentials validation failed")
            
            # Create clients from the session
            self.ec2 = self.session.client('ec2')
            self.ec2_resource = self.session.resource('ec2')
            
            # Handle custom endpoint if configured
            endpoint_url = config_manager.get_aws_endpoint_url()
            if endpoint_url:
                logger.debug(f"Using custom endpoint URL: {endpoint_url}")
                self.ec2 = self.session.client('ec2', endpoint_url=endpoint_url)
                self.ec2_resource = self.session.resource('ec2', endpoint_url=endpoint_url)
            
            self._test_connection()
            
            self.requests = {}
            self.vm_pool = None
            self.min_vm_workers = int(os.getenv('AWS_MIN_WORKERS', '10'))
            self.max_vm_workers = int(os.getenv('AWS_MAX_WORKERS', '50'))
            logger.debug(f"AWSClient initialized with min_workers={self.min_vm_workers}, max_workers={self.max_vm_workers}")
            
        except Exception as e:
            logger.error(f"AWSClient initialization failed: {e}")
            raise

    def _get_cached_credentials(self) -> Dict[str, str]:
        """Get cached credentials or fetch new ones if expired"""
        with self.credentials_lock:
            current_time = time.time()
            
            # Return cached credentials if still valid (with 5-minute buffer)
            if (self.credentials and self.credentials_expiry and 
                current_time < self.credentials_expiry - 300):
                logger.debug("Using cached credentials")
                return self.credentials.copy()
            
            # Fetch new credentials
            return self._refresh_credentials()
    
    def _refresh_credentials(self) -> Dict[str, str]:
        """Refresh credentials and update cache"""
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
            logger.debug("AWS credentials refreshed")
            return credentials.copy()
    
    def _refresh_credentials_if_needed(self):
        """Refresh credentials if they're about to expire"""
        if not self.credentials_expiry:
            logger.debug("No credentials expiry set, skipping refresh check")
            return
        
        current_time = time.time()
        if current_time >= self.credentials_expiry - 300:  # 5-minute buffer
            with self.credentials_lock:
                # Double-check after acquiring lock
                if current_time >= self.credentials_expiry - 300:
                    logger.debug("Refreshing AWS credentials (near expiry)")
                    self._refresh_credentials()
        else:
            logger.debug(f"Credentials still valid for {self.credentials_expiry - current_time:.0f} seconds")

    def _test_connection(self):
        """Test AWS connection by making a simple API call"""
        try:
            # Check credentials first
            self._refresh_credentials_if_needed()
            
            # Try to describe regions to test connectivity
            self.ec2.describe_regions()
            logger.debug("AWS connection test successful - found {len(regions['Regions'])} regions")
        except Exception as e:
            logger.error(f"AWS connection test failed: {e}")
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
                # Fallback to sequential execution
                
    @contextmanager
    def resource_context(self):
        """Context manager for resource cleanup"""
        try:
            yield self
        finally:
            self.cleanup()
            
    def cleanup(self):
        """Clean up thread pool resources"""
        if self.vm_pool:
            try:
                logger.debug("Shutting down VM thread pool")
                self.vm_pool.shutdown(wait=True)
                self.vm_pool = None
                logger.debug("VM thread pool shutdown complete")
            except Exception as e:
                logger.error(f"Error shutting down VM thread pool: {str(e)}")
        
    def _create_single_instance(self, template: Dict, instance_index: int, request_id: str) -> Dict[str, Any]:
        """Create a single EC2 instance (thread-safe)"""
        try:                  
            # TODO:
            # 1. Support multiple subnets
            # 2. Support ipv6

            logger.debug(f"Creating instance {instance_index} for request {request_id} with template id: {template.get('templateId')}")
            # Build network interfaces
            network_interfaces = []
            subnet_id = template.get('subnetId')
            if subnet_id:
                logger.debug(f"Using subnet: {subnet_id}")
                interface_config = {
                    'DeviceIndex': 0,
                    'SubnetId': subnet_id,
                    'Groups': template.get('securityGroupIds', [])
                }
                
                if template.get('interfaceType', '').lower() == 'efa':
                    interface_config['InterfaceType'] = 'efa'
                    logger.debug("Configuring EFA interface")
                    
                network_interfaces.append(interface_config)
                        
            # Build user data, read your user data script from a file
            userData = ""
            script_dir = os.path.dirname(os.path.abspath(__file__))
            # TODO: update fixed path with variable
            user_data_file = os.path.join(script_dir, "../scripts/user_data.sh")
            
            if os.path.exists(user_data_file):
                try:
                    with open(user_data_file, "r") as f:
                        content = f.read().strip()
                        if content:  # only use if not empty
                            userData = content
                            logger.debug(f"Loaded user data from {user_data_file}")
                except Exception as e:
                    logger.warning(f"Failed to read user data file {user_data_file}: {e}")
            else:
                logger.debug(f"User data file not found: {user_data_file}")
                    
            # Parse instance tags if defined
            # TODO: update the tags to json than string
            instance_tags = []
            tags_string = template.get('instanceTags')
            if tags_string:
                logger.debug(f"Processing instance tags: {tags_string}")
                tag_pairs = [pair.strip() for pair in tags_string.split(',') if pair.strip()]
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
                    else:
                        logger.warning(f"Invalid tag format '{pair}', expected 'Key=Value'")

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

            # Build launch template
            launch_template = {}
            launch_template_id = template.get('launchTemplateId')
            if launch_template_id:
                launch_template = {
                    'LaunchTemplateId': launch_template_id,
                    'Version': template.get('launchTemplateVersion', '$Default')
                }
                logger.debug(f"Using launch template: {launch_template_id}, version: {launch_template['Version']}")
            
            # Build the base parameters
            instances_params = {
                'ImageId': template['imageId'],
                'InstanceType': template['vmType'],
                'MinCount': 1,
                'MaxCount': 1,
                'UserData': userData,
                'EbsOptimized': template.get('ebsOptimized', False)
            }

            # Add optional parameters only if they have values
            if network_interfaces:
                instances_params['NetworkInterfaces'] = network_interfaces
                logger.debug(f"Using NetworkInterfaces parameter: {network_interfaces}")
            else:
                # Fallback to individual network parameters if no network interfaces
                security_groups = template.get('securityGroupIds', [])
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
                logger.debug("Added InstanceMarketOptions: {market_options}")

            if placement:
                instances_params['Placement'] = placement
                logger.debug("Added Placement configuration: {placement}")

            if iam_profile:
                instances_params['IamInstanceProfile'] = iam_profile
                logger.debug("Added IAM instance profile: {iam_profile}")

            if launch_template:
                instances_params['LaunchTemplate'] = launch_template
                logger.debug("Added LaunchTemplate: {launch_template}")

            logger.debug(f"About to call run_instances for instance {instance_index} with instance parameters: {instances_params}")
            response = self.ec2.run_instances(**instances_params)
            instance = response['Instances'][0]
            logger.debug(f"Successfully created instance {instance.get('InstanceId')} with details: {instance}")
            
            # Add machine to database with proper initial status
            machine_data = {
                "machineId": instance.get('InstanceId'),
                "name": instance.get('PrivateDnsName'),
                "template": template.get('templateId'),
                "result": "executing",
                "status": "pending",
                "privateIpAddress": instance.get('PrivateIpAddress'),
                "publicIpAddress": instance.get('PublicIpAddress') or "",
                "publicDnsName": instance.get('PublicDnsName') or "",
                "ncores": int(template.get('attributes', {}).get('ncores', ['Numeric', '1'])[1]),
                "nthreads": int(template.get('attributes', {}).get('ncpus', ['Numeric', '1'])[1]),
                # TODO: update the static values
                "rcAccount": "default",
                "lifeCycleType": "OnDemand", 
                "reqId": request_id,
                "retId": "",
                "message": "Instance creation initiated",
                "launchtime": int(time.time())
            }
            
            db_manager.add_machine_to_request(request_id, machine_data)
            logger.debug(f"Added machine {instance.get('InstanceId')} to database with details : {machine_data}")
            
            return {
                'success': True,
                'instance_id': instance['InstanceId'],
                'instance': instance
            }
            
        except ClientError as e:
            logger.error(f"Failed to create instance {instance_index}: {e}")
            logger.debug(f"ClientError details - error_code: {e.response['Error']['Code']}, message: {e.response['Error']['Message']}")
            return {
                'success': False,
                'error': str(e),
                'instance_index': instance_index
            }
        except Exception as e:
            logger.error(f"Unexpected error creating instance {instance_index}: {e}")
            logger.debug(f"Exception type: {type(e).__name__}, args: {e.args}")
            return {
                'success': False,
                'error': str(e),
                'instance_index': instance_index
            }

    def create_instances(self, template: Dict, count: int) -> str:
        """Create EC2 instances using multithreading"""
        # Check credentials once at the beginning of bulk operation
        self._refresh_credentials_if_needed()
        
        request_id = f"add-{os.getpid()}-{int(time.time())}"
        logger.debug(f"Starting instance creation request {request_id} for {count} instances")
        
        # Create request in database first
        db_manager.create_request(
            request_id=request_id,
            template_id=template['templateId']
        )
        logger.debug(f"Created request {request_id} in database")
        
        # Initialize thread pool
        self._init_vm_pool()
        
        instance_results = []
        instance_ids = []
        failed_instances = []
        
        try:
            if self.vm_pool:
                # Use thread pool for concurrent instance creation
                logger.debug(f"Using thread pool for {count} instance creations")
                futures = []
                for i in range(count):
                    future = self.vm_pool.submit(self._create_single_instance, template, i, request_id)
                    futures.append(future)
                
                # Wait for all futures to complete
                logger.debug("Waiting for all instance creation futures to complete")
                for future in as_completed(futures):
                    result = future.result()
                    instance_results.append(result)
                    
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
            
            # Collect any instances that were created before the error
            for result in instance_results:
                if result['success']:
                    instance_ids.append(result['instance_id'])
        
            # Clean up completely failed requests
            if not instance_ids:  # No instances were created at all
                logger.warning(f"Request {request_id} failed completely - no instances created. Cleaning up request.")
                try:
                    db_manager.remove_request(request_id)
                    logger.debug(f"Removed failed request {request_id} from database")
                except Exception as e:
                    logger.error(f"Failed to clean up request {request_id}: {e}")
                
                # Return a special indicator or raise an exception
                raise Exception(f"Instance creation failed completely for request {request_id}. No instances created.")
    
        # Store request information
        self.requests[request_id] = {
            'type': 'create',
            'instance_ids': instance_ids,
            'failed_instances': failed_instances,
            'status': 'running' if instance_ids else 'failed',
            'created_at': time.time(),
            'total_requested': count,
            'successful': len(instance_ids),
            'failed': len(failed_instances)
        }
        
        if failed_instances:
            logger.warning(f"Request {request_id}: {len(failed_instances)} instances failed to create")
            logger.debug(f"Failed instances details: {failed_instances}")
        
        logger.info(f"Request {request_id}: Created {len(instance_ids)}/{count} instances")
        logger.debug(f"Request {request_id} completed - successful: {len(instance_ids)}, failed: {len(failed_instances)}")
        return request_id

    def _terminate_single_instance(self, instance_id: str, return_id: str) -> Dict[str, Any]:
        """Terminate a single instance (thread-safe) - only update status, don't remove"""
        try:
            logger.debug(f"Terminating instance {instance_id} with return_id {return_id}")
            response = self.ec2.terminate_instances(InstanceIds=[instance_id])
            logger.debug(f"Termination response for {instance_id}: {response}")
            
            # Update machine status to terminating but don't remove from DB
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
            return {
                'success': False,
                'instance_id': instance_id,
                'error': str(e)
            }

    def terminate_instances(self, instance_ids: List[str]) -> str:
        """Terminate EC2 instances using multithreading"""
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
    
    def check_terminated_instances(self, instance_ids: List[str]) -> Dict[str, Any]:
        """Check if instances are terminated - return consistent format"""
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
            return {
                "status": "complete_with_error",
                "message": str(e),
                "requests": [],
                "requestId": ""
            }

    def _find_terminated_instances(self, instance_ids: List[str]) -> List[str]:
        """Internal method to find terminated instances"""
        terminated = []
        logger.debug(f"_find_terminated_instances called with: {instance_ids}")
        
        if not instance_ids:
            logger.debug("No instance IDs provided to _find_terminated_instances")
            return terminated
        
        try:
            instance_details = self.get_instance_details_bulk(instance_ids)
            logger.debug(f"Bulk instance details: {instance_details}")
            
            for instance_id, details in instance_details.items():
                if details.get('state') in ['terminated', 'shutting-down']:
                    terminated.append(instance_id)
                    logger.debug(f"Instance {instance_id} is terminated/shutting-down: {details.get('state')}")
                    
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
            # Fallback to individual checks
            logger.debug("Falling back to individual instance checks")
            for instance_id in instance_ids:
                try:
                    details = self.get_instance_details(instance_id, retries=0)
                    logger.debug(f"Individual check for {instance_id}: {details.get('state')}")
                    if details.get('state') in ['terminated', 'shutting-down']:
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
                
                if state != 'unknown' or attempt == retries:
                    result = {
                        'state': state,
                        'privateIpAddress': instance.private_ip_address,
                        'publicIpAddress': instance.public_ip_address,
                        'name': instance.private_dns_name,
                        'publicDnsName': instance.public_dns_name,
                        'launchtime': instance.launch_time.timestamp() if instance.launch_time else None
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
                        logger.debug(f"Instance details: {instance.meta.data}")
                        result[instance.id] = {
                            'state': state,
                            'privateIpAddress': instance.private_ip_address,
                            'publicIpAddress': instance.public_ip_address,
                            'name': instance.private_dns_name,
                            'publicDnsName': instance.public_dns_name,
                            'launchtime': instance.launch_time.timestamp() if instance.launch_time else None,
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
                    'source': 'individual-fallback'
                }
                logger.debug(f"Fallback result for {instance_id}: {individual_result['state']}")
        
        logger.debug(f"get_instance_details_bulk returning {len(result)} results")
        return result

    def get_request_status(self, request_id: str) -> Dict[str, Any]:
        """Get request status with proper state transition handling"""
        logger.debug(f"get_request_status called for request: {request_id}")
        
        # Determine request type
        is_creation = request_id.startswith("add-")
        is_deletion = request_id.startswith("remove-")
        logger.debug(f"Request type - creation: {is_creation}, deletion: {is_deletion}")
        
        if not (is_creation or is_deletion):
            logger.error(f"Request should start with 'add-' or 'remove-'. Unable to process request {request_id}.")
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
            network_info_changed = (
                updated_machine.get('privateIpAddress') != machine.get('privateIpAddress') or
                updated_machine.get('publicIpAddress') != machine.get('publicIpAddress') or
                updated_machine.get('publicDnsName') != machine.get('publicDnsName') or
                updated_machine.get('name') != machine.get('name')
            )
            
            if status_changed or result_changed or network_info_changed:
                logger.debug(f"Updating database for instance {instance_id} - status_changed: {status_changed}, result_changed: {result_changed}, network_info_changed: {network_info_changed}")
                db_manager.update_machine_status(
                    updated_machine.get('reqId', ''),
                    instance_id,
                    updated_machine['status'],
                    updated_machine['result'],
                    updated_machine['message'],
                    updated_machine.get('retId', '')
                )
                # Also update the network-specific fields if they changed
                if network_info_changed:
                    logger.debug(f"Updating network info for instance {instance_id}")
                    db_manager.update_machine_network_info(
                        updated_machine.get('reqId', ''),
                        instance_id,
                        updated_machine.get('privateIpAddress'),
                        updated_machine.get('publicIpAddress'),
                        updated_machine.get('publicDnsName'),
                        updated_machine.get('name')
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
        
        return {
            'status': final_status,
            'machines': updated_machines,
            'message': message,
            'requestId': request_id
        }