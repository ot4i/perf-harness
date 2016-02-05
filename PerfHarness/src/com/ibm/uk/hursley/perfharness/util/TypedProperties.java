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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.*;

import com.ibm.uk.hursley.perfharness.Copyright;

/**
 * <p>
 * Acts a property map, providing additional typed methods to make life simpler.
 * </p>
 * <p>
 * Utility methods getString, getBoolean etc allow programmers to avoid tedious
 * type conversions and are designed to throw an (unchecked)
 * TypedPropertyException if anything goes wrong. This gives the programmer the
 * choice of explicitly catching them or not.
 * </p>
 * <p>
 * {@link #load(InputStream)} and {@link #store(OutputStream, String)} create a
 * temporary Properties instance to perform the task. They are slightly wasteful
 * and should only be used when the operation is not going to be repeated many
 * times.
 * </p>
 * 
 */
public final class TypedProperties implements Map<String,String>, Serializable {
	
	private static final long serialVersionUID = 5654768473470726773L;

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;

	private Map<String,String> map;
	/**
	 * @param map This must be a Map whose values are all String objects.
	 */
	public TypedProperties( Map<String,String> map ) {
		this.map = map;
	}
	
	/**
	 * Creates a new TypedProperties backed by a java.util.HashMap object
	 */
	public TypedProperties() {
		this.map = new HashMap<String,String>();
	}
	
	public int size() {
		return map.size();
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	public String get(Object key) {
		return map.get(key);
	}

	public String put(String key, String value) {
		return map.put(key, value);
	}

	public String remove(Object key) {
		return map.remove(key);
	}

	public void putAll(Map<? extends String, ? extends String> t) {
		map.putAll(t);
	}

	public void clear() {
		map.clear();
	}

	public Set<String> keySet() {
		return map.keySet();
	}

	public Collection<String> values() {
		return map.values();
	}

	public Set<Entry<String,String>> entrySet() {
		return map.entrySet();
	}

	public String toString() {
		return map.toString();
	}

	/**
	 * Uses a temporary {@link Properties} object to load data in. 
	 * @param is The read data from.
	 * @throws IOException 
	 * @throws ClassCastException If called when not wrapping a Properties object.
	 * @throws IOException When properties cannot be loaded.
	 */
	public void load( InputStream is ) throws IOException {
		final Properties props = new Properties();
		props.load(is);
		
		//We have an incompatibility issue to fix here. Properties extends hashtable, but we want to store a
		//hashmap<String,String> - For Java 6, we need to convert
		for (String key : props.stringPropertyNames()) {
			map.put(key, props.getProperty(key));
		}
	}

	/**
	 * Uses a temporary {@link Properties} object to load data in. 
	 * @param os The stream to write data to
	 * @param comment A header to apply to the written properties file. 
	 * @throws IOException 
	 * @throws ClassCastException If called when not wrapping a Properties object.
	 * @throws IOException When properties cannot be stored.
	 */
	public void store(OutputStream os, String comment) throws IOException {
		final Properties props = new Properties();
		props.putAll(map);
		props.store(os, comment);
	}

	/** 
	 * @param name
	 * @return String version of the requested parameter
	 * @throws Error An unchecked Error is thrown if the parameter is not known.
	 */
	public String getString(String name) {
		final String value = (String)get(name);
		if (value == null)
			throw new TypedPropertyException("Property [" + name + "] not known.");
		return value.trim();
	}

	public String getString(String name, String def) {
		final String value = (String)get(name);
		return (value == null) ? def : value.trim();
	}

	public String[] getCSStringList(String name) {
		final String value = (String)get(name);
		if (value == null)
			throw new TypedPropertyException("Property [" + name + "] not known.");
		return value.trim().split(",");
	}

	/** 
	 * Gets a property as an integer. 
	 * @param name
	 * @return int version of the requested parameter
	 * @throws Error Any NumberFormatException is converted into an unchecked Error then rethrown. 
	 */	
	public int getInt(String name) {
		final String value = getString(name);
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			throw new TypedPropertyException("NumberFormatException on [" + name + "=" + value + "]");
		}
	}

	public int getInt(String name, int def) {
		final String value = (String)get(name);
		if (value == null || value.length()==0) {
			return def;
		} else {
			try {
				return Integer.parseInt(value);
			}
			catch (NumberFormatException e) {
				throw new TypedPropertyException("NumberFormatException on [" + name + "=" + value + "]");
			}
		}
	}

	public int[] getCSIntList(String name) {
		final String value = (String)get(name);
		if (value == null)
			throw new TypedPropertyException("Property [" + name + "] not known.");
		final String[] s = value.trim().split(",");
		final int[] r = new int[s.length];
		for (int k = 0; k < s.length; k++) {
			try {
				r[k] = Integer.parseInt(s[k]);
			}
			catch (NumberFormatException e) {
				throw new TypedPropertyException("NumberFormatException on [" + name + "=" + value + "], index " + String.valueOf(k + 1));
			}
		}
		return r;
	}

	public int[] getCSIntList(String name, int n, int def) {
		final String value = (String)get(name);
		if (value == null)
			throw new TypedPropertyException("Property [" + name + "] not known.");
		final String[] s = value.trim().split(",");
		final int[] r = new int[n];
		for (int k = 0; k < n; k++) {
			try {
				if ((k < s.length) && (s[k].length() > 0))
					r[k] = Integer.parseInt(s[k]);
				else
					r[k] = def;
			}
			catch (NumberFormatException e) {
				throw new TypedPropertyException("NumberFormatException on [" + name + "=" + value + "], index " + String.valueOf(k + 1));
			}
		}
		return r;
	}

	/** 
	 * @param name
	 * @return long version of the requested parameter
	 * @throws Error Any NumberFormatException is converted into an unchecked Error then rethrown.  
	 */
	public long getLong(String name) {

		String value = getString(name);
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new TypedPropertyException( "NumberFormatException on ["+name+"="+value+"]" );
		}

	}

	public long getLong(String name, long def) {

		String value = (String)get( name );
		if ( value==null || value.length()==0 ) { 
			return def;
		} else {
			try {
				return Long.parseLong(value);
			} catch (NumberFormatException e) {
				throw new TypedPropertyException( "NumberFormatException on ["+name+"="+value+"]" );
			}
		}
	}	
	
	/** 
	 *  
	 * @param name
	 * @return Boolean version of the requested parameter.  <b>The empty string is counted as true</b>
	 * @throws Error Any NumberFormatException is converted into an unchecked Error then rethrown. 
	 */	
	public boolean getBoolean(String name) {

		String value = getString(name);
		if ("false".equalsIgnoreCase(value) || "0".equals(value) ) {
			return false;
		} else if ("true".equalsIgnoreCase(value) || "1".equals(value) || "".equals(value) ) {
			//("".equals(value)&&cmdLineProps.containsKey(name)&&!applicationProps.containsKey(name)) ) {
			// special case "" iff it came from the commandline to allow "-tx" to mean "-tx true"
			return true;
		} else {
			throw new TypedPropertyException( "Boolean value error ["+name+"="+value+"] should be [true|false|0|1]" );
		}
		
	}

	public boolean getBoolean(String name, boolean def) {

		String value = (String)get( name );
		if ( value==null || value.length()==0 ) {
			return def;
		} else {
			value = value.trim();
			if ("false".equalsIgnoreCase(value) || "0".equals(value) ) {
				return false;
			} else if ("true".equalsIgnoreCase(value) || "1".equals(value) || "".equals(value) ) {
				//("".equals(value)&&cmdLineProps.containsKey(name)&&!applicationProps.containsKey(name)) ) {
				// special case "" iff it came from the commandline to allow "-tx" to mean "-tx true"
				return true;
			} else {
				throw new TypedPropertyException( "Boolean value error ["+name+"="+value+"] should be [true|false|0|1]" );
			}
		}
	}
	
	/** 
	 * @param name
	 * @return double version of the requested parameter
	 * @throws Error Any NumberFormatException is converted into an unchecked Error then rethrown. 
	 */	
	public double getDouble(String name) {

		String value = getString(name);
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new TypedPropertyException( "NumberFormatException on ["+name+"="+value+"]" );
		}
		
	}
	
	public double getDouble(String name, double def) {

		String value = (String)get( name );
		if ( value==null || value.length()==0 ) {
			return def;
		} else {
			try {
				return Double.parseDouble(value);
			} catch (NumberFormatException e) {
				throw new TypedPropertyException( "NumberFormatException on ["+name+"="+value+"]" );
			}
		}
	}	
	
	/**
	 * 
	 * @param name
	 * @return The corresponding Class object to the value of the named property.
	 */
	@SuppressWarnings("unchecked")
	public <T> Class<? extends T> getClazz(String name) {
		
		final String clazzname = getString(name);
		Class<? extends T> c = null;
		if (clazzname != null && !"".equals(clazzname)) {
			try {
				c = (Class<? extends T>) Class.forName(clazzname.toString());
			} catch (ClassNotFoundException e) {
				throw new TypedPropertyException("[" + name + "=" + clazzname
						+ "] " + e.getMessage());
			} catch (ClassCastException e) {
				throw new TypedPropertyException("[" + name + "=" + clazzname
						+ "] " + e.getMessage());
			}
		}
		return c;
		
	}	
	
	@SuppressWarnings("unchecked")
	public <T> Class<? extends T> getClazz( String name, Class<? extends T> def ) {
		
		final String value = (String)get( name );
		if ( value==null || value.length()==0 ) {
			return def;
		} else {
			Class<? extends T> c = null;
			try {
				c = (Class<? extends T>) Class.forName(value.trim());
			} catch (ClassNotFoundException e) {
				throw new TypedPropertyException("[" + name + "=" + value
						+ "] " + e.getMessage());
			} catch (ClassCastException e) {
				throw new TypedPropertyException("[" + name + "=" + value
						+ "] " + e.getMessage());
			}
			return c;
		}
	}
	
	public void putInt( String key, int value ) {
		
		map.put( key, Integer.toString( value ) );
		
	}
	
	public void putLong( String key, long value ) {
		
		map.put( key, Long.toString( value ) );
		
	}
	
	public void putDouble( String key, double value ) {
		
		map.put( key, Double.toString( value ) );
		
	}
	
	public void putBoolean( String key, boolean value ) {
		
		map.put( key, Boolean.toString( value ) );
		
	}

}
