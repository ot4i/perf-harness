/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;

//import com.ibm.mq.*;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.DestinationFactory;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.amqp.utils.BlockingJavaClient;
import com.ibm.uk.hursley.perfharness.amqp.utils.MessageContainer;

/**
 * @author ElliotGregory
 * 
 * Helper class which is extend by the harness classes.
 *
 */
public class AMQPWorkerThread extends WorkerThread {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;
	
	/** Subscriber request topic name has thread Id appended **/
	private final boolean queueByThreadIndex = Config.parms.getBoolean("qt");
	/** The base outbound./request topic name **/
	private final String  outboundTopicBody = Config.parms.getString("ot");
	/** Host name of the server **/
    protected final String host = Config.parms.getString("jh");
    /** Port number of the server **/
	protected final int port = Config.parms.getInt("jp");
	/** Frequency of copying message to a file **/
	private final int writeMessageInterval = Config.parms.getInt("wo");

	
	protected final static Boolean logEnabled = Log.logger.getLevel().intValue() <= Level.FINE.intValue();
	protected static DestinationFactory destFactory;
	
	protected BlockingJavaClient client = null;
	protected boolean done = false;
	protected String inMessage;
	protected MessageContainer outMessage;
//	protected String name;
	
	/**
	 * @param name : name for this thread.
	 */
	protected AMQPWorkerThread(String name) {
		super(name);
		try {
			destFactory = (DestinationFactory)Config.parms.getClazz("df").newInstance();
		}
		catch (Exception e) {
			Log.logger.log(Level.SEVERE, "Problem getting DestinationFactory class", e);
		}
	}

	/**
	 * 
	 */
	public static void registerConfig() {
		Config.registerSelf(MQProvider.class);
		if (!Config.isInvalid())
			Config.registerAnother(Config.parms.getClazz("df"));
	}

	/**
	 * 
	 */
	public void writeMessageToFileIfRequested() {
		//if we are writing some of the response msgs to a file and we have have sent numMsgs msgs then append msg to a file
		if (writeMessageInterval <= 0 || ((getIterations() % writeMessageInterval) != 0))
			return;

		try {
			FileOutputStream out = null;
			try {
				out = new FileOutputStream(new File(this.getName() + ".responsemsg"), true);
				out.write(inMessage.getBytes());
			}
			finally {
				if (out != null) out.close();
			}
		}
		catch (IOException e) {
		}
	}

	/**
	 * General implementation of the main body of a simple JMS primitive.
	 * 
	 * @param paceable
	 *            A paceable instance of WorkerThread.
	 * @param listener
	 *            If not null, this overrides the above method and the given
	 *            object as an asynchronous listener.
	 */
	protected void run(WorkerThread.Paceable paceable) {
		Log.logger.log(Level.INFO, "START");
		try {
			status = sCONNECTING;

			buildAMQPResources();
			status = sRUNNING;

			if (logEnabled) Log.logger.log(Level.FINE, "Entering client loop");

			if (startTime == 0) {
				startTime = System.currentTimeMillis();
			}

			pace(paceable);
			done = true;

		}
		catch (Throwable e) {
			handleException(e);
		}
		finally {
			if (done) {
				status = (status & sERROR) | sENDING;

				if (endTime == 0)
					endTime = System.currentTimeMillis();

				Log.logger.info("STOP");
				close();
				status = (status & sERROR) | sENDED;

				try {
					int wait = Config.parms.getInt("ss");
					if (wait < 0)
						wait = 1;

					Thread.sleep(1000 * wait);
				}
				catch (InterruptedException e) {
				}
				ControlThread.signalShutdown();
			}
		}
	}

	/**
	 * @throws Exception
	 */
	protected void buildAMQPResources() throws Exception {
		String threadName = getName();
		String amqpName = (threadName != null) ? threadName : "AMQP_" + getName();
		client = BlockingJavaClient.create("amqp://" + host + ":" + port, amqpName);
	}
	
	/**
	 * 
	 */
	protected void close () {
		Log.logger.log(Level.SEVERE, "*** Unexpected method call - missing override method ***");
	}


	/**
	 * Log an exception.
	 * 
	 * @param exception
	 */
	protected void handleException(Throwable exception) {
		if (endTime == 0) {
			endTime = System.currentTimeMillis();
		}

		Log.logger.log(Level.SEVERE, "Uncaught exception.", exception);
		status |= sERROR;
		ControlThread.signalShutdown();
		done = true;
	}
	
	/**
	 * @param level
	 * @param msg
	 * @param arguments
	 */
	public static void log(final Level level, String msg, Object... arguments) {
		Log.logger.log(Level.FINE, "AMQP " + msg, arguments);
	}
	
	/**
	 * @return the outbound/request topic
	 */
	protected String getOutboundTopic () {
        return queueByThreadIndex 
        		? outboundTopicBody + "." + getThreadNum() 
        		: outboundTopicBody;
	}
	
	/**
	 * @author ElliotGregory
	 *
	 */
	public static class AMQPException extends Exception {
		private static final long serialVersionUID = 1L;

		/**
		 * @param message
		 */
		public AMQPException(final String message) {
			super(message);
		}
		
		/**
		 * @param message
		 * @param cause
		 */
		public AMQPException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

}
