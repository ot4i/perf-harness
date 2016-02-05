/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.jms.r11;

import java.util.ArrayList;
import java.util.logging.Level;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Topic;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.jms.providers.JNDI;

/**
 * Subscribe to Topic-domain messages.
 */
public class Subscriber extends JMS11WorkerThread implements WorkerThread.Paceable, MessageListener {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	// Message Received
	Message inMessage = null;

	// Selector String
	String selector = null;   

	public static void registerConfig() {
		Config.registerSelf( Subscriber.class );
		System.setProperty( JNDI.PSMODE, JNDI.PSMODE_SUB);
	}    

	/**
	 * Constructor for JMSClientThread.
	 * @param name
	 */
	public Subscriber(String name) {
		super( name );
	}

	protected void buildJMSResources() throws Exception {

		super.buildJMSResources();

		// Open topics
		if ( destConsumer==null ) {
			destConsumer = jmsProvider.lookupTopic( destFactory.generateDestination( getThreadNum() ), session ).destination;
		}

		/**
		 * Selectors Branch
		 * 
		 * Parameters.
		 * 
		 *  "-sl" Defines the selector increment type. 'auto' value means the selector 
		 *  is incremented automatically. 'manual' mode means the selector ID is taken from
		 *  the parameter "-sm" (see below). 'Auto' can only be used when Subscribers are 
		 *  in same JVM. 'false' disables Selectors (default).
		 *  Example: "-sl auto" will mean the selector ID is automatically created.
		 *  
		 *  "-sn" Required. Defines the number of different selector IDs.
		 *  Example: "-sn 10" means there will be 10 different selectors from 0..9
		 *  
		 *  "-sm" Required when in manual mode. The ID is input from this parameter.
		 *  Example: "-sm 2" means the selector will be "stringproperty2".
		 *  
		 *  "-se" Optional. Defines the number of selector matches the Subscriber will hit.
		 *  Example: "-se 3" means the subscriber will match 3 messages. A value of
		 *  0 means that it's turned off and each subscriber will hit 1 message each.
		 *  
		 *  
		 *  Example Uses
		 *  
		 *  '-sl auto -sn 10'
		 *  10 different Selectors ID, automatically created. Each Subscriber has one
		 *  selector ID.
		 *  
		 *  '-sl manual -sn 10 -sm 2'
		 *  10 Different Selector IDs. This subscriber will have selector to match
		 *  "stringproperty2".
		 *  
		 *  '-sl manual -sn 10 -sm 2 -se 2'
		 *  10 Different Selector IDs. This subscriber will have selector to match
		 *  "stringproperty2 OR stringproperty3".
		 */

		if (Config.parms.containsKey("sl") && !(Config.parms.getString("sl").equals("false"))) {
			if (Config.parms.containsKey("sn")) {

				// Use a String Based Selector
				Log.logger.log(Level.FINE,"Using String Properties Based Selection");

				// Number of Selectors to Use
				int numberSelectors = Config.parms.getInt("sn");

				// Check to see the number of selectors is valid
				if (numberSelectors > 0) {

					/**
					 *  Manual mode. The selector ID number is taken from the parameter 'sm'.
					 *  Needed for when Subscribers are on different JVM's.
					 */
					if (Config.parms.getString("sl").equals("manual")) {

						Log.logger.log(Level.FINE, "Manual Selector Mode");

						if (Config.parms.containsKey("sm")) {	

							// Use supplied value for Selector ID
							int selectorID = Config.parms.getInt("sm");
							
							if (selectorID >= 0) {
				
								// Multiple Selector per Consumer function
								if (Config.parms.containsKey("se") && Config.parms.getInt("se") > 0) {
									Log.logger.log(Level.FINE, "Using Multiple Selectors. Each Subscriber will match {0} selectors.", Config.parms.getInt("se"));
									selector = multipleSelectorString(selectorID, Config.parms.getInt("se"), numberSelectors);
										
								}
								else if (Config.parms.getInt("se") > numberSelectors) {
									Log.logger.log(Level.FINE, "-se must have a value less than the total number of different Selectors. " +
											"We will default to each Subscriber having a selector to match all messages.");
									selector = multipleSelectorString(selectorID, numberSelectors, numberSelectors);
								}
								else if (Config.parms.getInt("se") <= 0) {
									Log.logger.log(Level.FINE, "-se must have a value of 1 or greater. We will default to each subscriber" +
											"matching 1 selector.");
									selector = singleSelectorString(selectorID);
									
								}
								else {
									Log.logger.log(Level.FINE, "Each Subscriber will match 1 selector." );
									selector = singleSelectorString(selectorID);
								}
							}
						}
						else {
							Log.logger.log(Level.FINE, "-sm Parameter needs to be defined when in manual mode." );
						}
					}


					/**
					 *  Automatic mode, the selector is created automatically and incremented.
					 *  Used when Subscribers are on the same JVM.
					 */
					else {
						Log.logger.log(Level.FINE, "Automatic Selector Mode");
						
						// Get Singleton Instance and next Number ID
						NumberSequencer numberSequencer;
						numberSequencer = NumberSequencer.getInstance(numberSelectors);
						int nextNumber = numberSequencer.nextNumber();

						// Multiple Selector per Consumer function
						if (Config.parms.containsKey("se") && Config.parms.getInt("se") > 0) {
								Log.logger.log(Level.FINE, "Using Multiple Selectors. Each Subscriber will match {0} selectors.", Config.parms.getInt("se"));
								selector = multipleSelectorString(nextNumber, Config.parms.getInt("se"), numberSelectors);
								
						}
						else if (Config.parms.getInt("se") > numberSelectors) {
							Log.logger.log(Level.FINE, "-se must have a value less than the total number of different Selectors.");
						}
						else if (Config.parms.getInt("se") <= 0) {
							Log.logger.log(Level.FINE, "-se must have a value of 1 or greater.");
						}
						else {
							Log.logger.log(Level.FINE, "Each Subscriber will match 1 selector." );
							selector = singleSelectorString(nextNumber);
						}
					}
				}
			}
			else {
				Log.logger.log(Level.FINE, "-sn Must be Specified when using Selectors");
			}
		}

		
		// Finally create Subscriber
		if ( durable ) {
			durableSubscriberName = makeDurableSubscriberName( connection, this );
			messageConsumer = session.createDurableSubscriber( (Topic)destConsumer, durableSubscriberName, selector, false );
		} 
		else if (Config.parms.containsKey("sf") && !(Config.parms.getString("sf").equals("false"))) {
			selector = Config.parms.getString("sf");
			System.out.println(selector);
			messageConsumer = session.createConsumer( destConsumer, selector, false );
		}
		else {
			messageConsumer = session.createConsumer( destConsumer, selector, false );
			
		}

		String destName = getDestinationName( destConsumer );
		Log.logger.log(Level.FINE, "Creating receiver on {0} selector:{1}", new Object[] { destName, selector} );
		
		// Check to see if we are being asked to support multiple messageConsumers per Subscriber
		int consumersPerSubscriber = Config.parms.getInt("ns");
		if ( consumersPerSubscriber > 1 ) {
		
			// We only support multiple messageConsumers per Subscriber in the async non-durable case
			if ( durable ||  ( Config.parms.getBoolean("as") == false ) ) { 
				Config.logger.warning("-ns > 1 is not supported for durable or syncronous subscribers, the parameter will be ignored ");
			}	
			else {
			
				// the first messageConsumer has already been created above and the listener is
				// set in the WorkerThread
				consumersPerSubscriber = consumersPerSubscriber-1;
		
				// lookup a topic and create a consumer for it 
				additionalMessageConsumers = new ArrayList<MessageConsumer>(consumersPerSubscriber);
				for (int i = 0; i < consumersPerSubscriber; i++) {
					destConsumer = jmsProvider.lookupTopic( destFactory.generateDestination( getThreadNum() ), session ).destination;
					additionalMessageConsumers.add( session.createConsumer( destConsumer, selector, false ));
					destName = getDestinationName( destConsumer );
					Log.logger.log(Level.FINE, "Creating receiver on {0} selector:{1}", new Object[] { destName, selector} );
				}

				// after all the message consumers are created we can set the listeners
				// don't move this up into loop above as once a listener is set you can't 
				// call other synchronous methods on the class
				// note that all subscribers on this connection share a single listener 
				for (int i = 0; i < consumersPerSubscriber; i++) {
					additionalMessageConsumers.get(i).setMessageListener(this);
				}
			}
		}
		
	}

	public void run() {

		MessageListener ml = Config.parms.getBoolean("as")?this:null;
		run( this, ml ); // call superclass generic method.

	} // End public void run()

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {

		if( (inMessage=messageConsumer.receive( timeout ))!=null ) {
			if ( transacted && (getIterations()+1)%commitCount==0 ) session.commit();
			incIterations();
		} else {
			//throw new Exception( "No message available" );
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

	
	/**
	 * This function creates the selector string when more than one selector is needed
	 * for a subscriber.
	 * 
	 * @param currentNumber - Current Selector ID to be used next.
	 * @param individualTotal - The total Number of Selectors the Subscriber needs
	 * @param totalNumber - The total Number of Subscribers used in the test
	 * @return Selector String
	 */
	private String multipleSelectorString(int currentNumber, int individualTotal, int totalNumber) {
		
		NumberSequencer tmpSequencer = new NumberSequencer(totalNumber);
		tmpSequencer.setCurrentNumber(currentNumber);
		StringBuilder selectorString = new StringBuilder();

		for (int i = 0; i < individualTotal ; i++) {
			selectorString.append(singleSelectorString(tmpSequencer.nextNumber()));
			if (i > 0) {
				selectorString.append(" OR ");
			}
		}

		return selectorString.toString();
	}
	
	/**
	 * 
	 * @param int - ID to be added at end of Selector String
	 * @return String with single Selector
	 */
	private String singleSelectorString(int id) {
		return "PerfStringProp = 'stringproperty" + id + "'";
	}
}
