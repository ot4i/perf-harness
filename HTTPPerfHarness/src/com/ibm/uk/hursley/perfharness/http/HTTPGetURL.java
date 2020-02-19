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
import java.net.*;
import java.util.Scanner;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;


public final class HTTPGetURL extends WorkerThread {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE
	protected static HTTPProvider httpprovider;
	private int nummsgs = 0;
	private boolean printResponseTime = false;

	// PRIVATE //  
	private URL fURL;
	private static final String HTTP = "http";
	private static final String HEADER = "header";
	private static final String CONTENT = "content";
	private static final String END_OF_INPUT = "\\Z";
	private static final String NEWLINE = System.getProperty("line.separator");
	
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
		    HTTPGetURL fetcher = new  HTTPGetURL(url, option);
			Log.logger.log(Level.INFO, "Requesting Page from " + url);
			
			while (!shutdown) {
				try {
					if (printResponseTime) {
						sentTime = System.currentTimeMillis();
					}
					
				    if ( HEADER.equalsIgnoreCase(option) ) {
				    	body = fetcher.getPageHeader();
				    	Log.logger.log(Level.FINE, body);
				    }
				    else if ( CONTENT.equalsIgnoreCase(option) ) {
				    	body = fetcher.getPageContent();
				    	Log.logger.log(Level.FINE, body);
				    }
				    else {
				    	Log.logger.log(Level.SEVERE, "Unknown option.");
				    }
				    
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
	
	public HTTPGetURL(String name) {
		super(name);
	}

	public HTTPGetURL( URL aURL ){
	  if ( ! HTTP.equals(aURL.getProtocol())  ) {
	    throw new IllegalArgumentException("URL is not for HTTP Protocol: " + aURL);
	  }
	  Log.logger.log(Level.FINE, "Connecting to " + aURL);
	  fURL = aURL;
	}

	public HTTPGetURL( String aUrlName, String aOption ) throws MalformedURLException {
	  this ( new URL(aUrlName) );
	}
	
	/** Fetch the HTML content of the page as simple text.   */
	public String getPageContent() throws IOException {
	    String result = null;
	    URLConnection connection = getConnection();
	    Scanner scanner = new Scanner(connection.getInputStream());
	    scanner.useDelimiter(END_OF_INPUT);
	    result = scanner.next();
            scanner.close
	    return result;
	  }
	
	  /** Fetch HTML headers as simple text.  */
	  public String getPageHeader() throws IOException{
	    StringBuilder result = new StringBuilder();
	
	    URLConnection connection = getConnection();
	
	    //not all headers come in key-value pairs - sometimes the key is
	    //null or an empty String
	    int headerIdx = 0;
	    String headerKey = null;
	    String headerValue = null;
	    while ( (headerValue = connection.getHeaderField(headerIdx)) != null ) {
	      headerKey = connection.getHeaderFieldKey(headerIdx);
	      if ( headerKey != null && headerKey.length()>0 ) {
	        result.append( headerKey );
	        result.append(" : ");
	      }
	      result.append( headerValue );
	      result.append(NEWLINE);
	      headerIdx++;
	    }
	    return result.toString();
	  }
	  
	  public URLConnection getConnection() throws IOException{
		    URLConnection connection = null;
		    try {
		      connection = fURL.openConnection();
		    }
		    catch (IOException ex) {
		    	Log.logger.log(Level.SEVERE, "Cannot open connection to " + fURL);
				throw ex;
		    }
		    return connection;
	  }

}
