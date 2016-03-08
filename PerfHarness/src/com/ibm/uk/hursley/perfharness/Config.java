/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.ibm.uk.hursley.perfharness.util.PrependFormatter;
import com.ibm.uk.hursley.perfharness.util.SelectableConsoleHandler;
import com.ibm.uk.hursley.perfharness.util.TypedProperties;
import com.ibm.uk.hursley.perfharness.util.TypedPropertyException;

/**
 * Handles all of the definition of configuration parameters and subsequent
 * access to them by the tooling. The code is somewhat complex in order to allow
 * the following features
 * <ul>
 * <li>Each module to specify its own properties file (allowing modules to be
 * developed in isolation.</li>
 * <li>Short and long help descriptions are available.</li>
 * <li>Each module is partially self documenting through the use of
 * descriptions and "xtra" descriptions (used with the full output)</li>
 * <li>Unassociated modules may independently use the same parameter but with
 * different meanings.</li>
 * <li>All loaded modules are verified such that no active parameter is
 * duplicated and no redundant parameters are left on the command line.</li>
 * <li>The help system gives help for the current context (thereby hiding
 * unused and unnecessary modules)</li>
 * <li>The help system allows specific modules to be described (if requested).
 * </li>
 * <li>Parameters may have defaults.</li>
 * <li>Defaults may be overridden by subclasses of a module.</li>
 * <li>Parameters from one module may be used by other sub-modules.</li>
 * <li>Parameters may be validated for syntax errors before the program begins
 * operation</li>
 * <li>Parameters may be a classname (for specifying module implementations).
 * These are searched for within several packages) as a static checked for
 * existence and correct inheritance.</li>
 * <li>Multiple parameter errors are listed in a single pass.</li>
 * <li>(in early testing) Help may also be dumped as XML allowing it to be
 * automatically picked by a documentation generating XSLT script.</li>
 * </ul>
 * This is achieved by a system of statically registering modules. The
 * requirements of this system are:
 * <ul>
 * <li>The entire inheritance tree, including any reflection, is touched
 * statically before the code tries to access its properties.</li>
 * <li>Each class needs to be touched in a sensible order that allows one
 * module to use another modules parameters only if they have been
 * registered/validated first.</li>
 * <li>Each registerable class must implement
 * <tt>public static void registerConfig()</tt>.</li>
 * <li>All registration related code must be contained solely within
 * registerConfig.</li>
 * <li>No class should call registerConfig directly, this is done only from
 * Config. </li>
 * <li>{@link Config#registerSelf(Class)} must be called (and should not called
 * by another class).</li>
 * 
 * <li>Each class should call {@link Config#registerAnother(Class)}for all
 * logical "child" classes which need registering.</li>
 * </ul>
 * Adhering to these requirements requires vigilance!. Check out the JMS code
 * to learn by example.
 * 
 */
public final class Config {
	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	public static final Logger logger;

	private static final ConfigFormatter logFormatter;

	static {
		logger = Logger.getLogger("com.ibm.uk.hursley.perfharness.Config");
		Handler handler = new SelectableConsoleHandler(System.err);
		handler.setFormatter(logFormatter = new ConfigFormatter());
		logger.setUseParentHandlers(false);
		logger.addHandler(handler);
		logger.setLevel(Level.ALL);
		handler.setLevel(Level.ALL);
	}

	/**
	 * List of registered modules.
	 */
	private static final LinkedHashMap<String,ResourceBundle> regModules = new LinkedHashMap<String,ResourceBundle>();
	
	/**
	 * Private Properties class.
	 */
	private final static ApplicationPropertyMap parmsInternal = new ApplicationPropertyMap();

	/**
	 * The public properties for this application.  This field is <i>public</i> for simplicity but <i>final</i> as
	 * this means it cannot be subverted by poor programming.
	 */	
	public final static TypedProperties parms = new TypedProperties(parmsInternal);

	// commandline
	private static final int INITIAL = 0;
	private static final int LOADING = 1;	// we have read command line, are loading modules
	private static final int LOADED = 2;	// all current modules loaded

	private static int configState = INITIAL;

	private static boolean invalid = false; // is the configuration invalid

	private static boolean asXML = false;

	private static boolean userConfigSupplied = false;

	private static final class ConfigFormatter extends PrependFormatter {
		private boolean squashExceptions = false;
		public ConfigFormatter() {
			super("Invalid parameter: ");
		}
		public String format(LogRecord record) {
			invalid = true;
			if (squashExceptions) {
				record.setThrown(null);
			}
			return super.format(record);
		}
		public void setSquashExceptions(boolean squash) {
			this.squashExceptions = squash;
		}
	}

	/**
	 * Called by each "module" to register itself and its parameters. It is
	 * expected that a &lt;classname&gt;.properties file will be co-located with
	 * the .class file. The format for this file is not written down but can be
	 * easily observed by example, just remember that a line break must end with "<tt>\n\</tt>"
	 * with no trailing spaces!
	 * <p>
	 * <b>NB.</b> This should only be called from the static registerConfig
	 * method of the class in question!
	 * 
	 * @param class1 -
	 *            the class being registered
	 */
	public synchronized static void registerSelf(Class<?> class1) {
		if (Log.logger.isLoggable(Level.FINEST)) {
			Log.logger.finest("Config is registering " + class1.getName());
		}

		switch (configState) {
		case LOADED:
			Log.logger.severe("Module " + class1.getName() + " tried to register too late.");
			break;
		case INITIAL:
			Log.logger.severe("Command line has not been successfully read and we are trying to register " + class1.getName());
			break;
		}

		if (!isRegistered(class1)) {
			try {
				final ResourceBundle bundle = ResourceBundle.getBundle(class1.getName());

				// ...add it to the list of modules
				regModules.put(class1.getName(), bundle);

				// Read in options for module
				registerProperties(class1, bundle);
			}
			catch (MissingResourceException e) {
					Log.logger.log(Level.FINEST, "Config found no resource bundle for {0}", class1.getName());
			}
			catch (Exception e) {
				logger.log(Level.SEVERE, "Error processing resource bundle for " + class1.getName(), e);
			}
		} // end if ! isRegistered
	}

	/**
	 * Has this class already been registered.
	 * 
	 * @param clazz
	 * @return
	 */
	public static boolean isRegistered(Class<?> clazz) {
		return regModules.containsKey(clazz.getName());
	}

	/**
	 * Register any classes used or owned (in the UML sense) by the caller. This
	 * should only be called from the callers registerConfig method and should
	 * only directed at those classes it is directly related to in runtime
	 * usage.
	 * <ul>
	 * <li>The Reflection API is used to determine if we can call
	 * <tt>public static void registerConfig()</tt> on the given class.</li>
	 * <li>This method will recursively attempt registration of superclasses of
	 * the given class</li>
	 * <li>No exceptions are raised if any class was already registered or was
	 * not registerable.</li>
	 * </ul>
	 * 
	 * @param clazz
	 *            Class to register
	 */
	public synchronized static <T> void registerAnother(Class<T> clazz) {
		try {
			// (Recursively) register an unregistered superclass first
			Class<? super T> parent = clazz.getSuperclass();
			if (parent != Object.class && !isRegistered(parent)) {
				registerAnother(parent);
			}

			// Find and invoke "public static void registerConfig()" if it
			// exists
			Method m = clazz.getDeclaredMethod("registerConfig", (Class[])null);
			if (Modifier.isStatic(m.getModifiers())) {
				m.invoke((Object)null, (Object[])null);
			} else {
				logger.log(Level.SEVERE, "Method {0} on {1} is not static", new Object[]{m.getName(), clazz.getName()});
			}
		}
		catch (NoSuchMethodException e) {
			// swallowed
		}
		catch (InvocationTargetException e) {
			logger.log(Level.WARNING, "Error attempting Config registration of " + clazz.getName(), e.getCause());
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Error attempting Config registration of " + clazz.getName(), e);
		}
	}

	/**
	 * Returns an unmodifiable Map of the given bundle.
	 * 
	 * @param bundle
	 * @return A Collections API version of the ResourceBundle.
	 */
	private static Map<String,String> resourceBundleMap(ResourceBundle bundle) {
		final HashMap<String,String> map = new HashMap<String, String>();
		final Enumeration<String> e = bundle.getKeys();
		while (e.hasMoreElements()) {
			final String key = e.nextElement();
			map.put(key, (String)bundle.getObject(key));
		}
		return Collections.unmodifiableMap(map);
	}

	/**
	 * Load, syntax-check and register the default properties of the given
	 * module. This method also checks for duplicate properties, but allows
	 * shadowed properties from subclasses.
	 * 
	 * @param regdata
	 */
	private static void registerProperties(Class<?> clazz, ResourceBundle bundle) {
		final Map<String, String> bundleMap = resourceBundleMap(bundle);
		final Iterator<Map.Entry<String, String>> iter = bundleMap.entrySet().iterator();
		while (iter.hasNext()) {
			final Map.Entry<String, String> entry = iter.next();
			final String key = entry.getKey(); // eg hq.dflt
			final String property = key.substring(0, key.length() - 5); // eg hq
			
			// Only process the .dflt items, the rest can be delivered from their resource bundles
			if (key.endsWith(".dflt")) {
				final String longKey = bundleMap.get(property + ".long"); // eg. headquarters
				final String value = entry.getValue(); // eg 10

				// Has this property already been registered?
				if (parmsInternal.get(property) != null) {
					// Find the class that owns this property
					final String classname = findFirstClassOwningProperty(property);
					if (classname == null) {
						// No previous owner ?!
						// Must be an application-specified property that has
						// been set very early on.

						// This shouldn't be necessary, so print a warning
						// message for developers.
						logger.log(Level.WARNING, "Property [{0}] has been overridden before it was even registered.", property);
						// Backfill anyway
						parmsInternal.putDefaultString(property, longKey, value);
					} else {
						Class<?> c = null;
						try {
							c = Class.forName(classname);
						}
						catch (ClassNotFoundException e) {
							// No op (impossible state)
						}

						// Are we a descendant? ...
						if (c.isAssignableFrom(clazz)) {
							// ... Yes, overwrite parameter defaults (this is
							// not considered as an application
							// specified property). Done anyway later on!
							parmsInternal.putDefaultString(property, longKey, value);
						} else {
							// ... No, error
							logger.log(Level.WARNING, "Property [{0}] exists twice in ResourceBundles", property);
							// Keep going and try to validate remaining
							// parameters
						} // end if isassignable
					} // end if classname
				} else { // end if getproperty

					// Property has not been registered is a new default
					// property, just add it in
					parmsInternal.putDefaultString(property, longKey, value);
				}

				// Does parameter pass type-checking etc
				checkParameterIsValid(bundle, property);
			} // end if dflt
		} // end while keys
	}

	/**
	 * Checks primitive parameters can be parsed without exception. this should
	 * absolve the programmer from responsibility to check them later on. Note
	 * that application-defined properties may well be set AFTER this method is
	 * called.
	 * 
	 * @param bundle
	 * @param key
	 * @return True if the named parameter passes some basic syntax checking.
	 */
	private static boolean checkParameterIsValid(ResourceBundle bundle, String key) {
		// This call will get the effective setting whether from defaults,
		// command line or property file
		final String value = parms.getString(key);
		try {
			// Get declared type
			final String type = bundle.getString(key + ".type");

			// Test parsing with relevant class
			if ("java.lang.Boolean".equals(type))
				parms.getBoolean(key);
			else
			if ("java.lang.Integer".equals(type))
				parms.getInt(key);
			else
			if ("java.lang.Long".equals(type))
				parms.getLong(key);
			else
			if ("java.lang.Double".equals(type))
				parms.getDouble(key);
			else
			if ("java.lang.String".equals(type)) {
				// No-op
			} else
				// We have specified a non-primitive "class" parameter.
				return checkClassParameterIsValid(key, value, type);

		}
		catch (MissingResourceException e) {
			// We dont have a "type" for this one
			logger.log(Level.WARNING,"INTERNAL ERROR: No type specified for property [{0}]", key);
			return false;
		}
		catch (TypedPropertyException err) {
			// NumberFormatException means the value did not parse correctly.
			logger.log(Level.WARNING, "Parameter validation failed for property [" + key + ']', err);
			return false;
		}
		// success if we passed all tests
		return true;
	}

	/**
	 * Checks that non-primitive types pass the following checks:
	 * <ul>
	 * <li>Class type exists.</li>
	 * <li>Configured implementiation exists.</li>
	 * <li>Implementaiton extends the class-type.</li>
	 * </ul>
	 * The classes are searched for in the following manner
	 * <ul>
	 * <li>(Fully qualified) &lt;type&gt; already exists.</li>
	 * <li>&lt;type&gt; is relative from the root package (e.g. -tc
	 * jms.r11.Publisher)</li>
	 * <li>&lt;type&gt; is relative from the package of the class type (e.g.
	 * -pc WebSphereMQ)</li>
	 * </ul>
	 * 
	 * @param key
	 * @param value
	 * @param type
	 * @return
	 */
	private static final boolean checkClassParameterIsValid(String key, String value, String type) {
		// We have specified a "class" parameter. This may need to be amended to
		// include the full package.

		// For class parameters, we either take a full package+class or
		// a value relative from the package of the root class defined as
		// the type. The chosen class must be assignable to the root class.

		// For relative values, we substitute the full classname into the
		// command
		// line arguments for subsequent accesses.
		Class<?> rootClass = null;
		try {
			// Get defined class
			rootClass = Class.forName(type);
		}
		catch (ClassNotFoundException e1) {
			logger.log(Level.WARNING, "INTERNAL ERROR: Class [{0}]] does not exist, required for property [{1}]", new Object[]{ type, key });
			return false;
		}

		// This check should be generalised or the class renamed
		if (value.equalsIgnoreCase("webspheremq") && !value.equals("WebSphereMQ")) {
			parms.put("pc", "WebSphereMQ");
			parmsInternal.put("pc", "WebSphereMQ");
			value = "WebSphereMQ";
			Log.logger.log(Level.WARNING, "Parameter -pc ({0}) is case-sensitive and should read WebSphereMQ", value);
		}

		if (value.equalsIgnoreCase("wbimb") && !value.equals("WBIMB")) {
			parms.put("pc", "WMB");
			parmsInternal.put("pc", "WMB");
			value = "WMB";
			Log.logger.log(Level.WARNING, "Parameter -pc ({0}) specified WBIMB this class has been renamed to WMB", value);
		}

		// Allow null values for classes
		if (value == null || "".equals(value))
			return true;

		Class<?> clazz;
		try {
			clazz = Class.forName(value);
		}
		catch (ClassNotFoundException e2) {
			// Not found, try prepending root package
			try {
				final String newvalue = "com.ibm.uk.hursley.perfharness." + value;
				// Note: could calculate what the root package is from our own package!
				clazz = Class.forName(newvalue);
				// replace the default entry
				parmsInternal.put(key, newvalue);
			}
			catch (ClassNotFoundException e3) {
				// Not found, now try prepending the package of the rootclass
				try {
					final String newvalue = rootClass.getPackage().getName() + '.' + value;
					clazz = Class.forName(newvalue);
					parmsInternal.put(key, newvalue);
				}
				catch (ClassNotFoundException e4) {
					// not found, bail out
					logger.log(Level.WARNING, "Property [{0}] specifies a class [{1}] which cannot be located.", new Object[] { key, value });
					return false;
				} // end try catch
			} // end try catch
		} // end try catch
		if (!rootClass.isAssignableFrom(clazz)) {
			logger.log(Level.WARNING, "Property [{0}={1}] specifies a class that does extend {3}", new Object[]{ key, value, rootClass });
			return false;
		}
		// success if we passed all tests
		return true;
	}

	/**
	 * @param key
	 * @return First class that registered that property or null
	 */
	private static String findFirstClassOwningProperty(String key) {
		final String keydflt = key + ".dflt";
		for (final Iterator<String> iter = regModules.keySet().iterator(); iter.hasNext(); ) {
			final String name = iter.next();
			final ResourceBundle bundle = (ResourceBundle)regModules.get(name);
			try {
				if (bundle.getString(keydflt) != null)
					return name;
			}
			catch (MissingResourceException e) {
				// Expensive No-op, but only called at program initiation
			} // end try
		} // end for
		return null;
	}

	/**
	 * @param key
	 * @return Last class that registered that property or null
	 */
	private static String findLastClassOwningProperty(String key) {
		final String keydflt = key + ".dflt";
		final List<String> l = Collections.list(Collections.enumeration(regModules.keySet()));
		for (final ListIterator<String> iter = l.listIterator(regModules.size()); iter.hasPrevious(); ) {
			final String name = iter.previous();
			final ResourceBundle bundle = regModules.get(name);
			try {
				if (bundle.getString(keydflt) != null)
					return name;
			}
			catch (MissingResourceException e) {
				// Expensive No-op, but only called at program initiation
			} // end try
		} // end for
		return null;
	}

	/**
	 * Describes named modules
	 * 
	 * @param modules
	 *            full classname of a module, also accepts multiple
	 *            space-separated classnames
	 * @param inFull
	 *            Describe in full detail
	 * @return
	 */
	public static String describeModules(String modules, boolean inFull) {
		final StringBuffer sb = new StringBuffer(1000);
		// Handle multiple space-separated modules
		describeModules_internal(sb, Arrays.asList(modules.split("\\s")), inFull);

		return sb.toString();
	}

	/**
	 * Describes the currently loaded module set.
	 * 
	 * @param inFull
	 */
	public static String describe(boolean inFull) {
		final StringBuffer sb = new StringBuffer();
		describeModules_internal(sb, regModules.keySet(), inFull);
		return sb.toString();
	}

	/**
	 * @param sb
	 *            StringBuffer to write the description into.
	 * @param list
	 *            A collection object containing one or more module names as
	 *            Strings.
	 * @param inFull
	 *            Describe in full detail
	 */
	private static void describeModules_internal(StringBuffer sb, Collection<? extends String> modules, boolean inFull) {

		if (asXML)
			sb.append("<modules>\n");
		for (final Iterator<? extends String> iter = modules.iterator(); iter.hasNext(); ) {
			String name = iter.next();
			// Get resources from memory
			ResourceBundle bundle = (ResourceBundle)regModules.get(name);
			if (bundle == null) {
				// Get resources from disk
				String searchlist[] = new String[] {
					"com.ibm.uk.hursley.perfharness.jms.providers.",
					"com.ibm.uk.hursley.perfharness.",
					"",
				};

				// Note: make relative to our own Package
				int index = searchlist.length;
				while (index-- > 0) {
					try {
						bundle = ResourceBundle.getBundle(searchlist[index] + name);
						// This part is only executed if successful
						name = searchlist[index] + name;
						index = 0;
					}
					catch (MissingResourceException e) {
						// no-op
					}
				} // end while searching
			} // end if bundle == null

			if (asXML)
				describeModuleAsXML(sb, name, bundle, inFull);
			else
				describeModuleAsText(sb, name, bundle, inFull);

			if (inFull)
				sb.append('\n');
		} // end for iterator
		if (asXML)
			sb.append("</modules>\n");
	}

	/**
	 * @param sb
	 *            IN/OUT StringBuffer parameter.
	 * @param name
	 *            Required for recursive help which cannot instantiate any Class
	 *            objects
	 * @param bundle
	 *            Data associated with the desired module.
	 * @param inFull
	 *            Describe in full detail
	 */
	private static void describeModuleAsText(StringBuffer sb, String name, ResourceBundle bundle, boolean inFull) {
		if (bundle == null) {
			sb.append("UNKNOWN MODULE [" + name + "]\n");
			return;
		}
		sb.append(name).append(":\n");
		try {
			if (inFull)
				sb.append(bundle.getString(name + ".desc")).append("\n\n");
		}
		catch (MissingResourceException e) {
		}

		final Enumeration<String> keys = bundle.getKeys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			if (key.endsWith(".dflt")) {
				key = key.substring(0, key.length() - 5);

				// Check property is not shadowed
				// Note: in the case of shadowed properties, which desc should we use
				final String last = findLastClassOwningProperty(key);
				if (last == null || last.equals(name)) {
					// == null for recursive help
					boolean hidden = false;
					try {
						hidden = bundle.getString(key + ".hide") != null;
					}
					catch (MissingResourceException e) {
						// no .hide specified = not hidden
					}

					if (inFull || !hidden) {
						// Get key description
						try {
							final String desc = bundle.getString(key + ".desc");
							sb.append("  ").append(key).append("\t").append(desc);

							// Get default value
							String def = null;
							try {
								def = bundle.getString(key + ".dflt");
							}
							catch (MissingResourceException e) {
								// No-op
							}
							if (def != null)
								sb.append(" (default: ").append(def).append(")");

							// Get current value
							sb.append("\n");
							try {
								if (inFull)
									sb.append("\t".concat(bundle.getString(key + ".xtra").replaceAll("\n", "\n\t"))).append('\n');
							}
							catch (MissingResourceException e) {
							}

							if (!bundle.getString(key + ".type").startsWith("java.lang")) {
								if (inFull) {
									final StringTokenizer modules = new StringTokenizer(bundle.getString(key + ".modules"));
									while (modules.hasMoreElements())
										sb.append("\t\t" + (String)modules.nextElement() + "\n");
								} else
									sb.append("\t\tFor full module list use -hm " + name + "\n");
							} // end if modules
						}
						catch (MissingResourceException e) {
							// undocumented feature !
						}
					} // end if ! hidden
				} // ends if ! shadowed
			} // end if endsWith(dflt)
		} // end while keys
	}

	/**
	 * Does not use any XML library or XSD template!
	 * 
	 * @param sb
	 *            IN/OUT StringBuffer parameter.
	 * @param name
	 *            Required for recursive help which cannot instantiate any Class
	 *            objects
	 * @param bundle
	 *            Data associated with the desired module.
	 * @param inFull
	 *            Describe in full detail
	 */
	private static void describeModuleAsXML(StringBuffer sb, String name, ResourceBundle bundle, boolean inFull) {
		// ignore inFull 
		sb.append( "<module name=\"").append(name).append("\">\n");
		if (bundle != null) {
			try {
				sb.append("<desc>").append(xmlFormat(bundle.getString(name + ".desc"))).append("</desc>\n");
			}
			catch (MissingResourceException e) {
			}

			final Enumeration<String> keys = bundle.getKeys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				if (key.endsWith(".dflt")) {
					key = key.substring(0, key.length() - 5);
					// Check property is not shadowed
					// Note: in the case of shadowed properties, which desc should we use
					final String last = findLastClassOwningProperty(key);
					if (last == null || last.equals(name)) {
						// Get key description
						try {
							final String desc = bundle.getString(key + ".desc");
							sb.append("<arg id=\"").append(key).append("\" default=\"");

							// Get default value
							String def = null;
							try {
								def = bundle.getString(key + ".dflt");
							}
							catch (MissingResourceException e) {
								// No-op
							}
							if (def != null)
								sb.append(def);

							sb.append("\"").append(">\n<desc>").append(xmlFormat(desc)).append("</desc>\n");

							try {
								sb.append("<xtra>").append(xmlFormat(bundle.getString(key + ".xtra"))).append("</xtra>\n");
							}
							catch (MissingResourceException e) {
							}

							if (!bundle.getString(key + ".type").startsWith("java.lang")) {
								try {
									final StringTokenizer modules = new StringTokenizer(bundle.getString(key + ".modules"));
									sb.append("<argmodules>\n");
									while (modules.hasMoreElements())
										sb.append("<argmodule id=\"" + (String)modules.nextElement() + "\"/>\n");
									sb.append("</argmodules>");
								}
								catch (MissingResourceException e) {
									// Swallowed - no modules parameter exists
								}
							} // end if modules
							sb.append("</arg>\n"); 
						}
						catch (MissingResourceException e) {
							// undocumented feature !
						}
					} // ends if ! shadowed
				} // end if endsWith(dflt)
			} // end while keys
		} // End if bundle != null
		sb.append("</module>\n");
	}

	/**
	 * Escapes symbols &gt; and &lt; in text
	 * 
	 * @param desc
	 * @return
	 */
	private static String xmlFormat(String desc) {
		// Only works in Java 1.4 onwards
		return desc.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
	}

	@SuppressWarnings("unused")
	private static String concatStrings(String... strings) {
		boolean first = true;
		final StringBuilder sb = new StringBuilder();
		for (String s : strings) {
			if (first)
				first = false;
			else
				sb.append(' ');
			sb.append(s);
		}
		return sb.toString();
	}

	/**
	 * Initialise the Config system. This method must be called before the first
	 * call to "register" from any class.
	 * 
	 * @param commandLine
	 *            Read in commandline supplied arguments. At this stage they
	 *            cannot be parsed or checked as we do not know what modules are
	 *            going to be loaded!
	 * @param root
	 *            If not <tt>null</tt>, this class is the registered after
	 *            the commandline has been processed (and Log and Config have
	 *            been registered). This is just shorthand for
	 *            {@link Config#registerAnother(Class)}.
	 * 
	 * @return false if no user config (i.e. non-default values) was supplied.
	 *         It is up to the application to decide if this is an invalid
	 *         configuration option.
	 */
	public synchronized static boolean init(String[] commandLine, Class<?> root) {
		if (configState==INITIAL) {

			// Read in command line
			if (commandLine != null && commandLine.length != 0) {
				userConfigSupplied = true;
				parmsInternal.readCommandLine(commandLine);
			}
			configState = LOADING;

			// Read in properties file
			try {
				String configfile = parms.getString("hp");
				if (configfile.length() > 0) {
					try {
						parmsInternal.readConfigFile(configfile);
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "Error reading file [" + configfile + "]", e);
					}
				} // end if
			}
			catch (TypedPropertyException e) {
				// No op - hp was not specified
			}

			// Register ourselves first !
			registerConfig();

			// Now we can use Log
			if (userConfigSupplied) {
				final StringBuilder sb = new StringBuilder();
				for (String s : commandLine)
					sb.append(s).append(' ');
				Log.logger.log(Level.FINEST, "Commandline:");
				Log.logger.log(Level.FINEST, sb.toString());
			}

		} // if ! commandline read
		if (root != null)
			registerAnother(root);
		
		return userConfigSupplied;
	}

	/**
	 * Register ourselves. Note that (since we are the config class) this
	 * implementation breaks almost all the contractual rules set out in this
	 * class' javadoc for normal implementations of registerConfig.
	 */
	private static void registerConfig() {
		// register the log class first as it will help clarify any subsequent
		// error reporting.
		Log.registerConfig();

		// Now we can use the Log parameters
		logFormatter.setSquashExceptions(!Config.parms.getBoolean("st"));
		registerSelf(Config.class);
		parmsInternal.setStrictDefaults(parms.getBoolean("hs"));

		int count = 0;
		if (parmsInternal.cmdLineProps.containsKey("h"))
			count++;
		if (parmsInternal.cmdLineProps.containsKey("hm"))
			count++;
		if (parmsInternal.cmdLineProps.containsKey("hf"))
			count++;
		if (parmsInternal.cmdLineProps.containsKey("v"))
			count++;

		if (count > 1)
			Config.logger.warning("Cannot specify more than one of -h, -hm, -hf or -v");

		asXML = parms.getBoolean("hx");
	}

	/**
	 * Status of the current configuration.
	 * 
	 * @return True if anything has been logged to the Config.logger object.
	 */
	public static boolean isInvalid() {
		return invalid;
	}

	/**
	 * Called by the application to notify that all modules are now registered.
	 * This enables unused parameters to be detected and exposed. Also provides
	 * the marker for when "-help" should be acted upon. A call to this method
	 * should be followed by a final check for invalid supplied parameters.
	 * 
	 * @see #isInvalid()
	 */
	public static void markLoaded() {
		configState = LOADED;
		if (userConfigSupplied) {
			if (parms.getBoolean("hs")) {
				getRedundantProperties(parmsInternal.cmdLineProps, "(from commandline)");
				getRedundantProperties(parmsInternal.fileProps, "(from file)");
			}
		} // end if args set

		try {
			displayHelpIfNeeded();
		}
		catch (ApplicationPropertyError e) {
			// No-op (invalid values of -h et al. will throw an exception here)
		}

		// All registration now complete so....
		if (invalid)
			exit();
	}

	/**
	 * Warn of invalid configuration and die!
	 */
	public static void exit() {
		Log.logger.log(Level.SEVERE, "The current configuration is not valid. Try re-running with -h to see available options.");
		System.exit(1);
	}

	/**
	 * Go through the property set to identify any that are not in the defaults.
	 * 
	 * @param c A map to check for surplus properties.
	 * @param src Textual name of property map being checked.
	 */
	private static void getRedundantProperties(Map<String,String> c, String src) {
		final Set<String> cmdset = new HashSet<String>(c.keySet()); // Take a copy given keySet
		final Set<String> defset = parmsInternal.keySet(); // Get all registered keys
		cmdset.removeAll(defset); // Subtract

		// Warn of any remaining (unregistered) values
		for (final Iterator<String> iter = cmdset.iterator(); iter.hasNext(); ) {
			final String key = iter.next();
			logger.log(Level.WARNING, "Parameter [{0}] {1} is not known/valid in this configuration", new Object[]{ key, src });
		} // end for
	}

	/**
	 * Process any help requests by calling the correct specific methods.
	 */
	private static void displayHelpIfNeeded() {
		int i = 0;
		String module = null;

		if (parms.getBoolean("h"))
			i = 1;
		if (parms.getBoolean("v"))
			i = 2;
		if (parms.getBoolean("hf"))
			i = 3;
		else {
			module = parms.getString("hm");
			if (module.length() > 0)
				i = 5;
		}

		if (i > 0 && asXML)
			System.out.println("<product>\n");

		switch (i) {
		case 1: // -h
			System.out.println(getVersion());
			System.out.println("(use -hf to see more help options)");
			System.out.println(Config.describe(false));
			break;
		case 2: // -v
			System.out.println(getVersion());
			break;
		case 3: // -hf
			System.out.println(getVersion());
			System.out.println(Config.describe(true));
			break;
		case 5: // -hm
			System.out.println(getVersion());
			System.out.println(describeModules(module, true));
			break;
		} // end switch

		if (i > 0) {
			if (asXML)
				System.out.println("</product>\n");
			System.exit(0);
		}
	}

	/**
	 * @return String description of the tools version (from properties file)
	 */
	public static String getVersion() {
		final String localURL = getLocalResourceURL(Config.class, "build.properties");
		final Properties buildProps = new Properties();
		String retVal;

		try {
			buildProps.load(Config.class.getClassLoader().getResourceAsStream(localURL));
			final String name = buildProps.getProperty("build.name");
			final String version = buildProps.getProperty("build.version");
			final String id = buildProps.getProperty("build.id");
			retVal = name + " " + version + " (build " + id + ")";
		}
		catch (Exception e) {
			Log.logger.severe("Cannot find build.properties -- version is unidentifiable.");
			retVal = "[unknown PerfHarness derivative]";
		}

		if (asXML)
			return "<name>" + retVal + "</name>\n";
		else
			return retVal;
	}

	private static String getLocalResourceURL(Class<?> c, String name) {
		final String root = c.getPackage().getName().replace('.', '/');
		return root + '/' + name;
	}

	/**
	 * Allows derivative applications to alter the system defaults before the
	 * config defaults are initialised. This should be used sparingly and must
	 * be called before {@link #init(String[], Class)}. Note that
	 * user-specified parameters will still override these values.
	 * 
	 * @throws RuntimeException
	 *             A call was made after initialisation started.
	 */
	public static void injectEarlyParameters(String key, String value) {
		if (configState == INITIAL) {
			final boolean strict = parmsInternal.setStrictDefaults(false);
			parmsInternal.put(key, value);
			parmsInternal.setStrictDefaults(strict);
		} else {
			throw new RuntimeException("Early parameter injection is only applicable BEFORE init() is called.");
		}
	}
}
