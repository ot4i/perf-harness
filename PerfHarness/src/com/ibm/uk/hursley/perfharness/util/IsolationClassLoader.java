/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

/*
 *  History:
 *  Date        ID, Company                 Description
 *  ----------  ------------------------    ------------------------------------------------------
 *  2006/12/00                              Release Candidate 2 
 */
package com.ibm.uk.hursley.perfharness.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A non-recursive classloader implementation providing isolation of subsystems.
 * An opt-out list of shareable classes can also be specified. It should be
 * noted that "package level" (i.e. not specified as public or protected)
 * methods will NOT be shareable but will throw java.lang.IllegalAccessError at
 * runtime.
 * 
 */
public class IsolationClassLoader extends URLClassLoader {

	private final ClassLoader parentClassLoader;
	// Class names which will be delegated to the parent loader.
	private final ArrayList<String> sharedClasses = new ArrayList<String>();

	public IsolationClassLoader() {

		this(getSystemClassLoader());

	}

	public IsolationClassLoader(ClassLoader cl) {

		super(makeURLs());
		parentClassLoader = cl == null ? getSystemClassLoader() : cl;

	}

	public IsolationClassLoader(ClassLoader cl, String[] sharedClasses) {

		this(cl);
		if ( sharedClasses!=null ) {
			Collections.addAll( this.sharedClasses, sharedClasses);
		}

	}

	/**
	 * Convert the classpath into a set of URLs. Anything invalid on the
	 * classpath will simply print a non-terminating exception stack trace.
	 * 
	 * @return
	 */
	private static URL[] makeURLs() {

		ArrayList<URL> urls = new ArrayList<URL>();
		String[] classpath = System.getProperty("java.class.path").split(
				System.getProperty("path.separator"));
		for (String cp : classpath) {
			try {
				urls.add(new File(cp).toURI().toURL());
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		return urls.toArray(new URL[0]);

	}

	public Class<?> loadClass(String name) throws ClassNotFoundException {

		return loadClass(name, false);

	}

	public Class<?> loadClass(String name, boolean resolve)
			throws ClassNotFoundException {

		Class<?> result = findLoadedClass(name);
		if (result == null) {

			// Check special cases. JNDI and JMS interfaces are included here as
			// otherwise JNDI lookups will return an object which is
			// incompatible with its own interface!
			if (sharedClasses.contains(name) || name.startsWith("javax.naming") || name.startsWith("javax.jms")) {
				result = parentClassLoader.loadClass(name);
			} else {

				// Load isolated version
				try {
					result = findClass(name);
				} catch (ClassNotFoundException e) {
					// Must be a java.*, javax.* etc
					try {
						result = parentClassLoader.loadClass(name);
					} catch (ClassNotFoundException e2) {
						throw e2;
					}
				}
			}
		}
		
		if (resolve) {
			resolveClass(result);
		}

		return result;

	}

}
