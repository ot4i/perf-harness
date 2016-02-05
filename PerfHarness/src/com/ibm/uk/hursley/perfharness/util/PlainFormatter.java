/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

/*
 *  History:
 *  Date        ID, Company               Description
 *  ----------  -----------------------   ----------------------------------------------------------------------
 *  2006/12/00                            Release Candidate 2 
 */
package com.ibm.uk.hursley.perfharness.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A basic JDK log formatter which provides output as if the application was
 * using System.out.println (i.e. no additional formatting).
 * 
 */
public class PlainFormatter extends Formatter {
	
	private final static String ls = System.getProperty("line.separator");
	private boolean appendLS;
	
	/**
	 * Create a new formatter which looks much like plain stdout.
	 * @param appendLineSeparator Add a newline to each piece of output.
	 */
	public PlainFormatter( boolean appendLineSeparator ) {
		appendLS = appendLineSeparator;
	}
	
	@Override
	public String format(LogRecord record) {
		
		String message = formatMessage(record);
		
		// Print exception using JVMs default renderer.
		Throwable t = record.getThrown();
		if (t != null) {
			StringBuilder sb = new StringBuilder(message.length() + 100);
			sb.append( message );
			if ( appendLS && ! message.endsWith(ls) ) {
				sb.append( ls );
			}
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			t.printStackTrace(pw);
			pw.close();
			sb.append(sw.toString()).append(ls);
			return sb.toString();
		} else {
			if ( appendLS && ! message.endsWith(ls) ) {
				StringBuilder sb = new StringBuilder(message.length() + 4);
				return sb.append(message).append(ls).toString();
			} else {
				return message;
			}
		}
		
	}
	
}
