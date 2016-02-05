/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.http;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.tcpip.TCPIPProviderBase;


public final class HTTPRequestor extends WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE
	protected static HTTPProvider httpprovider;
	private Socket socket = null;
	private BufferedOutputStream httpWriter;
	private DataInputStream httpReader;
	private int nummsgs = 0;

	private int writeResponseEveryN = 0;
	private int printResponse = 0;
	private int receiveBufferSize = 10000;
	private CharBuffer respBuf = null;
	private int clientSleepTime = 0;
	private boolean errorsAllowed = false;
	private int errorLimit = 0;
	private int errorSleep = 1000; 
	private int errorCount = 0;
	private int msgLimitPerPersistConn = 0;
	private int mcMsgCount = 0;

	private int connectionReset = Config.parms.getInt("cr");
	private final int connectionResetOriginal = connectionReset;

	public static void registerConfig() {
		Config.registerSelf(HTTPProvider.class);
		HTTPProvider.registerConfig();
		httpprovider = HTTPProvider.getInstance();
	}

	/**
	 * Constructor for JMSClientThread.
	 * 
	 * @param name
	 */
	public HTTPRequestor(String name) {
		super(name);
	}

	public BufferedOutputStream getHTTPWriter() {
		httpWriter = null;
		try {
			// Create a socket to the host
			if (socket == null)
				socket = HTTPProvider.USE_SSL ? httpprovider.getSSLSocket() : httpprovider.getSocket();
				httpWriter = new BufferedOutputStream(socket.getOutputStream());
		}
		catch (Exception e) {
			// TODO: handle exception
			System.out.println("ERROR: get writer " + e);
		}
		return httpWriter;
	}

	public DataInputStream getHTTPReader() {
		httpReader = null;
		try {
			// Create a socket to the host
			if (socket == null)
				socket = HTTPProvider.USE_SSL ? httpprovider.getSSLSocket() : httpprovider.getSocket();
			httpReader = new DataInputStream(socket.getInputStream());
		}
		catch (Exception e) {
			System.out.println("ERROR: get reader " + e);
			// TODO: handle exception
		}
		return httpReader;
	}

	public void run() {
		run(this);
	}

	public void run(WorkerThread.Paceable paceable) {
		try {
			Log.logger.log(Level.INFO, "START");

			status = sCONNECTING;
			Log.logger.log(Level.FINE, "Connecting to HTTP SERVER");

			// load messages
			httpprovider.loadMessages();

			// Are we going to write any response messages to a file and if so
			// after how many msgs
			writeResponseEveryN = Config.parms.getInt("wo");
			printResponse = Config.parms.getInt("ws");

			// set the read buffer size in which we will use to store the reply message
			receiveBufferSize = Config.parms.getInt("rb", 10000);
			respBuf = ((writeResponseEveryN > 0) || (printResponse > 0) ) ? CharBuffer.allocate(receiveBufferSize) : null;

			// Get the number of messages to send
			nummsgs = Config.parms.getInt("nm", 0);

			// Are we going to sleep after we send a message? If so how long.
			clientSleepTime = Config.parms.getInt("sl");

			Log.logger.log(Level.FINE, "Entering client loop");

			// get connections here if we want to use persistent HTTP connections, this
			// is a one time hit
			getHTTPWriter();
			getHTTPReader();

			// Check if we are limiting number of messages sent per persistent connection
			msgLimitPerPersistConn = httpprovider.getMsgLimitPerPersistConn();
			mcMsgCount = 1;

			// About to enter main loop so set it to say we are running
			status = sRUNNING;

			errorsAllowed = Config.parms.getBoolean("ea");
			errorLimit =  Config.parms.getInt("el");
			errorSleep = Config.parms.getInt("es");			
			if ( errorLimit > 0 ) errorsAllowed = true;
			
			errorCount = 0;
			try {
				pace(paceable);
			}
			catch (Exception e) {
				System.out.println("ERROR In main HTTP Client thread: \n" + e);

				// If connection reset allowed
				if (connectionReset > 0) {
					Log.logger.log(Level.FINE, "Resetting connection " + connectionReset + " of " + connectionResetOriginal);
					connectionReset--;
					socket.close();
					socket = null;
					getHTTPWriter();
					getHTTPReader();
				} else {
					throw e;
				}
			}
		}
		catch (Exception e) {
			// Handle a fatal error
			status |= sERROR;
			Log.logger.log(Level.SEVERE, "Fatal Error.", e);
			ControlThread.signalShutdown();
		}
		finally {
			if ((respBuf != null) && printResponse > 0) {
				respBuf.flip();
				if (respBuf.length() > 0) {
					if (respBuf.length() > 200) {
						String line = respBuf.toString();
						if (line.length() > 200)
							System.out.println("\nResponse msg (Thread " + this.getThreadNum() + ", MsgLength " + respBuf.length() + ") : " + line.substring(0, 199) + "...");
						else
							System.out.println("\nResponse msg (Thread " + this.getThreadNum() + ", MsgLength " + respBuf.length() + ") : " + line + "...");
					} else
						System.out.println("\nResponse msg (Thread " + this.getThreadNum() + ", MsgLength " + respBuf.length() + ") : " + respBuf.toString());
				} else
					System.out.println("\nResponse msg (" + this.getThreadNum() + ") : ERROR zero length message");		
			}
			
			// Clear up code carefully in fair weather or foul.
			status = (status & sERROR) | sENDED;

			if (endTime == 0)
				endTime = System.currentTimeMillis();

			try {
				httpReader.close();
				httpWriter.close();
				if (socket != null)
					socket.close();
			}
			catch (Exception e) {
				// TODO: handle exception
			}
			Log.logger.log(Level.INFO, "STOP");
		}
	} // End public void run()

	private boolean readResponse(CharBuffer buf) throws NumberFormatException, IOException {
		// read the data from the socket, we use a bytes buffer that
		// is our read buffer size + outbound msg size
		if (buf != null)
			buf.clear();

		int contentlength = 0;
		final BufferedReader br = new BufferedReader(new InputStreamReader(getHTTPReader()));

		int startContLength = -1;
		int dataChunked = -1;
		int timeoutCounter = 0;
		Boolean fourOfour = false;
		for (;;) {
			try {
				final String lineRead = br.readLine();
				if (lineRead == null) {
					Log.logger.log(Level.INFO, "ERROR: Stream ended while reading the header");
					if (errorsAllowed) {
						return false;						
					} else {
						throw new IOException("Stream ended while reading the header");
					}
				}
				
				// reset timeout counter after each successfully read line
				timeoutCounter = 0;

				if (lineRead.equals(""))
					// end of header is signified by a blank line
					break;
				if (lineRead.equals("HTTP/1.1 404 Not Found")) 
					fourOfour = true;
				
				// If we have not already found the Content-Length flag check for it
				if (startContLength == -1 && dataChunked == -1) {
					startContLength = lineRead.indexOf("Content-Length: ");
					// Once we have found the flag find the value of the Length
					if (startContLength != -1)
						contentlength = Integer.valueOf(lineRead.substring(startContLength + 16, lineRead.length())).intValue();
				}
				if (dataChunked == -1 && startContLength == -1)
					dataChunked = lineRead.indexOf("Transfer-Encoding: chunked");
			}
			catch (SocketTimeoutException e) {
				// this exception signals that the I/O operation (read a line from the HTTP header)
				//   has timed out and causes us to check various conditions pertaining to the timeout
				//   counter and evaluate the 'shutdown' boolean variable as part of the loop condition
				if (timeoutCounter >= TCPIPProviderBase.timeoutNumIntervals) {
					// number of consecutive allowed timeout events has been exceeded -- return and indicate failure
					Log.logger.log(Level.INFO, "TIMEOUT");
					return false;
				} else
				if (shutdown) {
					// check for asynchronous shutdown, but only within the timeout handler -- as long
					//   as we can keep reading read the stream without ever timing out, carry on
					// receiving an async shutdown in the timeout handler means that we can't finish
					//   reading the response, so interpret it as failure
					return false;
				} else
					// number of allowed timeouts has not yet been reached -- carry on
					timeoutCounter++;
			}
		}
		
		// If we got a 404 error
		if (fourOfour) {
			char[] cbuf = new char[contentlength];
			br.read(cbuf, cbuf.length - contentlength, contentlength);
			Log.logger.log(Level.SEVERE, "ERROR: HTTP/1.1 404 Not Found");				
			Log.logger.log(Level.SEVERE, String.valueOf(cbuf));
			System.exit(1);
		}


		// read the message body
		if (startContLength != -1) {
			return readToBuf(br, buf, contentlength);
		} else
		if (dataChunked != -1) {
			int resume = 0;
			String lineRead = null;
			int chunkLength = -1;
			timeoutCounter = 0;
			for (;;) {
				try {
					switch (resume) {
						// Mechanism to allow recovery from SocketTimeoutException in any
						// of the readLine() calls below and continue where left off.
						// (Unfortunately, this has not been tested, and I am not sure how
						// well readLine() works with SocketTimeExceptions. read() works quite
						// well, but skip() does not. Go figure.)
					default:
						resume = 0;
						lineRead = br.readLine();						// get length value
						if (lineRead == null)
							return false;								// unexpected end of stream: indicate failure
						timeoutCounter = 0;								// reset after every successful read
						chunkLength = Integer.parseInt(lineRead, 16);
					case 1:
						resume = 1;
						if (chunkLength == 0) {
							br.readLine();								// zero-length chunk followed by a newline
							return true;								// success: all chunks have been read
						} else
						if (!readToBuf(br, buf, chunkLength))			// readToBuf() employs an independent timeoutCounter
							return false;								// propagate failure
					case 2:
						resume = 2;
						br.readLine();									// a chunk is always followed by a newline
					}
					resume = 0;
				}
				catch (SocketTimeoutException e) {
					// this exception signals that the I/O operation (read line) has timed out
					if (timeoutCounter >= TCPIPProviderBase.timeoutNumIntervals) {
						// number of consecutive allowed timeout events has been exceeded -- return and indicate failure
						Log.logger.log(Level.INFO, "TIMEOUT");
						return false;
					} else
					if (shutdown) {
						// check for asynchronous shutdown, but only within the timeout handler -- as long
						//   as we can keep reading read the stream without ever timing out, carry on
						// receiving an async shutdown in the timeout handler means that we can't finish
						//   reading the response, so interpret it as failure

						return false;
					} else
						// number of allowed timeouts has not yet been reached -- carry on
						timeoutCounter++;
				}
			}
		} else {
			System.out.println("We have failed to find either \"Content-Length:\" or \"Transfer-Encoding: chunked\" in the header");
			return false;
		}
	}
	private final boolean readToBuf(BufferedReader br, CharBuffer buf, int n) throws IOException {
		if (buf == null) {
			char[] cbuf = new char[n];
			int timeoutCounter = 0;
			for (;;) {
				try {
					while (n > 0) {
						final int nread = br.read(cbuf, cbuf.length - n, n);
						if (nread > 0) {
							n -= nread;
							timeoutCounter = 0;
						}
					}
					return true;
				}
				catch (SocketTimeoutException e) {
					// this exception signals that the I/O operation (read line) has timed out
					if (timeoutCounter >= TCPIPProviderBase.timeoutNumIntervals) {
						// number of consecutive allowed timeout events has been exceeded -- return and indicate failure
						Log.logger.log(Level.INFO, "TIMEOUT");
						return false;
					} else
					if (shutdown) {
						// check for asynchronous shutdown, but only within the timeout handler -- as long
						//   as we can keep reading read the stream without ever timing out, carry on
						// receiving an async shutdown in the timeout handler means that we can't finish
						//   reading the response, so interpret it as failure

						return false;
					} else
						// number of allowed timeouts has not yet been reached -- carry on
						timeoutCounter++;
				}
			}
		} else {
			final int newLimit = buf.position() + n;
			if (newLimit < 0)
				throw new IllegalArgumentException("Not really sure what happened here...");
			if (newLimit > buf.capacity())
				throw new IllegalArgumentException("The read buffer is too small to hold the response received.");
			buf.limit(newLimit);
			int nToRead = n;
			int timeoutCounter = 0;
			while (nToRead > 0) {
				try {
					final int nRead = br.read(buf);
					if (nRead > 0) {
						nToRead -= nRead;
						timeoutCounter = 0;
					} else
					if (nRead < 0)
						return false;
				}
				catch (SocketTimeoutException e) {
					// this exception signals that the I/O operation has timed out
					if (timeoutCounter >= TCPIPProviderBase.timeoutNumIntervals) {
						// number of consecutive allowed timeout events has been exceeded -- shut down
						Log.logger.log(Level.INFO, "TIMEOUT");
						return false;
					} else
					if (shutdown) {
						// check for asynchronous shutdown, but only within the timeout handler -- as long
						//   as we can keep reading read the stream without ever timing out, carry on
						// receiving an async shutdown in the timeout handler means that we can't finish
						//   reading the response, so interpret it as failure

						return false;
					} else
						// number of allowed timeouts has not yet been reached -- carry on
						timeoutCounter++;
				}
			}
			return (nToRead <= 0);
		}
	}
	public boolean oneIteration() throws Exception {
		final int iIter = getIterations();
		final int iMessage = httpprovider.getMessageIndex(iIter, getThreadNum() - 1);
		final byte[] data = httpprovider.getBytesMessage(iMessage);
		final String header = httpprovider.createHeader(iMessage, getThreadNum());
		if ((getThreadNum() == 1) && (iIter == 1)) {
			Log.logger.log(Level.INFO, "Message Size = " + httpprovider.getMessageSize(iMessage));
			Log.logger.log(Level.INFO, header);
		}

		startResponseTimePeriod();

		httpWriter.write(header.getBytes());		// Send header
		httpWriter.write(data);						// Send data
		httpWriter.flush();							// Flush buffer onto the wire

		if (!readResponse(respBuf)) {
			errorCount++;
			if( errorsAllowed ) {
				java.util.Date date= new java.util.Date();
				System.out.println(new Timestamp(date.getTime()) + " - Thread-" + getThreadNum() + ": " + errorCount + "/" + errorLimit + " ERRORS");
				//Due to an error make a new connection
				socket.close();
				socket = null;
				getHTTPWriter();
				getHTTPReader();
				//Sleep for a second to prevent tight loops 
				try {
				    Thread.sleep(errorSleep);                 
				} catch(InterruptedException ex) {
				    Thread.currentThread().interrupt();
				}
			}
			if((errorCount > errorLimit && errorsAllowed && errorLimit != 0 ) || !errorsAllowed) {
				// if readResponse() fails, abandon ship
				shutdown = true;
				if (errorLimit > 0)
					System.out.println("Thread-" + getThreadNum() + " SHUTDOWN");
				return true;
			}
		}

		if (incIterations() == nummsgs) {
			shutdown = true;
		}
				
		// if we are writing some of the response msgs to a file and
		// we have have sent numMsgs msgs then append msg to a file
		if ((respBuf != null) && (writeResponseEveryN>0) && ((getIterations() % writeResponseEveryN) == 0)) {
			final File responsemsg = new File(this.getName() + ".responsemsg");
			final FileOutputStream out = new FileOutputStream(responsemsg, true);
			respBuf.flip();
			Log.logger.log(Level.INFO, "Bytes read in: " + respBuf.length() + "; written to \"" + responsemsg.getCanonicalPath() + "\"");
			out.write(Charset.defaultCharset().encode(respBuf).array());
			out.close();
		}

		// If using NonPersistent connections, close all then reopen ready for next send
		// or reset if using Persistent connections and reached a msg limit
		if ((HTTPProvider.USE_NP_CONNECTIONS) || (mcMsgCount == msgLimitPerPersistConn)) {
			Log.logger.log(Level.FINE, "Closing connection after " + mcMsgCount + " messages");
			mcMsgCount = 0;
			socket.close();
			socket = null;
			getHTTPWriter();
			getHTTPReader();
		}
		if (msgLimitPerPersistConn > 0)
			mcMsgCount++;
		if (clientSleepTime > 0)
			sleep(clientSleepTime);
		return true;
	}
}
