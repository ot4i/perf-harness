/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

package com.ibm.uk.hursley.perfharness.jms.providers;

import java.io.File;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.jms.DestinationWrapper;

/**
 * Provider independent access to JMS resources.  All destination names will be
 * interpreted as the lookup name rather than the absolute name.
 * <p>This class also provides a base on which to build vendor-specific JMSProvider
 * implementations which may also use JNDI. 
 */
public class JNDI extends AbstractJMSProvider {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	public static final String PSMODE = "PUBSUBMODE";
	public static final String PSMODE_PUB = "PUB";
	public static final String PSMODE_SUB = "SUB";
	
	protected static boolean usingJNDI;	

	/**
	 * Validate parameters.  The contents of <code>-ii</code> and <code>-iu</code> are not checked at this stage.
	 */
	public static void registerConfig() {

		Config.registerSelf( JNDI.class );
		usingJNDI = ! "".equals( Config.parms.getString("cf") ) || Config.parms.getString("pc").equalsIgnoreCase("jndi");
		
	}
	
	protected InitialContext initialContext = null;

	
	//
	// JMS 1.0.2 Methods
	//

	/**
	 * Return a Queue object from JNDI
	 */
	protected DestinationWrapper<Queue> lookupQueueFromJNDI(String uri)	throws NamingException {
		return new DestinationWrapper<Queue>(uri, (Queue) getInitialContext().lookup(uri));
	}

	/**
	 * Return a Topic object from JNDI
	 */
	protected DestinationWrapper<Topic> lookupTopicFromJNDI(String uri) throws NamingException {
		return new DestinationWrapper<Topic>(uri, (Topic) getInitialContext().lookup(uri));
	}

	/**
	 * <b>JMS 1.0.2</b>  Calls the JNDI-specific version of this method.  This method is expected to be overidden
	 * by vendor-specific subclasses.
	 * @return A valid ConnectionFactory.
	 */
	public TopicConnectionFactory lookupTopicConnectionFactory(String name)	throws JMSException,NamingException {
		return lookupTopicConnectionFactoryFromJNDI(name==null?Config.parms.getString("cf"):name);
	}
	
	/**
	 * <b>JMS 1.0.2</b>  Calls the JNDI-specific version of this method.  This method is expected to be overidden
	 * by vendor-specific subclasses.
	 * @return A valid ConnectionFactory.
	 */
	public QueueConnectionFactory lookupQueueConnectionFactory(String name)	throws JMSException, NamingException {
		return lookupQueueConnectionFactoryFromJNDI(name==null?Config.parms.getString("cf"):name);
	}
	
	/**
	 * <b>JMS 1.0.2</b>
	 * @return A valid Queue object created either from JNDI lookup or directly from the given session.
	 */
	public DestinationWrapper<Queue> lookupQueue(String uri, QueueSession session) throws JMSException, NamingException {
		if ( usingJNDI ) {
			return lookupQueueFromJNDI( uri );
		} else {
			return new DestinationWrapper<Queue>( uri, session.createQueue( uri ) );
		}
	}

	/**
	 * <b>JMS 1.0.2</b>
	 * @return A valid Topic object created either from JNDI lookup or directly from the given session.
	 */
	public DestinationWrapper<Topic> lookupTopic(String uri, TopicSession session) throws JMSException, NamingException {
		if ( usingJNDI ) {
			return lookupTopicFromJNDI( uri );
		} else {
			return new DestinationWrapper<Topic>( uri, session.createTopic( uri ) );
		}
	}	
	
	//
	// JMS 1.1 methods
	//
	
	/**
	 * Return a (JMS 1.1) Destination object from JNDI.
	 */
	protected DestinationWrapper<Destination> lookupDestinationFromJNDI(String uri) throws NamingException {
		return new DestinationWrapper<Destination>(uri,	(Destination) getInitialContext().lookup(uri));
	}
	
	/**
	 * <b>JMS 1.1</b>  Calls the JNDI-specific version of this method.  This method is expected to be overidden
	 * by vendor-specific subclasses.
	 * @return A valid ConnectionFactory.
	 */
	public ConnectionFactory lookupConnectionFactory(String name) throws JMSException,NamingException {
		return lookupConnectionFactoryFromJNDI(name==null?Config.parms.getString("cf"):name);
	}

	/**
	 * <b>JMS 1.1</b>
	 * @return A valid Topic object created either from JNDI lookup or directly from the given session.
	 */
	public DestinationWrapper<Queue> lookupQueue(String uri, Session session) throws JMSException, NamingException {
		if ( usingJNDI ) {
			return lookupQueueFromJNDI( uri );
		} else {
			return new DestinationWrapper<Queue>( uri, session.createQueue( uri ) );
		}
	}
	
	/**
	 * <b>JMS 1.1</b>
	 * @return A valid Topic object created either from JNDI lookup or directly from the given session.
	 */
	public DestinationWrapper<Topic> lookupTopic(String uri, Session session) throws JMSException, NamingException {
		if ( usingJNDI ) {
			return lookupTopicFromJNDI( uri );
		} else {
			return new DestinationWrapper<Topic>( uri, session.createTopic( uri ) );
		}
	}

	/**
	 * <b>JMS 1.1</b>
	 * @return A valid Destination object
	 * @throws JMSException This method only works when using JNDI, since an abstract Destination object
	 * can only be looked up and not created.
	 */
	public DestinationWrapper<Destination> lookupDestination(String uri, Session session) throws JMSException, NamingException {
		if ( usingJNDI ) {
			return lookupDestinationFromJNDI( uri );
		} else {
			throw new JMSException( "Abstract destinations cannot be created (should be using JNDI?)" );
		}
	}
	
	/**
	 * <b>JMS 2.0</b>
	 * @param uri String containing queue name
	 * @param context Active JMSContext must be supplied if not retrieving from JNDI
	 * @return A valid DestinationWrapper object containing a queue retrieved from JNDI or created from the provided JMSContext
	 * @throws NamingException
	 */
	public DestinationWrapper<Queue> lookupQueue(String uri, JMSContext context) throws JMSException, NamingException {
		if (usingJNDI || context == null) {
			return lookupQueueFromJNDI(uri); 
		} else {
			return new DestinationWrapper<Queue>(uri, context.createQueue(uri));
		}
	}

	/**
	 * <b>JMS 2.0</b>
	 * @param uri String containing topic name
	 * @param context Active JMSContext must be supplied if not retrieving from JNDI
	 * @return A valid DestinationWrapper object containing a queue retrieved from JNDI or created from the provided JMSContext
	 * @throws NamingException
	 */
	public DestinationWrapper<Topic> lookupTopic(String uri, JMSContext context) throws JMSException, NamingException {
		if (usingJNDI || context == null) {
			return lookupTopicFromJNDI(uri);
		} else {
			return new DestinationWrapper<Topic>(uri, context.createTopic(uri));			
		}
	}    
	
	
	//
	// JNDI specific methods
	//
	/**
	 * Return a JNDI Initialcontext based upon any parameters passed on the command line and then
	 * using the standard JNDI methods for commandline and properties files. 
	 */
	public synchronized InitialContext getInitialContext() throws NamingException {
	
		if (initialContext == null) {
			//Altered second parameter to object as not all entries are strings
			final Hashtable<Object, Object> env = new Hashtable<Object,Object>();

			//If we are passed a properties file, use that for initial context creation
			String ipf = Config.parms.getString("ipf").trim();
			if (ipf.length() > 0) {
				try {
					File f = new File(ipf);
					InputStream is = new FileInputStream(f);
					Properties props = new Properties();
					props.load(is);
					env.putAll(props);
				} catch (Exception e) {
					Log.logger.log(Level.SEVERE, "Problem loading JNDI properties file: " + ipf + "; Exception: " + e);
				}
			} else {
			final String username = Config.parms.getString("us");
			if (username != null && username.length() != 0) {
				Log.logger.log(Level.INFO, "getInitialContext(): authenticating as \"" + username + "\"");
				// env.put(Context.SECURITY_AUTHENTICATION, "simple"); // ???
				env.put(Context.SECURITY_PRINCIPAL, username);
				env.put(Context.SECURITY_CREDENTIALS, Config.parms.getString("pw"));
			}

			final String ii = Config.parms.getString("ii");
			if (ii != null && ii.length() != 0)
				env.put(Context.INITIAL_CONTEXT_FACTORY, ii);

			final String iu = Config.parms.getString("iu");
			if (iu != null && iu.length() != 0)	
				env.put(Context.PROVIDER_URL, iu);
			}
			boolean useSunJRE = Config.parms.getBoolean("iz");
			if (useSunJRE) {
				System.out.println("Using Suns JRE - Setting ORB initialisation string");
				//env.put("java.naming.corba.orb", org.omg.CORBA.ORB.init((String[]) null, null));
				//The alternative can also be tried if the above doesnt work with JRE in use
				//The IBM JRE will automatically find the correct ORB, so do not set this param
				//env.put("com.ibm.CORBA.ORBInit", "com.ibm.ws.sib.client.ORB");
			}
			
			final String jndiPropertiesFile = Config.parms.getString("jf");
			if(jndiPropertiesFile != null && jndiPropertiesFile.length() != 0) {
				Log.logger.log( Level.INFO, "Loading JNDI properties from file {0}", jndiPropertiesFile );
				final Properties props = new Properties();
				try {
	                props.load( new BufferedInputStream( new FileInputStream( jndiPropertiesFile ) ) );
                	for (final Iterator<Map.Entry<Object, Object>> iter = props.entrySet().iterator(); iter.hasNext();) {
                		final Map.Entry<Object, Object> entry = iter.next();
						
                		String key = (String)entry.getKey();
                		String value = (String)entry.getValue();						
				
                		Log.logger.log( Level.FINE, "Adding JNDI property key: {0} value: {1} ", new Object[] { key, value } );
                		env.put(key,value);	
						
                	} // end for all props
				} catch (FileNotFoundException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
                } catch (IOException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
                }
			}
			
			initialContext = new InitialContext(env);
			
		}
		return initialContext;
	
	}
	
	/**
	 * <b>JMS 1.1</b> Look up the named ConnectionFactory object.
	 * @param uri
	 * @throws NamingException
	 */
	protected synchronized ConnectionFactory lookupConnectionFactoryFromJNDI(String uri) throws NamingException {
	
		final InitialContext jndiContext = getInitialContext();
		return (ConnectionFactory)jndiContext.lookup(uri);
	
	}

	/**
	 * <b>JMS 1.0.2</b> Look up the named QueueConnectionFactory object.
	 * @param uri
	 * @throws NamingException
	 */	
	protected synchronized QueueConnectionFactory lookupQueueConnectionFactoryFromJNDI(String uri) throws NamingException {

		final InitialContext jndiContext = getInitialContext();
		return (QueueConnectionFactory)jndiContext.lookup(uri);
	
	}

	/**
	 * <b>JMS 1.0.2</b> Look up the named TopicConnectionFactory object.
	 * @param uri
	 * @throws NamingException
	 */	
	protected synchronized TopicConnectionFactory lookupTopicConnectionFactoryFromJNDI(String uri) throws NamingException {
	
		final InitialContext jndiContext = getInitialContext();
		return (TopicConnectionFactory)jndiContext.lookup(uri);
	
	}

	public void createQueue(String name) throws Exception {
		throw new RuntimeException( "JNDI module cannot create a destination" );
	}

	public void createTopic(String name) throws Exception {
		throw new RuntimeException( "JNDI module cannot create a destination" );
	}

	public void createConnectionFactory(String name) throws Exception {
		throw new RuntimeException( "JNDI module cannot create a connection factory" );
	}
	
	public void createTopicConnectionFactory(String name) throws Exception {
		throw new RuntimeException( "JNDI module cannot create a connection factory" );
	}
	
	public void createQueueConnectionFactory(String name) throws Exception {
		throw new RuntimeException( "JNDI module cannot create a connection factory" );
	}

    /**
     ** delete a queue on the provider. differs from deleting a jms queue.
     ** unbind the associated admin object from the jndi store. 
     */
    public void deleteQueue(String name) throws Exception {
    	// No op
    }
    
    /**
     ** delete a topic on the provider. differs from deleting a jms topic.
     ** unbind the associated admin object from the jndi store. 
     */
    
    public void deleteTopic(String name) throws Exception {
    	// No op
    }
    
    /**
     ** unbind an admin object from the jndi store.
     */
    public void unbind(String lookupName) throws NamingException {
        try { 
       		getInitialContext().unbind(lookupName);
        } catch ( NameNotFoundException e ) {
        	// Swallowed
        }
    }
    
    public void initializeSetup() throws Exception {
    	// No op
    }

    public void completeSetup() throws Exception {
    	// No op
    }
    
    public boolean isQueue(Destination destination) {
    	return destination instanceof Queue;
    }

}
