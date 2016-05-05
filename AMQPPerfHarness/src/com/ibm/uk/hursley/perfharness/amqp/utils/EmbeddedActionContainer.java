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

import com.ibm.mqlight.api.BytesDelivery;
import com.ibm.mqlight.api.Delivery;
import com.ibm.mqlight.api.StringDelivery;

/**
 * @author ElliotGregory
 *
 */
public class EmbeddedActionContainer {
	
	private final EmbeddedAction embeddedAction;
	
	/**
	 * @param delivery
	 */
	public EmbeddedActionContainer (final Delivery delivery) {
		byte[] data;
		if (delivery != null) {
			if (delivery instanceof StringDelivery) {
				data = ((StringDelivery) delivery).getData().getBytes();
				embeddedAction = EmbeddedAction.createEmbeddedAction(data);
			}
			else if (delivery instanceof BytesDelivery) {
				ByteBuffer buffer = ((BytesDelivery) delivery).getData();
				embeddedAction = EmbeddedAction.createEmbeddedAction(buffer.array());
			}
			else {
				embeddedAction = null;
			}
		}
		else {
			embeddedAction = null;
		}
	}

	
	/**
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public String getValue(final String key, final String defaultValue) {
		String value = defaultValue;
		if (embeddedAction != null) value = embeddedAction.getCommand(key, defaultValue);
		return value;
	}
	
	/**
	 * @param key
	 * @return
	 */
	public String getValue(final String key) {
		return getValue(key, null);
	}


	@Override
	public String toString() {
		return "EmbeddedActionContainer [embeddedAction=" + embeddedAction + "]";
	}
	
	
}
