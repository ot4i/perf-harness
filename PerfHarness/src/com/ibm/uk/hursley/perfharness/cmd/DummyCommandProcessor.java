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

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;

/**
 * As this harness was written a long time before introducing a Command server,
 * the default will use this dummy command server
 * 
 * @author fentono
 *
 */

public class DummyCommandProcessor extends Command {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {

		Config.registerSelf( DummyCommandProcessor.class );		
		
	}
	
	public DummyCommandProcessor(ControlThread parent) {
		super(parent);
		
		// do nothing
		
	}

	@Override
	public void sendMessage(String message) {
		// TODO Auto-generated method stub
		
	}
	
	

}
