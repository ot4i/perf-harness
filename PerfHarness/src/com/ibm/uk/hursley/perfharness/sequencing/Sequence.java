/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
/*

 * PerfHarness $Name$
 */
package com.ibm.uk.hursley.perfharness.sequencing;

/**
 * Defines a sequence of integers and methods based upon those integers for validating
 * them.
 */
public interface Sequence {
	public void registerElement( int n );
	public int getMissing();
	public int getOrdering();
	public int getDuplicates();
	public int getCount();
	public int getErrors();
	public void reset();
	public void incrementErrors();
	
}
