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
import glob
from os import path
from random import seed, randint
import time
from nextgen_utils import RcInOut

from nextgen_rc_config import NextGenTemplate, NextGenConfig, GetNextGenConfigs, SetRcLogger

import vpc_vm_dns as reqVpc
global file_dirname
 
class ReturnMachinesOutput:
  def __init__(self):
    seed(time.time())
    self.data = {}
    self.requestId = str(randint(10000, 99999)) + "-" + str(os.getpid()) + "-remove"

  @property
  def message(self):
    return self._message
  @message.setter
  def message(self, value):
    self._message = value
    self.data['message'] = self._message
  
  @property
  def status(self):
    return self._status
  @status.setter
  def status(self, value):
    self._status = value
    self.data['status'] = self._status

  @property
  def requestId(self):
    return self._requestId
  @requestId.setter
  def requestId(self, value):
    self._requestId = value
    self.data['requestId'] = value
  
  def __str__(self):
    return json.dumps(self.data, indent=2)


def main():

  # get the input json file
  input_fp = open(sys.argv[1], "r")
  home_dir = sys.argv[2]
  instanceList = []

  work_dir = os.environ["PRO_DATA_DIR"]
  if work_dir is None:
     work_dir = home_dir + "/data"
     print("The LSF_SHAREDIR env. variable is not set. Using " + work_dir)

  if not os.path.exists(work_dir):
     os.makedirs(work_dir)
 
  if input_fp.mode == 'r':
    contents = input_fp.read()
    logging.critical(contents)
    fileOpObj = RcInOut("")
    instanceList = fileOpObj.getVmListFromJson(sys.argv[1])
    input_fp.close()
  else:
    logging.error("Fail to read the input file")
    sys.exit("Fail to read the input file\n")

  logging.info("Requesting to destroy %s machines" % len(instanceList))
  #back up the size here
  instanceListSize = len(instanceList)

  # The name of each VM has this format: prefix-pid-[0..n]
  # Based on the pid, we should be able to get a unique file (UUID-PID-ADD/UUID-PID-REMOVE) in the files dir
  # From the data kept in files (produced by requestMachine), we can find the corresponding template id.

  # key: template id value: a list of VMs for this template
  templateIdToVmsMap={}
  # key: machineId value: the RCInstance object for the VM
  machineIdToVmMap={}
  reqIdToVmMap={}

  for instance in instanceList:
    # get the PID based on the instance name
      ary = instance.name.split("-")
      req_pid = ary[len(ary)-2]
      req_pattern = "*-" + str(req_pid) + "-add"

      fileOpObj = RcInOut(work_dir)
      templateId, discardList, requestId = fileOpObj.getVmListFromFile(req_pattern)
        # build up a Dic to map from machineId to VM (history file from requestMachines)
      if len(discardList) == 0:
         break
      for inst in discardList:
          machineIdToVmMap[inst.machineId] = inst
 
      if instance.machineId in machineIdToVmMap:
         vm = machineIdToVmMap[instance.machineId]
         # instance from the input only has name and id. populate the rest of the fields
         # based on the info. stored in logs/UUID-PID_ADD
         instance.copy(vm)
      

        # check if templateId is already in " terminated, stopped" status
      logging.info("instance "+ instance.machineId + "is in " + instance.status + " status from db")
      if instance.status not in ["terminated", "stopped"]:
         if templateId not in templateIdToVmsMap:
            templateIdToVmsMap[templateId] =[]
         templateIdToVmsMap[templateId].append(instance)

      if requestId not in reqIdToVmMap:
         reqIdToVmMap[requestId] =[]
      reqIdToVmMap[requestId].append(instance)


  hisFile = RcInOut(work_dir)
  for templateId, instanceList in templateIdToVmsMap.items():
      logging.info("Deleting %s instances with templateId %s" % (len(instanceList), templateId))
      config, template = GetNextGenConfigs(templateId)
      reqVpc.NextGenVPCInit(config, template)
      reqVpc.delete_resources_from_vms(instanceList)

  outJson = ReturnMachinesOutput()
  outJson.message = str(instanceListSize) + " instances records destroyed"
  outJson.status = "complete"

  for reqId, instanceList in reqIdToVmMap.items():
      logging.info("Updating retId for %s instances with requestId %s" % (len(instanceList), reqId))
      hisFile.updateVmListToFile(reqId, instanceList, outJson.requestId)
  
  logging.critical(outJson)
  print(outJson)
    
SetRcLogger()
if __name__ == "__main__":
  try:
    logging.critical("----- Entering requestReturnMachines -----")
    main()
    logging.critical("----- Exiting requestReturnMachines -----")
  except Exception as e:
    logging.error(traceback.format_exc())
    raise
