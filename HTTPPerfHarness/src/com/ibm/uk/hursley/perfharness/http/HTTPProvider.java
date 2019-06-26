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
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.tcpip.TCPIPProviderBase;


public class HTTPProvider extends TCPIPProviderBase {
	private static String URL = "/";
	private static String soapAction = null;
	private static String contentType = null;
	private static LinkedList<String> extraHeaders = null;
	public static int msgLimitPerPersistConn = 0;
	private static boolean absoluteURI;

	private static HTTPProvider instance = null;

	public static void registerConfig() {
		Config.registerSelf(HTTPProvider.class);
	} // end static initialiser

	public synchronized static HTTPProvider getInstance() {
		if (instance == null) {
			instance = new HTTPProvider();
			instance.setupProvider();
		}
		return instance;
	}

	@Override
	protected void setupProvider() {
		super.setupProvider();
		absoluteURI = Config.parms.getBoolean("px");
		URL = Config.parms.getString("ur");
		msgLimitPerPersistConn = Config.parms.getInt("mc");
		extraHeaders = readExtraHeaders(Config.parms.getString("he"));
		soapAction = Config.parms.getString("sa", null);
		contentType = Config.parms.getString("hc", "text/xml");
	}

	private static LinkedList<String> readExtraHeaders(String fileName) {
		if (fileName != null && !fileName.equals("")) {
			try {
				final LinkedList<String> lh = new LinkedList<String>();
				final BufferedReader br = new BufferedReader(new FileReader(fileName));
				for (;;) {
					final String line = br.readLine();
					if (line == null)
						break;
					if (!line.equals(""))
						lh.add(line);
				}
				br.close();
				return lh;
		} catch (IOException ioe) {
				Log.logger.log(Level.SEVERE, "Cannot read file " + fileName, ioe);
				System.exit(1);
			}
		}
		return null;
	}
	
	public String createHeader(int msgNo, int threadId) {
		final StringBuffer header = new StringBuffer();
		String operationType = Config.parms.getString("ot", "POST");
		
		String[] HostArray = hostname.split("[,]"); 
		String threadhostname = HostArray[(((threadId-1)%HostArray.length)+1)-1];
				
		if (absoluteURI) {
			header.append(operationType + " " + "http://" + threadhostname + ":" + port + "/" + URL + " HTTP/1.1\r\n");
			header.append("HOST: " + threadhostname + "\r\n");
		} else {
			header.append(operationType + " " + "/" + URL + " HTTP/1.1\r\n");
			header.append("HOST: " + threadhostname + "\r\n");
		}

		// If we are to use non persistent connections put in the close http
		// header so socket closed after each request.
		if (USE_NP_CONNECTIONS)
			header.append("Connection: close " + "\r\n");
		else
			header.append("Connection: keep-alive " + "\r\n");
		if(getMessageSize(msgNo) > 0) {
			header.append("Content-Length: " + getMessageSize(msgNo) + "\r\n");
			header.append("Content-Type: " + contentType + "\r\n");
		}
		
		// If BasicAuth specified then base64 encode the username:password string
		// and append the Authorization header
		String auth = Config.parms.getString("au");
		if(!auth.isEmpty()) {
			 try {
				// Update to used java.util.Base64 in Java8
	            header.append("Authorization: Basic " + DatatypeConverter.printBase64Binary(auth.getBytes("UTF-8")) + "\r\n");
            } catch (UnsupportedEncodingException uee) {
            	Log.logger.log(Level.SEVERE, "Error encoding based auth string " + auth, uee);
	            System.exit(1);
            }
		}
		
		if (soapAction != null && !soapAction.isEmpty()) {
			header.append("SOAPAction: " + soapAction + "\r\n");
		}
		
		if (extraHeaders != null) {
			for (final String s : extraHeaders) {
				header.append(s + "\r\n");
			}
		}
		
		header.append("\r\n");

		return header.toString();
	}

	public int getMsgLimitPerPersistConn() {
		return msgLimitPerPersistConn;
	}

	public String createGetURL() {
		port = Config.parms.getInt("jp");
		hostname = Config.parms.getString("jh");
		URL = Config.parms.getString("ur");
		StringBuffer header = new StringBuffer();
		header.append("http://" + hostname + ":" + port + "/" + URL);
		return header.toString();
	}
}
