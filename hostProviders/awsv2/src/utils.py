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
import sys
import os
import logging
import socket
from typing import Dict, Any

def setup_logging(config_file_path=None):
    """Setup logging configuration without full ConfigManager initialization"""
    log_level = 'INFO'
    
    # Try to read log level from config file if provided
    if config_file_path and os.path.exists(config_file_path):
        try:
            with open(config_file_path, 'r') as f:
                config_data = json.load(f)
            log_level = config_data.get('LogLevel', 'INFO')
            if log_level not in list(logging._nameToLevel.keys()):
                log_level = 'INFO'
        except Exception:
            pass  # Use default if config file can't be read

    log_dir = os.environ.get('PRO_LSF_LOGDIR', '/tmp')
    hostname = socket.gethostname()
    log_file = os.path.join(log_dir, f'aws-provider.log.{hostname}')
    
    logging.basicConfig(
        level=getattr(logging, log_level.upper(), logging.INFO),
        format='%(asctime)s - %(levelname)s - %(filename)s:%(lineno)d - %(message)s',
        handlers=[logging.FileHandler(log_file)]
    )
    
    # Suppress boto3 debug noise
    logging.getLogger('boto3').setLevel(logging.WARNING)
    logging.getLogger('botocore').setLevel(logging.WARNING)
    logging.debug("Logging configured with level: %s", log_level)
    
def read_json(input_file):
    with open(input_file, 'r') as f:
        return json.load(f)
    
def read_input_json():
    """Read input JSON from file specified with -f option"""
    if len(sys.argv) != 2:
        raise ValueError("Usage: script.py -f <input.json>")
    
    input_file = sys.argv[1]
    if not os.path.exists(input_file):
        raise FileNotFoundError(f"Input file not found: {input_file}")
    
    return read_json(input_file)

def write_output_json(output_data: Dict[str, Any]):
    """Write output JSON to stdout"""
    print(json.dumps(output_data, indent=2))

def get_config_path():
    """Get configuration directory path"""
    conf_dir = os.environ.get('PRO_CONF_DIR')
    if conf_dir:
        return os.path.join(conf_dir, 'conf')
    
    lsf_top = os.environ.get('PRO_LSF_TOP')
    provider_name = os.environ.get('PROVIDER_NAME', 'aws')
    
    if not lsf_top:
        raise EnvironmentError("PRO_LSF_TOP environment variable not set")
    
    conf_dir = os.path.join(lsf_top, 'conf', 'resource_connector', provider_name, 'conf')
    os.makedirs(conf_dir, exist_ok=True)
    return conf_dir

