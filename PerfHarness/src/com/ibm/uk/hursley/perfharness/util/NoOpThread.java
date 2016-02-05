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
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.WorkerThread.Paceable;


/**
 * Perform a no-op in the pacable loop. This is used to measure/improve
 * performance counters and throughput throttling. This is a hidden
 * <code>-tc</code> testclass as it is of such rare use.
 * 
 */
public final class NoOpThread extends WorkerThread implements Paceable {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT; // IGNORE compiler warning
	
    /**
     * Constructor for DoNothingThread.
     * @param name
     */
    public NoOpThread(String name) {
        super();
    }
    
    public void run() {

        try {
        	
            status = sRUNNING;
            pace( this );           

            // Handle a fatal error
        } catch (Exception e) {

			status |= sERROR;
            ControlThread.signalShutdown();

            // Clear up code carefully in fair weather or foul.	
        } finally {
        	
           	status = (status&sERROR)|sENDED;
       	}

    } // End public void run()
    
    public boolean oneIteration() throws Exception {
    	incIterations();
    	return true;
    }

}
