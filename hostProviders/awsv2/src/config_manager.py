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
import boto3
from typing import Dict, List, Any, Optional, Set
import configparser
from utils import get_config_path, setup_logging
import subprocess
import logging
import time
import hashlib
import threading

logger = logging.getLogger(__name__)

class ConfigManager:
    # Required configuration keys
    REQUIRED_KEYS: Set[str] = {
        'AWS_REGION'
    }
    
    # Optional configuration keys
    OPTIONAL_KEYS: Set[str] = {
        'LogLevel', 'AWS_CREDENTIAL_FILE', 'AWS_CREDENTIAL_SCRIPT', 'AWS_ENDPOINT_URL', 
        'AWS_KEY_FILE', 'AWS_SPOT_TERMINATE_ON_RECLAIM', 'AWS_TAG_InstanceID'
    }
    
    def __init__(self):
        self.config_file = self._get_config_file()
        self.script_cache = {}
        self.script_cache_lock = threading.RLock()
        self.configs = self._load_and_validate_configs()
        self._setup_logging()
    
    def _get_config_file(self) -> str:
        """Get config file path"""
        conf_dir = get_config_path()
        return os.path.join(conf_dir, 'awsprov_config.json')
    
    def _load_and_validate_configs(self) -> Dict[str, Any]:
        """Load and validate configuration from JSON file"""
        if not os.path.exists(self.config_file):
            logger.warning(f"Config file not found: {self.config_file}")
            return {"validation_errors": ["Config file not found"]}
        
        try:
            with open(self.config_file, 'r') as f:
                config_data = json.load(f)
            
            # Validate the configuration structure
            validation_errors = self._validate_config_structure(config_data)
            
            if validation_errors:
                logger.error(f"Config validation errors: {validation_errors}")
                config_data["validation_errors"] = validation_errors
            else:
                config_data["validation_errors"] = []
                logger.debug("Configuration loaded and validated successfully")
            
            return config_data
            
        except (json.JSONDecodeError, FileNotFoundError) as e:
            error_msg = f"Failed to load config: {e}"
            logger.error(error_msg)
            return {"validation_errors": [error_msg]}

    def _validate_config_structure(self, config_data: Dict[str, Any]) -> List[str]:
        """Validate the configuration structure - only check required keys and unknown keys"""
        errors = []
        
        if not isinstance(config_data, dict):
            errors.append("Configuration data must be a JSON object")
            return errors
        
        # Check required keys
        missing_keys = self.REQUIRED_KEYS - set(config_data.keys())
        if missing_keys:
            errors.append(f"Missing required keys: {sorted(missing_keys)}")
        
        # Check for unknown keys (keys not in required or optional sets)
        all_valid_keys = self.REQUIRED_KEYS | self.OPTIONAL_KEYS
        unknown_keys = set(config_data.keys()) - all_valid_keys
        if unknown_keys:
            errors.append(f"Unknown keys: {sorted(unknown_keys)}. Valid keys are: {sorted(all_valid_keys)}")
        
        return errors
    
    def get_region(self) -> str:
        """Get AWS region from configuration"""
        region = self.configs.get('AWS_REGION')
        if not region:
            raise ValueError("AWS_REGION not found in configuration")
        return region

    def get_aws_credentials(self) -> Dict[str, str]:
        """
        Get AWS credentials using one of the three supported methods:
        1. AWS_CREDENTIAL_FILE - path to credentials file
        2. AWS_CREDENTIAL_SCRIPT - script that generates temporary credentials
        3. IAM Role (default) - when running on EC2 instance
        """
        if not os.path.exists(self.config_file):
            raise ValueError(f"Config file not found: {self.config_file}")
        
        # Method 1: Credentials file
        credential_file = self.configs.get('AWS_CREDENTIAL_FILE')
        if credential_file:
            if not os.path.exists(credential_file):
                raise ValueError(f"AWS_CREDENTIAL_FILE not found: {credential_file}")
            
            logger.info(f"Using credentials from file: {credential_file}")
            return self._get_credentials_from_file(credential_file)
        
        # Method 2: Credentials script
        credential_script = self.configs.get('AWS_CREDENTIAL_SCRIPT')
        if credential_script:
            if not os.path.exists(credential_script):
                raise ValueError(f"AWS_CREDENTIAL_SCRIPT not found: {credential_script}")
            
            logger.info(f"Using credentials from script: {credential_script}")
            return self._get_credentials_from_script(credential_script)
        
        # Method 3: IAM Role (let boto3 handle it automatically)
        logger.info("Using IAM Role credentials (EC2 instance profile)")
        return {}
    
    def _get_credentials_from_file(self, credential_file: str) -> Dict[str, str]:
        """Extract credentials from AWS credentials file"""
        try:
            config = configparser.ConfigParser()
            config.read(credential_file)
            
            if 'default' not in config:
                raise ValueError("No 'default' section found in credentials file")
            
            credentials = {
                'aws_access_key_id': config['default'].get('aws_access_key_id'),
                'aws_secret_access_key': config['default'].get('aws_secret_access_key'),
                'aws_session_token': config['default'].get('aws_session_token')
            }
            
            if not credentials['aws_access_key_id'] or not credentials['aws_secret_access_key']:
                raise ValueError("Missing access key or secret key in credentials file")
            
            return credentials
            
        except Exception as e:
            logger.error(f"Failed to parse credentials file: {e}")
            raise ValueError(f"Invalid credentials file: {e}")
    
    def _get_script_hash(self, script_path: str) -> str:
        """Generate hash for script content for caching"""
        try:
            with open(script_path, 'rb') as f:
                content = f.read()
            return hashlib.md5(content).hexdigest()
        except Exception:
            return hashlib.md5(script_path.encode()).hexdigest()
    
    def _parse_expiration(self, expiration_str: str) -> float:
        """Parse expiration string to timestamp"""
        try:
            # Handle ISO format expiration strings
            from datetime import datetime
            dt = datetime.fromisoformat(expiration_str.replace('Z', '+00:00'))
            return dt.timestamp()
        except (ValueError, AttributeError):
            # Default to 1 hour if parsing fails
            return time.time() + 3600
    
    def _cache_script_result(self, script_hash: str, credentials: Dict, expiry: float):
        """Cache script execution results"""
        with self.script_cache_lock:
            cache_entry = {
                'credentials': credentials,
                'expiry': expiry,
                'timestamp': time.time()
            }
            self.script_cache[script_hash] = cache_entry
    
    def _get_cached_script_result(self, script_hash: str) -> Optional[Dict]:
        """Get cached script result if valid"""
        with self.script_cache_lock:
            if script_hash in self.script_cache:
                cache_entry = self.script_cache[script_hash]
                current_time = time.time()
                
                # Check if cache entry is still valid
                if cache_entry['expiry'] and current_time < cache_entry['expiry'] - 300:
                    return cache_entry
                elif not cache_entry['expiry'] and current_time - cache_entry['timestamp'] < 300:
                    return cache_entry
            
            return None
    
    def _is_script_result_expired(self, cache_entry: Dict) -> bool:
        """Check if cached script result is expired"""
        current_time = time.time()
        if cache_entry['expiry']:
            return current_time >= cache_entry['expiry'] - 300  # 5-minute buffer
        else:
            return current_time - cache_entry['timestamp'] >= 300  # 5-minute cache for non-expiring
    
    def _get_credentials_from_script(self, script_path: str) -> Dict[str, str]:
        """Execute credential script with caching and expiration handling"""
        try:
            # Check if script output is cached
            script_hash = self._get_script_hash(script_path)
            cached_result = self._get_cached_script_result(script_hash)
            
            if cached_result and not self._is_script_result_expired(cached_result):
                logger.debug(f"Using cached credentials from script: {script_path}")
                return cached_result['credentials']
            
            # Execute script
            logger.debug(f"Executing credential script: {script_path}")
            result = subprocess.run(
                [script_path],
                capture_output=True,
                text=True,
                check=True,
                timeout=30  # Add timeout to prevent hanging
            )
            
            credentials_data = json.loads(result.stdout)
            
            # Validate required fields
            required_fields = ['AccessKeyId', 'SecretAccessKey', 'SessionToken']
            for field in required_fields:
                if field not in credentials_data:
                    raise ValueError(f"Missing {field} in script output")
            
            # Extract expiration if available
            expiry = None
            if 'Expiration' in credentials_data:
                expiry = self._parse_expiration(credentials_data['Expiration'])
            
            credentials = {
                'aws_access_key_id': credentials_data['AccessKeyId'],
                'aws_secret_access_key': credentials_data['SecretAccessKey'],
                'aws_session_token': credentials_data['SessionToken'],
                'Expiration': expiry
            }
            
            # Cache the result
            self._cache_script_result(script_hash, credentials, expiry)
            
            return credentials
            
        except subprocess.CalledProcessError as e:
            logger.error(f"Credential script failed: {e.stderr}")
            raise ValueError(f"Credential script execution failed: {e.stderr}")
        except subprocess.TimeoutExpired:
            logger.error(f"Credential script timed out: {script_path}")
            raise ValueError("Credential script execution timed out")
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON from credential script: {e}")
            raise ValueError(f"Credential script must output valid JSON: {e}")
        except Exception as e:
            logger.error(f"Error executing credential script: {e}")
            raise ValueError(f"Credential script error: {e}")
    
    def validate_aws_credentials(self, credentials: Optional[Dict[str, str]] = None) -> bool:
        """Validate that AWS credentials are working"""
        try:
            # If no credentials provided, try to get them
            if credentials is None:
                credentials = self.get_aws_credentials()
            
            # For IAM role case (empty credentials), boto3 will use instance profile
            if not credentials:
                # Create session without explicit credentials
                session = boto3.Session()
            else:
                # Create session with provided credentials
                session = boto3.Session(
                    aws_access_key_id=credentials.get('aws_access_key_id'),
                    aws_secret_access_key=credentials.get('aws_secret_access_key'),
                    aws_session_token=credentials.get('aws_session_token')
                )
            
            # Test with a simple API call
            sts = session.client('sts')
            sts.get_caller_identity()
            return True
            
        except Exception as e:
            logger.error(f"AWS credentials validation failed: {e}")
            return False
    
    def get_aws_endpoint_url(self) -> Optional[str]:
        """Get AWS endpoint URL for custom endpoints"""
        return self.configs.get('AWS_ENDPOINT_URL')

    def get_aws_key_file(self) -> Optional[str]:
        """Get AWS key file path"""
        key_file = self.configs.get('AWS_KEY_FILE')
        if key_file and os.path.exists(key_file):
            return key_file
        elif key_file:
            logger.warning(f"AWS_KEY_FILE not found: {key_file}")
        return None

    def get_spot_terminate_on_reclaim(self) -> bool:
        """Get spot instance termination setting"""
        return self.configs.get('AWS_SPOT_TERMINATE_ON_RECLAIM', False)

    def get_instance_id_tag(self) -> bool:
        """Get instance ID tag name"""
        return self.configs.get('AWS_TAG_InstanceID', False)
    
    def _setup_logging(self):
        """Setup logging configuration"""
        setup_logging(self.config_file)
        
    
# Create global instance
config_manager = ConfigManager()