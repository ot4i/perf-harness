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
import jakarta.jms.Queue;
import jakarta.jms.TemporaryQueue;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.jms.DestinationWrapper;

/**
 * Puts a message to a queue then waits for a reply on another queue.  Currently
 * the only use of correlation idenfiers is to keep using the same id for every
 * request (ie NOT to use the sent messageId of each request). This is much faster
 * for JMS applications. 
 */
public final class Requestor extends JMS11WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	protected Message inMessage = null;
    protected Message outMessage = null;
    protected String correlID = null;    
	
	private static boolean tempQueues;
	private static boolean tempQueuePerMessage;

	public static void registerConfig() {
		Config.registerSelf( Requestor.class );		
		//We use temporary queues if no specific output queue specified
		tempQueues = Config.parms.getString("oq").length() == 0;
		
		//The current default is to use a temp queue per message
		//Its more performant to cache a temporary queue per thread/session
		tempQueuePerMessage = Config.parms.getBoolean("tqpm");
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public Requestor(String name) {
        super(name);
    }

    protected void buildJMSResources() throws Exception {
    	super.buildJMSResources();
    	
        // Create message
		outMessage = msgFactory.createMessage(session, getName(), 0);
		
        // Set correlationID for this thread onto the cached message
        if (Config.parms.getBoolean("co") ) {
        	correlID = msgFactory.setJMSCorrelationID(this, outMessage);
        }            
        
        // Get destination pair if multiple are configured.
        int destID = destFactory.generateDestinationID(getThreadNum());
        String iq = Config.parms.getString("iq");
        String oq = Config.parms.getString("oq");
        if (destID >= 0) {
        	iq += String.valueOf(destID);
        	oq += String.valueOf(destID);
        }
      
        // Open queues
        destProducer = jmsProvider.lookupQueue(iq, session).destination;
        Log.logger.log( Level.FINE, "Creating sender on {0}", getDestinationName(destProducer));
        messageProducer = session.createProducer(destProducer);
        
        String selector = null;
        if (correlID != null) {
			StringBuffer sb = new StringBuffer("JMSCorrelationID='");
			sb.append(correlID);
			sb.append("'");
			selector = sb.toString();
		}

        if (!tempQueues) {
        	//Use permanent queues
        	DestinationWrapper<Queue> consumerDestinationWrapper = jmsProvider.lookupQueue(oq, session);
        	destConsumer = consumerDestinationWrapper.destination;
        	String consumerDestinationName = consumerDestinationWrapper.name;
            Log.logger.log(Level.FINE, "Creating receiver on {0} selector:{1}", new Object[] {consumerDestinationName, selector});
            messageConsumer = session.createConsumer(destConsumer, selector);
        } else if (!tempQueuePerMessage) {
        	Log.logger.info("Using temporary reply queue per session");    	
        	//Use 1 temporary queue per session
        	destConsumer = session.createTemporaryQueue();
            Log.logger.log(Level.FINE, "Creating receiver on {0} temporary queue per thread", getDestinationName(destConsumer));
            messageConsumer = session.createConsumer(destConsumer);
        } else {
        	Log.logger.log(Level.FINE, "Using temporary reply queue per message");    	
        }
		//Set the reply to field to be the output queue name
		outMessage.setJMSReplyTo(destConsumer);
    }
    
    public void run() {
        run(this, null); // call superclass generic method.
    } 
    
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public final boolean oneIteration() throws Exception {
		if ((tempQueues) && (tempQueuePerMessage)) {
			// Close temporary queue
			if (messageConsumer != null) messageConsumer.close();
			if (destConsumer != null) {
				((TemporaryQueue) destConsumer).delete();
			}
			
			// Open new temporary queue
			destConsumer = session.createTemporaryQueue();
			messageConsumer = session.createConsumer(destConsumer);
			outMessage.setJMSReplyTo(destConsumer);
		}
		
		startResponseTimePeriod();
		messageProducer.send(outMessage, deliveryMode, priority, expiry);				
		if (transacted) session.commit();
		
		if ((inMessage = messageConsumer.receive(timeout))!= null) {
			if (transacted) session.commit();
			incIterations();
		} else {
			throw new Exception("No response to message (\nID: "+outMessage.getJMSMessageID()+ "\nCorrId: " + outMessage.getJMSCorrelationID() +")");
		}
		return true;
	}
	
}
