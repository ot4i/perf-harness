/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp;

import java.util.logging.Level;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import com.ibm.msg.client.jms.JmsConnectionFactory;
import com.ibm.msg.client.jms.JmsConstants;
import com.ibm.msg.client.jms.JmsFactoryFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.amqp.utils.MessageContainer;
import com.ibm.uk.hursley.perfharness.amqp.utils.MessageContainer.MessageException;

/**
 * This class is design to send the same message as quickly as
 * possible to the named topic.
 */
public final class FloodPublisher extends AMQPWorkerThread implements WorkerThread.Paceable {
	
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	
	private static final long ONE_SECOND = 1000L;
	
	/** The inbound topic name that this publisher will send to**/
	private final String  inboundTopic = Config.parms.getString("it");
	/** Whether to use JMS facilities to send the message to the topic **/
	private final boolean useJMS = Config.parms.getBoolean("jms");
	/** Whether the topic being published to has the thread id appended **/
	private final boolean topicIndexByThread = Config.parms.getBoolean("tpt");
	/** Rate of flooding the message per second */
	private final int messageRate = Config.parms.getInt("mr");

	/** Sample message used to send **/
	private final String message;
	/** Topic to publish to */
	private final String topic;
	/** Use for JMS sending of messages **/
	private Connection jmsConnection;
	/** Use for JMS sending of messages **/
	MessageProducer jmsProducer;
	/** Use for JMS sending of messages **/
	BytesMessage jmsMessage; 
	
	/** Message sent so far this message period. **/
	int numOfMessages = 0;
	/** Start time of message period **/
	long startPeriod = System.currentTimeMillis();


    /**
     * @param name the name for this thread.
     * @throws MessageException
     */
    public FloodPublisher(String name) throws MessageException {
        super(name);
		topic = topicIndexByThread ? inboundTopic + getThreadNum() : inboundTopic;
        message = new MessageContainer().getStringMessage();
    }
    
	/**
	 * 
	 */
	public static void registerConfig() {
		Config.registerSelf(FloodPublisher.class);
		if (!Config.isInvalid())
			Config.registerAnother(Config.parms.getClazz("df"));
	}

    public void run() {
        run(this);
    } 

    /* (non-Javadoc)
     * @see com.ibm.uk.hursley.perfharness.mqjava.MQJavaWorkerThread#buildMQJavaResources()
     */
    public void buildAMQPResources() throws Exception {
    	if (useJMS) {
    		// Use JMS to send messages to a topic.
			JmsFactoryFactory ff = JmsFactoryFactory.getInstance(JmsConstants.WMQ_PROVIDER);
			JmsConnectionFactory cf = ff.createConnectionFactory();
			cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_BINDINGS);
			cf.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, "PERF0");
			jmsConnection = cf.createConnection();
			jmsConnection.start();
			Session jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Topic jmsTopic = jmsSession.createTopic(inboundTopic);
			jmsProducer = jmsSession.createProducer(jmsTopic);
			jmsMessage = jmsSession.createBytesMessage();
			jmsMessage.writeBytes(message.getBytes());
    	}
    	else {
    		super.buildAMQPResources();
    	}
    }
    
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		if (useJMS) {
			jmsProducer.send(jmsMessage);
		}
		else {
			client.sendMessage("flood", topic, message);
		}
		messageRateControl ();
		incIterations();
		return true;
	}
	
	/**
	 * 
	 */
	private void messageRateControl() {
		// Is feature enabled?
		if (messageRate > 0) {
			// Has the max. message of period been reached?
			if (++numOfMessages > messageRate) {
				long timeSpend = System.currentTimeMillis() - startPeriod;
				long pauseTime = ONE_SECOND - timeSpend;
				if (pauseTime > 0 ) pause(pauseTime);
				// Reset counts
				numOfMessages = 0;
				startPeriod = System.currentTimeMillis();
			}
		}
	}
	

	/**
	 * @param pauseTime
	 */
	private void pause (final long pauseTime) {
		if (logEnabled) log(Level.INFO, "Pausing for {0} millseconds",pauseTime);
		try {
			Thread.sleep(pauseTime);
		} catch (InterruptedException e) { 
			// Ignore
		}
	}
	

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.amqp.AMQPWorkerThread#close()
	 */
	@Override
	protected void close () {
		if (client != null) client.close(getName());
	}

	
	/**
	 * @author ElliotGregory
	 *
	 */
	public class ResponderException extends Exception {
		private static final long serialVersionUID = 1L;

		public ResponderException(final String message) {
			super(message);
		}
	}

}
