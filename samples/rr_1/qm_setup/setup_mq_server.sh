#!/bin/sh
########################################################## {COPYRIGHT-TOP} ###
# Copyright 2023 IBM Corporation
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the MIT License
# which accompanies this distribution, and is available at
# http://opensource.org/licenses/MIT
########################################################## {COPYRIGHT-END} ###

###########################################################################
### Sample MQ Queue Manager Setup for MQ-CPH Test (Paul Harris: Sept 2023)
###########################################################################
. ../config.sh

MQ_DEFAULT_INSTALLATION_PATH=/opt/mqm
MQ_INSTALLATION_PATH=${MQ_INSTALLATION_PATH:=$MQ_DEFAULT_INSTALLATION_PATH}
. $MQ_INSTALLATION_PATH/bin/setmqenv

#Override the following two variables for non-default file locations
MQ_LOG_DIR=$MQ_DATA_PATH/log
MQ_DATA_DIRECTORY=$MQ_DATA_PATH/qmgrs

if crtmqm -u SYSTEM.DEAD.LETTER.QUEUE -h 50000 -lc -ld $MQ_LOG_DIR -md $MQ_DATA_DIRECTORY -p $LISTENER_PORT -lf 16384 -lp 16 $QM_NAME
then
	echo "Modifying $MQ_DATA_DIRECTORY/$QM_NAME/qm.ini"
    perl ./modifyQmIni.pl $MQ_DATA_DIRECTORY/$QM_NAME/qm.ini ./qm_update.ini

    #strmqm -c $QM_NAME
    strmqm $QM_NAME
    runmqsc $QM_NAME < "./mqsc/base.mqsc"
    runmqsc $QM_NAME < "./mqsc/rr.mqsc"

    echo "DEFINE CHANNEL (MQPERF.TLS) CHLTYPE(SVRCONN) TRPTYPE(TCP) SSLCIPH($MQ_CIPHER_SPEC) SSLCAUTH(OPTIONAL) REPLACE" | runmqsc $QM_NAME
    echo "REFRESH SECURITY TYPE(SSL)" | runmqsc $QM_NAME

else
	echo "Cannot create queue manager $QM_NAME"
fi
