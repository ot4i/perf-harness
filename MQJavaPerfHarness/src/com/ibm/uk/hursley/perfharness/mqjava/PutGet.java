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
 * Sends a message then receives one from the same queue.  Normal use is with
 * correlation identifier to ensure the same message is received.
 */
public final class PutGet extends MQJavaWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
   
    protected static MQProvider mqprovider;
	
    private final boolean transacted = Config.parms.getBoolean( "tx" );           
    
	public static void registerConfig() {

		Config.registerSelf( PutGet.class );
		MQProvider.registerConfig();
		mqprovider = MQProvider.getInstance();
		
	}    
    
    /**
     * Constructor for 
     * @param name
     */
    public PutGet(String name) {
    	
        super( name );
        try {
			inMessage = mqprovider.createMessage(getName());
		} catch (Exception e) {
		}
		
    }

    public void run() {
    	run(this);	
    }

    protected void buildMQJavaResources() throws Exception {
    	
    	super.buildMQJavaResources();
    	
    	// Use the DefaultDestinationFactory object instead of just
    	// taking the manual destination entry.
    	String qname = destFactory.generateDestination(getThreadNum());  	
    	
    	Log.logger.log(Level.INFO, "Opening {0}", qname);
		int mqoo = CMQC.MQOO_OUTPUT | CMQC.MQOO_INPUT_SHARED | CMQC.MQOO_FAIL_IF_QUIESCING;
		if (Config.parms.getBoolean("bf")) {
			mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
		}
		queue = qm.accessQueue(qname, mqoo);
    }  
    
	public boolean oneIteration() throws Exception {
		queue.put( inMessage,pmo );
		
		if (transacted)
			qm.commit();
		
		queue.get( inMessage,gmo );
		
		if (transacted) 
			qm.commit();
		
		incIterations();
		
		return true;
	}

}
