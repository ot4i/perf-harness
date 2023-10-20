/********************************************************* {COPYRIGHT-TOP} ***
* Licensed Materials - Property of IBM
*
* IBM Performance Harness for Java Message Service
*
* (C) Copyright IBM Corp. 2005, 2021  All Rights Reserved.
*
* US Government Users Restricted Rights - Use, duplication, or
* disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
********************************************************** {COPYRIGHT-END} **/

package com.ibm.uk.hursley.perfharness.cmd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.Log;

/**
 * Polls a Linux named pipe file for incoming commands & passes through to the superclass
 * 
 * To use: enable with -cmd_c LinuxPipeCommandProcessor -cmd_pipe [fifo pipe name (must not already exist)] (default: /tmp/perfharness_fifopipe)
 *         while application is running, issue commands to pipe. E.g.
 *         
 *         echo ALTER -rt 0 > /tmp/perfharness_fifopipe
 *         
 *		   See Command class for supported commands
 *
 * @author Paul Harris
 *
 */
public class LinuxPipeCommandProcessor extends Command {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	private static String fifoFileSpec;
	private File fifoFile = null;
	
	PrintWriter out = null; // we can only hold 1 connection at a time
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {

		Config.registerSelf( LinuxPipeCommandProcessor.class );		
		fifoFileSpec = Config.parms.getString( "cmd_pipe" );
	}
	
	public LinuxPipeCommandProcessor(ControlThread parent) {
		super(parent);
		fifoFile = createNamedPipe(fifoFileSpec);
	}
	
	/**
	 * Create the Linux named pipe. 
	 * @see Config#registerSelf(Class)
	 */
	File createNamedPipe(String pipeFileSpecString){
		Runtime run = Runtime.getRuntime();
		try {
			Process pr = run.exec("mkfifo " + pipeFileSpecString);
            try {
				pr.waitFor();
			} catch (InterruptedException e) {
				Log.logger.log(Level.SEVERE, "Error creation of pipe " + fifoFileSpec + " was interrupted");
				ControlThread.signalShutdown();
			}
            if(pr.exitValue() != 0){
                BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
                String line = "";
                String returnMessage="";
                while ((line=buf.readLine())!=null) {
                	returnMessage = returnMessage + line;
                }
				Log.logger.log(Level.SEVERE, "Error creating pipe " + fifoFileSpec + ". OS returned: " + returnMessage);
				ControlThread.signalShutdown();
            } else {
        		System.out.println("PerfHarness command processor is monitoring Linux pipe file " + fifoFileSpec + " for commands");
            }
		} catch (IOException e) {
			Log.logger.log(Level.SEVERE, "Error creating pipe " + fifoFileSpec + " " + e);
			ControlThread.signalShutdown();
		}
		
		return new File(pipeFileSpecString);
	}	

	/**
	 * Processes commands
	 */
	public final void run() {
		BufferedReader bfr=null;
		String inputLine = "";
		try {
			bfr = new BufferedReader(new FileReader(fifoFile));
		} catch (FileNotFoundException e1) {
			Log.logger.log(Level.SEVERE, "Error reading from named pipe " + fifoFileSpec);
			return;
		} 
		
		while ( !shutdown ) {
			try {
				while ((inputLine = bfr.readLine()) != null) {
					processMessage(inputLine);
				}
				Thread.sleep(100);
	        } catch (IOException e) {
	        	cleanUp(bfr);
				Log.logger.log(Level.SEVERE, "Error reading from named pipe " + fifoFileSpec);
				return;
	        } catch (InterruptedException e) {
	        	//No need to handle interrupted Thread sleep
			} 
		}

    	cleanUp(bfr);		
	}
	
	private void cleanUp(BufferedReader bfr){
		try {
			bfr.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//Delete the pipe file we created during class instantiation
		fifoFile.delete();
	}

	@Override
	public void sendMessage(String message) {
		if (id != null) {
			message = id + "," + message;
		}		
		System.out.println(message);
	}
}
