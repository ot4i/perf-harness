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

package com.ibm.uk.hursley.perfharness.mqtt;

import java.util.logging.Level;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.DestinationFactory;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.util.TypedPropertyException;

public abstract class MqttWorkerThread extends WorkerThread {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;
	
	protected MqttClient messageConnection = null;
	protected MqttTopic destProducer = null;
    protected MqttTopic destConsumer = null;
    
    protected MqttConnectOptions props;
    
    protected MessageFactory msgFactory = null;
    protected static DestinationFactory destFactory;
	
    protected String connid;
    protected final Integer qos = Config.parms.getInt("qos");
    protected final Boolean cleansession = Config.parms.getBoolean("cs");
    protected final Integer maxinflight = Config.parms.getInt("if");
    protected final Integer connectionTimeout = 600;
    //protected final Integer keepAliveInterval = Integer.MAX_VALUE;
	
    
    protected boolean done = false;
    //boolean ignoreExceptions = true;
    boolean ignoreExceptions = false;
    	
	
    /**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
    public static void registerConfig() {
		
    	DefaultMessageFactory.registerConfig();
		
		if ( ! Config.isInvalid() ) {
			Class dfClazz = Config.parms.getClazz("df");
			Config.registerAnother( dfClazz );
			
		}
	
	}
    
    protected MqttWorkerThread( String name ) {

    	super( name );
    	
		try {
			Class dfClazz = Config.parms.getClazz("df");
			destFactory = (DestinationFactory) dfClazz.newInstance();
			msgFactory = DefaultMessageFactory.getInstance();	
			
		} catch (Exception e) {
		    Log.logger.log( Level.SEVERE, "Problem getting DestinationFactory class", e );
		}
    	
		connid = getName() + "." + Config.parms.getString("id", "");
		
		props = new MqttConnectOptions();
		props.setCleanSession(cleansession);
		props.setConnectionTimeout(connectionTimeout);
		//props.setMaxInflight(maxinflight);
		try {
			int keepAliveInterval = Config.parms.getInt("ka");
			props.setKeepAliveInterval(keepAliveInterval);
		} catch(TypedPropertyException tpe) {
			Log.logger.log(Level.INFO, "Using default KeepAliveInterval value");
		}
		
    }
    
    /**
	 * Creates and sets the JMS connection and session variables.
	 * @throws Exception
	 */
    protected void buildMQTTResources() throws Exception {
    	
    	destroyMQTTResources(true);
    	
        Log.logger.log(Level.FINE, "Making connection" );
        messageConnection = new MqttClient(Config.parms.getString("iu"), connid, null);
        messageConnection.connect(props);
        Log.logger.log(Level.FINE, "Connection started {0}",messageConnection );    	
	
    }
    
    /**
     * Attempts to safely shutdown all JMS objects.
     * <p>Also calls Session.rollback() to back out any
     * incomplete units-of-work.  This may look confusing to people....
     * @param reconnecting Whether this method is being called in a reconnection scenario.
     * This affects the treatment of durable subscribers.
     */
    protected void destroyMQTTResources(boolean reconnecting) {
    	
    	if (messageConnection != null) {
    		if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing producer {0}",messageConnection );
    		try {
    			messageConnection.disconnect();
    		} catch (MqttException e) {
    			// swallow
    		} finally {
    			messageConnection = null;
    		}
        }       	
    	
    	msgFactory = null;
    	        
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
     * General implementation of the main body of a simple MQTT primitive.
     * @param paceable A paceable instance of WorkerThread.
     * @param listener If not null, this overrides the above method and the given object
     * as an asynchronous listener. 
     */
    protected void run( WorkerThread.Paceable paceable, MqttCallback listener ) {

    	Log.logger.info("START");    	
    	
    	while ( !done && !shutdown ) {
    	
	        try {
					
	            // Connect to queuemanager
	            status = sCONNECTING;
	                        
	            buildMQTTResources();
	
	            status = sRUNNING;
	
	            Log.logger.fine( "Entering client loop" );

	            if ( listener!=null ) {
	            	
        			startTime = System.currentTimeMillis();
	            	
	            	// Use mqtt listener
	            	messageConnection.setCallback(listener);
	            	
					waitForShutdownSignal();
	            	
	            } else {
		            // WorkerThread.pace handles all speed limiting etc.
		            pace( paceable );
	            }	            
	            
	            done = true;

	        } catch ( MqttException je ) {
	        	
	        	if ( ignoreExceptions ) {
	        		Log.logger.fine("disconnected?");
	        		Log.logger.log(Level.SEVERE, "MQTT Exception.", je);
	        		status |= sERROR;
	        		done = true;
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
		
		           	destroyMQTTResources(false);

		           	Log.logger.info("STOP");
		           	status = (status&sERROR)|sENDED;
		           	
	        	} else {
	        		// ! done
	        		// reconnections++;
	        	}
	        	
	       	} // end trycatch finally

    	} // end while !done
    	
    } // End public void run()
    
}
