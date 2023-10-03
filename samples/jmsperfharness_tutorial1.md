# Running a JMS Performance Test with IBM MQ using JMSPerfHarness

This tutorial demonstrates how to setup and run a simple JMS performance test using JMSPerfHarness, via the 2 supplied JMS provider modules in the tool:

* **WebSphereMQ**  
A JMS provider utilising the IBM MQ classes for JMS, which communicate with IBM MQ using a proprietary protocol.
* **QPID**  
A JMS provider utilising the Apache Qpid JMS classes, which communicate with IBM MQ using AMQP. 

The QPID provider is a new module, added to the tool in August 2023.

## What is JMSPerfHarness?
JMSPerfHarness (a component of PerfHarness) is a JMS client scaling, performance test tool made available on GitHub at https://github.com/ot4i/perf-harness).  It provides an extensive set of performance messaging functionality, as well as many other features such as throttled operation (a fixed rate and/or number of messages), multiple destinations and live performance reporting.  It is one of the many tools used by the IBM MQ performance team at Hursley, for tests ranging from a single client to more than 10,000 clients.

## What Types of Application Can be Simulated?
Each JMSPerfHarness process can start 1-n worker threads of the same type (module). The types of module that a worker can specify are listed below:
* **Sender :** Sends messages to a named queue destination.
* **Receiver :** Receives messages from a named queue destination. This can be used in conjunction with the Sender module.
* **PutGet :** Sends a message to queue then retrieves the same message (using CorrelationId).
* **Requestor :** Sends a message to a queue then waits for a corresponding reply (using CorrelationId) on a second queue.  
* **Responder :** Waits for a message on a queue then replies to it on another queue. This can be used in conjunction with the Requestor module.
* **Publisher :** Sends messages to a named topic destination.
* **Subscriber :** Subscribes and receives messages from a named topic. This can be used in conjunction with the Publisher module.

## How do I Use the Tool?
JMSPerfHarness is a command line tool which can be configured in a variety of ways to run multiple JMS application worker threads, specifying persistence, transactionality, pacing etc. The simplest way to understand its use is by means of an example. Consider the following simple message flows between two JMS applications, where 'Requester' is a client bound application on a remote machine, and 'Responder' is a client bound application on a second remote machine.

**(Worker Module)** --> JMS Operation

1. **Requester** --> ‘send’ to queue 'REQUEST1' (on remote host)
2. **Responder** --> ‘receive’ from queue 'REQUEST1' (on remote host)
3. **Responder** --> ‘send’ to queue 'REPLY1', with the correlid of the message got from queue 'Request1' (on remote host)
4. **Requester** --> ‘receive’ (by Correlid) from queue 'REPLY1' (on remote host)

This is a self-regulating application, as for each requester thread, the next JMS send will not be executed, until the previous JMS receive has been successful. 

The maximum number of messages being processed will be limited by the round-trip time but can be increased overall by adding more requester and responder applications (often iteratively, in a long test).

We can use JMSPerfHarness to run such a 'requester-responder' scenario, simulating multiple requesters, and multiple responders, spreading the workload across multiple pairs of request/reply queues. Figure 1, below, shows a scenario with JMS requesters and responders across 10 pairs of request/reply queues.

![image](https://github.com/ot4i/perf-harness/assets/20699408/a2aebec2-9849-4f32-9199-91875dccfae1)
#### Figure 1 : Requester/Responder Application across 10 Pairs of Request/Reply Queues (not all shown).
JMSPerfHarness can be started with any number of requester or responder threads, and can be specified to use a single pair of request/reply queues, or a multiple set of queues (i.e. ten pairs of queues, as in the diagram above). Typically, requesters and responders are configured to access the queues in a round-robin fashion so if we were to specify 20 requesters, across ten sets of queues, we would get the following:

Requester1 & requester11: send to 'REQUEST1' and receive from 'REPLY1'  
Requester2 & requester12: send to 'REQUEST2' and receive from 'REPLY2'  
Requester10 & requester20: send to 'REQUEST10' and receive from 'REPLY10'

Once a requester, or responder starts using a particular request/reply pair of queues, it stays on that queue pair. It is for this reason, that if we specify a queue range of n, then we start a multiple of n requesters (and responders) to keep the load balanced across the queues, unless you are trying to model an unbalanced workload, of course!

In diagram 1 above, requesters 1,2 and 20 are shown, so you can see that requester 20 goes back to using queues request10 & reply10, as this is the range being used, in the example shown. Any number of further requesters and/or responders can be specified however, and the range of queue pairs used can be larger.

A typical scaling scenario for requester/responder  tests is to start all the responders first (e.g. start 100 responders), then run tests with increasing numbers of requesters (e.g. 1,2,20,25 & 50)

## Let’s go!   -  Running the JMS Requester/responder Scenario using JMSPerfHarness
The topology will be like that shown in diagram 1, and we will be specifying the following main parameters:
* Queue pair range  = 10 (queues REQUEST1-REQUEST10 and REPLY1-REPLY10)
* Number of responders = 20
* Number of requesters = 10
* Message Size: 2KiB
* Quality of Service: Non-persistent.

### Step 1 - Download and build JMSPerfHarness
PerfHarness (of which JMSPerfHarness is a component), can be downloaded from GitHub here: https://github.com/ot4i/perf-harness

Follow the instructions on the ReadMe page in GitHub to import the code into Eclipse and get any pre-reqs you need. 

You should have the following jars in your Eclipse buildpath for the JMSPerfHarness project:
* **jms.jar**  
Required by all JMS provider modules and included in the IBM MQ JMS and Java redistributable client*
* **com.ibm.mq.allclient.jar**  
Required by the WebSphereMQ JMS provider module and included in the IBM MQ JMS and  Java redistributable client*
* **qpid-jms-client-xxx-jar**  
Required by the QPID JMS provider module and included in the Apache Qpid JMS library† (where xxx is the version of the Qpid client you’re using).

\* The first 2 jars are included in the IBM MQ JMS and Java redistributable client, which can be obtained from: https://ibm.biz/mq93clients Select IBM-MQC-Redist-Java from the list of downloads presented.  
†The Qpid jar is contained in the Apache Qpid client download, which can be obtained here: https://qpid.apache.org/components/jms/index.html
The Qpid provider in JMSPerfHarness is based on javax.jms. Qpid JMS 0.61.0 is the latest version to support that.

If you only intend to use the WebSphereMQ JMS provider, you can omit the Qpid Jar and ignore the source errors in QPID.java. The project will still build ok. 

![image](https://github.com/ot4i/perf-harness/assets/20699408/dcfd7f27-5e4e-40bb-96e6-16fd57009e4a)
#### Figure 2: JMSPerfHarness project in Eclipse with IBM MQ and Apache Qpid jar dependencies added.

Figure 2 shows the Java Build Path libraries for the JMSPerfHarness project with IBM MQ and Apache Qpid client jars added. As you can see, we’ve put the jars in the IBM_WMQ_9 and QPID_JMS folders that are pre-defined in the PerfHarness Eclipse workspace.

Now you’re ready to build the PerfHarness project. There are several Ant scripts to help you in the PerfHarness. Right-click ‘build-Release.xml’ and select ‘Run As -> 1 Ant Build’ to build the full release (see Figure 3 below).

![image](https://github.com/ot4i/perf-harness/assets/20699408/3640fb30-c801-44d0-87d5-fc2368916dc4)
#### Figure 3 Building PerfHarness
Now PerfHarness has been built, a performance test can be run, using the perfharness.jar generated.

### Step 2: Setup the queue manager
Sample scripts (for Linux) to set-up the queue manager used in this test scenario (rr_1) and run  PerfHarness clients are in the [samples/rr_1](./rr_1) directory

Review the [config.sh](./rr_1/config.sh) script to setup various parameters used in the test, including the queue manager name, listener port, hostname etc. Also included in this configuration files are options to specify the cipher used in a TLS test, along with the java keystore location and password. 

Once you’re happy with the contents of config.sh then go into the [samples/rr_1/qm_setup](./rr_1/qm_setup) directory and run the “setup_mq_server.sh” script to create your queue manager (named ‘PERF0’ by default) with the necessary objects required for a JMSPerfHarness requester/responder test. The mqsc files used will create 20 pairs of request/reply queues, you can edit these if you want more. 
<dl>
<dt>TLS Configuration</dt>
<dd>If you run a TLS test it is assumed the keystores for the queue manager have already been setup correctly. correctly. A svrconn channel will be created with the SSLCIPH attribute set to the cipherspec value specified in <a href="./rr_1/config.sh">config.sh</a>. The location of the  Java keystore used by the client will need to be specified in the configuration file, along with the password. 
<p><p>If you want to run a TLS  test with QPID JMS then the AMQP channel can be enabled (or subsequently disabled) for TLS encryption by running the ‘add_ampq_tls.sh’ or  ‘remove_ampq_tls.sh’ scripts in the <a href="./rr_1/qm_setup">samples/rr_1 directory/qm_setup</a> directory. </dd>
</dd>

The [setup_mq_server.sh](./rr_1/qm_setup/setup_mq_server.sh) script has a dependency on Perl, to merge the settings in the [samples/rr_1/qm_setup/qm_update.ini](./rr_1/qm_setup/qm_update.ini)  (containing some recommended tunings), with the qm.ini of the newly created queue manager.  

The version this was tested on is Perl v5.16.3. If you don’t have Perl on your system, then you can comment out the perl line in qm_setup.sh and merge the files yourself (.e. adding or modifying existing values in the *Channels* and *Log* stanzas and adding the *TuningParameters* stanza and value).

### Step 3: Run the JMSPerfHarness test.

Now the queue manager has been created, you can execute the test, there are four scripts included in the [samples/rr_1](./rr_1)  directory.
* **[start_responders_mq_jms.sh](./rr_1/start_responders_mq_jms.sh)**  
Used to start JMS responder threads, using the IBM MQ JMS Provider
* **[start_requesters_mq_jms.sh](./rr_1/start_requesters_mq_jms.sh)**  
Used to start JMS requester threads, using the IBM MQ JMS Provider
* **[start_responders_qpid_jms.sh](./rr_1/start_responders_qpid_jms.sh)**  
Used to start JMS responder threads, using the Apache Qpid JMS Provider
* **[start_requesters_qpid_jms.sh](./rr_1/start_requesters_qpid_jms.sh)**  
Used to start JMS responder threads, suing the Apache Qpid Provider

The scripts have the following parameters that can be changed:
	
* **messageSize	(Requester scripts only.)**  
The size of the messages to be sent. Responders will reply with a message of the same size delivered to the request queue in these examples.  
* **queues**  
Maximum number of queue pairs to be used. If the number of client threads is greater than the number of  maximum queue pairs specified then the clients will spread across the queue pairs. E.g. if 10 queues are specified and 20 threads are started then each queue will have two client threads putting and getting off the queues.   
* **stats_interval**  
Interval (in seconds) between statistics messages written to stdout.

Each script contains a number of examples you can run, dependant on the JMS provider you’re using, as follows.

<!-- In the table below &#x2714 = tick mark, &#x2716; = check mark -->
|Test	|IBM MQ JMS Provider	|Apache Qpid JMS Provider|
|:---|:---:|:---:|
|Non-persistent|&#x2714;|&#x2714;|
|Non-persistent with connection authentication.|&#x2714;|&#x2714;|
|Non-persistent with TLS|&#x2714;|&#x2714;|
|Non-persistent with Qpid No Acknowledge mode.|&#x2716;|&#x2714;|
|Persistent, transacted.|&#x2714;|&#x2716;[^1]|
|Persistent, non-transacted.|&#x2714;[^2]|&#x2714;|

[^1]:As of MQ V9.3.3, the MQ AMQP service does not support transactions. See: https://www.ibm.com/docs/en/ibm-mq/9.3?topic=mq-amqp-clients-communicating-over
[^2]:This is included for comparison with persistent messaging using the Qpid JMS provider. 

Simply un-comment the test you want to run then execute it (starting with the requesters). Usually you will run the requesters and responders in the same mode (i.e., if you’re running non-persistent requesters, you’ll set the responders to be the same. 

The scenarios listed in the tables above are simply examples. Try some, look at the parameters set and then you can use these as a basis for running your own tests with the help of the MQ and PerfHarness documentation (e.g. running a persistent TLS variant).

<dl>
<dt>TLS Client Configuration</dt>
<dd>If you run a TLS test you must set the following environment variables in <a href="./rr_1 directory/config.sh">samples/rr_1 directory/config.sh</a>
<dl><dt>CLIENT_CIPHER_SUITE</dt><dd>Ciphersuite used by the client†</dd> 
<dt>CLIENT_TRUSTSTORE</dt><dd>Location of a Java keystore file (*.jks)†† containing the certificate of the queue manager.</dd>
<dt>CLIENT_TRUSTSTORE_PASSWORD<dt><dd>Password to access the Java keystore file.</dd>
</dl>
† To match the SSLCIPH attribute for the svrconn or amqp channel set via the MQ_CIPHER_SPEC variable in <a href="./rr_1 directory/config.sh">config.sh</a>. See https://www.ibm.com/docs/en/ibm-mq/9.3?topic=jms-tls-cipherspecs-ciphersuites-in-mq-classes
<p></p>†† e.g. to create a new jks file and import the certificate labelled ibmwebspheremqperf0 from the certificate file perf0.cert
<ol><li>runmqckm -keydb -create -db  jms.jks -type jks -pw &lt;your truststore password&gt;</li>
<li>runmqckm -cert -add -db jms.jks -pw &lt;your truststore password&gt; -label ibmwebspheremqperf0 -file perf0.cert</li></ol>
</dd></dl>

Taking the first test (non-persistent messaging) as an example, the following line is uncommented in [start_responders_mq_jms.sh](./rr_1/start_responders_mq_jms.sh) :
```
### Responders (MQ-JMS non-persistent) 
$perf_cmd -su -wt 10 -wi 0 -nt $1 -ss $stats_interval -sc BasicStats -rl 0 -id $2 -tc jms.r11.Responder -oq REPLY -iq REQUEST -cr -to 30 -db 1 -dx $queues -dn 1  -pc WebSphereMQ -jfq true -jh $QM_HOST -jp $LISTENER_PORT -jc SYSTEM.DEF.SVRCONN -jb $QM_NAME
```

The equivalent line is uncommented in [start_requesters_mq_jms.sh](./rr_1/start_requesters_mq_jms.sh) and we’re ready to go.
First start the responders. All scripts take a single command line parameter denoting the number of threads to start.  
e.g. to start 20 responders:

```
./start_responders_mq_jms.sh 20
ControlThread1: START                                   
Responder1: START                                       
Responder2: START                                       
Responder3: START                                       
Responder4: START                                       
Responder5: START                                       
Responder6: START                                       
Responder7: START                                       
Responder8: START                                       
Responder9: START                                       
Responder10: START                                      
Responder11: START                                      
Responder12: START                                      
Responder13: START                                      
Responder14: START                                      
Responder15: START                                      
Responder16: START                                      
Responder16: START
rate=0.00,total messages=0,Snapshot period=4,threads=15
Responder17: START
Responder18: START
Responder19: START
Responder20: START
rate=0.00,total messages=0,Snapshot period=4,threads=20
rate=0.00,total messages=0,Snapshot period=4,threads=20
rate=0.00,total messages=0,Snapshot period=4,threads=20
……
```

The  responder threads start up and rate messages will be output (every 4 seconds here as set in the script). The rate will  report 0, until the requesters are started. 

Now start  the requesters. 
e.g. to start 10 requesters:
```
./start_requesters_mq_jms.sh 10
ControlThread1: START
Requestor1: START
Requestor2: START
Requestor3: START
Requestor4: START
Requestor5: START
Requestor6: START
rate=2631.00,total messages=5262,Snapshot period=2,threads=5
Requestor7: START
Requestor8: START
Requestor9: START
Requestor10: START
rate=15551.50,total messages=31103,Snapshot period=2,threads=10
rate=16516.50,total messages=33033,Snapshot period=2,threads=10
rate=16648.00,total messages=33296,Snapshot period=2,threads=10
……
```

In the sample above, the statistics interval for the requesters was set to 2. The rate messages show that the test running at about 16,600 iterations/sec, i.e. ~33,200 messages for every reporting interval. Your results will depend on a number of factors, including the specifications of the hosts and the network links (and  the filesystem hosting the MQ recovery log, for persistent messaging). 

Once the requesters’ rates have settled you’ll see the responders’ rates match. You may choose not to report responder rates at all (set **stats_interval** to 0 in [start_responders_mq_jms.sh](./rr_1/start_responders_mq_jms.sh)), as the rate reported by the requesters indicates the test is running ok (requesters won’t run unless the responders are replying).

JMS tests will settle after a short time as JIT compilation in the JVM optimises the code. As coded, the tests will run as fast as they can (hint: to set threads to run at a rate simulating your application, look at the -rt parameter for the WorkerThread class in the PerfHarness documentation). 

You can stop a JMSPerfHarness process by issuing SIGINT (ctrl-c or kill -2). When the JMSPerfHarness control thread detects the signal, it will stop all the worker threads and issue a statistics summary as in the example here:

```
……
rate=16836.00,total messages=33672,Snapshot period=2,threads=10
^CRequestor6: STOP
Requestor10: STOP
Requestor2: STOP
Requestor4: STOP
Requestor3: STOP
Requestor8: STOP
Requestor5: STOP
Requestor7: STOP
Requestor1: STOP
Requestor9: STOP
totalIterations=1302393,avgDuration=77.61,totalRate=16782.39
ControlThread1: STOP
```

<dl>
<dt>totalIterations</dt><dd>Total number of Put/Get iterations executed (sum of all threads).</dd>	
<dt>avgDuration</dt><dd>Average time a thread was running (wall clock time).</dd>
<dt>totalRate</dt><dd>Average iterations/per second for the process (i.e. total of all average worker thread rates).</dd>
</dt></dl>

By definition, **totalRate ~= totalIterations / avgDuration**  (although avgDuration will include some time not spent messaging).

This tutorial should have enabled you to experiment with a few different variations of the requester/responder workload, using JMSPerfHarness. The detail of setting the tool’s command line parameters is largely hidden, with the essential ones being set by environment variables. The tables below show JMSPerfHarness command line parameters set from script variables in these samples:

JMSPerfHarness command line parameters set via variables in the test execution scripts[^3]

|JMSPerfHarness Command Line Parameter	|Environment Variable	|Description|
|:--|:--|:--|
|-ms|messageSize	|Message size in bytes.|
|-dx	|queues	|Multi-destination numeric maximum. Controls the number of queues used.|
|-ss	|stats_interval	|Statistics reporting period (seconds).|
|-jb	|QM_NAME	Queue manager to connect to (not used by Qpid JMS tests).|
|-jh	|QM_HOST	Host to connect to.|
|-jp	|LISTENER_PORT	|TCP listener port used by MQ JMS tests.|
|-jp	|AMQP_PORT	|AMQP channel port used by Qpid JMS tests.|
|-us	|QM_USERID	|Userid used by connection authentication tests.|
|-pw	|QM_PASSWORD	|Password used by connection authentication tests.|
|-jl	|CLIENT_CIPHER_SUITE	|Ciphersuite used by the client[^4] in the TLS tests.|

[^3]:These are start_requesters_mq_jms.sh,  start_requesters_qpid_jms.sh,  start_responders_mq_jms.sh  and start_responders_qpid_jms.sh
[^4]:To match the SSLCIPH attribute for the svrconn or amqp channel set via the MQ_CIPHER_SPEC variable in config.sh. See https://www.ibm.com/docs/en/ibm-mq/9.3?topic=jms-tls-cipherspecs-ciphersuites-in-mq-classes

To change the way JMSPerfHarness tests behave further you’ll want a more detailed understanding of the command line parameters. These are listed out, per module in the propertiesSummary.txt file, and the JMSPerfHarness manual provides further information. Both these documents can be found here: https://github.com/ot4i/perf-harness/tree/main/PerfHarness/doc .

A Docker container version of JMSPerfHarness is available here, with some pre-defined MQ JMS tests: https://github.com/ibm-messaging/jmstestp
