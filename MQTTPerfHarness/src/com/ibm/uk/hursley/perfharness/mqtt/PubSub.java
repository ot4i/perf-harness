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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * @author icraggs / fentono
 * Publish messages to, and receive messages from, a Topic. 
 * Ensure this thread's topic is unique from all other threads. If it's not, THIS CLASS IS NOT SUITABLE!
 * Message count is per iteration (i.e. 1 send & 1 receive)
 */
public final class PubSub extends MqttWorkerThread
                          implements WorkerThread.Paceable, MqttCallback
{
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	
	protected MqttMessage inMessage = null;
	protected MqttMessage outMessage = null;
	
	protected IMqttDeliveryToken dt = null;
	
    // TODO: not implemented yet
    //protected final Boolean useDeliveryToken;
	
    public static void registerConfig() {
		Config.registerSelf( PubSub.class );
	} 
    
    /**
     * Constructor for MQTTClientThread.
     * @param name
     */
    public PubSub(String name) {
        super( name );
        
		//useDeliveryToken = Config.parms.getBoolean("dv");
    }
    
    protected void buildMQTTResources() throws Exception {
    	
		super.buildMQTTResources();

        if (destProducer == null ) {
        	// TODO: replace this section using the destination factory
        	//String topicname = Config.parms.getString("tp") + connid;
        	//Log.logger.log(Level.INFO, "Associating client: {0} with topic: {1}", new Object[] {connid, topicname} );
        	destProducer = messageConnection.getTopic( destFactory.generateDestination( getThreadNum() ) );
        	Log.logger.log(Level.FINE, "Associating client: {0} with topic: {1}", new Object[] {connid, destProducer.getName()} );
        	//destProducer = messageConnection.getTopic(topicname);
        }
        
        messageConnection.subscribe(destProducer.getName(), qos);
        
        outMessage = new MqttMessage(msgFactory.createMessage( getName(), 0 ));
        outMessage.setQos(qos);
        outMessage.setRetained(false); // might wanna add this as an option later
		
	}
    
    protected void destroyMQTTResources(boolean reconnecting) {
    	if (messageConnection != null) {
    		if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing producer {0}",messageConnection );
    		try {
    			if (destProducer != null && !cleansession) {
    				messageConnection.unsubscribe(destProducer.getName());
    			}
    		} catch (MqttException e) {
    			// swallow
    		}
    		super.destroyMQTTResources(reconnecting);
    	}
    }
    
    public void run() {

		run( this, this );  // call superclass generic method telling it to pace
	       	
    } // End public void run()
    
    /**
     * This thread needs a special version of run to cope with a listener & paceability.
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

	            // Use mqtt listener
	            messageConnection.setCallback(listener);
	            	
		        // WorkerThread.pace handles all speed limiting etc.
		        pace( paceable );            
	            
	            done = true;

	        } catch ( MqttException je ) {
	        	
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
    
    
    private int messagesPublished = 0; // warning: dirtily read. Must only be updated by this thread
	private int messagesReceived = 0; // warning: dirtily read. Must only be updated by this thread
    /**
     * Send a message to topic then wait till it comes back. 
     */
	public final boolean oneIteration() throws Exception {
		
		dt = destProducer.publish( outMessage );
		
		waitOnListener(++messagesPublished);
		
		return true;
	}
	
	/**
	 * This is a bit messy - we need it as we have a separate listener thread
	 * @param msgCount wait for at least this number of messages to be received before sending anymore
	 *   it doesn't matter too much if we get too many as we'll just publish a few more next time.
	 *   (actually it matters a little as we'll get message batching rather than an even distribution) 
	 */
	public void waitOnListener(int messagesPublished) {
		while (!shutdown && (messagesReceived < (messagesPublished - (maxinflight - 1)))) {
			synchronized (this) {
				try {
					this.wait(1 * 1000);
				} catch (InterruptedException e) {
					// swallow
				}
			}
		}
	}
	
	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
	    // increment message counter
	    messagesReceived++;
	    synchronized (this) {
	        notify(); // tell waitOnListener to wake up
	    }

	    incIterations();
	}

	
	public void connectionLost(Throwable cause)
    {
      	handleException(cause);
    }
  
	@Override
	public void deliveryComplete(IMqttDeliveryToken token)
    {
  	
    }

}

