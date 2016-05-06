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
 * Send messages to a Topic.
 * 
 * TODO: this is incomplete and not ready for use
 */
/**
 * @author ElliotGregory
 *
 */
public class Publisher extends AMQPWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	
    protected MessageContainer outMessage = null;
    protected String correlID = null;
    
    /**
     * 
     */
    public static void registerConfig() {
		Config.registerSelf( Publisher.class );
	}
    
    /**
     * @param name
     */
    public Publisher(String name) {
        super( name );
    }
    
    
    /**
     * @throws Exception
     */
    protected void buildAMQPResources() throws Exception {
    	super.buildAMQPResources();
		outMessage = new MessageContainer (null);
    }
    
    
    /* (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    public void run() {
    	run( this );
    }
    
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		String topic = destFactory.generateDestination(getThreadNum());
		client.sendMessage("tbd", topic, outMessage);

		incIterations();
		return true;
	}
	
}
