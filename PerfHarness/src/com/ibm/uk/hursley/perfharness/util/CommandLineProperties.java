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
package com.ibm.uk.hursley.perfharness.util;

import java.util.HashMap;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Copyright;

/**
 * Wrapper class to present a commandline parser as a collections API interface allowing it to be iterated over.
 * Also gives warnings for duplicate entries.
 */
public final class CommandLineProperties extends HashMap<String,String> {
	
	private static final long serialVersionUID = -783106115916741865L;

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	/**
	 * Properties class cannot store null, so we must use a special token.  Note that the textual contents of
	 * the String are irrelevant because all checks are done with == and not .equals(). 
	 */
	private static final String NULL = "NULL";
	
	public CommandLineProperties() {
		super();
	}
	
	public CommandLineProperties( String args[] ) {
		readCommandLine( args );
	}

	/**
	 * Removes any - or -- prepends as it populates properties.
	 * @param args A command line.
	 */
	public void readCommandLine(String[] args) {
		
		String key = null;
		String value = "";
		for (int i = 0; i < args.length; i++) {
			if (args[i].startsWith("-")) {
				// this is a short key
				if (key != null) {
					// Store last key, value pair
					put(key, value);
				}
				key = args[i].substring(1);
				int ik = key.indexOf('=');
				if ( ik>=0 ) {
					// this is -a=b type, so aplit it
					value = key.substring( ik+1 );
					key = key.substring( 0, ik ).toLowerCase();
				} else {
					key = key.toLowerCase();
					value = NULL;
				}
			} else {
				// this is a value (not prepended with - or --)
				if ( value == NULL ) {
					// this is the first value
					value = args[i];
				} else {
					// this else allows us to have spaces in the key values i.e.
					// the values will span
					// multiple commandline arguments.
					value += (" " + args[i]);
				}
			}
		}
		
		// Store last key on the line
		if (key != null) {
			put(key, value);
		}
		
	}

	public String get(String key) {
		String value = super.get( key );
		return value==NULL?"":value;
	}

	/**
	 * Checks the value does not already exist (in effect making this a Set)
	 */
	public String put(String key, String value) {
		if ( super.containsKey( key ) ) {
			Config.logger.warning( "Parameter ["+key+"] duplicated on the command line" );
			return null;
		} else {
			return super.put( key, value );
		}
	}

}
