/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp;

import java.nio.ByteBuffer;
import java.util.logging.Level;

import com.ibm.mqlight.api.BytesDelivery;
import com.ibm.mqlight.api.Delivery;
import com.ibm.mqlight.api.StringDelivery;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.amqp.utils.BlockingJavaClient.AUTO_CONFIRM;
import com.ibm.uk.hursley.perfharness.amqp.utils.EmbeddedActionContainer;

/**
 * Transfers messages from the subscribed request topic and places them on the reply topic.
 * The reply topic defaults to the -it parameters unless it is embedded within the message
 * being transferred.
 */
public final class Responder extends AMQPWorkerThread implements WorkerThread.Paceable {
	
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	
	/** Force failure if connection is lost. **/
	private final boolean failureOnLostConnection = Config.parms.getBoolean("fl");
	/** The subscriber request topic **/
	private final String  outboundTopic = getOutboundTopic();
	/** Default reply topic **/
	private final String  inboundTopic = Config.parms.getString("it");

    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public Responder(String name) {
        super(name);
    }

    /* (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    public void run() {
        run(this);
    } 

    /* (non-Javadoc)
     * @see com.ibm.uk.hursley.perfharness.mqjava.MQJavaWorkerThread#buildMQJavaResources()
     */
    public void buildAMQPResources() throws Exception {
    	super.buildAMQPResources();
		client.subscribe(outboundTopic);
    }
    
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {

		Delivery delivery = client.receiveMessage();
		if (delivery == null && !shutdown) {
			log(Level.SEVERE, "({0}) has lost its connection.", getName());
			if (failureOnLostConnection) {
				throw new AMQPException("Connection to AMQP Server has been lost.");
			}
		} else {
			// collect embedded key/values from within the message being transferred
			EmbeddedActionContainer eac = new EmbeddedActionContainer(delivery);
			String reference = eac.getValue("Reference");
			String reply2Topic = eac.getValue("Reply2Topic", inboundTopic);
			
			if (delivery != null) {
				if (client.getAutoConfirm() == AUTO_CONFIRM.MANUAL) {
					delivery.confirm();
				}
				if (delivery instanceof StringDelivery) {
					String message = ((StringDelivery) delivery).getData();
					client.sendMessage(reference, reply2Topic, message);
				} else if (delivery instanceof BytesDelivery) {
					ByteBuffer message = ((BytesDelivery) delivery).getData();
					client.sendMessage(reference, reply2Topic, message);
				} else {
					throw new ResponderException("Received unsupport message type of " + delivery.getClass().getName());
				}
				if (client.getAutoConfirm() == AUTO_CONFIRM.CONFIRM_AFTER_SENT)
					delivery.confirm();

				startResponseTimePeriod();
				incIterations();
			}
		}
		return true;
	}
	
	/**
	 * Closes the client connection.
	 */
	@Override
	protected void close () {
		if (client != null) client.close(getName());
	}
	
	/**
	 * @author ElliotGregory
	 *
	 */
	public class ResponderException extends Exception {
		private static final long serialVersionUID = 1L;

		public ResponderException(final String message) {
			super(message);
		}
	}

}
