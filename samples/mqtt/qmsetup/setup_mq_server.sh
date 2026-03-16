#!/bin/bash
########################################################## {COPYRIGHT-TOP} ###
# Copyright 2025 IBM Corporation
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the MIT License
# which accompanies this distribution, and is available at
# http://opensource.org/licenses/MIT
########################################################## {COPYRIGHT-END} ###

######################################################################################################
### Creates PERF0 queue manager with required MQXR / MQTT objects
### Usage:
### setup_mq_server.sh
######################################################################################################

. ./config.sh

crtmqm -md /var/mqm/qmgrs -u SYSTEM.DEAD.LETTER.QUEUE \
  -h 50000 -lf 16384 -lp 16 -lc $QM_NAME

strmqm $QM_NAME

# Base tuning + MQTT XR setup + listener
runmqsc $QM_NAME < ./mqtt.mqsc
