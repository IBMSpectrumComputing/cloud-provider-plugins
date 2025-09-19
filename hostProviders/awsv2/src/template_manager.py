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
from typing import Dict, List, Any, Set
from utils import get_config_path

logger = logging.getLogger(__name__)

class TemplateManager:
    # Required template keys
    REQUIRED_KEYS: Set[str] = {
        'templateId', 'maxNumber', 'imageId', 'vmType', 'subnetId'
    }
    
    # Optional template keys
    OPTIONAL_KEYS: Set[str] = {
        'attributes', 'keyName', 'interfaceType', 'securityGroupIds', 'instanceTags', 'ebsOptimized', 'placementGroupName', 'userData', 'gpuextend', 
        'launchTemplateId', 'launchTemplateVersion', 'allocationStrategy', 'computeUnit', 'fleetRole', 'spotPrice', 'priority', 'tenancy', 'ec2FleetConfig' 
        'onDemandTargetCapacityRatio'
    }
    
    def __init__(self):
        self.template_file = self._get_template_file()
        self.templates = self._load_and_validate_templates()

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
                logger.error(f"Template validation errors: {validation_errors}")
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
        
        # Validate each template in the templates array
        for i, template in enumerate(templates_data['templates']):
            if not isinstance(template, dict):
                errors.append(f"Template at index {i} must be a JSON object")
                continue
            
            # Check required keys for each template
            missing_keys = self.REQUIRED_KEYS - set(template.keys())
            if missing_keys:
                template_id = template.get('templateId', f'index_{i}')
                errors.append(f"Template '{template_id}' missing required keys: {sorted(missing_keys)}")
            
            # Check for unknown keys in each template
            all_valid_keys = self.REQUIRED_KEYS | self.OPTIONAL_KEYS
            unknown_keys = set(template.keys()) - all_valid_keys
            if unknown_keys:
                template_id = template.get('templateId', f'index_{i}')
                errors.append(f"Template '{template_id}' has unknown keys: {sorted(unknown_keys)}. Valid keys are: {sorted(all_valid_keys)}")
        
        return errors

    def get_available_templates(self) -> Dict[str, Any]:
        """Get all available templates"""
        return self.templates

    def get_template(self, template_id: str) -> Dict[str, Any]:
        """Get specific template by ID"""
        if not self.templates or 'templates' not in self.templates:
            raise ValueError("No templates available")
        
        for template in self.templates.get('templates', []):
            if template.get('templateId') == template_id:
                return template
        
        raise ValueError(f"Template {template_id} not found")