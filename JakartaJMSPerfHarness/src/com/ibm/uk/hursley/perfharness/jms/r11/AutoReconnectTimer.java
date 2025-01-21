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
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Sends a message then receives one from the same queue.  Normal use is with
 * correlation identifier to ensure the same message is received.
 * @author Marc Carter, IBM 
 */
public final class AutoReconnectTimer extends JMS11WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    Message inMessage = null;
    Message outMessage = null;
    String correlID = null;

    static boolean firstExceptionDetected = false;
    static long maxReconnectTime = 0;
    static long minReconnectTime = Long.MAX_VALUE;
    long reconnectStart =0;
    long reconnectTime = 0;
    static String formattedDate = "";
    static int numThreads;
    static int connectedThreads=0;
    static int reconnectedThreads=0;
    static Date initialConnectStartTime=null;
    DateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
    static boolean usingCCDT = false;
    static String firstHost;
	static String secondHost;
    
    
    public static void registerConfig() {
		Config.registerSelf( AutoReconnectTimer.class );
        numThreads = Config.parms.getInt("nt");
        if (!Config.parms.getString("ccdt","").equals("")){
        	usingCCDT = true;
        }
        if(!usingCCDT) {
		   firstHost=Config.parms.getString("jh")+"("+Config.parms.getString("jp")+")";
   		   secondHost=Config.parms.getString("h2")+"("+Config.parms.getString("p2",Config.parms.getString("jp"))+")";
        }
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public AutoReconnectTimer(String name) {
        super(name);
        inMessage = null;
        outMessage = null;
        correlID = null;
        if (!Config.parms.getString("ft").equals("l")){
        	formatter.setTimeZone(TimeZone.getTimeZone("GMT"));	
        }
    }

	protected void buildJMSResources() throws Exception {
		synchronized(AutoReconnectTimer.class){
			if(connectedThreads==0 && initialConnectStartTime == null){
				initialConnectStartTime = new Date();
			}
		}
		//We'll take control of setting up the connection factory from the super class so we can set auto-reconnect on.
		//This could be done in WebSphereMQ.java for all classes (with an auto-reconnect parm of -ar perhaps?) 
		//It's isolated in this class however, as it's the only one with the necessary code to handle possible exceptions
		//during QM switch/fail-over (e.g. transactions failing, or timeouts on calls).
		if ( cf==null ) {
        	Log.logger.log(Level.FINE, "Getting ConnectionFactory");
	        cf = jmsProvider.lookupConnectionFactory(null);
	        ((MQConnectionFactory)cf).setClientReconnectOptions(WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR);
	        ((MQConnectionFactory)cf).setClientReconnectTimeout(100000);
	        if(!usingCCDT) {
       		   ((MQConnectionFactory)cf).setConnectionNameList(firstHost+","+secondHost);
	        }
        }
		super.buildJMSResources();
		
	    synchronized(AutoReconnectTimer.class){
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
    
    private static final int JMSSEND = 0;
    private static final int JMSCOMMIT = 1;
    private static final int JMSRECEIVE = 2;
    private static final String[] cmdNames = {"JMSSEND", "JMSCOMMIT", "JMSRECEIVE"};
    
	boolean exceptionReceived = false;
	
    boolean executeJMSCmd(int cmd) throws Exception{
    	Exception storedException = null;
    	boolean succeeded = true;

    	long JMSCmdStart = System.currentTimeMillis();
    	
    	try {
    	   switch(cmd){
    	   case JMSSEND:
    	      messageProducer.send(outMessage, deliveryMode, priority, expiry);
    	      break;
    	   case JMSRECEIVE:
    			if ((inMessage = messageConsumer.receive(timeout)) != null) {
             
               	} else {
                  	//if(!firstExceptionDetected){
                	//	firstExceptionDetected = true;
               		//throw new Exception("No response to message (" + outMessage.getJMSMessageID() + ")");
               		    System.out.println("No response to message (" + outMessage.getJMSMessageID() + ")");
                  	//}
               		succeeded = false;
               	}
    		  break;
    	   case JMSCOMMIT:
    		  session.commit();
    	      break;
    	   }
    	} catch(jakarta.jms.JMSException e){
    		//We're going to make a big assumption that this is the broken connection
        	//if(!firstExceptionDetected){
        	//	firstExceptionDetected = true;
        		storedException = e;
        		Log.logger.log(Level.SEVERE, (new StringBuilder("Exception received: ")).append(storedException).toString());
        		Log.logger.log(Level.INFO, "{0} messages processed from {1} on primary QM", new Object[] {
                Integer.valueOf(getIterations()), getDestinationName(destProducer)
        		});
        		Log.logger.log(Level.SEVERE, "Backing out transaction after failed " + cmdNames[cmd]);
        		session.rollback();
        	//}
            succeeded = false;
       }
    	
       long JMSApiTime = System.currentTimeMillis() - JMSCmdStart;
       
       if(!succeeded){
        	 if(JMSApiTime < minReconnectTime){
                minReconnectTime =  JMSApiTime;
             } 
        	 
         	 if(JMSApiTime > maxReconnectTime){
                 maxReconnectTime =  JMSApiTime;
             } 
             Log.logger.log(Level.SEVERE, "Time to connect to secondary host is {0} ms (min: {1} ms  max: {2} ms)", new Object[] {
            		 JMSApiTime,minReconnectTime,maxReconnectTime
                  });
           	 
           	reconnectedThreads++;
            if(reconnectedThreads == numThreads){
               Log.logger.log(Level.SEVERE, "All threads reconnected at {0}", new Object[] {
            		   formatter.format(new Date())
                   });
               firstExceptionDetected = false;  
               maxReconnectTime = 0;
               minReconnectTime = Long.MAX_VALUE;
            }
       }
       return succeeded;
    }
    
    /**
     * Send a message to one queue then get it back again. 
     */
	public final boolean oneIteration() throws Exception {
        //System.out.println("Iteration:"+getIterations());
        boolean cmdSuccess;
        
        //PUT
        cmdSuccess = executeJMSCmd(JMSSEND);
        if (cmdSuccess && transacted) {
           cmdSuccess = executeJMSCmd(JMSCOMMIT);
        }
        
        //GET
        if (cmdSuccess){
           cmdSuccess = executeJMSCmd(JMSRECEIVE);
           if (cmdSuccess && transacted){
              executeJMSCmd(JMSCOMMIT);
           }
        }
        
   		incIterations();
        return true;
    }
	
}
