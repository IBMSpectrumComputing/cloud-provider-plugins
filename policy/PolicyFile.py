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

import json
import os
import fcntl
import shutil
import pdb
from Log import Log

class Request:
    def __init__(self, data ):
         for k in data:
            setattr(self, k, data[k])

class PolicyFile:
    def __init__(self, filename):
        self.filename = filename
        self.fp = None
        self.lockname = self.filename + '.lock'
        self.lockfp = None
        self.requests = {}

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
        self.requests = {}
        if not os.path.exists(self.filename):
            Log.logger.info('Creating a new policy data file: %s', self.filename)
            self.fp = file(self.filename, 'w+')
            return
        elif os.path.getsize(self.filename) == 0: return
        else: self.fp = file(self.filename, 'r+')
        self.fp.seek(0)
        request_data = json.loads(self.fp.read())
        if request_data.has_key('requests') and type(request_data['requests']) is list:
            for r in request_data['requests']:
                r = Request(r)
                requestId = r.providerName + '-' + r.templateName + '-' + r.rcAccount
                self.requests[requestId] = r


    def save(self):
        request_list = [r.__dict__ for r in self.requests.values()]
        data = {'requests': request_list}
        tmp_file = file(self.filename + '.tmp', 'w')
        fdata = json.dump(data, tmp_file, indent = 2)
        tmp_file.flush()
        shutil.move(self.filename + '.tmp', self.filename)

def new_request(filename, providerName,templateName, rcAccount,requestTime, lastRequestUpdateTime, lastStepIncrementTime, requested,currentRequest, targetAlloc, allocated, reclaimed):
    policy_file = PolicyFile(filename)
    try:
        policy_file.load()
        requestId = providerName + '-' + templateName + '-' + rcAccount
        policy_file.requests[requestId] = Request({'templateName': templateName,
                                                  'providerName': providerName,
                                                       'rcAccount': rcAccount,
                                                       'requestTime': requestTime,
                                                       'stepWindowStartTime': lastRequestUpdateTime,
                                                       'stepWindowEndTime': lastStepIncrementTime,
                                                       'requested': requested,
                                                       'requestedInCurrStepWindow' : currentRequest,
                                                       'targetAlloc': targetAlloc,
                                                       'allocated': allocated,
                                                       'reclaimed' : reclaimed})
        policy_file.save()
    except Exception as ex:
        Log.logger.error('Failed to load or save %s, exception: %s', filename, str(ex))
        return None
    del policy_file
    return requestId

def update_request(filename,reqId, requestInfo):
    if not requestInfo: return
    policy_file = PolicyFile(filename)
    try:
        policy_file.load()
        for rinfo in requestInfo:
            rinfo = dict(rinfo)
            request = policy_file.requests.get(reqId, None)
            if request:
                del policy_file.requests[reqId]
                policy_file.requests[reqId] = request
                # update request info
                for attr in rinfo.keys(): setattr(request, attr, rinfo.get(attr, ''))
            else:
                Log.logger.error('%s not found in policy file', reqId)
        policy_file.save()
    except Exception as ex:
        Log.logger.error('Failed to update %s, exception: %s', filename, str(ex))
        return
    del policy_file



def get_request(filename,requestId):
    policy_file = PolicyFile(filename)
    try:
        policy_file.load()
    except Exception as ex:
        Log.logger.error('Failed to load from %s, exception: %s', filename, str(ex))
        return None
    request = policy_file.requests.get(requestId, None)
    del policy_file
    return request
            
                
