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
 * $Id: PutGet.java 516 2010-12-20 10:28:14Z fentono $
 * JMSPerfHarness $Name$
 */

package com.ibm.uk.hursley.perfharness.jms.r20;

import java.util.Random;
import java.util.logging.Level;

import javax.jms.Message;
import javax.jms.Queue;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.jms.DestinationWrapper;

/**
 * Sends a message then receives one from the same queue.  Normal use is with
 * correlation identifier to ensure the same message is received.
 * @author smassey@uk.ibm.com 
 */

public final class PutGet extends JMS20WorkerThread implements WorkerThread.Paceable {
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    Message inMessage = null;
    Message outMessage = null;
    String correlID = null;
    
    public static void registerConfig() {
		Config.registerSelf(PutGet.class);
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public PutGet(String name) {
        super(name);
    }

	protected void buildJMSResources() throws Exception {
		super.buildJMSResources();
			
        // Open queues
        DestinationWrapper<Queue> producerDestinationWrapper = jmsProvider.lookupQueue(destFactory.generateDestination(getThreadNum()), context);
    	destProducer = producerDestinationWrapper.destination;
    	String destName = producerDestinationWrapper.name;
        outMessage = msgFactory.createMessage(context);
    
        String selector = null;
        if (Config.parms.containsKey("sl") && Config.parms.getString("sl").equals("true")) {
        	// Use a String Based Selector
        	Random random = new Random();
        	int randomNumber = random.nextInt(Integer.MAX_VALUE);                	

        	Log.logger.log(Level.FINE, "Using String Properties Based Selection");
        	outMessage.setStringProperty("PerfStringProp", ("randomstringproperty" + randomNumber));        	
        	selector = "PerfStringProp = '" + outMessage.getStringProperty("PerfStringProp") + "'";
        } else {
            // Use CorrelID Based Selector
        	if (Config.parms.getBoolean("co")) {
        		correlID = msgFactory.setJMSCorrelationID(this, outMessage);
        	}
            if (correlID != null) {
    			StringBuffer sb = new StringBuffer("JMSCorrelationID='");
    			sb.append(correlID);
    			sb.append("'");
    			selector = sb.toString();
    		}
        }
        
        Log.logger.log(Level.FINE, "Creating receiver on {0} selector:{1}", new Object[] {destName, selector} );
        System.out.println("Creating receiver on " + destName + " with selector: " + selector);
        messageConsumer = context.createConsumer(destProducer, selector);

        Log.logger.log(Level.FINE, "Creating sender on {0}", destName);
        messageProducer = context.createProducer();
		messageProducer.setDeliveryMode(deliveryMode);
		messageProducer.setPriority(priority);
		messageProducer.setTimeToLive(expiry);
	}

    public void run() {
        run(this, null);  // call superclass generic method.
    }
    
    /**
     * Send a message to one queue then get it back again. 
     */
	public final boolean oneIteration() throws Exception {
		messageProducer.send(destProducer, outMessage);				
		if (transacted) context.commit();

		if ((inMessage = messageConsumer.receive(timeout)) != null) {
			if (transacted) context.commit();
			incIterations();
		} else {
			throw new Exception("No response to message (\nID: " + outMessage.getJMSMessageID() + "\nCorrID: " + outMessage.getJMSCorrelationID() +" )");
		}
		return true;
	}
}
