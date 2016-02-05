/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.*;
import java.util.logging.Level;

import javax.xml.bind.DatatypeConverter;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.tcpip.TCPIPProviderBase;

public final class HTTPPostURL extends WorkerThread {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE
	protected static HTTPProvider httpprovider;
	private int nummsgs = 0;
	private boolean printResponseTime = false;
	protected static String name = "ibmuser";
	protected static String password = "password";
	protected static String authString = name + ":" + password;	

	// PRIVATE //
	private URL fURL;
	private static final String HTTP = "http";

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
	public void run() {
		try {
			
			System.setProperty("http.keepAlive", "true");
			System.setProperty("http.maxConnections", String.valueOf(Config.parms.getInt("nt") + 2));

			// Get the number of messages to send
			nummsgs = Config.parms.getInt("nm", 0);

			int clientSleepTime = Config.parms.getInt("sl");
			boolean clientSleep = false;
			if (clientSleepTime > 0) {
				clientSleep = true;
			}

			Log.logger.log(Level.INFO, "Entering client loop");

			// About to enter main loop so set it to say we are running
			status = sRUNNING;
			long sentTime = 0;
			long receiveTime = 0;

			String url = httpprovider.createGetURL();
			String option = "content";
			String body = "";
			HTTPPostURL putter = new HTTPPostURL(url, option);
			Log.logger.log(Level.INFO, "Putting Page to " + url);
			Boolean auth = Config.parms.getBoolean("au");
			String authStringEnc = DatatypeConverter.printBase64Binary(authString.getBytes("UTF-8"));
			
			if (auth != false) {
				Log.logger.log(Level.INFO, "Auth string: " + authString);
				Log.logger.log(Level.INFO, "Base64 encoded auth string: " + authStringEnc);
			}
			String payload = new String(TCPIPProviderBase.loadMessageFromFile(Config.parms.getString("mf")));
			
			while (!shutdown) {
				try {
					if (printResponseTime) {
						sentTime = System.currentTimeMillis();
					}	
					
					body = putter.putPageContent(payload,auth, authStringEnc);
					Log.logger.log(Level.FINE, body);

					if (printResponseTime) {
						receiveTime = System.currentTimeMillis();
						Log.logger.log(Level.INFO, "ResponseTime = "
								+ (receiveTime - sentTime));
						printResponseTime = false;
					}
					int msgs = incIterations();

					if (msgs == nummsgs) {
						shutdown = true;
					}

					if (clientSleep) {
						sleep(clientSleepTime);
					}
				} catch (Exception e) {
					System.out.println("ERROR In main HTTP Client thread: \n" + e);
					throw e;
				}
			} // end while !shutdown
			// Handle a fatal error
		} catch (Exception e) {
			status |= sERROR;
			Log.logger.log(Level.SEVERE, "Fatal Error.", e);
			ControlThread.signalShutdown();
			// Clear up code carefully in fair weather or foul.
		} finally {
			status = (status & sERROR) | sENDED;

			if (endTime == 0) {
				endTime = System.currentTimeMillis();
			}

			Log.logger.log(Level.INFO, "STOP");
		}
	} // End public void run()

	public HTTPPostURL(String name) {
		super(name);
	}

	public HTTPPostURL(URL aURL) {
		if (!HTTP.equals(aURL.getProtocol())) {
			throw new IllegalArgumentException("URL is not for HTTP Protocol: " + aURL);
		}
		Log.logger.log(Level.FINE, "Connecting to " + aURL);
		fURL = aURL;
	}

	public HTTPPostURL(String aUrlName, String aOption) throws MalformedURLException {
		this(new URL(aUrlName));
	}

	/** Fetch the HTML content of the page as simple text. */
	public String putPageContent(String payload, Boolean auth, String authStringEnc) throws IOException {				String result = null;	
		try {
			HttpURLConnection connection = (HttpURLConnection) fURL.openConnection();
			connection.setRequestMethod("POST");
			if (auth != false) {
				connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
			}
			connection.setDoInput(true);
			connection.setDoOutput(true);			
			connection.setRequestProperty("Content-Type", "application/json");
			connection.connect();		
			OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
			if (payload == "") {
				Log.logger.log(Level.FINE, "Putting: replaydestination=MyDestination");
				out.write("replaydestination=MyDestination");
			} else {
				Log.logger.log(Level.FINE, "Putting: " + payload);
				out.write(payload);
			}
			out.close();

			BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			while (br.ready()) {
				result = br.readLine();
	    	  Log.logger.log(Level.FINE, result);
	      }
			br.close();

		} catch (IOException ex) {
			Log.logger.log(Level.SEVERE, "Cannot open connection to " + fURL.toString());
			throw ex;
		}
		return result;
	}

}
