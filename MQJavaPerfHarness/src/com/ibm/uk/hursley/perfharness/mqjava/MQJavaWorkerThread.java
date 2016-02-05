/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.mqjava;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;

import com.ibm.mq.*;
import com.ibm.uk.hursley.perfharness.*;

public class MQJavaWorkerThread extends WorkerThread {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;

	protected static DestinationFactory destFactory;
	protected static MQProvider mqprovider;
	
	protected boolean done = false;

	protected boolean ignoreExceptions = false;

	protected MQQueueManager qm = null;

	protected MQQueue queue;
	protected MQQueue inqueue;
	protected MQQueue outqueue;

	private final int writeLastMsgOnShutdown = Config.parms.getInt("ws");
	private final int writeMessageInterval = Config.parms.getInt("wo");

	protected MQMessage inMessage;
	protected MQMessage outMessage;
	
	protected MQGetMessageOptions gmo;
	protected MQPutMessageOptions pmo;
	
	protected int savedGmoWaitInterval = 0; // Save timeout value
	protected int savedGmoMatchOptions = 0; // Save match options
	protected int savedGmoOptions      = 0; // Save options
	protected int savedPmoOptions      = 0; // Save save options
	
	protected MQJavaWorkerThread(String name) {
		super(name);
		mqprovider = MQProvider.getInstance();
		try {
			/*
			 * TODO: Java 8 support target type inference, so when moving to Java 8 can
			 * replace the following line with:
			 * destFactory = Config.parms.<DestinationFactory>getClazz("df").newInstance();
			 */
			destFactory = (DestinationFactory)Config.parms.getClazz("df").newInstance();
		}
		catch (Exception e) {
			Log.logger.log(Level.SEVERE, "Problem getting DestinationFactory class", e);
		}
	}

	public static void registerConfig() {
		Config.registerSelf(MQProvider.class);
		if (!Config.isInvalid())
			Config.registerAnother(Config.parms.getClazz("df"));
	}

	public void writeMessageToFileIfRequested() {
		//if we are writing some of the response msgs to a file and we have have sent numMsgs msgs then append msg to a file
		if (writeMessageInterval <= 0 || ((getIterations() % writeMessageInterval) != 0))
			return;

		try {
			final FileOutputStream out = new FileOutputStream(new File(this.getName() + ".responsemsg"), true);
			try {
				final byte[] buf = new byte[inMessage.getMessageLength()];
				inMessage.readFully(buf);
				out.write(buf);
			}
			finally {
				out.close();
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

			buildMQJavaResources();
			status = sRUNNING;

			Log.logger.log(Level.FINE, "Entering client loop");

			if (startTime == 0)
				startTime = System.currentTimeMillis();

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

				// On shutdown, write the first 100 bytes off the received message to the screen
				if (writeLastMsgOnShutdown > 0) {
					try {
						if (inMessage != null) {
							if (inMessage.getMessageLength() > 0) {
								if (inMessage.getMessageLength() > 200) {
									String line = inMessage.readLine();
									if (line.length() > 200)
										System.out.println("\nResponse msg (Thread " + this.getThreadNum() + ", MsgLength " + inMessage.getTotalMessageLength() + ") : " + line.substring(0, 199) + "...");
									else
										System.out.println("\nResponse msg (Thread " + this.getThreadNum() + ", MsgLength " + inMessage.getTotalMessageLength() + ") : " + line + "...");
								} else
									System.out.println("\nResponse msg (Thread " + this.getThreadNum() + ", MsgLength " + inMessage.getTotalMessageLength() + ") : " + inMessage.readLine());
							} else
								System.out.println("\nResponse msg (" + this.getThreadNum() + ") : ERROR zero length message");
						} else
							System.out.println("\nResponse msg (" + this.getThreadNum() + ") : No MQ message");
					}
					catch (IOException e) {
						if (e.getMessage().contains("MQJE088")) {
							System.out.println("Failed to decode response message");
						} else {
							e.printStackTrace();
						}
					}
				}

				destroyMQJavaResources(false);

				Log.logger.info("STOP");
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
	} // End public void run()

	protected void buildMQJavaResources() throws Exception {
		Log.logger.log(Level.FINE, "Connecting to queue manager");
		qm = mqprovider.getQueueManager();

		gmo = mqprovider.getGMO();
		savedGmoWaitInterval = gmo.waitInterval; 
		savedGmoMatchOptions = gmo.matchOptions; 
		savedGmoOptions      = gmo.options;      

		pmo = mqprovider.getPMO();
		savedPmoOptions = pmo.options;    
	}

	private void destroyMQJavaResources(boolean b) {
		if (queue != null) {
			try {
				Log.logger.log(Level.FINE, "Closing queue " + queue.getName());
				queue.close();
			}
			catch (MQException e) {
			}
			finally {
				queue = null;
			}
		}
		if (inqueue != null) {
			try {
				Log.logger.log(Level.FINE, "Closing queue " + inqueue.getName());
				inqueue.close();
			}
			catch (MQException e) {
			}
			finally {
				inqueue = null;
			}
		}
		if (outqueue != null) {
			try {
				Log.logger.log(Level.FINE, "Closing queue " + outqueue.getName());
				outqueue.close();
			}
			catch (MQException e) {
			}
			finally {
				outqueue = null;
			}
		}
		if (qm != null) {
			try {
				Log.logger.log(Level.FINE, "Closing queue manager " + qm.getName());
				qm.disconnect();
			}
			catch (MQException e) {
			}
			finally {
				qm = null;
			}
		}
	}

	/**
	 * Log an exception.
	 * 
	 * @param e
	 */
	protected void handleException(Throwable e) {
		if (endTime == 0)
			endTime = System.currentTimeMillis();

		Log.logger.log(Level.SEVERE, "Uncaught exception.", e);
		status |= sERROR;
		ControlThread.signalShutdown();
		done = true;
	}
}
