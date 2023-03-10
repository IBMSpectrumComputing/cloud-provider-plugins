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

from ibm_vpc import VpcV1
from ibm_cloud_sdk_core.authenticators import IAMAuthenticator
#from ibm_cloud_sdk_core import ApiException

from ibm_cloud_networking_services import ResourceRecordsV1
from ibm_cloud_networking_services import DnsZonesV1

import multiprocessing
import time
import os,sys
import logging
import random

from nextgen_rc_config import NextGenTemplate, NextGenConfig
from nextgen_utils import RCInstance

# The service for VPC
global service
service = None

# worker threads for VM
global vmPool
vmPool = None
global minVmWorkers
minVmWorkers = 10

# nextgen template object
global template

# nextgen config object
global config

# if VMs are stuck during creation/destroy, give up after the timeout value
#Increasing the time out value from 120 (added by research team)

global timeout
# timeout in seconds
timeout = 300

global regionToApiEndPoint
regionToApiEndPoint = {
        "us-east"  : "https://us-east.iaas.cloud.ibm.com/v1",
        "us-south" : "https://us-south.iaas.cloud.ibm.com/v1"
}


def getTimeout():
  return timeout

def NextGenVPCInit(_config, _template):
  global service
  global dnsResourceRecords
  global vmPool
  global dnsPool
  global template
  global config

  os.environ["IBM_CREDENTIALS_FILE"] = _config.key_file

  if service is None:
    service = VpcV1.new_instance()

  if _template.region in regionToApiEndPoint:
    service.set_service_url(regionToApiEndPoint[_template.region])
  else:
    region_list = []
    for region in regionToApiEndPoint.keys():
      service.set_service_url(regionToApiEndPoint[region])
      try:
        regions = service.list_regions().get_result()
        region_list = regions["regions"]
        break
      except Exception as e:
        code = getattr(e, 'code', 'N/A')
        message = getattr(e, 'message', 'N/A')
        logging.error("\tlist-regions() failed with status code " + str(code) + ": " + message + " " + base_url)

    if region_list:
      for region in region_list:
        regionToApiEndPoint[region["name"]] = region["endpoint"] + "/v1"

    if _template.region in regionToApiEndPoint:
      service.set_service_url(regionToApiEndPoint[_template.region])
    else:
      logging.error("The region %s provided in the template file is not valid!" % _template.region)
      sys.exit("Fail to get region.")

  if vmPool is None:
    size = minVmWorkers if (multiprocessing.cpu_count()-1) < minVmWorkers else (multiprocessing.cpu_count()-1)
    vmPool = multiprocessing.Pool(size)

  config = _config
  template = _template

def create_instance(rg, vpc, profile, zone, image, subnet, sgs, ssh_id, templateUserData, tagValue, templateId, instance_name, userDataFile):
  # Construct a dict representation of a SecurityGroupIdentityById model
  security_group_identity_model_list = []
  for id in sgs:
    security_group_identity_model = {}
    security_group_identity_model['id'] = id
    security_group_identity_model_list.append(security_group_identity_model)
 
  # Construct a dict representation of a SubnetIdentityById model
  subnet_identity_model = {}
  subnet_identity_model['id'] = subnet

  # Construct a dict representation of a ImageIdentityById model
  image_identity_model = {}
  image_identity_model['id'] = image

  # Construct a dict representation of a InstanceProfileIdentityByName model
  instance_profile_identity_model = {}
  instance_profile_identity_model['name'] = profile

  # Construct a dict representation of a KeyIdentityById model
  key_identity_model = {}
  key_identity_model['id'] = ssh_id

  # Construct a dict representation of a NetworkInterfacePrototype model
  network_interface_prototype_model = {}
  network_interface_prototype_model['name'] = 'eth0'
  network_interface_prototype_model['security_groups'] = security_group_identity_model_list
  network_interface_prototype_model['subnet'] = subnet_identity_model

  # Construct a dict representation of a VPCIdentityById model
  vpc_identity_model = {}
  vpc_identity_model['id'] = vpc

  # Construct a dict representation of a ZoneIdentityByName model
  zone_identity_model = {}
  zone_identity_model['name'] = zone

  # Construct a dict representation of a InstanceMetadataServicePrototype model
  instance_metadata_model = {}
  instance_metadata_model['enabled'] = True

  # Construct a dict representation of a InstancePrototypeInstanceByImage model
  instance_prototype_model = {}
  instance_prototype_model['keys'] = [key_identity_model]
  instance_prototype_model['name'] = instance_name
  instance_prototype_model['profile'] = instance_profile_identity_model
  instance_prototype_model['vpc'] = vpc_identity_model
  instance_prototype_model['image'] = image_identity_model
  instance_prototype_model[
    'primary_network_interface'] = network_interface_prototype_model
  instance_prototype_model['zone'] = zone_identity_model
  instance_prototype_model['metadata_service'] = instance_metadata_model
  if rg:
    resource_group_identity_model = {}
    resource_group_identity_model['id'] = rg
    instance_prototype_model['resource_group'] = resource_group_identity_model
  
  if os.path.isfile(userDataFile):
     f = open(userDataFile, "r");
     user_data = f.read()
     exportCmd = ""
     if templateUserData and templateUserData.strip():    
        exportCmd = "export " + templateUserData.replace(";", " ") + ";"
     if tagValue:
        exportCmd = exportCmd + "export rc_account=" + tagValue + ";"
     if templateId:
        exportCmd = exportCmd + "export template_id=" + templateId + ";"  
     providerName = os.environ["PROVIDER_NAME"]
     if providerName is None or len(providerName) == 0:
        providerName = 'ibmcloudgen2'
     exportCmd = exportCmd + "export providerName=" + providerName + ";"
     scriptOptions = os.environ["SCRIPT_OPTIONS"]
     if scriptOptions:
        clusterName = scriptOptions.split("clusterName=",1)[1]
        exportCmd = exportCmd + "export clusterName=" + clusterName + ";"

     logging.info("exporting: " + exportCmd)
     user_data = user_data.replace("%EXPORT_USER_DATA%", exportCmd)
     instance_prototype_model['user_data'] = user_data

  # Set up parameter values
  instance_prototype = instance_prototype_model

  response = service.create_instance(instance_prototype)
  return response

def create_multi_instances(args):
  instanceName = args[0]
  # vsi is the NextGenTemplate object
  vsi = args[1]
  userData = args[2]
  tagValue = args[3]
  logging.info("create_multi_instances(): instanceName=%s" % instanceName)
  newInstance = {}
  try:
    newInstance=create_instance(
             vsi.rgId,
             vsi.vpcId,
             vsi.vmType,
             vsi.zone,
             vsi.imageId,
             vsi.subnetId,
             vsi.securityGroupId,
             vsi.sshkey_id,
             vsi.userData,
             tagValue,
             vsi.templateId,
             instanceName,
             userData
             ).get_result()

    logging.info("\tinstance id = %s" % newInstance['id'])

  except Exception as e:
    code = getattr(e, 'code', 'N/A')
    message = getattr(e, 'message', 'N/A')
    logging.warning("\tCreating a instance failed with status code " + str(code) + ": " + message)
    logging.warning("\t\tinstanceName: " + instanceName)
    newInstance['error'] = message
    return newInstance

  return newInstance

def request_new_machines(machineCount, tagValue):

  # populate vmNameList with the name for each instances
  pid = os.getpid()
  base_name = config.vm_prefix + "-" + str(pid)

  vmNameList=[]
  for i in range(machineCount):
    instanceName = base_name + "-" + str(i)
    vmNameList.append(instanceName)

  args_map = [ (instance, template, config.provision_file, tagValue) for instance in vmNameList ]

  # create VSIs
  time1 = time.time()
  multi_results = vmPool.map(create_multi_instances, args_map)
  error = ""
  instanceList=[]
  for index, instance in enumerate(multi_results):
    if "error" in instance:
      logging.error("Fail to create a VM named " + vmNameList[index] + " with error " + instance['error'])
      error = instance['error']
    else:
      rcInstance = RCInstance()
      rcInstance.machineId = instance['id']
      rcInstance.name = instance['name']
      rcInstance.status = instance['status']
      rcInstance.launchtime = int(time.time())
      rcInstance.rcAccount = tagValue
      rcInstance.result = "executing"
      rcInstance.statusReason = instance['status_reasons']

      logging.info("instance= %s %s" % (rcInstance.machineId, rcInstance.name))
      instanceList.append(rcInstance)

  logging.info("time (sec) for creating %s VMs: %s " % (len(instanceList), time.time()-time1))

  # No need vmNameList
  del vmNameList[:]

  return instanceList, error;

def wait_for_vm_ready(instanceList):
  time1 = time.time()

  readyList=[]
  stuckList=[]
  failList=[]
  capacityStatusReason = None

  updateInstanceList = check_status(instanceList)
  for index, update_instance in enumerate(updateInstanceList):
    instance = instanceList[index]

    # update the status, ip and result for instances in instanceList
    instance.status = update_instance.status

    #if instance.privateIpAddress == "":
    instance.privateIpAddress = update_instance.privateIpAddress

    instance.statusReasons = update_instance.statusReasons

    if (update_instance.status == 'running' and instance.privateIpAddress and instance.privateIpAddress != "0.0.0.0"):
      instance.result = "succeed"
      readyList.append(instance)
    elif (update_instance.status in ["terminated"]):
      instance.result = "fail"
      instance.message = "VM cannot be found on the cloud"
      failList.append(instance) 
    else: # for starting/stopped/failed
      instance.result = "executing"
      duration = time.time() - instance.launchtime

      #check whether status_reasons is related to capacity issue
      #If there is an instance has capacity status_reasons, we will return the first one we found to
      #indicate the stack has capacity issue.
      myCapacityReason = get_capacity_statusReason(update_instance.statusReasons)
 
      # if VM is not in the running state after 120 seconds, then give up.
      if (duration > timeout) or (update_instance.status == 'failed'):
        if (update_instance.status == 'failed') and (myCapacityReason is not None):
          logging.error("VM %s is in the failed state with reason <%s>. Will clean up and retry!" % (instance.name, ','.join(update_instance.statusReasons)))
          if capacityStatusReason is None:
              capacityStatusReason = myCapacityReason
        elif (update_instance.status == 'failed'):
          logging.error("VM %s is in the failed state. Will clean up and retry!" % (instance.name))
        else:
          logging.error("VM %s still not in the running state after %s seconds" % (instance.name, duration))
        instance.result = "fail"
        instance.status = "terminated"
        instance.message = "Timeout: VM is not in the running state"
        stuckList.append(instance)
      # else: we will continue to wait until the timeout and clean up VMs if still not starting
    
    logging.info("wait_for_vm_ready: instance name " + instance.name + " status " + instance.status + " result " + instance.result)

  logging.info("time (sec) for checking %s VM status (ready): %s " % (len(instanceList), time.time()-time1))

  return readyList, stuckList, failList, capacityStatusReason

def delete_multi_resources(args):
  instance = args

  maxVmRetry = 5

  instanceId = instance.machineId
  logging.info("delete_multi_resources(): instanceId=" + instanceId)
  for attempt in range(maxVmRetry):
    try:
      service.delete_instance(id=instanceId)
      break
    except Exception as e:
      # For the connection exception, 'ConnectionError' object has no attribute 'code' or 'message'
      code = getattr(e, 'code', 'N/A')
      msg = getattr(e, 'message', 'N/A')

      # ibm_cloud_sdk_core.api_exception.ApiException: Error: Instance not found, Code: 404
      if hasattr(e, 'code') and code == 404:
        break
      else:
        logging.warning("\tDeleting instances failed with status code " + str(code) + ": " + msg)
        # backup and retry delete
        logging.warning("\t\tDeleting instanceId(%s) failed. Retrying(attempt=%d)..." % (instanceId, attempt))
        time.sleep(randint(10,50))

    if attempt == (maxVmRetry-1):
      logging.error("\tUsers need to manually delete this instance %s!" % instanceId)

def delete_resources_from_vms(instanceList):
  
  args_map = [ inst for inst in instanceList ]
  time1 = time.time()
  vmPool.map(delete_multi_resources, args_map)
  logging.info("time (sec) for deleting %s instances: %s " % (len(instanceList), time.time()-time1))

  # will use the launchtime here to keep the timestamp when VM is requested to be destroyed
  for instance in instanceList:
    instance.launchtime = int(time.time())
    instance.status = "stopping"
    instance.result = "executing"

def wait_for_vm_destroy(instanceList):
  time1 = time.time()

  # Construct a VM list and check the status of each instance
  # Do it in serial as we want to wait until all VMs are being destroyed
  vmList = instanceList.copy()

  destroyList=[]
  stuckList = []
  for instance in vmList:
    if instance.status == "terminated":
      destroyList.append(instance)
      continue
 
    logging.info("wait_for_vm_destroy: Checking the status for the VM %s %s" % (instance.name, instance.machineId))
    try:
      myInstance = service.get_instance(id=instance.machineId).get_result()

      # update the status of the instance
      instance.status = myInstance['status']
      logging.info("The status for the VM %s : %s" % (instance.name, instance.status))
      if (myInstance['status'] in ["terminated"]):
        instance.result = "succeed"
        destroyList.append(instance)
      else:
        instance.result = "executing"

      # the launchtime here should be the timestamp when VMs are requested to be destroyed
      duration = time.time() - instance.launchtime
      if (duration > timeout):
        logging.error("VM %s cannot be destroyed after %s seconds. Users need to manually delete this instance!" % (instance.name, duration))
        # make sure host.json will mark this node terminated
        instance.result = "fail"
        if (instance.status in ["stopping", "deleting", "shutting-down"]):
          instance.message = "VM cannot be destroyed"
        else:
          instance.message = "Timeout: VM cannot be destroyed"
        stuckList.append(instance)
  
    except Exception as e:
      # VM is gone
      code = getattr(e, 'code', 'N/A')
      message = getattr(e, 'message', 'N/A')
      logging.warning("Fetching instance failed "+ instance.name + " with status code " + str(code) + ": " + message)
      logging.warning("wait_for_vm_destroy : VM " + instance.name + " cannot be fetched from cloud, marking it terminated")
      instance.result = "succeed"
      instance.status = "terminated"
      destroyList.append(instance)
    

  logging.info("time (sec) for checking %s VM status (destroy): %s " % (len(instanceList), time.time()-time1))

  return destroyList, stuckList

def update_multi_instances(args):
  # argument is passed by value. so need to return instance to catch the update in instance
  instance = args

  instanceId = instance.machineId

  myInstance = None
  try:
    myInstance = service.get_instance(id=instance.machineId).get_result()

    # update the status of the instance
    instance.status = myInstance['status']

    # update the ip of the instance
    if 'network_interfaces' in myInstance and myInstance['network_interfaces'] is not None:
      if 'primary_ipv4_address' in myInstance['network_interfaces'][0]:
        instance.privateIpAddress = myInstance['network_interfaces'][0]['primary_ipv4_address']
    if instance.privateIpAddress is None or not instance.privateIpAddress:
        if myInstance["primary_network_interface"] and myInstance["primary_network_interface"]["primary_ip"]:
            instance.privateIpAddress = myInstance["primary_network_interface"]["primary_ip"]["address"]

    # update statusReasons
    if 'status_reasons' in myInstance:
      instance.statusReasons = myInstance['status_reasons']
    
    logging.debug("instance " + instance.name + " has status of " + instance.status + " from cloud")
  except Exception as e:
    code = getattr(e, 'code', 'N/A')
    message = getattr(e, 'message', 'N/A')
    logging.warning("Fetching instance failed "+ instance.name + " with status code " + str(code) + ": " + message)
    logging.warning("error getting the instance " + instance.name + " from cloud")
    if code == 404:
       instance.result = "succeed"
       instance.status = "terminated"

  return instance

def check_status(instanceList):

  args_map = [ inst for inst in instanceList ]
  multi_results = vmPool.map(update_multi_instances, args_map)
  return multi_results

def get_capacity_statusReason(reasonsList):
  if len(reasonsList) == 0:
    return None
  
  #If there is expand code related to capacity, you can return
  #"other_capacity_issues" for it, ebrokerd could recognize it.
  #If you do not care the detail code, this way could help you
  #integrate new reason code without modifying ebrokerd.
  if ("cannot_start_capacity" in reasonsList):
    return "cannot_start_capacity"
  elif ("cannot_start_compute" in reasonsList):
    return "cannot_start_compute"
  elif ("cannot_start_ip_address" in reasonsList):
    return "cannot_start_ip_address"
  elif ("cannot_start_network" in reasonsList):
    return "cannot_start_network"
  elif ("cannot_start_placement_group" in reasonsList):
    return "cannot_start_placement_group"
  elif ("cannot_start_storage" in reasonsList):
    return "cannot_start_storage"
  else: 
    return None
