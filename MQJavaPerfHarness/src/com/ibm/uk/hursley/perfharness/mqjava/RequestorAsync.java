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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends messages on the request queue and waits for the replies on the reply queue using separate
 * background threads to avoid changing the put rate. Assumes "message ID to correl ID" pattern.
 * The goal of this driver code is to enable testing at a constant message rate while still keeping
 * track of message response times and timeouts; this cannot be achieved easily without using an
 * asynchronous message handling pattern.
 * 
 * The basic structure of this class is similar to the other worker classes, but the WorkerThread
 * statistics are updated from a background thread (a get thread of the timeout thread). The queue
 * terminology is server-based, so this class puts messages to the input queue and gets messages
 * from the output queue, which may not be intuitive at first glance.
 * 
 * This class has three sets of threads:
 * 
 * 1) The main worker threads started by the perfharness framework. These threads are similar to 
 *    those in other classes (such as Sender and Requestor) but only put messages to the input queue.
 * 2) The GetReplyMessagesThreads started by the main worker threads. There is one GetReplyMessage
 *    thread for each main worker thread (as many as specified by the -nt parameter) but they do not
 *    not filter messages when calling MQGET: any message on the output queue may be picked up by
 *    any of the threads. This means that it is possible for one reply thread to handle all of the
 *    response messages, and while this leads to unbalanced statistics, it is not an error.
 * 3) The single timeout thread, which handles all timeouts for the process. It does this by 
 *    waiting until a message expiry time is reached, and then checking to see if the message has
 *    been received. If it has, then no action is needed, but if not then it is removed from the 
 *    message ID map and flagged as a timeout. This is a fast enough process that only one thread
 *    is needed for the whole process.
 * 
 * The WorkerThread keeps track of four counters as well as response time statistics:
 * 
 * - iterations: counts the number of messages sent by this worker thread
 * 
 * - responses: counts the responses received by the GetReplyMessagesThread started by this
 *   worker thread. Note that the responses could be received by any thread, and so the
 *   responses count may not be close to the iterations count for the thread (and could be 0)
 * 
 * - unknownMessages: counts the number of messages received that could not be matched
 * 
 * - timeouts: counts how many messages were never matched by a resposne within the timeout
 *
 * Messages are tracked using an InFlightMessageDetails class, with one of them per message
 * sent out by the oneIteration() method. This class contains the messageID, which will be
 * returned in the response as a the correlID (allowing matching), the put time (for the
 * statistics), and the expiry time (for the timeout thread to use). The same object is put
 * on the timeout queue so the timeout thread can handle expiry, but the common case is 
 * likely to involve one of the GetReplyMessagesThreads handling the message first.
 * 
 * The timeout and reply threads can both remove the entry from the shared ConcurrentHashMap
 * that stores the InFlightMessageDetails objects, and the status of the reply message is 
 * determined by which thread removes it from the map: if the timeout thread is able to remove
 * it, then the reply message did not arrive in time and so it is counted as timed out, while
 * the reply thread counts it as a reponse if it is able to remove it from the map before the
 * timeout thread gets to it.
 * 
 * Unknown messages are those which arrive without a correlID that can be matched in the map,
 * which means they are either late arrivals that have already timed out or else messages from
 * other applications using the same queue.
 * 
 * A partial overview looks as follows:
 * 
 *  +--------------+                +----------------+
 *  |              |                |                |
 *  | WorkerThread | Calls          | RequestorAsync |
 *  |              | oneIteration() |                |
 *  |              |===============>| Puts a message |
 *  |              |                | on the input Q |
 *  |              |                |                |                                +--------------+
 *  |              |                | Adds messageID |===============================>| TimeoutQueue |
 *  |              |                | to the CHM and |                                |              |
 *  |              |                | timeout queue  |      +-------------------+     | Stores the   |
 *  |              |                |                |=====>| ConcurrentHashMap |     | messageIDs   |
 *  |              |                |                |      |                   |     | and expiry   |
 *  |              |  Returns       |                |      | Indexed by byte[] |     | times        |
 *  |              |<===============|                |      | and stores the    |     +--------------+
 *  |              |                |                |      | message IDs along |            ||
 *  |              |                +----------------+      | with expiry time  |            ||
 *  |              |                                        | and put time.     |            \/
 *  |              |                                        +-------------------+     +---------------+
 *  |              |                                          ||              /\      | TimeoutThread |
 *  |              |                                          \/              ||      |               |
 *  | Counters:    |                   +-------------------------+            ||      | Waits for the |
 *  |  iterations  |                   | GetReplyMessagesThread  |            ||      | timeout queue |
 *  |  responses   |                   | (calls getMessages())   |            ||      | entry expiry  |
 *  |  timeouts    |                   |                         |            ||      | time to occur |
 *  |  unknowns    | Calls             | Receives a message from |            ||      |               |
 *  |              | incResponses()    | the output Q and looks  |            \=======| Removes CHM   |
 *  |              |<==================| up the correlID in the  |                    | entry         |
 *  |              |                   | CHM to find the start   |                    +---------------+
 *  |              |                   | time for statistics.    |                    
 *  +--------------+                   +-------------------------+
 *   
 * The ConcurrentHashMap uses the MQByteArrayHolder class as a key because a plain byte[] is not
 * usable as a key by itself. The InFlightMessageDetails objects can be allocated from the heap
 * and then returned, but they can also be re-used by putting them on a return queue where they
 * can be picked up by the oneIteration() class for the next message. This saves heap operations
 * but it is unclear how helpful this is in practice, and so the behavior can be switched using
 * the "-rhu" option.
 * 
 * The messageID-to-correlID pattern is assumed, as that is a common pattern in the MQ world. It
 * would be possible to change the code to use messageID-to-messageID or correlID-to-correlID as
 * alternatives, but this has not been implemented in the first version. If this Requestor is used
 * with the standard Responder class, then "-co true" is needed on the Responder command line.
 * 
 * Message expiry is handled using the "-ex" parameter, with a default of five seconds. Setting
 * this to zero disables the timeout behavior (including the background thread), but otherwise 
 * the timeout thread will expire messages in line with the parameter.
 * 
 * This class does not require messages to be sent transactionally, but very fast responders may
 * send replies back so quickly that the oneIteration() method has not had time to populate the
 * messageID map before the messages arrive, so the message will incorrectly be classed as unknown.
 * The IBM App Connect aggregation nodes can suffer from this issue also, and the solution is to
 * enable transactional behavior using "-tx true": this tells the code to call MQCMIT after the
 * map has been updated, which means that the responses can never be received before the map
 * contents are in place.
 *  
 * Example command to run this requestor:
 * 
 * java -cp /opt/mqm/java/lib/com.ibm.mq.allclient.jar:/home/tdolby/github.com/perf-harness/PerfHarness/build/perfharness.jar JMSPerfHarness -tc mqjava.RequestorAsync -nt 10 -ss 1 -sc AsyncResponseTimeStats -wi 5 -rl 60 -mf somefile -jb ACEv12_QM -iq ACE.INPUT.QUEUE -oq ACE.REPLY.QUEUE -jt mqb -rt 100 -tx true
 *
 * Example responder command:
 * 
 * java -cp /opt/mqm/java/lib/com.ibm.mq.allclient.jar:/home/tdolby/github.com/perf-harness/PerfHarness/build/perfharness.jar JMSPerfHarness -tc mqjava.Responder -nt 20 -ss 1 -sc BasicStats -wi 10 -to 3000 -rl 3600 -sh false -ws 1 -jb ACEv12_QM -iq ACE.INPUT.QUEUE -oq ACE.REPLY.QUEUE -jt mqb -co true
 * 
 **/
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
	public final boolean reduceHeapUsage = Config.parms.getBoolean("rhu");
	

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
		WINDOWSIZE = Config.parms.getInt("wti") * TIME_PRECISION;
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

		//for ( int i=0 ; i<1000 ; i++ )
		//{
		//	messagesToReUse.add(new InFlightMessageDetails());
		//}

		Log.logger.log(Level.FINE, "Opening for input {0}", iq);
		inqueue = qm.accessQueue(iq, mqoo);
		outMessage = mqprovider.createMessage(getName());

		if (replyToQmgr != null && !replyToQmgr.equals("")) {
			outMessage.replyToQueueManagerName = replyToQmgr;
		}

		outMessage.replyToQueueName = oq;

		outMessage.report = CMQC.MQRO_COPY_MSG_ID_TO_CORREL_ID;

		inMessage = new MQMessage();

		RFHFormat = Config.parms.getInt("rf");
		NewGetMessage = Config.parms.getInt("mm");
		getThread = new GetReplyMessagesThread(this);
        getThread.start();

		if ( expiryInMilliSeconds > 0 )
		{
			// Slightly hacky but using the object monitor works . . .
			synchronized (timeoutQueue)
			{
				if ( timeoutThread == null )
				{
					timeoutThread = new TimeoutThread();
        			timeoutThread.start();
				}
			}
		}

		// Wait for the get thread to start completely (MQCONN delays might affect results)
		for ( int i=0 ; i<100 ; i++ )
		{
			if ( getThreadIsReady.get() )
			{
				break;
			}
			Thread.sleep(100);
		}
	}

	protected void destroyMQJavaResources(boolean b) {
		stopping = true;
		boolean successfullyStopped = false;
		try{
			getThread.join(2000);
			successfullyStopped = true;
		} catch (Throwable e){ 
		}
		if (!successfullyStopped){
			System.out.println("Waiting for messages to stop arriving on the reply queue");
			try{
				getThread.join(60000);
			} catch(Throwable e){
				System.out.println("Giving up waiting");
			}
			
		}
		super.destroyMQJavaResources(b);
	}

    public static ConcurrentHashMap<MQByteArrayHolder, InFlightMessageDetails> messageIDsInFlight = new ConcurrentHashMap<MQByteArrayHolder, InFlightMessageDetails>();
    public static ConcurrentLinkedQueue<InFlightMessageDetails> timeoutQueue = new ConcurrentLinkedQueue<InFlightMessageDetails>();


	public boolean oneIteration() throws Exception {

		long startTime = System.nanoTime();
		if ( expiryInMilliSeconds > 0 )
		{
			outMessage.expiry = expiryInMilliSeconds/100;
		}
		outMessage.report = CMQC.MQRO_COPY_MSG_ID_TO_CORREL_ID;
		inqueue.put(outMessage, pmo);
		InFlightMessageDetails imd = getOrCreateInFlightMessageDetails(outMessage.messageId, startTime, System.currentTimeMillis() + expiryInMilliSeconds);
		messageIDsInFlight.put(imd.messageID, imd);
		timeoutQueue.add(imd);
		//System.out.println("In oneIteration() - correlId "+imd.messageID+" added to hashmap; imd.messageID.dataHashCode "+imd.messageID.dataHashCode);
		if (transacted)
			qm.commit();


		incIterations(false);
		
		return true;
	}

	public void getMessages() throws Exception 
	{
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
		MQByteArrayHolder messageIdHolder = new MQByteArrayHolder(24);
		boolean gotMessageOnLastIteration = false;

		while ( !stopping || gotMessageOnLastIteration)
		{
			getThreadIsReady.set(true);
			try {
				gotMessageOnLastIteration = false;
				outqueue.get(inMessage, gmo);
				gotMessageOnLastIteration = true;
			}
			catch ( com.ibm.mq.MQException mqe ) {
				if ( mqe.reasonCode != 2033 ){
					mqe.printStackTrace();
					Thread.sleep(1000);
				}
				if (transacted)
					qmHConnForGetThread.commit(); // Not sure this is needed, but it does no harm
				continue;
			}
			catch (Throwable e){
				e.printStackTrace();
				if (transacted)
					qmHConnForGetThread.commit(); // Not sure this is needed, but it does no harm
				Thread.sleep(1000);
			}

			if (transacted)
				qmHConnForGetThread.commit();

			messageIdHolder.setData(inMessage.correlationId);

			InFlightMessageDetails messageDetails = messageIDsInFlight.remove(messageIdHolder);
			if ( messageDetails == null )
			{
				incUnknownMessages();
				System.out.println("In RequestorAsync.getMessages - correlId "+messageIdHolder+" did not match (transacted "+transacted+") messageIdHolder.dataHashCode "+messageIdHolder.dataHashCode);
			}
			else 
			{
				incResponses(messageDetails.startTime, System.nanoTime());
			}
		}
		
		return;
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
					RequestorAsync.InFlightMessageDetails imd = RequestorAsync.timeoutQueue.peek();
					if ( imd == null )
					{
						Thread.sleep(50);
						continue;
					}
					if ( imd.expiryZeroHour < System.currentTimeMillis() )
					{
						// We're the only thread taking messages from this queue, so this is safe, and
						// should never fail if we're the only timeout thread (which we should be).
						if ( RequestorAsync.timeoutQueue.poll() != imd )
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
						RequestorAsync.returnInFlightMessageDetails(imd);
						continue;
					}

					Thread.sleep(50);
				}
			}
			catch (Throwable e)
			{
				e.printStackTrace();
				System.exit(98);
			}
		}
	}

	public class InFlightMessageDetails
	{
		public MQByteArrayHolder messageID;
		public long startTime;
		public long expiryZeroHour;
		public InFlightMessageDetails()
		{
			messageID = new MQByteArrayHolder(24);
			startTime = -1;
			expiryZeroHour = -1;
		}
		public InFlightMessageDetails(byte[] messageIDAsBytes, long startTime, long expiryZeroHour)
		{
			messageID = new MQByteArrayHolder(24);
			messageID.setData(messageIDAsBytes);
			this.startTime = startTime;
			this.expiryZeroHour = expiryZeroHour;
		}

		public void resetValues(byte[] messageIDAsBytes, long startTime, long expiryZeroHour)
		{
			messageID.setData(messageIDAsBytes);
			this.startTime = startTime;
			this.expiryZeroHour = expiryZeroHour;
		}
	}


    public static ConcurrentLinkedQueue<InFlightMessageDetails> messagesToReUse = new ConcurrentLinkedQueue<InFlightMessageDetails>();

	public InFlightMessageDetails getOrCreateInFlightMessageDetails(byte[] messageIDAsBytes, long startTime, long expiryZeroHour)
	{
		InFlightMessageDetails imd = null;
		if ( reduceHeapUsage )
		{
			imd = messagesToReUse.poll();
		}

		if ( imd == null )
		{
			imd = new InFlightMessageDetails(messageIDAsBytes, startTime, expiryZeroHour);
		}
		else
		{
			imd.resetValues(messageIDAsBytes, startTime, expiryZeroHour);
		}
		return imd;
	}

	public static void returnInFlightMessageDetails(InFlightMessageDetails imd)
	{
		// threadToUseForTimeoutStats will have been set before we get here
		if ( RequestorAsync.threadToUseForTimeoutStats.reduceHeapUsage )
		{
			messagesToReUse.add(imd);
		}
	}
}
