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
import java.util.concurrent.LinkedBlockingDeque;

import com.ibm.mqlight.api.Delivery;
import com.ibm.uk.hursley.perfharness.amqp.AMQPWorkerThread.AMQPException;

/**
 * @author ElliotGregory
 * 
 * Outer class to contain the various sub classes for the FIFO facility.
 *
 */
public class FIFO {

	/**
	 * @author ElliotGregory
	 *
	 */
	public static class FIFODelivery extends FIFOAbs {
		public FIFODelivery(final String reference) {
			super(reference);
		}

		/**
		 * Insert an item into the FIFO
		 * 
		 * @param delivery
		 */
		public void put(final Delivery delivery) {
			super.put(delivery);
		}

		/**
		 * Removes a delivery message from the FIFO
		 * 
		 * @return a delivery or null if exception.
		 * @throws AMQPException 
		 *
		 */
		public Delivery get() throws AMQPException {
			Object item = super.get();
			if (item instanceof Delivery) {
				return (Delivery) item;
			}
			else {
				throw new AMQPException("Internal error: expected Delivery but got " + item.getClass().getName());
			}
		}
	}

	/**
	 * @author ElliotGregory
	 * 
	 * Wrapped FIFOAbs class for handling HashMaps
	 *
	 */
	public static class FIFOHashMap extends FIFOAbs {
		public FIFOHashMap(final String reference) {
			super(reference);
		}


		/**
		 * @param hashMap
		 */
		public void put(final HashMap<String, Object> hashMap) {
			super.put(hashMap);
		}


		/* (non-Javadoc)
		 * @see com.ibm.uk.hursley.perfharness.amqp.utils.FIFO.FIFOAbs#get()
		 */
		@SuppressWarnings("unchecked")
		public HashMap<String, Object> get() throws AMQPException {
			Object item = super.get();
			if (item instanceof HashMap) {
				return (HashMap<String, Object>) item;
			}
			else {
				throw new AMQPException("Internal error: expected HashMap but got " + item.getClass().getName());
			}
		}
	}

	/**
	 * @author ElliotGregory
	 *
	 *  Wrapped FIFOAbs class for handling Delivery
	 */
	public static abstract class FIFOAbs {

		/** Associated FIFO **/
		private final LinkedBlockingDeque<Object> lbd = new LinkedBlockingDeque<Object>();
		/** Trace reference **/
		protected final String reference;

		/**
		 * @param reference
		 */
		public FIFOAbs(final String reference) {
			this.reference = reference;
		}

		/**
		 * @return the number of items in the FIFO.
		 */
		public int size() {
			return lbd.size();
		}

		/**
		 * Insert an item into the FIFO
		 * 
		 * @param delivery
		 */
		void put(final Object item) {
			lbd.add(item);
		}

		/**
		 * Removes a delivery message from the FIFO
		 * 
		 * @return a delivery or null if exception.
		 * @throws AMQPException 
		 *
		 */
		protected Object get() throws AMQPException {
			Object item = null;
			try {
				item = lbd.take();
				if (item instanceof AMQPException) {
					throw (AMQPException) item;
				}
			} catch (InterruptedException ie) {
				// ignore - return null
			}
			return item;
		}
	}
	
	/**
	 * @author ElliotGregory
	 *
	 */
	public static class AMQPWakeupException extends AMQPException {
		private static final long serialVersionUID = 1L;

		public AMQPWakeupException() {
			super("Wake-up requested");
		}
		
	}

}
