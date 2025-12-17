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

from typing import Dict, List, Any
from aws_client import AWSClient
from contextlib import contextmanager
from template_manager import TemplateManager
import logging

logger = logging.getLogger(__name__)

class RequestManager:
    def __init__(self):
        self.aws_client = AWSClient()

    @contextmanager
    def resource_context(self):
        """Context manager for resource cleanup"""
        try:
            yield self
        finally:
            self.cleanup()

    def cleanup(self):
        """Clean up resources"""
        self.aws_client.cleanup()

    # Note: Amoung the 5 scripts implementation, get_available_templates is the only function
    # that doesn't need AWS interaction, so moving out to get_available_templates.py       
    # def get_available_templates(self) -> Dict[str, Any]:
    #     """Get available templates"""   
    #     template_manager = TemplateManager()
    #     templates = template_manager.get_available_templates()
    #     logger.info(templates)     
    #     return templates

    def request_machines(self, template_id: str, count: int, rc_account: str = 'default') -> Dict[str, Any]:
        """Request to create machines using multithreading"""
        
        template_manager = TemplateManager()
        template = template_manager.get_template(template_id)
        
        with self.aws_client.resource_context():
            request_id = self.aws_client.request_machines(template, count, rc_account)
        
        return {
            "requestId": request_id,
            "message": f"Request instances success from aws. Requested {count} instances/slots."
        }

    def request_return_machines(self, machines: List[Dict[str, str]]) -> Dict[str, Any]:
        """Request to terminate machines using multithreading"""
        instance_ids = [machine['machineId'] for machine in machines]
        
        # Use context manager to ensure proper cleanup
        with self.aws_client.resource_context():
            request_id = self.aws_client.request_return_machines(instance_ids)
        
        return {
            "requestId": request_id,
            "message": "Request to terminate instances successful."
        }

    def get_request_status(self, request_ids: List[str]) -> Dict[str, Any]:
        """Get status of requests"""
        requests = []
        
        for request_id in request_ids:
            result = self.aws_client.get_request_status(request_id)
            requests.append({
                "requestId": request_id,
                "status": result['status'],
                "machines": result['machines'],
                "message": result['message']
            })
        
        return {"requests": requests}

    def get_return_requests(self, machines: List[Dict[str, str]]) -> Dict[str, Any]:
        """Check for terminated instances by reading from database and AWS"""
        
        # Use context manager to ensure proper cleanup
        with self.aws_client.resource_context():
            result = self.aws_client.get_return_requests(machines)
    
        return {
            "requests": result['requests'],
            "status": result['status'],
            "message": result['message']
        }