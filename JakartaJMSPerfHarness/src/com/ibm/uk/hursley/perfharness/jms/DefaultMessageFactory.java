/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

package com.ibm.uk.hursley.perfharness.jms;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.server.UID;
import java.util.*;
import java.util.logging.Level;

import jakarta.jms.*;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.util.ByteArray;
import com.ibm.uk.hursley.perfharness.util.FastByteArrayOutputStream;

/**
 * Creates JMS messages based on user configuration.
 * 
 */
public class DefaultMessageFactory implements MessageFactory {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT;
	
	private static final String BYTES =  "bytes";
	private static final String EMPTY =  "empty";
	private static final String MAP =    "map";
	private static final String OBJECT = "object";
	private static final String STREAM = "stream";
	private static final String TEXT =   "text"; // default codepage
	private static final String EBCDIC =   "ebcdic";

	protected static boolean useNativeCorrelationId;
	
	protected static boolean sameMessage;
	
	private static MessageFactory instance;	
	
	private Message outMessage;
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		// static validation of parameters

		Config.registerSelf( DefaultMessageFactory.class );
		
		if ( ! Config.isInvalid() ) {
			
			int msgSize = Config.parms.getInt("ms");
			if ( msgSize<0 ) {
				Config.logger.log( Level.WARNING, "Message size (ms={0}) must be at least 0", msgSize);
			}
			String mt = Config.parms.getString("mt");
			if ( !mt.equals(BYTES)&!mt.equals(MAP)&!mt.equals(STREAM)&!mt.equals(OBJECT)&!mt.equals(TEXT)&!mt.equals(EBCDIC)&!mt.equals(EMPTY) ) {
				Config.logger.log(Level.WARNING,
						"Message type (mt={0}) must be one of [" + BYTES + ","
								+ TEXT + "," + EBCDIC + "," + MAP + "," + STREAM + ","
								+ OBJECT + "," + EMPTY + "]", mt);
			}
			//if we are using an empty message and then there is no point them setting a message file so throw an error
			//Note: We should also throw an error if the -ms flag is used with an empty message  
			String messageFile = Config.parms.getString( "mf" );
			if ( messageFile!=null && !messageFile.equals("") ) {
				
				if ( mt.equals(EMPTY) ) {
					Config.logger.warning("Cannot specify -mf when using an empty message type.");
				}
				// Check file existence if specified.
				if ( ! new File( messageFile ).exists() ) {
					Config.logger.log( Level.WARNING, "File {0} does not exist", messageFile );
				}
				
			}
			
			useNativeCorrelationId = Config.parms.getBoolean( "cp" );
				
			String propertiesFile = Config.parms.getString( "pf" );
			if ( propertiesFile!=null && !propertiesFile.equals("") ) {
				
				// Check file existence if specified.
				if ( ! new File( propertiesFile ).exists() ) {
					Config.logger.log( Level.WARNING, "File {0} does not exist", propertiesFile);
				}
				
			}
		}
	}
	
	private byte[] bytes = null;
	private boolean readFile = false;
	
	
	/**
	 * @see com.ibm.uk.hursley.perfharness.jms.MessageFactory#getMessage(javax.jms.Session, java.lang.String, int, com.ibm.uk.hursley.perfharness.WorkerThread)
	 * @deprecated This method is not correct and should not be used.
	 */
	public Message getMessage(Session session, String threadname, int seq, WorkerThread worker) throws Exception {
		
		if (sameMessage) {
			// FIXME: each WorkerThread MUST have its own message instance as it cannot be used by >1 thread at once without conflict.
			if (outMessage == null) {
				// Create generic message 
				// Use ThreadLocal ? have a MessageFactory instance per worker? 
				outMessage = createMessage(session, null, seq);
			}
			return outMessage;
		} else {
			return createMessage(session, threadname, seq); 
		}
		
	}
	
	/**
	 * Returns a JMS message based upon the given configuration.  If no content is specified 
	 * (via <code>-mf</code>) then we generate our own garbage as filler.  <b>This method has
	 * terrible performance and is not intended for use in the core loop.</b>
	 * @param session The session which will be used to create the Message objects. 
	 * @param threadname Calling thread name.  <b>Now unused.</b> 
	 * content.  This is only used when creating our own content.
	 * @return A JMS Message object ready to send.
	 */
	public Message createMessage(Session session, String threadname, int seq) throws Exception {
	
		Message m = null;   	
		String messageFile = Config.parms.getString( "mf" );
		
		synchronized ( this ) {
			
			if ( bytes==null ) {
				if ( "".equals( messageFile ) ) {
					// Generate our own random message
			    	bytes = generateRandomBytes( Config.parms.getInt( "ms" ) );
				} else {		
					// Read in the application data
					try {
						final InputStream instream = new BufferedInputStream( new FileInputStream( messageFile ) );
						int size = instream.available();
						bytes = new byte[size];
						instream.read( bytes );
						readFile = true;
						instream.close();
					} catch( IOException ioe ) {
						Log.logger.log( Level.SEVERE, "Cannot read file {0}",messageFile );
						throw ioe;
					}
				} // end ifelse		
			} // end if
			
		} // end sync
		
		String mtype = Config.parms.getString( "mt" );
		
		if ( mtype.equals(TEXT) ) {
			// A little bit of Java 1.4 NIO...
			try {
				String s = new String(bytes, 0, bytes.length, "UTF-8");
				m = session.createTextMessage(s);
			} catch ( Exception e ) {
				Log.logger.severe( "Cannot create TextMessage on this JVM" );
				throw e;
			}
			
		} else if ( mtype.equals(BYTES) ) {
			
			m = session.createBytesMessage();
			((BytesMessage)m).writeBytes(bytes);
			
		} else if ( mtype.equals(OBJECT) ) {
			
			Serializable o = bytes;
			if( readFile ) { 
				// Try deserialising these bytes in case they are a specific Java object.
				// We have already read and closed the inputstream so we need create a new one
				try {
					ObjectInputStream ois = new ObjectInputStream( new ByteArrayInputStream( bytes ) );
					o = (Serializable) ois.readObject();
					ois.close();
				} catch ( Exception e ) {
					Log.logger
							.log(
									Level.WARNING,
									"Could not deserialise {0}, using it as a byte array.",
									messageFile);
				}
			}
			m = session.createObjectMessage(o);
			
		} else if ( mtype.equals(MAP) ) {
			m = session.createMapMessage();
			((MapMessage)m).setBytes( "name", bytes );
			
		} else if ( mtype.equals(STREAM) ) {
			m = session.createStreamMessage();
			((StreamMessage)m).writeBytes( bytes );
			
		} else if ( mtype.equals(EMPTY) ) {
			m = session.createMessage();	
			
		} else {
			throw new Exception( "messagetype=["+mtype+"] unknown" );
		}
		
		String propertiesFile = Config.parms.getString( "pf" );
		if ( propertiesFile!=null && !propertiesFile.equals("") ) {
			// use contents of file as JMS properties
			
			final Properties props = new Properties();
			props.load( new BufferedInputStream( new FileInputStream( propertiesFile ) ) );
			
			for (Iterator<String> iter = props.stringPropertyNames().iterator(); iter.hasNext();) {
				String key = iter.next();
				if (!key.endsWith(".type")) {
					String keyType = props.getProperty(key + ".type");
					String value = props.getProperty(key);
					if (keyType != null) {
						try {
							if ( keyType.equalsIgnoreCase( "int" ) ) {
								m.setIntProperty( key, Integer.parseInt(value) );
							} else if ( keyType.equalsIgnoreCase( "boolean" ) ) {
								m.setBooleanProperty( key, Boolean.parseBoolean(value) );
							} else if ( keyType.equalsIgnoreCase( "short" ) ) {
								m.setShortProperty( key, Short.parseShort(value) );
							} else if ( keyType.equalsIgnoreCase( "byte" ) ) {
								m.setByteProperty( key, Byte.parseByte(value) );
							} else if ( keyType.equalsIgnoreCase( "float" ) ) {
								m.setFloatProperty( key, Float.parseFloat(value) );
							} else if ( keyType.equalsIgnoreCase( "double" ) ) {
								m.setDoubleProperty( key, Double.parseDouble(value) );
							} else if ( keyType.equalsIgnoreCase( "long" ) ) {
								m.setLongProperty( key, Long.parseLong(value) );
							} else if ( keyType.equalsIgnoreCase( "string" ) ) {
								m.setStringProperty( key, value );
							} else {
								Log.logger
										.log(
												Level.WARNING,
												"JMS message property [{0}] has unknown type [{1}] - adding as a string.",
												new Object[]{key, keyType});
								m.setStringProperty( key, value );
							}
						} catch ( Exception e ) {
							Log.logger
									.log(
											Level.SEVERE,
											"JMS message property [{0}] is not of type [{1}] - skipping.",
											new Object[]{key, keyType});
						}
						
					} else { 
						m.setStringProperty( key, value );
					} // end if keyType
					
				} // end if endswith
				
			} // end for all props
			
		} // end if propsfile
	
	    return m;
	    
	} // end public static Message createMessage

	/**
	 * JMS 2.0 Compatibility
	 * Returns a JMS message based upon the given configuration.  If no content is specified 
	 * (via <code>-mf</code>) then we generate our own garbage as filler.  <b>This method has
	 * terrible performance and is not intended for use in the core loop.</b>
	 * 
	 * @param context The context which will be used to create the Message objects. 
	 * @return A JMS Message object ready to send.
	 */
	public Message createMessage(JMSContext context) throws Exception {
		Message m = null;   	
		String messageFile = Config.parms.getString( "mf" );
		
		synchronized(this) {
			if (bytes == null) {
				if ("".equals(messageFile)) {
					// Generate our own random message
			    	bytes = generateRandomBytes(Config.parms.getInt("ms"));
				} else {
					// use contents of file as message
					// Read in the application data
					try {
						final InputStream instream = new BufferedInputStream(new FileInputStream(messageFile));
						int size = instream.available();
						bytes = new byte[size];
						instream.read(bytes);
						readFile = true;
						instream.close();
					} catch(IOException ioe) {
						Log.logger.log( Level.SEVERE, "Cannot read file: {0}", messageFile);
						throw ioe;
					}
				} // end ifelse		
			} // end if
		} // end sync
		
		String mtype = Config.parms.getString("mt");
		if (mtype.equals(TEXT)) {
			try {
				// Original CharSet Decoder threw errors with large messages
				String s = new String(bytes, 0, bytes.length, "UTF-8");
				m = context.createTextMessage(s);
			} catch (Exception e) {
				Log.logger.severe("Cannot create TextMessage on this JVM");
				throw e;
			}
		} else if (mtype.equals(BYTES)) {
			m = context.createBytesMessage();
			((BytesMessage)m).writeBytes(bytes);
		} else if (mtype.equals(OBJECT)) {
			Serializable o = bytes;
			if(readFile) { 
				// Try deserialising these bytes in case they are a specific Java object.
				// We have already read and closed the inputstream so we need create a new one
				try {
					ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
					o = (Serializable) ois.readObject();
					ois.close();
				} catch (Exception e) {
					Log.logger.log(Level.WARNING, "Could not deserialise {0}, using it as a byte array.", messageFile);
				}
			}
			m = context.createObjectMessage(o);
		} else if (mtype.equals(MAP)) {
			m = context.createMapMessage();
			((MapMessage)m).setBytes( "name", bytes );
		} else if (mtype.equals(STREAM)) {
			m = context.createStreamMessage();
			((StreamMessage)m).writeBytes( bytes );
		} else if (mtype.equals(EMPTY)) {
			m = context.createMessage();	
		} else {
			throw new Exception("messagetype=["+mtype+"] unknown");
		}
		
		String propertiesFile = Config.parms.getString( "pf" );
		if ((propertiesFile != null) && (!propertiesFile.equals(""))) {
			// use contents of file as JMS properties
			Properties props = new Properties();
			props.load(new BufferedInputStream(new FileInputStream(propertiesFile)));
			for (Iterator<String> iter = props.stringPropertyNames().iterator(); iter.hasNext();) {
				String key = iter.next();
				if (!key.endsWith(".type")) {
					String keyType = props.getProperty(key + ".type");
					String value = props.getProperty(key);
					if (keyType != null) {
						try {
							if (keyType.equalsIgnoreCase("int")) {
								m.setIntProperty( key, Integer.parseInt(value) );
							} else if ( keyType.equalsIgnoreCase("boolean") ) {
								m.setBooleanProperty( key, Boolean.parseBoolean(value) );
							} else if ( keyType.equalsIgnoreCase("short") ) {
								m.setShortProperty( key, Short.parseShort(value) );
							} else if ( keyType.equalsIgnoreCase("byte") ) {
								m.setByteProperty( key, Byte.parseByte(value) );
							} else if ( keyType.equalsIgnoreCase("float") ) {
								m.setFloatProperty( key, Float.parseFloat(value) );
							} else if ( keyType.equalsIgnoreCase("double") ) {
								m.setDoubleProperty( key, Double.parseDouble(value) );
							} else if ( keyType.equalsIgnoreCase("long") ) {
								m.setLongProperty( key, Long.parseLong(value) );
							} else if ( keyType.equalsIgnoreCase("string") ) {
								m.setStringProperty( key, value );
							} else {
								Log.logger.log(Level.WARNING, "JMS message property [{0}] has unknown type [{1}] - adding as a string.", new Object[]{key, keyType});
								m.setStringProperty( key, value );
							}
						} catch (Exception e) {
							Log.logger.log(Level.SEVERE, "JMS message property [{0}] is not of type [{1}] - skipping.",	new Object[]{key, keyType});
						}
					} else { 
						m.setStringProperty( key, value );
					} // end if keyType
				} // end if endswith
			} // end for all props
		} // end if propsfile
	
	    return m;
	} // end public static Message createMessage	
	
	/**
	 * Create a byte array suitable for using in Message objects such as
	 * StringMessage.
	 * 
	 * @return Some random bytes.
	 */
	protected byte[] generateRandomBytes(int length) {
		
		byte[] data = new byte[ length ];
		
		while( length--!=0 ) {
			data[length] = (byte) (Math.random() * (122-65) + 65);
		} // end while
	
	    return data;
	    
	}

	/**
	 * Sets a generated CorrelationId.  The id is based upon hostname and
	 * the UID class.  The ID is set as bytes if supported or as a string otherwise.
	 * Currently this is
	 * unique per worker, but is undefined as to wether it returns the same cid from multiple
	 * calls.
	 * <p><b>NB.</b> This method call is lazily coded and is only
	 * meant to be used once in setup, not on a per-message basis.
	 * @see #makeCorrelIdAsBytes(WorkerThread)
	 * @see #makeCorrelIdAsString(WorkerThread)
	 * @param worker The worker to associate the correlationId with.  
	 * @param msg The JMS Message to work with.
	 * @return The result of msg.getJMSCorrelationID() after setting the id.
	 * @throws JMSException
	 */
	public String setJMSCorrelationID(WorkerThread worker, Message msg ) throws JMSException {
		
		String ret = null;
		try {
			if ( useNativeCorrelationId ) {
				try {
					// may throw UnsupportedOperationException
					msg.setJMSCorrelationIDAsBytes( makeCorrelIdAsBytes( worker ) );
					ret = msg.getJMSCorrelationID();
				} catch ( UnsupportedOperationException e ) {
					// Fallback to other method
					useNativeCorrelationId = false;
					Log.logger.log(Level.WARNING, "Using generic correlation ID" );
					msg.setJMSCorrelationID( makeCorrelIdAsString( worker ) );
					ret = msg.getJMSCorrelationID();
				}
			} else {
				msg.setJMSCorrelationID( makeCorrelIdAsString( worker ) );
				ret = msg.getJMSCorrelationID();
			}
		} catch ( UnknownHostException uhe ) {
			Log.logger.log(Level.SEVERE, "Cannot create JMSCorrelationID as we cannot determine our hostname "+uhe );
			// return null;
		}
        Log.logger.log(Level.FINEST, "setJMSCorrelationID: {0}", ret);
		return ret;
	}

	/**
	 * Attempts to use as much system info as possible to create a unique 24 byte identifier for the
	 * given WorkerThread.  In default mode it uses the UID class (not the best option!) and it is therefore
	 * advisable to set <code>-id</code> in which case the tool assumes that this id is globally unique within
	 * the test environment and can safely be used to differentiate JVMs.
	 * Currently this is
	 * unique per worker, but is undefined as to wether it returns the same cid from multiple
	 * calls.
	 * @param worker The subject for the correlation id.
	 * @return A 24-byte array
	 * @throws UnknownHostException The local hostname is being used to identify this JVM but could not be looked up
	 * (try using <code>-id</code> to avoid the requirement on hostname.
	 */
	protected byte[] makeCorrelIdAsBytes(WorkerThread worker) throws UnknownHostException {
	
		String proc_id = Config.parms.getString("id");
		byte[] bytes = new byte[24];
		
		if (proc_id != null && proc_id.length() > 0) {
			// If we have set an ID then use that 
			byte[] b = proc_id.getBytes();
			System.arraycopy( b, 0, bytes, 0, b.length>24?24:b.length );
		} else {
			// we have not set an ID, so attempt to create a random one.
			byte[] addr = InetAddress.getLocalHost().getAddress();
			String uid = new UID().toString();
			if (uid.length() > (22 - addr.length)) {
				uid = uid.substring(0, 22 - addr.length);
			}
		
			System.arraycopy(addr, 0, bytes, 0, addr.length);
			System.arraycopy(uid.toString().getBytes(), 0, bytes, addr.length, uid.length());
		}
		
		// Add on the the thread id
		int threadInt = worker.getThreadNum();
		byte[] threadBytes = String.valueOf(threadInt).getBytes();
		System.arraycopy(threadBytes, 0, bytes, (24 - threadBytes.length), threadBytes.length);

		return bytes;
	}

	/**
	 * Returns a correlation id constructed from a UID and the name of the targeted workerthread. 
	 * @param worker
	 * @throws UnknownHostException The local hostname is being used to identify this JVM but could not be looked up
	 * (try using <code>-id</code> to avoid the requirement on hostname.
	 */
	protected String makeCorrelIdAsString(WorkerThread worker) throws UnknownHostException {
		String proc_id = Config.parms.getString("id");
		StringBuffer cid = new StringBuffer();
		if (proc_id != null && proc_id.length() > 0) {
			cid.append(proc_id);
		} else {
			cid.append( InetAddress.getLocalHost().toString() );	
		}
		
		cid.append(worker.getName());
		cid.append( new UID().toString() );
        Log.logger.log(Level.FINEST, "makeCorrelIDAsString: {0}", cid);
		return cid.toString();
	}

	/**
	 * A helper method for data validation.  This attempts to return a byte array for the given
	 * Message type.  Currently only TextMessage and BytesMessage are supported.  
	 * @param msg
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	public ByteArray getBytes(Message msg, FastByteArrayOutputStream fbaos) throws Exception {
		
		fbaos.reset();
		ByteArray b = fbaos.getFastByteArray();
		try {
		if (msg instanceof BytesMessage) {

				BytesMessage bmsg = (BytesMessage) msg;
				int len = (int) bmsg.getBodyLength();
				b.ensureCapacity( len );
				bmsg.readBytes(b.buf);
				b.length = len;

			} else if (msg instanceof TextMessage) {

				TextMessage tmsg = (TextMessage) msg;
				b = new ByteArray( tmsg.getText().getBytes() );

			} else if (msg instanceof ObjectMessage) {

				ObjectMessage omsg = (ObjectMessage) msg;

				ObjectOutputStream oos = new ObjectOutputStream(fbaos);
				oos.writeObject(omsg.getObject());
				oos.close();

			} else if (msg instanceof StreamMessage) {

				StreamMessage smsg = (StreamMessage) msg;
				smsg.reset();
				
				try {
					while (true) {
						String s = smsg.readString(); // All except byte[] can
														// be read as a String
						fbaos.write(s.getBytes());
					}
				} catch (MessageEOFException e) {
					// Expected
				}

			} else if (msg instanceof MapMessage) {

				MapMessage mmsg = (MapMessage) msg;
				
				List<String> keys = Collections.list( mmsg.getMapNames() );
				Collections.sort( keys );
				for (String key : keys) {
					String s = mmsg.getString(key); // All except byte[] can be
													// read as a String
					fbaos.write(s.getBytes());
				}

			} else {
				Log.logger.log(Level.SEVERE, "Cannot get CRC bytes from "
						+ msg.getClass().getSimpleName() + ".");
			}
			return b;
			
		} catch (MessageFormatException e) {
			throw new Exception( "Cannot generate CRC for a non BytesMessage that has a byte[] in it.", e );
		}
		
	}	

	/**
	 * Trivial method to return a new MessageFactory, this can be used to 
	 * @return
	 */
	/**
	 * Simple singleton pattern to return the JMSProvider in use.  Hence, this is limited to a single provider. 
	 * @return A subclass of JMSProvider defined by the <code>-pc</code> parameter.
	 */
	public synchronized static MessageFactory getInstance() {
		if ( instance == null ) {
			try {
				instance = new DefaultMessageFactory(); // Not yet parameterised
			} catch ( Exception e ) {
				Log.logger.log( Level.SEVERE, "Problem getting MessageFactory class", e );
			}
		}
		return instance;
	}

}
