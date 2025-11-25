# Perfharness
Perfharness is a flexible and modular Java package for performance testing of HTTP, JMS, MQ and other transport scenarios. It provides a complete set of transport functionality, as well as many other features such as throttled operation (a fixed rate and/or number of messages), multiple destinations, live performance reporting, JNDI. It is one of the many tools used by performance teams for IBM MQ and IBM Integration Bus in order to conduct tests ranging from a single client to more than 10,000 clients. Customers also use the tool to test their own systems and validate they perform accordingly.

## Importing Perfharness into Eclipse.

The top level folders in the repository are Eclipse projects. 
* Download the repository zip file and extract into a temporary directory.  
* Import the projects using the Import Wizard selecting "Existing Projects into Workspace". 
* Select all of the projects and make sure "Copy projects to workspace" is ticked when importing.

This should result in you having a workspace with the following projects:

![Workspace](images/PerfharnessWorkspace.png?raw=true "Workspace")

## Importing Prereqs into Eclipse

Building Perfharness in Eclipse requires some extra dependancies importing to the workspace. Depending on the modules you would like to build please import the following files into the PerfHarnessPrereqs project, these files are either available on the web from Apache or from the respective product installations. JMSPerfharness requires MQ V8 (or above), JakartaJMSPerfHarness requires MQ V9.3 (or above), MQJavaPerfHarness requires MQ V7 (or above) and the Perfharness and HTTPPerfharness modules have no prereqs.

* IBM_WMQ_7: 
    * com.ibm.mq.commonservices.jar
    * com.ibm.mq.headers.jar
    * com.ibm.mq.jar
    * com.ibm.mq.jmqi.jar
    * com.ibm.mq.pcf.jar
    * com.ibm.mqjms.jar
    * com.ibm.msg.client.jms.internal.jar
    * com.ibm.msg.client.jms.jar
    * connector.jar
    * fscontext.jar
    * jms.jar
    * jndi.jar
    * jta.jar
    * ldap.jar
    * providerutil.jar
* IBM_WMQ_8: 
    * com.ibm.mq.headers.jar
    * com.ibm.mq.jar
    * com.ibm.mq.jqmi.jar
    * com.ibm.mqjms.jar
    *  com.ibm.mq.pcf.jar
    * jms.jar
* IBM_WMQ_9: 
    * jms.jar
    * com.ibm.mq.allclient.jar
    * jakarta.jms-api.jar (only if building JakartaJMSPerfHarness)
    * com.ibm.mq.jakarta.client.jar (only if building JakartaJMSPerfHarness)
 * MQTT_Clients:
    * org.eclipse.paho.client.mqttv3-1.2.6.jar (only if building MQTT)

See the MQ knowledge center for details of the IBM Classes for Java. E.g. for MQ V9: [Developing JMS and Java applications](https://www.ibm.com/support/knowledgecenter/SSFKSJ_9.0.0/com.ibm.mq.dev.doc/q118320_.html)


## AMQP Channel Module Pre-reqs

This module has now been removed from PerfHarness as its dependent mq-light module is no longer available. To run PerfHarness using the AMQP protocol, please see the newly added QPID provider module.

## JakartaJMS Module

This module is identical to the JMS Module, but has been configured to use the Jakarta JMS interfaces found in MQ from V9.3. Building this module on its own will produce a jakartajmsperfharness.jar. It will build against the jakarta.jms-api.jar and com.ibm.mq.jakarta.client.jar from the PerfHarnessPreReqs module. Your application can still use JMS 1.1 or JMS 2.0 API, but an application can use only one of the Jakarta interfaces or the javax JMS interfaces. Ensure you set the correct environment paths when running the application to find the appropriate libraries. MQ provides options to crtmqenv and setmqenv (-j 3.0) to assist with this.

## MQTT Module

The MQTT module relies on the Eclipse Paho MQTT v3 client for all MQTT operations. Since this dependency cannot be distributed, users must download the Paho client jar and place it in PerfHarnessPreReqs/MQTT_Clients before building MQTTPerfHarness. Building this module will produce a mqttperfharness.jar, which allows running PerfHarness workloads using MQTT publishers, subscribers, and Pub/Sub patterns

## Running Perfharness within Eclipse

Once you have imported the pre-reqs you are ready to run within Eclipse or compile the jar for using on other machines. If you would like to run perfharness create a new Run Configuration for a Java application. Generally we would recommend running from Eclipse for functional and setup testing but for real performance testing using the jar from the command-line on a server.

Select "JMSPerfHarnessMain" as the Project and "JMSPerfHarness" as the Main Class as shown below

![Run Configuration Class](images/PerfharnessRunConfiguration1.png?raw=true "RunConfigurationClass")

On the "Arguments Tab" insert the appropriate set of program arguments such as (MQ Receiver Example):

![Run Configuration Arguments](images/PerfharnessRunConfiguration2.png?raw=true "RunConfigurationArguments")

Select the Classpath tab, highlight the "User Entries" section and click the Add button. Add all of the PerfHarness projects to the classpath. This should result in a configuration such as this:

![Run Configuration Arguments](images/PerfharnessRunConfiguration3.png?raw=true "RunConfigurationClasspath")

Click "Run"

## Compiling perfharness.jar within Eclipse

There are 5 scripts which can be used to compile Perfharness.jar depending on whether you want all the capability or a specific one. The 5 scripts are:

* Perfharness/build_HTTP.xml - Used for building only the http modules.
* Perfharness/build_JMS.xml - Used for building the JMS module.
* Perfharness/build_JakartaJMS.xml - Used for building the Jakarta JMS module.
* Perfharness/build_MQ.xml - Used for building the MQ module.
* Perfharness/build_MQTT.xml - Used for building the MQTT module.

To compile perfharness.jar right click the the appropriate .xml file and select "Run As" then "1 Ant Build". 

![Run As](images/RunAs.png?raw=true "RunAs")

This should launch the build process which will produce the jar file perfharness.jar

![JarBuilt](images/PerfHarnessBuilt.png?raw=true "JarsBuilt")

## Sample Commands:

MQ (using IBM MQ classes for JMS) Requestor (preferred way to access MQ):
```java  -Xms512M -Xmx512M -cp /PerfHarness/perfharness.jar JMSPerfHarness -tc jms.r11.Requestor -nt 1 -ss 5 -sc BasicStats -wi 10 -to 3000 -rl 60 -su -wt 10 -dn 1 -mf<yourInputFile> -jh <mq host> -jp 1420 -jc SYSTEM.DEF.SVRCONN -jb <your qm> -iq REQUEST -oq REPLY -mt text -jt mqc -pc WebSphereMQ -ja 100```

MQ (using IBM MQ classes for Java) Requestor:

```java -ms512M -mx512M -cp /PerfHarness/perfharness.jar JMSPerfHarness -tc mqjava.Requestor -nt 1 -ss 5 -sc BasicStats -wi 10 -to 3000 -rl 60 -sh false -ws 1 -dn 1 -mf <yourInputFile> -jh localhost -jp 1414 -jb CSIM  -jc SYSTEM.BKR.CONFIG -iq CSIM_SERVER_IN_Q -oq CSIM_COMMON_REPLY_Q```

HTTP Requestor:

```java -ms512M -mx512M -cp /PerfHarness/perfharness.jar JMSPerfHarness -tc http.HTTPRequestor -nt 1 -ss 5 -sc BasicStats -wi 10 -to 3000 -rl 60 -sh false -ws 1 -dn 1 -mf MyInputMessage.xml -jh localhost -jp 7800 -ur "requestinout"```

HTTPS Requestor:

```java -ms512M -mx512M -Djavax.net.ssl.trustStore=httpsCAPHTrustStore.jks -Djavax.net.ssl.trustStorePassword=MyPassw0rd -Djavax.net.ssl.trustStoreType=JKS -cp /PerfHarness/perfharness.jar JMSPerfHarness -tc http.HTTPRequestor -nt 1 -ss 5 -sc BasicStats -wi 10 -to 3000 -rl 60 -sh false -ws 1 -dn 1 -mf MyInputMessage.xml -jh localhost -jp 7800 -ur "requestinout" -se true```

SOAP Requestor:

```java -ms512M -mx512M -cp /PerfHarness/perfharness.jar JMSPerfHarness -tc http.HTTPRequestor -nt 1 -ss 5 -sc BasicStats -wi 10 -to 3000 -rl 60 -sh false -ws 1 -dn 1 -mf <yourInputFile> -jh localhost -jp 7800 -ur "SoapProvider" -sa SummerSale```

TCPIP Requestor: 

```java -ms512M -mx512M -cp /PerfHarness/perfharness.jar JMSPerfHarness -tc tcpip.TCPIPRequestor -nt 1 -ss 5 -sc BasicStats -wi 10 -to 3000 -rl 60 -sh false -dn 1 -mf MyInputMessage.xml -jh localhost -jp 1455 -rb 1384```

### Common Flags:

```-tc transport
-iq Input Queue
-oq Output Queue
-jp MQ Listener Port
-jb Queue Manager Name
-jc Queue Manager Channel name
-jh Endpoint Hostname
-rb Receive Buffer
-to Request Timeout
-ss Snapshot interval
-sc Statistics Module
-mg Input Sample Data filename
-nt Number of Threads
-sh Intercept crtl-c
-rl Run Length
-wi wait interval in ms between starting clients
```

## Docker

There is a git repo that can help develop a dockerized version of JMSPerfHarness called [jmstestp](https://github.com/ibm-messaging/jmstestp)

## Additional Information
For more in-depth documentation please refer to the PerfHarness manual in the [PerfHarness/doc](./PerfHarness/doc) folder.
A tutorial on running a JMS performance test with perfharness is available in the [PerfHarness/samples](./samples) folder [here](./samples/jmsperfharness_tutorial1.md).


