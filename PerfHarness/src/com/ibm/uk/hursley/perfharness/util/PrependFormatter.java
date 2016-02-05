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
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A basic JDK log formatter which prepends messages with a given string.
 * 
 */
public class PrependFormatter extends Formatter {
	
	private final static String ls = System.getProperty("line.separator");
	private String header = null;
	
	public PrependFormatter( String header ) {
		this.header = header;
	}
	
	public String format(LogRecord record) {
    	
		String message = formatMessage(record);
		StringBuffer sb = new StringBuffer(message.length() + header.length());
		sb.append(header).append(message).append(ls);

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
