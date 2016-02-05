/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
/*

 * JMSPerfHarness $Name:  $
 */

import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;

/**
 * Start the system with the specified arguments, this is just a wrapper onto ControlThread.
 * This class is actually required as ControlThread cannot have its own main method
 * due to the static-access-based module configuration in Config.register()
 */
public final class JMSPerfHarness {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	/**
	 * Earliest recorded time in the JVMs lifecycle.  May be useful for certain metrics. 
	 */
	static final long time = System.currentTimeMillis();
	
	/**
	 * Main method 
	 * @param args The commandline.
	 */
	public static void main(String[] args) {

		Config.init( args, null );
		ControlThread.registerConfig();
		
		ControlThread ct;
		try {
			ct = ControlThread.getInstance();
			ct.start();    	
		} catch (Exception e) {
			Log.logger.log( Level.SEVERE, "Uncaught exception.", e );
		}
		
	}

}
