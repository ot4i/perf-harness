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
### start_fanout_subscriber_mqi.sh <#mqi_subscribers> <id>
######################################################################################################

. ./config.sh

stats_interval=$STATS_INTERVAL
message_size=$MESSAGE_SIZE
topic_name=$TOPIC_NAME

export CLASSPATH=$MQ_CPH_CLASSPATH

### MQI subscribers (FanOut)
perl runcph.pl cph -nt $1 -ms $message_size -cv false -vo 3 \
  -wt 240 -wi 20 -rl 0 -id $2 -tx -pp \
  -tc Subscriber -ss $stats_interval -to 30 \
  -d $topic_name -db 1 -dx 1 \
  -jp $LISTENER_PORT -jc SYSTEM.DEF.SVRCONN \
  -jb $QM_NAME -jt mqc -jh $QM_HOST
