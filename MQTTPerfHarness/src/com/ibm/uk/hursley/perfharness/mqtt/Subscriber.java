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

/**
 * @author icraggs
 *
 */

import java.util.logging.Level;

import org.eclipse.paho.client.mqttv3.*;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * @author icraggs
 * Subscribe to Topic-domain messages.
 */
public final class Subscriber extends MqttWorkerThread
  implements WorkerThread.Paceable, MqttCallback
{
	
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE
																						// compiler
																						// warning
	
	protected MqttMessage inMessage = null;

	public static void registerConfig() {
		Config.registerSelf(Subscriber.class);
	}
	
	/**
	 * Constructor for MQTTClientThread.
	 * 
	 * @param name
	 */
	public Subscriber(String name) {
		super(name);
	}
	
	protected void buildMQTTResources() throws Exception {

		super.buildMQTTResources();

		if (destConsumer == null) {
			destConsumer = messageConnection.getTopic(destFactory
					.generateDestination(getThreadNum()));
			Log.logger.log(Level.FINE,
					"Associating client: {0} with topic: {1}", new Object[] {
							connid, destConsumer.getName() });
		}

		messageConnection.subscribe(destConsumer.getName(), qos);

	}
	
	protected void destroyMQTTResources(boolean reconnecting) {
		if (messageConnection != null) {
			if (!reconnecting)
				Log.logger.log(Level.FINE, "Closing consumer {0}",
						messageConnection);
			try {
				if (destConsumer != null && !cleansession) {
					messageConnection.unsubscribe(destConsumer.getName());
				}
			} catch (MqttException e) {
				// swallow
			}
			super.destroyMQTTResources(reconnecting);
		}
	}
	
	public void run() {

		run(this, this); // call superclass generic method telling it to pace

	} // End public void run()
	
	/**
	 * one iteration
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message) throws java.lang.Exception
    {

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
    
    public final boolean oneIteration() throws Exception {
	
		// empty
		return true;
		
	}
	
}

