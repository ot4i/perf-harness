/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.ibm.mqlight.api.Delivery;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.amqp.utils.EmbeddedAction;
import com.ibm.uk.hursley.perfharness.amqp.utils.EmbeddedActionContainer;
import com.ibm.uk.hursley.perfharness.amqp.utils.FIFO.AMQPWakeupException;
import com.ibm.uk.hursley.perfharness.amqp.utils.MessageContainer;

/**
 * Generates sample message based on given size and send to the outbound/request topic.
 * It then waits for the same message back again on the reply topic.
 */
public final class Requestor extends AMQPWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	

	/** Number of message to send and receive per iteration. **/
	private final int msgPerIteration     		  = Config.parms.getInt("mi");
	/** Unique reference for this harness(JVM) **/
	private final static String harnessReference  = Config.parms.getString("id");
	/** Forces a failure is the connection to server is lost. **/
	private final boolean failureOnLostConnection = Config.parms.getBoolean("fl");
	/** Send message to topic with thread index appended **/
	private final boolean queueByThreadIndex 	  = Config.parms.getBoolean("qt");
	/** Base inbound topic name. **/
	private final static String inboundTopicBase  = Config.parms.getString("it");
	/** Base outbound topic name. **/
	private final static String outboundTopicBase = Config.parms.getString("ot");
	
	/** Embedded value to be include in the sample message **/
	private final EmbeddedAction embeddedAction = new EmbeddedAction();
	/** Topic that this Requestor will subscribe to to receive messages **/
	private final String inboundTopic;
	/** Topic that this Requestor will send messages to **/
	private final String outboundTopic;


	/**
	 * Constructor for JMSClientThread.
	 * @param name
	 */
	public Requestor(String name) {
		super(name);
		
		// Calculate the Request topic value.
        String topic = outboundTopicBase;
        if (queueByThreadIndex) {
        	topic += "." + getThreadNum();
        }
        outboundTopic = topic;
        
        inboundTopic = inboundTopicBase + "." + getName() + "." + harnessReference;
       	embeddedAction.putCommand("Reply2Topic", inboundTopic);
	}


	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		run(this);
	}

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.amqp.MQJavaWorkerThread#buildMQJavaResources()
	 */
	protected void buildAMQPResources() throws Exception {
		super.buildAMQPResources();

		client.subscribe(inboundTopic);
	}

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		
		if (failureOnLostConnection && client.connectionLost()) {
			log(Level.SEVERE, "({0}) has lost its connection.", getName());
			throw new AMQPException("Connection to AMQP Server has been lost.");
		}
		
		startResponseTimePeriod();
		
		String thisReference = generateReference(getName());
		embeddedAction.putCommand("Reference", thisReference);
		String prefix = embeddedAction.generateMessage();
		outMessage = new MessageContainer (prefix);
		
		try {
			for (int i = 0; i < msgPerIteration; i++) {
				client.sendMessage(thisReference, outboundTopic, outMessage);
			}
	
			for (int i = 0; i < msgPerIteration; i++) {
				Delivery delivery = client.receiveMessage();
				if (delivery == null) {
					break;
				}
				EmbeddedActionContainer eac = new EmbeddedActionContainer(delivery);
				String rxReference = eac.getValue("Reference");
				if (logEnabled) log(Level.FINE, "({0},{1}): Received - completed(2)", thisReference,rxReference);
				if (!thisReference.equals(rxReference)) log(Level.SEVERE,"Received the wrong message Expect:{0} Actual:{1}", thisReference, rxReference);
				if (client.doesRequireConfirmation()) {
					delivery.confirm();
				}
				incIterations();
				writeMessageToFileIfRequested();
			}
		}
		catch (AMQPWakeupException we) {
			log(Level.SEVERE, "({0}) has lost its connection.", getName());
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.amqp.AMQPWorkerThread#close()
	 */
	@Override
	protected void close () {
		if (client != null) client.close(getName());
	}


	/** Use to allocate unique reference values **/
	private static AtomicInteger refIndex = new AtomicInteger();
	/**
	 * Generates a unique reference.
	 * @param baseName
	 * @return
	 */
	private static String generateReference(final String baseName) {
		int myRefIndex = refIndex.addAndGet(1);
		return harnessReference + ":" + baseName + ":" + myRefIndex;
	}
}
