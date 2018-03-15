/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/


package com.ibm.uk.hursley.perfharness.jms.providers;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicSession;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;

import com.ibm.mq.jms.JMSC;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.mq.jms.MQDestination;
import com.ibm.mq.jms.MQQueue;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.mq.jms.MQTopic;
import com.ibm.mq.jms.MQTopicConnectionFactory;
import com.ibm.mq.pcf.CMQCFC;
import com.ibm.mq.pcf.PCFMessage;
import com.ibm.mq.pcf.PCFMessageAgent;
import com.ibm.msg.client.wmq.WMQConstants;
import com.ibm.msg.client.wmq.common.CommonConstants;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.jms.DestinationWrapper;

/**
 * Settings for direct connection to a WMQ broker.  These could all be set through
 * JMSAdmin allowing use of the JNDI module.
 */
@SuppressWarnings("deprecation")
public class WebSphereMQ extends JNDI implements JMSProvider {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;
	
	protected static boolean npsurviverestart;
	protected static boolean useUniqueQ;
	protected static boolean itx;
	protected static int ackCount;
	protected static boolean useOldJMS;
	protected static boolean autoCreateTopics;
	protected static String sslCipherSuite;
	protected static int providerVersion;
	protected static String receiveConversion; 
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		
		Config.registerSelf(WebSphereMQ.class);
		
		// Note: Potential things we could check
		// -jg should not be used with topics, it is ignored
		// -ju -jd not used with queued domain
		
		npsurviverestart = Config.parms.getBoolean( "js" );
		useUniqueQ = Config.parms.getBoolean( "ju" );
		useOldJMS = Config.parms.getBoolean( "jr" );
		ackCount = Config.parms.getInt( "ja" );
		itx = Config.parms.getBoolean( "jx" );
		autoCreateTopics = Config.parms.getBoolean( "je" );
		sslCipherSuite = Config.parms.getString( "jl" );
		providerVersion = Config.parms.getInt( "jv" );
		receiveConversion = Config.parms.getString( "jrc" );
	}


	/**
	 * Apply vendor-specific settings for building up a connection factory to WMQ.
	 * @param cf
	 * @throws JMSException
	 */
	protected void configureMQConnectionFactory(MQConnectionFactory cf)	throws JMSException {
		boolean bindings = Config.parms.getString("jt").equals("mqb");
		if (bindings) {
			// -jt mqb
			cf.setTransportType(CommonConstants.WMQ_CM_BINDINGS);
			cf.setPort(Config.parms.getInt("jp"));
			cf.setChannel(Config.parms.getString("jc"));
			
			if (Config.parms.getString("bt").equals("fp")) { 
				Log.logger.log( Level.INFO, "MQCNO_FASTPATH_BINDINGs set on");
				cf.setMQConnectionOptions(CMQC.MQCNO_FASTPATH_BINDING);
			} else if (Config.parms.getString("bt").equals("is")) {
				Log.logger.log( Level.INFO, "MQCNO_ISOLATED_BINDINGs set on");
				cf.setMQConnectionOptions(CMQC.MQCNO_ISOLATED_BINDING);
			} // else default is shared
		} else {
			// -jt mqc
			cf.setTransportType(CommonConstants.WMQ_CM_CLIENT);
			cf.setHostName(Config.parms.getString("jh"));
			cf.setPort(Config.parms.getInt("jp"));
			cf.setChannel(Config.parms.getString("jc"));
			cf.setSSLCipherSuite( sslCipherSuite );
		}
		cf.setQueueManager(Config.parms.getString("jb"));
		
		//Has no affect from WMQ V7 onwards - connection pooling removed
		cf.setUseConnectionPooling(Config.parms.getBoolean("jo"));

		if (useOldJMS) {
			cf.setSubscriptionStore(CommonConstants.WMQ_SUBSTORE_QUEUE);
		} else {
			cf.setSubscriptionStore(CommonConstants.WMQ_SUBSTORE_BROKER);
		}		

		/****************************************************************
		 * Provider Version options will only run using MQ
		 * JMS libraries at MQv7.0+
		 ****************************************************************/
		/* default is to ignore this section - useful for forcing a v7 client to v6 mode */
		if (providerVersion == 6) {
			setProviderVersion(cf, "6.0.0.0");
		} else if (providerVersion == 7) {
			setProviderVersion(cf, "7.0.0.0");
		}
		
		String psmode = System.getProperty( PSMODE );
		if ( psmode != null ) {
			// For subscribers, this sets the stream for them to subscribe to
			
			// Patch needed to run against WMQ older than 530CSD6
			if ( cf instanceof MQTopicConnectionFactory ) {
			
				MQTopicConnectionFactory tcf = ((MQTopicConnectionFactory)cf);

				tcf.setBrokerPubQueue( Config.parms.getString("jq") );
				if ( psmode.equals( PSMODE_PUB ) ) {
					
					if ( ackCount>=0 ) {
						tcf.setPubAckInterval( ackCount );
					}
					if ( itx ) {
						cf.setOutcomeNotification(true);
						cf.setOptimisticPublication(true);
					}				
				} else if (psmode.equals( PSMODE_SUB ) ) {
					if (useUniqueQ && !durable)	{
						// Note: For durable subscriptions, SubQueue is set at the topic level
						tcf.setBrokerSubQueue("SYSTEM.JMS.ND.SUB.*");
					}
					if ( itx ) {
						cf.setReceiveIsolation(CommonConstants.WMQ_RCVISOL_UNCOMMITTED); 
						cf.setProcessDuration(CommonConstants.WMQ_PROCESSING_SHORT); 
						cf.setOutcomeNotification(false);
					}				
				}
			} else {		
				cf.setBrokerPubQueue( Config.parms.getString("jq") );
				if ( ackCount>=0 ) {
					cf.setPubAckInterval( ackCount );
				}
				if (useUniqueQ && !durable)	{
					// Note: For durable subscriptions, SubQueue is set at the topic level
					cf.setBrokerSubQueue("SYSTEM.JMS.ND.SUB.*");
				}				
				
				if ( psmode.equals( PSMODE_PUB ) ) {
					if ( itx ) {
						cf.setOutcomeNotification(true);
						cf.setOptimisticPublication(true);
					}				
					
				} else if (psmode.equals( PSMODE_SUB ) ) {
					if ( itx ) {
						cf.setReceiveIsolation(JMSC.MQJMS_RCVISOL_UNCOMMITTED); 
						cf.setProcessDuration(JMSC.MQJMS_PROCESSING_SHORT); 
						cf.setOutcomeNotification(false);
					}				
				}				
			}
		} // end if psmodel!=null
		
		//JMS has two ways of performing authentication with an MQ QM
		//1. Compatibility mode
		//2. MQCSP Authentication
		//The default is using the compatibility method, but applications wanting to use CSP authentication
		//will need to set "jm" to true 
		if (Config.parms.getBoolean("jm")) {
			//MQCSP Authentication
			cf.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
		} else {
			cf.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, false);
		}
			
	}

	/**
	 * Apply vendor-specific settings to all destinations.
	 * @param dest
	 * @throws JMSException
	 */
	private void configureMQDestination( MQDestination dest ) throws JMSException {
		
		// msc - this will only compile against WMQ5.3 CSD6 or later
		// As long as -js isn't used, it will be fine for running tests on older levels  
		if (npsurviverestart)	{
			dest.setPersistence(CommonConstants.WMQ_PER_NPHIGH);
		}

		/****************************************************************
		 * Read Ahead and Streaming options can only be used with MQ
		 * JMS libraries at MQv7.0+
		 ****************************************************************/
		// If we want to use streaming/fire and forget on this destination
		// force enable.
		if (Config.parms.getBoolean( "jf" )) {
			setPutAsyncAllowed(dest);
		}

		// If we want to use read ahead on this destination
		// force enable.
		if (Config.parms.getBoolean( "jy" )) {
			setReadAheadAllowed(dest);
		}
		
		// If we want to test the receive conversion code
		if (receiveConversion.equals("WMQ_RECEIVE_CONVERSION_QMGR")) {
			setReceiveConversion(dest, "WMQ_RECEIVE_CONVERSION_QMGR");
		} else if (receiveConversion.equals("WMQ_RECEIVE_CONVERSION_CLIENT_MSG")) {
			setReceiveConversion(dest, "WMQ_RECEIVE_CONVERSION_CLIENT_MSG");
		}
	}

	/**
	 * Apply vendor-specific settings to topics.
	 * @param topic
	 * @throws JMSException
	 */
	private Topic configureMQTopic( MQTopic topic ) throws JMSException {
		
		if (durable) {
			if (useUniqueQ) {
				topic.setBrokerDurSubQueue("SYSTEM.JMS.D.SUB.*");
			}
		}
			
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
		
		configureMQDestination( topic );
		
		return topic;
		
	}

	
	/**
	 * Apply vendor-specific settings to all queues.
	 * @param queue
	 * @throws JMSException
	 */	
	private Queue configureMQQueue( MQQueue queue ) throws JMSException {
		// Currently there are no queue specific options enabled in here
		if ( Config.parms.getBoolean( "jg" ) ) {
			queue.setTargetClient(CommonConstants.WMQ_CLIENT_NONJMS_MQ);
		}
		
		/****************************************************************
		 * Read Ahead and Streaming options will only compile using MQ
		 * JMS libraries at MQv7.0+
		 ****************************************************************/
		
		// If we want to use streaming/fire and forget on this destination
		// force enable.
		if (Config.parms.getBoolean( "jf" )) {
			setPutAsyncAllowed(queue);
		}

		// If we want to use read ahead on this destination
		// force enable.
		if (Config.parms.getBoolean( "jy" )) {
			setReadAheadAllowed(queue);	
		}
		
		configureMQDestination( queue );
		return queue;
	}	

	/**
	 ****************************************************************
		* Read Ahead, Streaming & setStringProperty can only be used with MQ
		* JMS libraries at MQv7.0+
		* (this horrible reflection code allows it to compile with v6)
	 ****************************************************************
	 * Try to setPutAsyncAllowed using reflection
	 *
	 * @param dest an Object that supports setPutAsyncAllowed
	 */
	protected void setPutAsyncAllowed(Object dest) {
		try {
			Field ProviderVersion = JMSC.class.getField("MQJMS_PUT_ASYNC_ALLOWED_ENABLED");
		
			Integer MQJMS_PUT_ASYNC_ALLOWED_ENABLED = (Integer) ProviderVersion.get(null);
		
			Class<?> MQDestination = dest.getClass();
			Class<?>[] parameterTypes = {int.class};
			Method setPutAsyncAllowed = MQDestination.getMethod("setPutAsyncAllowed", parameterTypes);
		
			setPutAsyncAllowed.invoke(dest, MQJMS_PUT_ASYNC_ALLOWED_ENABLED);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			Log.logger.log( Level.WARNING, "-jf unsupported for this JMS version");
		} catch (IllegalArgumentException e) {
			Log.logger.log( Level.WARNING, "-jf unsupported for this JMS version");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			Log.logger.log( Level.WARNING, "-jf unsupported for this JMS version");
		} catch (InvocationTargetException e) {
			Log.logger.log( Level.WARNING, "-jf unsupported for this JMS version");
		}
	}
	
	/**
	 * Try to setReadAheadAllowed using reflection
	 * @param dest an Object that supports setReadAheadAllowed
	 */
	protected void setReadAheadAllowed(Object dest) {
		try {
			Field ProviderVersion = JMSC.class.getField("MQJMS_READ_AHEAD_ALLOWED_ENABLED");
			Integer MQJMS_READ_AHEAD_ALLOWED_ENABLED = (Integer) ProviderVersion.get(null);
		
			Class<?> MQDestination = dest.getClass();
			Class<?>[] parameterTypes = {int.class};
			Method setReadAheadAllowed = MQDestination.getMethod("setReadAheadAllowed", parameterTypes);
		
			setReadAheadAllowed.invoke(dest, MQJMS_READ_AHEAD_ALLOWED_ENABLED);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			Log.logger.log( Level.WARNING, "-jy unsupported for this JMS version");
		} catch (IllegalArgumentException e) {
			Log.logger.log( Level.WARNING, "-jy unsupported for this JMS version");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			Log.logger.log( Level.WARNING, "-jy unsupported for this JMS version");
		} catch (InvocationTargetException e) {
			Log.logger.log( Level.WARNING, "-jy unsupported for this JMS version");
		}
	}
	
	/**
	 * Try to setProviderVersion using reflection
	 * This method needs to exist as WMQ JMS v6 libs doesn't have a setStringProperty option
	 * so this code won't compile with those libs. As we want the code to be backwards
	 * compatible this code tries to setStringProperty & reports a warning if it doesn't
	 * work. The application will continue as normal.
	 * @param cf an MQConnectionFactoryObject
	 */
	protected void setProviderVersion(MQConnectionFactory cf, String version) {
		try {
			Class<?> WMQConstants = Class.forName("com.ibm.msg.client.wmq.WMQConstants");
			Field ProviderVersion = WMQConstants.getField("WMQ_PROVIDER_VERSION");
			String WMQ_PROVIDER_VERSION = (String) ProviderVersion.get(null);
			Class<? extends MQConnectionFactory> MQConnectionFactory = cf.getClass();
			Class<?>[] parameterTypes = {String.class, String.class};
			Method setStringProperty = MQConnectionFactory.getMethod("setStringProperty", parameterTypes);
			/* default is to ignore this section - useful for forcing a v7 client to v6 mode */
			setStringProperty.invoke(cf, WMQ_PROVIDER_VERSION, version);
			Log.logger.log( Level.INFO, "WMQ_PROVIDER_VERSION set to " + version);
		} catch (ClassNotFoundException e) {
			Log.logger.log( Level.WARNING, "-jv unsupported for this JMS version");
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			Log.logger.log( Level.WARNING, "-jv unsupported for this JMS version");
		} catch (IllegalArgumentException e) {
			Log.logger.log( Level.WARNING, "-jv unsupported for this JMS version");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			Log.logger.log( Level.WARNING, "-jv unsupported for this JMS version");
		} catch (InvocationTargetException e) {
			Log.logger.log( Level.WARNING, "-jv unsupported for this JMS version");
		}
	}

	protected void setReceiveConversion(MQDestination dest, String value) {
		try {
			Class<?> WMQConstants = Class.forName("com.ibm.msg.client.wmq.common.CommonConstants");
			Field ReceiveConversion = WMQConstants.getField(value);
			int WMQ_RECEIVE_CONVERSION = (Integer) ReceiveConversion.get(null);
			
			Class<? extends MQDestination> MQDestination = dest.getClass();
			Class<?>[] parameterTypes = {int.class};
			Method setReceiveConversion = MQDestination.getMethod("setReceiveConversion", parameterTypes);			
			setReceiveConversion.invoke(dest, WMQ_RECEIVE_CONVERSION);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			Log.logger.log( Level.WARNING, "-jrc unsupported for this JMS version");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			Log.logger.log( Level.WARNING, "-jrc unsupported for this JMS version");
		} catch (InvocationTargetException e) {
			Log.logger.log( Level.WARNING, "-jrc unsupported for this JMS version");
		} catch (NoSuchFieldException e) {
			Log.logger.log( Level.WARNING, "-jrc unsupported for this JMS version");
		} catch (ClassNotFoundException e) {
			Log.logger.log( Level.WARNING, "-jrc unsupported for this JMS version");
		}
	}
	
	
	//
	// JMS 1.0.2 methods
	//
	/**
	 * Create a new vendor-specific ConnectionFactory (or delegate to JNDI if that is has been selected).
	 */
	public TopicConnectionFactory lookupTopicConnectionFactory(String name) throws JMSException,NamingException {

		if ( usingJNDI ) {
			return super.lookupTopicConnectionFactory(name);
		} else {
			MQTopicConnectionFactory tcf = new MQTopicConnectionFactory();
			configureMQConnectionFactory(tcf);
			return tcf;
		} // end if tcf==null
		
	}

	/**
	 * Create a new vendor-specific ConnectionFactory (or delegate to JNDI if that is has been selected).
	 */	
	public synchronized QueueConnectionFactory lookupQueueConnectionFactory(String name) throws JMSException,NamingException {

		if ( usingJNDI ) {
			return super.lookupQueueConnectionFactory(name);
		} else {
			MQQueueConnectionFactory qcf = new MQQueueConnectionFactory();
			configureMQConnectionFactory(qcf);
			return qcf;
		} // end if qcf==null

	}	
	
	public DestinationWrapper<Queue> lookupQueue( String queue, QueueSession session ) throws JMSException, NamingException {

		if ( usingJNDI || session==null ) {
			return lookupQueueFromJNDI( queue ); 
		} else {
			return new DestinationWrapper<Queue>(queue,
					configureMQQueue((MQQueue) session.createQueue(queue)));
		}		
		
	}
	

	public DestinationWrapper<Topic> lookupTopic( String topic, TopicSession session ) throws JMSException, NamingException {

		if ( usingJNDI || session==null ) {
			return new DestinationWrapper<Topic>(topic,
					configureMQTopic((MQTopic) session.createTopic(topic))); 
		} else {
			return new DestinationWrapper<Topic>(topic,
					configureMQTopic((MQTopic) session.createTopic(topic)));
		}		
		
	}

	//
	// JMS 1.1 methods
	//
	/**
	 * Create a new vendor-specific ConnectionFactory (or delegate to JNDI if that is has been selected).
	 */	
	public synchronized ConnectionFactory lookupConnectionFactory(String name) throws JMSException, NamingException {
		
		if ( usingJNDI ) {
			return super.lookupConnectionFactory(name);
		} else {
			MQConnectionFactory cf = new MQConnectionFactory();
			configureMQConnectionFactory( cf );
			return cf;
		} // end if cf==null

	}

	public DestinationWrapper<Queue> lookupQueue( String queue, Session session ) throws JMSException, NamingException {
		
		if ( usingJNDI || session==null ) {
			return lookupQueueFromJNDI( queue ); 
		} else {
			return new DestinationWrapper<Queue>(queue,
					configureMQQueue((MQQueue) session.createQueue(queue)));
		}
		
	}
		
	public DestinationWrapper<Topic> lookupTopic( String topic, Session session ) throws JMSException, NamingException {
		
		if ( usingJNDI || session==null ) {
			if ( autoCreateTopics ) {
				Topic t = configureMQTopic( (MQTopic)session.createTopic( topic ) );
				try {
					getInitialContext().bind( topic, t );
					Log.logger.fine( "Auto-created JNDI entry for "+topic );
				} catch ( NameAlreadyBoundException e ) {
					// No op - already exists
				}
			} // end if
			return lookupTopicFromJNDI( topic );
		} else {
			return new DestinationWrapper<Topic>(topic,
					configureMQTopic((MQTopic) session.createTopic(topic)));			
		}
		
	}
	
	public void createQueue(String name) throws Exception {
		
		if ( usingJNDI ) {
			// Assumes use of ME01 SupportPac for WMQInitialContextFactory
			Queue queue = configureMQQueue( new MQQueue(name) );
			try {
				getInitialContext().bind( name, queue );
			} catch ( NameAlreadyBoundException e ) {
				// No op - already exists
			}
		} else {
            // "new" PCF style.
			PCFMessageAgent agent = new PCFMessageAgent( Config.parms.getString("jh"), Config.parms.getInt("jp"), "CLIENT" );
			PCFMessage message = new PCFMessage( CMQCFC.MQCMD_CREATE_Q );
			message.addParameter( CMQC.MQCA_Q_NAME, name);
			
			agent.send( message );
		}
		
	}

	public void createTopic(String name) throws Exception {
		
		if ( usingJNDI ) {
			// Assumes use of ME01 SupportPac for WMQInitialContextFactory
			Topic topic = configureMQTopic( new MQTopic(name) );
			try {
				getInitialContext().bind( name, topic );
			} catch ( NameAlreadyBoundException e ) {
				// No op - already exists
			}
		} else {
			// No-op
		}
		
	}

	public void createConnectionFactory(String name) throws Exception {
		
		if ( usingJNDI ) {
			ConnectionFactory cf = new MQConnectionFactory();
			configureMQConnectionFactory( (MQConnectionFactory)cf );
			try {
				getInitialContext().bind( name, cf );
			} catch ( NameAlreadyBoundException e ) {
				// swallowed
			}
		} else {
			// No op
		}
		
	}
	
	public void createQueueConnectionFactory(String name) throws Exception {
		
		if ( usingJNDI ) {
			QueueConnectionFactory qcf = new MQQueueConnectionFactory();
			configureMQConnectionFactory((MQConnectionFactory)qcf);
			try {
				getInitialContext().bind( name, qcf );
			} catch ( NameAlreadyBoundException e ) {
				// swallowed
			}
		} else {
			// No op
		}
		
	}
	
	public void createTopicConnectionFactory(String name) throws Exception {
		
		if ( usingJNDI ) {
			TopicConnectionFactory tcf = new MQTopicConnectionFactory();
			configureMQConnectionFactory((MQConnectionFactory)tcf);
			try {
				getInitialContext().bind( name, tcf );
			} catch ( NameAlreadyBoundException e ) {
				// swallowed
			}
		} else {
			// No op
		}
		
	}

    /**
     ** delete a queue on the provider. differs from deleting a jms queue.
     ** unbind the associated admin object from the jndi store. 
     */
    public void deleteQueue(String name) throws Exception {
        
        if ( usingJNDI ) {
            // Assumes use of ME01 SupportPac for WMQInitialContextFactory
        	System.setProperty( "MQJMS_PURGE_ON_DELETE", "yes" );
        	unbind( name );
        } else {
            
            // "new" PCF style.
			PCFMessageAgent agent = new PCFMessageAgent( Config.parms.getString("jh"), Config.parms.getInt("jp"), "CLIENT" );
			PCFMessage message = new PCFMessage( CMQCFC.MQCMD_DELETE_Q );
			message.addParameter( CMQC.MQCA_Q_NAME, name);
			
			message.addParameter( CMQCFC.MQIACF_PURGE, CMQCFC.MQPO_YES );
			
			agent.send( message );
		 	
        }
    
    }
    
    /**
     ** delete a topic on the provider. differs from deleting a jms topic.
     ** unbind the associated admin object from the jndi store. 
     */
    
    public void deleteTopic(String name) throws Exception {
    	
    	if ( usingJNDI ) {
            // Assumes use of ME01 SupportPac for WMQInitialContextFactory
    		System.setProperty( "MQJMS_PURGE_ON_DELETE", "yes" );
            unbind( name );
    	} else {
    	    // No op
    	}
    	
    }


	public DestinationWrapper<Queue> lookupQueue(String queue, JMSContext context) throws JMSException, NamingException {
		if (usingJNDI || context == null) {
			return lookupQueueFromJNDI(queue); 
		} else {
			return new DestinationWrapper<Queue>(queue, configureMQQueue((MQQueue) context.createQueue(queue)));
		}
		
	}
		
	public DestinationWrapper<Topic> lookupTopic(String topic, JMSContext context) throws JMSException, NamingException {
		
		if (usingJNDI || context == null) {
			if (autoCreateTopics) {
				Topic t = configureMQTopic((MQTopic)context.createTopic(topic));
				try {
					getInitialContext().bind(topic, t);
					Log.logger.fine( "Auto-created JNDI entry for: " + topic );
				} catch ( NameAlreadyBoundException e ) {
					// No op - already exists
				}
			} // end if
			return lookupTopicFromJNDI(topic);
		} else {
			return new DestinationWrapper<Topic>(topic,	configureMQTopic((MQTopic) context.createTopic(topic)));			
		}
	}    
    
}
