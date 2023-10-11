/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.mqjava;

import java.util.logging.Level;
import com.ibm.mq.*;
import com.ibm.mq.constants.CMQC;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends messages on the request queue and then waits for the replies on the reply queue.
 * Assumes "message ID to correl ID" pattern.
 * 
 * java -cp /opt/mqm/java/lib/com.ibm.mq.allclient.jar:/home/tdolby/github.com/perf-harness/PerfHarness/build/perfharness.jar JMSPerfHarness -tc mqjava.RequestorAsync -nt 1 -ss 1 -sc BasicStats -wi 10 -to 3000 -rl 60 -sh false -ws 1 -mf testdb-odbc.ini -jb ACEv12_QM -iq ACE.INPUT.QUEUE -oq ACE.REPLY.QUEUE -jt mqb -rt 10
 * 
 * java -cp /opt/mqm/java/lib/com.ibm.mq.allclient.jar:/home/tdolby/github.com/perf-harness/PerfHarness/build/perfharness.jar JMSPerfHarness -tc mqjava.Responder -nt 1 -ss 1 -sc BasicStats -wi 10 -to 3000 -rl 600 -sh false -ws 1 -jb ACEv12_QM -iq ACE.INPUT.QUEUE -oq ACE.REPLY.QUEUE -jt mqb
 */
public final class RequestorAsync extends MQJavaWorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    protected static MQProvider mqprovider;

    private final boolean transacted = Config.parms.getBoolean( "tx" );

    private final boolean getMsgById = Config.parms.getBoolean( "mi" ); 

	private final boolean correlateMsg = Config.parms.getBoolean("co");
	private final int msgsToSendBeforeGetResp = Config.parms.getInt( "ir" ); //input to out put ratio, def =1  e.g. 3 means send 3 then get 1.
	private final int msgsToGetBeforePutReq = Config.parms.getInt( "or" ); //input to out put ratio, default =1  e.g. 3 means get 3 then put 1 msg, used for pubsub fan out.
	private final String replyToQmgr = Config.parms.getString("qm");
	protected final int expiryInMilliSeconds = Config.parms.getInt("ex");
	
	protected MQQueueManager qmHConnForGetThread = null;

	protected GetReplyMessagesThread getThread  = null;

	private AtomicBoolean getThreadIsReady = new AtomicBoolean(false);

	protected static TimeoutThread timeoutThread = null;
	protected static RequestorAsync threadToUseForTimeoutStats = null;
	int RFHFormat = 0;
	int NewGetMessage = 0;


	static {
		Config.registerSelf( RequestorAsync.class );
		MQProvider.registerConfig();
		mqprovider = MQProvider.getInstance();
	}

	/**
	 * Constructor for JMSClientThread.
	 * @param name
	 */
	public RequestorAsync(String name) {
		super(name);
		// This doesn't need a lock, as we only need one thread and it doesn't matter which
		if ( threadToUseForTimeoutStats == null )
			threadToUseForTimeoutStats = this;
	}

	public void run() {
		run(this);
	}

	protected void buildMQJavaResources() throws Exception {
		super.buildMQJavaResources();

		// Get destination pair if multiple are configured.
		final int destID = destFactory.generateDestinationID(getThreadNum());
		final int mdm = Config.parms.getInt("mdm");		// multi-destination numbering minimum
		String iq = Config.parms.getString("iq");
		String oq = Config.parms.getString("oq");

		int	mqoo = CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING;
			if (Config.parms.getBoolean("bf")) {
				mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
			}

			Log.logger.log(Level.FINE, "Opening for input {0}", iq);
			inqueue = qm.accessQueue(iq, mqoo);
		outMessage = mqprovider.createMessage(getName());

		if (replyToQmgr != null && !replyToQmgr.equals("")) {
			outMessage.replyToQueueManagerName = replyToQmgr;
		}

		outMessage.replyToQueueName = oq;

		inMessage = new MQMessage();

		RFHFormat = Config.parms.getInt("rf");
		NewGetMessage = Config.parms.getInt("mm");
		getThread = new GetReplyMessagesThread(this);
        getThread.start();

		// Slightly hacky but using the object monitor works . . .
		synchronized (messagesToTimeOut)
		{
			if ( timeoutThread == null )
			{
				timeoutThread = new TimeoutThread();
        		timeoutThread.start();
			}
		}
	}

	protected void destroyMQJavaResources(boolean b) {
		stopping = true;
		super.destroyMQJavaResources(b);
		try{
			getThread.join(1000);
		} catch (Throwable e){ 
		}
	}

	public boolean oneIteration() throws Exception {
		//startResponseTimePeriod();

		// Wait for the get thread to start completely (MQCONN delays might affect results)
		for ( int i=0 ; i<10 ; i++ )
		{
			if ( getThreadIsReady.get() )
			{
				break;
			}
			Thread.sleep(100);
		}

		long startTime = System.nanoTime();
		if ( expiryInMilliSeconds > 0 )
		{
			outMessage.expiry = expiryInMilliSeconds/100;
		}
		inqueue.put(outMessage, pmo);
		String sentMsgId = getHexString(outMessage.messageId);
		//System.out.println("In RequestorAsync.oneIteration - msgId "+sentMsgId);
		InFlightMessageDetails imd = new InFlightMessageDetails(sentMsgId, startTime, System.currentTimeMillis() + expiryInMilliSeconds);
		messageIDsInFlight.put(sentMsgId, imd);
		messagesToTimeOut.put(imd);

		if (transacted)
			qm.commit();

		//System.out.println("In RequestorAsync.oneIteration - msgId "+getHexString(outMessage.messageId)+" correlId "+getHexString(outMessage.correlationId));

		incIterations();
		//writeMessageToFileIfRequested();
		
		return true;
	}

    public static ConcurrentHashMap<String, InFlightMessageDetails> messageIDsInFlight = new ConcurrentHashMap<String, InFlightMessageDetails>();
    public static LinkedBlockingQueue<InFlightMessageDetails> messagesToTimeOut = new LinkedBlockingQueue<InFlightMessageDetails>();



	public void getMessages() throws Exception {

		// Initialization has to be done here to get a new MQHCONN for the new thread
		if ( qmHConnForGetThread == null )
		{
			qmHConnForGetThread = mqprovider.getQueueManager();
			String oq = Config.parms.getString("oq");
			Log.logger.log(Level.FINE, "Opening for output {0}", oq);
			int mqoo = CMQC.MQOO_INPUT_SHARED | CMQC.MQOO_FAIL_IF_QUIESCING;
			if (Config.parms.getBoolean("bf")) {
				mqoo |= CMQC.MQOO_BIND_NOT_FIXED;
			}
			outqueue = qmHConnForGetThread.accessQueue(oq, mqoo);
		}

		inMessage = new MQMessage();
		
		gmo.waitInterval = 1000;
		gmo.options      = savedGmoOptions;
		gmo.matchOptions = CMQC.MQMO_NONE;

		while ( !stopping )
		{
			getThreadIsReady.set(true);
			try {
				outqueue.get(inMessage, gmo);
			}
			catch ( com.ibm.mq.MQException mqe ) {
				if ( mqe.reasonCode != 2033 ){
					mqe.printStackTrace();
					Thread.sleep(1000);
				}

				if (transacted)
					qmHConnForGetThread.commit();
				continue;
			}
			catch (Throwable e){
				e.printStackTrace();
				if (transacted)
					qmHConnForGetThread.commit();
				Thread.sleep(1000);
			}

			if (transacted)
				qmHConnForGetThread.commit();

			String receivedCorrelId = getHexString(inMessage.correlationId);
			InFlightMessageDetails messageDetails = messageIDsInFlight.remove(receivedCorrelId);
			if ( messageDetails == null )
			{
				incUnknownMessages();
				//System.out.println("In RequestorAsync.getMessages - correlId "+receivedCorrelId+" did not match (transacted "+transacted+")");
			}
			else 
			{
				incResponses(messageDetails.startTime, System.nanoTime());
			}
		}
		
		//writeMessageToFileIfRequested();
		
		return;
	}
    
    public static String getHexString(byte[] b) throws Exception {
        String result = "";
        for (int i = 0; i < b.length; i++) {
            result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }

	public class InFlightMessageDetails
	{
		public String messageID;
		public long startTime;
		public long expiryZeroHour;
		public InFlightMessageDetails(String messageID, long startTime, long expiryZeroHour)
		{
			this.messageID = messageID;
			this.startTime = startTime;
			this.expiryZeroHour = expiryZeroHour;
		}
	}

	static boolean stopping = false;
	public class GetReplyMessagesThread extends Thread 
	{
		RequestorAsync parent;

		public GetReplyMessagesThread(RequestorAsync parent)
		{
			this.parent = parent;
		}
		public void run()
		{
			try
			{
				parent.getMessages();
			}
			catch (Throwable e)
			{
				e.printStackTrace();
				System.exit(98);
			}
		}
	}

	public class TimeoutThread extends Thread 
	{
		public TimeoutThread()
		{
		}
		public void run()
		{
			try
			{
				while ( !RequestorAsync.stopping )
				{
					RequestorAsync.InFlightMessageDetails imd = RequestorAsync.messagesToTimeOut.peek();
					if ( imd == null )
					{
						Thread.sleep(100);
						continue;
					}
					if ( imd.expiryZeroHour < System.currentTimeMillis() )
					{
						// We're the only thread taking messages from this queue, so this is safe
						if ( RequestorAsync.messagesToTimeOut.poll() != imd )
							System.out.println("In TimeoutThread.run - imd.messageID "+imd.messageID+" picked up by someone else!");

						// The normal case is this next call returns null because the message has been claimed by
						// one of the GET threads, so if we actually manage to take the in-flight object then it
						// has definitely timed out. 
						RequestorAsync.InFlightMessageDetails messageDetails = RequestorAsync.messageIDsInFlight.remove(imd.messageID);
						if ( messageDetails != null )
						{
							//System.out.println("In TimeoutThread.run - imd.messageID "+imd.messageID+" removed");
							RequestorAsync.threadToUseForTimeoutStats.incTimeouts();
						}
						continue;
					}

					Thread.sleep(100);
				}
			}
			catch (Throwable e)
			{
				e.printStackTrace();
				System.exit(98);
			}
		}
	}
}
