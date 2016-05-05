/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

package com.ibm.uk.hursley.perfharness.amqp;

import com.ibm.uk.hursley.perfharness.Config;

/**
 * Provides access to WebSphere MQ classes for Java.
 */
public class MQProvider {
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	
	private static MQProvider instance = null;	

	public MQProvider() {
		// TODO Auto-generated constructor stub
	}

	public static void registerConfig() {
		Config.registerSelf(MQProvider.class);
	} // end static initialiser	

	public synchronized static MQProvider getInstance() {
		if (instance == null)
			instance = new MQProvider();
		return instance;
	}
}
