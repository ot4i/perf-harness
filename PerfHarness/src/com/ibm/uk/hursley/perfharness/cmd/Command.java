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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.ApplicationPropertyMap;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.util.TypedProperties;

/**
 * Base Command class. This is meant to be extended by subclasses
 * 
 * Handles input commands to change test settings in-flight
 * Things like rate, thread count can be changed
 * 
 * Commands:
 * 
 * > REPORT -stats
 *   returns the loaded statistics class's requestStatistics method
 * > START -nt 4
 *   starts 4 new threads. Returns when the new threads a running
 * > END -nt 2
 *   ends 2 threads. Returns when the threads have stopped running - note, they may still be shutting down.
 *   If there are no more worker threads owned by the ControlThread, the application shuts down.
 * > END
 *   ends all remaining worker threads & shuts down.
 * > ALTER -rate 0
 *   changes the pacing of the threads. 0 means maximum rate, otherwise it's messages/sec
 *   Returns after all worker threads have been told the new rate. Please wait for the rate to steady before
 *   taking any new measurements.
 * 
 * @author fentono
 *
 */

public abstract class Command extends java.lang.Thread {
	
	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	private static Class<? extends Command> cmdclazz;
	
	protected ArrayList<WorkerThread> workers = null; 
	protected ControlThread parent = null;
	
	protected String id = Config.parms.getString("id");
	
	protected volatile boolean shutdown = false;
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		
		Config.registerSelf( Command.class );
		
		if ( cmdclazz==null ) {
			cmdclazz = Config.parms.<Command>getClazz( "cmd_c" );
			Config.registerAnother( cmdclazz );
		}
		
	}
	
	protected Command(ControlThread parent) {
		this.parent = parent;
		this.workers = parent.getWorkers(); // keep a reference
	}
	
	/**
	 * Singleton style accessor for Command class (and subclasses)
	 * @param parent
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	public static Command newInstance(ControlThread parent) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		
		final Constructor<? extends Command> c = cmdclazz.getConstructor( new Class[] { ControlThread.class } );
		Command cmd = c.newInstance( new Object[] { parent } );
		
		cmd.setDaemon(true);
		
		return cmd;
		
	}
	
	
	/**
	 * 
	 * @param cmd
	 * 		format of all commands is defined as
	 * 		[COMMAND] [-attr value] [-attr value] ...
	 * @return
	 */
	protected void processMessage(String input) {
		
		// parse cmd with spaces (like cmdline)
		String cmd[] = input.split(" ");
		
		if (cmd != null && cmd.length != 0) {
		
			String headCmd = cmd[0];
			
			// remove the headCmd & pass the rest of the parameters 
			// through to the command processor
			String[] cmdParams = new String[cmd.length-1];
			for (int i = 1; i < cmdParams.length; i++) {
				cmdParams[i-1] = cmd[i];
			}
			
			final ApplicationPropertyMap parmsInternal = new ApplicationPropertyMap();
			parmsInternal.readCommandLine(cmd);
			final TypedProperties parms = new TypedProperties( parmsInternal );
			
			
			if (headCmd.equalsIgnoreCase("start")) {
				
				if (start(parms)) { 
					sendMessage("SUCCESS: " + input); 
				} else { 
					sendMessage("FAIL: " + input);
				}
				return;
			
			} else if (headCmd.equalsIgnoreCase("end")) {
			
				if (end(parms)) { 
					sendMessage("SUCCESS: " + input); 
				} else { 
					sendMessage("FAIL: " + input); 
				}
				return;
			
			} else if (headCmd.equalsIgnoreCase("alter")) {
			
				if (alter(parms)) { 
					sendMessage("SUCCESS: " + input); 
				} else { 
					sendMessage("FAIL: " + input); 
				}
				return;
			
			} else if (headCmd.equalsIgnoreCase("report")) {
			
				sendMessage(report(parms));
				return;
				
			} else {
				Log.logger.log( Level.WARNING, "unsupported command: " + input);
				sendMessage("WARNING: unsupported command: " + input);
				return;
			}
		}

	}
	
	/**
	 * used to send a message to the send-point
	 * We may want to do this 
	 * - as a reply to an incoming request
	 * - at certain intervals
	 * - when certain criteria are met (e.g. just before shutdown)
	 * 
	 * @param message message to send
	 */
	public abstract void sendMessage(String message);
	
	
	/**
	 * 
	 * @param parms just like the command line parameters
	 * nt = number of threads to start 
	 * @return
	 */
	private boolean start(TypedProperties parms) {
		int nt = parms.getInt("nt", 1); // by default add 1
		
		try {
			if (parent.changeWorkerCount(nt)) { // adds the new workers
				while (!shutdown && (parent.getRunningWorkers() != workers.size())) {
					this.wait( 1 * 1000 );
				}
				if (shutdown) return false;
				else return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			Log.logger.log( Level.SEVERE, "Fatal Error: possible notify while attempting to start threads", e);
		}
		
		return false;
	}
	
	/**
	 * 
	 * @param parms
	 * nt = number of threads to end
	 * @return
	 */
	private boolean end(TypedProperties parms) {
		int nt = parms.getInt("nt", parent.getRunningWorkers()); // by default shutdown
		
		try {
			if (parent.changeWorkerCount(-nt)) { // adds the new workers
				while (!shutdown && (parent.getRunningWorkers() != workers.size())) {
					this.wait( 1 * 1000 );
				}
				if (shutdown) return false;
				else return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			Log.logger.log( Level.SEVERE, "Fatal Error: possible notify while attempting to end threads", e);
		}
		return false;
	}
	
	/**
	 * 
	 * @param parms
	 * @return
	 */
	private boolean alter(TypedProperties parms) {
		/**
		 * alter the rate
		 */
		double rt = parms.getDouble("rt", -1); // if it's not set, we'll get back -1 & we won't update it
		Config.parms.putDouble("rt", rt); // update the global config setting so any new workers will start with the current rate
		// update the rate for all running threads
		if (rt >= 0 ) {
			for(int i = 0; i < workers.size(); i++)
				workers.get(i).updateRate(rt);
		}
		
		return true;
	}
	
	private String report(TypedProperties parms) {
		
		if (parms.getBoolean("stats") == true) {
			return parent.requestStatistics();
		} else {
			return "FAIL: invalid report request (options are 'stats')\n";
		}
		
	}
}
