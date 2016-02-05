/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/


package com.ibm.uk.hursley.perfharness.jms.r11;

import java.util.Random;
import java.util.logging.Level;

import javax.jms.Message;
import javax.jms.Queue;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Sends a message then receives one from the same queue.  Normal use is with
 * correlation identifier to ensure the same message is received.
 * @author Marc Carter, IBM 
 */
public final class PutGet extends JMS11WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    Message inMessage = null;
    Message outMessage = null;
    String correlID = null;

    private static boolean useUnidentifiedProducers = false;
    
    public static void registerConfig() {
		Config.registerSelf( PutGet.class );
		useUnidentifiedProducers = Config.parms.getBoolean("up");
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
        if (destProducer == null) {
        	destProducer = jmsProvider.lookupQueue( destFactory.generateDestination( getThreadNum() ), session ).destination;
        }
        
        outMessage = msgFactory.createMessage( session, getName(), 0 );
    
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
        	if (Config.parms.getBoolean( "co" )) {
        		correlID = msgFactory.setJMSCorrelationID(this, outMessage);
        	}
        	
            if (correlID != null) {
    			StringBuffer sb = new StringBuffer("JMSCorrelationID='");
    			sb.append(correlID);
    			sb.append("'");
    			selector = sb.toString();
    		}
        }
        
        String destName = getDestinationName( destProducer );
        Log.logger.log(Level.FINE, "Creating receiver on {0} selector:{1}", new Object[] {destName, selector});
        System.out.println("Creating receiver on " + destName + " with selector: " + selector);
        messageConsumer = session.createConsumer((Queue)destProducer, selector);

        if (useUnidentifiedProducers) {
    		// Use unidentified producers
        	messageProducer = session.createProducer(null);
    	} else {
	        Log.logger.log(Level.FINE, "Creating sender on {0}", destName );
	        messageProducer = session.createProducer((Queue)destProducer );
    	}
	}

    public void run() {
        run(this, null);  // call superclass generic method.
    } // End public void run()
    
    /**
     * Send a message to one queue then get it back again. 
     */
	public final boolean oneIteration() throws Exception {
        if (useUnidentifiedProducers) {
    		// Use unidentified producers
        	messageProducer.send(destProducer, outMessage, deliveryMode, priority, expiry);
        } else {
        	messageProducer.send(outMessage, deliveryMode, priority, expiry);
        }
		if (transacted) session.commit();

		if ((inMessage = messageConsumer.receive(timeout)) != null) {
			// these three items should be an atomic operation!
			if (transacted) session.commit();
			incIterations();
		} else {
			throw new Exception("No response to message (" + outMessage.getJMSMessageID() + ")");
		}
		return true;
	}
}
