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

import javax.jms.Destination;
import javax.jms.Message;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Takes messages off the request queue and places the same message on the reply queue.
 * Does not currently have an option to change the CorrelationId (in keeping with the
 * Requestor class).
 * @author Marc Carter, IBM 
 */
public final class Responder extends JMS11WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    Message inMessage = null;
    Message outMessage = null;
	private final boolean tempQueues 			= Config.parms.getString("oq").length() == 0;
	private final boolean copyReplyFromRequest = Config.parms.getBoolean( "cr" );
	private final boolean correlIDFromMsgID = Config.parms.getBoolean( "co" );
    private final boolean fixedReplyQ 			= Config.parms.getBoolean("jfq");
    
	public static void registerConfig() {
		Config.registerSelf( Responder.class );
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public Responder(String name) {
    	super(name);
    }

    protected void buildJMSResources() throws Exception {
    	super.buildJMSResources();
    	
        // Get destination pair if multiple are configured.
        int destID = destFactory.generateDestinationID(getThreadNum());
        String iq = Config.parms.getString("iq");
        String oq = Config.parms.getString("oq");
        if (destID >= 0) {
        	iq += String.valueOf(destID);
        	oq += String.valueOf(destID);
        }
        
        destConsumer = jmsProvider.lookupQueue( iq, session ).destination;
        if ( !tempQueues) {
        	destProducer = jmsProvider.lookupQueue( oq, session ).destination;
        }
        
        Log.logger.log(Level.FINE, "Creating receiver on {0}", getDestinationName( destConsumer ) );
        messageConsumer = session.createConsumer( destConsumer );

        Log.logger.log(Level.FINE, "Creating sender on {0}", getDestinationName( destProducer ) );
        messageProducer = session.createProducer( destProducer );    	
    }    
    
    public void run() {
    	run(this, null); // call superclass generic method.
    }
    
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public final boolean oneIteration() throws Exception {
		if ((inMessage = messageConsumer.receive(timeout)) != null) {
			// -cr
			outMessage = copyReplyFromRequest ? inMessage : msgFactory.createMessage(session, getName(), 0);
			
			// -co
			outMessage.setJMSCorrelationID(correlIDFromMsgID ? inMessage.getJMSMessageID() : inMessage.getJMSCorrelationID()); 
			
			if ( tempQueues) {
				Destination reply = inMessage.getJMSReplyTo();
				messageProducer.send(reply, outMessage, deliveryMode, priority, expiry);
			} else {
				//We can avoid the expensive Destination comparison, by using the fixed replyQ 
				if (!fixedReplyQ) {
					Destination reply = inMessage.getJMSReplyTo();
					if ((reply != null) && (!reply.equals(destProducer))) {
						// We keep the last producer cached, this code rebuilds it if the current request
						// needs to go somewhere different.  This code is therefore very inefficient when
						// multiple requestors specify JMSReplyToQueue
						messageProducer.close();
						destProducer = reply;
						messageProducer = session.createProducer(reply);
					}
				}
				messageProducer.send(outMessage, deliveryMode, priority, expiry);
			}
			if (transacted) session.commit();
			
			incIterations();			
		} else {
			//Old behaviour was to just return from this method and proceed to the next iteration, which is why it behaves
			//as if it ignores the time out. I will at least log an issue has occurred
	        Log.logger.log(Level.FINE, "No message received within timeout period");
		}
		return true;
	}
}
