/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp.utils;

import java.util.HashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Log;

/**
 * @author ElliotGregory
 *
 */
public class Blocker {
	
	private final SynchronousQueue<HashMap<String, Object>> sq = new SynchronousQueue<HashMap<String, Object>>();
	private final String reference;
	

	/**
	 * @param reference a string to reference this object for trace and logging.
	 */
	public Blocker (final String reference) {
		this.reference = reference;
	}
	
	/**
	 * @param data option data to be passed as hashmap or null.
	 */
	public void signal(final HashMap<String, Object> data) {
		try {
			sq.put(data == null ? new HashMap<String,Object>() : data);
		} catch (InterruptedException ie) {
			log("{0} Signal - exception {1}", reference, ie.toString());
		}
	}
	
	/**
	 * @return a hashmap of the passed data or null is no data was passed.
	 */
	public HashMap<String, Object> pause() {
		HashMap<String, Object> data = null;
		try {
			data = (HashMap<String, Object>)sq.take();
			if (data.isEmpty()) data = null;
		} catch (InterruptedException ie) {
			log("{0} Pause - exception {1}", reference, ie.toString());
		}
		return data;
	}
	
	/**
	 * @param msg
	 * @param arguments
	 */
	public static void log(String msg, Object... arguments) {
		Log.logger.log(Level.FINE, "AMQP " + msg, arguments);
	}

}
