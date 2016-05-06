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

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.WorkerThread;


/**
 * @author ElliotGregory
 *
 * This class is designed to read as many message from the subscribed topic.
 */
public final class FloodSubscriber extends AMQPWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	
	/** force failure if connection lost. **/
	private final boolean failureOnLostConnection = Config.parms.getBoolean("fl");
	/** Topic to read the messages from **/
	private final String  inboundTopicBase = Config.parms.getString("it");
	/** Topic index per thread id **/
	private final boolean topicIndexByThread = Config.parms.getBoolean("tpt");
	
	/** Inbound topic to subscribe and receive messages from **/
	private final String inboundtopic;



	/**
	 * @param name
	 */
	public FloodSubscriber(String name) {
		super(name);
		inboundtopic = topicIndexByThread ? inboundTopicBase + getThreadNum() : inboundTopicBase;
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

		client.subscribe(inboundtopic);
	}

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		
		if (failureOnLostConnection && client.connectionLost()) {
			log(Level.SEVERE, "({0}) has lost its connection.", getName());
			throw new AMQPException("Connection to AMQP Server has been lost.");
		}
		
		client.receiveMessage();
		incIterations();
		return true;
	}
	

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.amqp.AMQPWorkerThread#close()
	 */
	@Override
	protected void close () {
		if (client != null) client.close(getName());
	}
}
