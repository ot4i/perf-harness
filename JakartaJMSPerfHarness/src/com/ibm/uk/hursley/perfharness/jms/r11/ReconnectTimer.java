/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/


package com.ibm.uk.hursley.perfharness.jms.r11;
import java.util.logging.Level;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import jakarta.jms.Message;
import jakarta.jms.Queue;

import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Reconnection enabled PutGet.
 * Sends a message then receives one from the same queue. Normal use is with
 * correlation identifier to ensure the same message is received.
 * @author Sam Massey/Paul Harris, IBM 
 */
public final class ReconnectTimer extends JMS11WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    Message inMessage = null;
    Message outMessage = null;
    String correlID = null;

    boolean reconnectAttempted;
    static boolean firstExceptionDetected = false;
    static long maxReconnectTime = 0;
    static long minReconnectTime = Long.MAX_VALUE;
    long reconnectStart =0;
    long reconnectTime = 0;
    static String formattedDate = "";
    static int numThreads;
    static int connectedThreads=0;
    static int reconnectedThreads=0;
    Date initialConnectStartTime;
    DateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
    boolean primaryQM = true;
    static boolean usingCCDT = false;
    
    static Object gate = new Object();
    
    
    public static void registerConfig() {
		Config.registerSelf( ReconnectTimer.class );
		numThreads = Config.parms.getInt("nt");
        if (!Config.parms.getString("ccdt","").equals("")){
        	usingCCDT = true;
        }
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public ReconnectTimer(String name) {
        super(name);
        inMessage = null;
        outMessage = null;
        correlID = null;
        reconnectAttempted = false;
        if (!Config.parms.getString("ft").equals("l")){
        	formatter.setTimeZone(TimeZone.getTimeZone("GMT"));	
        }
    }

	protected void buildJMSResources() throws Exception {
		if(connectedThreads==0){
			initialConnectStartTime = new Date();
		}
		super.buildJMSResources();
	    synchronized(ReconnectTimer.class){
		   connectedThreads++;
	    }
		if(connectedThreads == numThreads) {
			Log.logger.log(Level.SEVERE, "All threads initially connected. Start/End times: {0} / {1}", new Object[] {
					formatter.format(initialConnectStartTime),formatter.format(new Date())
		    });
		}
        // Open queues
        if (destProducer == null) {
        	destProducer = jmsProvider.lookupQueue(destFactory.generateDestination(getThreadNum()), session).destination;
        }
        
        outMessage = msgFactory.createMessage(session, getName(), 0);
        String selector = null;
        
        // Use CorrelID Based Selector
       	if (Config.parms.getBoolean("co")) {
       		correlID = msgFactory.setJMSCorrelationID(this, outMessage);
      	}
        if (correlID != null) {
    		StringBuffer sb = new StringBuffer("JMSCorrelationID='");
    		sb.append(correlID);
    		sb.append("'");
    		selector = sb.toString();
    	}
        
        String destName = getDestinationName( destProducer );
        Log.logger.log(Level.FINE, "Creating receiver on {0} selector:{1}", new Object[] {destName, selector});
        System.out.println("Creating receiver on " + destName + " with selector: " + selector);
        messageConsumer = session.createConsumer((Queue)destProducer, selector);

        Log.logger.log(Level.FINE, "Creating sender on {0}", destName );
        messageProducer = session.createProducer((Queue)destProducer );
	}

    public void run() {
        run(this, null);  // call superclass generic method.
    } 
    
    /**
     * Send a message to one queue then get it back again. 
     */
	public final boolean oneIteration() throws Exception {
	    Exception storedException = null;
        boolean exceptionReceived = false;
        
        try {
        	//System.out.println("Iteration:"+getIterations());
           	messageProducer.send(outMessage, deliveryMode, priority, expiry);
           	if (transacted) session.commit();

           	if ((inMessage = messageConsumer.receive(timeout)) != null) {
           		if (transacted) session.commit();
           		incIterations();
           	} else {
           		throw new Exception("No response to message (" + outMessage.getJMSMessageID() + ")");
           	}
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        	if(!firstExceptionDetected){
        		firstExceptionDetected = true;

        		storedException = e;
        		Log.logger.log(Level.SEVERE, (new StringBuilder("Exception received: ")).append(storedException).toString());
        		Log.logger.log(Level.INFO, "{0} messages processed from {1} on primary QM", new Object[] {
                Integer.valueOf(getIterations()), getDestinationName(destProducer)
        		});
        	}
            exceptionReceived = true;
        }
        
        if(exceptionReceived) {
           // if(!reconnectAttempted) {
                if(!usingCCDT) {
        	       if(primaryQM) {
                      //Log.logger.log(Level.INFO, "Will attempt connection to secondary host");
                      ((MQConnectionFactory)cf).setHostName(Config.parms.getString("h2"));
                
                      //Set new port for secondary server (or use same port)
                      ((MQConnectionFactory)cf).setPort(Config.parms.getInt("p2",Config.parms.getInt("jp")));
                      primaryQM = false;
        	       } else {
                      //Log.logger.log(Level.INFO, "Will attempt connection to primary host");
                      ((MQConnectionFactory)cf).setHostName(Config.parms.getString("jh"));
                 
                      //Set port for primary server
                      ((MQConnectionFactory)cf).setPort(Config.parms.getInt("jp"));
                      primaryQM = true;
        	       }
                }
        	    reconnectStart = System.currentTimeMillis();
                try {
                	
                    buildJMSResources();
                } catch(Exception e) {
                    //Log.logger.log(Level.INFO, (new StringBuilder("Exception received connecting to secondary host: ")).append(e).toString());
                    //throw e;
                }
                long now = System.currentTimeMillis();
                reconnectTime = now - reconnectStart;
                
                if(reconnectTime > maxReconnectTime){
                	maxReconnectTime =  reconnectTime;
                }
                
                if(reconnectTime < minReconnectTime){
                	minReconnectTime =  reconnectTime;
                }
                
                Log.logger.log(Level.SEVERE, "Time to connect to secondary host is {0} ms (min: {1} ms  max: {2} ms)", new Object[] {
                        reconnectTime,minReconnectTime,maxReconnectTime
                    });
                
                //reconnectAttempted = true;
                
                synchronized(ReconnectTimer.class){
                	reconnectedThreads++;
         	    }
                if(reconnectedThreads == numThreads){
                   Log.logger.log(Level.SEVERE, "All threads reconnected at {0}", new Object[] {
                		   formatter.format(new Date())
                       });
                   reconnectedThreads=0;
                } 
            //} else {
               // throw storedException;
            //}
        }
        return true;
    }
	
	
}
