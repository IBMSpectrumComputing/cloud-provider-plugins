#!/usr/bin/python

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

# -*- coding: utf-8 -*-

"""
Output: a string with format: retcode retval
    - if retcode = 0, then retval is the interface output
    - if retcode != 0, then retval is expect to be error message
"""

import json
import os, sys, getopt, socket, fcntl, stat
from Log import Log
import MachineFile
import OpenStackClient
import pdb

config_filename = 'osprov_config.json'
template_filename = 'osprov_templates.json'
machine_filename = 'osprov_machines.json'

class main:
    opt_getAvailableMachines = False
    opt_getReturnRequest = False
    opt_requestMachines = False
    opt_requestReturnMachines = False
    opt_getRequestStatus = False
    opt_getAvailableTemplates = False

    def __error(self, errmsg, *args):
            Log.logger.error(errmsg % args)
            print(errmsg % args)
            sys.exit(1)

    def __init__(self, argv):

        if len(argv) < 2:
            #log error input
            print ('The number of input parameters is not correct')
            sys.exit(1)

        self.homeDir = argv[1]
        if not os.path.isdir(self.homeDir):
            print('%s is not a valid host provider directory', self.homeDir)
            sys.exit(1)
        
        #Get lsf conf dir
        env_var = dict(os.environ)
        lsfConfTopDir =  env_var.get('LSF_ENVDIR', '')
        if lsfConfTopDir is None or len(lsfConfTopDir) == 0 or not os.path.isdir(lsfConfTopDir):
            print('LSF_ENVDIR is not set in env or LSF_ENVDIR: %s is not a directory', lsfConfTopDir)
            sys.exit(1)
        self.providerName = env_var.get('PROVIDER_NAME', '')
        if self.providerName is None or len(self.providerName) == 0:
            self.providerName = 'openstack'
        self.confDir = env_var.get('PRO_CONF_DIR', '')
        if not os.path.isdir(self.confDir):
            print('%s resource connector directory: %s does not exist', self.providerName, self.confDir)
            sys.exit(1)
        #Get lsf log dir
        logDir =  env_var.get('PRO_LSF_LOGDIR', '')
        if logDir is None or len(logDir) == 0 or not os.path.isdir(logDir):
            print('LSF_LOGDIR is not set in env or LSF log directory: %s is not a directory', logDir)
            sys.exit(1)               

        confFile = lsfConfTopDir + '/lsf.conf'
        uid = os.geteuid()
        gid = os.getegid()
        try:
            st = os.stat(confFile)
            uid = st.st_uid
            gid = st.st_gid
        except Exception, ex:
            printf('LSF conf file: %s does not exist', confFile)
            sys.exit(1)

        host_name = socket.gethostname()
        log_file_path = logDir + '/' + self.providerName+ '-provider.log.' + str(host_name)

        #Load host provider configuration
        try:
            with open(self.confDir + '/conf/' + config_filename) as conf_file:
                conf_data = json.load(conf_file)
        except Exception, ex:
            Log.init(log_file_path, 'INFO')
            os.chown(log_file_path, uid, gid)
            self.__error('%s is not well formated. exception: %s', config_filename, str(ex))

        # tighten file permission
        os.chmod(self.confDir + '/conf/' + config_filename, stat.S_IRUSR|stat.S_IWUSR|stat.S_IRGRP|stat.S_IWGRP)

        logLevel = conf_data.get("LogLevel", "INFO")
        Log.init(log_file_path, logLevel)
        #Load input file content for the interface calling
        self.input_data = {}
        if len(argv) == 3:
            input_file = argv[2]
            Log.logger.debug('Input file is %s', input_file)
            try:
                with open(input_file) as input_data_file:
                    self.input_data = json.load(input_data_file)
            except Exception, ex:
                self.__error('input file %s is not well formated', input_file)

        else:
            Log.logger.debug('There is no input file provided')

        self.mode               = conf_data.get('mode', 'production')
        self.prefix             = conf_data.get('InstancePrefix', 'host')
        self.username           = conf_data.get('OS_USERNAME', '')
        self.api_key            = conf_data.get('OS_PASSWORD', '')
        self.auth_url           = conf_data.get('OS_AUTH_URL', '')
        self.user_domain_id     = conf_data.get('OS_USER_DOMAIN_ID', 'default')
        self.project_id         = conf_data.get('OS_PROJECT_NAME', '')
        self.project_domain_id  = conf_data.get('OS_PROJECT_DOMAIN_ID', self.user_domain_id)
        self.security_groups    = conf_data.get('OS_SECURITYGROUPS', [])
        self.keypair            = conf_data.get('OS_KEYPAIR', '')
        self.network            = conf_data.get('OS_NETWORK_NAME', '')
        self.dns_server         = conf_data.get('OS_DNS_SERVER', '')
        if self.auth_url[-1] == '/': self.auth_url = self.auth_url[:-1]
        if (not self.username):     self.__error('OS_USERNAME is not defined in %s', config_filename)
        if (not self.api_key):      self.__error('OS_PASSWORD is not defined in %s', config_filename)
        if (not self.auth_url):     self.__error('OS_AUTH_URL is not defined in %s', config_filename)
        if (not self.project_id):   self.__error('OS_PROJECT_NAME is not defined in %s', config_filename)
        if (not self.network):      self.__error('OS_NETWORK_NAME is not defined in %s', config_filename)
        self.cred = {'Username': self.username,
                     'APIKey': self.api_key,
                     'AuthURL': self.auth_url,
                     'ProjectID': self.project_id,
                     'ProjectDomainID': self.project_domain_id,
                     'UserDomainID': self.user_domain_id}
        self.os_client = None

        #Load tempalte definition
        try:
            with open(self.confDir + '/conf/' + template_filename) as template_file:
                template_data = json.load(template_file)
        except Exception, ex:
            self.__error('%s is not well formated. exception: %s', template_filename, str(ex))
        if (not template_data.has_key('Templates')) or (not type(template_data['Templates']) is list):
            self.__error('No templates found in %s', template_filename)
        self.templates = {}
        for t in template_data['Templates']:
            if not t.has_key('Name'): self.__error('Template name not found')
            if not t.has_key('Attributes'): self.__error('Attributes not found for template %s', t['Name'])
            if not t.has_key('Image'): self.__error('Image not found for template %s', t['Name'])
            if not t.has_key('Flavor'): self.__error('Flavor not found for template %s', t['Name'])
            self.templates[t['Name']] = t
            if not t.has_key('Priority'): t['Priority'] = '0'
            if t.has_key('priority'): t['Priority'] = t['priority']

        # get machine file location
        prod_name = os.getenv('PROVIDER_NAME', 'openstack')
        data_dir = os.getenv('PRO_DATA_DIR', self.homeDir + '/data')
        if not os.path.exists(data_dir):
            try:
                os.makedirs(data_dir)
            except Exception, ex:
                self.__error('Failed to create directory %s. exception: %s', data_dir, str(ex))
        self.machineFile = '%s/%s.%s' % (data_dir, machine_filename, prod_name)
        self.lockfp = None

        try:
            opts, args = getopt.getopt(argv, "ltqnrs", ["getAvailableMachines", "getAvailableTemplates", "getReturnRequests", "requestMachines", "requestReturnMachines", "getRequestStatus"])
        except getopt.GetoptError:
            print ("GetoptError")
            sys.exit(1)

        for opt, arg in opts:
            if opt in ("-l", "--getAvailableMachines"):
                self.opt_getAvailableMachines = True
            if opt in ("-t", "--getAvailableTemplates"):
                self.opt_getAvailableTemplates = True
            elif opt in ("-q", "--getReturnRequests"):
                self.opt_getReturnRequest = True
            elif opt in ("-n", "--requestMachines"):
                self.opt_requestMachines = True
            elif opt in ("-r", "--requestReturnMachines"):
                self.opt_requestReturnMachines = True
            elif opt in ("-s", "--getRequestStatus"):
                self.opt_getRequestStatus = True

    def lock(self):
        if self.lockfp: return
        lockname = self.machineFile + '.lock'
        try:
            if not os.path.exists(lockname):
                Log.logger.info('Creating a new lock file: %s', lockname)
                self.lockfp = file(lockname, 'w+')
            else:
                self.lockfp = file(lockname, 'r+')
            fcntl.lockf(self.lockfp, fcntl.LOCK_EX)
        except Exception, ex:
            self.__error('Failed to lock %s. exception: %s', lockname, str(ex))

    def unlock(self):
        if self.lockfp:
            try:
                fcntl.lockf(self.lockfp, fcntl.LOCK_UN)
            except Exception, ex:
                Log.logger.warn('Failed to unlock. exception: %s', str(ex))
            self.lockfp = None

    def createOpenStackClient(self):
        if not self.os_client:
            self.os_client = OpenStackClient.OpenStackClient(self.cred)
            if not self.os_client.token:
                self.__error('Failed to obtain OpenStack auth token. %s', self.os_client.errmsg)

    """
    retrieve information about the available templates from the reosurce provider
    """
    def getAvailableTemplates(self):

        Log.logger.info('Calling host provider to get all the available templates')
        #m_by_t = MachineFile.get_machines_by_template(self.machineFile)
        #if m_by_t is None: return(1, 'Failed to load machine file')
        out = {'message':'', 'templates':[]}
        for t in self.templates.values():
            t1 = {}
            for k, v in t.items():
                if k == 'Name': t1['templateId'] = v
                elif k == 'MaxNumber': t1['maxNumber'] = v
                elif k == 'Priority': t1['priority'] = v
                elif k == 'Attributes':
                    t1['attributes'] = v
                    t1['attributes']['template'] = ["String", t['Name']]
                    t1['attributes']['openstackhost'] = ["Boolean", '1']
                    for k1, v1 in v.items():
                        t1['attributes'][k1] = v1
                else:
                    t1[k] = v 
                    #if m_by_t.has_key(t['Name']): mlist = m_by_t.get(t['Name']).machines
                    #else: mlist = []
                    #t1['requestedMachines'] = [m.name for m in mlist]
            out['templates'].append(t1)
        return (0,json.dumps(out, indent = 2))

    """
    Retrieve information about the available machines from the resource provider
    """
    def getAvailableMachines(self):

        Log.logger.info('Calling host provider to get all the available machines')

        out = {'message':'This interface is not supported by '+ self.providerName +' provider.', 'machines':[]}
        return (0, json.dumps(out))

    """
    Retrieve information if the resource provider wants any machines back.
    """
    def getReturnRequests(self):

        Log.logger.info('Calling host provider to get the machines which have to be returned')

        out = {'message':'This interface is not supported by '+ self.providerName +' provider.', 'requests':[]}
        return (0, json.dumps(out))

    """
    Raise a request to get machines from the resource provider
    """
    def requestMachines(self):

        Log.logger.info('Calling host provider to request machines')

        if not self.input_data:
            Log.logger.error('valid input file is required for requestMachines call')
            return(1, 'Valid input file is required for requestMachines call')

        code = 0
        machines = self.input_data.get('machines', None)
        template_para = self.input_data.get('template', None)
        acctTagValue = self.input_data.get('rc_account', None)
        return_data = {}
        message = ''
        if machines:
            return(1, 'Input parameter machines is not supported for interface requestMachines in this host provider')
        elif template_para:
            attr_list=[]
            hosts_number = None
            templateId = None
            for key, value in template_para.items():
                if key == 'machineCount':
                    if value is not None:
                        hosts_number = str(value)
                        Log.logger.debug('')
                    else:
                        Log.logger.warn('machineCount value %s is not valid', value)
                elif key == 'templateId':
                    if value is not None and len(str(value).strip()) > 0:
                        templateId=str(value)
                    else:
                        Log.logger.warn('templateId value %s is not valid', value)
                else:
                    Log.logger.warn('Attribute %s is not supported by this resource provider', key)

            if hosts_number is None or len(hosts_number) == 0:
                return(1, 'Please specify machineCount in the input parameters')
            try:
                hosts_number = int(hosts_number)
            except:
                return(1, 'Please provide valid machineCount in the input parameters')
            if templateId is None or len(templateId) == 0 or (not self.templates.has_key(templateId)):
                return(1, 'Please provide valid templateId in the input parameters')

            Log.logger.info('Request %d machine(s) with template %s and account tag %s', hosts_number, templateId, acctTagValue)
            template = self.templates[templateId]

            self.createOpenStackClient()

            userData = template.get('UserData', {})
            if userData:
                if type(userData) == str or type(userData) == unicode:
                    tmp = userData.split(';')
                    userData = {}
                    for t in tmp:
                        pos = t.find('=')
                        if pos > 0: userData[t[:pos]] = t[pos+1:]
                        else: userData[t] = ''
                else:
                    errmsg = 'UserData parameter of template %s is invalid' % templateId
                    Log.logger.error(errmsg)
                    return (1, errmsg)
            # userData = template_para.get('userData', {})
            # prepare openstack client parameters
            os_flavor = template['Flavor']
            os_image = template['Image']
            # os_network = userData.get('network', self.network)
            os_network = self.network
            # os_meta = userData.get('meta', {})
            # if os_meta:
            #     tmp = os_meta.split(',')
            #     os_meta = {}
            #     for t in tmp:
            #         pos = t.find(':')
            #         if pos > 0: os_meta[t[:pos]] = t[pos+1:]
            os_meta = {}
            # os_key_name = userData.get('key-name', self.keypair)
            os_key_name = self.keypair
            # os_availability_zone = userData.get('availability-zone', None)
            os_availability_zone = None
            # os_security_groups = userData.get('security-groups', '')
            # if os_security_groups:
            #     os_security_groups = [{'name': sg.strip()} for sg in os_security_groups.split(',')]
            # else:
            #     os_security_groups = self.security_groups
            os_security_groups = self.security_groups
            if not os_security_groups:
                os_security_groups.append({'name':'default'})
            # os_admin_pass = userData.get('admin-pass', None)
            os_admin_pass = None
            # get volume info
            volume_devices = ''
            os_volumes = []
            # os_volumes = userData.get('volumes', [])
            # if os_volumes:
            #     os_volumes = [v.strip() for v in os_volumes.split(',')]
            #     volume_ids = []
            #     for v in os_volumes:
            #         vid = self.os_client.get_volume_id(v)
            #         if not vid:
            #             errmsg = 'Unable to get id of volume %s' % v
            #             Log.logger.error(errmsg)
            #             return (1, errmsg)
            #         volume_ids.append(vid)
            #     volume_devices = ' '.join([vname + ':virtio-' + vid[:20] for vname,vid in zip(os_volumes, volume_ids)])
            # read user_data.sh
            post_script = file(self.homeDir + '/scripts/user_data.sh').read()
            # # substitute %VOLUME_DEVICES%
            # post_script = post_script.replace('%VOLUME_DEVICES%', volume_devices)
            # substitute %EXPORT_USER_DATA% with export command
            export_cmd = ''
            if userData: export_cmd = 'export ' + ' '.join(['%s="%s"' % (k.replace('-','_').upper(),v) for k,v in userData.items()]) + ';'
            if acctTagValue: export_cmd += 'export rc_account=' +  acctTagValue;
            post_script = post_script.replace('%EXPORT_USER_DATA%', export_cmd)
            # # substitute %OS_RESOURCES% with resource string
            # res_string = '[resource openstackhost][resourcemap %s*template]' % templateId
            # if userData: res_string += ''.join(['[resourcemap %s*%s]'% (v,k) for k,v in userData.items() if v])
            # post_script = post_script.replace('%OS_RESOURCES%', res_string)
            # substitute %OS_DNS_SERVER%
            post_script = post_script.replace('%OS_DNS_SERVER%', self.dns_server)
            # user script will be injected to vm instance
            user_script = template.get('UserScript', None)
            if user_script: user_script = os.path.join(self.homeDir, user_script)
            # additional create server parameter
            para_filename = self.confDir + '/conf/create_server.json'
            additional_para = None
            if os.path.exists(para_filename):
                try:
                    additional_para = json.load(file(para_filename))
                except Exception, ex:
                    additional_para = None
                    Log.logger.error('Failed to load data from %s', para_filename)

            # Generate request id and host names
            requestId, hosts = MachineFile.new_request(self.machineFile, templateId, hosts_number, self.prefix, acctTagValue)
            if not requestId:
                return(1, 'Failed to create a new request')

            machine_msg = []
            for host in hosts:
                # create ip address
                ip, port_id = self.os_client.create_ip_port(os_network, os_security_groups)
                # update machine information
                machine_info = {'name': host}
                if ip:
                    host = '%s-%s' % (self.prefix, ip.replace('.', '-'))
                    machine_info['rename'] = host
                    machine_info['port'] = port_id
                else:
                    machine_info['status'] = 'error'
                    machine_info['message'] = 'Unable to get an IP address for this machine'
                MachineFile.update_machines(self.machineFile, [machine_info])
                # create machines
                if ip:
                    Log.logger.info('Creating machines %s', host)
                    machine_info = self.os_client.create_machine(host, port_id, image = os_image,
                                        flavor = os_flavor, user_data = post_script, volumes = os_volumes,
                                        user_script = user_script, key_name = os_key_name, security_groups = None,
                                        metadata = os_meta, availability_zone = os_availability_zone, admin_pass = os_admin_pass,
                                        additional_para = additional_para)
                    # update machine status
                    if machine_info['id']: machine_info['status'] = 'building'
                    else: machine_info['status'] = 'error'
                    MachineFile.update_machines(self.machineFile, [machine_info])

                    if machine_info['message']:
                        machine_msg.append(host + ' - ' + machine_info['message'])

            message = ','.join(machine_msg)
        else:
            Log.logger.error('Either machines or templates should be provided in the input')
            return(1, 'Either machines or templates should be provided in the input parameters')

        return_data['message'] = message
        return_data['requestId'] = requestId
        return(code, json.dumps(return_data, indent = 2))

    """
    Raise a request to return machines back to the resource provider
    """
    def requestReturnMachines(self):

        Log.logger.info('Calling host provider to return machines back')

        if not self.input_data:
            Log.logger.error('Valid input file is required for requestReturnMachines call')
            return(1, 'Valid input file is required for requestReturnMachines call')

        code = 0
        return_data = {}
        requestId = ''
        message = ''
        machines = self.input_data.get('machines', None)
        if machines:
            hostnames = []
            for machine in machines:
                #find all the machines which have to be returned
                for key, value in machine.items():
                    if key == 'name':
                        if value is not None and len(str(value).strip()) > 0:
                            hostnames.append(str(value))
                        else:
                            Log.logger.warn('machine name is not correctly specified')
                    else:
                        if key != 'machineId':
                            Log.logger.warn('Attribute %s is not supported by this resource provider', key)
            if not hostnames:
                return(1, 'Please specify name for machines in parameters')

            Log.logger.info('Return machine(s) %s back', hostnames)

            requestId, machine_data = MachineFile.new_return_request(self.machineFile, hostnames)
            if not requestId: return(1, 'Failed to create a new request')

            Log.logger.info('Deleting machines %s', str(hostnames))
            self.createOpenStackClient()
            machine_msg = []
            for m in machine_data:
                errmsg = self.os_client.delete_machine(m.id)
                machine_info = {'name': m.name, 'status': 'active' if errmsg else 'deleting'}
                if errmsg:
                    machine_info['message'] = errmsg
                    machine_msg.append(errmsg)
                MachineFile.update_machines(self.machineFile, [machine_info])
            message = ','.join(machine_msg)
        else:
            return(1, 'machines information must be provided in the input parameters')

        return_data['message'] = message
        return_data['requestId'] = requestId
        return(code, json.dumps(return_data, indent = 2))

    """
    Retrieve status of particular interface calling, etc. requestMachines and requestReturnMachines
    """
    def getRequestStatus(self):

        Log.logger.info('Calling host provider to get the request status')

        if not self.input_data:
            Log.logger.error('Valid input file is required for getRequestStatus call')
            return(1, 'Valid input file required for getRequestStatus call')

        return_requests = []
        return_data = {}
        requests = self.input_data.get('requests', None)
        if requests:
            for request in requests:
                for key, value in request.items():
                    if key == 'requestId':
                        if value is not None and len(str(value).strip()) > 0:
                            requestId = str(value)
                    else:
                        Log.logger.warn('Attribute %s is not supported by this host provider', key)

                if requestId is None or len(requestId) == 0:
                    return(1, 'Please specify valid requestId in the input parameters')

            for request in requests:
                requestId = request['requestId']
                Log.logger.info('Get status for request %s', requestId)

                status = 'complete_with_error'
                message = ''
                machines = []
                request_data = MachineFile.get_request(self.machineFile, requestId)
                if request_data:
                    self.createOpenStackClient()
                    # some machines are still building or deleting, we need to query openstack again
                    if request_data.status == 'running':
                        # refresh request status
                        if request_data.type == 'request':
                            for machine in request_data.machines:
                                server_details = {}
                                new_status = ''
                                if machine.status == 'building':
                                    server_details = self.os_client.get_machine_details(machine.id)
                                    if server_details and server_details['status']:
                                        new_status = server_details['status']
                                    if new_status and machine.status != new_status:
                                        machine.status = new_status
                                        # delete the machines which are in 'ERROR' status
                                        if machine.status == 'error':
                                            self.os_client.delete_machine(machine.id)
                                            self.os_client.delete_port(machine.port)
                                        # update dns name for active machines
                                        # elif machine.status == 'active':
                                        #     self.os_client.update_machine_dns(machine.id, machine.name)
                                        # write status back to file
                                        machine_info = {'name': machine.name, 'status': machine.status}
                                        if server_details and server_details.has_key('privateIpAddr'):
                                            machine_info['privateIpAddr'] =  server_details['privateIpAddr']
                                            machine.privateIpAddr = server_details['privateIpAddr']
                                        if server_details and server_details.has_key('publicIpAddr'):
                                            machine_info['publicIpAddr'] = server_details['publicIpAddr']
                                            machine.publicIpAddr = server_details['publicIpAddr']
                                        if server_details and server_details.has_key('launchTime'):
                                            machine_info['launchTime'] = server_details['launchTime']
                                        MachineFile.update_machines(self.machineFile, [machine_info])
                            request_data.status = 'complete'
                            for m in request_data.machines:
                                if m.status == 'building':
                                    request_data.status = 'running'
                                    break
                                if m.status == 'error':
                                    request_data.status = 'complete_with_error'
                        else:
                            for machine in request_data.machines:
                                server_details = {}
                                new_status = ''
                                if machine.status == 'deleting':
                                    server_details = self.os_client.get_machine_details(machine.id)
                                    if server_details and server_details['status']:
                                        new_status = server_details['status']
                                    if new_status and machine.status != new_status:
                                        machine.status = new_status
                                        # we treat the machine still as 'active' if it's found in 'ERROR' status in return request
                                        if machine.status == 'error': machine.status = 'active'
                                        elif machine.status == 'deleted' and machine.port:
                                            port_status = self.os_client.delete_port(machine.port)
                                            # if failed to delete port, set machine 'active', so HF will retry to delete machine and port
                                            if port_status: machine.status = 'active'
                                        # write status back to file                                        
                                        machine_info = {'name': machine.name, 'status': machine.status}                                       
                                        if machine.status == 'deleted': machine_info['port'] = ''
                                        MachineFile.update_machines(self.machineFile, [machine_info])
                            request_data.status = 'complete'
                            for m in request_data.machines:
                                if m.status == 'deleting':
                                    request_data.status = 'running'
                                    break
                                if m.status == 'active':
                                    request_data.status = 'complete_with_error'
                    # construct response
                    status = request_data.status
                    for m in request_data.machines:
                        mname = m.name
                        if request_data.type == 'request':
                            if m.status == 'building': mresult = 'executing'
                            elif m.status == 'error': mresult = 'fail'
                            else: mresult = 'succeed'
                        else:
                            if m.status == 'deleting': mresult = 'executing'
                            elif m.status == 'deleted': mresult = 'succeed'
                            else: mresult = 'fail'
                        if m.privateIpAddr:
                            privateIpAddr = m.privateIpAddr
                        else:
                            privateIpAddr = ''
                        if m.publicIpAddr:
                            publicIpAddr = m.publicIpAddr
                            publicDnsName = m.name
                        else:
                            publicIpAddr = ''
                            publicDnsName = '' 
                        if m.launchTime:
                            launchtime = m.launchTime;
                        else:
                            launchtime = '0';

                        machines.append({'machineId': m.id , 'name': mname, 'result': mresult, 'message': m.message, 'rcAccount': m.rcAccount, 'status': m.status, 'privateIpAddress': privateIpAddr, 'publicIpAddress': publicIpAddr, 'launchtime': launchtime, 'publicDnsName': publicDnsName})
                    # for machines in error status (which means they are not succesfully created in openstack) of this request,
                    # clean them up by setting a fake returnId and change to deleted status
                    # if port is created but instance fails, delete the port
                    if request_data.type == 'request' and request_data.status != 'running':
                        for m in request_data.machines:
                            if m.status == 'error':
                                if m.port: self.os_client.delete_port(m.port)
                                MachineFile.update_machines(self.machineFile, [{'name': m.name, 'status': 'deleted', 'returnId': 'error-machines', 'port': ''}])
                else:
                    Log.logger.warn('Request %s is not found in data file', requestId)
                    status = 'complete'
                    message = 'request not found'
                return_requests.append({'requestId': requestId, 'status': status, 'message': message, 'machines': machines})

        else:
            Log.logger.error('requests information must be provided in the input')
            return(1, 'requests information must be provided in the input parameters')

        return_data['requests'] = return_requests
        return(0, json.dumps(return_data, indent = 2))
"""
Main
"""
if __name__ == '__main__':
    obj = main(sys.argv[1:])
    if obj.opt_getAvailableMachines:
        (retcode, retVal) = obj.getAvailableMachines()
        print (retVal)
        sys.exit(retcode)
    elif obj.opt_getAvailableTemplates:
        (retcode, retVal) = obj.getAvailableTemplates()
        print (retVal)
        sys.exit(retcode)
    elif obj.opt_getReturnRequest:
        (retcode, retVal) = obj.getReturnRequests()
        print (retVal)
        sys.exit(retcode)
    elif obj.opt_requestMachines:
        obj.lock()
        (retcode, retVal) = obj.requestMachines()
        print (retVal)
        obj.unlock()
        sys.exit(retcode)
    elif obj.opt_requestReturnMachines:
        obj.lock()
        (retcode, retVal) = obj.requestReturnMachines()
        print (retVal)
        obj.unlock()
        sys.exit(retcode)
    elif obj.opt_getRequestStatus:
        obj.lock()
        (retcode, retVal) = obj.getRequestStatus()
        print (retVal)
        obj.unlock()
        sys.exit(retcode)
