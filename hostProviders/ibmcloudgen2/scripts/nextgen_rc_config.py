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

import json
import logging
from logging.handlers import RotatingFileHandler
import os, socket
import sys
from os import path

#global logFile
#logFile = "nextgen.log"
global log_file

def SetRcLogger():
  config, template = GetNextGenConfigs()
  logLevel = logging.INFO
  if (config.log_level.lower() == "debug"):
    logLevel=logging.DEBUG
  elif (config.log_level.lower() == "info"):
    logLevel=logging.INFO
  elif (config.log_level.lower() == "warning"):
    logLevel=logging.WARNING
  elif (config.log_level.lower() == "critical"):
    logLevel=logging.CRITICAL
  elif (config.log_level.lower() == "error"):
    logLevel=logging.ERROR
   
  log_dirname = os.environ["PRO_LSF_LOGDIR"]
  if log_dirname is None:
    print("The PRO_LSF_LOGDIR env. variable is not set")
    sys.exit("The PRO_LSF_LOGDIR env. variable is not set\n")
 
  providerName = os.environ["PROVIDER_NAME"]
  if providerName is None or len(providerName) == 0:
     providerName = 'ibmcloudgen2'

  # clear up the handlers in the "root" default logger
  for handler in logging.root.handlers[:]:
    logging.root.removeHandler(handler)

  if (log_dirname != ""):
    host_name = socket.gethostname()
    log_file = log_dirname + "/" + providerName+ '-provider.log.' + str(host_name)
    os.makedirs(os.path.dirname(log_file), exist_ok=True)
    handler = RotatingFileHandler(log_file, maxBytes=2097152, backupCount=5)
    logging.basicConfig(
      handlers=[handler],
      level=logLevel,
      format= '[%(asctime)s] {%(filename)s:%(lineno)d} %(levelname)s - %(message)s',
      datefmt='%m-%d %H:%M:%S'
    )
  else:
    logging.basicConfig(
      level=logLevel,
      format= '[%(asctime)s] {%(filename)s:%(lineno)d} %(levelname)s - %(message)s',
      datefmt='%m-%d %H:%M:%S'
    )

class NextGenConfig:
  def __init__(self, content):
    self.key_file = ""
    self.provision_file = ""
    self.vm_prefix = ""
    self.log_level = ""

    theJson = json.loads(content)
    if "IBMCLOUDGEN2_KEY_FILE" in theJson:
      self.key_file = theJson["IBMCLOUDGEN2_KEY_FILE"]   

    if "IBMCLOUDGEN2_PROVISION_FILE" in theJson:
      self.provision_file = theJson["IBMCLOUDGEN2_PROVISION_FILE"]   

    if "IBMCLOUDGEN2_MACHINE_PREFIX" in theJson:
      self.vm_prefix = theJson["IBMCLOUDGEN2_MACHINE_PREFIX"]   

    if "LogLevel" in theJson:
      self.log_level = theJson["LogLevel"]   

  @property
  def key_file(self):
    return self._key_file

  @key_file.setter
  def key_file(self, value):
    self._key_file = value

  @property
  def provision_file(self):
    return self._provision_file

  @provision_file.setter
  def provision_file(self, value):
    self._provision_file = value

  @property
  def vm_prefix(self):
    return self._vm_prefix

  @vm_prefix.setter
  def vm_prefix(self, value):
    self._vm_prefix = value

  @property
  def log_level(self):
    return self._log_level

  @log_level.setter
  def log_level(self, value):
    self._log_level = value

  def __str__(self):
   return "api_key: " + self.api_key + "\n" + \
          "vm_prefix: " + self.vm_prefix + "\n" + \
          "provision_file: " + self.provision_file + "\n" + \
          "log_level: " + self.log_level

class NextGenTemplate:
  def __init__(self, content, templateId):
    self.templateId = ""
    self.imageId = ""
    self.subnetId = ""
    self.vpcId = ""
    self.rgId = ""
    self.vmType = ""
    self.securityGroupId = []
    self.sshkey_id = ""
    self.region = ""
    self.zone = ""
    self.maxNumber = 0
    self.userData = ""

    theJson = json.loads(content)
    if "templates" not in theJson: return

    for template in theJson["templates"]:
      if template["templateId"] == templateId:
        self.templateId = template["templateId"]
        if "maxNumber" in template:
          self.maxNumber = template["maxNumber"]
        if "imageId" in template:
          self.imageId = template["imageId"]
        if "subnetId" in template:
          self.subnetId = template["subnetId"]
        if "vpcId" in template:
          self.vpcId = template["vpcId"]
        if "resourceGroupId" in template:
          self.rgId = template["resourceGroupId"] 
        if "vmType" in template:
          self.vmType = template["vmType"]
        if "securityGroupIds" in template:
          self.securityGroupId = template["securityGroupIds"]
        if "sshkey_id" in template:
          self.sshkey_id = template["sshkey_id"]
        if "region" in template:
          self.region = template["region"]
        if "zone" in template:
          self.zone = template["zone"]
        if "userData" in template:
          self.userData = template["userData"]

  @property
  def maxNumber(self):
    return self._maxNumber
  @maxNumber.setter
  def maxNumber(self, value):
    self._maxNumber = value
  
  @property
  def imageId(self):
    return self._templateId
  @imageId.setter
  def imageId(self, value):
    self._templateId = value

  @property
  def imageId(self):
    return self._imageId
  @imageId.setter
  def imageId(self, value):
    self._imageId = value

  @property
  def subnetId(self):
    return self._subnetId
  @subnetId.setter
  def subnetId(self, value):
    self._subnetId = value

  @property
  def vpcId(self):
    return self._vpcId
  @vpcId.setter
  def vpcId(self, value):
    self._vpcId = value

  @property
  def rgId(self):
    return self._rgId
  @rgId.setter
  def rgId(self, value):
    self._rgId = value

  @property
  def vmType(self):
    return self._vmType
  @vmType.setter
  def vmType(self, value):
    self._vmType = value

  @property
  def securityGroupId(self):
    return self._securityGroupId
  @securityGroupId.setter
  def securityGroupId(self, value):
    self._securityGroupId = value

  @property
  def sshkey_id(self):
    return self._sshkey_id
  @sshkey_id.setter
  def sshkey_id(self, value):
    self._sshkey_id = value

  @property
  def region(self):
    return self._region
  @region.setter
  def region(self, value):
    self._region = value

  @property
  def zone(self):
    return self._zone
  @zone.setter
  def zone(self, value):
    self._zone = value

  @property
  def userData(self):
    return self._userData
  @userData.setter
  def userData(self, value):
    self._userData = value

  def __str__(self):
   return "imageId: " + self.imageId + "\n" + \
          "templateId: " + self.templateId + "\n" + \
          "subnetId: " + self.subnetId + "\n" + \
          "vpcId: " + self.vpcId + "\n" + \
          "rgId: " + self.rgId + "\n" + \
          "vmType: " + self.vmType + "\n" + \
          "securityGroupId: " + ", ".join(self.securityGroupId) + "\n" + \
          "sshkey_id: " + self.sshkey_id + "\n" + \
          "region: " + self.region + "\n" + \
          "zone: " + self.zone + "\n" + \
          "dns_instance_id: " + self.dns_instance_id + "\n" + \
          "dns_zone_id: " + self.dns_zone_id  + "\n" + \
          "domain_name: " + self.domain_name + "\n" + \
          "userData: " + self.userData

def GetNextGenConfigs(templateId=""):

  # get the templates conf file
  conf_dir = os.environ["PRO_CONF_DIR"]
  if conf_dir is None:
    print("The PRO_CONF_DIR env. variable is not set")
    sys.exit("The PRO_CONF_DIR env. variable is not set\n")
    
  template_file = conf_dir + "/conf/ibmcloudgen2_templates.json"
  if not path.exists(template_file):
    print("The file %s does not exist" % template_file)
    sys.exit("The file " + template_file + " does not exist\n")

  # get the template for the templateId in the request
  template_fp = open(template_file, "r")
  contents = template_fp.read()
  template_fp.close()
  logging.debug("template in GetNextGenConfigs " + templateId)
  template = NextGenTemplate(contents, templateId) if (templateId != "") else None

  # get the nextgen config file
  config_file = conf_dir + "/conf/ibmcloudgen2_config.json"
  if not path.exists(config_file):
    print("The file %s does not exist" % config_file)
    sys.exit("The file " + config_file + " does not exist\n")

  config_fp = open(config_file, "r")
  contents = config_fp.read()
  config_fp.close()

  config = NextGenConfig(contents)

  return config, template

def main():
  config_fp = open("conf/ibmcloudgen2_config.json", "r")
  if config_fp.mode == 'r':
    contents = config_fp.read()
    configObj = NextGenConfig(contents)
    print(configObj)
  else:
    logging.warning("config file not exist")
  config_fp.close()

  template_fp = open("conf/ibmcloudgen2_templates.json", "r")
  if template_fp.mode == 'r':
    contents = template_fp.read()
    templateObj = NextGenTemplate(contents, "CENTOS-Template-NGVM-1")
    print(templateObj)
  else:
    logging.warning("template file not exist")
  template_fp.close()

if __name__ == "__main__":
  logging.basicConfig(level=logging.DEBUG)
  main()
