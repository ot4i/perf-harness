/********************************************************* {COPYRIGHT-TOP} ***
* Licensed Materials - Property of IBM
*
* IBM Performance Harness for Java Message Service
*
* (C) Copyright IBM Corp. 2005, 2007  All Rights Reserved.
*
* US Government Users Restricted Rights - Use, duplication, or
* disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
********************************************************** {COPYRIGHT-END} **/

package com.ibm.uk.hursley.perfharness.cmd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.Log;

/**
 * Listens on a socket for incoming connections & passes commands through to the superclass
 * 
 * To use: enable with -cmd_c SocketCommandProcessor -cmd_p [port] (default: 4444)
 *         while application is running, connect using
 *         
 *         >telnet localhost 4444
 *         <<< connected to SocketCommandProcessor on port 4444
 *			
 *		   See Command class for supported commands
 *
 * @author fentono
 *
 */
public class SocketCommandProcessor extends Command {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	private static int port;
	private ServerSocket serverSocket = null;
	private Socket clientSocket = null;
	
	PrintWriter out = null; // we can only hold 1 connection at a time
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {

		Config.registerSelf( SocketCommandProcessor.class );		
		
		port = Config.parms.getInt( "cmd_p" );
	}
	
	public SocketCommandProcessor(ControlThread parent) {
		super(parent);
		
		try {
			serverSocket = new ServerSocket(port);
			
		} catch (IOException e) {
			Log.logger.log(Level.SEVERE, "Error starting SocketCommandProcessor on port " + port);
			return;
		}
		
	}

	/**
	 * Processes commands
	 * NOTE: only 1 connection allowed at a time with this implementation
	 */
	public final void run() {
		try {
			while ( !shutdown ) {
				// wait to connect
				clientSocket = serverSocket.accept();
				
				// setup some variables for printing & receiving msgs
				out = new PrintWriter(clientSocket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				String inputLine;

				sendMessage("connected to SocketCommandProcessor on port " + port);
				
				while ((inputLine = in.readLine()) != null) {
				    if (inputLine.equals("quit"))
				        break;
					
				    processMessage(inputLine);
				}
				
				out.close();
				in.close();	
				clientSocket.close();	
			}
			
			serverSocket.close();
			
		} catch (IOException e) {
			Log.logger.log(Level.WARNING, "Warning client failed to accept on SocketCommandProcessor on port " + port);
		}
		
	}

	@Override
	public void sendMessage(String message) {
		if (id != null) {
			message = id + "," + message;
		}
		
		if (out != null) {
			out.println(message);
		} else {
			out.println("null");
		}
	}
}
