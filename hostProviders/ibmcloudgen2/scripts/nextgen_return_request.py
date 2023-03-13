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
from nextgen_utils import RCInstance
from nextgen_rc_config import NextGenTemplate, NextGenConfig, GetNextGenConfigs, SetRcLogger

import vpc_vm_dns as reqVpc
 
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
    logging.debug(contents)
    fileOpObj = RcInOut("")
    instanceList = fileOpObj.getVmListFromJson(sys.argv[1])
    input_fp.close()
  else:
    logging.error("Fail to read the input file")
    sys.exit("Fail to read the input file\n")
  
  logging.info("Requesting the status of %s machines" % len(instanceList))
  #back up the size here
  instanceListSize = len(instanceList)
  machineIdToVmMap={}
  for inst in instanceList:
      machineIdToVmMap[inst.machineId] = inst 

  terminatedList = []
  hisFile = RcInOut(work_dir)
  requestData =  hisFile.readAllRequests()
  if requestData is None:
    requestData = {}
  tempIdToVms = {}
  if "requests" in requestData:
     for req in requestData['requests']:
         treqId = req['requestId']
         logging.debug("reading req " + treqId)
         for vm in req['machines']:
             machineName = vm['name']
             logging.debug("checking machine " + machineName)
             if vm['machineId'] in machineIdToVmMap:
                if vm['status'] in ["terminated", "stopped"]:
                   rcInstance = RCInstance()
                   rcInstance.populate(vm)
                   terminatedList.append(rcInstance)
                else:
                   tempId = req['templateId']
                   logging.debug("looking up template " + tempId)
                   if tempId not in tempIdToVms.keys():
                     tempIdToVms[tempId] = []
                   rcInstance = machineIdToVmMap[vm['machineId']]
                   rcInstance.populate(vm)
                   rcInstance.reqId = treqId
                   tempIdToVms[tempId].append(rcInstance)
             else:
                if vm['status'] in ["terminated","stopped"]:
                   req['machines'].remove(vm)
                   logging.debug("removing machine " + machineName + "status is "+ vm['status'])
                else:
                   ttepId = req['templateId']
                   logging.debug("looking up template " + ttepId)
                   if ttepId not in tempIdToVms.keys():
                     tempIdToVms[ttepId] = []
                   rcInstance = RCInstance()
                   rcInstance.populate(vm)
                   rcInstance.reqId = treqId
                   tempIdToVms[ttepId].append(rcInstance)
         if len(req['machines']) == 0:
            logging.debug("Remove REQ "  + req["requestId"] + " from db!")
            requestData['requests'].remove(req) 
      
  for tempId in tempIdToVms.keys():
    config, template = GetNextGenConfigs(tempId)
    reqVpc.NextGenVPCInit(config, template)

    statusList = reqVpc.check_status(tempIdToVms[tempId])

    for rcInstance in statusList:
      logging.debug("Checking (name=" + rcInstance.name + " machineId=" + rcInstance.machineId + " template=" + tempId + " reqId=" + rcInstance.reqId + "): with status " + rcInstance.status)

      req = next(reqItem for reqItem in requestData['requests'] if reqItem["requestId"] == rcInstance.reqId)
      instance = next(item for item in req['machines'] if item["name"] == rcInstance.name)

      # update status in db
      instance["status"] = rcInstance.status

      if rcInstance.machineId in machineIdToVmMap:
        if rcInstance.status in ["terminated", "stopped"]:
          logging.warning("Return VM " + rcInstance.name + " to ebroderd!")
          terminatedList.append(rcInstance)
      else:
        if rcInstance.status in ["terminated", "stopped"]:
          logging.warning("Remove VM " + rcInstance.name + " from db!")
          req['machines'].remove(instance)

      if len(req['machines']) == 0:
        logging.debug("Remove REQ "  + req["requestId"] + " from db!")
        requestData['requests'].remove(req)   

  hisFile.writeAllRequests(requestData)

  requestList = [] 
  for inst in terminatedList:
      data = {}
      data['machine'] = inst.name
      data['machineId'] = inst.machineId
      data['gracePeriod'] = 0
      requestList.append(data)

  outJson = {}
  outJson['requests'] = requestList
  outJson['message'] = "Instances marked for termination retrieved successfully"
  outJson['status'] = "complete"
  
  logging.critical(outJson)
  print(outJson)
    
SetRcLogger()
if __name__ == "__main__":
  try:
    logging.critical("----- Entering getReturnRequests -----")
    main()
    logging.critical("----- Exiting getReturnRequests -----")
  except Exception as e:
    logging.error(traceback.format_exc())
    raise
