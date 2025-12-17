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

import json
import os
import logging
import re
from datetime import datetime, timedelta
from typing import Dict, List, Any, Set
from utils import get_config_path

try:
    import boto3
    from botocore.exceptions import ClientError, BotoCoreError
except ImportError:
    boto3 = None

logger = logging.getLogger(__name__)

class TemplateManager:
    # Required template keys
    REQUIRED_KEYS: Set[str] = {
        'templateId', 'maxNumber' 
    }
    
    # Optional template keys
    OPTIONAL_KEYS: Set[str] = {
        'attributes', 'imageId', 'vmType', 'subnetId', 'keyName', 'interfaceType', 'securityGroupIds', 'instanceTags', 'ebsOptimized', 'placementGroupName', 'userData',  
        'launchTemplateId', 'launchTemplateVersion', 'allocationStrategy', 'computeUnit', 'fleetRole', 'spotPrice', 'priority', 'tenancy', 'ec2FleetConfig', 'gpuextend', 
        'onDemandTargetCapacityRatio', 'instanceProfile'
    }
    
    def __init__(self):
        self.template_file = self._get_template_file()
        self.templates = self._load_and_validate_templates()
        self.ec2_client = None
        self.aws_region = None
        self.aws_initialized = False
        self._lazy_init_aws_if_needed()

    def _lazy_init_aws_if_needed(self):
        """Lazily initialize AWS client only if spot templates are present"""
        if not self._has_spot_templates():
            logger.debug("No spot templates found, skipping AWS client initialization")
            return
            
        try:
            from config_manager import config_manager
            self.aws_region = config_manager.get_region()
            
            if boto3 is None:
                logger.warning("boto3 not available - spot price validation will be disabled")
                return
                
            # Get AWS credentials
            credentials = config_manager.get_aws_credentials()
            endpoint_url = config_manager.get_aws_endpoint_url()
            
            if credentials:
                # Use explicit credentials
                self.ec2_client = boto3.client(
                    'ec2',
                    region_name=self.aws_region,
                    aws_access_key_id=credentials.get('aws_access_key_id'),
                    aws_secret_access_key=credentials.get('aws_secret_access_key'),
                    aws_session_token=credentials.get('aws_session_token'),
                    endpoint_url=endpoint_url
                )
            else:
                # Use IAM role/default credentials
                self.ec2_client = boto3.client(
                    'ec2',
                    region_name=self.aws_region,
                    endpoint_url=endpoint_url
                )
            
            self.aws_initialized = True
            logger.debug(f"AWS EC2 client initialized successfully for region: {self.aws_region}")
            
        except Exception as e:
            logger.warning(f"Failed to initialize AWS client for spot price validation: {e}")

    def _has_spot_templates(self) -> bool:
        """Check if any templates have spotPrice field"""
        if not self.templates or 'templates' not in self.templates:
            return False
            
        for template in self.templates.get('templates', []):
            if 'spotPrice' in template:
                return True
        return False

    def _get_template_file(self):
        """Get template file path"""
        conf_dir = get_config_path()
        return os.path.join(conf_dir, 'awsprov_templates.json')
    
    def _load_and_validate_templates(self) -> Dict[str, Any]:
        """Load and validate templates from JSON file"""
        if not os.path.exists(self.template_file):
            logger.warning(f"Template file not found: {self.template_file}")
            return {"templates": [], "validation_errors": ["Template file not found"]}
        
        try:
            with open(self.template_file, 'r') as f:
                templates_data = json.load(f)
            
            # Validate the templates structure
            validation_errors = self._validate_templates_structure(templates_data)
            
            if validation_errors:
                logger.warning(f"Template validation warnings: {validation_errors}")
                templates_data["validation_errors"] = validation_errors
            else:
                logger.debug("Templates loaded and validated successfully")
            
            return templates_data
            
        except (json.JSONDecodeError, FileNotFoundError) as e:
            error_msg = f"Failed to load templates: {e}"
            logger.error(error_msg)
            return {"templates": [], "validation_errors": [error_msg]}

    def _validate_templates_structure(self, templates_data: Dict[str, Any]) -> List[str]:
        """Validate the templates structure"""
        errors = []
        
        if not isinstance(templates_data, dict):
            errors.append("Templates data must be a JSON object")
            return errors
        
        # Check if 'templates' key exists and is a list
        if 'templates' not in templates_data:
            errors.append("Missing 'templates' key")
            return errors
        
        if not isinstance(templates_data['templates'], list):
            errors.append("'templates' must be an array")
            return errors
        
        # Check for duplicate template IDs
        template_ids = set()
        for i, template in enumerate(templates_data['templates']):
            if not isinstance(template, dict):
                errors.append(f"Template at index {i} must be a JSON object")
                continue
            
            template_id = template.get('templateId')
            if template_id:
                if template_id in template_ids:
                    errors.append(f"Duplicate templateId found: {template_id}")
                else:
                    template_ids.add(template_id)
        
        # Validate each template in the templates array
        for i, template in enumerate(templates_data['templates']):
            if not isinstance(template, dict):
                continue  # Error already added above
            
            template_id = template.get('templateId', f'index_{i}')
            
            # Check required keys for each template
            missing_keys = self.REQUIRED_KEYS - set(template.keys())
            if missing_keys:
                errors.append(f"Template '{template_id}' missing required keys: {sorted(missing_keys)}")
            
            # Check for unknown keys in each template
            all_valid_keys = self.REQUIRED_KEYS | self.OPTIONAL_KEYS
            unknown_keys = set(template.keys()) - all_valid_keys
            if unknown_keys:
                errors.append(f"Template '{template_id}' has unknown keys: {sorted(unknown_keys)}. Valid keys are: {sorted(all_valid_keys)}")
            
            # Validate individual parameters
            param_errors = self._validate_individual_parameters(template, template_id)
            errors.extend(param_errors)
            
            # Validate template type specific compulsory parameters
            template_errors = self._validate_template_type(template, template_id)
            errors.extend(template_errors)
        
        return errors

    def _validate_individual_parameters(self, template: Dict[str, Any], template_id: str) -> List[str]:
        """Validate individual parameters with specific rules"""
        errors = []
        
        # Validate templateId
        if 'templateId' in template:
            template_id_value = template['templateId']
            if not isinstance(template_id_value, str):
                errors.append(f"Template '{template_id}': templateId must be a string")
            elif '_' in template_id_value:
                errors.append(f"Template '{template_id}': templateId cannot contain underscores (_)")
            elif not template_id_value.strip():
                errors.append(f"Template '{template_id}': templateId cannot be empty")
        
        # Validate maxNumber
        if 'maxNumber' in template:
            max_number = template['maxNumber']
            if not isinstance(max_number, int):
                errors.append(f"Template '{template_id}': maxNumber must be an integer")
            elif max_number <= 0:
                errors.append(f"Template '{template_id}': maxNumber must be a positive integer")
        
        # Validate attributes
        if 'attributes' in template:
            attr_errors = self._validate_attributes(template['attributes'], template_id)
            errors.extend(attr_errors)
        
        # Validate imageId
        if 'imageId' in template:
            image_id = template['imageId']
            if not isinstance(image_id, str):
                errors.append(f"Template '{template_id}': imageId must be a string")
            elif not image_id.strip():
                errors.append(f"Template '{template_id}': imageId cannot be empty")
        
        # Validate subnetId
        if 'subnetId' in template:
            subnet_id = template['subnetId']
            if not isinstance(subnet_id, str):
                errors.append(f"Template '{template_id}': subnetId must be a string")
            elif not subnet_id.strip():
                errors.append(f"Template '{template_id}': subnetId cannot be empty")
            # Allow comma-separated subnet IDs
            # Each subnet should be a valid subnet ID format (starts with 'subnet-')
            subnets = [s.strip() for s in subnet_id.split(',') if s.strip()]
            for subnet in subnets:
                if not subnet.startswith('subnet-'):
                    errors.append(f"Template '{template_id}': subnet ID '{subnet}' should start with 'subnet-'")
        
        # Validate vmType
        if 'vmType' in template:
            vm_type = template['vmType']
            if not isinstance(vm_type, str):
                errors.append(f"Template '{template_id}': vmType must be a string")
            elif not vm_type.strip():
                errors.append(f"Template '{template_id}': vmType cannot be empty")
            # Allow comma-separated VM types for direct requests
            # Each VM type should follow AWS instance type naming pattern
            vm_types = [v.strip() for v in vm_type.split(',') if v.strip()]
            for vm in vm_types:
                # Basic validation for AWS instance type format
                if not re.match(r'^[a-z0-9][a-z0-9.-]+$', vm.lower()):
                    errors.append(f"Template '{template_id}': VM type '{vm}' has invalid format")
        
        # Validate launchTemplateId
        if 'launchTemplateId' in template:
            launch_template_id = template['launchTemplateId']
            if not isinstance(launch_template_id, str):
                errors.append(f"Template '{template_id}': launchTemplateId must be a string")
            elif not (1 <= len(launch_template_id) <= 255):
                errors.append(f"Template '{template_id}': launchTemplateId must be 1-255 characters")
        
        # Validate launchTemplateVersion
        if 'launchTemplateVersion' in template:
            version = template['launchTemplateVersion']
            if not isinstance(version, str):
                errors.append(f"Template '{template_id}': launchTemplateVersion must be a string")
            elif version not in ['$Latest', '$Default']:
                try:
                    float(version)
                except ValueError:
                    errors.append(f"Template '{template_id}': launchTemplateVersion must be '$Latest', '$Default', or a version number")
        
        # Validate fleetRole
        if 'fleetRole' in template:
            fleet_role = template['fleetRole']
            if not isinstance(fleet_role, str):
                errors.append(f"Template '{template_id}': fleetRole must be a string")
            elif not fleet_role.strip():
                errors.append(f"Template '{template_id}': fleetRole cannot be empty")
        
        # Validate spotPrice
        if 'spotPrice' in template:
            spot_price = template['spotPrice']
            if not isinstance(spot_price, (int, float)):
                errors.append(f"Template '{template_id}': spotPrice must be a number")
            elif spot_price < 0:
                errors.append(f"Template '{template_id}': spotPrice must be non-negative")
        
        # Validate allocationStrategy
        if 'allocationStrategy' in template:
            strategy = template['allocationStrategy']
            valid_strategies = ['capacityOptimized', 'lowestPrice', 'diversified']
            if not isinstance(strategy, str):
                errors.append(f"Template '{template_id}': allocationStrategy must be a string")
            elif strategy not in valid_strategies:
                errors.append(f"Template '{template_id}': allocationStrategy must be one of {valid_strategies}")
        
        # Validate keyName
        if 'keyName' in template:
            key_name = template['keyName']
            if not isinstance(key_name, str):
                errors.append(f"Template '{template_id}': keyName must be a string")
            elif not key_name.strip():
                errors.append(f"Template '{template_id}': keyName cannot be empty")
        
        # Validate interfaceType
        if 'interfaceType' in template:
            interface_type = template['interfaceType']
            if not isinstance(interface_type, str):
                errors.append(f"Template '{template_id}': interfaceType must be a string")
            elif interface_type not in ['efa', 'interface']:
                errors.append(f"Template '{template_id}': interfaceType must be either 'efa' or 'interface'")
        
        # Validate securityGroupIds
        if 'securityGroupIds' in template:
            security_groups = template['securityGroupIds']
            if not isinstance(security_groups, list):
                errors.append(f"Template '{template_id}': securityGroupIds must be a list")
            else:
                for sg in security_groups:
                    if not isinstance(sg, str):
                        errors.append(f"Template '{template_id}': securityGroupIds must contain only strings")
                    elif not sg.strip():
                        errors.append(f"Template '{template_id}': securityGroupIds cannot contain empty strings")
        
        # Validate instanceProfile
        if 'instanceProfile' in template:
            instance_profile = template['instanceProfile']
            if not isinstance(instance_profile, str):
                errors.append(f"Template '{template_id}': instanceProfile must be a string")
            elif not instance_profile.strip():
                errors.append(f"Template '{template_id}': instanceProfile cannot be empty")
            else:
                # Check if it's an ARN or short name
                if instance_profile.startswith('arn:'):
                    # Validate ARN format
                    if not re.match(r'^arn:aws:iam::\d+:instance-profile/[\w+=,.@-]+$', instance_profile):
                        errors.append(f"Template '{template_id}': instanceProfile ARN format is invalid")
                else:
                    # Validate short name format
                    if not re.match(r'^[\w+=,.@-]+$', instance_profile):
                        errors.append(f"Template '{template_id}': instanceProfile short name contains invalid characters")
        
        # Validate instanceTags
        if 'instanceTags' in template:
            instance_tags = template['instanceTags']
            if not isinstance(instance_tags, str):
                errors.append(f"Template '{template_id}': instanceTags must be a string")
            else:
                # Check for reserved AWS prefix
                if 'aws:' in instance_tags.lower():
                    errors.append(f"Template '{template_id}': instanceTags cannot start with 'aws:' prefix")
        
        # Validate ebsOptimized
        if 'ebsOptimized' in template:
            ebs_optimized = template['ebsOptimized']
            if not isinstance(ebs_optimized, bool):
                errors.append(f"Template '{template_id}': ebsOptimized must be a boolean (true or false)")
        
        # Validate priority
        if 'priority' in template:
            priority = template['priority']
            if not isinstance(priority, int):
                errors.append(f"Template '{template_id}': priority must be an integer")
        
        # Validate placementGroupName
        if 'placementGroupName' in template:
            placement_group = template['placementGroupName']
            if not isinstance(placement_group, str):
                errors.append(f"Template '{template_id}': placementGroupName must be a string")
        
        # Validate tenancy
        if 'tenancy' in template:
            tenancy = template['tenancy']
            valid_tenancy = ['default', 'dedicated', 'host']
            if not isinstance(tenancy, str):
                errors.append(f"Template '{template_id}': tenancy must be a string")
            elif tenancy not in valid_tenancy:
                errors.append(f"Template '{template_id}': tenancy must be one of {valid_tenancy}")
            elif tenancy == 'host':
                errors.append(f"Template '{template_id}': tenancy 'host' is not currently supported by LSF")
        
        # Validate userData
        if 'userData' in template:
            user_data = template['userData']
            if not isinstance(user_data, str):
                errors.append(f"Template '{template_id}': userData must be a string")
        
        # Validate ec2FleetConfig
        if 'ec2FleetConfig' in template:
            fleet_config = template['ec2FleetConfig']
            if not isinstance(fleet_config, str):
                errors.append(f"Template '{template_id}': ec2FleetConfig must be a string")
            elif not fleet_config.strip():
                errors.append(f"Template '{template_id}': ec2FleetConfig cannot be empty")
        
        # Validate onDemandTargetCapacityRatio
        if 'onDemandTargetCapacityRatio' in template:
            ratio = template['onDemandTargetCapacityRatio']
            if not isinstance(ratio, (int, float)):
                errors.append(f"Template '{template_id}': onDemandTargetCapacityRatio must be a number")
            elif not (0.0 <= ratio <= 1.0):
                errors.append(f"Template '{template_id}': onDemandTargetCapacityRatio must be between 0.0 and 1.0")
        
        # Validate gpuextend
        if 'gpuextend' in template:
            gpuextend = template['gpuextend']
            if not isinstance(gpuextend, str):
                errors.append(f"Template '{template_id}': gpuextend must be a string")
            else:
                gpuextend_errors = self._validate_gpuextend(gpuextend, template_id)
                errors.extend(gpuextend_errors)
        
        return errors

    def _validate_attributes(self, attributes: Any, template_id: str) -> List[str]:
        """Validate attributes structure"""
        errors = []
        
        if not isinstance(attributes, dict):
            errors.append(f"Template '{template_id}': attributes must be a JSON object")
            return errors
        
        for attr_name, attr_value in attributes.items():
            if not isinstance(attr_value, list) or len(attr_value) != 2:
                errors.append(f"Template '{template_id}': attribute '{attr_name}' must be a list of [attribute_type, attribute_value]")
                continue
            
            attr_type, attr_val = attr_value
            
            if attr_type not in ['Boolean', 'String', 'Numeric']:
                errors.append(f"Template '{template_id}': attribute '{attr_name}' has invalid type '{attr_type}'. Must be Boolean, String, or Numeric")
                continue
            
            if attr_type == 'Boolean':
                if attr_val not in [0, 1, '0', '1']:
                    errors.append(f"Template '{template_id}': attribute '{attr_name}' Boolean value must be 0 or 1")
            elif attr_type == 'Numeric':
                # Check if it's a range format [min:max] or a single number
                if isinstance(attr_val, str) and attr_val.startswith('[') and attr_val.endswith(']'):
                    range_parts = attr_val[1:-1].split(':')
                    if len(range_parts) != 2:
                        errors.append(f"Template '{template_id}': attribute '{attr_name}' Numeric range must be in format [min:max]")
                else:
                    try:
                        float(attr_val)
                    except (ValueError, TypeError):
                        errors.append(f"Template '{template_id}': attribute '{attr_name}' Numeric value must be a number or range [min:max]")
        
        return errors

    def _validate_gpuextend(self, gpuextend: str, template_id: str) -> List[str]:
        """Validate gpuextend format"""
        errors = []
        
        # Split by semicolon and validate each key=value pair
        pairs = gpuextend.split(';')
        for pair in pairs:
            if not pair.strip():
                continue
                
            if '=' not in pair:
                errors.append(f"Template '{template_id}': gpuextend must be in format 'key1=value1;key2=value2;...'")
                continue
            
            key, value = pair.split('=', 1)
            key = key.strip()
            value = value.strip()
            
            valid_keys = ['ngpus', 'nnumas', 'gbrand', 'gmodel', 'gmem', 'nvlink']
            if key not in valid_keys:
                errors.append(f"Template '{template_id}': gpuextend key '{key}' is invalid. Valid keys: {valid_keys}")
                continue
            
            # Validate specific key values
            if key == 'ngpus':
                if not value.isdigit() or int(value) <= 0:
                    errors.append(f"Template '{template_id}': gpuextend ngpus must be a positive integer")
            elif key == 'nnumas':
                if not value.isdigit() or int(value) <= 0:
                    errors.append(f"Template '{template_id}': gpuextend nnumas must be a positive integer")
            elif key == 'gmem':
                if not value.isdigit() or int(value) <= 0:
                    errors.append(f"Template '{template_id}': gpuextend gmem must be a positive integer (MB)")
            elif key == 'nvlink':
                if value.lower() not in ['y', 'n', 'yes', 'no']:
                    errors.append(f"Template '{template_id}': gpuextend nvlink must be y, n, yes, or no")
        
        return errors

    def _validate_template_type(self, template: Dict[str, Any], template_id: str) -> List[str]:
        """Validate template type specific compulsory parameters"""
        errors = []
        
        # Check if it's a LaunchTemplate based template
        if 'launchTemplateId' in template:
            # Validate launch template configuration
            launch_template_id = template.get('launchTemplateId')
            if not launch_template_id:
                errors.append(f"Template '{template_id}': launchTemplateId must not be empty when specified")
        
        # Check if it's a Spot Fleet template
        elif 'fleetRole' in template:
            missing_spot_fleet_keys = []
            if 'fleetRole' not in template:
                missing_spot_fleet_keys.append('fleetRole')
            if 'spotPrice' not in template:
                missing_spot_fleet_keys.append('spotPrice')
            
            if missing_spot_fleet_keys:
                errors.append(f"Template '{template_id}' is a Spot Fleet template but missing required keys: {sorted(missing_spot_fleet_keys)}")
        
        # Check if it's an EC2 Fleet template
        elif 'ec2FleetConfig' in template or 'onDemandTargetCapacityRatio' in template:
            missing_ec2_fleet_keys = []
            if 'ec2FleetConfig' not in template:
                missing_ec2_fleet_keys.append('ec2FleetConfig')
            if 'onDemandTargetCapacityRatio' not in template:
                missing_ec2_fleet_keys.append('onDemandTargetCapacityRatio')
            
            if missing_ec2_fleet_keys:
                errors.append(f"Template '{template_id}' is an EC2 Fleet template but missing required keys: {sorted(missing_ec2_fleet_keys)}")
        
        # Otherwise, it's a basic template
        else:
            missing_basic_keys = []
            if 'imageId' not in template:
                missing_basic_keys.append('imageId')
            if 'vmType' not in template:
                missing_basic_keys.append('vmType')
            if 'subnetId' not in template:
                missing_basic_keys.append('subnetId')
            if 'keyName' not in template:
                missing_basic_keys.append('keyName')
            
            if missing_basic_keys:
                errors.append(f"Template '{template_id}' is a basic template but missing required keys: {sorted(missing_basic_keys)}")
        
        return errors

    def _get_current_spot_price(self, template: Dict[str, Any]) -> float:
        """
        Calculate current market spot price for a template
        Returns the minimum spot price across all availability zones for the template
        """
        # Check if AWS client is available
        if not self.aws_initialized or not self.ec2_client:
            logger.debug("AWS client not initialized, cannot calculate market spot price")
            return 0.0

        # Validate required parameters
        vm_type = template.get('vmType')
        subnet_id = template.get('subnetId')
        
        if not vm_type or not subnet_id:
            logger.debug("Missing vmType or subnetId for spot price calculation")
            return 0.0

        # Check allocation strategy - only proceed if allocationStrategy is present AND its value is 'lowestprice'
        allocation_strategy = template.get('allocationStrategy')
        if allocation_strategy is not None:
            # allocationStrategy exists, check if it's 'lowestprice'
            if allocation_strategy.lower() != 'lowestprice':
                logger.debug("Market spot price is only supported for lowestPrice allocation strategy")
                return 0.0

        # Check for multiple VM types - not supported in Java
        if ',' in vm_type:
            logger.debug("Market spot price is not supported for multiple vm types")
            return 0.0

        try:
            # Get single instance type
            instance_type = vm_type.strip()
            
            # Get all subnets and their AZs
            subnet_ids = [s.strip() for s in subnet_id.split(',') if s.strip()]
            
            # Describe subnets to get availability zones
            subnet_response = self.ec2_client.describe_subnets(SubnetIds=subnet_ids)
            subnets = subnet_response.get('Subnets', [])
            
            if not subnets:
                logger.error("Could not retrieve subnet information")
                return 0.0

            min_price = float('inf')
            found_prices = False

            # Check spot price in each availability zone
            for subnet in subnets:
                az = subnet.get('AvailabilityZone')
                if not az:
                    continue

                # Get current spot price for this AZ
                spot_response = self.ec2_client.describe_spot_price_history(
                    InstanceTypes=[instance_type],
                    ProductDescriptions=['Linux/UNIX'],
                    StartTime=datetime.utcnow(),
                    AvailabilityZone=az,
                    MaxResults=1
                )

                spot_history = spot_response.get('SpotPriceHistory', [])
                if spot_history:
                    current_price = float(spot_history[0]['SpotPrice'])
                    if current_price < min_price:
                        min_price = current_price
                    found_prices = True
                    logger.debug(f"Found spot price {current_price} for {instance_type} in {az}")

            if found_prices and min_price != float('inf'):
                logger.debug(f"Minimum spot price for template {template.get('templateId')} vm type {instance_type} is {min_price}")
                return min_price
            else:
                logger.error("Could not retrieve current spot price")
                return 0.0

        except ClientError as e:
            error_code = e.response['Error']['Code']
            if error_code == 'InvalidSubnetID.NotFound':
                logger.error(f"Invalid subnet ID in template {template.get('templateId')}: {e}")
            else:
                logger.error(f"AWS API error calculating spot price for template {template.get('templateId')}: {e}")
            return 0.0
        except Exception as e:
            logger.error(f"Unexpected error calculating spot price for template {template.get('templateId')}: {e}")
            return 0.0

    def get_available_templates(self) -> Dict[str, Any]:
        """Get all available templates with market spot prices added"""
        if not self.templates or 'templates' not in self.templates:
            return self.templates
            
        available_templates = []
        
        for template in self.templates.get('templates', []):
            template_copy = template.copy()
            
            # Ensure spotPrice is always a float
            if 'spotPrice' in template_copy:
                try:
                    template_copy['spotPrice'] = float(template_copy['spotPrice'])
                except (ValueError, TypeError):
                    # If conversion fails, remove the invalid spotPrice
                    template_copy.pop('spotPrice', None)
                    logger.warning(f"Template {template.get('templateId')} has invalid spotPrice: {template['spotPrice']}")
            
            # Add market spot price for Spot instances and Spot Fleet with lowestPrice strategy
            if 'spotPrice' in template_copy:
                market_price = self._get_current_spot_price(template)
                # Ensure marketSpotPrice is always a float
                try:
                    template_copy['marketSpotPrice'] = float(market_price)
                except (ValueError, TypeError):
                    # If conversion fails, set to 0.0 or handle as needed
                    template_copy['marketSpotPrice'] = 0.0
                    logger.warning(f"Template {template.get('templateId')} has invalid marketSpotPrice: {market_price}")
                
                logger.debug(f"Template {template.get('templateId')}: spotPrice={template_copy['spotPrice']}, marketSpotPrice={template_copy['marketSpotPrice']}")
            
            available_templates.append(template_copy)
        
        result = self.templates.copy()
        result['templates'] = available_templates
        
        return result

    def get_template(self, template_id: str) -> Dict[str, Any]:
        """Get specific template by ID"""
        if not self.templates or 'templates' not in self.templates:
            raise ValueError("No templates available")
        
        for template in self.templates.get('templates', []):
            if template.get('templateId') == template_id:
                return template
        
        raise ValueError(f"Template {template_id} not found")