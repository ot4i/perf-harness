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

package com.ibm.uk.hursley.perfharness.cmd;

import java.util.logging.Level;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.ibm.mq.jms.JMSC;
import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.mq.jms.MQQueue;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.Log;

/**
 * Listens on an MQ Queue for commands calls super.processCommand to action
 * Replies to an MQ Queue with status/feedback
 * 
 * NOTE: this is independent of the server under test. So if you
 * want to test WAS, this processor may still be valid if you want test control
 * through MQ
 * 
 * To use: enable with -cmd_c MQCommandProcessor and provide parameters as described
 * in the properties file.
 * 
 * The simplest way to send & receive messages is to use the WMQ sample applications
 * amqsput0 and amqsget0. Do something like:
 * 
 * To send:
 * amqsput0 [qm] [q]
 * >REPORT -stats
 * 
 * To receive:
 * amqsget0 [qm] [q]
 * <[stats]
 * 
 * See Command class for supported commands
 * 
 * @author fentono
 * 
 */

@SuppressWarnings("deprecation")
public class MQCommandProcessor extends Command implements MessageListener {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;

	// JMS interface classes
	protected ConnectionFactory cf = null;
	protected Connection connection = null;
	protected Session insession = null;
	protected Session outsession = null;
	protected MessageProducer messageProducer = null; //outbound
	protected MessageConsumer messageConsumer = null; //inbound
	protected Destination inQueue = null; 
    protected Destination outQueue = null;
    protected Message outMessage = null;
    
    private Object mutexWaiting = null; // Lock for frozen/finished threads.

	/**
	 * Register our presence and look up any required parameters for this class.
	 * 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {

		Config.registerSelf(MQCommandProcessor.class);

	}

	public MQCommandProcessor(ControlThread parent) {
		super(parent);

		buildJMSResource();

	}

	/**
	 * Apply vendor-specific settings for building up a connection factory to
	 * WMQ.
	 * 
	 * @param cf
	 * @throws JMSException
	 */
	protected void configureMQConnectionFactory(MQConnectionFactory cf)
			throws JMSException {

		// always client bindings
		cf.setTransportType(JMSC.MQJMS_TP_CLIENT_MQ_TCPIP);
		cf.setHostName(Config.parms.getString("cmd_jh"));
		cf.setPort(Config.parms.getInt("cmd_p"));
		cf.setChannel(Config.parms.getString("cmd_jc"));

		cf.setQueueManager(Config.parms.getString("cmd_jb"));

	}
	

	public final void buildJMSResource() {
		
		try {
			cf = new MQConnectionFactory();
			configureMQConnectionFactory((MQConnectionFactory) cf);
			connection = cf.createConnection();
			connection.start();
			
			insession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			outsession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			
			inQueue = new MQQueue(Config.parms.getString("cmd_inq"));
			outQueue = new MQQueue(Config.parms.getString("cmd_outq"));
			
			((MQQueue) outQueue).setTargetClient(JMSC.MQJMS_CLIENT_NONJMS_MQ);
			
			messageConsumer = insession.createConsumer(inQueue);
			messageProducer = outsession.createProducer(outQueue);
			
		} catch (JMSException e) {
			Log.logger.log(Level.SEVERE,
					"Error connecting Command Processor to WebSphereMQ", e);
			return;
		}
		
	}

	protected void destroyJMSResources(boolean reconnecting) {
		
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
		
    	if (insession != null) {
        	if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing session {0}", insession );
            try {
                insession.close();
            } catch (JMSException e) {
            } finally {
                insession = null;
            }
        }
    	
    	if (outsession != null) {
        	if ( !reconnecting ) Log.logger.log(Level.FINE, "Closing session {0}", outsession );
            try {
                outsession.close();
            } catch (JMSException e) {
            } finally {
                outsession = null;
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
        	inQueue = null;
        	outQueue = null;
        	cf = null;
        }
	}

	public final void run() {
		try {
			messageConsumer.setMessageListener(this);
			Log.logger.log( Level.FINE, "connected to command server & listening on {0}, responses to {1}", 
					new Object[] {inQueue.toString(), outQueue.toString()});
			sendMessage("command server listening on " + inQueue.toString());
			waitForShutdownSignal();
		} catch (JMSException e) {
			// swallow
		} catch (InterruptedException e) {
			// swallow
		}
	}
	
	/**
	 * Causes the current thread to block until signalShutdown is called. This
	 * is used by WorkerThreads which are not actively polling the shutdown
	 * field.
	 * 
	 * @throws InterruptedException
	 *             This is thrown if the thread is interrupted outside of the
	 *             expected shutdown period.
	 */
	protected void waitForShutdownSignal() throws InterruptedException {

		if (mutexWaiting == null) {
			mutexWaiting = new Object();
		}
		try {
			synchronized (mutexWaiting) {
				mutexWaiting.wait();
			}
		} catch (InterruptedException e) {
			if (!shutdown) {
				throw e;
			} // else swallowed
		}

	}

	// process incoming messages
	public void onMessage(Message m) {
		try {
			
			// convert the message to text
			String txt = ((TextMessage) m).getText();
			processMessage(txt);

			
		} catch(JMSException e) {
			Log.logger.log( Level.WARNING, "error processing message {0}", m.toString());
		}
		
	}

	// send outgoing messages
	@Override
	public final void sendMessage(String message) {
		
		if (id != null) {
			message = "id=" + id + "," + message;
		}
		
		try {
			if (message != null) {
				outMessage = outsession.createTextMessage(message);	
			} else {
				outMessage = outsession.createTextMessage("null");
			}
			
			// send the message
			messageProducer.send(outMessage);
			
		} catch (JMSException e) {
			Log.logger.log( Level.WARNING, "error sending message to {0}: {1}", new Object[] {outQueue.toString(), e.getMessage()});
		}
		
	}
}
