/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.tcpip;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;

public class TCPIPProviderBase {
	protected static byte[][] messageData = null;
	protected static String hostname = null;
	protected static int port;
	private static int currentPort;
	private static int portRange;
	private static int timeoutIntervalLength = 500;
	public static int timeoutNumIntervals = 20;
	private static InetAddress addr;
	private static final String msgEncoding = "UTF8";
	public static boolean USE_NP_CONNECTIONS = false; 
	public static boolean USE_SSL = false;

	protected void setupProvider() {
		port = Config.parms.getInt("jp");
		currentPort = port;
		portRange = Config.parms.getInt("dn");
		timeoutIntervalLength = Config.parms.getInt("ri");
		timeoutNumIntervals = Config.parms.getInt("to") / timeoutIntervalLength;
		hostname = Config.parms.getString("jh");
		USE_NP_CONNECTIONS = Config.parms.getBoolean("cs");
		USE_SSL = Config.parms.getBoolean("se");
		Log.logger.log(Level.INFO, "ThreadID " + Thread.currentThread().getId());

	}
	public int getNumMessages() {
		return messageData.length;
	}
	public int getMessageSize(int k) {
		return messageData[k].length;
	}
	public String getStringMessage(int k) throws Exception {
		return new String(messageData[k], msgEncoding);
	}
	public byte[] getBytesMessage(int k) throws Exception {
		return messageData[k];
	}
	public int getMessageIndex(int iIter, int iThread) {
		final int msgPattern = Config.parms.getInt("pa");
		if (msgPattern == 1) {
			// PRIME
			return Math.min(iIter, getNumMessages() - 1);
		} else
		if (msgPattern == 2) {
			// THREAD
			return iThread % getNumMessages();
		} else {
			// CYCLE
			return iIter % getNumMessages();
		}
	}
	public void loadMessages() throws Exception {
		final String[] messageFiles = Config.parms.getCSStringList("mf");
		if ((messageData != null) && (messageData.length == messageFiles.length))
			// the messages have already been loaded
			return;

		messageData = new byte[messageFiles.length][];
		for (int k = 0; k < messageFiles.length; k++) {
			try {
				messageData[k] = loadMessageFromFile(messageFiles[k]);
			}
			catch (IOException ioe) {
				Log.logger.log(Level.SEVERE, "Cannot read file \"" + messageFiles[k] + "\"", ioe);
				System.exit(1);
			}
		}
	}
	public static byte[] loadMessageFromFile(String fileName) throws IOException {
		if(fileName.isEmpty()) {
			// no file has been specified, return an empty byte array
			return new byte[0];
		}
		final File f = new File(fileName);
		final FileInputStream fis = new FileInputStream(f);
		final long msgSize = (int)f.length();
		if (msgSize > (long)Integer.MAX_VALUE)
			throw new IOException("File is too large");
		final byte[] data = new byte[(int)msgSize];
		fis.read(data);
		fis.close();
		return data;
	}
	public Socket getSocket() throws IOException {
		
		getHostname(); 
		
		final Socket socket = new Socket();
		socket.setTcpNoDelay(true);
		socket.setReuseAddress(true);
		socket.setSoLinger(true, 0);
		socket.setSoTimeout(timeoutIntervalLength);
		socket.connect(new InetSocketAddress(addr, currentPort), 0);

		// Check to see if the user has requested a range of ports to be used
		if (portRange > 1) {
			// For a range of ports, keep incrementing up to the max range and then
			// loop back to the start
			if (currentPort >= port + portRange - 1)
				currentPort = port;
			else
				currentPort++;
		}

		return socket;
	}
	public Socket getSSLSocket() throws IOException {
		
		getHostname();
		
		final javax.net.SocketFactory sf = javax.net.ssl.SSLSocketFactory.getDefault();
		if (portRange > 1)
			System.out.println("About to connect to port " + currentPort + " for thread " + Thread.currentThread());

		final Socket socket = sf.createSocket();
		socket.setReuseAddress(true);
		socket.setSoLinger(true, 0);
		socket.setSoTimeout(timeoutIntervalLength);
		socket.connect(new InetSocketAddress(addr, currentPort), 0);

		// Check to see if the user has requested a range of ports to be used
		if (portRange > 1) {
			// For a range of ports, keep incrementing up to the max range and then
			// loop back to the start
			if (currentPort >= port + portRange - 1)
				currentPort = port;
			else
				currentPort++;
		}

		return socket;
	}
	
	public void getHostname()  {
		
		String[] HostArray = hostname.split("[,]"); 
		String threadhostname = HostArray[(int) ((((Thread.currentThread().getId()-13)%HostArray.length)+1)-1)];
		
		if (HostArray.length >1 ) {
			Log.logger.log(Level.INFO, "Connecting to " + threadhostname);
		}
		
		try {
			addr = InetAddress.getByName(threadhostname);
		}
		catch (UnknownHostException e) {
			Log.logger.log(Level.SEVERE, "Cannot resolve Hostname " + threadhostname, e);
			System.exit(1);
		}
	}
}
