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
from random import seed, randint
import time
from nextgen_utils import RcInOut

from nextgen_rc_config import NextGenTemplate, NextGenConfig, GetNextGenConfigs, SetRcLogger

import vpc_vm_dns as reqVpc

# If anything goes wrong after the resources have been created in the main() function,
# we will destroy any resources recorded in createdInstanceList by main().
global createdInstanceList
createdInstanceList = []

class RequestMachinesOutput:
  def __init__(self):
    seed(time.time())
    self.data = {}
    #self.requestId = str(randint(1, sys.maxsize)) + "-" + str(os.getpid()) + "-add"
    self.requestId = str(randint(10000, 99999)) + "-" + str(os.getpid()) + "-add"

  @property
  def message(self):
    return self._message
  @message.setter
  def message(self, value):
    self._message = value
    self.data['message'] = self._message
  
  @property
  def requestId(self):
    return self._requestId
  @requestId.setter
  def requestId(self, value):
    self._requestId = value
    self.data['requestId'] = value
  
  def __str__(self):
    return json.dumps(self.data, indent=2)

class RequestMachinesInput:
  def __init__(self, content):
    self.templateId = ""
    self.machineCount = 0
    self.tagValue = ""

    theJson = json.loads(content)
    if "template" in theJson:
      if "templateId" in theJson["template"]:
        self.templateId = theJson["template"]["templateId"]
      if "machineCount" in theJson["template"]:
        self.machineCount = theJson["template"]["machineCount"]
    if "rc_account" in theJson:
      self.tagValue = theJson["rc_account"]

  @property
  def templateId(self):
    return self._templateId
  @templateId.setter
  def templateId(self, value):
    self._templateId = value

  @property
  def machineCount(self):
    return self._machineCount
  @machineCount.setter
  def machineCount(self, value):
    self._machineCount = value

  @property
  def machineCount(self):
    return self._tagValue
  @machineCount.setter
  def machineCount(self, value):
    self._tagValue = value

def main():
  global createdInstanceList

  # get the input json file
  input_fp = open(sys.argv[1], "r")
  home_dir = sys.argv[2]
  if input_fp.mode == 'r':
    contents = input_fp.read()
    logging.critical(contents)
    request = RequestMachinesInput(contents)
    input_fp.close()
  else:
    logging.error("Fail to read the input file")
    sys.exit("Fail to read the input file\n")

  config, template = GetNextGenConfigs(request.templateId)

  logging.info("Requesting %s machines, rc_account %s with templateId %s (profile %s)" % \
               (request.machineCount, request.tagValue, request.templateId, template.vmType))

  reqVpc.NextGenVPCInit(config, template)
  instanceList, error = reqVpc.request_new_machines(request.machineCount, request.tagValue)
  logging.info("%s instances successfully created (pid=%s)" % (len(instanceList), os.getpid()))
  
  if (len(instanceList) <= 0):
     logging.error("Fail to create any VSI")
     outJson = RequestMachinesOutput()
     if error:
        outJson.message = error
     logging.critical(outJson)
     print(outJson)
  else:
     createdInstanceList = instanceList

  outJson = RequestMachinesOutput()
  outJson.message = str(len(instanceList)) + " instances successfully created"
  
  work_dir = os.environ["PRO_DATA_DIR"]
  if work_dir is None:
     work_dir = home_dir + "/data"
     print("The LSF_SHAREDIR env. variable is not set. Using " + work_dir)

  if not os.path.exists(work_dir):
     os.makedirs(work_dir)

  hisFile = RcInOut(work_dir)
  logging.info("writing to db json from request Machines for requestId" + outJson.requestId)
  hisFile.dumpVmListToFile(outJson.requestId, instanceList, request.templateId, "")

  logging.critical(outJson)
  print(outJson)

SetRcLogger()

if __name__ == "__main__":
  try:
    logging.critical("----- Entering requestMachines -----")
    main()
    logging.critical("----- Exiting requestMachines -----")
  except Exception as e:
    if len(createdInstanceList) > 0:
      logging.critical("Cleaning up any resources created on the Cloud")
      reqVpc.delete_resources_from_vms(createdInstanceList)
    logging.error(traceback.format_exc())
    raise
