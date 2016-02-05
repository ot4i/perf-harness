/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.util;

import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;

/**
 * Signals a system shutdown when a SIGINT is received.
 * 
 */
public class ShutdownHook extends Thread {
	
	public ShutdownHook() {
		super( "PerfHarnessSignalHandler" );
	}

	public void run() {

		Log.logger.finer("START");
		long start = System.currentTimeMillis();
		ControlThread.signalShutdown();
		ControlThread.joinAllControllers();
		long duration = System.currentTimeMillis() - start;
		Log.logger.finer("STOP after " + duration + " msec");
		// Get around deadlock issue
		if (Log.errorsReported()) {
			Runtime.getRuntime().halt(1);
		} else {
			Runtime.getRuntime().halt(0);
		}

	}

}
