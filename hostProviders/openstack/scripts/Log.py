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

import logging.handlers
import logging

class Log:
   
    logger = None
    
    @staticmethod
    def init (filePath, logLevel): # constructor
        if Log.logger == None:

            Log.logger = logging.getLogger(filePath)
            handler = logging.handlers.RotatingFileHandler(filePath, maxBytes=1024*1000, backupCount=5)
            handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s:%(message)s'))
            Log.logger.addHandler(handler)
            
            if logLevel == "DEBUG":
                Log.logger.setLevel(logging.DEBUG)
            elif logLevel == "INFO":
                Log.logger.setLevel(logging.INFO)
            elif logLevel == "WARNING":
                Log.logger.setLevel(logging.WARNING)
            elif logLevel == "ERROR":
                Log.logger.setLevel(logging.ERROR)
            else:
                Log.logger.setLevel(logging.WARNING)
