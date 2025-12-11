#!/bin/sh
########################################################## {COPYRIGHT-TOP} ###
# Copyright 2025 IBM Corporation
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the MIT License
# which accompanies this distribution, and is available at
# http://opensource.org/licenses/MIT
########################################################## {COPYRIGHT-END} ###

# Queue manager parameters
QM_NAME=PERF0
QM_HOST=qm_host_or_ip

# XR / MQTT listener port (MQXR plain-text)
XR_PORT=1883

# Standard MQ listener port (for MQI/JMS publishers/subscribers in Fan-In)
LISTENER_PORT=1420

# Connection authentication (used only if enabled in MQ)
QM_USERID=qm_connauth_userid
QM_PASSWORD=qm_connauth_password

# Java binary and heap settings
JAVABIN=/opt/mqm/java/jre64/jre/bin/java
JAVA_HEAP_VALS="-Xms768M -Xmx768M"

# MQTTPerfHarness classpath
# Paho MQTT client jar must be placed in:
#   PerfHarnessPrereqs/MQTT_Clients/
PAHO_JAR=./PerfHarnessPrereqs/MQTT_Clients/org.eclipse.paho.client.mqttv3-1.2.6.jar
MQTT_CLASSPATH="$PAHO_JAR:./mqttperfharness.jar"

# MQ CPH classpath (for MQI fan-in publishers/subscribers)
MQ_CPH_CLASSPATH="/opt/mqm/java/lib/com.ibm.mq.allclient.jar:./perfharness.jar"

# JMSPerfHarness classpath (if using JMS publishers in Fan-In)
MQ_JMS_CLASSPATH="/opt/mqm/java/lib/com.ibm.mq.allclient.jar:./perfharness.jar"

# Script helper values
STATS_INTERVAL=10
MESSAGE_SIZE=256
TOPIC_NAME=TOPIC
