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
import sys
import json
import os
from os import path
import socket
import fnmatch
from nextgen_rc_config import NextGenTemplate, NextGenConfig, GetNextGenConfigs

class RCInstance:
  def __init__(self):
    self.machineId = ""
    self.name = ""
    self.result = ""
    self.status = ""
    self.privateIpAddress = ""
    self.launchtime = 0
    self.message = "" 
    self.reqId = ""
    self.retId = ""
    self.template = ""
    self.rcAccount = ""
    self.statusReasons = []

  def copy(self, inst):
    self.machineId = inst.machineId
    self.name = inst.name
    self.result = inst.result
    self.status = inst.status
    self.privateIpAddress = inst.privateIpAddress
    self.launchtime = inst.launchtime
    self.message = inst.message
    self.reqId = inst.reqId
    self.retId = inst.retId
    self.template = inst.template
    self.rcAccount = inst.rcAccount
    self.statusReasons = inst.statusReasons   

  def populate(self, data):
    if 'machineId' in data:
      self.machineId = data['machineId']
    if 'name' in data:
      self.name = data['name']
    if 'result' in data:
      self.result = data['result']
    if 'status' in data:
      self.status = data['status']
    if 'privateIpAddress' in data:
      self.privateIpAddress = data['privateIpAddress']
    if 'launchtime' in data:
      self.launchtime = data['launchtime']
    if 'message' in data:
      self.message = data['message']
    if 'reqId' in data:
      self.reqId = data['reqId']
    if 'retId' in data:
      self.retId = data['retId']
    if 'template' in data:
      self.template = data['template']
    if 'rcAccount' in data:
      self.rcAccount = data['rcAccount']
    if 'statusReasons' in data:
      self.statusReasons = data['statusReasons']
   
  @property
  def machineId(self):
    return self._machineId
  @machineId.setter
  def machineId(self, value):
    self._machineId = value

  @property
  def name(self):
    return self._name
  @name.setter
  def name(self, value):
    self._name = value

  @property
  def result(self):
    return self._result
  @result.setter
  def result(self, value):
    self._result = value

  @property
  def status(self):
    return self._status
  @status.setter
  def status(self, value):
    self._status = value

  @property
  def privateIpAddress(self):
    return self._privateIpAddress
  @privateIpAddress.setter
  def privateIpAddress(self, value):
    self._privateIpAddress = value

  @property
  def launchtime(self):
    return self._launchtime
  @launchtime.setter
  def launchtime(self, value):
    self._launchtime = value

  @property
  def message(self):
    return self._message
  @message.setter
  def message(self, value):
    self._message = value

  @property
  def reqId(self):
    return self._reqId
  @reqId.setter
  def reqId(self, value):
    self._reqId = value
 
  @property
  def retId(self):
    return self._retId
  @retId.setter
  def retId(self, value):
    self._retId = value

  @property
  def template(self):
    return self._template
  @template.setter
  def template(self, value):
    self._template = value

  @property
  def rcAccount(self):
    return self._rcAccount
  @rcAccount.setter
  def rcAccount(self, value):
    self._rcAccount = value

  @property
  def statusReasons(self):
    return self._statusReasons
  @statusReasons.setter
  def statusReasons(self, value):
    self._statusReasons = value

class RcInOut:

  def __init__(self, dirname=""):
    self.dirname = dirname
    providerName = os.environ["PROVIDER_NAME"]
    if providerName is None or len(providerName) == 0:
       providerName = 'ibmcloudgen2'
    self.statusFile = providerName + "-db.json"

  def getVmList(self, instanceList, reqId, retId, templateId):
    data = []

    for instance in instanceList:
      data.append({
          'name': instance.name,
          'machineId': instance.machineId,
          'result': instance.result,
          'status': instance.status,
          'privateIpAddress': instance.privateIpAddress,
          'launchtime': instance.launchtime,
          'message': instance.message,
          'reqId': reqId if len(reqId) != 0 else instance.reqId,
          'retId': retId if len(retId) != 0 else instance.retId,
          'template': instance.template if len(instance.template) != 0 else templateId,
          'rcAccount': instance.rcAccount,
          'statusReasons': instance.statusReasons
      })

    return data

  def getDictFromVmList(self, requestId, instanceList, templateId, retId):
    data = {}
    
    data['requestId'] = requestId

    data['templateId'] = templateId

    data['machines'] = self.getVmList(instanceList, requestId, retId, templateId)
      
    return data

  def getFullPath(self, filename):
    if self.dirname == "":
      return filename
    else:
      return (self.dirname + "/" + self.statusFile)

  def dumpVmListToFile(self, requestId, rcInstanceList, templateId, retId):
    full_path = self.getFullPath(requestId)
    requestsObj = {}
    requestList = []
    data = self.getDictFromVmList(requestId, rcInstanceList, templateId, retId)
    
    if not os.path.exists(full_path): 
       requestList.append(data)
       requestsObj['requests'] = requestList
       with open(full_path, 'w+') as outfile:
           json.dump(requestsObj, outfile, indent=2)
           outfile.close()
    else:
       isUpdate = False
       fp = open(full_path, "r")
       if fp.mode == 'r':
          contents = fp.read()
          theJson = self.loadFromFile(full_path, contents)
          if theJson is None:
            #rebuild the file
            fp.close()
            requestList.append(data)
            requestsObj['requests'] = requestList
            with open(full_path, 'w+') as outfile:
              json.dump(requestsObj, outfile, indent=2)
              outfile.close()
            logging.debug("Rebuild file <"+full_path+"> success")
            return
 
          if "requests" in theJson:
             for req in theJson['requests']:
                 if requestId == req['requestId']:
                    isUpdate = True
                    req['machines'] = self.getVmList(rcInstanceList, requestId, retId, templateId)
       fp.close()      
       if not isUpdate:
          if "requests" in theJson:
             reqList = theJson['requests']
             reqList.append(data)
          else:
             requestList.append(data)
             theJson['requests'] = requestList
       self.dumpJsonToFile(theJson, full_path)
  
  def updateVmListToFile(self, requestId, rcInstanceList, retId):
    full_path = self.getFullPath(requestId)
    requestsObj = {}
    requestList = []
    machineIds = []
    tmpInstList = []

    for vm in rcInstanceList:
        machineIds.append(vm.machineId)

    if not os.path.exists(full_path):
       logging.info("Error: "+ full_path + "do not exist")
       return
    else:
       fp = open(full_path, "r")
       if fp.mode == 'r':
          contents = fp.read()
          theJson = self.loadFromFile(full_path, contents)
          if theJson is None:
            fp.close()
            return

          if "requests" in theJson:
             for req in theJson['requests']:
                 if requestId == req['requestId']:
                    if "machines" in req:
                       for vm in req['machines']:
                           machineId = vm['machineId']
                           machineName = vm['name']
                           if machineId not in machineIds:
                              tmpInstList.append(vm)
                    templateId = req['templateId']
                    rcInstList = self.getVmList(rcInstanceList, requestId, retId, templateId)
                    if tmpInstList:
                       rcInstList.extend(tmpInstList)
                    req['machines'] = rcInstList 
       fp.close()
       self.dumpJsonToFile(theJson, full_path) 

  #The more safe way to dump json object to file
  def dumpJsonToFile(self, data, full_path):
    #backup orginal file if exist
    fnameBkp = full_path+".bkp"
    hasBkp = 0
    if os.path.exists(full_path):
      try:
        os.rename(full_path, fnameBkp)
        hasBkp = 1
        logging.debug("Backup json file <"+full_path+"> sucess.")
      except Exception as e:
        logging.debug("Backup json file <"+full_path+"> error. "+repr(e))
    #dump to file
    try:
      #success, use it and remove backup one
      fp = open(full_path, "w")
      json.dump(data, fp, indent=2)
      if hasBkp:
        os.remove(fnameBkp)
      fp.close()
    except Exception as e:
      #failed, then rollback to backup one
      logging.error("Write object to json file <"+full_path+"> error. "+repr(e))
      fp.close()
      if hasBkp:
        os.rename(full_path, fnameBkp)
        logging.error("Rollback json file <"+full_path+">.")

  def loadFromFile(self, full_path, contents):
    if contents is None or not contents:
      logging.error("The file <" + full_path + "> is empty which cannot be parsed to json object, remove it.")
      os.remove(full_path)
      return None

    try:
      return  json.loads(contents)
    except Exception as e:
      logging.error("Parse json object from file <"+full_path+"> error. "+repr(e))
      return None

 
  def getVmListFromFile(self, reqId):
    full_path = self.getFullPath(reqId)
    instanceList = []
    templateId = ""
    requestId = ""
    if not os.path.exists(full_path):
       return templateId, instanceList, requestId
    fp = open(full_path, "r")
    if fp.mode == 'r':
      contents = fp.read()
      theJson = self.loadFromFile(full_path, contents)
      if theJson is None:
          fp.close()
          return templateId, instanceList, requestId

      if "requests" in theJson:
        for req in theJson['requests']:
          requestId = req['requestId']
          if fnmatch.fnmatch(requestId, reqId):
            if "machines" in req:
              for vm in req['machines']:
                 rcInstance = RCInstance()
                 rcInstance.populate(vm)
                 instanceList.append(rcInstance)
            if "templateId" in req:
              templateId = req['templateId']
            break
    fp.close()

    return templateId, instanceList, requestId

  def readAllRequests(self):
      full_path = self.getFullPath("")
      data = {}
      if not os.path.exists(full_path):
         return data
      fp = open(full_path, "r")
      if fp.mode == 'r':
         contents = fp.read()
         data = self.loadFromFile(full_path, contents)
      fp.close()
      
      return data

  def writeAllRequests(self, data):
      full_path = self.getFullPath("")
      self.dumpJsonToFile(data, full_path)

  def getVmListFromJson(self, filename):
    full_path = self.getFullPath(filename)

    instanceList = []
    fp = open(full_path, "r")
    if fp.mode == 'r':
      contents = fp.read()
      theJson = self.loadFromFile(full_path, contents)
      if theJson is None:
        fp.close()
        return instanceList

      if "machines" in theJson:
        for vm in theJson['machines']:
          rcInstance = RCInstance()
          rcInstance.populate(vm)
          instanceList.append(rcInstance)
      fp.close()

    return instanceList
  
  def getMultiVmListFromFile(self, retId):
    full_path = self.getFullPath(retId)
    logging.info("looking up retId " + retId + "from db " + full_path)
    instanceList = []
    fp = open(full_path, "r")
    if fp.mode == 'r':
      contents = fp.read()
      theJson = self.loadFromFile(full_path, contents)
      if theJson is None:
        fp.close()
        return instanceList

      if "requests" in theJson:
         for req in theJson['requests']:
             templateId = ""
             if "machines" in req:
                 for vm in req['machines']:
                     if "retId" in vm:
                         if retId == vm['retId']:
                            rcInstance = RCInstance()
                            rcInstance.populate(vm)
                            instanceList.append(rcInstance)
        
    fp.close()

    return instanceList

def GetLocalHostnameAndIp():
  host_name = ""
  try:
    host_name = socket.gethostname()
  except:
    logging.error("Fail to get local hostname")

  s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  host_ip = ""
  try:
    # doesn't even have to be reachable
    s.connect(('10.255.255.255', 1))
    host_ip = s.getsockname()[0]
  except Exception:
    logging.error("Fail to get my local ip")
  finally:
    s.close()

  return host_name, host_ip
