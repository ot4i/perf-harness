package com.ibm.uk.hursley.perfharness.mqtt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;

public class DefaultMessageFactory implements MessageFactory {
	
	static int messageSize;
	static String messageFile;
	
	private byte[] bytes = null;
	
	private static MessageFactory instance;
	
	public static void registerConfig() {
		
		Config.registerSelf( DefaultMessageFactory.class );
		
		if ( ! Config.isInvalid() ) {
			messageSize = Config.parms.getInt("ms");
			if ( messageSize<0 ) {
				Config.logger.log( Level.WARNING, "Message size (ms={0}) must be at least 0", messageSize);
			}
			
			//if we are using an empty message and then there is no point them setting a message file so throw an error
			//Note: We should also throw an error if the -ms flag is used with an empty message  
			messageFile = Config.parms.getString( "mf" );
			if ( messageFile!=null && !messageFile.equals("") ) {
				
				// Check file existence if specified.
				if ( ! new File( messageFile ).exists() ) {
					Config.logger.log( Level.WARNING, "File {0} does not exist", messageFile );
				}
				
			}
			
		}
		
	}
	
	public byte[] createMessage(String threadname, int seq) throws Exception {
		
		synchronized ( this ) {
			
			if ( bytes==null ) {
				if ( (messageFile == null) || ("".equals( messageFile )) ) {
					// Generate our own random message
			    	bytes = generateRandomBytes( messageSize );
				} else {
					// use contents of file as message
			
					// Read in the application data
					try {
						InputStream instream = new BufferedInputStream( new FileInputStream( messageFile ) );
						int size = instream.available();
						bytes = new byte[size];
						instream.read( bytes );
					} catch( IOException ioe ) {
						Log.logger.log( Level.SEVERE, "Cannot read file {0}",messageFile );
						throw ioe;
					}
				} // end ifelse		
			} // end if
			
		} // end sync
		
		return bytes;
	}
	
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
