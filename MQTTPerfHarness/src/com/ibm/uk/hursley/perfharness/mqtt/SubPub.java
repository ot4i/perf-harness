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
 * @author ivansrib
 * Listen to a Topic A. For each received message publish reply to Topic B. 
 * Message count is per iteration (i.e. 1 receive & 1 send)
 */
public final class SubPub extends MqttWorkerThread 
						  implements WorkerThread.Paceable, MqttCallback
{
	
	protected MqttMessage inMessage = null;
	protected MqttMessage outMessage = null;
	
	public SubPub(String name) {
		super(name);
	}

	public static void registerConfig() {
		Config.registerSelf( SubPub.class );
	} 
	
	protected void buildMQTTResources() throws Exception {
    	
		super.buildMQTTResources();
		//destConsumer - topic to subscribe to
		if (destConsumer == null) {
			destConsumer = messageConnection.getTopic(destFactory
					.generateDestination(getThreadNum()));
			Log.logger.log(Level.FINE,
					"Associating client: {0} with topic: {1}", new Object[] {
							connid, destConsumer.getName() });
		}

		messageConnection.subscribe(destConsumer.getName(), qos);
		
		//destProducer - topic to publish to
		if (destProducer == null ) {
			String replyTopic = Config.parms.getString("rd");
        	destProducer = messageConnection.getTopic(replyTopic);
        	Log.logger.log(Level.FINE, 
        			"Associating client: {0} with topic: {1}", new Object[] {
        					connid, destProducer.getName() });
        }
        
        outMessage = new MqttMessage(msgFactory.createMessage( getName(), 0 ));
        outMessage.setQos(qos);
        outMessage.setRetained(false);
		
	}
	
	protected void destroyMQTTResources(boolean reconnecting) {
    	if (messageConnection != null) {
    		if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing consumer {0}",messageConnection );
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
	
	

	@Override
	public void connectionLost(Throwable cause) {
		handleException(cause);		
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
				
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		destProducer.publish( outMessage );
		incIterations();
	}

	@Override
	public boolean oneIteration() throws Exception {
		// empty
		return true;
	}

}
