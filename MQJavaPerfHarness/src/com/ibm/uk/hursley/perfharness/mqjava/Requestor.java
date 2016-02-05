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
import com.ibm.mq.*;
import com.ibm.mq.constants.CMQC;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Takes messages off the request queue and places the same message on the reply queue.
 * Does not currently have an option to change the CorrelationId (in keeping with the
 * Requestor class).
 */
public final class Requestor extends MQJavaWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    protected static MQProvider mqprovider;

    private final boolean transacted = Config.parms.getBoolean( "tx" );

    private final boolean getMsgById = Config.parms.getBoolean( "mi" ); 

	private final boolean correlateMsg = Config.parms.getBoolean("co");
	private final int msgsToSendBeforeGetResp = Config.parms.getInt( "ir" ); //input to out put ratio, def =1  e.g. 3 means send 3 then get 1.
	private final int msgsToGetBeforePutReq = Config.parms.getInt( "or" ); //input to out put ratio, default =1  e.g. 3 means get 3 then put 1 msg, used for pubsub fan out.
	private final String replyToQmgr = Config.parms.getString("qm");
	protected MQQueue inqueue2;

	int RFHFormat = 0;
	int NewGetMessage = 0;

	static {
		Config.registerSelf( Requestor.class );
		MQProvider.registerConfig();
		mqprovider = MQProvider.getInstance();
	}

	/**
	 * Constructor for JMSClientThread.
	 * @param name
	 */
	public Requestor(String name) {
		super(name);
	}

	public void run() {
		run(this);
	}

	protected void buildMQJavaResources() throws Exception {
		super.buildMQJavaResources();

		// Get destination pair if multiple are configured.
		final int destID = destFactory.generateDestinationID(getThreadNum());
		final int mdm = Config.parms.getInt("mdm");		// multi-destination numbering minimum
		String iq = Config.parms.getString("iq");
		String oq = Config.parms.getString("oq");
		if (destID >= 0 && msgsToSendBeforeGetResp == 1) {
			if (destID >= mdm) {						// 30749
				iq += String.valueOf(destID);
				oq += String.valueOf(destID);
			}
		}

		Log.logger.log(Level.FINE, "Opening for output {0}", oq);
		int mqoo = CMQC.MQOO_INPUT_SHARED | CMQC.MQOO_FAIL_IF_QUIESCING;
		if (Config.parms.getBoolean("bf")) {
			mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
		}
		outqueue = qm.accessQueue(oq, mqoo);

		mqoo = CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING;
		if (Config.parms.getBoolean("bf")) {
			mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
		}

		//TODO: need a better solution here, should do a loop so can handle any number of in queues
		if (msgsToSendBeforeGetResp == 2) {
			Log.logger.log(Level.FINE, "Opening for input {0}", iq+"1");
			inqueue = qm.accessQueue(iq+"1", mqoo);

			Log.logger.log(Level.FINE, "Opening for input {0}", iq+"2");
			inqueue2 = qm.accessQueue(iq+"2", mqoo);
		} else {
			Log.logger.log(Level.FINE, "Opening for input {0}", iq);
			inqueue = qm.accessQueue(iq, mqoo);
		}

		outMessage = mqprovider.createMessage(getName());

		if (replyToQmgr != null && !replyToQmgr.equals("")) {
			outMessage.replyToQueueManagerName = replyToQmgr;
		}

		outMessage.replyToQueueName = oq;

		inMessage = new MQMessage();

		RFHFormat = Config.parms.getInt("rf");
		NewGetMessage = Config.parms.getInt("mm");
	}

	public boolean oneIteration() throws Exception {
		startResponseTimePeriod();

		inqueue.put(outMessage, pmo);
		
		if (msgsToSendBeforeGetResp == 2)
			inqueue2.put(outMessage, pmo);

		if (msgsToSendBeforeGetResp > 2) {
			for (int i = 1; i < msgsToSendBeforeGetResp; i++) {
				inqueue.put(outMessage, pmo);
				if (transacted)
					qm.commit();
			}
		}

		if (transacted)
			qm.commit();

		inMessage = new MQMessage();  
		
		if (correlateMsg) {
			// copy correlid for GMO matching
			inMessage.correlationId = outMessage.correlationId;
		} else
		if (getMsgById) {
			// copy msgId for GMO matching
			inMessage.messageId = outMessage.messageId;
		}

		gmo.waitInterval = savedGmoWaitInterval;
		gmo.options      = savedGmoOptions;
		gmo.matchOptions = savedGmoMatchOptions;	

		try {
			outqueue.get(inMessage, gmo);
		}
		catch (Throwable e){
			e.printStackTrace();
			System.exit(99);
		}

		if (transacted)
			qm.commit();
		
		if (msgsToGetBeforePutReq > 1) {
			for (int i = 1; i < msgsToGetBeforePutReq ; i++) {
				outqueue.get(inMessage, gmo);
				incIterations();
				if (transacted)
					qm.commit();
			}
		}

		incIterations();
		writeMessageToFileIfRequested();
		
		return true;
	}
    
}
