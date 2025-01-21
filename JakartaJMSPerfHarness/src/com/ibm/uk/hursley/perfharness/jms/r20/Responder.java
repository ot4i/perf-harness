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
 * $Id: Responder.java 556 2013-09-27 15:17:24Z smassey $
 * JMSPerfHarness $Name$
 */

package com.ibm.uk.hursley.perfharness.jms.r20;

import java.util.logging.Level;

import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.Queue;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.jms.DestinationWrapper;

/**
 * Takes messages off the request queue and places the same message on the reply queue.
 * Does not currently have an option to change the CorrelationId (in keeping with the
 * Requestor class).
 * @author smassey@uk.ibm.com 
 */
public final class Responder extends JMS20WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    Message inMessage = null;
    Message outMessage = null;
	
    //Use temporary reply queues
	private final boolean tempQueues = Config.parms.getString("oq").length() == 0;
	
	//Use input message for output message, else crete new output message
	private final boolean copyReplyFromRequest = Config.parms.getBoolean("cr");
	
	//This is horrible using the same flag as requestor to mean different behaviour in the responder
	// -co requester - Use correlationID scheme
	// -co responder - Copy MsgID from input message to CorrelationID if set, else copy CorrelationID from input message
	private final boolean correlIDFromMsgID = Config.parms.getBoolean("co");
	
	//Use fixed reply queues, avoids extraction of replyTo and costly destination comparison
    private final boolean fixedReplyQ = Config.parms.getBoolean("jfq");
    
	public static void registerConfig() {
		Config.registerSelf(Responder.class);
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
        
    	DestinationWrapper<Queue> consumerDestinationWrapper = jmsProvider.lookupQueue(iq, context);
    	destConsumer = consumerDestinationWrapper.destination;
    	String consumerName = consumerDestinationWrapper.name;
        Log.logger.log(Level.FINE, "Creating receiver on {0}", consumerName);
        messageConsumer = context.createConsumer(destConsumer);

        if (!tempQueues) {
        	//We cant use a destination in our call to createProducer, but we need to cache the destination for use later
            DestinationWrapper<Queue> producerDestinationWrapper = jmsProvider.lookupQueue(oq, context);
        	destProducer = producerDestinationWrapper.destination;
        }
        //Can only create anonymous producers through JMS 2.0 simplified API
        Log.logger.log(Level.FINE, "Creating anonymous sender");
        messageProducer = context.createProducer();
		messageProducer.setDeliveryMode(deliveryMode);
		messageProducer.setPriority(priority);
		messageProducer.setTimeToLive(expiry);
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
			outMessage = copyReplyFromRequest ? inMessage : msgFactory.createMessage(context);
			
			// -co
			outMessage.setJMSCorrelationID(correlIDFromMsgID ? inMessage.getJMSMessageID() : inMessage.getJMSCorrelationID()); 
			
			if (tempQueues) {
				Destination reply = inMessage.getJMSReplyTo();
				messageProducer.send(reply, outMessage);
			} else {
				//We can avoid the expensive Destination comparison, by using the fixed replyQ 
				if (!fixedReplyQ) {
					Destination reply = inMessage.getJMSReplyTo();
					if ((reply != null) && (!reply.equals(destProducer))) {
						// We keep the last producer cached, this code rebuilds it if the current request
						// needs to go somewhere different.  This code is therefore very inefficient when
						// multiple requestors specify JMSReplyToQueue
						destProducer = reply;
						messageProducer = context.createProducer();
			    		messageProducer.setDeliveryMode(deliveryMode);
			    		messageProducer.setPriority(priority);
			    		messageProducer.setTimeToLive(expiry);
					}
				}
				messageProducer.send(destProducer, outMessage);
			}
			if (transacted) context.commit();
			
			incIterations();			
		} else {
			//Old behaviour was to just return from this method and proceed to the next iteration, which is why it behaves
			//as if it ignores the time out. I will at least log an issue has occurred
	        Log.logger.log(Level.FINE, "No message received within timeout period");
		}

		return true;
	}
}
