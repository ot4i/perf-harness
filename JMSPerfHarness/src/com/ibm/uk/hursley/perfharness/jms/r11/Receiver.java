/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.jms.r11;

import java.util.logging.Level;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Consumes messages only.  Currently this class, although JMS 1.1 compliant, is only coded to accept
 * Queue-domain messages.  Use the Subscriber class for topic-domain messages. 
 */
public class Receiver extends JMS11WorkerThread implements WorkerThread.Paceable, MessageListener {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    Message inMessage = null;
	   
    public static void registerConfig() {
		Config.registerSelf( Receiver.class );
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public Receiver(String name) {
        super( name );
    }

    protected void buildJMSResources() throws Exception {
    	
    	super.buildJMSResources();

        if ( destConsumer == null ) {
        	destConsumer = jmsProvider.lookupQueue( destFactory.generateDestination( getThreadNum() ), session ).destination;
        }
        
        String selector = null;
        Log.logger.log(Level.FINE,"Creating receiver on {0} selector:{1}", new Object[] { getDestinationName(destConsumer), selector });
        
        messageConsumer = session.createConsumer( destConsumer, selector );
    	
    }
    
    public void run() {
    	
    	MessageListener ml = Config.parms.getBoolean("as")?this:null;
    	run( this, ml ); // call superclass generic method.
        
    } // End public void run()

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		startResponseTimePeriod();
		if( (inMessage=messageConsumer.receive( timeout ))!=null ) {
			if ( transacted && (getIterations()+1)%commitCount==0 ) session.commit();
			incIterations();
		}
		
		return true;
	}

	/* (non-Javadoc)
	 * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
	 */
	public void onMessage(Message arg0) {
		if ( transacted && (getIterations()+1)%commitCount==0 ) {
			try {
				session.commit();
			} catch (JMSException je) {
				handleException(je);
			}
		}
		incIterations();		
	}
	
}
