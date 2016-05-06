/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.amqp.utils.MessageContainer;

/**
 * Subscribe to Topic-domain messages.
 * 
 * 
 * TODO: this is incomplete and not ready for use
 */
public class Subscriber extends AMQPWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	// Message Received
	MessageContainer inMessage = null;

	// Selector String
	String selector = null;   

	public static void registerConfig() {
		Config.registerSelf( Subscriber.class );
	}    

	/**
	 * Constructor for JMSClientThread.
	 * @param name
	 */
	public Subscriber(String name) {
		super( name );
	}

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.amqp.AMQPWorkerThread#buildAMQPResources()
	 */
	protected void buildAMQPResources() throws Exception {
		super.buildAMQPResources();
		String topicPattern = destFactory.generateDestination(getThreadNum());
		client.subscribe(topicPattern);
	}

	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		run(this);
	}

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		if (client.receiveMessage() != null) {
			incIterations();
		}
		return true;
	}

}
