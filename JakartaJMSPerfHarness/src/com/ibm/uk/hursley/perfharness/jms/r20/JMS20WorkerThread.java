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
 * $Id: JMS11WorkerThread.java 556 2013-09-27 15:17:24Z smassey $
 * JMSPerfHarness $Name$
 */
package com.ibm.uk.hursley.perfharness.jms.r20;

import java.util.ArrayList;
import java.util.logging.Level;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.DestinationFactory;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.jms.DefaultMessageFactory;
import com.ibm.uk.hursley.perfharness.jms.MessageFactory;
import com.ibm.uk.hursley.perfharness.jms.providers.AbstractJMSProvider;
import com.ibm.uk.hursley.perfharness.jms.providers.JMSProvider;

/**
 * Provides a service layer for the lifecycle of all JMS 2.0 WorkerThreads.
 * Handles destinations, connection factories, connections and exceptions.
 * 
 * The classic JMS 1.1 API will remain indefinitely and can be used in deference to the 2.0 support
 * The simplified JMS 2.0 API will be implemented here for those wishing to use the additional features
 * 
 * From a performance/measurement perspective the main change in 2.0 is the support
 * of asynchronous message delivery
 * 
 * Access to the message body is now provided without having to cast it to an appropriate subtype
 * 
 * There are some changes and restrictions when utilising the JMS API from within a J2EE environment
 * 
 * MessageConsumers for durable topic subscriptions can now be created
 * 
 * Most of the other 2.0 changes have been made to increase usability with the implementation
 * of sensible defaults to common JMS infrastructure code
 * 
 * @author smassey@uk.ibm.com
 */
public abstract class JMS20WorkerThread extends WorkerThread {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;

	// The JMS provider instance for this class.
    protected static JMSProvider jmsProvider;
	protected static DestinationFactory destFactory;
	protected static JMSContext masterContext = null;

	//JMS Resources in use by this thread
	protected ConnectionFactory cf = null;
	protected JMSContext context = null;
    protected MessageFactory msgFactory = null;
	protected JMSProducer messageProducer = null;
    protected JMSConsumer messageConsumer = null;
    protected ArrayList<MessageConsumer> additionalMessageConsumers = null;
    protected Destination destProducer = null;
    protected Destination destConsumer = null;
    protected String durableSubscriberName = null;

    // Cache config settings
    protected final boolean transacted = 	Config.parms.getBoolean("tx");
    protected final long 	timeout = 		Config.parms.getInt("to") * 1000;
    protected final int 	deliveryMode = 	Config.parms.getBoolean("pp") ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
    protected final boolean durable = 		Config.parms.getBoolean("du");
    protected final int 	commitCount = 	Config.parms.getInt("cc");
    protected final int 	expiry = 		Config.parms.getInt("ex");
    protected final int 	priority = 		Config.parms.getInt("pr");
	protected final int 	ackMode = 		Config.parms.getInt("am");
	
    // Config controlling test run and exception logging
    protected boolean ignoreExceptions = false;
    protected boolean done = false;
    
    private static volatile boolean connectionInitialised = false;
    
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
    public static void registerConfig() {
		//Ensure JMSProvider and DefaultMessageFactory configuration is loaded 
		AbstractJMSProvider.registerConfig();
		DefaultMessageFactory.registerConfig();
		
		//Attempt to register the default destination factory if config is still valid
		if (!Config.isInvalid()) {
			Class<? extends Object> dfClazz = Config.parms.getClazz("df");
			Config.registerAnother( dfClazz );
			
			//Cache the JMS Provider in use
			jmsProvider = AbstractJMSProvider.getInstance();
		}
	
	}
	
	/**
	 * Creates new worker thread and sets thread name if provided, else will use a default from the thread itself
	 */
    protected JMS20WorkerThread(String name) {
		super(name);
		try {
			destFactory = Config.parms.<DestinationFactory>getClazz("df").newInstance();
			
			//Cache message factory
			msgFactory = DefaultMessageFactory.getInstance();
		} catch (Exception e) {
		    Log.logger.log(Level.SEVERE, "Problem getting DestinationFactory class {0}", e );
		}
	}
    
    synchronized void buildConnectionResources() throws Exception {
    	//If we find we need to configure the ConnectionFactory differently, then add new API to WebSphereMQ.java
       	Log.logger.log(Level.FINE, "Getting ConnectionFactory");
        cf = jmsProvider.lookupConnectionFactory(null);
        
        if (transacted) {
        	Log.logger.log(Level.FINE, "Using Transacted Mode");
        	masterContext = (JMSContext) cf.createContext(JMSContext.SESSION_TRANSACTED);
        } else {
        	Log.logger.log(Level.FINE, "Using Acknowledge Mode {0}", ackMode);
        	masterContext = (JMSContext) cf.createContext(ackMode);
        }
    	
    	connectionInitialised = true;
    }
    
	/**
	 * Creates and sets the JMS connection and session variables.
	 * @throws Exception
	 */
    protected void buildJMSResources() throws Exception {
    	destroyJMSResources(true);
    	if (!connectionInitialised) buildConnectionResources();
    	
    	//Build any JMS 2.0 thread resources here
        //Create the first JMSContext here, which can be used to create other JMSContexts for each thread
        if (transacted) {
        	Log.logger.log(Level.FINE, "Using Transacted Mode");
        	context = masterContext.createContext(JMSContext.SESSION_TRANSACTED);
        } else {
        	int ackMode = Config.parms.getInt("am");
        	Log.logger.log(Level.FINE, "Using Acknowledge Mode: {0}", ackMode);
        	context = masterContext.createContext(ackMode);
        }
    }
	
    /**
     * Attempts to safely shutdown all JMS objects.
     * <p>Also calls rollback() to back out any incomplete units-of-work. 
     * 
     * @param reconnecting Whether this method is being called in a reconnection scenario.
     * This affects the treatment of durable subscribers.
     */
    protected void destroyJMSResources(boolean reconnecting) {
    	// Delete any JMS 2.0 resources here
       	// Rollback any incomplete UOWs
       	if ((context != null) && transacted && (getIterations() != 0)) { 
       		try {
				context.rollback();
			} catch (JMSRuntimeException jmsre) {
	       		Log.logger.log(Level.FINE, "Exception received during context rollback: {0}", jmsre);
			}
       	}
       	
    	if (messageConsumer != null) {
    		if (!reconnecting) Log.logger.log(Level.FINE, "Closing consumer: {0}", messageConsumer);
            try {
                messageConsumer.close();
            } catch (JMSRuntimeException jmsre) {
	       		Log.logger.log(Level.FINE, "Exception received during messageConsumer close: {0}", jmsre);
            } finally {
                messageConsumer = null;
            }
        }
    	
    	// There is no close method on the lightweight producer, so we will just set the cached object to null
    	messageProducer = null;
    	
    	if (additionalMessageConsumers != null) {
 			for (int i = 0; i < additionalMessageConsumers.size(); i++) {
				MessageConsumer additionalMessageConsumer = additionalMessageConsumers.get(i);
	    		if (!reconnecting) Log.logger.log(Level.FINE, "Closing additional consumer: {0}", additionalMessageConsumer);
	            try {
	            	additionalMessageConsumer.close();
	            } catch (JMSException jmse) {
		       		Log.logger.log(Level.FINE, "Exception received during additional messageConsumer close: {0}", jmse);
	            } finally {
	            	additionalMessageConsumers.remove(i);
	            	additionalMessageConsumer = null;
	            }
			}
        } 	
   	
       	// If we are going to unsubscribe
       	if ((durableSubscriberName != null) && !reconnecting && (context != null) && (Config.parms.getBoolean("un"))) {
       		// note that we dont want to unsubscribe in disconnect cases
       		Log.logger.log(Level.FINE, "Unsubscribing {0}", durableSubscriberName);
           	try {
				context.unsubscribe(durableSubscriberName);
			} catch (JMSRuntimeException jmsre) {
	       		Log.logger.log(Level.FINE, "Exception received during durableSubscription unsubscribe: {0}", jmsre);
			}
       	}
       	
        if (context != null) {
        	if (!reconnecting) Log.logger.log(Level.FINE, "Closing context: {0}", context);
            try {
                context.close();
            } catch (JMSRuntimeException jmsre) {
	       		Log.logger.log(Level.FINE, "Exception received during context close: {0}", jmsre);
            } finally {
                context = null;
            }
        }
        
        if (!reconnecting) {
        	destProducer = null;
        	destConsumer = null;
        	cf = null;
        }
    }	


    /**
     * General implementation of the main body of a simple JMS primitive.
     * @param paceable A paceable instance of WorkerThread.
     * @param listener If not null, this overrides the above method and the given object
     * as an asynchronous listener. 
     */
    protected void run(WorkerThread.Paceable paceable, MessageListener listener) {
    	Log.logger.info("START");  
    	
    	while (!done && !shutdown) {
	        try {
	            // Connect to queuemanager
	            status = sCONNECTING;
	            buildJMSResources();
	            status = sRUNNING;
	            Log.logger.fine("Entering client loop");
	            if (listener != null) {
        			startTime = System.currentTimeMillis();
	            	
	            	// Use onMessage listener
	            	messageConsumer.setMessageListener(listener);
	            	
					waitForShutdownSignal();
	            } else {
		            // WorkerThread.pace handles all speed limiting etc.
		            pace(paceable);
	            }	            
	            done = true;
	        } catch (JMSException je) {
	        	if (ignoreExceptions) {
	        		Log.logger.fine("disconnected?");
	        	} else {
	        		handleException(je);
	        	}
	        } catch (Throwable e) {
				handleException(e);
	        } finally {
	        	if (done) {
	        		status = (status & sERROR)| sENDING;
	        		if (endTime == 0) {
	        			endTime = System.currentTimeMillis();
	        		}
		           	destroyJMSResources(false);
		           	Log.logger.info("STOP");
		           	status = (status & sERROR) | sENDED;
	        	}
	        }
    	} // end while !done
    	
    } // End public void run()
    
    /**
	 * Overloaded method to cross-link JDK 1.4 initCause and JMS 1.1
	 * linkedException if it has not already been done by the JMS vendor
	 * implementation.
	 * 
	 * @param je
	 */
    protected void handleException(JMSException je) {
    	//If thread not already stopped, set endTime
    	if (endTime == 0) {
    		endTime = System.currentTimeMillis();
    	}
		Exception le = je.getLinkedException();
		Throwable t = je.getCause();
		if ((null == t) && (null != le) && (t != le)) {
			je.initCause(le);
		}
		handleException((Exception) je);
	}

    /**
     * Log an exception.
     * @param e
     */
	protected void handleException(Throwable e) {
    	//If thread not already stopped, set endTime
    	if (endTime==0) {
    		endTime = System.currentTimeMillis();
    	}
    	
		Log.logger.log(Level.SEVERE, "Uncaught exception: {0}", e);
		status |= sERROR;
		ControlThread.signalShutdown();
		done = true;
	}	
}
