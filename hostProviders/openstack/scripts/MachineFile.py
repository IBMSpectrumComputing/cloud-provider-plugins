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

import json
import os
import fcntl
import uuid
import shutil
import pdb
from Log import Log

class Machine:
    def __init__(self, data):
        for k in data:
            setattr(self, k, data[k])

class Request:
    def __init__(self, id):
        self.type = ''
        self.id = id
        self.status = ''
        self.message = ''
        self.machines = []

class Template:
    def __init__(self, name):
        self.name = name
        self.machines = []

class MachineFile:
    def __init__(self, filename):
        self.filename = filename
        self.fp = None
        self.lockname = self.filename + '.lock'
        self.lockfp = None
        self.machines = {}
        self.requests = {}
        self.return_requests = {}
        self.templates = {}

    def lock(self):
        if not os.path.exists(self.lockname):
            Log.logger.info('Creating a new lock file: %s', self.lockname)
            self.lockfp = file(self.lockname, 'w+')
        else:
            self.lockfp = file(self.lockname, 'r+')
        fcntl.lockf(self.lockfp, fcntl.LOCK_EX)

    def unlock(self):
        if self.lockfp:
            fcntl.lockf(self.lockfp, fcntl.LOCK_UN)

    def load(self):
        self.machines = {}
        self.requests = {}
        self.return_requests = {}
        self.templates = {}
        if not os.path.exists(self.filename):
            Log.logger.info('Creating a new machine data file: %s', self.filename)
            self.fp = file(self.filename, 'w+')
            return
        elif os.path.getsize(self.filename) == 0: return
        else: self.fp = file(self.filename, 'r+')
        self.fp.seek(0)
        machine_data = json.loads(self.fp.read())
        if machine_data.has_key('machines') and type(machine_data['machines']) is list:
            for m in machine_data['machines']:
                m = Machine(m)
                self.machines[m.name] = m
                if m.requestId:
                    if self.requests.has_key(m.requestId):
                        req = self.requests.get(m.requestId)
                    else:
                        req = Request(m.requestId)
                        req.type = 'request'
                        req.status = 'complete'
                        self.requests[m.requestId] = req
                    req.machines.append(m)
                    if m.status == 'building':
                        req.status = 'running'
                    elif m.status == 'error' and (not req.status == 'running'):
                        req.status = 'complete_with_error'
                if m.returnId:
                    if self.return_requests.has_key(m.returnId):
                        ret = self.return_requests.get(m.returnId)
                    else:
                        ret = Request(m.returnId)
                        ret.type = 'return'
                        ret.status = 'complete'
                        self.return_requests[m.returnId] = ret
                    ret.machines.append(m)
                    if m.status == 'deleting':
                        ret.status = 'running'
                    elif m.status == 'active':
                        ret.status = 'complete_with_error'
            # rescan machine to generate template list
            for m in self.machines.values():
                if self.templates.has_key(m.template):
                    t = self.templates.get(m.template)
                else:
                    t = Template(m.template)
                    self.templates[m.template] = t
                req = self.requests[m.requestId]
                if m.status == 'active' and (req.status == 'complete' or req.status == 'complete_with_error'):
                    t.machines.append(m)

    def save(self):
        for ret in self.return_requests.values():
            ret.status = 'complete'
            for m in ret.machines:
                if m.status == 'deleting':
                    ret.status = 'running'
                elif m.status == 'active':
                    ret.status = 'complete_with_error'
        # remove machines which belong to complete return requests
        machine_list = [m.__dict__ for m in self.machines.values()
            if not (m.returnId and self.return_requests.has_key(m.returnId) and self.return_requests[m.returnId].status == 'complete')]
        data = {'machines': machine_list}
        tmp_file = file(self.filename + '.tmp', 'w')
        fdata = json.dump(data, tmp_file, indent = 2)
        tmp_file.flush()
        #del tmp_file
        shutil.move(self.filename + '.tmp', self.filename)
        #fdata = json.dumps(data, indent = 2)
        #self.fp.seek(0)
        #self.fp.truncate(0)
        #self.fp.write(fdata)
        #self.fp.flush()

def get_machines_by_template(filename):
    machine_file = MachineFile(filename)
    # machine_file.lock()
    try:
        machine_file.load()
    except Exception, ex:
        log.logger.error('Failed to load from %s, exception: %s', filename, str(ex))
        # machine_file.unlock()
        return None
    # machine_file.unlock()
    templates = machine_file.templates
    del machine_file
    return templates

def new_request(filename, templateId, machineCount,  prefix, acctTagValue):
    machine_file = MachineFile(filename)
    # machine_file.lock()
    try:
        machine_file.load()
        requestId = str(uuid.uuid1())
        names = []
        index = 1
        while len(names) < machineCount:
            name = 'machine-' + ('%03d' % index)
            if not name in machine_file.machines.keys():
                machine_file.machines[name] = Machine({'name': name,
                                                       'id': '',
                                                       'port': '',
                                                       'template': templateId,
                                                       'requestId': requestId,
                                                       'returnId': '',
                                                       'status': 'building',
                                                       'launchTime': 0,
                                                       'message': '',
                                                       'rcAccount': acctTagValue,
                                                       'privateIpAddr': '',
                                                       'publicIpAddr': ''})
                names.append(name)
            index = index + 1
        #import time; print 'locking'; time.sleep(10); print 'unlock'
        machine_file.save()
    except Exception, ex:
        Log.logger.error('Failed to load or save %s, exception: %s', filename, str(ex))
        # machine_file.unlock()
        return None, None
    # machine_file.unlock()
    del machine_file
    return requestId, names

def new_return_request(filename, machineNames):
    machine_file = MachineFile(filename)
    result = []
    # machine_file.lock()
    try:
        machine_file.load()
        requestId = str(uuid.uuid1())
        for name in machineNames:
            m = machine_file.machines.get(name, None)
            if m:
                m.returnId = requestId
                result.append(m)
        machine_file.save()
    except Exception, ex:
        Log.logger.error('Failed to load or save %s, exception: %s', filename, str(ex))
        # machine_file.unlock()
        raise
        return None, None
    # machine_file.unlock()
    del machine_file
    return requestId, result

def update_machines(filename, machineInfo):
    if not machineInfo: return
    machine_file = MachineFile(filename)
    # machine_file.lock()
    try:
        machine_file.load()
        for minfo in machineInfo:
            minfo = dict(minfo)
            mname = minfo['name']
            machine = machine_file.machines.get(mname, None)
            if machine:
                if minfo.has_key('rename'):
                    machine_file.machines[minfo['rename']] = machine
                    del machine_file.machines[mname]
                    minfo['name'] = minfo['rename']
                    del minfo['rename']
                # update machine info
                for attr in minfo.keys(): setattr(machine, attr, minfo.get(attr, ''))
            else:
                Log.logger.error('%s not found in machine file', mname)
        machine_file.save()
    except Exception, ex:
        Log.logger.error('Failed to update %s, exception: %s', filename, str(ex))
        # machine_file.unlock()
        return
    # machine_file.unlock()
    del machine_file

def get_machines(filename):
    machine_file = MachineFile(filename)
    # machine_file.lock()
    try:
        machine_file.load()
    except Exception, ex:
        log.logger.error('Failed to load from %s, exception: %s', filename, str(ex))
        # machine_file.unlock()
        return None
    # machine_file.unlock()
    machines = machine_file.machines
    del machine_file
    return machines


def get_request(filename, requestId):
    machine_file = MachineFile(filename)
    # machine_file.lock()
    try:
        machine_file.load()
    except Exception, ex:
        Log.logger.error('Failed to load from %s, exception: %s', filename, str(ex))
        # machine_file.unlock()
        return None
    # machine_file.unlock()
    request = machine_file.requests.get(requestId, None)
    if not request: request = machine_file.return_requests.get(requestId, None)
    del machine_file
    return request
