/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.util;

import java.io.OutputStream;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import com.ibm.uk.hursley.perfharness.Log;

/**
 * Sends log to chosen console stream. This is intended to be used as a drop in replacement
 * for "proper" logging facilities.
 *
 * @see java.util.logging.ConsoleHandler
 */
public class SelectableConsoleHandler extends StreamHandler {
	/**
	 * Log to given stream
	 * @param stream
	 */
	public SelectableConsoleHandler(OutputStream stream) {
		super();
		setOutputStream(stream);
	}

	/**
	 * Log to stdout.
	 */
	public SelectableConsoleHandler() {
		this(System.out);
	}

	public void publish(LogRecord record) {
		super.publish(record);
		flush();
	}

	public void close() {
		Log.reviveLogger();
		flush();
	}
}
