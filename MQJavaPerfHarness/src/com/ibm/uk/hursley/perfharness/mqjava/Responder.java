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

import com.ibm.mq.MQMessage;
import com.ibm.mq.constants.CMQC;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Takes messages off the request queue and places the same message on the reply queue.
 * Does not currently have an option to change the CorrelationId (in keeping with the
 * Requestor class).
 */
public final class Responder extends MQJavaWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    protected static final MQProvider mqprovider;
	
    final boolean transacted = Config.parms.getBoolean( "tx" );
   
    final long timeout = Config.parms.getInt("to")*1000;
	
	private final boolean copyReplyFromRequest = Config.parms.getBoolean( "cr" );
	private final boolean correlIDFromMsgID = Config.parms.getBoolean("co");
	private final String replyToQmgr = Config.parms.getString("qm");
	private final String replyToQueue = Config.parms.getString("qq");
    
	static {
		
		Config.registerSelf( Responder.class );
		MQProvider.registerConfig();
		mqprovider = MQProvider.getInstance();
		
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public Responder(String name) {
        super( name );
    }

    public void run() {
        run(this);
    } 

    public void buildMQJavaResources() throws Exception {
    	
    	super.buildMQJavaResources();
    	
        // Get destination pair if multiple are configured.
        int destID = destFactory.generateDestinationID( getThreadNum() );
        String iq = Config.parms.getString("iq");
        String oq = Config.parms.getString("oq");
		
        if ( destID>=0 ) {
        	iq += String.valueOf( destID );
        	oq += String.valueOf( destID );
        }    	
    	
		int mqoo = CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING;
		if (Config.parms.getBoolean("bf")) {
			mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
		}
		Log.logger.log(Level.FINE, "Opening {0}", oq);
		outqueue = qm.accessQueue(oq, mqoo);

		mqoo = CMQC.MQOO_INPUT_SHARED | CMQC.MQOO_FAIL_IF_QUIESCING;
		if (Config.parms.getBoolean("bf")) {
			mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
		}
		Log.logger.log(Level.FINE, "Opening {0}", iq);
		inqueue = qm.accessQueue(iq, mqoo);
		
		if ( ! copyReplyFromRequest ) {
			outMessage = mqprovider.createMessage( getName() );
			if (replyToQmgr != null && !replyToQmgr.equals("")) {
				outMessage.replyToQueueManagerName = replyToQmgr;
			}
			
			if (replyToQueue != null && !replyToQueue.equals("")) {
				outMessage.replyToQueueName = replyToQueue;
				outMessage.report = CMQC.MQRO_PASS_CORREL_ID | CMQC.MQRO_PASS_MSG_ID;
			}
		}
		
    }
    
	public boolean oneIteration() throws Exception {
		
		inMessage = new MQMessage();
		
		inqueue.get( inMessage,gmo );
		
		if ( copyReplyFromRequest ) {
			outMessage = inMessage;
		} // else see default above
		
		if ( correlIDFromMsgID ) {
			System.arraycopy(inMessage.messageId, 0, outMessage.correlationId, 0, CMQC.MQ_CORREL_ID_LENGTH);
			pmo.options &= ~CMQC.MQPMO_NEW_MSG_ID;
			pmo.options &= ~CMQC.MQPMO_NEW_CORREL_ID;
		} else if ( !copyReplyFromRequest ) {
			// only need to copy if we created a new message
			System.arraycopy(inMessage.correlationId, 0, outMessage.correlationId, 0, CMQC.MQ_CORREL_ID_LENGTH);
			pmo.options &= ~CMQC.MQPMO_NEW_CORREL_ID;
		}
		startResponseTimePeriod();
		
		outqueue.put( outMessage,pmo );
		
		if (transacted)
			qm.commit();
		
		incIterations();
		
		return true;
	}
}
