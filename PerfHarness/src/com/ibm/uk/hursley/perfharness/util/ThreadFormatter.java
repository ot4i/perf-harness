/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A JDK log formatter which prepends messages with the name of the issuing
 * thread. Note that the mechanism (<tt>Thread.currentThread</tt> probably
 * should not be relied upon, particularly with batching loggers. Use with
 * caution!
 * 
 * A cache is used to improve performance of threadname lookups.
 * 
 */
public class ThreadFormatter extends Formatter {

	private final HashMap<String,String> threadNames = new HashMap<String,String>();

	private final static String ls = System.getProperty("line.separator");

	/**
	 * Format the given record with the current thread's name.
	 */
	public String format(LogRecord record) {

		String key = Integer.toString(record.getThreadID());
		String name = (String) threadNames.get(key);
		if (name == null) {
			name = Thread.currentThread().getName() + ": ";
			// Note: use a WeakReference to the thread object (for long-lived
			// applications with many threads).
			threadNames.put(key, name);
		}

		String message = formatMessage(record);
		StringBuilder sb = new StringBuilder(message.length() + 100);
		sb.append(name).append(message).append(ls);

		// Print exception using JVMs default renderer.
		Throwable t = record.getThrown();
		if (t != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			t.printStackTrace(pw);
			pw.close();
			sb.append(sw.toString());
		}

		return sb.toString();
	}

}
