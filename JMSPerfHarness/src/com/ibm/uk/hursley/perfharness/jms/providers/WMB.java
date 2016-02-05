/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/


package com.ibm.uk.hursley.perfharness.jms.providers;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicSession;
import javax.naming.NamingException;

import com.ibm.mq.jms.JMSC;
import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.mq.jms.MQTopic;
import com.ibm.mq.jms.MQTopicConnectionFactory;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.jms.DestinationWrapper;


/**
 * Settings for direct connection to an IBM Websphere MB or WebSphere EB broker.
 * These could all be set through JMSAdmin allowing use of the JNDI module.
 * 
 */
public class WMB extends WebSphereMQ {
	
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;
	
	private static String transport;
	private static int bufferSize =1000;
	static int brokerVersion = JMSC.MQJMS_BROKER_V2;
	protected static boolean usingMQ;
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		Config.registerSelf(WMB.class);	
		transport = Config.parms.getString("jt");
		bufferSize = Config.parms.getInt("jz");
		if ( transport.equals("mqb") || transport.equals("mqc") ) {
			usingMQ = true;
		} else {
			usingMQ = false;
		}
	}
	
	//
	// JMS 1.1 methods
	//
	/**
	 * Provide additional functionality for WMB connection factories.
	 */
	private void configureWBIMBConnectionFactory(MQConnectionFactory cf) throws JMSException {
	
			//Set common attributes i.e. version,port,hostname and buffersize.			
			//Set the broker version we will use version 2 is suitable for argo brokers
			cf.setBrokerVersion(brokerVersion);
			cf.setHostName(Config.parms.getString("jh"));
			cf.setPort(Config.parms.getInt("jp"));
			if (bufferSize > 0)
			{
				cf.setMaxBufferSize(bufferSize);
			}
	
			if (transport.equals("ip"))
			{
				System.out.println("Using transport type MQJMS_TP_DIRECT_TCPIP");
				cf.setTransportType(JMSC.MQJMS_TP_DIRECT_TCPIP);
			}
			else if (transport.equals("ipmc"))
			{
				System.out.println("Using transport type MQJMS_TP_DIRECT_TCPIP, Multicast Enabled");
				cf.setTransportType(JMSC.MQJMS_TP_DIRECT_TCPIP);
				cf.setMulticast(JMSC.MQJMS_MULTICAST_ENABLED);
			}
			else if (transport.equals("ipmcr"))
			{
				System.out.println("Using transport type MQJMS_TP_DIRECT_TCPIP, Multicast Enabled & Reliable");
				cf.setTransportType(JMSC.MQJMS_TP_DIRECT_TCPIP);
				cf.setMulticast(JMSC.MQJMS_MULTICAST_RELIABLE);
			}
			else if (transport.equals("ipmcn"))
			{
				System.out.println("Using transport type MQJMS_TP_DIRECT_TCPIP, Multicast Enabled & NOT Reliable");
				cf.setTransportType(JMSC.MQJMS_TP_DIRECT_TCPIP);
				cf.setMulticast(JMSC.MQJMS_MULTICAST_NOT_RELIABLE);
			}
			else if (transport.equals("mqb"))
			{
				//Place holder statement incase we ever need to do anything for this transport over and above what the MQ plugin already does.
				cf.setBrokerQueueManager(Config.parms.getString("jb"));
			}
			else if (transport.equals("mqc"))
			{
				//Place holder statement incase we ever need to do anything for this transport over and above what the MQ plugin already does.
				cf.setBrokerQueueManager(Config.parms.getString("jb"));
			}
			else
			{
				System.out.println("Invalid transport type");
				System.exit(1);
			}
			
	
	}
	
	private Topic configureWBIMBTopic( MQTopic topic ) throws JMSException {
		
		topic.setBrokerVersion(brokerVersion);
		
		
		/****************************************************************
		 * Read Ahead and Streaming options will only compile using MQ
		 * JMS libraries at MQv7.0+
		 ****************************************************************/
		
		// If we want to use streaming/fire and forget on this destination
		// force enable.
		if (Config.parms.getBoolean( "jf" )) {
			setPutAsyncAllowed(topic);			
		}

		// If we want to use read ahead on this destination
		// force enable.
		if (Config.parms.getBoolean( "jy" )) {
			setReadAheadAllowed(topic);			
		}	
		
		return topic; 
		
	}
	
	public synchronized ConnectionFactory lookupConnectionFactory(String name) throws JMSException, NamingException {
		
		ConnectionFactory cf;
		if ( usingJNDI  ) 
		{
			//call the super method which will get the cf from JNDI and return just that
			return super.lookupConnectionFactory(name);
		} 
		else if (usingMQ)
		{
			//call the super method which will set most of the MQ stuff but we will call our method as well to override/set anything for MB.
			cf = super.lookupConnectionFactory(name);
			configureWBIMBConnectionFactory( (MQConnectionFactory)cf );
		}
		else
		{
			//we must be using IP or MC, lets create and setup the cf ourselves...
			cf = new MQConnectionFactory();
			configureWBIMBConnectionFactory( (MQConnectionFactory)cf );
			return cf;
		} // end if cf==null
		return cf;
	
	}

	public DestinationWrapper<Topic> lookupTopic( String topic, Session session ) throws JMSException, NamingException {	
		if ( usingJNDI || session==null ) 
		{
			return lookupTopicFromJNDI( topic ); 
		} else if(usingMQ)
		{
			//if we are using MQ call the superclass MQ methods to create the topic then we'll do anything MB specific..
			DestinationWrapper<Topic> dw = super.lookupTopic(topic, session);
			configureWBIMBTopic((MQTopic)dw.destination);
			return dw;
		}
		//if we are here then we need to go create and configure the topic ourselves as it must be for MC or IP
		return new DestinationWrapper<Topic>(topic,
				configureWBIMBTopic((MQTopic) session.createTopic(topic)));
	}


	//
	// JMS 1.0.2 methods
	//
	public TopicConnectionFactory lookupTopicConnectionFactory( String name )
			throws JMSException, NamingException {

		if (usingJNDI || usingMQ) {
			return super.lookupTopicConnectionFactory(name);
		} else {
			MQTopicConnectionFactory tcf = new MQTopicConnectionFactory();
			configureWBIMBConnectionFactory((MQConnectionFactory) tcf);
			return tcf;
		} // end if tcf==null

	}

	public DestinationWrapper<Topic> lookupTopic(String topic, TopicSession session)
			throws JMSException, NamingException {

		if (usingJNDI || session == null) {
			return lookupTopicFromJNDI(topic);
		} else if (usingMQ) {
			//if we are using MQ call the superclass MQ methods to create the
			// topic then we'll do anything MB specific..
			//if we are using MQ call the superclass MQ methods to create the topic then we'll do anything MB specific..
			DestinationWrapper<Topic> dw = super.lookupTopic(topic, session);
			configureWBIMBTopic((MQTopic)dw.destination);
			return dw;
		}
		//if we are here then we need to go create and configure the topic
		// ourselves as it must be for MC or IP
		return new DestinationWrapper<Topic>(topic,
				configureWBIMBTopic((MQTopic) session.createTopic(topic)));
	}

}
