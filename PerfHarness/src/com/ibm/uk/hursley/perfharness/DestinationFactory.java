/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness;

public interface DestinationFactory {

	/**
	 * Returns the name of destination which should be used by the calling
	 * thread.
	 * 
	 * @param id
	 *            A numeric identifier (expected to be a thread number). This is
	 *            currently unused as synchronisation takes care of any required
	 *            differentiation.
	 * @return A name for the next destination, as defined by the controlling
	 *         arguments. No guarantees are made that this destination is exists
	 *         or is valid in the current situation.
	 */
	public abstract String generateDestination( int id );
	
	/**
	 * Returns the number of a destination which should be used by the calling
	 * thread.  This allows threads to ignore the -d prefix if required.
	 * @return The destination ID number assigned or -1 if no ID is required
	 */
	public abstract int generateDestinationID( int id );

}
