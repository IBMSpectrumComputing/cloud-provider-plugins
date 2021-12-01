#!/usr/bin/python

# Copyright IBM 2016 

# -*- coding: utf-8 -*-

import logging.handlers
import logging

class Log:
   
    logger = None
    
    @staticmethod
    def init (filePath): # constructor
        if Log.logger == None:

            Log.logger = logging.getLogger(filePath)
            handler = logging.handlers.RotatingFileHandler(filePath, maxBytes=1024*1000, backupCount=5)
            handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s:%(message)s'))
            Log.logger.addHandler(handler)              
            Log.logger.setLevel(logging.DEBUG)
    
