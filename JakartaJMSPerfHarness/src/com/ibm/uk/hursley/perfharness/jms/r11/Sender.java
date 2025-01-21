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

import jakarta.jms.Message;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Send messages to a Queue.
 */
public class Sender extends JMS11WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    protected Message outMessage = null;
    protected String correlID = null;
		
    public static void registerConfig() {
		Config.registerSelf( Sender.class );
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public Sender(String name) {
        super( name );
    }

    protected void buildJMSResources() throws Exception {
    	
    	super.buildJMSResources();

		outMessage=msgFactory.createMessage( session, getName(), 0 );
		
        if ( Config.parms.getBoolean( "co" ) ) {
        	correlID = msgFactory.setJMSCorrelationID( this, outMessage );
        }    
    	
        // Open queues
        if ( destProducer == null ) {
        	destProducer = jmsProvider.lookupQueue( destFactory.generateDestination( getThreadNum() ), session ).destination;
        }
        
        Log.logger.log(Level.FINE, "Creating sender on "
                + getDestinationName(destProducer)
                + (correlID == null ? "" : (" with correlId " + correlID)));
        messageProducer = session.createProducer( destProducer ); 
        
    	String rq = Config.parms.getString("rq");
    	if (rq != null && !rq.equals("")) {
    		//Set the reply to field even though we don't get these messages
    		outMessage.setJMSReplyTo(jmsProvider.lookupQueue( rq, session ).destination);
    	}

        
    }    
    
    public void run() {

        run( this, null );  // call superclass generic method.
        
    } // End public void run()

    
    
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		startResponseTimePeriod();
		messageProducer.send( outMessage, deliveryMode, priority, expiry );				
		
		if ( transacted && (getIterations()+1)%commitCount==0 ) {
		   if(commitDelay > 0) {
		      if(commitDelayMsg){
		         Log.logger.log(Level.INFO, "Delaying " + (commitDelay) + " milliseconds before each commit");
			  }
			  Thread.sleep(commitDelay);
		   }
	       session.commit();
		}
		incIterations();
		
		return true;
	}
}
