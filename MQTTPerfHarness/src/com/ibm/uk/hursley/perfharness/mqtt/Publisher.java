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

import org.eclipse.paho.client.mqttv3.*;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * @author icraggs Publish messages to a Topic.
 */
public final class Publisher extends MqttWorkerThread implements
		WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE
																						// compiler
																						// warning

	protected MqttMessage outMessage = null;

	public static void registerConfig() {
		Config.registerSelf(Publisher.class);
	}

	/**
	 * Constructor for MQTTClientThread.
	 * 
	 * @param name
	 */
	public Publisher(String name) {
		super(name);
	}

	protected void buildMQTTResources() throws Exception {

		super.buildMQTTResources();

		if (destProducer == null) {
			destProducer = messageConnection.getTopic(destFactory
					.generateDestination(getThreadNum()));
			Log.logger.log(Level.FINE,
					"Associating client: {0} with topic: {1}", new Object[] {
							connid, destProducer.getName() });
		}

		outMessage = new MqttMessage(msgFactory.createMessage(getName(), 0));
		outMessage.setQos(qos);
		outMessage.setRetained(false);

	}

	protected void destroyMQTTResources(boolean reconnecting) {
		if (messageConnection != null) {
			if (!reconnecting)
				Log.logger.log(Level.FINE, "Closing producer {0}",
						messageConnection);
			super.destroyMQTTResources(reconnecting);
		}
	}

	public void run() {

		run(this, null); // call superclass generic method telling it to pace

	} // End public void run()

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public final boolean oneIteration() throws Exception {
		destProducer.publish( outMessage );
		incIterations();
		return true;
	}
}
