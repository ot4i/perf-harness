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

import javax.jms.JMSException;
import javax.jms.Message;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.sequencing.BasicSequence;
import com.ibm.uk.hursley.perfharness.sequencing.Sequence;
import com.ibm.uk.hursley.perfharness.sequencing.SequentialWorker;
import com.ibm.uk.hursley.perfharness.util.ByteArray;
import com.ibm.uk.hursley.perfharness.util.FastByteArrayOutputStream;

public class SeqSubscriber extends Subscriber implements SequentialWorker {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	Sequence seq = new BasicSequence();

	private static Checksum checksumImpl;
	
	private FastByteArrayOutputStream fbaos = null;
	
	public static void registerConfig() {
		Config.registerSelf( SeqSubscriber.class );
		Config.parms.put( "sc", "com.ibm.uk.hursley.perfharness.sequencing.SequenceStats" );
		
		String ck = Config.parms.getString( "ck" ).toLowerCase();
		if ( "crc".compareTo( ck )==0 ) {
			checksumImpl = new CRC32();
		} else if ( "adler".compareTo( ck ) ==0 ) {
			checksumImpl = new Adler32();
		} else {
			checksumImpl = null;
		}
	}	

	public SeqSubscriber(String name) {
		
		super(name);
		if ( checksumImpl!=null ) {
			fbaos = new FastByteArrayOutputStream( 2048 );
		}
		
	}

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		if( (inMessage=messageConsumer.receive( timeout ))!=null ) {
			if ( transacted ) {
				if ( (getIterations()+1)%commitCount==0 ) {
					session.commit();			
				}
			}
				incIterations();
			int seqnum = inMessage.getIntProperty("seq");
			seq.registerElement(seqnum);
			
			if ( checksumImpl!=null ) {
				validate( inMessage );
			}
			
		}

		return true;
	}

	/* (non-Javadoc)
	 * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
	 */
	public void onMessage(Message arg0) {
		if ( transacted ) {
			if ((getIterations()+1)%commitCount==0 ) {
				try {
					session.commit();
					//iterations+=commitCount;				
				} catch (JMSException je) {
					handleException(je);
				}
			}
		}
		incIterations();
		
		try {
			int seqnum=0;			
			seqnum = arg0.getIntProperty("seq");
			seq.registerElement(seqnum);
			if ( checksumImpl!=null ) {
				validate( arg0 );
			}			
		} catch (JMSException e) {
		    handleException( e );
		} catch (Exception e) {
		    handleException( e );
		}
		


	}

	public Sequence getSequence() {
		return seq;
	}	
	
	private void validate( Message msg ) throws Exception {
		checksumImpl.reset();
		ByteArray b = msgFactory.getBytes( msg, fbaos );
		checksumImpl.update( b.buf, 0, b.length );
		long received = msg.getLongProperty("ck");
		if ( received != checksumImpl.getValue() ) {
			seq.incrementErrors();
		}
	}	
	
}
