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
import time
import shutil
import tempfile
from typing import Dict, List, Any, Optional, Tuple
import logging
from datetime import datetime
from contextlib import contextmanager
from collections import defaultdict

logger = logging.getLogger(__name__)

class DBManager:
    def __init__(self):
        self._lock = threading.RLock()
        self.data_dir = os.environ.get('PRO_DATA_DIR', '/tmp')
        self.db_file = os.path.join(self.data_dir, 'aws-db.json')
        self.backup_file = os.path.join(self.data_dir, 'aws-db.json.backup')
        self._ensure_db_file()
        
    def _ensure_db_file(self):
        """Ensure the database file exists with proper structure"""
        try:
            if not os.path.exists(self.db_file):
                os.makedirs(os.path.dirname(self.db_file), exist_ok=True, mode=0o755)
                
                initial_data = {"requests": []}
                self._atomic_write(self.db_file, initial_data)
                logger.info(f"Created new database file: {self.db_file}")
                
                # Create backup
                self._atomic_write(self.backup_file, initial_data)
            else:
                # Verify existing file is valid JSON
                try:
                    with open(self.db_file, 'r') as f:
                        json.load(f)
                except (json.JSONDecodeError, IOError) as e:
                    logger.warning(f"Existing database file is corrupted: {e}")
                    self._restore_from_backup()
        except Exception as e:
            logger.error(f"Error ensuring database file: {e}")
            # Fallback to temporary directory
            self.data_dir = tempfile.gettempdir()
            self.db_file = os.path.join(self.data_dir, 'aws-db.json')
            self.backup_file = os.path.join(self.data_dir, 'aws-db.json.backup')
            
            try:
                initial_data = {"requests": []}
                self._atomic_write(self.db_file, initial_data)
                logger.info(f"Created database file in temp directory: {self.db_file}")
            except Exception as e2:
                logger.error(f"Failed to create database file even in temp dir: {e2}")
                raise

    def _restore_from_backup(self):
        """Restore database from backup if available"""
        try:
            if os.path.exists(self.backup_file):
                with open(self.backup_file, 'r') as f:
                    data = json.load(f)
                self._atomic_write(self.db_file, data)
                logger.info(f"Restored database from backup: {self.backup_file}")
                return True
            else:
                logger.warning("No backup file available for restore")
                return False
        except Exception as e:
            logger.error(f"Failed to restore from backup: {e}")
            return False

    @contextmanager
    def _file_lock_context(self, lock_file=None):
        """Context manager for file-level locking"""
        if lock_file is None:
            lock_file = self.db_file + '.lock'
        
        lock_acquired = False
        max_attempts = 10
        attempt = 0
        
        while not lock_acquired and attempt < max_attempts:
            try:
                # Create lock file atomically
                fd = os.open(lock_file, os.O_CREAT | os.O_EXCL | os.O_RDWR, 0o644)
                os.write(fd, str(os.getpid()).encode())
                os.close(fd)
                lock_acquired = True
                logger.debug("Acquired file lock")
            except FileExistsError:
                attempt += 1
                if attempt < max_attempts:
                    time.sleep(0.1 * attempt)
                else:
                    logger.warning(f"Could not acquire lock after {max_attempts} attempts")
                    # Check if lock is stale (older than 30 seconds)
                    try:
                        lock_age = time.time() - os.path.getmtime(lock_file)
                        if lock_age > 30:
                            logger.warning(f"Removing stale lock file (age: {lock_age}s)")
                            os.remove(lock_file)
                    except:
                        pass
        
        try:
            yield lock_acquired
        finally:
            if lock_acquired:
                try:
                    os.remove(lock_file)
                    logger.debug("Released file lock")
                except:
                    pass

    def _atomic_write(self, filepath: str, data: Dict[str, Any], backup: bool = False):
        """Atomically write data to file with backup"""
        temp_file = None
        try:
            os.makedirs(os.path.dirname(filepath), exist_ok=True, mode=0o755)
            
            # Write to temporary file first
            with tempfile.NamedTemporaryFile(
                mode='w', 
                dir=os.path.dirname(filepath), 
                prefix=os.path.basename(filepath) + '.',
                suffix='.tmp',
                delete=False
            ) as f:
                temp_file = f.name
                json.dump(data, f, indent=2)
                f.flush()
                os.fsync(f.fileno())
            
            # Backup existing file if requested and it exists
            if backup and os.path.exists(filepath):
                backup_file = filepath + '.prev'
                shutil.copy2(filepath, backup_file)
            
            # Atomic rename
            os.replace(temp_file, filepath)
            
            logger.debug(f"Successfully wrote to {filepath}")
            return True
            
        except Exception as e:
            logger.error(f"Error writing to {filepath}: {e}")
            
            if temp_file and os.path.exists(temp_file):
                try:
                    os.remove(temp_file)
                except:
                    pass
                    
            return False

    def _read_data(self) -> Dict[str, Any]:
        """Read data from the database file"""
        with self._lock:
            # Use file lock for reading to prevent read during write
            with self._file_lock_context() as lock_acquired:
                if not lock_acquired:
                    logger.warning("Could not acquire lock for reading, using empty structure")
                    return {"requests": []}
                
                # Try to read the main file
                primary_data = self._read_file_safe(self.db_file)
                if primary_data is not None:
                    return primary_data.copy()
                
                # If primary file failed, try backup
                logger.warning("Primary database file read failed, trying backup...")
                backup_data = self._read_file_safe(self.backup_file)
                if backup_data is not None:
                    # Restore backup to primary
                    self._atomic_write(self.db_file, backup_data, backup=False)
                    return backup_data.copy()
                
                # Both failed, return empty structure
                logger.error("Both primary and backup database files are corrupted or unavailable")
                return {"requests": []}

    def _read_file_safe(self, filepath: str) -> Optional[Dict[str, Any]]:
        """Safely read and validate JSON file"""
        if not os.path.exists(filepath):
            return None
            
        try:
            with open(filepath, 'r') as f:
                data = json.load(f)
            
            # Validate structure
            if not isinstance(data, dict):
                logger.error(f"Invalid JSON structure in {filepath}: root is not a dict")
                return None
                
            if 'requests' not in data:
                logger.error(f"Invalid JSON structure in {filepath}: missing 'requests' key")
                return None
                
            if not isinstance(data['requests'], list):
                logger.error(f"Invalid JSON structure in {filepath}: 'requests' is not a list")
                return None
            
            logger.debug(f"Successfully read and validated {filepath}")
            return data
            
        except json.JSONDecodeError as e:
            logger.error(f"JSON decode error in {filepath}: {e}")
            
            # Try to read and fix corrupted JSON
            fixed_data = self._attempt_json_recovery(filepath)
            if fixed_data:
                logger.info(f"Successfully recovered JSON from {filepath}")
                return fixed_data
                
            return None
            
        except Exception as e:
            logger.error(f"Error reading {filepath}: {e}")
            return None

    def _write_data(self, data: Dict[str, Any]):
        """Write data to the database file with full error handling"""
        with self._lock:
            # Validate data structure before writing
            if not isinstance(data, dict):
                logger.error(f"Cannot write invalid data: root is not a dict")
                return
                
            if 'requests' not in data:
                logger.error(f"Cannot write invalid data: missing 'requests' key")
                return
                
            if not isinstance(data['requests'], list):
                logger.error(f"Cannot write invalid data: 'requests' is not a list")
                return
            
            # Use file lock for writing
            with self._file_lock_context() as lock_acquired:
                if not lock_acquired:
                    logger.error("Could not acquire lock for writing")
                    return
                
                # Create backup of current file
                if os.path.exists(self.db_file):
                    try:
                        current_data = self._read_file_safe(self.db_file)
                        if current_data:
                            self._atomic_write(self.backup_file, current_data)
                            logger.debug("Created backup before write")
                    except Exception as e:
                        logger.warning(f"Failed to create backup: {e}")
                
                # Write new data
                if self._atomic_write(self.db_file, data, backup=False):
                    logger.debug("Successfully wrote data to database")
                else:
                    logger.error("Failed to write data")
                    # Try to restore from backup if write failed
                    self._restore_from_backup()
                    
    def _attempt_json_recovery(self, filepath: str) -> Optional[Dict[str, Any]]:
        """Attempt to recover corrupted JSON file"""
        try:
            with open(filepath, 'r') as f:
                content = f.read()
            
            # Try to find and extract valid JSON
            # Look for complete requests array
            start_idx = content.find('{"requests": [')
            if start_idx == -1:
                return None
                
            # Find matching closing bracket
            bracket_count = 0
            in_string = False
            escape = False
            
            for i in range(start_idx, len(content)):
                char = content[i]
                
                if escape:
                    escape = False
                    continue
                    
                if char == '\\':
                    escape = True
                    continue
                    
                if char == '"':
                    in_string = not in_string
                    continue
                    
                if not in_string:
                    if char == '[':
                        bracket_count += 1
                    elif char == ']':
                        bracket_count -= 1
                        if bracket_count == 0:
                            # Found complete array
                            end_idx = i + 1
                            try:
                                partial_json = content[start_idx:end_idx] + '}'
                                data = json.loads(partial_json)
                                logger.info(f"Recovered partial JSON from {filepath}")
                                return data
                            except json.JSONDecodeError:
                                # Try to find closing brace
                                if content.find('}', end_idx) != -1:
                                    end_idx = content.find('}', end_idx) + 1
                                    try:
                                        partial_json = content[start_idx:end_idx]
                                        data = json.loads(partial_json)
                                        logger.info(f"Recovered partial JSON from {filepath}")
                                        return data
                                    except json.JSONDecodeError:
                                        pass
                            
            return None
            
        except Exception as e:
            logger.error(f"Failed to recover JSON from {filepath}: {e}")
            return None


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
                "time": int(datetime.now().timestamp() * 1000),
                "machines": [],
                "requestId": request_id,
                "templateId": template_id,
                "rc_account": rc_account,
                "hostAllocationType": host_allocation_type
            }
            
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
                    return request.copy()
            
            return {}
            
        except Exception as e:
            logger.error(f"Error getting request: {e}")
            return {}
    
    def get_all_requests(self) -> List[Dict[str, Any]]:
        """Get all requests"""
        try:
            data = self._read_data()
            return [req.copy() for req in data['requests']]
        except Exception as e:
            logger.error(f"Error getting all requests: {e}")
            return []
    

    def add_machines_to_request(self, request_id: str, machines_data: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Batch add multiple machines to a request in a single operation.
        
        Returns: Dict with 'success_count', 'failed_count', and 'errors' list
        """
        if not machines_data:
            return {'success_count': 0, 'failed_count': 0, 'errors': []}
            
        with self._lock:
            try:
                data = self._read_data()
                
                # Find the request
                target_request = None
                for request in data['requests']:
                    if request['requestId'] == request_id:
                        target_request = request
                        break
                
                # Try legacy fallback if not found
                if not target_request:
                    for request in data['requests']:
                        for machine in request.get('machines', []):
                            if machine.get('machineId') in [m.get('machineId') for m in machines_data]:
                                target_request = request
                                break
                        if target_request:
                            break
                
                if not target_request:
                    return {
                        'success_count': 0,
                        'failed_count': len(machines_data),
                        'errors': [f"Request {request_id} not found"]
                    }
                
                # Initialize machines list if not exists
                if 'machines' not in target_request:
                    target_request['machines'] = []
                
                # Create set of existing machine IDs for fast lookup
                existing_ids = {m['machineId'] for m in target_request['machines']}
                
                # Add machines in batch
                success_count = 0
                failed_count = 0
                errors = []
                
                for i, machine_data in enumerate(machines_data):
                    try:
                        machine_id = machine_data.get('machineId')
                        
                        if not machine_id:
                            errors.append(f"Machine {i}: Missing machineId")
                            failed_count += 1
                            continue
                        
                        if machine_id in existing_ids:
                            errors.append(f"Machine {i}: Machine {machine_id} already exists")
                            failed_count += 1
                            continue
                        
                        target_request['machines'].append(machine_data)
                        existing_ids.add(machine_id)
                        success_count += 1
                        
                    except Exception as e:
                        errors.append(f"Machine {i}: {str(e)}")
                        failed_count += 1
                
                # Write back only if we have successful additions
                if success_count > 0:
                    self._write_data(data)
                    logger.debug(f"Batch added {success_count} machines to request {request_id}")
                
                return {
                    'success_count': success_count,
                    'failed_count': failed_count,
                    'errors': errors[:10]
                }
                
            except Exception as e:
                logger.error(f"Error in add_machines_to_request: {e}")
                return {
                    'success_count': 0,
                    'failed_count': len(machines_data),
                    'errors': [str(e)]
                }

    def update_machines(self, updates: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Batch update multiple machines in a single database operation.
        
        Each update dict should contain:
        - request_id: str (required)
        - machine_id: str (required)
        - status: Optional[str] = None
        - result: Optional[str] = None
        - message: Optional[str] = None
        - return_id: Optional[str] = None
        - private_ip: Optional[str] = None
        - public_ip: Optional[str] = None
        - public_dns: Optional[str] = None
        - name: Optional[str] = None
        - lifecycle: Optional[str] = None
        - tag_instance_id: Optional[bool] = None
        
        Returns: Dict with 'success_count', 'failed_count', and 'errors' list
        """
        if not updates:
            return {'success_count': 0, 'failed_count': 0, 'errors': []}
            
        with self._lock:
            try:
                data = self._read_data()
                
                # Group machines by request_id for faster lookup
                request_map = {}
                machine_map = {}
                for request in data['requests']:
                    request_map[request['requestId']] = request
                    for machine in request.get('machines', []):
                        machine_map[(request['requestId'], machine['machineId'])] = machine
                
                # Process updates
                success_count = 0
                failed_count = 0
                errors = []
                
                for i, update in enumerate(updates):
                    try:
                        request_id = update.get('request_id')
                        machine_id = update.get('machine_id')
                        
                        if not request_id or not machine_id:
                            errors.append(f"Update {i}: Missing request_id or machine_id")
                            failed_count += 1
                            continue
                        
                        key = (request_id, machine_id)
                        if key not in machine_map:
                            # Try to find machine in any request (legacy support)
                            machine_found = False
                            for request in data['requests']:
                                for machine in request.get('machines', []):
                                    if machine['machineId'] == machine_id:
                                        machine_map[key] = machine
                                        machine_found = True
                                        break
                                if machine_found:
                                    break
                            
                            if not machine_found:
                                errors.append(f"Update {i}: Machine {machine_id} not found in request {request_id}")
                                failed_count += 1
                                continue
                        
                        machine = machine_map[key]
                        
                        # Batch update all fields at once
                        if 'status' in update and update['status'] is not None:
                            machine['status'] = update['status']
                        if 'result' in update and update['result'] is not None:
                            machine['result'] = update['result']
                        if 'message' in update and update['message'] is not None:
                            machine['message'] = update['message']
                        if 'return_id' in update and update['return_id'] is not None:
                            machine['retId'] = update['return_id']
                        
                        # Batch update network info
                        if 'private_ip' in update and update['private_ip'] is not None:
                            machine['privateIpAddress'] = update['private_ip']
                        if 'public_ip' in update and update['public_ip'] is not None:
                            machine['publicIpAddress'] = update['public_ip']
                        if 'public_dns' in update and update['public_dns'] is not None:
                            machine['publicDnsName'] = update['public_dns']
                        if 'name' in update and update['name'] is not None:
                            machine['name'] = update['name']
                        if 'lifecycle' in update and update['lifecycle'] is not None:
                            machine['lifeCycleType'] = update['lifecycle']
                        if 'tag_instance_id' in update and update['tag_instance_id'] is not None:
                            machine['tagInstanceId'] = update['tag_instance_id']
                        
                        success_count += 1
                        
                    except Exception as e:
                        errors.append(f"Update {i}: {str(e)}")
                        failed_count += 1
                
                # Write back only if we have successful updates
                if success_count > 0:
                    self._write_data(data)
                    logger.debug(f"Batch update completed: {success_count} successful, {failed_count} failed")
                
                return {
                    'success_count': success_count,
                    'failed_count': failed_count,
                    'errors': errors[:10]
                }
                
            except Exception as e:
                logger.error(f"Error in update_machines: {e}")
                return {
                    'success_count': 0,
                    'failed_count': len(updates),
                    'errors': [str(e)]
                }

    def remove_machine_from_request(self, request_id: str, machine_id: str) -> Tuple[bool, bool]:
        """
        Remove a machine from a request.
        
        Returns: (machine_removed: bool, request_removed: bool)
        """
        try:
            data = self._read_data()
            machine_removed = False
            request_removed = False
            
            # Find the target request
            target_request = None
            request_index = -1
            for i, request in enumerate(data.get('requests', [])):
                if request['requestId'] == request_id:
                    target_request = request
                    request_index = i
                    break

            # Fallback for legacy instances
            if not target_request:
                for i, request in enumerate(data.get('requests', [])):
                    for machine in request.get('machines', []):
                        if machine['machineId'] == machine_id:
                            target_request = request
                            request_index = i
                            break
                    if target_request:
                        break

            if not target_request:
                logger.warning(f"Request {request_id} not found")
                return (False, False)
            
            # Find the target machine
            target_machine = None
            machine_index = -1
            for i, machine in enumerate(target_request.get('machines', [])):
                if machine.get('machineId') == machine_id:
                    target_machine = machine
                    machine_index = i
                    break
            
            if not target_machine:
                logger.warning(f"Machine {machine_id} not found in request {request_id}")
                return (False, False)
            
            # Check if machine can be removed
            machine_status = target_machine.get('status', '')
            removable_statuses = {'terminated', 'failed'}
            
            if machine_status not in removable_statuses:
                logger.warning(f"Cannot remove machine {machine_id} - status is '{machine_status}', must be one of {removable_statuses}")
                return (False, False)
            
            # Remove the machine
            if machine_index >= 0:
                target_request['machines'].pop(machine_index)
                machine_removed = True
            
            # Check if request is now empty
            if not target_request['machines']:
                if request_index >= 0:
                    data['requests'].pop(request_index)
                    request_removed = True
            
            if machine_removed:
                self._write_data(data)
                logger.debug(f"Removed machine {machine_id} from request {request_id}")
                if request_removed:
                    logger.info(f"Removed empty request {request_id}")
            
            return (machine_removed, request_removed)
            
        except Exception as e:
            logger.error(f"Error removing machine from request: {e}")
            return (False, False)


    def get_request_for_machine(self, machine_id: str) -> Dict[str, Any]:
        """Get the request that contains a specific machine"""
        try:
            data = self._read_data()
            
            for request in data.get('requests', []):
                for machine in request.get('machines', []):
                    if machine['machineId'] == machine_id:
                        return {
                            'request': request.copy(),
                            'machine': machine.copy()
                        }
            
            return {}
            
        except Exception as e:
            logger.error(f"Error finding machine {machine_id}: {e}")
            return {}
    
    def get_machines_for_return(self, request_id: str) -> List[Dict[str, Any]]:
        """Get all the machines that contains a specific retId"""
        try:
            machines = []
            data = self._read_data()
            
            for request in data.get('requests', []):
                for machine in request.get('machines', []):
                    if machine.get('retId') == request_id:
                        machines.append(machine.copy())
            return machines
            
        except Exception as e:
            logger.error(f"Error finding machines for return request {request_id}: {e}")
            return []
        
        
    def cleanup_old_data(self, max_request_age_minutes: int = 60) -> Dict[str, Any]:
        """
        Cleanup of old empty requests and terminated machines.
        
        Returns: Dict with cleanup statistics including list of removed fleet requests
        """
        try:
            data = self._read_data()
            current_time_ms = int(datetime.now().timestamp() * 1000)
            max_age_ms = max_request_age_minutes * 60 * 1000
            
            stats = {
                'empty_requests_removed': 0,
                'terminated_machines_removed': 0,
                'removed_fleet_requests': []
            }
            
            requests_to_keep = []
            
            for request in data.get('requests', []):
                request_age_ms = current_time_ms - request['time']
                is_old_request = request_age_ms > max_age_ms
                
                # Remove terminated machines in batch
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
                    
                    # Track fleet requests for external cleanup
                    if request['requestId'].startswith('fleet-'):
                        stats['removed_fleet_requests'].append(request['requestId'])
                else:
                    requests_to_keep.append(request)
            
            # Update database if changes were made
            if stats['empty_requests_removed'] > 0 or stats['terminated_machines_removed'] > 0:
                data['requests'] = requests_to_keep
                self._write_data(data)
                logger.info(f"Cleanup removed {stats['empty_requests_removed']} empty requests and {stats['terminated_machines_removed']} terminated machines")
            
            return stats
            
        except Exception as e:
            logger.error(f"Error during cleanup: {e}")
            return {'empty_requests_removed': 0, 'terminated_machines_removed': 0, 'removed_fleet_requests': []}
        
# Global instance
db_manager = DBManager()