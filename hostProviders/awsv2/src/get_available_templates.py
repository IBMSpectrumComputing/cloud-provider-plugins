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
from utils import write_output_json, setup_logging
from template_manager import TemplateManager

logger = logging.getLogger(__name__)

def main():
    setup_logging()
    logger.info("Running getAvailableTemplates script")
    try:
        template_manager = TemplateManager()
        templates = template_manager.get_available_templates()
        logger.info(templates)
        print(templates)
        sys.exit(0)
        
    except Exception as e:
        logger.error(f"Error in getAvailableTemplates: {e}")
        write_output_json({"error": str(e)})
        sys.exit(1)

if __name__ == "__main__":
    main()