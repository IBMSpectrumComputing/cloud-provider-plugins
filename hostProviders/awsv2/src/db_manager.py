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
import threading
import os
from typing import Dict, List, Any
import logging
from datetime import datetime

logger = logging.getLogger(__name__)

class DBManager:
    def __init__(self):
        self._lock = threading.RLock()
        self._cache = None
        self.data_dir = os.environ.get('PRO_DATA_DIR', '/tmp')
        self.db_file = os.path.join(self.data_dir, 'aws-db.json')
        self._ensure_db_file()

    def _ensure_db_file(self):
        """Ensure the database file exists with proper structure"""
        if not os.path.exists(self.db_file):
            initial_data = {"requests": []}
            self._write_data(initial_data)
            logger.info(f"Created new database file: {self.db_file}")

    def _read_data(self) -> Dict[str, Any]:
        """Read data from the database file"""
        with self._lock:
            if self._cache:
                return self._cache
            try:
                with open(self.db_file, 'r') as f:
                    return json.load(f)
            except (FileNotFoundError, json.JSONDecodeError) as e:
                logger.error(f"Error reading database file: {e}")
                return {"requests": []}

    def _write_data(self, data: Dict[str, Any]):
        """Write data to the database file"""
        try:
            # Create directory if it doesn't exist
            os.makedirs(os.path.dirname(self.db_file), exist_ok=True)
            
            # Write to temporary file first, then rename
            temp_file = self.db_file + '.tmp'
            with open(temp_file, 'w') as f:
                json.dump(data, f, indent=2)
            os.rename(temp_file, self.db_file)
            self._cache = data
        except Exception as e:
            self._cache = None 
            logger.error(f"Error writing to database file: {e}")

    def create_request(self, request_id: str, template_id: str,  
                    host_allocation_type: str = "direct", rc_account: str = "default", 
                    fleet_type: str = None) -> bool:
        """Create a new request entry"""
        try:
            data = self._read_data()
            
            # Check if request already exists
            for req in data['requests']:
                if req['requestId'] == request_id:
                    logger.warning(f"Request {request_id} already exists in database")
                    return False

            new_request = {
                "time": int(datetime.now().timestamp() * 1000),  # milliseconds
                "machines": [],
                "requestId": request_id,
                "templateId": template_id,
                "rc_account": rc_account,
                "hostAllocationType": host_allocation_type
            }
            
            # Only add fleet_type if provided (for EC2 Fleet requests)
            if fleet_type is not None:
                new_request["fleet_type"] = fleet_type
            
            data['requests'].append(new_request)
            self._write_data(data)
            logger.info(f"Created new request {request_id} in database")
            return True
            
        except Exception as e:
            logger.error(f"Error creating request in database: {e}")
            return False

    def get_request(self, request_id: str) -> Dict[str, Any]:
        """Get request by ID"""
        try:
            data = self._read_data()
            
            for request in data.get('requests', []):
                if request['requestId'] == request_id:
                    return request
            
            return {}
            
        except Exception as e:
            logger.error(f"Error getting request: {e}")
            return {}
    
    def get_request_for_machine(self, machine_id: str) -> Dict[str, Any]:
        """Get the request that contains a specific machine"""
        try:
            data = self._read_data()
            
            for request in data.get('requests', []):
                for machine in request.get('machines', []):
                    if machine['machineId'] == machine_id:
                        return {
                            'request': request,
                            'machine': machine
                        }
            
            return {}
            
        except Exception as e:
            logger.error(f"Error finding machine {machine_id}: {e}")
            return {}
        
    # def get_all_requests(self) -> List[Dict[str, Any]]:
    #     """Get all requests"""
    #     try:
    #         data = self._read_data()
    #         return data['requests']
    #     except Exception as e:
    #         logger.error(f"Error getting all requests: {e}")
    #         return []
        
    # def remove_request(self, request_id: str) -> bool:
    #     """Remove a request from the database, regardless of whether it has machines or not"""
    #     try:
    #         data = self._read_data()
            
    #         # Check if request exists
    #         request_exists = False
    #         for request in data.get('requests', []):
    #             if request['requestId'] == request_id:
    #                 request_exists = True
    #                 break
            
    #         if not request_exists:
    #             logger.warning(f"Request {request_id} not found in database")
    #             return False
            
    #         # Remove the request
    #         original_count = len(data['requests'])
    #         data['requests'] = [
    #             req for req in data['requests'] 
    #             if req['requestId'] != request_id
    #         ]
            
    #         request_removed = (len(data['requests']) < original_count)
            
    #         if request_removed:
    #             self._write_data(data)
    #             logger.info(f"Removed request {request_id} from database")
                
    #             # If this was a fleet request, trigger launch template version cleanup
    #             if request_id.startswith('fleet-'):
    #                 try:
    #                     # Import here to avoid circular imports
    #                     from aws_client import AWSClient
    #                     aws_client = AWSClient()
    #                     aws_client._cleanup_launch_template_versions_for_fleet(request_id)
    #                 except Exception as e:
    #                     logger.warning(f"Failed to cleanup launch template versions for fleet {request_id}: {e}")
                        
    #             return True
    #         else:
    #             logger.warning(f"Failed to remove request {request_id} from database")
    #             return False
                
    #     except Exception as e:
    #         logger.error(f"Error removing request {request_id}: {e}")
    #         return False
        
    def add_machine_to_request(self, request_id: str, machine_data: Dict[str, Any]) -> bool:
        """Add a machine to an existing request"""
        try:
            data = self._read_data()
            
            for request in data.get('requests', []):
                if request['requestId'] == request_id:
                    # Check if machine already exists
                    for existing_machine in request['machines']:
                        if existing_machine['machineId'] == machine_data['machineId']:
                            logger.warning(f"Machine {machine_data['machineId']} already exists in request {request_id}")
                            return False
                    
                    request['machines'].append(machine_data)
                    self._write_data(data)
                    logger.info(f"Added machine {machine_data['machineId']} to request {request_id}")
                    return True
            
            logger.warning(f"Request {request_id} not found in database")
            return False
            
        except Exception as e:
            logger.error(f"Error adding machine to request: {e}")
            return False
        
    def get_machines_for_return(self, request_id: str) -> List[Dict[str, Any]]:
        """Get all the machines that contains a specific retId"""
        try:
            machines = []
            data = self._read_data()
            
            for request in data.get('requests', []):
                for machine in request.get('machines', []):
                    if machine.get('retId') == request_id:
                        machines.append(machine)
            return machines
            
        except Exception as e:
            logger.error(f"Error finding machines for return request {request_id}: {e}")
            return []

    def update_machine_status(self, request_id: str, machine_id: str, 
                            status: str, result: str, message: str = "", return_id: str = "") -> bool:
        """Update machine status and result"""
        try:
            data = self._read_data()
            
            for request in data.get('requests', []):
                if request['requestId'] == request_id:
                    for machine in request.get('machines', []):
                        if machine['machineId'] == machine_id:
                            machine['status'] = status
                            machine['result'] = result
                            machine['message'] = message
                            # only available for return request (_terminate_single_instance)
                            machine['retId'] = return_id
                            self._write_data(data)
                            logger.info(f"Updated machine {machine_id} status to {status}")
                            return True
            
            logger.warning(f"Machine {machine_id} not found in request {request_id}")
            return False
            
        except Exception as e:
            logger.error(f"Error updating machine status: {e}")
            return False
        
    def update_machine_network_info(self, request_id: str, machine_id: str, 
                              private_ip: str, public_ip: str, 
                              public_dns: str, name: str,
                              lifecycle: str = None) -> bool:
        """Update machine network information without affecting status/result"""
        try:
            data = self._read_data()
            
            for request in data.get('requests', []):
                if request['requestId'] == request_id:
                    for machine in request.get('machines', []):
                        if machine['machineId'] == machine_id:
                            # Update network fields only
                            if private_ip is not None:
                                machine['privateIpAddress'] = private_ip
                            if public_ip is not None:
                                machine['publicIpAddress'] = public_ip
                            if public_dns is not None:
                                machine['publicDnsName'] = public_dns
                            if name is not None:
                                machine['name'] = name
                            if lifecycle is not None:
                                machine['lifeCycleType'] = lifecycle
                            self._write_data(data)
                            logger.info(f"Updated machine {machine_id} network info")
                            return True
            
            logger.warning(f"Machine {machine_id} not found in request {request_id}")
            return False
            
        except Exception as e:
            logger.error(f"Error updating machine network info: {e}")
            return False
    
    def remove_machine_from_request(self, request_id: str, machine_id: str) -> bool:
        """Remove a machine from a request - only if it's terminated or failed"""
        try:
            data = self._read_data()
            machine_removed = False
            request_found = False
            
            # Find the target request
            target_request = None
            for request in data.get('requests', []):
                if request['requestId'] == request_id:
                    target_request = request
                    request_found = True
                    break
            
            if not request_found:
                logger.warning(f"Request {request_id} not found")
                return False
            
            # Find and validate the target machine
            target_machine = None
            for machine in target_request['machines']:
                if machine['machineId'] == machine_id:
                    target_machine = machine
                    break
            
            if not target_machine:
                logger.warning(f"Machine {machine_id} not found in request {request_id}")
                return False
            
            # Check if machine can be removed (terminated or failed states)
            machine_status = target_machine.get('status', '')
            removable_statuses = {'terminated', 'failed'}
            
            if machine_status not in removable_statuses:
                logger.warning(f"Cannot remove machine {machine_id} - status is '{machine_status}', must be one of {removable_statuses}")
                return False
            
            # Remove the machine
            original_count = len(target_request['machines'])
            target_request['machines'] = [
                m for m in target_request['machines'] 
                if m['machineId'] != machine_id
            ]
            
            machine_removed = (len(target_request['machines']) < original_count)
            
            if machine_removed:
                # Clean up empty requests if this was the last machine
                if not target_request['machines']:
                    # If this was a fleet request, trigger launch template version cleanup
                    if request_id.startswith('fleet-'):
                        try:
                            # Import here to avoid circular imports
                            from aws_client import AWSClient
                            aws_client = AWSClient()
                            aws_client._cleanup_launch_template_versions_for_fleet(request_id)
                        except Exception as e:
                            logger.warning(f"Failed to cleanup launch template versions for fleet {request_id}: {e}")
                    data['requests'] = [
                        req for req in data['requests'] 
                        if req['requestId'] != request_id
                    ]
                    logger.info(f"Removed machine {machine_id} and cleaned up empty request {request_id}")
                else:
                    logger.info(f"Removed machine {machine_id} from request {request_id}")
                
                self._write_data(data)
                return True
            
            logger.warning(f"Failed to remove machine {machine_id} from request {request_id}")
            return False
            
        except Exception as e:
            logger.error(f"Error removing machine from request: {e}")
            return False
        
    def cleanup_old_data(self, max_request_age_minutes: int = 60) -> Dict[str, int]:
        """Simple cleanup of old empty requests and terminated machines"""
        try:
            data = self._read_data()
            current_time_ms = int(datetime.now().timestamp() * 1000)
            max_age_ms = max_request_age_minutes * 60 * 1000
            
            stats = {
                'empty_requests_removed': 0,
                'terminated_machines_removed': 0,
                'fleet_requests_cleaned': 0
            }
            
            requests_to_keep = []
            fleet_requests_to_cleanup = []
            
            for request in data.get('requests', []):
                request_age_ms = current_time_ms - request['time']
                is_old_request = request_age_ms > max_age_ms
                
                # Remove terminated machines
                original_count = len(request.get('machines', []))
                if original_count > 0:
                    request['machines'] = [
                        machine for machine in request['machines'] 
                        if machine.get('status') != 'terminated'
                    ]
                    stats['terminated_machines_removed'] += (original_count - len(request['machines']))
                
                # Remove old empty requests
                current_count = len(request.get('machines', []))
                if current_count == 0 and is_old_request:
                    stats['empty_requests_removed'] += 1
                    
                    # Track fleet requests for additional cleanup
                    if request['requestId'].startswith('fleet-'):
                        fleet_requests_to_cleanup.append(request['requestId'])
                else:
                    requests_to_keep.append(request)
            
            # Update database if changes were made
            if stats['empty_requests_removed'] > 0 or stats['terminated_machines_removed'] > 0:
                data['requests'] = requests_to_keep
                self._write_data(data)
                logger.info(f"Cleanup removed {stats['empty_requests_removed']} empty requests and {stats['terminated_machines_removed']} terminated machines")
            
            # Cleanup launch template versions for fleet requests
            if fleet_requests_to_cleanup:
                try:
                    from aws_client import AWSClient
                    aws_client = AWSClient()
                    for fleet_request_id in fleet_requests_to_cleanup:
                        aws_client._cleanup_launch_template_versions_for_fleet(fleet_request_id)
                        stats['fleet_requests_cleaned'] += 1
                except Exception as e:
                    logger.warning(f"Failed to cleanup launch template versions for fleet requests: {e}")
            
            return stats
            
        except Exception as e:
            logger.error(f"Error during cleanup: {e}")
            return {'empty_requests_removed': 0, 'terminated_machines_removed': 0, 'fleet_requests_cleaned': 0}

# Global instance
db_manager = DBManager()