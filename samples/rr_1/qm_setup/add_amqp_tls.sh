#!/bin/sh
########################################################## {COPYRIGHT-TOP} ###
# Copyright 2023 IBM Corporation
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the MIT License
# which accompanies this distribution, and is available at
# http://opensource.org/licenses/MIT
########################################################## {COPYRIGHT-END} ###

. ../config.sh

echo "stop SERVICE(SYSTEM.AMQP.SERVICE)" | runmqsc $QM_NAME
echo "alter CHANNEL(SYSTEM.DEF.AMQP) CHLTYPE(AMQP) SSLCIPH($MQ_CIPHER_SPEC) SSLCAUTH(OPTIONAL)" | runmqsc $QM_NAME
echo "ALTER QMGR SSLFIPS(NO) SSLRKEYC(0)" | runmqsc $QM_NAME
echo  "REFRESH SECURITY" | runmqsc $QM_NAME
sleep 3
echo "start SERVICE(SYSTEM.AMQP.SERVICE)" | runmqsc $QM_NAME
