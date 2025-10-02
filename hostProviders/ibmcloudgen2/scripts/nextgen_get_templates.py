# Copyright International Business Machines Corp, 2020
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

import logging
import traceback
import sys
import json
import os
from os import path
from nextgen_rc_config import SetRcLogger

def main():

  conf_dir = os.environ["PRO_CONF_DIR"]
  if conf_dir is None:
     logging.critical("The PRO_CONF_DIR env. variable is not set. Exiting... ")
     sys.exit("The PRO_CONF_DIR env. variable is not set")

  template_file = conf_dir + "/conf/ibmcloudgen2_templates.json"

  if not os.path.exists(template_file):
     logging.critical(template_file + "file does not exist")
     sys.exit("ibmcloudgen2_templates.json doesnot exist")
  
  fp = open(template_file, "r")
  if fp.mode == 'r':
     contents = fp.read()
     outJson = json.loads(contents)
  fp.close()
  logging.info(outJson)
  # Depreciation warnings
  for tmpl in outJson["templates"]:
      if "crn" in tmpl:
        logging.warning("Variable crn is deprecated from FP16, use encryptionKey instead")
      if "sshkey_id" in tmpl:
        logging.warning("Variable sshkey_id is deprecated from FP16, use sshkeyIds instead")
  print(outJson)

SetRcLogger()

if __name__ == "__main__":
  try:
    logging.critical("----- Entering getAvailableTemplates -----")
    main()
    logging.critical("----- Exiting getAvailableTemplates -----")
  except Exception as e:
    logging.error(traceback.format_exc())
    raise
