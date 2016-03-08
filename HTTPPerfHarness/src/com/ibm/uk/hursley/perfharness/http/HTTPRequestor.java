/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.HeadOutputStream;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.MirrorOutputStream;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.tcpip.TCPIPProviderBase;
import com.ibm.uk.hursley.perfharness.util.ByteArray;


public final class HTTPRequestor extends WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE
	protected static HTTPProvider httpprovider;
	private Socket socket = null;
	private BufferedOutputStream httpWriter;
	private BufferedInputStream httpReader;
	private int nummsgs = 0;

	private int writeResponseEveryN = 0;
	private int printResponse = 0;
	private int readBufSize = 16384;		// all HTTP header lines being read, including the terminating CRLF, must fit into a buffer of this size
	private byte[] bReadBuf = null;
	private static final int iPrintResponseBufSize = 200;
	private byte[] bPrintResponseBuf = null;
	private int iPrintResponseBufDataLen = 0;
	private boolean bPrintResponseBufOverflow = false;
	private int iLastResponseMsgLen = -1;

	private int clientSleepTime = 0;
	private boolean errorsAllowed = false;
	private int errorLimit = 0;
	private int errorSleep = 1000; 
	private int errorCount = 0;
	private int msgLimitPerPersistConn = 0;
	private int mcMsgCount = 0;

	private static final byte[] bCRLF = { 0x0D, 0x0A };
	private static final Charset csHTTPHeader = Charset.forName("US-ASCII");

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
				socket = HTTPProvider.USE_SECURE ? httpprovider.getSSLSocket() : httpprovider.getSocket();
				httpWriter = new BufferedOutputStream(socket.getOutputStream());
		}
		catch (Exception e) {
			// TODO: handle exception
			System.out.println("ERROR: get writer " + e);
		}
		return httpWriter;
	}

	public BufferedInputStream getHTTPReader() {
		httpReader = null;
		try {
			// Create a socket to the host
			if (socket == null)
				socket = HTTPProvider.USE_SECURE ? httpprovider.getSSLSocket() : httpprovider.getSocket();
			httpReader = new BufferedInputStream(socket.getInputStream());
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
			if (printResponse > 0)
				bPrintResponseBuf = new byte[iPrintResponseBufSize];

			// set the read buffer size in which we will use to store the reply message
			readBufSize = Config.parms.getInt("rb", 16384);
			bReadBuf = new byte[readBufSize];
			// respBuf = new byte[receiveBufferSize];

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
					if (!HTTPProvider.USE_SECURE) {
						// java.lang.UnsupportedOperationException: The method shutdownInput() is not supported in SSLSocket
						// java.lang.UnsupportedOperationException: The method shutdownOutput() is not supported in SSLSocket
						socket.shutdownInput();					// shutdown*() need to be called before close(), otherwise
						socket.shutdownOutput();				// the HTTP Reply node tends to indicate failures
					}
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
			if (printResponse > 0) {
				System.out.print("\nResponse msg (Thread " + this.getThreadNum());
				final String line = new String(bPrintResponseBuf, 0, iPrintResponseBufDataLen, csHTTPHeader);
				if (iLastResponseMsgLen > 0) {
					System.out.println(", MsgLength " + iLastResponseMsgLen + "): " + line +
						(bPrintResponseBufOverflow ? "..." : ""));
				} else {
					System.out.println("): WARNING: zero length response");
				}
			}

			// Clear up code carefully in fair weather or foul.
			status = (status & sERROR) | sENDED;

			if (endTime == 0)
				endTime = System.currentTimeMillis();

			try {
				if (socket != null) {
					if (!HTTPProvider.USE_SECURE) {
						// java.lang.UnsupportedOperationException: The method shutdownInput() is not supported in SSLSocket
						// java.lang.UnsupportedOperationException: The method shutdownOutput() is not supported in SSLSocket
						socket.shutdownInput();					// shutdown*() need to be called before close(), otherwise
						socket.shutdownOutput();				// the HTTP Reply node tends to indicate failures
					}
					socket.close();
					socket = null;							// this is not really necessary as we're stopping, but be nice & tidy
				}
				httpReader.close();
				httpWriter.close();
			}
			catch (Exception e) {
				// TODO: handle exception
			}
			Log.logger.log(Level.INFO, "STOP");
		}
	} // End public void run()

	/**
	 * Read an HTTP response.
	 * @param buf Target buffer, can be null
	 * @return Number of bytes read, or -1 on failure.
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	private final int readAndCopyHTTPResponse(OutputStream os) throws NumberFormatException, IOException {
		// read the data from the socket, we use a bytes buffer that
		// is our read buffer size + outbound msg size
		int contentLength = 0;
		final BufferedInputStream bis = getHTTPReader();

		// ---------------
		//   READ HEADER
		// ---------------
		int startContLength = -1;
		int dataChunked = -1;
		int timeoutCounter = 0;
		for (;;) {
			try {
				final int nRead = ByteArray.readUntil(bis, bCRLF, bReadBuf, 0, bReadBuf.length);
				if (nRead < bCRLF.length) {
					Log.logger.log(Level.INFO, "ERROR: Stream ended while reading the header");
					if (errorsAllowed) {
						return -1;
					} else {
						throw new IOException("Stream ended while reading the header");
					}
				}

				if (nRead >= bReadBuf.length)
					// this should be quite rare
					throw new Error(String.valueOf(nRead) + "[" + String.valueOf(bReadBuf.length)+ "]");

				if (nRead == bCRLF.length)
					// end of header is signified by a blank line
					break;

				final String lineRead = new String(bReadBuf, 0, nRead - bCRLF.length, csHTTPHeader);
				if (lineRead.matches("HTTP/1.\\d+\\s+404\\s+Not\\s+Found")) {
					Log.logger.log(Level.SEVERE, "ERROR: 404 Not Found");
					return -1;
				}

				//System.out.println("HEADER: \"" + lineRead + "\"");
				//if (lineRead == null)
				//	throw new IOException("Stream ended while reading the header");

				// reset timeout counter after each successfully read line
				timeoutCounter = 0;

				// If we have not already found the Content-Length flag check for it
				if (startContLength == -1 && dataChunked == -1) {
					final String sContentLength = "Content-Length: ";
					startContLength = lineRead.indexOf(sContentLength);
					// Once we have found the flag find the value of the Length
					if (startContLength != -1)
						contentLength = Integer.valueOf(lineRead.substring(startContLength + sContentLength.length())).intValue();
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
					return -1;
				} else
				if (shutdown)
					// check for asynchronous shutdown, but only within the timeout handler -- as long
					//   as we can keep reading read the stream without ever timing out, carry on
					// receiving an async shutdown in the timeout handler means that we can't finish
					//   reading the response, so interpret it as failure
					return -1;
				else
					// number of allowed timeouts has not yet been reached -- carry on
					timeoutCounter++;
			}
		}

		// -------------
		//   READ BODY
		// -------------
		if (startContLength != -1) {
			// No longer reading the bytes to don't need to read in buffers. This will need
			// to be updated 
			// If the remaining content fits in one block, read all of it, otherwise read a block and continue
			//int remainingBytes = contentlength;
			//int bytesToRead = remainingBytes < receiveBufferSize ? remainingBytes : receiveBufferSize;
			return readAndCopy(bis, contentLength, os) ? contentLength : -1;
		} else
		if (dataChunked != -1) {
			int resume = 0;
			String lineRead = null;
			int chunkLength = -1;
			timeoutCounter = 0;
			int nTotalRead = 0;
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
						{	// get length value
							final int nRead = ByteArray.readUntil(bis, bCRLF, bReadBuf, 0, bReadBuf.length);
							if (!ByteArray.endsWith(bReadBuf, 0, nRead, bCRLF))
								return -1;
							lineRead = new String(bReadBuf, 0, nRead - bCRLF.length, csHTTPHeader);
						}
						timeoutCounter = 0;								// reset after every successful read
						chunkLength = Integer.parseInt(lineRead, 16);
					case 1:
						resume = 1;
						if (chunkLength == 0) {
							// zero-length chunk followed by a newline
							final int nRead = ByteArray.readUntil(bis, bCRLF, bReadBuf, 0, bReadBuf.length);
							if ((nRead != bCRLF.length) || (!ByteArray.endsWith(bReadBuf, 0, nRead, bCRLF)))
								return -1;
							return nTotalRead;							// success: all chunks have been read
						} else {
							if (!readAndCopy(bis, chunkLength, os))		// readAndCopy() employs an independent
								return -1;								// timeoutCounter propagate failure
							nTotalRead += chunkLength;
						}
					case 2:
						// a chunk is always followed by a newline
						resume = 2;
						final int nRead = ByteArray.readUntil(bis, bCRLF, bReadBuf, 0, bReadBuf.length);
						if ((nRead != bCRLF.length) || (!ByteArray.endsWith(bReadBuf, 0, nRead, bCRLF)))
							return -1;
					}
					resume = 0;
				}
				catch (SocketTimeoutException e) {
					// this exception signals that the I/O operation (read line) has timed out
					if (timeoutCounter >= TCPIPProviderBase.timeoutNumIntervals) {
						// number of consecutive allowed timeout events has been exceeded -- return and indicate failure
						Log.logger.log(Level.INFO, "TIMEOUT");
						return -1;
					} else
					if (shutdown)
						// check for asynchronous shutdown, but only within the timeout handler -- as long
						//   as we can keep reading read the stream without ever timing out, carry on
						// receiving an async shutdown in the timeout handler means that we can't finish
						//   reading the response, so interpret it as failure

						return -1;
					else
						// number of allowed timeouts has not yet been reached -- carry on
						timeoutCounter++;
				}
			}
		} else {
			System.out.println("We have failed to find either \"Content-Length:\" or \"Transfer-Encoding: chunked\" in the header");
			return -1;
		}
	}
	/**
	 * Read bytes from a stream following our internal timeout and shutdown procedures
	 * @param is InputStream to read from
	 * @param buf Target buffer, can be null
	 * @param offset Start offset in buffer; ignored if buf == null
	 * @param n Number of bytes to read/skip
	 * @return true if the entire operation completed successfully
	 * @throws IOException
	 */
	private final boolean readAndCopy(InputStream is, int n, OutputStream os) throws IOException {
		//System.out.println("buf.position(): " + String.valueOf(buf.position()));
		//System.out.println("buf.capacity(): " + String.valueOf(buf.capacity()));
		//System.out.println("buf.limit(): " + String.valueOf(buf.limit()));
		//System.out.println("n: " + String.valueOf(n));
		int timeoutCounter = 0;
		while (n > 0) {
			try {
				final int nr = is.read(bReadBuf, 0, Math.min(bReadBuf.length, n));
				if (nr > 0) {
					n -= nr;
					if (os != null)
						os.write(bReadBuf, 0, nr);
					timeoutCounter = 0;
				} else
				if (nr < 0)
					return false;
			}
			catch (SocketTimeoutException e) {
				// this exception signals that the I/O operation has timed out
				if (timeoutCounter >= TCPIPProviderBase.timeoutNumIntervals) {
					// number of consecutive allowed timeout events has been exceeded -- shut down
					Log.logger.log(Level.INFO, "TIMEOUT");
					return false;
				} else
				if (shutdown)
					// check for asynchronous shutdown, but only within the timeout handler -- as long
					//   as we can keep reading read the stream without ever timing out, carry on
					// receiving an async shutdown in the timeout handler means that we can't finish
					//   reading the response, so interpret it as failure

					return false;
				else
					// number of allowed timeouts has not yet been reached -- carry on
					timeoutCounter++;
			}
		}
		return (n <= 0);
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

		httpWriter.write(header.getBytes(csHTTPHeader));	// Send header
		httpWriter.write(data);								// Send data
		httpWriter.flush();									// Flush buffer onto the wire // DJG this is causing the exception

		final MirrorOutputStream os = new MirrorOutputStream();

		// if we are writing some of the response msgs to a file and
		// we have have sent numMsgs msgs then append msg to a file
		File responseMsg = null;
		if ((writeResponseEveryN > 0) && ((getIterations() % writeResponseEveryN) == 0)) {
			responseMsg = new File(this.getName() + ".responsemsg");
			os.add(new FileOutputStream(responseMsg, true));
		}
		HeadOutputStream hos = null;
		if (printResponse > 0) {
			hos = new HeadOutputStream(bPrintResponseBuf);
			os.add(hos);
		}

		iLastResponseMsgLen = readAndCopyHTTPResponse(os);
		if (iLastResponseMsgLen < 0) {
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
				os.close();
				return true;
			}
		}

		if (responseMsg != null)
			Log.logger.log(Level.INFO, "Bytes read in: " + iLastResponseMsgLen + "; written to \"" +
				responseMsg.getCanonicalPath() + "\"");
		if (printResponse > 0) {
			iPrintResponseBufDataLen = hos.getOffset();
			bPrintResponseBufOverflow = hos.getOverflow();
		}
		
		os.close();

		if (incIterations() == nummsgs)
			shutdown = true;

		// If using NonPersistent connections, close all then reopen ready for next send
		// or reset if using Persistent connections and reached a msg limit
		if ((HTTPProvider.USE_NP_CONNECTIONS) || (mcMsgCount == msgLimitPerPersistConn)) {
			Log.logger.log(Level.FINE, "Closing connection after " + mcMsgCount + " messages");
			mcMsgCount = 0;
			if (!HTTPProvider.USE_SECURE) {
				// java.lang.UnsupportedOperationException: The method shutdownInput() is not supported in SSLSocket
				// java.lang.UnsupportedOperationException: The method shutdownOutput() is not supported in SSLSocket
				socket.shutdownInput();							// shutdown*() need to be called before close(), otherwise
				socket.shutdownOutput();						// the HTTP Reply node tends to indicate failures
			}
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
