
/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.tcpip;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;


public final class TCPIPRequestor extends WorkerThread implements WorkerThread.Paceable {
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	protected static TCPIPProvider tcpipprovider;
	private int[] receiveBufferSizeTab = null;
	private boolean HL7 = false;
	private int writeResponseEveryN = 0;
	private int clientSleepTime = 0;
	private int mcMsgCount = 1;
	private int msgLimitPerPersistConn = 0;
	
	private Socket socket = null;
	private DataOutputStream tcpipWriter;
	private DataInputStream tcpipReader;

	private static final boolean sendStringData = false;
	private int nummsgs = 0;

	public static void registerConfig() {
		Config.registerSelf(TCPIPProvider.class);
		TCPIPProvider.registerConfig();
		tcpipprovider = TCPIPProvider.getInstance();
	}

	/**
	 * Constructor for JMSClientThread.
	 * @param name
	 */
	public TCPIPRequestor(String name) {
		super(name);
	}

	public DataOutputStream getTCPIPWriter() throws Exception {
		tcpipWriter = null;
		try {
			// Create a socket to the host
			if (socket == null)
				socket = TCPIPProvider.USE_SECURE ? tcpipprovider.getSSLSocket() : tcpipprovider.getSocket();
			tcpipWriter = new DataOutputStream(socket.getOutputStream());
		}
		catch (Exception e) {
			// TODO: handle exception
			System.out.println("ERROR: get writer " + e);
			throw e;
		}
		return tcpipWriter;
	}

	public DataInputStream getTCPIPReader() throws Exception {
		tcpipReader = null;
		try {
			// Create a socket to the host
			if (socket == null)
				socket = TCPIPProvider.USE_SECURE ? tcpipprovider.getSSLSocket() : tcpipprovider.getSocket();
			tcpipReader = new DataInputStream((socket.getInputStream()));
		}
		catch (Exception e) {
			// TODO: handle exception
			System.out.println("ERROR: get reader " + e);
			throw e;
		}
		return tcpipReader;
	}

	public void run() {
		run(this);
	}

	public void run(WorkerThread.Paceable paceable) {
		try {
			Log.logger.log(Level.INFO, "START");

			HL7 = Config.parms.getBoolean("hl");

			status = sCONNECTING;
			Log.logger.log( Level.FINE, "Connecting to TCPIP SERVER");

			// Are we going to write any response messages to a file and if so after how many msgs
			// TODO: move to provider
			writeResponseEveryN = Config.parms.getInt("wo");

			// Get the number of messages to send
			nummsgs = Config.parms.getInt("nm", 0);

			// Are we going to sleep after we send a message? If so how long.
			// TODO: move to provider
			clientSleepTime = Config.parms.getInt("sl");

			Log.logger.log( Level.FINE, "Entering client loop" );

			// get conns here if we want to use persistent TCPIP connections, this is a one time hit
			getTCPIPWriter();
			getTCPIPReader();

			// Check if we are limiting number of messages sent per persistent connection
			msgLimitPerPersistConn = tcpipprovider.getMsgLimitPerPersistConn();
			mcMsgCount = 1;

			// Load messages
			tcpipprovider.loadMessages();
			for (int k = 0; k < tcpipprovider.getNumMessages(); k++)
				Log.logger.log(Level.INFO, "MessageSize[" + String.valueOf(k + 1) + "] = " + tcpipprovider.getMessageSize(k));
			if (HL7)
				Log.logger.log(Level.INFO, "HL7 message processing, prepending with 0x0B, appending with 0x1C 0x0D");

			// read the read buffer size in which we will use to store the reply message
			receiveBufferSizeTab = Config.parms.getCSIntList("rb", tcpipprovider.getNumMessages(), 10000);

			System.out.println("Sending TCPIP Messages");
			// About to enter main loop so set it to say we are running
			status = sRUNNING;
			
			pace(paceable);

		}
		catch (Exception e) {
			status |= sERROR;
			Log.logger.log(Level.SEVERE, "Fatal Error.", e);
			Log.logger.log(Level.SEVERE, "Localport is " + socket.getLocalPort());
			Log.logger.log(Level.SEVERE, "Port is " + socket.getPort());
			Log.logger.log(Level.SEVERE, "LocalAddress is " + socket.getLocalAddress());
			ControlThread.signalShutdown();
			// Clear up code carefully in fair weather or foul.	
		}
		finally {
			// System.out.println("In TCPIP run method Finally Block: ");
			status = (status & sERROR) | sENDED;
			if (endTime==0) {
				endTime = System.currentTimeMillis();
			}
			try {
				if (tcpipReader != null) {
					tcpipReader.close();
				}
				if (tcpipWriter != null) {
					tcpipWriter.close();
				}
				if (socket != null) {
					socket.close();
				}
			}
			catch (Exception e) {
				// TODO: handle exception
				System.out.println("Exception in TCPIP run method: " + e);
			}
			Log.logger.log(Level.INFO, "STOP");
		}
	} // End public void run()

	@SuppressWarnings("unused")
	public boolean oneIteration() throws Exception {
		// body. = null;

		// Get Message
		String data = null;
		byte[] bytesData = null;
		final int iIter = getIterations();
		final int iMessage = tcpipprovider.getMessageIndex(iIter, getThreadNum() - 1);
		final int receiveBufferSize = receiveBufferSizeTab[iMessage];
		if (sendStringData == true)
			data = tcpipprovider.getStringMessage(iMessage);
		else
			bytesData = tcpipprovider.getBytesMessage(iMessage);

		startResponseTimePeriod();

		// Send data
		if (sendStringData == true) {
			tcpipWriter.writeUTF(data);
		} else {
			// System.out.println("Bytes Length = "+ bytesData.length + new String( bytesData ));
			if (HL7) {
				tcpipWriter.write((byte)0x0B);
				tcpipWriter.write(bytesData);
				tcpipWriter.write((byte)0x1C);
				tcpipWriter.write((byte)0x0D);
			} else {
				tcpipWriter.write(bytesData);
			}
		}

		// Flush buffer onto the wire
		tcpipWriter.flush();
		final byte[] body = new byte[receiveBufferSize];
		int totalBytesRead = 0;
		int timeoutCounter = 0;
		while (totalBytesRead < receiveBufferSize) {
			// we should not exit this loop in response to an asynchronous shutdown request, but
			//   rather try to finish reading the message -- for this reason, we only check the
			//   value of the 'shutdown' boolean variable in this loop within the timeout
			//   exception handler

			// If we have a content length but have not read all
			// the data we need to do another read and recheck.
			try {
				final int n = tcpipReader.read(body, totalBytesRead, receiveBufferSize - totalBytesRead);
				if (n > 0) {
					timeoutCounter = 0;					// reset timeout counter on every successful read
					totalBytesRead = totalBytesRead + n;
				} else
				if (n < 0) 
					throw new Exception("Server unexpectedly closed the connection.");
			}
			catch (SocketTimeoutException e) {
				// this exception signals that the I/O operation has timed out (read:
				//   exceptions are being used for control during normal operation)
				// the exception causes us to evaluate the 'shutdown' boolean variable
				//   as well as part of the loop condition
				if (timeoutCounter >= TCPIPProviderBase.timeoutNumIntervals) {
					// number of consecutive timeout events allowed has been exceeded -- get out
					Log.logger.log(Level.INFO, "TIMEOUT");
					shutdown = true;					// proceed with shutdown after exiting the loop
					break;
				} else
				if (shutdown) {
					// check for asynchronous shutdown, but only within the timeout handler -- as long
					//   as we can keep reading read the stream without ever timing out, carry on
					break;
				} else
					// number of allowed timeouts has not yet been reached -- carry on
					timeoutCounter++;
			}
		}

		if (incIterations() == nummsgs)
			// System.exit(1);
			shutdown = true;

		if ((writeResponseEveryN > 0) && (getIterations() % writeResponseEveryN == 0)) {
			// if we are writing some of the response msgs to a file and we have have sent numMsgs msgs then append msg to a file
			final File responsemsg  = new File(this.getName() + ".responsemsg");
			final FileOutputStream out = new FileOutputStream(responsemsg, true);
			out.write(body, 0, totalBytesRead);
			Log.logger.log(Level.INFO, "Bytes read in: " + totalBytesRead + "; written to \"" + responsemsg.getCanonicalPath() + "\"");
			out.close();
		}

		if (shutdown) {
			Log.logger.log(Level.FINE, "Shutting down");
			socket.close();
			return true;								// exit while loop
		}

		// using np conns so close all, then reopen ready for the next msg.
		if ((TCPIPProvider.USE_NP_CONNECTIONS) || (mcMsgCount == msgLimitPerPersistConn)) {
			Log.logger.log(Level.FINE, "Closing connection after " + mcMsgCount + " messages");
			mcMsgCount = 0;
			socket.close();
			socket = null;
			getTCPIPWriter();
			getTCPIPReader();
		}
		if (msgLimitPerPersistConn > 0)
			mcMsgCount++;
		if (clientSleepTime > 0)
			sleep(clientSleepTime);
		return true;
	}
}
