/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp.utils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.logging.Level;

import com.ibm.mqlight.api.ClientException;
import com.ibm.mqlight.api.ClientOptions;
import com.ibm.mqlight.api.ClientState;
import com.ibm.mqlight.api.CompletionListener;
import com.ibm.mqlight.api.Delivery;
import com.ibm.mqlight.api.DestinationAdapter;
import com.ibm.mqlight.api.MalformedDelivery;
import com.ibm.mqlight.api.NonBlockingClient;
import com.ibm.mqlight.api.NonBlockingClientAdapter;
import com.ibm.mqlight.api.QOS;
import com.ibm.mqlight.api.SendOptions;
import com.ibm.mqlight.api.SendOptions.SendOptionsBuilder;
import com.ibm.mqlight.api.StringDelivery;
import com.ibm.mqlight.api.SubscribeOptions;
import com.ibm.mqlight.api.SubscribeOptions.SubscribeOptionsBuilder;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.amqp.AMQPWorkerThread.AMQPException;
import com.ibm.uk.hursley.perfharness.amqp.utils.FIFO.FIFODelivery;
import com.ibm.uk.hursley.perfharness.amqp.utils.FIFO.FIFOHashMap;
import com.ibm.uk.hursley.perfharness.amqp.utils.MessageContainer.MESSAGE_TYPE;
import com.ibm.uk.hursley.perfharness.util.TypedPropertyException;

/**
 * @author ElliotGregory
 *
 */
public class BlockingJavaClient {
	
	private final static Boolean logEnabled = Log.logger.getLevel().intValue() <= Level.FINE.intValue();
	
	private final static int QUEUE_SIZE_WARNING = 20;
	
	/** AMQP Client object **/
	private final NonBlockingClient client;
	/** Server address **/
	private final String amqpAddress;
	/** FIFO communication **/
	private final FIFODelivery fifoDelivery;
	/** Characteristics of the transfer **/
	private final TransferParameters transferParameters;


	/**
	 * Create a client to given server
	 * @param amqpAddress
	 * @param clientId
	 * @return
	 * @throws Exception
	 */
	public static BlockingJavaClient create (final String amqpAddress, final String clientId) throws Exception {
		return new BlockingJavaClient (amqpAddress, clientId);
	}
	
	/**
	 * @param topicPattern
	 * @throws Exception 
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void subscribe(final String topicPattern) throws Exception {
		if (logEnabled) log("({0}, {1}): Subscribe - completed", amqpAddress, topicPattern);
		
		final Blocker blocker = new Blocker (amqpAddress);
		
		client.subscribe(topicPattern, transferParameters.getSubscribeOptions(), new DestinationAdapter() {

			public void onMalformed(NonBlockingClient client, Object context, MalformedDelivery delivery) {
				if (logEnabled) log("({0}, {1}): Subscribe - Malformed",amqpAddress, topicPattern);
			}

			public void onMessage(NonBlockingClient client, Object context, Delivery delivery) {
				if (logEnabled) log("({0}, {1}, {2}): Subscribe - received()",getReference(delivery), topicPattern);
				fifoDelivery.put(delivery);
			}

			@SuppressWarnings("unused")
			public void onUnsubscribed(NonBlockingClient client, Object context, String topicPattern, String share) {
				if (logEnabled) log("({0}, {1}): Subscribe - Unsubscribed",amqpAddress, topicPattern);
			}
			
		}, new CompletionListener() {

			@Override
			public void onError(NonBlockingClient client, Object context, Exception exception) {
				if (logEnabled) log("({0}, {1}): Subscribe - Exception {2}",amqpAddress,  topicPattern, exception.toString());
				HashMap map = new HashMap<String,Object>();
				map.put("exception", exception);
				blocker.signal(map);
			}

			@Override
			public void onSuccess(NonBlockingClient client, Object context) {
				if (logEnabled) log("({0}, {1}): Subscribe  - Completed",amqpAddress, topicPattern);
				blocker.signal(null);
			}

		}, null);
		
		// Wait for completion event
		HashMap map = blocker.pause();
		if (map != null && map.containsKey("exception")) {
			if (logEnabled) log("({0}, {1}): Subscribe - Exception {2}",amqpAddress, topicPattern, map.get("exception").toString());
			throw (Exception)map.get("exception");
		}
		if (logEnabled) log("({0}, {1}): Subscribe - completed", amqpAddress, topicPattern);
	}
	
	/**
	 * @param client
	 * @throws Exception 
	 */
	public BlockingJavaClient (final String amqpAddress, String clientId) throws Exception {
		this.amqpAddress = amqpAddress;
		this.transferParameters = new TransferParameters();
		this.fifoDelivery = new FIFODelivery (amqpAddress);
		
		
		final FIFOHashMap fifoCconnection = new FIFOHashMap(amqpAddress);
	
    	if (logEnabled) log("({0}, {1}): Create connection", amqpAddress, clientId);
    	ClientOptions clientOptions = ClientOptions.builder().build();
    	client = NonBlockingClient.create(amqpAddress, clientOptions, new NonBlockingClientAdapter<Void>() {
			public void onStarted(NonBlockingClient client, Void context) {
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("client", client);	// Optional
				fifoCconnection.put (map);
			}

			@SuppressWarnings("unused")
			public void onError(NonBlockingClient client, Object context, Exception exception) {
				if (logEnabled) log("({0}): Create connection - Exception {1}",amqpAddress, exception.getLocalizedMessage());
				HashMap<String,Object> map = new HashMap<String,Object>();
				map.put("client", client);	// Optional
				map.put("exception", exception);
				fifoCconnection.put (map);
			}
			
			public void onStopped(NonBlockingClient client, Void context, ClientException throwable) {
				String errorMsg = throwable == null ? "" : throwable.toString();
				if (logEnabled) log("({0}): Create connection - Stopped. Exception {1}",amqpAddress, errorMsg);
			}

			public void onRestarted(NonBlockingClient client, Void context) {
				if (logEnabled) log("({0}): Create connection - Restarted.",amqpAddress);
			}

			public void onRetrying(NonBlockingClient client, Void context, ClientException throwable) {
				String reason = throwable == null ? "Unknown" : throwable.getLocalizedMessage();
				if (logEnabled) log("({0}): Create connection - Retrying. Exception {1}",amqpAddress, reason);
				HashMap<String,Object> map = new HashMap<String,Object>();
				AMQPException amqpException = new AMQPException ("Client connection failed.Now retrying", throwable);
				map.put("exception",amqpException);
				fifoCconnection.put (map);
				Log.logger.info("*** Lost connection with Server ***");
				
				// Report exception to delivery queue which acts as a wake-up
				if (fifoDelivery != null) fifoDelivery.put(amqpException);
			}
			
		}, null);
		
		// Wait for connect complete event.
		if (logEnabled) log("({0}): Create connection - wait on block", amqpAddress);
		HashMap<String,Object> map = fifoCconnection.get();
		
		if (map.containsKey("exception")) {
			if (logEnabled) log("({0}): Create connection - Exception {1}",amqpAddress, map.get("exception").toString());
			client.stop(null, null);
			throw (Exception)map.get("exception");
		}
		
		if (logEnabled) log("({0}): Create connection - completed", amqpAddress);
	}
	

	/**
	 * @param reference
	 * @param topic
	 * @param message
	 * @throws Exception
	 */
	public void sendMessage (final String reference, final String topic, final MessageContainer message) throws Exception {
		if (message.getMessageType() == MESSAGE_TYPE.STRING) {
			sendMessage (reference, topic, message.getStringMessage());
		}
		else {
			sendMessage (reference, topic, message.getBufferMessage());
		}
	}
	
	

	/**
	 * @param topic <String> contains the topic reference to send to
	 * @param message <String or ByteBuffer> message to send.
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void sendMessage (final String reference, final String topic, final String message) throws Exception {
		if (logEnabled) log("({0}): Send - topic = {1}", reference, topic);
		
		final Blocker blocker = new Blocker (reference);
        client.send(topic, message, null, transferParameters.getSendOptions(), new CompletionListener() {

                @Override
                public void onError(NonBlockingClient client, Object context, Exception exception) {
                	if (logEnabled) log("({0}): Send  - Exception {1}",reference, exception);
    				HashMap map = new HashMap<String,Object>();
    				map.put("exception", exception);
					blocker.signal(map);
                }

                @Override
                public void onSuccess(NonBlockingClient client, Object context) {
                	if (logEnabled) log("({0}): Send - Completed(1)",reference);
                	blocker.signal(null);
                }

        }, null);
        
		// Wait for completion event
		HashMap map = blocker.pause();
		if (map != null && map.containsKey("exception")) {
			if (logEnabled) log("({0}): Send - Exception {1}",reference, map.get("exception").toString());
			throw (Exception)map.get("exception");
		}
		if (logEnabled) log("({0}): Send - completed(2)", reference);
	}
	
	
	/**
	 * @param reference
	 * @param topic
	 * @param message
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void sendMessage (final String reference, final String topic, final ByteBuffer message) throws Exception {
		if (logEnabled) log("({0}): Send", amqpAddress);
		
		final Blocker blocker = new Blocker (amqpAddress);
        client.send(topic, message, null, transferParameters.getSendOptions(), new CompletionListener() {

                @Override
                public void onError(NonBlockingClient client, Object context, Exception exception) {
                	if (logEnabled) log("({0}): Send  - Exception-001 {1}",amqpAddress, exception);
    				HashMap map = new HashMap<String,Object>();
    				map.put("exception", exception);
    				blocker.signal(map);
                }

                @Override
                public void onSuccess(NonBlockingClient client, Object context) {
                	if (logEnabled) log("({0}): Send  - Completed", amqpAddress);
					blocker.signal(null);
                }

        }, null);
        
		// Wait for completion event
        try {
			HashMap map = blocker.pause();
			if (map != null && map.containsKey("exception")) {
				if (logEnabled) log("({0}): Send - Exception-002 {1}",amqpAddress, map.get("exception").toString());
				throw (Exception)map.get("exception");
			}
		} catch (InterruptedException ie) {
			if (logEnabled) log("({0}): Send  - Interrupted Exception-003 {1}", amqpAddress, ie);
		}
        if (logEnabled) log("({0}): Send - completed", amqpAddress);
	}
	
	/**
	 * 
	 */
	public void close(final String reference) {
		if (logEnabled) log("({0}): Stop  - requested", reference);
		client.stop(new CompletionListener() {

			@Override
			public void onError(NonBlockingClient client, Object context, Exception exception) {
				if (logEnabled) log("({0}): Stop  - Exception-001 {1}", reference, exception);

			}

			@Override
			public void onSuccess(NonBlockingClient client, Object context) {
				if (logEnabled) log("({0}): Stop  - Completed", reference);
			}

		}, null);
	}
	
	/**
	 * @return true if connection has been lost.
	 */
	public boolean connectionLost () {
		ClientState clientState = client.getState();
		return clientState == ClientState.RETRYING && 
				clientState == ClientState.STOPPED && 
				clientState == ClientState.STOPPING;
	}
	
	/**
	 * @return
	 */
	public String getAMQPAddress() {
		return amqpAddress;
	}
	
	/**
	 * @return
	 * @throws AMQPException 
	 */
	public Delivery receiveMessage () throws AMQPException {
		Delivery delivery = fifoDelivery.get();
		if (fifoDelivery.size() > QUEUE_SIZE_WARNING) {
			// Report if mechanism is not keeping up.
			Log.logger.log(Level.SEVERE, "Internal queue size now {0}", fifoDelivery.size());
		}
		if (logEnabled) log("({0}): receiveMessage - get()",getReference(delivery));
		return delivery;
	}
	
	/**
	 * @param level
	 * @param msg
	 * @param arguments
	 */
	public static void log(String msg, Object... arguments) {
		Log.logger.log(Level.FINE, "AMQP " + msg, arguments);
	}
	
	/**
	 * @return
	 */
	public AUTO_CONFIRM getAutoConfirm() {
		return transferParameters.getAutoConfirm();
	}
	
	/**
	 * @return
	 */
	public boolean doesRequireConfirmation () {
		AUTO_CONFIRM autoConfirm = transferParameters.getAutoConfirm();
		return autoConfirm == null ? false : autoConfirm.doesRequireConfirmation();
	}
	
	/**
	 * @param delivery
	 * @return
	 */
	private String getReference (final Delivery delivery) {
		String reference = null;
		if (delivery != null) {
			if (delivery instanceof StringDelivery) {
				byte[] data = ((StringDelivery) delivery).getData().getBytes();
				EmbeddedAction ac = EmbeddedAction.createEmbeddedAction(data);
				if (ac == null) {
					reference = "No Embedded data length=" + data.length;
				}
				else {
					reference = ac == null ? null : ac.getCommand("Reference");
				}
			} else { reference = "bad class " + delivery.getClass().getName();} 
		} else { reference = "Null message";} 
		
		return reference;
	}
			
	/**
	 * @author ElliotGregory
	 *
	 */
	public static class TransferParameters {
		
		private final String shareName;
		private final QOS qos;
    	private final Integer linkCredit;
    	private final Integer ttl;
    	private final AUTO_CONFIRM autoConfirm;
		
		/**
		 * 
		 */
		public TransferParameters () {
	    	shareName    = Config.parms.getString("gr", null);
	    	linkCredit   = getInteger("lc");
	    	ttl = getInteger("ttl");
	    	autoConfirm  = readAutoConfirm(Config.parms.getString("ac"));
	    	Integer numericalQOS = getInteger("qs",0,1);
    		qos = (numericalQOS == null) ? null : (numericalQOS== 0 ? QOS.AT_MOST_ONCE : QOS.AT_LEAST_ONCE);
    		
    		if (autoConfirm !=null && autoConfirm.doesRequireConfirmation() && (qos == null || qos == QOS.AT_MOST_ONCE)) {
				throw new TypedPropertyException(
						"Invalid configuration. QOS must be set to AT_LEAST_ONCE for manual confirmation");
    		}
		}
		
		/**
		 * @return
		 */
		public SendOptions getSendOptions() {
			SendOptionsBuilder sob = SendOptions.builder();
			if (ttl != null && ttl > 0) sob.setTtl(ttl);
			if (qos != null) sob.setQos(qos);
			return sob.build();
		}
		
		/**
		 * @return
		 */
		public SubscribeOptions getSubscribeOptions() {
			SubscribeOptionsBuilder sob = SubscribeOptions.builder();
			if (autoConfirm != null) sob.setAutoConfirm(autoConfirm == AUTO_CONFIRM.AUTO);
			if (linkCredit != null) sob.setCredit(linkCredit);
			if (qos != null) sob.setQos(qos);
			if (shareName != null) sob.setShare(shareName);
			if (ttl != null) sob.setTtl(ttl);
			return sob.build();
		}
		
		@Override
		public String toString() {
			return "TransferParameters [shareName=" + shareName + ", qos=" + qos + ", linkCredit=" + linkCredit
					+ ", ttlSend=" + ttl + ", autoConfirm=" + autoConfirm + "]";
		}
	
		/**
		 * @return
		 */
		public AUTO_CONFIRM getAutoConfirm () {
			return autoConfirm;
		}

		/**
		 * @param autoConfirmStr
		 * @return
		 */
		private AUTO_CONFIRM readAutoConfirm (final String autoConfirmStr) {
			if (autoConfirmStr == null || autoConfirmStr.isEmpty()) return null;
			
			AUTO_CONFIRM autoConfirm =  AUTO_CONFIRM.getValue(autoConfirmStr);
	    	if (autoConfirm == null) {
				throw new TypedPropertyException(
						"Invalid option of " + autoConfirmStr + "for \"ac\". Valid values are " + AUTO_CONFIRM.options());
	    	}
	    	if (autoConfirm == AUTO_CONFIRM.NONE) autoConfirm = null;
	    	
	    	return autoConfirm;
		}


		/**
		 * A wrapper for the getInt to allow Integer:null to be returned if not present.
		 * @param name <String> the name of the parameter
		 * @param min <Integer> optional minimum value
		 * @param max <Integer> optional maximum value.
		 * @return [Null,Integer] the numerical value of the parameter or null if not present.
		 */
		private Integer getInteger(final String name) {
			return getInteger(name,null,null);
		}
		private Integer getInteger(final String name, final Integer min, final Integer max) {
			Integer retValue = null;

			String strValue = Config.parms.getString(name, null);
			if (strValue != null && !strValue.isEmpty()) {
				try {
					retValue = Integer.parseInt(strValue);
					if (min != null && retValue < min) {
						throw new TypedPropertyException(
								"Valid range must >=  " + min + "for [" + name + "=" + retValue + "]");
					}
					if (max != null && retValue > max) {
						throw new TypedPropertyException(
								"Valid range must <=  " + max + "for [" + name + "=" + retValue + "]");
					}
				} catch (NumberFormatException e) {
					throw new TypedPropertyException("NumberFormatException on [" + name + "=" + retValue + "]");
				}
			}
			return retValue;
		}
	}

	
	/**
	 * @author ElliotGregory
	 *
	 */
	public static enum AUTO_CONFIRM {
		NONE("none","none", false), 
		AUTO("auto","auto", false), 
		MANUAL("manual","manual", true), 
		CONFIRM_AFTER_SENT("confirmaftersent","confirm-after-sent", true);
		
		private final String match;
		private final String perferred;
		private final boolean requireConfirmation;
		
		/**
		 * @param match
		 * @param perferred
		 */
		private AUTO_CONFIRM(final String match, final String perferred, final boolean requireConfirmation) {
			this.match = match;
			this.perferred = perferred;
			this.requireConfirmation = requireConfirmation;
		}
		
		/**
		 * @return <String> listing the possible options.
		 */
		public static String options () {
			StringBuilder sb = new StringBuilder ();
			String separator = "";
			for (AUTO_CONFIRM autoConfirm : AUTO_CONFIRM.values()) {
				sb.append(separator); separator=",";
				sb.append(autoConfirm.perferred);
			}
			return sb.toString();
		}
		
		/**
		 * @return
		 */
		public boolean doesRequireConfirmation () {
			return requireConfirmation;
		}
		
		/**
		 * @param text
		 * @return
		 */
		public static AUTO_CONFIRM getValue(final String text) {
			String searchValue = text.toLowerCase().replaceAll("-|_", "");
			for (AUTO_CONFIRM autoConfirm : AUTO_CONFIRM.values()) {
				if (autoConfirm.match.equals(searchValue)) {
					return autoConfirm;
				}
			}
			return null;
		}
	}
}
