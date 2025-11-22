package com.ibm.uk.hursley.perfharness.mqtt;

import java.util.Random;
import java.util.logging.Level;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.MultiDestinationFactory;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Publish messages to multiple topics
 * 
 * @author fentono
 */
public class MultiPublisher extends MqttWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE
																						// compile																				// warning

	protected MqttMessage outMessage = null;
	
	// use this instead of destProducer
	protected MqttTopic[] destProducers = null;
	
	/*
	 * Be very careful with SEQUENTIAL publishing. 
	 * If all threads have the same topic list, then all will publish to the same
	 * topic at the same time. This could give very strange results. I suggest
	 * using a varied destination set (db, dx, dn, ds) so the list of topics is differently
	 * ordered for different threads.
	 */
	private final static int STYLE_RANDOM = 0;
	private final static int STYLE_SEQUENTIAL = 1;
	protected final int style;
	private int nextTopic = 0;
	
	// share the random number generator between threads
	protected static Random randomGenerator = new Random();
	
	public static void registerConfig() {
		Config.registerSelf(Publisher.class);
	}
	
	protected MultiPublisher(String name) {
		super(name);
		
		if ("sequential".equals(Config.parms.getString("ps"))) {
			style = STYLE_SEQUENTIAL;
		} else {
			style = STYLE_RANDOM;
		}
	}
	
	protected void buildMQTTResources() throws Exception {

		super.buildMQTTResources();

		if (destProducers == null) {
			String dests[];
			if (destFactory instanceof MultiDestinationFactory) {
				dests = ((MultiDestinationFactory) destFactory).generateDestinations(getThreadNum());
			} else {
				dests = new String[1];
				dests[0] = destFactory.generateDestination(getThreadNum());
			}
			
			destProducers = new MqttTopic[dests.length];
			for(int i = 0; i < dests.length; i++) {
				destProducers[i] = messageConnection.getTopic(dests[i]);
				Log.logger.log(Level.FINE,
						"Associating multi-client: {0} with topic: {1}", new Object[] {
								connid, destProducers[i].getName() });
			}
			
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

	@Override
	public boolean oneIteration() throws Exception {
		
		if (style == STYLE_SEQUENTIAL) {
			destProducers[nextTopic++].publish( outMessage );
			if (nextTopic >= destProducers.length) {
				nextTopic = 0; // reset
			}
		} else {
			destProducers[ randomGenerator.nextInt(destProducers.length) ].publish( outMessage );
		}
		
		incIterations();
		return true;
	}

}
