# MQTTPerfHarness Tutorial

This tutorial provides a simple way to build, configure, and run the MQTTPerfHarness workload using IBM MQ’s MQTT/XR service. It follows the same style and structure used in other PerfHarness samples, with runnable examples, clear explanations, and minimal queue manager setup.

---

## 1. Build the MQTTPerfHarness Module

The MQTT module depends on the **Eclipse Paho MQTT v3 client**.  
Because this library cannot be redistributed, you must download it manually and place it in the expected directory.

### 1.1 Download the Paho Client

You may download **any version that supports MQTT 3.1.1**, for example:

- https://github.com/eclipse/paho.mqtt.java/releases  
- https://search.maven.org/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3  

The jar used during development was:

org.eclipse.paho.client.mqttv3-1.2.6.jar


### 1.2 Place the Paho jar

Copy the jar into:

perf-harness/PerfHarnessPrereqs/MQTT_Clients/


### 1.3 Build MQTTPerfHarness

From the root of the perf-harness project:

build with ANT perf-harness/PerfHarness/build_MQTT.xml

This produces:

PerfHarness/build/MqttPerfHarness.jar


You are now ready to run MQTT publisher/subscriber workloads.

---

## 2. Queue Manager Setup (PERF0)

This section describes the minimal MQ objects required for MQTT/XR and for running the example workloads.

Sample setup scripts are placed under:

samples/mqtt/qm_setup/


### 2.1 Review Configuration

Before running any setup scripts, update a `config.sh` with values such as:

- Queue manager name (PERF0)  
- Listener port  
- Hostname  
- XR port (1883)

### 2.2 Create the Queue Manager

A sample script might call:

crtmqm PERF0
strmqm PERF0


### 2.3 Apply MQSC Definitions

You can just run your setup script to apply the MQSC file.  
Below is a minimal MQSC example for MQTTPerfHarness:

ALTER QMGR CHLAUTH(DISABLED)
ALTER AUTHINFO(SYSTEM.DEFAULT.AUTHINFO.IDPWOS) AUTHTYPE(IDPWOS) CHCKCLNT(OPTIONAL)
REFRESH SECURITY(*) TYPE(CONNAUTH)

DEFINE LISTENER(L1) TRPTYPE(TCP) PORT(1420) CONTROL(QMGR)
START LISTENER(L1)

ALTER CHANNEL(SYSTEM.DEF.SVRCONN) CHLTYPE(SVRCONN) MCAUSER('mqperf') SHARECNV(1)

DEFINE QLOCAL('SYSTEM.MQTT.TRANSMIT.QUEUE') USAGE(XMITQ) MAXDEPTH(999999999)
ALTER QMGR DEFXMITQ('SYSTEM.MQTT.TRANSMIT.QUEUE')
ALTER QMGR MAXHANDS(999999999)
ALTER QMGR MAXUMSGS(100000)

DEFINE TOPIC('TOPIC') TOPICSTR('TOPIC') REPLACE


### 2.4 Start the MQXR Service and XR Channel

Your script should create and start the MQTT/XR service, as well as an XR channel on port **1883**.

***

MQ setup is now complete.

---

## 3. QoS Levels

MQTTPerfHarness supports all MQTT QoS levels:

| QoS | Delivery Guarantee                         |
|-----|---------------------------------------------|
| 0   | At-most-once (fire-and-forget)               |
| 1   | At-least-once (requires acknowledgement)     |
| 2   | Exactly-once (two-phase handshake)           |

You may choose the QoS level using:

-qos <0|1|2>

---

## 4. Test Scenarios

MQTTPerfHarness supports two primary use cases: FanOut and FanIn.

- **FanOut (XR → MQI/JMS):** many publishers send to one or more topics; MQ consumers subscribe to those topics.  

This tutorial uses **one topic** (`TOPIC`) as a minimal, reproducible example. To exercise multi-topic behaviour, increase the destination range using `-dx`.

### When to use

To evaluate XR → MQ ingest and end-to-end delivery under load.

To measure how many publishers (XR) and subscribers (MQI/JMS or XR) the system can sustain.

To test different delivery guarantees by changing QoS.

### How it works

Publishers (XR clients) publish messages to one or more topics. Use -d <TOPIC> to set the base topic name and -dx to control the number of topic destinations.

The number of publisher threads is controlled with -nt; each thread typically acts as a separate client connection.

PerfHarness/CPh behaviour:

By default, CPH publisher threads set the -tp flag to true. That means each publisher thread repeatedly publishes to the same topic.

Set -tp false if you want a small number of publisher threads to cycle across many topics (useful for fan-out tests with large -dx).

MQ/XR maps topic subscriptions to MQ destination queues (dynamic or static) — MQ delivers messages to those subscription queues, which are consumed by your MQI/JMS subscribers. There can be one or many MQI/JMS subscribers — it’s not limited to a single consumer.

To run a single-topic example set -dx 1. To exercise multi-topic behaviour increase -dx (and optionally adjust -db to change the base index).

-qos configures delivery semantics (0,1,2) and affects how XR and MQ persist/ack messages.

### Example commands

### XR Publisher
perl runjava.pl java  -Xms768M -Xmx768M MQTTPerfHarness \
  -nt 1000 -ss 10 -rl 0 -wp true -wc 50 -wt 240 -wi 20 \
  -rt 0.04 -id 5 -qos 2 -ka 600 -cs false \
  -tc mqtt.Publisher -ms 256 -d TOPIC -db 1 -dx 1 \
  -iu tcp://<QM_HOST>:<XR_PORT>

### MQI Subscriber (Example)
perl runcph.pl cph -nt 1 -ms 256 -cv false -vo 3 \
  -wt 240 -wi 20 -rl 0 -id 1 -tx -pp \
  -tc Subscriber -ss 10 -to 30 -d TOPIC -db 1 -dx 1 \
  -jp <LISTENER_PORT> -jc SYSTEM.DEF.SVRCONN -jb PERF0 -jt mqc -jh <QM_HOST>

### JMS Subscriber (Example)
perl runjava.pl java  -Xms768M -Xmx768M JMSPerfHarness \
  -wt 240 -wi 20 -nt 1 -ss 10 -rl 0 -wp true -wc 10 -id 1 \
  -tc jms.r11.Subscriber -db 1 -dx 1 -d TOPIC -cc 500 \
  -pc WebSphereMQ -jp <LISTENER_PORT> -jc SYSTEM.DEF.SVRCONN \
  -jb PERF0 -jt mqc -jh <QM_HOST>

- **FanIn (MQI/JMS → XR):** many MQI/JMS publishers send to one or more topics; XR subscribers consume from those topics.

### When to use

To evaluate MQ → XR delivery behaviour under increasing publish load.

To measure how many MQI/JMS publishers the system can sustain while XR subscribers consume the resulting MQTT messages.

To test the impact of QoS, topic count, and thread counts on XR subscriber throughput.

### How it works

MQI/JMS publisher clients put messages to one or more topics. Use -d <TOPIC> to set the base topic and -dx to configure the number of topic destinations.

The number of publishing threads is controlled with -nt. Each thread behaves as an independent publisher connection.

CPH behaviour:

By default, CPH publishers limit each thread to a single topic per thread.
With -tp true, each thread repeatedly publishes to one topic.
Set -tp false to make threads cycle across many topics, which is useful when -dx is large, and you want to simulate many publishing destinations with a smaller number of threads.

MQ delivers these topic publications into the associated XR subscription. The XR subscriber client (mqtt.Subscriber) receives messages across the configured topic range. You can run one or many XR subscribers — the example here uses a single subscriber.

To run a single-topic example, set -dx 1. To expand the fan-in behaviour, increase -dx.

QoS (-qos) determines delivery semantics expected by the XR subscriber as messages arrive via MQTT.

### Example commands

### MQI Publisher (Example)
perl runcph.pl cph -vo 3 -nt 5 -ss 10 -ms 256 \
  -wt 240 -wi 20 -rl 0 -id 1 -rt 650 \
  -tc Publisher -d TOPIC -db 1 -dx 100000 -dn 1 -tp false \
  -jp <LISTENER_PORT> -jc SYSTEM.DEF.SVRCONN \
  -jb PERF0 -jt mqc -jh <QM_HOST>

### JMS Publisher (Example)
perl runjava.pl java  -Xms768M -Xmx768M JMSPerfHarness \
  -wt 240 -wi 20 -nt 5 -ss 10 -ms 256 -rl 0 \
  -wp true -wc 10 -rt 650 -id 1 \
  -tc jms.r11.Publisher -d TOPIC -mt text \
  -db 1 -dx 100000 -dn 1 \
  -pc WebSphereMQ -jp <LISTENER_PORT> -jc SYSTEM.DEF.SVRCONN \
  -jb PERF0 -jt mqc -ja 100 -jh <QM_HOST>

### XR Subscriber (Example)
perl runjava.pl java -Xms768M -Xmx768M MQTTPerfHarness \
  -nt 1 -ss 10 -rl 0 -wp true -wc 50 -wt 240 -wi 20 \
  -id 1 -qos 0 -ka 600 -cs true \
  -tc mqtt.Subscriber -d TOPIC -db 1 -dx 100000 -dn 1 \
  -iu tcp://<QM_HOST>:1883

  ---

## 6. Running the Test

1. Start the MQ subscriber (FanOut) or MQTT subscriber (FanIn).  
2. Start publishers (MQXR or MQI, depending on scenario).  
3. Allow the steady-state period to stabilize.

---

## 7. Modifying Test Parameters

Key MQTTPerfHarness parameters:

| Parameter | Meaning |
|----------|---------|
| `-nt` | Number of client threads |
| `-ms` | Message size bytes |
| `-dx` | Number of topics (set to 1 here) |
| `-qos` | MQTT QoS level 0/1/2 |
| `-iu` | XR/MQTT connection URL |
| `-wt` | Test duration |
| `-wi` | Warm-up time |
| `-sc` | Stats class (e.g., `BasicStats`) |

---

