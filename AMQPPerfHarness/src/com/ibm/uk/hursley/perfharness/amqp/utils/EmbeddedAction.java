/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp.utils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import com.ibm.mqlight.api.BytesDelivery;
import com.ibm.mqlight.api.Delivery;
import com.ibm.mqlight.api.StringDelivery;

 /**
 * @author ElliotGregory
 * 
 * Embedded Action message for the test payloads.
 * Takes the form of
 * $EC$command=argument[,command=argument]$EC$
 */
public class EmbeddedAction {

	private final static String EYE_CATCHER = "$EC$";
	private final static int MAX_SEARCH = 200;
	private final Map<String,String> commands = new HashMap<String,String>();
	
	/**
	 * @param delivery
	 * @return
	 */
	public static EmbeddedAction createEmbeddedAction (final Delivery delivery) {
		EmbeddedAction rc = null;
		
		byte[] data;
		if (delivery != null) {
			if (delivery instanceof StringDelivery) {
				data = ((StringDelivery) delivery).getData().getBytes();
				rc = createEmbeddedAction(data);
			}
			else if (delivery instanceof BytesDelivery) {
				ByteBuffer buffer = ((BytesDelivery) delivery).getData();
				rc = createEmbeddedAction(buffer.array());
			}
		}
		return rc;
	}

	
	/**
	 * @param buffer
	 * @return
	 */
	public static EmbeddedAction createEmbeddedAction (final byte [] buffer) {

		if (isEyeCatcher(buffer, 0)) {
			int maxSearch = Math.min(MAX_SEARCH, buffer.length);
			for (int endOffset = EYE_CATCHER.length(); endOffset < maxSearch; endOffset++) {
				if (isEyeCatcher(buffer, endOffset)) {
					return new EmbeddedAction(new String(buffer,EYE_CATCHER.length(), endOffset - EYE_CATCHER.length()));
				}
			}
		}
		
		return null;
	}
	
	/**
	 * @param buffer
	 * @param offset
	 * @return
	 */
	private static boolean isEyeCatcher (final byte [] buffer, final int offset) {
		int os = offset;
		for (byte b : EYE_CATCHER.getBytes()) {
			if (buffer[os++] != b) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @throws EmbeddedActionException 
	 * @throws ParserConfigurationException 
	 * 
	 */
	public EmbeddedAction () {

	}
	
	/**
	 * 
	 */
	private EmbeddedAction (final String rawText) {
		String[] items = rawText.split(",");
		for (String item : items) {
			String[] elements = item.split("=");
			if (elements.length == 2) {
				commands.put(elements[0], elements[1]);
			}
		}
	}
	
	/**
	 * @param command
	 * @param argument
	 */
	public void putCommand (final String command, final String argument) {
		commands.put(command, argument);
	}
	
	/**
	 * @param command
	 * @return
	 */
	public String getCommand (final String command) {
		return commands.get(command);
	}
	

	/**
	 * @param command
	 * @param defaultValue
	 * @return
	 */
	public String getCommand (final String command, final String defaultValue) {
		return commands.containsKey(command) ? commands.get(command) : defaultValue;
	}
	
	/**
	 * @return
	 */
	public String generateMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(EYE_CATCHER);
		String separator = "";
		for (Map.Entry<String,String> entry : commands.entrySet()) {
			sb.append(separator);
			separator = ",";
			sb.append(entry.getKey());
			sb.append("=");
			sb.append(entry.getValue());
		}
		sb.append(EYE_CATCHER);
		return sb.toString();
	}

	private final static String EOL = System.getProperty("line.separator");

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("EmbeddedAction [commands="+ EOL);
		for (Map.Entry<String,String> entry : commands.entrySet()) {
			String value = entry.getValue();
			if (value == null) value = "NULL";
			sb.append(entry.getKey() + " = " + value + EOL);
		}
		sb.append("]" + EOL);
		return sb.toString();
	}
}
