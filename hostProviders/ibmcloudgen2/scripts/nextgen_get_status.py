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
from nextgen_utils import RcInOut, GetLocalHostnameAndIp

from nextgen_rc_config import NextGenTemplate, NextGenConfig, GetNextGenConfigs, SetRcLogger

import vpc_vm_dns as reqVpc

# Acquire the timeout variable set in vpc_vm_dns
# if a VSI is not being created or destroyed within timeout, we will delete it in the
# requestMachines case or report an error in the requestReturnMachines case
global timeout
timeout = reqVpc.getTimeout()

class GetStatusOutput:
  def __init__(self):
    seed(time.time())
    self.data = {}
    self.requestId = ""
    self.message = ""
    self.status = "complete"
    self.data['machines'] = []

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

  @property
  def status(self):
    return self._status
  @status.setter
  def status(self, value):
    self._status = value
    self.data['status'] = value
  
  def setMachineList(self, machineList):
    self.data['machines'] = machineList

  def appendMachineList(self, machineList):
    self.data['machines'].extend(machineList)

  def __str__(self):
    return json.dumps(self.data, indent=2)

class GetStatusInput:
  def __init__(self, content):
    self.requestList = []

    theJson = json.loads(content)
    if "requests" in theJson:
      for request in theJson['requests']:
        if "requestId" in request:
          self.requestList.append(request['requestId'])



def HandleAddStatus(hisObj, outForReq, outputJson):
  local_hostname, local_ip = GetLocalHostnameAndIp()
  if local_hostname == "" or local_ip == "":
    logging.error("Fail to get local hostname and|or local ip")
    sys.exit("Fail to get local hostname and|or local ip")

  request = outForReq.requestId 
  message = ""

  templateId, instanceList, reqId = hisObj.getVmListFromFile(request)

  if len(instanceList) == 0 or templateId == "":
    if len(instanceList) == 0:
      logging.warning("No instance can be found for this request %s" % request)

    if templateId == "":
      logging.warning("No templateId can be found for this request %s" % request)

    outputJson['requests'].append(outForReq.data)
    return
  
  config, template = GetNextGenConfigs(templateId)
  reqVpc.NextGenVPCInit(config, template)

  logging.info("Request (%s): Checking if VMs are ready..." % request)
  logging.info("checking for %s machines" % (len(instanceList)))
  readyList, stuckList, failList, capacityStatusReason = reqVpc.wait_for_vm_ready(instanceList)
  pendingSize = len(instanceList) - len(readyList) - len(stuckList) - len(failList)
  if len(readyList) == len(instanceList):
    outForReq.status = "complete"
    outForReq.message = "all vms are running"

    logging.info("all %s vms are running" % len(readyList))
  else:
    if (pendingSize > 0):
      outForReq.status = "running"
    else:
      outForReq.status = "complete_with_error"

    if capacityStatusReason is not None:
      outForReq.message ="some vms still not in the running state due to capacity issues <%s>"%(capacityStatusReason)
      logging.error("There are vms not in the running state due to capacity issues <%s>" % (capacityStatusReason))
      if message == "":
        message = "Error Code: %s"%(capacityStatusReason)
 
    elif len(stuckList) > 0 and len(failList) > 0:
      outForReq.message = "some vms still not in the running state after timeout and some vms cannot be queried"
      logging.error("%s vms are not in the running state after %s seconds and %s vm cannot be queried" % (len(stuckList), timeout, len(failList)))
    elif len(stuckList) > 0:
      outForReq.message = "some vms still not in the running state after timeout"
      logging.error("%s vms are not in the running state after %s seconds" % (len(stuckList), timeout))
    elif len(failList) > 0:
      outForReq.message = "some vms cannot be queried"
      logging.error("# of %s vm cannot be queried" % len(failList))
    else:
      outForReq.message = "some vms are not ready yet"
      logging.info("%s vms are still getting ready" % pendingSize)

  outForReq.setMachineList( hisObj.getVmList(instanceList, "", "", ""))

  # update the history file for requestMachine
  logging.info("writing to db json from request Status for requestId" + request)
  hisObj.dumpVmListToFile(request, instanceList, templateId, "")

  if len(stuckList) > 0:
     logging.warning("We will destroy %s VMs that are either stuck or not able to run provisioning successfully" % len(stuckList))
     reqVpc.delete_resources_from_vms(stuckList)

  # append the result for the current request
  outputJson['requests'].append(outForReq.data)
  if 'message' in outputJson and not outputJson['message'].startswith("Error Code: "):
    outputJson['message'] = message

def HandleRemoveStatus(hisObj, outForReq, outputJson):
  request = outForReq.requestId

  instanceList = hisObj.getMultiVmListFromFile(request)
  templateIdToInstances = {}
  reqIdToInstances = {}
  for inst in instanceList:
      if inst.template not in templateIdToInstances:
         templateIdToInstances[inst.template] = []
      templateIdToInstances[inst.template].append(inst)
      
  logging.info("Request (%s): Checking if VMs are being destroyed..." % (request))

  allInstanceList = []
  allDestroyList = []
  allStuckList = []

  for templateId, instanceList in templateIdToInstances.items():
      logging.info("Checking the VMs for templateId %s..." % (templateId))
      config, template = GetNextGenConfigs(templateId)
      reqVpc.NextGenVPCInit(config, template)

      destroyList, stuckList = reqVpc.wait_for_vm_destroy(instanceList)

      allInstanceList.extend(instanceList)
      allDestroyList.extend(destroyList)
      allStuckList.extend(stuckList)
      outForReq.appendMachineList( hisObj.getVmList(instanceList, "", "", "") )
      for inst in instanceList:
          if inst.reqId not in reqIdToInstances:
             reqIdToInstances[inst.reqId] = []
          reqIdToInstances[inst.reqId].append(inst)
       
  pendingSize = len(allInstanceList) - len(allDestroyList) - len(allStuckList)
  if len(allDestroyList) == len(allInstanceList):
    outForReq.status = "complete"
    outForReq.message = "all vms are destroyed"
    logging.info("all %s vms are being destroyed" % (len(allDestroyList)))
  else:
    if (pendingSize > 0):
      outForReq.status = "running"
    else:
      outForReq.status = "complete_with_error"

    if len(allStuckList) > 0:
      outForReq.message = "some vms cannot be destroyed after " + str(timeout) + " seconds"
      logging.error("%s vms cannot be destroyed after %s seconds" % (len(allStuckList), timeout))
    else:
      outForReq.message = "some vms are waiting to be destroyed"
      logging.info("%s vms are still waiting to be destroyed" % (pendingSize))

  logging.info("writing to db json from request Status for requestId" + request)
  for reqId, instList in reqIdToInstances.items():
      hisObj.updateVmListToFile(reqId, instList, "")
 
  # append the result for the current request
  outputJson['requests'].append(outForReq.data)

def main():

  # get the input json file
  input_fp = open(sys.argv[1], "r")
  home_dir = sys.argv[2]
  if input_fp.mode == 'r':
    contents = input_fp.read()
    logging.critical(contents)

    inputJson = GetStatusInput(contents)
    input_fp.close()
  else:
    logging.error("Fail to read the input file")
    sys.exit("Fail to read the input file\n")

  work_dir = os.environ["PRO_DATA_DIR"]
  if work_dir is None:
     work_dir = home_dir + "/data"
     print("The LSF_SHAREDIR env. variable is not set. Using " + work_dir)

  if not os.path.exists(work_dir):
     os.makedirs(work_dir) 
  
  # initialize the RcInOut object
  hisObj = RcInOut(work_dir)

  outputJson = {}
  outputJson['requests'] = []
  outputJson['message'] = ""
  for request in inputJson.requestList:
    
    outForReq = GetStatusOutput()
    outForReq.requestId = request

    # waiting for the instances to be running
    if request.endswith("-add"):
      HandleAddStatus(hisObj, outForReq, outputJson)
       
    elif request.endswith("-remove"):
      HandleRemoveStatus(hisObj, outForReq, outputJson)

  logging.critical(json.dumps(outputJson, indent=2))
  print(json.dumps(outputJson, indent=2))

SetRcLogger()
if __name__ == "__main__":
  try:
    logging.critical("----- Entering getRequestStatus -----")
    main()
    logging.critical("----- Exiting getRequestStatus -----")
  except Exception as e:
    logging.error(traceback.format_exc())
    raise
