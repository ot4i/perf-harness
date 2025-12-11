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
### Usage:
### start_fanout_publisher_xr.sh <#xr_publishers> <id>
######################################################################################################

. ./config.sh

stats_interval=$STATS_INTERVAL
message_size=$MESSAGE_SIZE
topic_name=$TOPIC_NAME

export CLASSPATH=$MQTT_CLASSPATH

### XR publishers (FanOut)
$JAVABIN $JAVA_HEAP_VALS MQTTPerfHarness -su \
  -nt $1 -ss $stats_interval -rl 0 -wp true -wc 50 \
  -wt 240 -wi 20 -rt 0.04 -id $2 \
  -qos 0 -ka 600 -cs true \
  -tc mqtt.Publisher -ms $message_size \
  -d $topic_name -db 1 -dx 1 \
  -iu tcp://$QM_HOST:$XR_PORT
