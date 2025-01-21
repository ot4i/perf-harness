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
/*
 * $Id: PutGet.java 562 2013-12-12 14:41:26Z smassey $
 * JMSPerfHarness $Name$
 */

package com.ibm.uk.hursley.perfharness.jms.r11;

import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * This class uses the PerfHarness infrastructure to measure the time taken to create and destroy connections
 * This can be used to measure this overhead which might also include any additional connection authentication
 * with this provider  
 *  
 *  @author smassey@uk.ibm.com 
 */
public final class ConnectionTest extends JMS11WorkerThread implements WorkerThread.Paceable {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    public static void registerConfig() {
		Config.registerSelf( ConnectionTest.class );
	}    
    
    /**
     * Constructor for ConnectionTest
     * @param name
     */
    public ConnectionTest(String name) {
        super(name);
    }
    
	/**
	 * Only destroys(if exists) and creates a JMS connection
	 * Use this method if you are only interested in connection creation and tear down
	 * @throws Exception
	 */
    protected void buildJMSResources2() throws Exception {
    	destroyJMSResources(true);
    	
        if (cf == null) {
        	Log.logger.log(Level.FINE, "Getting ConnectionFactory");
	        cf = jmsProvider.lookupConnectionFactory(null);
        }
    	
        Log.logger.log(Level.FINE, "Making connection");
        connection = jmsProvider.getConnection(cf, this, String.valueOf(this.getThreadNum()));	
    }
    
	/**
   	 * Invokes superclass to perform connection and session creation
   	 * Using this interface is at least a little more realistic than just connection creation and tear down 
	 * @throws Exception
	 */
	protected void buildJMSResources() throws Exception {
		super.buildJMSResources();
	}

	/**
   	 * Invokes superclass to perform thread work
	 */
    public void run() {
        run(this, null);  // call superclass generic method.
    } // End public void run()    
    
    /**
     * Perform work for this particular class 
	 * @throws Exception
     */
	public final boolean oneIteration() throws Exception {		
		buildJMSResources();
		incIterations();
		return true;
	}
}
