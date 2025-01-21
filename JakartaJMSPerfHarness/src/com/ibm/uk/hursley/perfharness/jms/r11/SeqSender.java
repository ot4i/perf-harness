/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.jms.r11;

import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.util.ByteArray;
import com.ibm.uk.hursley.perfharness.util.FastByteArrayOutputStream;

/**
 * Send messages to a Queue.
 */
public final class SeqSender extends Sender {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	int seq = 0;
	
	static boolean make_checksum = true;
	private static Checksum checksumImpl;
	
	private FastByteArrayOutputStream fbaos = null;
	
	public static void registerConfig() {
		Config.registerSelf( SeqSender.class );
		String ck = Config.parms.getString( "ck" ).toLowerCase();
		if ( "crc".compareTo( ck )==0 ) {
			checksumImpl = new CRC32();
		} else if ( "adler".compareTo( ck ) ==0 ) {
			checksumImpl = new Adler32();
		} else {
			checksumImpl = null;
			make_checksum = false;
		}
	}	   
    
    public SeqSender(String name) {
    	
        super(name);
        if ( checksumImpl!=null ) {
			fbaos = new FastByteArrayOutputStream( 2048 );
		}
        
    }

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public final boolean oneIteration() throws Exception {
		
		if ( make_checksum ) {
			// Hardly the best place to place this function, but this class
			// is no longer focussed on raw performance, so we will keep the source
			// code shorter.
			make_checksum = false;
			ByteArray b = msgFactory.getBytes( outMessage, fbaos );
			checksumImpl.update( b.buf, 0, b.length );
			outMessage.setLongProperty( "ck", checksumImpl.getValue() );
		}
		
		outMessage.setIntProperty("seq",seq);
		messageProducer.send( outMessage, deliveryMode, priority, expiry );				
		if ( transacted && (getIterations()+1)%commitCount==0 ) session.commit();
		seq++;
		incIterations();
		
		return true;
	}
}
