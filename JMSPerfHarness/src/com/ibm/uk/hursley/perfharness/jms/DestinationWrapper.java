/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

package com.ibm.uk.hursley.perfharness.jms;

import javax.jms.Destination;

/**
 * Simple data accessor that provides the requested name as well as the returned
 * destination. In some cases (especially if using JNDI) there is no reason why
 * these would be the same.
 * 
 */
public final class DestinationWrapper<E extends Destination> {
	
	public E destination;
	public String name;
	
	public DestinationWrapper( String name, E destination ) {
		this.destination = destination;
		this.name = name;
	}
	
}
