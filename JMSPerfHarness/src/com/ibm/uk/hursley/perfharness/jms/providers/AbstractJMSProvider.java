/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/


package com.ibm.uk.hursley.perfharness.jms.providers;

import java.rmi.server.UID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Defines the methods and facilities common to all supported JMS providers. It
 * is expected that all vendor implementations will extend JNDI rather than this
 * class.
 * 
 * @see com.ibm.uk.hursley.perfharness.jms.providers.JNDI
 */
public abstract class AbstractJMSProvider implements JMSProvider {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;
	
	private static JMSProvider instance = null;

	protected static boolean durable;	
	/**
	 * Contains a record of thread -> clientId (for UID() random ids).  Required for reconnections.
	 */
	protected static ConcurrentHashMap<String, String> durableConnectionIdMap = null; // this could be implemented as a threadlocal.
	
	/**
	 * Register our presence and look up any required parameters for this class.
	 * 
	 * The current implementation does not fully differentiate between the
	 * different modes it may be used in and will therefore allow many
	 * combinations of arguments which are nonsensical (most of them are simply
	 * ignored in the current configuration).
	 * 
	 * @see Config#registerSelf(Class).
	 */
	public static void registerConfig() {
		// static validation of parameters

		Config.registerSelf( AbstractJMSProvider.class );

		if ( ! Config.isInvalid() ) {
			int timeOut = Config.parms.getInt("to");
			if ( timeOut<0 ) {
				Config.logger.log(Level.WARNING,"Time out (to={0}) must be at least 0", timeOut);
			}
				
			int am = Config.parms.getInt("am");
			if ((am != Session.AUTO_ACKNOWLEDGE) && (am != Session.DUPS_OK_ACKNOWLEDGE) && (am != Session.CLIENT_ACKNOWLEDGE)) {
				Config.logger.log(Level.WARNING, "Acknowledgement (am={0}) must be one of \n{1} for Auto\n{2} for DupsOK\n{3} for Client\n", new Object[] {am, Session.AUTO_ACKNOWLEDGE, Session.DUPS_OK_ACKNOWLEDGE, Session.CLIENT_ACKNOWLEDGE} );
			}
			
			if ( Config.parms.getString("pw").length()!=0 && Config.parms.getString("us").length()==0 ) {
				Config.logger.warning("Cannot specify -pw without -us");
			}
	
			durable = Config.parms.getBoolean( "du" );
			
			int commit = Config.parms.getInt("cc");
			if ( commit<1 ) {
				Config.logger.log( Level.WARNING, "Commit count (cc={0}) must be greater than 0", commit);
			}
			
			/*
			 * TODO: Java 8 supports target type inference, so when moving to Java8 can
			 * change following line to:
			 * Config.registerAnother( Config.parms.<JMSProvider>getClazz("pc") );
			 */
			Config.registerAnother( Config.parms.getClazz("pc") );
		}
		
	}
	
	protected AbstractJMSProvider() {
		super();
	}

	/**
	 * Simple singleton pattern to return the JMSProvider in use.  Hence, this is limited to a single provider. 
	 * @return A subclass of JMSProvider defined by the <code>-pc</code> parameter.
	 */
	public synchronized static JMSProvider getInstance() {
		if ( instance == null ) {
			try {
				/*
				 * TODO: Java 8 support target type inference, so when moving to Java 8 can
				 * replace the following line with:
				 * instance = Config.parms.<JMSProvider>getClazz("pc").newInstance();
				 */
				instance =(JMSProvider) Config.parms.getClazz("pc").newInstance();
			} catch ( Exception e ) {
				Log.logger.log( Level.SEVERE, "Problem getting JMS provider class", e );
			}
		}
		return instance;
	}

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.jms.providers.JMSProvider#getQueueConnection(javax.jms.QueueConnectionFactory)
	 */
	public QueueConnection getQueueConnection(QueueConnectionFactory qcf)
			throws JMSException {

		final QueueConnection qc;
		final String username = Config.parms.getString("us");
		if (username != null && username.length() != 0) {
			Log.logger.log(Level.INFO, "getQueueConnection(): authenticating as \"" + username + "\"");
			final String password = Config.parms.getString("pw");
			qc = qcf.createQueueConnection(username, password);
		} else {
			qc = qcf.createQueueConnection();
		}

		return qc;

	}	

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.jms.providers.JMSProvider#getTopicConnection(javax.jms.TopicConnectionFactory)
	 */
	public TopicConnection getTopicConnection(TopicConnectionFactory tcf, String uniqueID )
			throws JMSException {

		final TopicConnection tc;
		final String username = Config.parms.getString("us");
		if (username != null && username.length() != 0) {
			Log.logger.log(Level.INFO, "getTopicConnection(): authenticating as \"" + username + "\"");
			final String password = Config.parms.getString("pw");
			tc = tcf.createTopicConnection(username, password);
		} else {
			tc = tcf.createTopicConnection();
		}

		if (durable) {
			// Note: change signature to match getConnection
			setDurableConnectionId( tc, ((WorkerThread)Thread.currentThread()), uniqueID );
		} // end if durable

		return tc;

	}	
	
	/**
	 * <b>JMS 1.1</b>Equivalent of both JMS 1.0.2 connection methods combined.  This is now wrappered by the
	 * reconnect-enabled getConnection.
	 * @param cf
	 * @param worker
	 * @param uniqueID If not null, this value is unique within the current JVM
	 * @throws JMSException
	 * @see #getConnection(ConnectionFactory, WorkerThread, String)
	 */
	protected Connection getConnection_internal(ConnectionFactory cf, WorkerThread worker, String uniqueID ) throws JMSException {
	
		final Connection c;
		final String username = Config.parms.getString("us");

		if (username != null && username.length() != 0) {
			Log.logger.log(Level.INFO, "getConnection_internal(): authenticating as \"" + username + "\"");
			final String password = Config.parms.getString("pw");
			c = cf.createConnection(username, password);
		} else {
			c = cf.createConnection();
		}

		// Always set the id as we don't know how this connection will be used
		setDurableConnectionId( c, worker, uniqueID );
		
		return c;
		
	}

	/**
	 * A durable subscription requires a client id on the connection.  This method generates
	 * such an id.  if -id is specified then it does so deterministically, otherwise it uses
	 * UID() to create a random id.  In either case the Id will be recorded for the calling worker,
	 * allowing that worker to reconnect on different Connections.
	 * This presumes that if you have given -id to this JVM, you have given a UNIQUE -id to ALL
	 * subscriber JVMs. 
	 * @param c
	 * @param worker
	 * @param uniqueID If not null, this value is unique within the current JVM
	 * @throws JMSException
	 */
	public void setDurableConnectionId( Connection c, WorkerThread worker, String uniqueID ) throws JMSException {
		
		String durableConnectionId;
		
		if ( worker==null ) {
			durableConnectionId = createDurableConnectionId(uniqueID);
		} else {
		
			if ( durableConnectionIdMap==null ) {
				durableConnectionIdMap = new ConcurrentHashMap<String,String>();
			}
			
			String workername = worker.getName();
			
			// find client id....
			if ( durableConnectionIdMap.containsKey(workername) ) {
				// We have it cached already
				durableConnectionId = (String)durableConnectionIdMap.get(workername);
			} else { 
				// We must build it
				
				durableConnectionId = createDurableConnectionId(uniqueID);
				durableConnectionIdMap.put( workername, durableConnectionId );
			} // end if not in map
		} // end if else worker == null
		
		c.setClientID( durableConnectionId );

	}

	protected String createDurableConnectionId(String uniqueID) {
		String durableConnectionId;
		// Note proc_id is not set unless -id n passed on cmdline
		String proc_id = Config.parms.getString("id");
		if ( uniqueID!=null && proc_id!=null && proc_id.length()>0 ) {
			// This presumes that if you have given -id to this JVM
			// you have given a UNIQUE -id to ALL subscriber JVMs
			durableConnectionId = proc_id+"_"+uniqueID;
		} else {
			// If no -id then get a random ID ( UID() is crap at
			// this, but better than nothing ) 
			durableConnectionId = new UID().toString().replaceAll(":", "");
		}
		return durableConnectionId;
	}
	
	/**
	 * <b>JMS 1.1</b> Get a connection with timeout and retry options enabled.
	 * Creates a connection from the given factory and any username or password
	 * specified in the application configuration. This method also sets the
	 * durable connection id if required. The idenfier given is either the "-id"
	 * passed in or a psuedo-random string created using UID().
	 * 
	 * @param cf
	 *            ConnectionFactory to get connection from.
	 * @param worker
	 *            The WorkerThread this connection is intended for (has an
	 *            impact on clientId and durable subscribers).
	 * @param uniqueID
	 *            If not null, this value is unique within the current JVM
	 * @throws JMSException
	 *             Connection (including all configured attempts to reconnect)
	 *             failed.
	 * @throws InterruptedException
	 *             The method was interrupted while sleeping for a retry
	 *             interval.
	 */
	public Connection getConnection( ConnectionFactory cf, WorkerThread worker, String uniqueID ) throws JMSException, InterruptedException {

		Connection c = null;
		
		long started = System.currentTimeMillis();
		long retry_interval = Config.parms.getLong( "ri" ) * 1000;
		long retry_timeout = Config.parms.getLong( "ro" ) * 1000;
		
		if ( retry_timeout == 0 ) {
			started = 0; // forces no retries
		}
		
		while ( !ControlThread.isShuttingDown() ) {
			try {
				c = getConnection_internal( cf, worker, uniqueID );
				return c;
			} catch (JMSException e) {
				// connection attempt failed.
				Log.logger.log(Level.WARNING, "Connection attempt failed", e);

				if ( System.currentTimeMillis() - started > retry_timeout ) {
					// rethrow exception to higher level handler
					throw e;
				} else {
					Thread.sleep( retry_interval );
				}
			}
			
		} // end while
		
		// we are shutting down but were not interrupted, throw one anyway.
		// NB. we should never reach this piece of code.
		throw new InterruptedException();
		
	}

}
