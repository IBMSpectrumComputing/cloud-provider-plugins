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

import sys
import logging
from request_manager import RequestManager
from utils import write_output_json, read_input_json

logger = logging.getLogger(__name__)

def main():
    logger.debug("Running getReturnRequests script")
    try:
        input_data = read_input_json()
        logger.info(f"getReturnRequests input: {input_data}")
        machines = input_data['machines']
        
        # Handle empty case directly without initializing RequestManager (input_data = {'machines': []})
        if not machines:
            output_data = {"requests": [], "status": "complete", "message": "No instances found to return"}
            logger.info(f"getReturnRequests output: {output_data}")
            write_output_json(output_data)
            sys.exit(0)
        
        request_manager = RequestManager()
        output_data = request_manager.get_return_requests(machines)
        
        logger.info(f"getReturnRequests output: {output_data}")
        write_output_json(output_data)
        sys.exit(0)
        
    except Exception as e:
        logger.error(f"Error in requestReturnMachines: {e}")
        error_output = {
            "requests": [],
            "status": "complete",
            "message": str(e)
        }
        write_output_json({"error": str(e)})
        sys.exit(1)

if __name__ == "__main__":
    main()