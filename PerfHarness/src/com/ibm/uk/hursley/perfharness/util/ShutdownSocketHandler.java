/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.util;

import java.net.BindException;
import java.net.ServerSocket;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;

/**
 * Listens on a named socket, any connection on this socket will terminate the
 * test.
 * 
 */
public class ShutdownSocketHandler extends Thread {

	private int socketNum;
	private ServerSocket socket = null;
	
	public ShutdownSocketHandler( int socketNum ) {
		
		super( "PerfHarnessShutdownSocket" );
		this.socketNum = socketNum;
		setDaemon(true);
		
	}
	
	public void run() {
		
		setName("ShutdownSocketThread");
		Log.logger.log(Level.FINE, "START");

		while ( !ControlThread.isShuttingDown() ) {
			
			try {
				
				try {
					socket = new ServerSocket(socketNum);
				} catch (BindException e1) {
					// Socket already in use. Wait a bit before
					// we loop and retry
					Log.logger.log(Level.WARNING, "Socket " + socketNum
							+ " in use waiting before retrying");
					Thread.sleep(10000);
				}
				Thread.sleep(3000);
				socket.accept();
				Log.logger.log(Level.FINE, "Socket Shutdown Signalled");
				ControlThread.signalShutdown();
				
			} catch (Exception e) {
				
				if ( !ControlThread.isShuttingDown() ) {
					Log.logger.log(Level.SEVERE, "Error on socket "
						+ socketNum, e);
				}
				
			} finally {
				
				closeSocket();
				
			} // end try finally
			
		} // end while
		
		Log.logger.log(Level.FINE, "STOP");
		
	}
	
	public void closeSocket() {
		
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (Exception e) {
			Log.logger.log(Level.FINE, "Error closing socket", e);
		}
		
	}

}
