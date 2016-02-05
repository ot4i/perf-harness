/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.http;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.util.logging.Level;

import javax.xml.bind.DatatypeConverter;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.tcpip.TCPIPProviderBase;

public final class HTTPPutURL extends WorkerThread {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE
	private int nummsgs = 0;
	private boolean printResponseTime = false;
	protected static String name = "ibmuser";
	protected static String password = "password";
	protected static String authString = name + ":" + password;

	// PRIVATE //  
	private URL fURL;
	private static final String HTTP = "http";
	
	public static void registerConfig() {
		Config.registerSelf(HTTPPutURL.class);
	} // end static initialiser
	
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
			
			int port = Config.parms.getInt("jp");
			String hostname = Config.parms.getString("jh");
			String URL = Config.parms.getString("ur");
			StringBuffer header = new StringBuffer();
			header.append("http://" + hostname + ":" + port + "/" + URL + "MyTest");
			String url = header.toString();			
			String payload = new String(TCPIPProviderBase.loadMessageFromFile(Config.parms.getString("mf")));			
		    String option = "content";
		    int counter = 0;
			String ReplaceString = null;
		    
		    // rs flag is used to identify the string to replace with the counter
		    if ( Config.parms.getString("rs") != null) {
			    ReplaceString = Config.parms.getString("rs");		    
		    }		  
			String authStringEnc = DatatypeConverter.printBase64Binary(authString.getBytes("UTF-8"));
		    
			while (!shutdown) {
				try {
					if (printResponseTime) {
						sentTime = System.currentTimeMillis();
					}
					String nurl = url + counter;
					counter++;
				    HTTPPutURL putter = new  HTTPPutURL(nurl, option);
					Log.logger.log(Level.FINE, "Putting Page to " + nurl);
					Log.logger.log(Level.FINE, "Auth string: " + authString);
					Log.logger.log(Level.FINE, "Base64 encoded auth string: " + authStringEnc);
					
				    putter.putPageContent(counter, ReplaceString, payload, authStringEnc);
				    Log.logger.log(Level.FINE, payload);
				    
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
	
	public HTTPPutURL(String name) {
		super(name);
	}

	public HTTPPutURL( URL aURL ){
	  if ( ! HTTP.equals(aURL.getProtocol())  ) {
	    throw new IllegalArgumentException("URL is not for HTTP Protocol: " + aURL);
	  }
	  Log.logger.log(Level.FINE, "Connecting to " + aURL);
	  fURL = aURL;
	}

	public HTTPPutURL( String aUrlName, String aOption ) throws MalformedURLException {
	  this ( new URL(aUrlName) );
	}
	
	/** Fetch the HTML content of the page as simple text.   */
	public String putPageContent(int counter, String ReplaceString, String payload, String authStringEnc) throws IOException {
	  String result = null;
		
	    try {
	      HttpURLConnection connection = (HttpURLConnection)fURL.openConnection();
	      connection.setDoOutput(true);
	      connection.setRequestMethod("PUT"); 
	      connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
	      connection.setRequestProperty("Content-Type", "application/json");
	      connection.setRequestProperty("Accept", "application/json");
	      connection.connect();
	      
	      if ( ReplaceString != null) {
	    	  payload = payload.replace(ReplaceString, Integer.toString(counter));
	      }
	      
	      OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
	      out.write(payload);
	      out.flush();
	      out.close();
	      connection.getInputStream();
	    }
	    catch ( IOException ex ) {
	    	Log.logger.log(Level.SEVERE, "Cannot open connection to " + fURL.toString());
			throw ex;
	    }
	    return result;
	  }
	
	

}
