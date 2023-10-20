#!/bin/sh
########################################################## {COPYRIGHT-TOP} ###
# Copyright 2023 IBM Corporation
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the MIT License
# which accompanies this distribution, and is available at
# http://opensource.org/licenses/MIT
########################################################## {COPYRIGHT-END} ###
#Queue manager parms
QM_NAME=PERF0
QM_HOST=qm_host_or_ip
LISTENER_PORT=1414
AMQP_PORT=5672

#For connauth
QM_USERID=qm_connauth_userid
QM_PASSWORD=qm_connauth_password


#For TLS - See https://www.ibm.com/docs/en/ibm-mq/9.3?topic=jms-tls-cipherspecs-ciphersuites-in-mq-classes
MQ_CIPHER_SPEC=TLS_RSA_WITH_AES_256_CBC_SHA256
CLIENT_CIPHER_SUITE=SSL_RSA_WITH_AES_256_CBC_SHA256
#
CLIENT_TRUSTSTORE=/client_keystore_dir/jms.jks
CLIENT_TRUSTSTORE_PASSWORD=cts_password
JAVA_TLS_VALS="-Djavax.net.ssl.trustStorePassword=$CLIENT_TRUSTSTORE_PASSWORD -Djavax.net.ssl.trustStore=$CLIENT_TRUSTSTORE"

JAVABIN=/opt/mqm/java/jre64/jre/bin/java
JAVA_HEAP_VALS="-Xms768M -Xmx768M -Xmn600M"

#For MQ JMS Client
MQ_JMS_CLASSPATH=/opt/mqm/java/lib/com.ibm.mq.allclient.jar:./perfharness.jar

#For QPID JMS Client (extends previous classpath)
QPID_DIR=/qpid_downloads/apache-qpid-jms-0.61.0
QPID_JMS_CLASSPATH=$QPID_DIR/lib/*:./perfharness.jar
