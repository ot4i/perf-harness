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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

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
 * Provides a service layer for the lifecycle of all JMS 1.1 WorkerThreads.
 * Handles destinations, connection factories, connections and exceptions.
 * 
 */
public abstract class JMS11WorkerThread extends WorkerThread {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;

	/**
	 * The JMS provider instance for this class.
	 */
    protected static JMSProvider jmsProvider;
    
	protected static DestinationFactory destFactory;
	
    // JMS interface classes
	protected ConnectionFactory cf = null;
	protected Connection connection = null;
    protected Session session = null;
    protected MessageProducer messageProducer = null;
    protected MessageConsumer messageConsumer = null;
    protected ArrayList<MessageConsumer> additionalMessageConsumers = null;
    protected Destination destProducer = null;
    protected Destination destConsumer = null;
    protected Destination tempQueue = null;
    protected MessageFactory msgFactory = null;
    
    // Cache config settings
    protected final boolean transacted = Config.parms.getBoolean( "tx" );
    protected final long timeout=Config.parms.getInt("to")*1000;
    protected final int deliveryMode = Config.parms.getBoolean("pp")?DeliveryMode.PERSISTENT:DeliveryMode.NON_PERSISTENT;
    protected final boolean durable = Config.parms.getBoolean( "du" );
    protected final int commitCount = Config.parms.getInt("cc");
    protected final int expiry = Config.parms.getInt("ex");
    protected final int priority = Config.parms.getInt("pr");
    protected final int commitDelay = Config.parms.getInt("cd");		
    protected final boolean commitDelayMsg = Config.parms.getBoolean("cdm");
    
    boolean ignoreExceptions = false;
    
	protected boolean done = false;
    protected String durableSubscriberName;

	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		
		AbstractJMSProvider.registerConfig();
		DefaultMessageFactory.registerConfig();
		
		if ( ! Config.isInvalid() ) {
			/*
			 * TODO: Java 8 support target type inference, so when moving to Java 8 can
			 * replace the following line with:
			 * Class<? extends DestinationFactory> dfClazz = Config.parms.<DestinationFactory>getClazz("df");
			 */
			final Class<?> dfClazz = Config.parms.getClazz("df");
			Config.registerAnother(dfClazz);
			jmsProvider = AbstractJMSProvider.getInstance();
		}
	}
    
    protected JMS11WorkerThread(String name) {
		super(name);
		try {
			/*
			 * TODO: Java 8 support target type inference, so when moving to Java 8 can
			 * replace the following lines with:
			 * destFactory = Config.parms.<DestinationFactory>getClazz("df").newInstance();
			 */
			final Class<?> dfClazz = Config.parms.getClazz("df");
			destFactory = (DestinationFactory) dfClazz.newInstance();
			msgFactory = DefaultMessageFactory.getInstance();
		} catch (Exception e) {
		    Log.logger.log( Level.SEVERE, "Problem getting DestinationFactory class", e );
		}
	}

	/**
	 * Creates and sets the JMS connection and session variables.
	 * @throws Exception
	 */
    protected void buildJMSResources() throws Exception {
    	
    	destroyJMSResources(true);
    	
        if ( cf==null ) {
        	Log.logger.log(Level.FINE, "Getting ConnectionFactory");
	        cf = jmsProvider.lookupConnectionFactory(null);
        }
    	
        Log.logger.log(Level.FINE, "Making connection" );
        connection = jmsProvider.getConnection( cf, this, String.valueOf( this.getThreadNum() ) );
        connection.start();
        Log.logger.log(Level.FINE, "Connection started {0}",connection );

        session = connection.createSession( transacted, Config.parms.getInt("am") ); // acknowledge
        Log.logger.log(Level.FINE, "Session started {0}",session );    	
	
    }
	
    /**
     * Attempts to safely shutdown all JMS objects.
     * <p>Also calls Session.rollback() to back out any
     * incomplete units-of-work.  This may look confusing to people....
     * @param reconnecting Whether this method is being called in a reconnection scenario.
     * This affects the treatment of durable subscribers.
     */
    protected void destroyJMSResources(boolean reconnecting) {
    	
       	// Rollback any incomplete UOWs
       	if ( session!=null && transacted && getIterations()!=0 ) { //&& iterations%commitCount!=0 ) {
       		Log.logger.log(Level.FINE, "Rolling back any current transactions." );
       		try {
				session.rollback();
			} catch (JMSException e1) {
			}
       	}

    	if (messageProducer != null) {
    		if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing producer {0}",messageProducer );
            try {
                messageProducer.close();
            } catch (JMSException e) {
            } finally {
                messageProducer = null;
            }
        }       	
       	
    	if (messageConsumer != null) {
    		if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing consumer {0}",messageConsumer );
            try {
                messageConsumer.close();
            } catch (JMSException e) {
            } finally {
                messageConsumer = null;
            }
        }
    	
    	if (additionalMessageConsumers != null) {
 			for (int i = 0; i < additionalMessageConsumers.size(); i++) {
				messageConsumer = additionalMessageConsumers.get(i);
	    		if ( !reconnecting )Log.logger.log(Level.FINE, "Closing consumer {0}",messageConsumer );
	            try {
	                messageConsumer.close();
	            } catch (JMSException e) {
	            } finally {
	                messageConsumer = null;
	            }
			}
        } 	
   	
       	// If we are going to unsubscribe
       	if ( durableSubscriberName!=null && !reconnecting && session != null && Config.parms.getBoolean( "un" ) ) {
       		// note that we dont want to unsubscribe in disconnect cases
       		Log.logger.log(Level.FINE, "Unsubscribing {0}",durableSubscriberName );
           	try {
				session.unsubscribe( durableSubscriberName );
			} catch (JMSException e) {
			}
       	}
       	
        if (session != null) {
        	if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing session {0}", session );
            try {
                session.close();
            } catch (JMSException e) {
            } finally {
                session = null;
            }
        }
        
        if (connection != null) {
        	if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing connection {0}",connection );
            try {
                connection.close();
            } catch (JMSException e) {
            } finally {
                connection = null;
            }
        }
        
        if ( ! reconnecting ) {
        	destProducer = null;
        	destConsumer = null;
        	cf = null;
        }
        
    }	

    /**
	 * Overloaded method to cross-link JDK 1.4 initCause and JMS 1.1
	 * linkedException if it has not already been done by the JMS vendor
	 * implementation.
	 * 
	 * @param je
	 */
    protected void handleException(JMSException je) {
		// Cross-link JDK 1.4 initCause and JMS 1.1 linkedexception if it has
		// not already been done by the JMS vendor implementation.
    	
    	if ( endTime==0 ) {
    		endTime = System.currentTimeMillis();
    	}
    	
		Exception le = je.getLinkedException();
		Throwable t = je.getCause();
		if (null==t && null!=le && t != le) {
			je.initCause(le);
		}
		handleException((Exception) je);
	}

    /**
     * Log an exception.
     * @param e
     */
	protected void handleException(Throwable e) {
		
    	if ( endTime==0 ) {
    		endTime = System.currentTimeMillis();
    	}
    	
		Log.logger.log(Level.SEVERE, "Uncaught exception.", e);
		status |= sERROR;
		ControlThread.signalShutdown();
		done = true;
	}

    /**
     * General implementation of the main body of a simple JMS primitive.
     * @param paceable A paceable instance of WorkerThread.
     * @param listener If not null, this overrides the above method and the given object
     * as an asynchronous listener. 
     */
    protected void run( WorkerThread.Paceable paceable, MessageListener listener ) {

    	Log.logger.info("START");    	
    	
    	while ( !done && !shutdown ) {
    	
	        try {
					
	            // Connect to queuemanager
	            status = sCONNECTING;
	                        
	            buildJMSResources();
	
	            status = sRUNNING;
	
	            Log.logger.fine( "Entering client loop" );

	            if ( listener!=null ) {
	            	
        			startTime = System.currentTimeMillis();
	            	
	            	// Use onMessage listener
	            	messageConsumer.setMessageListener(listener);
	            	
					waitForShutdownSignal();
	            	
	            } else {
		            // WorkerThread.pace handles all speed limiting etc.
		            pace( paceable );
	            }	            
	            
	            done = true;

	        } catch ( JMSException je ) {
	        	
	        	if ( ignoreExceptions ) {
	        		Log.logger.fine("disconnected?");
					
	        	} else {
	        		handleException( je );
	        	}
	        	
	        } catch (Throwable e) {
	
				handleException( e );
	
	            // Clear up code carefully in fair weather or foul.	
	        } finally {
	        	
	        	if ( done ) {
	        		
	        		status = (status&sERROR)|sENDING;
	        		
	        		if ( endTime==0 ) {
	        			endTime = System.currentTimeMillis();
	        		}
		
		           	destroyJMSResources(false);

		           	Log.logger.info("STOP");
		           	status = (status&sERROR)|sENDED;
		           	
	        	} else {
	        		// ! done
	        		// reconnections++;
	        	}
	        	
	       	} // end trycatch finally

    	} // end while !done
    	
    } // End public void run()
    
	/**
	 * Makes a unique durable subscriber name based on the given thread's name.
	 * @param conn A valid connection object.
	 * @param worker
	 * @return A subscriber name.
	 * @throws JMSException Probably means the client identifier has not been set on this connection.
	 * @see javax.jms.Connection#setClientID(java.lang.String)
	 */
	protected String makeDurableSubscriberName(Connection conn, WorkerThread worker) throws JMSException {
		
		StringBuffer name = new StringBuffer( conn.getClientID() ).append("_").append(worker.getName());
		return ( name.toString() );
		
	}
	
	/**
	 * Returns a short name for this destination regardless of its type. The JMS
	 * 1.1 specification has a shortcoming in that it does not include
	 * Destination.getName(). This method does what that call would have done.
	 * 
	 * @param dest
	 */
	public static String getDestinationName( Destination dest ) throws JMSException {

		if (dest == null) {
			// For cases with anonymous producers
			return("null"); 
		}
		
	    if ( jmsProvider.isQueue(dest) ) {
	    	return ((Queue)dest).getQueueName();
	    } else {
	        return ((Topic)dest).getTopicName();
	    }
	}
}
