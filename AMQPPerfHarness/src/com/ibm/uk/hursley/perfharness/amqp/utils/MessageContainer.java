/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.amqp.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.util.TypedPropertyException;

/**
 * @author ElliotGregory
 *
 */
public class MessageContainer {
	
	/** Message types **/
	public enum MESSAGE_TYPE {STRING, BYTES};

	/** Type of message for this message **/
	private final MESSAGE_TYPE messageType;
	/** Option message file name **/
	private final String messageFile;
	/** Size of message to construct **/
	private final Integer messageSize;
	
	/** Message as bytes **/
	private byte[] messageBytes = null;
	/** Message as String **/
	private String messageString = null;
	/** Message as Buffer **/
	private ByteBuffer messageBuffer = null;
	
	/**
	 * @throws MessageException
	 */
	public MessageContainer() throws MessageException {
		this(null);
	}
	
	/**
	 * @throws MessageException
	 */
	public MessageContainer (final String prefix) throws MessageException {
		
		// Collect arguments
		String messageTypeStr = Config.parms.getString("mt");	// Message type (String or buffer)
		messageFile = argumentAsString("mf", null);				// File name to take message from
		messageSize = argumentAsInteger("ms", null);			// Size of generate message to create
		
		// Validate arguments
    	if (messageTypeStr.equalsIgnoreCase("string")) {
    		messageType = MESSAGE_TYPE.STRING;
    	}
    	else if (messageTypeStr.equalsIgnoreCase("buffer")) {
    		messageType = MESSAGE_TYPE.BYTES;
    	} else {
    		throw new TypedPropertyException("Invalid message type of " + messageTypeStr + " must be String or Buffer");
    	}
		
		// Create message
		if (messageFile != null) {
			messageBytes = readMessageFile(prefix, messageFile);
		}
		
		if (messageBytes == null) {
			messageBytes = generateRandomBytes(prefix, null, messageSize);
		}
	}
	
	/**
	 * @return
	 */
	public MESSAGE_TYPE getMessageType() {
		return messageType;
	}
	
	/**
	 * @return
	 */
	public ByteBuffer getBufferMessage () {
		if (messageBuffer == null) {
			messageBuffer = ByteBuffer.allocate(messageBytes.length);
			messageBuffer.put(messageBytes);
			messageBuffer.rewind();
		}
		return messageBuffer;
	}
	
	/**
	 * @return
	 */
	public String getStringMessage () {
		if (messageString == null) {
			messageString = new String(messageBytes);
		}
		return messageString;
	}
	
	/**
	 * @param prefix to be include at the start of the byte array.
	 * @param messageFileName
	 * @return
	 * @throws MessageException 
	 */
	private byte[] readMessageFile(final String prefix, final String messageFileName) throws MessageException {
		log(Level.FINER, "Entry: readMessageFile {1}", messageFileName);
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		InputStream bis = null;
		try {
			if (prefix != null) bos.write(prefix.getBytes());
			
			bis = new BufferedInputStream(new FileInputStream(messageFile));
			byte [] bytesRead = new byte[bis.available()];
			bis. read(bytesRead);
			bos.write(bytesRead);
		} catch (IOException e) {
			MessageException me = new MessageException ("Could not read file " + messageFile,e);
			log(Level.FINER, "Throw: readMessageFile Exception" + me.toString());
			throw me;
		} finally {
			if (bis != null)
				try {
					bis.close();
				} catch (IOException e) {
					// Ignored
				}
		}

		byte[] bytes = bos.toByteArray();
		log(Level.FINER, "Exit: readMessageFile size={1}", bytes.length);
		return bytes;
	}

	
	/**
	 * @param level
	 * @param msg
	 * @param arguments
	 */
	public static void log(final Level level, String msg, Object... arguments) {
		Log.logger.log(Level.FINE, "AMQP " + msg, arguments);
	}
	
	/**
	 * @author ElliotGregory
	 *
	 */
	public class MessageException extends Exception {
		private static final long serialVersionUID = 1L;
		/**
		 * @param message
		 */
		public MessageException (final String message) {
			super(message);
		}
		/**
		 * @param message
		 * @param cause
		 */
		public MessageException (final String message, final Throwable cause) {
			super(message, cause);
		}
	}
	

	/**
	 * Generate a sequence of byte starting with the optional supplied prefix and 
	 * there afterwards values in the 65-122 range.
	 * @param prefix <Byte> to be include at the start of the byte array.
	 * @param body <Byte> prefix sequence of bytes
	 * @param length <int> the total length to bytes to generate including prefix.
	 * @return
	 */
	private byte[] generateRandomBytes(final String prefix, final Byte[] body, final int length) {
		byte[] data = new byte[ length ];
		for (int i = 0; i < length; i++) {
			data[i] = (body != null && i < body.length) ? data[i] = body[i] : (byte) (Math.random() * (122-65) + 65);
		}
		if (prefix != null) {
			byte[] prefixBytes = prefix.getBytes();
			for (int i=0; i < prefix.length(); i++) {
				data[i] = prefixBytes[i];
			}
		}
	    return data;
	}
	
	/**
	 * @param name
	 * @param defValue
	 * @return
	 */
	public String argumentAsString (final String name, final String defValue) {
		return argumentAsString (name, defValue , false);
	}

	/**
	 * @param name
	 * @param defValue
	 * @param emptyValid
	 * @return
	 */
	public String argumentAsString (final String name, final String defValue, final boolean emptyValid) {
		String retValue = Config.parms.getString(name, defValue);
		if (!emptyValid && retValue != null & retValue.isEmpty()) {
			retValue = null;
		}
		return retValue;
	}
	

	/**
	 * @param name
	 * @param defValue
	 * @return
	 */
	public Integer argumentAsInteger (final String name, final Integer defValue) {
		return Config.parms.containsKey(name) ? Config.parms.getInt(name) : null;
	}

}
