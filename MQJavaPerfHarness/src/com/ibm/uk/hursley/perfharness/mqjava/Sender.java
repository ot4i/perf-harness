/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.mqjava;

import java.util.logging.Level;

import com.ibm.mq.constants.CMQC;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Send messages to a Queue.
 */
public final class Sender extends MQJavaWorkerThread implements WorkerThread.Paceable{

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	protected static MQProvider mqprovider;

	private final boolean transacted = Config.parms.getBoolean("tx");
	
	private final String replyToQmgr = Config.parms.getString("qm");

	private final String replyToQueue = Config.parms.getString("qq");

	static {
		
		Config.registerSelf(Sender.class);
		MQProvider.registerConfig();
		mqprovider = MQProvider.getInstance();
		
	}

	/**
	 * Constructor for mqjava.Sender
	 * 
	 * @param name
	 */
	public Sender(String name) {
		super( name );
		try {
			inMessage = mqprovider.createMessage(getName());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void run() {		
		run(this);
	}
	
	protected void buildMQJavaResources() throws Exception {
		super.buildMQJavaResources();
		
		// Use the DefaultDestinationFactory object instead of just
    	// taking the manual destination entry.
    	
    	final int destID = destFactory.generateDestinationID(getThreadNum());
		final int mdm = Config.parms.getInt("mdm");		// multi-destination numbering minimum
		String destQName = Config.parms.getString("d");
		if (destID >= 0) {
			if (destID >= mdm) {						// 30749
				destQName += String.valueOf(destID);
			}
		}
    	
		
		int mqoo = CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING;
		if (Config.parms.getBoolean("bf")) {
			mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
		}
		
		inMessage.messageType = CMQC.MQMT_DATAGRAM;
		
		Log.logger.log(Level.FINE, "Opening {0}", destQName);
		queue = qm.accessQueue(destQName, mqoo);
	
		if (replyToQmgr != null && !replyToQmgr.equals("")) {
			inMessage.replyToQueueManagerName = replyToQmgr;
		}
		
		if (replyToQueue != null && !replyToQueue.equals("")) {
			inMessage.replyToQueueName = replyToQueue;
			inMessage.messageType = CMQC.MQMT_REQUEST;
		}
	}
	
  	public boolean oneIteration() throws Exception {
			
  		startResponseTimePeriod();
  		
		queue.put(inMessage, pmo);
		if (transacted) {
			qm.commit();
		}
		
		incIterations();

		return true;
	}

}
