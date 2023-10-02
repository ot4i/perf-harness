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
### start_responders_mq_jms.sh <#threads>
######################################################################################################
. ./config.sh
queues=10
stats_interval=4

perf_cmd="$JAVABIN $JAVA_HEAP_VALS JMSPerfHarness"
perf_tls_cmd="$JAVABIN $JAVA_HEAP_VALS $JAVA_TLS_VALS JMSPerfHarness"

export CLASSPATH=$MQ_JMS_CLASSPATH

### Responders (MQ-JMS non-persistent)
$perf_cmd -su -wt 10 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -rl 0 -id $2 -tc jms.r11.Responder -oq REPLY -iq REQUEST -cr -to 30 -db 1 -dx $queues -dn 1  -pc WebSphereMQ -jfq true -jh $QM_HOST -jp $LISTENER_PORT -jc SYSTEM.DEF.SVRCONN -jb $QM_NAME

### Responders (MQ-JMS non-persistent with connauth)
### -jm set to enable MQCSP authentication mode (including passwords > 12 characters). Remove for compatibility mode
#$perf_cmd -su -wt 10 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -rl 0 -id $2 -tc jms.r11.Responder -oq REPLY -iq REQUEST -cr -to 30 -db 1 -dx $queues -dn 1  -pc WebSphereMQ -jfq true -jh $QM_HOST -jp $LISTENER_PORT -jc SYSTEM.DEF.SVRCONN -jb $QM_NAME -us $QM_USERID -pw $QM_PASSWORD -jm true

### Responders (MQ-JMS, non-persistent with TLS)
#$perf_tls_cmd -su -wt 10 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -rl 0 -id $2 -tc jms.r11.Responder -oq REPLY -iq REQUEST -cr -to 30 -db 1 -dx $queues -dn 1  -pc WebSphereMQ -jfq true -jh $QM_HOST -jp $LISTENER_PORT -jc MQPERF.TLS -jb $QM_NAME -jl $CLIENT_CIPHER_SUITE

### Responders (MQ-JMS, persistent, transacted)
#$perf_cmd -su -wt 10 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -rl 0 -id $2 -tc jms.r11.Responder -pp -tx -oq REPLY -iq REQUEST -cr -to 30 -db 1 -dx $queues -dn 1  -pc WebSphereMQ -jfq true -jh $QM_HOST -jp $LISTENER_PORT -jc SYSTEM.DEF.SVRCONN -jb $QM_NAME

### Responders (MQ-JMS, persistent, non-transacted) - Atypical, included for comparison with QPID/AMQP whihc doesn't support transaction in MQ V9.3.3
#$perf_cmd -su -wt 10 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -rl 0 -id $2 -tc jms.r11.Responder -pp -oq REPLY -iq REQUEST -cr -to 30 -db 1 -dx $queues -dn 1  -pc WebSphereMQ -jfq true -jh $QM_HOST -jp $LISTENER_PORT -jc SYSTEM.DEF.SVRCONN -jb $QM_NAME

