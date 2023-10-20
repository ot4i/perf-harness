package com.ibm.uk.hursley.perfharness.jms.providers;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.NamingException;
import org.apache.qpid.jms.JmsConnectionFactory;

import com.ibm.uk.hursley.perfharness.Config;

/**
 * Settings for connection to AMQP service using Qpid JMS client.
 * These could all be set through JMSAdmin allowing use of the JNDI module.
 *  
 */

public class QPID  extends JNDI implements JMSProvider {

	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		Config.registerSelf(QPID.class);
		//Add the non-standard acknowledge mode
		addAcknowlegeMode(100,"NO_ACKNOWLEDGE");
	}
	
	/**
	 * Create a new vendor-specific ConnectionFactory (or delegate to JNDI if that is has been selected).
	 */	
	protected String configureAMQPUri() {	
	//Get ampq specific properties
	long qpit = Config.parms.getInt("qpit");
	long idleTimeout = (qpit == 0) ?-1:qpit;
	long  jmsConnectTimeout = Config.parms.getInt("qpjct");
	long  drainTimeout = Config.parms.getInt("qpdt");
	boolean sync = Config.parms.getBoolean("qpsy");
	String tlsCipherSuite = Config.parms.getString( "jl" );

	String tlsOpt="";
	String syncOpt = "";
	String jmsConnectTimeoutOpt="";
	String drainTimeoutOpt="";

	String protocol="amqp";

	
	if(!tlsCipherSuite.equals("")){
		protocol="amqps";
	    tlsOpt = "&transport.enabledCipherSuites="+tlsCipherSuite+"&transport.verifyHost="+Config.parms.getBoolean("jvh");
	} 
	
	if(sync){
		syncOpt = "&jms.forceSyncSend=true";
	} 
	
	if(jmsConnectTimeout > 0){
		jmsConnectTimeoutOpt="&jms.connectTimeout="+jmsConnectTimeout;
	}

	if(drainTimeout > 0){
		drainTimeoutOpt="&amqp.drainTimeout="+drainTimeout;
	}
	
	String amqpUri = protocol
					 +"://" + Config.parms.getString("jh") + ":" 
	                 + Config.parms.getInt("jp") 
	                 +  "?"
	                 + "transport.connectTimeout="+Config.parms.getInt("qptct")
	                 + "&"
	                 + "amqp.idleTimeout="+idleTimeout
	                 + jmsConnectTimeoutOpt
	                 + drainTimeoutOpt
	                 + tlsOpt
	                 + syncOpt;

	
    return amqpUri;
	}
	   
	/**
	 * Create a new vendor-specific ConnectionFactory (or delegate to JNDI if that is has been selected).
	 */	
	public synchronized ConnectionFactory lookupConnectionFactory(String name) throws JMSException, NamingException {
		if ( usingJNDI ) {
			return super.lookupConnectionFactory(name);
		} else {
			String amqpuri = configureAMQPUri();
			System.out.println("amqpuri is " + amqpuri); 
			final ConnectionFactory cf = new JmsConnectionFactory(amqpuri);
			return cf;
		} // end if cf==null

	}
	
}
