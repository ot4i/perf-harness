/********************************************************* {COPYRIGHT-TOP} ***
* IBM Confidential
* OCO Source Materials
* Performance Harness for Java Message Service
*
* (C) Copyright IBM Corp. 2006  All Rights Reserved.
*
* The source code for this program is not published or otherwise
* divested of its trade secrets, irrespective of what has been
* deposited with the U.S. Copyright Office.
********************************************************** {COPYRIGHT-END} **/
/*
 * $Id: MessageFactory.java 243 2006-05-29 19:06:28Z mcarter $
 * JMSPerfHarness $Name$
 */
package com.ibm.uk.hursley.perfharness.mqtt;

/**
 * Represents a system for producing and handling JMS Messages.  Implementations
 * will vary considerably in how they approach this.
 * @see javax.jms.Message
 */
public interface MessageFactory {

	/**
	 * Return a new message body (MqttClient.publish doesn't use a special Message type) based on settings.
	 * @param threadname The textual name of the current thread.
	 * @param seq An incrementing sequence number (this will be a per-thread sequence).
	 * @return
	 * @throws Exception 
	 */
	public abstract byte[] createMessage(String threadname, int seq) throws Exception;
		
}
