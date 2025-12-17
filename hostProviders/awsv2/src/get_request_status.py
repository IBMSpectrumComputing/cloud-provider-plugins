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
    logger.debug("Running getRequestStatus script")
    try:
        input_data = read_input_json()
        logger.info(f"getRequestStatus input: {input_data}")
        requests = input_data['requests']
        request_ids = [req['requestId'] for req in requests]
        
        request_manager = RequestManager()
        output_data = request_manager.get_request_status(request_ids)
        
        logger.info(f"getRequestStatus output: {output_data}")
        write_output_json(output_data)
        sys.exit(0)
        
    except Exception as e:
        logger.error(f"Error in getRequestStatus: {e}")
        error_output = {
            "requestId": None,
            "status": "complete_with_error",
            "machines": [],
            "message": str(e)
        }
        write_output_json(error_output)
        sys.exit(1)

if __name__ == "__main__":
    main()