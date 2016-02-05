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
package com.ibm.uk.hursley.perfharness;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import com.ibm.uk.hursley.perfharness.util.CommandLineProperties;

/**
 * Governing the user-specified properties for an application.  It is assumed
 * that there is a default for EVERY valid property of the system and that there are no further properties set.  This
 * is only checked when adding application-specified properties.
 * @see #get(Object)
 */
public class ApplicationPropertyMap extends AbstractMap<String,String>  {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	final HashMap<String,String> longKeyMap = new HashMap<String,String>(); // Long key -> short key
	final HashMap<String,String> defaultProps = new HashMap<String,String>(); // short key -> value
	final HashMap<String,String> fileProps = new HashMap<String,String>(); // short key -> value
	final CommandLineProperties cmdLineProps = new CommandLineProperties(); // (long|short) key -> value
	final HashMap<String,String> applicationProps = new HashMap<String,String>(); // (long|short) key -> value

	private boolean strict = true;

	private Set<String> cachedKeySet = new HashSet<String>(); // Set.keySet();

	/**
	 * Load and import properties from a config file.
	 * @param configfile
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 * @see Properties#load(java.io.InputStream)
	 */
	public void readConfigFile(String configfile) throws FileNotFoundException, IOException {
		final Properties props = new Properties();
		props.load(new FileInputStream(configfile));
		
		//We have an incompatibility issue to fix here. Properties extends hashtable, but we want to store a
		//hashmap<String,String> - For Java 6, we need to convert
		for (String key : props.stringPropertyNames()) {
			fileProps.put(key, props.getProperty(key));
		}
	}
	
	/**
	 * Pass-through method.
	 * @param args command line
	 * @see CommandLineProperties#readCommandLine(String[])
	 */
	public void readCommandLine(String[] args) {
		cmdLineProps.readCommandLine( args );
	}	

	/**
	 * Returns the number of unique keys.
	 */	
	public int size() {
		return defaultProps.size();
	}
	
	/**
	 * Always false.
	 */
	public boolean isEmpty() {
		return false;
	}

	public boolean containsKey(Object key) {
		return keySet().contains( canonicalKey( key ) );
	}

	/**
	 * Gets an application property in the folowing order
	 * <ol><li>Application set values</li>
	 * <li>Command-line values</li>
	 * <li>Properties file values</li>
	 * <li>Application defaults</li></ol>
	 */
	public String get(Object key) {
		
		String canonical = canonicalKey( key );
		if ( applicationProps.containsKey( canonical ) ) return applicationProps.get( canonical );
		if ( cmdLineProps.containsKey( canonical ) ) return cmdLineProps.get( canonical );
		if ( fileProps.containsKey( canonical ) ) return fileProps.get( canonical );
		return defaultProps.get( canonical );
		
	}

	/**
	 * Places the property in the application-specific store.
	 * @throws Error an unchecked error is thrown if the property does not have a default entry.   
	 */
	public String put(String key, String value) {
		
		cachedKeySet.clear();
		
		String canonical = canonicalKey(key);
		if ( strict && ! defaultProps.containsKey(canonical) ) {
			throw new ApplicationPropertyError( "Key ["+key+"] does not exist in defaults and, therefore, cannot be set by the application." );
		}
		return applicationProps.put( canonical, value );
		
	}

	/**
	 * Only provides basic functionality of entrySet. 
	 */
	public java.util.Set<Map.Entry<String,String>> entrySet() {
	
		Set<Map.Entry<String,String>> set = new HashSet<Map.Entry<String,String>>();
		for ( String key : keySet() ) {
			set.add( new Entry( key ) );
		}
		return set;
		
	}
    
	public Set<String> keySet() {
		
		if ( cachedKeySet.isEmpty()) {
			cachedKeySet.addAll( defaultProps.keySet() );			
			if ( !strict ) {
				cachedKeySet.addAll( applicationProps.keySet() );
				cachedKeySet.addAll( cmdLineProps.keySet() );
				cachedKeySet.addAll( fileProps.keySet() );
			}
		}
		return cachedKeySet;
		
	}

	/**
	 * Add a new default property.  No validity checking is done on this operation, that is up to the user.
	 * @param property
	 * @param longKey If not null, this name is also registered to the corresponding property
	 * @param value
	 */
	void putDefaultString(String property, String longKey, String value) {
		
		defaultProps.put( property, value );
		if ( longKey != null ) {
			
			longKeyMap.put( longKey, property );
			// Update any preset keys
			canonicaliseMapEntry( cmdLineProps, longKey, property );
			canonicaliseMapEntry( fileProps, longKey, property );
			
		} // end if
		cachedKeySet.clear();
		
	}

	/**
	 * @param key A long or short key name.
	 * @return The short key name.
	 */
	private final String canonicalKey( Object key ) {
		if ( longKeyMap.containsKey( key ) ) {
			return longKeyMap.get( key);
		} else {
			return (String)key;
		}
	}
	
	/**
	 * Replacing any keys with the canonical (short) version 
	 * @param map
	 */
	private void canonicaliseMapEntry( Map<String,String> map, String longKey, String shortKey ) {
		
		if ( map.containsKey( longKey ) ) {
			String value = map.remove( longKey );
			map.put( shortKey, value );
		}

	}

	/**
	 * Determines whether or not all application paramteres set must have defaults.
	 * @param strict
	 * @return The previous setting.
	 */
	public boolean setStrictDefaults(boolean strict) {
		boolean retVal = this.strict;
		this.strict  = strict;
		return retVal;
	}
	
	class Entry implements Map.Entry<String,String> {
		
		private String key;
		
		private Entry( String key ) {
			this.key  = key;
		}
		
		public String getKey() {
			return key;
		}

		public String getValue() {
			return get( key );
		}

		public String setValue(String value) {
			return put( key, value );
		}
		
	}
	
}
