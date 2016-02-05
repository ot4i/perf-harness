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

import com.ibm.uk.hursley.perfharness.Copyright;

/**
 * This implements the bare minimum of sequence checking.  It does not keep 
 * a history and therefore cannot determine duplicates unless they arrive
 * consecutively.  Highly experimental.
 * 
 * It reports ordering as the sum of the distance between each entry and
 * the next.  This is best defined by example:
 * <table>
 *   <tr><td>BC</td><td>0</td</tr>
 *   <tr><td>BD</td><td>1</td</tr>
 *   <tr><td>BA</td><td>2</td</tr>
 *   <tr><td>ABCD</td><td>0</td</tr>
 *   <tr><td>ADCB</td><td>6</td</tr>
 *   <tr><td>DBCA</td><td>6</td</tr>?? first one should start the seq
 *   <tr><td>DCBA</td><td>6</td</tr>??
 * </table>
 */
public class BasicSequence implements Sequence {
	
	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT; // IGNORE compiler warning
	
	public int duplicates = 0;

	public int count = 0;
	public int ordering = 0;
	public int errors = 0;
	
	public int max = Integer.MIN_VALUE;
	public int min = Integer.MAX_VALUE;
	
	public int last = 0;
	public boolean first = true;
	
	/**
	 * Note that this is not synchronized and should be called with attention
	 * paid to any multi-threaded use.
	 * @param n
	 */
	public void registerElement( int n ) {
		
		if ( first ) {
			first = false;
			last = n - 1;
		}
		if ( max<n ) max = n;
		if ( min>n  ) min = n;
		if ( n==last ) duplicates++;
		count ++;
		
		int distance = n - last; // Distance from last number

		ordering += Math.abs( distance-1 );
		
		last = n;

	}

	public int getMissing() {
		// Don't return a bogus value if no numbers have been registered.
		return ((count>1)?(max-min-count+1):0);		
	}

	public int getOrdering() {
		return ordering;
	}

	public int getDuplicates() {
		return duplicates;
	}

	public int getCount() {
		return count;
	}

	public void reset() {
		duplicates = 0;
		count = 0;
		ordering = 0;
		first = true;
		errors = 0;
		
		max = Integer.MIN_VALUE;
		min = Integer.MAX_VALUE;
		
		last = 0;
	}

	public int getErrors() {
		return errors;
	}

	public void incrementErrors() {
		errors ++;
	}
	
}
