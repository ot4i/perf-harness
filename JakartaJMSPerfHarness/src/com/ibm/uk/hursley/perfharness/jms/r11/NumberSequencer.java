/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.jms.r11;

import java.io.Serializable;

/**
 * NumberSequencer
 * ---------------
 * In the style of a Singleton class, but the constructor has been left public
 * for use when multiple instantiations are needed.
 * 
 * When a single instance is needed across multiple objects, use the getInstance() method
 * Otherwise use the constructor as normal.
 * 
 * @author cbanes
 * @version 080903
 */

public class NumberSequencer implements Serializable {
	
	private static final long serialVersionUID = 8720187380081311603L;
	private int number = 0;
    private int maxNumber = 10;
    
    private static NumberSequencer instance;
	
    /**
     * Protected Constructor needed for Singleton style
     */
    protected NumberSequencer() {}
	
    
	/**
	 * 
	 * @param maxSelectorNumber
	 */
	public NumberSequencer(int maxNumber) {
		this();
		this.maxNumber = maxNumber;
	}
		
	/**
	 * Get Instance method. Returns a instance if there isn't already one, otherwise
	 * returns the existing instantiation.
	 * @param maxNumber
	 * @return
	 */
	public synchronized static NumberSequencer getInstance(int maxNumber) {
		if (instance == null)
			instance = new NumberSequencer(maxNumber);
		return instance;
	}
	
	/**
	 * Returns the next number.
	 * @return int
	 */
	public synchronized int nextNumber() {
		int tmpNumber = number;
		number = (number+1) % maxNumber;
		return tmpNumber;
	}
	
	/**
	 * Set's the current number
	 * @param number
	 */
	public void setCurrentNumber(int number) {
		if (number < maxNumber && number >= 0) {
			this.number = number;
		}
	}
}
