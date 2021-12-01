#!/usr/bin/python
#
#********************************************************
#
# Name: docker-run.py
#
# (c) Copyright International Business Machines Corp 2020.
# US Government Users Restricted Rights - Use, duplication or disclosure
# restricted by GSA ADP Schedule Contract with IBM Corp.
#
#********************************************************

# -*- coding: utf-8 -*-

"""
Output: a string with format: retcode retval
    - if retcode = 0, then retval is the interface output
    - if retcode != 0, then retval is expect to be error message
"""
from __future__ import division
import json
import os, sys, getopt, socket, fcntl, stat, math
import PolicyFile
from Log import Log
import pdb, time
import subprocess
from fcntl import F_GETFL, F_SETFL
from os import O_NONBLOCK, read

config_filename = 'policy_config.json'

class main:

    def __error(self, errmsg, *args):
            Log.logger.error(errmsg % args)
            print(errmsg % args)
            sys.exit(1)
    
    def __warn(self, errmsg, *args):
            Log.logger.warn(errmsg % args)            

    def __init__(self, argv):

        if len(argv) < 2:
            #log error input
            print ('The number of input parameters is not correct')
            sys.exit(1)

        #Get lsf conf dir
        env_var = dict(os.environ)
        lsfConfTopDir =  env_var.get('LSF_ENVDIR', '')
        if lsfConfTopDir is None or len(lsfConfTopDir) == 0 or not os.path.isdir(lsfConfTopDir):
            print('LSF_ENVDIR is not set in env or LSF_ENVDIR: %s is not a directory', lsfConfTopDir)
            sys.exit(1)

        #Get lsf log dir
        logDir =  env_var.get('PRO_LSF_LOGDIR', '')
        if logDir is None or len(logDir) == 0 or not os.path.isdir(logDir):
            print('LSF_LOGDIR is not set in env or LSF log directory: %s is not a directory', logDir)
            sys.exit(1)

        host_name = socket.gethostname()
        log_file_path = logDir + '/policy.log.' + str(host_name)
        Log.init(log_file_path)

        #Load input file content for the interface calling
        self.input_data = {}
        if len(argv) == 2:
            self.input_file = argv[0]
            Log.logger.debug('Input file is %s', self.input_file)
            try:
                with open(self.input_file) as input_data_file:
                   self.input_data = json.load(input_data_file)
                   self.demand_data = self.input_data.get('request', None)
            except Exception as ex:
                self.__error('input file %s is not well formated', self.input_file)
        else:
            Log.logger.debug('There is no input file provided')

        policy_config_file =  lsfConfTopDir + '/resource_connector/' + config_filename

        if (not os.path.exists(policy_config_file)) or (not os.path.isfile(policy_config_file)):
            Log.logger.warn('Cannot access %s file. Skip calculating new demand.', policy_config_file)
            output = {'message':'Success', 'request':self.demand_data}
            print (json.dumps(output, indent = 2))
            sys.exit(0)


        #Load policies configuration file
        try:
            with open(policy_config_file) as conf_file:
                policy_data = json.load(conf_file)
        except Exception as ex:
            self.__error('%s is not well formated. exception: %s', config_filename, str(ex))

        if self.demand_data:
            for key, value in self.demand_data.items():
                 if key == 'providerName':
                     if value is not None and len(str(value).strip()) > 0:
                         self.providerName=str(value)
                     else:
                        Log.logger.warn('providerName value %s is not valid', value)
                 elif key == 'templateName':
                     if value is not None and len(str(value).strip()) > 0:
                         self.templateName=str(value)
                     else:
                        Log.logger.warn('templateName value %s is not valid', value)
                 elif key == 'rcAccount':
                     if value is not None and len(str(value).strip()) > 0:
                         self.rcAccount=str(value)
                     else:
                        Log.logger.warn('rcAccount value %s is not valid', value)
                 elif key == 'requestTime':
                     if value is not None and len(str(value).strip()) > 0:
                         self.requestTime=str(value)
                     else:
                        Log.logger.warn('requestTime value %s is not valid', value)
                 elif key == 'newDemand':
                     if value is not None and int(value) >= 0:
                        self.newDemand = int(value)
                     else:
                        Log.logger.warn('newDemand value %s is not valid', value)
                 elif key == 'targetAlloc':
                     if value is not None and int(value) >= 0:
                         self.targetAlloc=int(value)
                     else:
                         Log.logger.warn('targetAlloc value %s is not valid', value)
                 elif key == 'allocated':
                     if value is not None and int(value) >= 0:
                         self.allocated=int(value)
                     else:
                        Log.logger.warn('allocated value %s is not valid', value)
                 elif key == 'requested':
                     if value is not None and int(value) >= 0:
                         self.requested=int(value)
                     else:
                        Log.logger.warn('requested value %s is not valid', value)
                 elif key == 'reclaimed':
                     if value is not None and int(value) >= 0:
                         self.reclaimed=int(value)
                     else:
                        Log.logger.warn('reclaimed value %s is not valid', value)
                 else:
                     Log.logger.warn('Attribute %s is not supported by this resource provider', key)

        self.userDefinedScriptPath = ""
        if(not policy_data.has_key('UserDefinedScriptPath')) or (not os.path.exists(policy_data['UserDefinedScriptPath'])):
            Log.logger.info('UserDefinedScriptPath is not defined or not valid')
        else:
            if os.path.isfile(policy_data['UserDefinedScriptPath']) and os.access(policy_data['UserDefinedScriptPath'], os.X_OK):
                self.userDefinedScriptPath = policy_data['UserDefinedScriptPath']
            else:
                Log.logger.warn('UserDefinedScriptPath %s is not an executable',  policy_data['UserDefinedScriptPath'])
        if (not policy_data.has_key('Policies')) or (not type(policy_data['Policies']) is list):
            if (not self.userDefinedScriptPath):
                Log.logger.warn('No policies found in %s. Skip calculating new demand.', policy_config_file)
                output = {'message':'Success', 'request':self.demand_data}
                print (json.dumps(output, indent = 2))
                sys.exit(0)
            else:
                self.__error('No policies found in %s', policy_config_file)
        self.policies = {}
        for p in policy_data['Policies']:
            if not p.has_key('Name'): self.__error('Policy name not found')
            if not p.has_key('Consumer'): self.__warn('Consumer not found for policy %s', p['Name'])
            if not p.has_key('MaxNumber'): self.__warn('Max Number not found for policy %s', p['Name'])
            if not p.has_key('StepValue'): self.__warn('Step Value not found for policy %s', p['Name'])
            self.policies[p['Name']] = p
          # get machine file location

        if not self.policies and not self.userDefinedScriptPath :
            Log.logger.warn('Empty policy list in %s. Skip calculating new demand.', policy_config_file)
            output = {'message':'Success', 'request':self.demand_data}
            print (json.dumps(output, indent = 2))
            sys.exit(0)

        data_dir = os.getenv('PRO_DATA_DIR', '/tmp/data')
        if not os.path.exists(data_dir):
            try:
                os.makedirs(data_dir)
            except Exception as ex:
                self.__error('Failed to create directory %s. exception: %s', data_dir, str(ex))
        self.policyFile = '%s/policy.json'% (data_dir)
        self.lockfp = None
    def lock(self):
        if self.lockfp: return
        lockname = self.policyFile + '.lock'
        try:
            if not os.path.exists(lockname):
                Log.logger.info('Creating a new lock file: %s', lockname)
                self.lockfp = file(lockname, 'w+')
            else:
                self.lockfp = file(lockname, 'r+')
                fcntl.lockf(self.lockfp, fcntl.LOCK_EX)
        except Exception as ex:
            self.__error('Failed to lock %s. exception: %s', lockname, str(ex))

    def unlock(self):
        if self.lockfp:
            try:
                fcntl.lockf(self.lockfp, fcntl.LOCK_UN)
            except Exception as ex:
                Log.logger.warn('Failed to unlock. exception: %s', str(ex))
            self.lockfp = None

    def _run_local_cmd(self, cmd):
        """
        Execute the external command and get its exitcode, stdout and stderr.
        """
        Log.logger.debug('Run client cmd: %s', cmd)
        env = dict(os.environ)
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, shell=True, env=env)
        proc.wait()
        #flags = fcntl(proc.stdout, F_GETFL) # get current proc.stdout flags
        #fcntl(proc.stdout, F_SETFL, flags | O_NONBLOCK)
        out, err = proc.communicate()
        exitcode = proc.returncode
        # Log if error
        if exitcode :
            Log.logger.warn('Failed to run cmd: %s, exitcode : %i, stdout : %s, stderr : %s', cmd, exitcode, out, err)

        return exitcode, out, err

    def calculateDemand(self):
        inData = {'request':self.demand_data}
        if self.userDefinedScriptPath:
            tmp_file = file(self.input_file + '.tmp', 'w')
            fdata = json.dump(inData, tmp_file, indent = 2)
            tmp_file.flush()
            returnReq_cmd = self.userDefinedScriptPath + ' ' + self.input_file
            exitcode, out, err = self._run_local_cmd(returnReq_cmd)
            if exitcode:
                Log.logger.error('%s failed with error [%s] exitcode[%d] ', self.userDefinedScriptPath, err, exitcode)
                out =  err
                self.newDemand = 0
            else:
                tmpOutData = json.loads(out)
                output_data = tmpOutData.get('request', None)
                if output_data:
                    for key, value in output_data.items():
                        if key == 'newDemand':
                             if value is not None:
                                 self.newDemand=int(value)
                             break
                else:
                    self.newDemand = 0
            return (exitcode, out)
        return (0,json.dumps(out, indent = 2))
"""
Main
"""
if __name__ == '__main__':
    if len(sys.argv) == 3:
        sys.stdout = open (sys.argv[2], 'w')
        obj = main(sys.argv[1:])
        #obj.lock()
        (retcode, retVal) = obj.calculateDemand()
        #obj.unlock()
        print (retVal)
        Log.logger.debug(' Written %s successfully to the output file %s with exitCode %d ', retVal, sys.argv[2], retcode)
        sys.stdout.close()
        sys.exit(retcode)
    else:
        sys.exit(1)
        
                    
                         
        
            
                     
        
