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
 * Consumes messages from a queue.
 */
public final class Receiver extends MQJavaWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	protected static MQProvider mqprovider;
	
    boolean transacted;
	    
	static {
		
		Config.registerSelf( Receiver.class );
		MQProvider.registerConfig();
		mqprovider = MQProvider.getInstance();
		
	}    
    
    /**
     * Constructor for 
     * @param name
     */
    public Receiver(String name) {
    	
        super( name );
        transacted = Config.parms.getBoolean( "tx" );
	
    }

    public void run() {
    	run(this);   	
    }

    protected void buildMQJavaResources() throws Exception {
    	super.buildMQJavaResources();

    	// Generate queue name from the base queue name and
    	// the parameters -db -dn. For example:
    	// -db 0 -dn 2 -d CSIM_DISCARD_REPLY_Q
    	// Will produce CSIM_DISCARD_REPLY_Q and CSIM_DISCARD_REPLY_Q2
    	// -db is the start, -dn is the number of queues.
    	// Note no 1!
    	int destID = destFactory.generateDestinationID( getThreadNum() );
        String qname = Config.parms.getString("d");
        if ( destID>=0 ) {
        	if (destID>1 ) {
        		qname += String.valueOf( destID );
        	} 
        }    	
    	    	
		Log.logger.log(Level.FINE, "Opening {0}", qname );  
		int mqoo = CMQC.MQOO_INPUT_SHARED | CMQC.MQOO_FAIL_IF_QUIESCING;
		if (Config.parms.getBoolean("bf")) {
			mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
		}
		queue = qm.accessQueue(qname, mqoo);
    }
    
	public boolean oneIteration() throws Exception {
		
		startResponseTimePeriod();

		// As of MQ 7.5.0.1, each message needs a new MQMessage object
		final MQMessage m = new MQMessage();

		gmo.waitInterval = savedGmoWaitInterval; 
		gmo.options      = savedGmoOptions;
		gmo.matchOptions = savedGmoMatchOptions;

		queue.get(m, gmo);

		if (transacted)
			qm.commit();

		incIterations();
		return true;
	}

}
