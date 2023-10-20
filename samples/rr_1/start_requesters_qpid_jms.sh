#!/bin/bash
########################################################## {COPYRIGHT-TOP} ###
# Copyright 2023 IBM Corporation
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the MIT License
# which accompanies this distribution, and is available at
# http://opensource.org/licenses/MIT
########################################################## {COPYRIGHT-END} ###

######################################################################################################
### Usage:
### start_requesters_qpid_jms.sh <#threads> 
######################################################################################################
. ./config.sh
messageSize=2048
queues=10
stats_interval=2

perf_cmd="$JAVABIN $JAVA_HEAP_VALS JMSPerfHarness"
perf_tls_cmd="$JAVABIN $JAVA_HEAP_VALS $JAVA_TLS_VALS JMSPerfHarness"

export CLASSPATH=$QPID_JMS_CLASSPATH

#Requesters (JMS-AMQP, non-persistent)
#$perf_cmd -su -wt 60 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -ms $messageSize -rl 0 -tc jms.r11.Requestor -iq REQUEST -oq REPLY -to 5 -mt text -db 1 -dx $queues -dn 1 -pc QPID -jh $QM_HOST -jp $AMQP_PORT

#Requesters (JMS-AMQP, non-persistent with connauth)
#$perf_cmd -su -wt 60 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -ms $messageSize -rl 0 -tc jms.r11.Requestor -iq REQUEST -oq REPLY -to 5 -mt text -db 1 -dx $queues -dn 1 -pc QPID -jh $QM_HOST -jp $AMQP_PORT -us $QM_USERID -pw $QM_PASSWORD

# Requesters (JMS-AMQP, non-persistent with TLS)
#$perf_tls_cmd -su -wt 60 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -ms $messageSize -rl 0 -tc jms.r11.Requestor -iq REQUEST -oq REPLY -to 5 -mt text -db 1 -dx $queues -dn 1 -pc QPID -jh $QM_HOST -jp $AMQP_PORT -jl $CLIENT_CIPHER_SUITE

#Requesters (JMS-AMQP, non-persistent, NoAck mode: -am 100)
$perf_cmd -su -wt 60 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -ms $messageSize -rl 0 -tc jms.r11.Requestor -iq REQUEST -oq REPLY -to 5 -mt text -db 1 -dx $queues -dn 1 -pc QPID -jh $QM_HOST -jp $AMQP_PORT -am 100

#Requesters (JMS-AMQP, persistent, non-transacted)
#$perf_cmd -su -wt 60 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -ms $messageSize -rl 0 -tc jms.r11.Requestor -pp -iq REQUEST -oq REPLY -to 5 -mt text -db 1 -dx $queues -dn 1 -pc QPID -jh $QM_HOST -jp $AMQP_PORT
