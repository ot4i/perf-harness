/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.util;

/**
 * An error indicating a problem with the properties.  Since this is not
 * a checked exception, the programmer has the option of catching this or
 * ignoring it (since most of the checking is done at startup).
 * 
 */
public class TypedPropertyException extends RuntimeException {
	
	private static final long serialVersionUID = -2232000761547888252L;

	public TypedPropertyException(String message) {
		super( message );
	}
	
}
