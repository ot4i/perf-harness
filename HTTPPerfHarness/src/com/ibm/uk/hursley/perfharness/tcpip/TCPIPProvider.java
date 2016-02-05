/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.tcpip;

import com.ibm.uk.hursley.perfharness.Config;


public class TCPIPProvider extends TCPIPProviderBase {
	private static TCPIPProvider instance = null;
	public static int msgLimitPerPersistConn = 0;

	public static void registerConfig() {
		Config.registerSelf( TCPIPProvider.class );
		// TODO parameter checking here
	} // end static initialiser	

	public int getMsgLimitPerPersistConn() {
		return msgLimitPerPersistConn;
	}

	public synchronized static TCPIPProvider getInstance() {
		if ( instance == null ) {
			instance = new TCPIPProvider();
			instance.setupProvider();
		}
		return instance;
	}

	@Override
	protected void setupProvider() {
		super.setupProvider();
		msgLimitPerPersistConn = Config.parms.getInt("mc");
	}

}
