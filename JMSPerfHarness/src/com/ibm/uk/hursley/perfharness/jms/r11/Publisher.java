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

import javax.jms.Message;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.jms.providers.JNDI;

/**
 * Send messages to a Topic.
 */
public class Publisher extends JMS11WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    protected Message outMessage = null;
    protected String correlID = null;
    
    private boolean multipleSelectors;
    
    protected Message[] messages;
    
    private NumberSequencer numberSequencer;
	
    public static void registerConfig() {
		Config.registerSelf( Publisher.class );
		System.setProperty( JNDI.PSMODE, JNDI.PSMODE_PUB);
	}
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public Publisher(String name) {
        super( name );
        multipleSelectors = false;
    }
    
    
    protected void buildJMSResources() throws Exception {
    	
    	super.buildJMSResources();
    	
        // Open topics
        if ( destProducer == null ) {
        	destProducer = jmsProvider.lookupTopic( destFactory.generateDestination( getThreadNum() ), session ).destination;
        }
        
        
        // Logger Text        
        Log.logger.log(Level.FINE, "Creating publisher on " + getDestinationName(destProducer));
        
        // Correlation ID
        if ( Config.parms.getBoolean( "co" ) ) {
        	correlID = msgFactory.setJMSCorrelationID( this, outMessage );
        	Log.logger.log(Level.FINE, "Publisher is using correlID: " + correlID);
        }
        
        
        /**
    	 * Selectors Branch
    	 * 
    	 * Parameters.
    	 * 
    	 *  "-sl" Turns on selectors. 
    	 *  Example: "-sl true" will mean turns Selectors on.
    	 *  
    	 *  "-sn" Required. Defines the number of different selector IDs. The ID is put in
    	 *  message property "PerfStringProp".
    	 *  Example: "-sn 10" means there will be 10 types different message with a property
    	 *  value of "stringpropertyN"  when N is a value from 0..9
    	 */
        if (Config.parms.containsKey("sl") && Config.parms.getString("sl").equals("true")) {

        	Log.logger.log(Level.FINE, "Using String based Selectors.");

        	// Multiple Selectors Branch
        	if (Config.parms.containsKey("sn")) {

        		int numberMessages = Config.parms.getInt("sn");			// Number of different Selectors
        		if (numberMessages > 0) {
        		
        			messages = new Message[numberMessages];					// Collection to hold different Messages 
        			multipleSelectors = true;
        			numberSequencer = new NumberSequencer(numberMessages);	// NumberSequencer to iterate numbers.

        			Log.logger.log(Level.FINE, "Message Property \"PerfStringProp\" will have values: stringproperty0 ... stringproperty{0}", (numberMessages-1));

        			// Create the different messages and add them to collection.
        			for (int i = 0 ; i < numberMessages ; i++) {
        				Message msg = msgFactory.createMessage( session, getName(), 0 );
        				msg.setStringProperty("PerfStringProp", ("stringproperty" + i));
        				messages[i] = msg;
        			}
        		}
        		else {
        			Log.logger.log(Level.FINE, "-sn Must have a value of 1 or greater");
        		}
        	}
        	else {
        		Log.logger.log(Level.FINE, "-sn Must be Specified when using Selectors");
        	}
        }
		
		// Non-Selector Branch
		else {
			outMessage = msgFactory.createMessage( session, getName(), 0 );
		}      
        messageProducer = session.createProducer( destProducer ); 
    }
    
    
    public void run() {

    	run( this, null ); // call superclass generic method.
    	
    } // End public void run()
    
    /**
     * Publish a single message.
     */
	public boolean oneIteration() throws Exception {
		
		if (multipleSelectors) {
			outMessage = messages[numberSequencer.nextNumber()];
		}
		
		messageProducer.send( outMessage, deliveryMode, priority, expiry );
		if ( transacted && (getIterations()+1)%commitCount==0 ) session.commit();
		incIterations();
		return true;
	}
	
}
