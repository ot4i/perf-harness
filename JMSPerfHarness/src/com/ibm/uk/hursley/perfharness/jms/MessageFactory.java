/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.jms;

import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.util.ByteArray;
import com.ibm.uk.hursley.perfharness.util.FastByteArrayOutputStream;

/**
 * Represents a system for producing and handling JMS Messages.  Implementations
 * will vary considerably in how they approach this.
 * @see javax.jms.Message
 */
public interface MessageFactory {

	/**
	 * Return a new Message object based on settings.
	 * @param session JMS session to create the message from.
	 * @param threadname The textual name of the current thread.
	 * @param seq An incrementing sequence number (this will be a per-thread sequence).
	 * @throws Exception 
	 */
	public abstract Message createMessage(Session session, String threadname, int seq) throws Exception;

	/**
	 * JMS 2.0 Compatibility
	 * Return a new Message object based on settings.
	 * @param context JMS context to create the message from.
	 * @throws Exception 
	 */
	public abstract Message createMessage(JMSContext context) throws Exception;

	/**
	 * Genrates then sets a CorrelationId.
	 * @param sender
	 * @param outMessage
	 * @throws JMSException
	 */
	public abstract String setJMSCorrelationID(WorkerThread sender, Message outMessage) throws JMSException;

	/**
	 * A helper method for data validation.  This attempts to return a byte array for the given
	 * Message type.  Currently only TextMessage and BytesMessage are supported.  
	 * @param msg
	 * @throws Exception 
	 */	
	public ByteArray getBytes(Message msg, FastByteArrayOutputStream fbaos) throws Exception;
	
	/**
	 * A helper class for the SameMessage functionality. This checks if we are using the same message
	 * and acts accordingly.
	 * @param session
	 * @param threadname
	 * @param seq
	 * @param worker
	 * @return
	 * @throws Exception
	 */
	public abstract Message getMessage(Session session, String threadname, int seq, WorkerThread worker) throws Exception;

}
