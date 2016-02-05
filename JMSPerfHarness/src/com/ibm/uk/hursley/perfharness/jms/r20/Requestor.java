/********************************************************* {COPYRIGHT-TOP} ***
* Licensed Materials - Property of IBM
*
* IBM Performance Harness for Java Message Service
*
* (C) Copyright IBM Corp. 2005, 2007  All Rights Reserved.
*
* US Government Users Restricted Rights - Use, duplication, or
* disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
********************************************************** {COPYRIGHT-END} **/
/*
 * $Id: Requestor.java 548 2012-11-19 13:44:43Z gezagel $
 * JMSPerfHarness $Name$
 */

package com.ibm.uk.hursley.perfharness.jms.r20;

import java.util.logging.Level;

//import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.jms.DestinationWrapper;

/**
 * Puts a message to a queue then waits for a reply on another permanent or temporary queue.  Currently
 * the only use of correlation identifiers is to keep using the same id for every
 * request (ie NOT to use the sent messageId of each request). This is much faster
 * for JMS applications.
 * 
 * @author smassey@uk.ibm.com 
 */
public final class Requestor extends JMS20WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	protected Message inMessage = null;
    protected Message outMessage = null;
    protected String correlID = null;    
	
	private static boolean tempQueues;
	private static boolean tempQueuePerMessage;

	public static void registerConfig() {
		Config.registerSelf(Requestor.class);
		
		//Use temporary queues if output queue (from application perspective) is not set
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
        outMessage = msgFactory.createMessage(context);
		
        // Set correlationID for this thread onto the cached message
        if ( Config.parms.getBoolean("co") ) {
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
        DestinationWrapper<Queue> producerDestinationWrapper = jmsProvider.lookupQueue(iq, context);
    	destProducer = producerDestinationWrapper.destination;
    	String producerName = producerDestinationWrapper.name;
    	
        Log.logger.log( Level.FINE, "Creating sender on {0}", producerName);
        messageProducer = context.createProducer();
		messageProducer.setDeliveryMode(deliveryMode);
		messageProducer.setPriority(priority);
		messageProducer.setTimeToLive(expiry);
		
        String selector = null;
        if (correlID != null) {
			StringBuffer sb = new StringBuffer("JMSCorrelationID='");
			sb.append( correlID );
			sb.append("'");
			selector = sb.toString();
		}

        if (!tempQueues) { 
        	//Use permanent queues
        	DestinationWrapper<Queue> consumerDestinationWrapper = jmsProvider.lookupQueue(oq, context);
        	destConsumer = consumerDestinationWrapper.destination;
        	String consumerName = consumerDestinationWrapper.name;
            Log.logger.log(Level.FINE, "Creating receiver on {0} selector:{1}", new Object[] {consumerName, selector});
            messageConsumer = context.createConsumer(destConsumer, selector);
        } else if (!tempQueuePerMessage) {
        	//Use 1 temporary queue per session
        	destConsumer = context.createTemporaryQueue();
            Log.logger.log(Level.FINE,"Creating receiver on temporary queue per thread with selector:{1}", selector);
            messageConsumer = context.createConsumer(destConsumer, selector);
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
			// Close existing temporary queue
			if (messageConsumer != null) messageConsumer.close();
			if (destConsumer != null) {
				((TemporaryQueue) destConsumer).delete();
			}
			
			// Open new temporary queue
			destConsumer = context.createTemporaryQueue();
			messageConsumer = context.createConsumer(destConsumer);
			outMessage.setJMSReplyTo(destConsumer);
		}
		
		messageProducer.send(destProducer, outMessage);				
		if ( transacted ) context.commit();
		
		if ((inMessage = messageConsumer.receive(timeout)) != null) {
			if ( transacted ) context.commit();
			incIterations();
		} else {
			throw new Exception("No response to message (\nID: " + outMessage.getJMSMessageID() + "\nCorrID: " + outMessage.getJMSCorrelationID() +" )");
		}
		return true;
	}
	
}
