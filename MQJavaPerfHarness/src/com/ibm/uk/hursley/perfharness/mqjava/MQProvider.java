/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

package com.ibm.uk.hursley.perfharness.mqjava;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.ibm.mq.*;
import com.ibm.mq.constants.CMQC;
import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.stats.ResponseTimeStats;

/**
 * Provides access to WebSphere MQ classes for Java.
 */
public class MQProvider {
	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

	private static String messagesList[] = null;

	// Running count of iterations executed.
	private final AtomicInteger msgFileToUse = new AtomicInteger(0);
	
	private static MQProvider instance = null;	

	public MQProvider() {
		// TODO Auto-generated constructor stub
	}

	public static void registerConfig() {
		Config.registerSelf(MQProvider.class);
		checkMQJMSTraceProperty();
	} // end static initialiser	

	public synchronized static MQProvider getInstance() {
		if (instance == null)
			instance = new MQProvider();
		return instance;
	}

	private static void checkMQJMSTraceProperty() {
		String traceProperty = System.getProperty("MQJMS_TRACE_LEVEL");
		if (traceProperty != null) {
			traceProperty = traceProperty.trim();

			// Convert the MQJMS property into an MQJava tracing level [1..5]	        
			int traceLevel = -1;
			try { 
				if (traceProperty.equalsIgnoreCase("on"))
					traceLevel = 2;
				else
				if (traceProperty.equalsIgnoreCase("base"))
					traceLevel = 4;
				else
					traceLevel = Integer.parseInt(traceProperty);
			}
			catch (Exception e) {
				// No op
			}
			finally {
				if (traceLevel == -1) {
					Config.logger.warning("MQJMS_TRACE_LEVEL must be \"on\" (2), \"base\" (4) or an integer from 1 to 5.");
					return;
				}
			} // end finally

			// Get the trace filename
			String traceDir = System.getProperty("MQJMS_TRACE_DIR");
			if (traceDir==null)
				traceDir = ".";

			final String traceFile = new File(traceDir, "mqjava.trc").getAbsolutePath();
			Log.logger.log(Level.INFO, "Detected MQJMS_TRACE_LEVEL. Tracing at level " + traceLevel + " to " + traceFile);

			// Start the trace
			try { 
				MQEnvironment.enableTracing(traceLevel, new FileOutputStream(traceFile));
			}
			catch (IOException e) {
				// couldn't open the file,
				// trace to System.err instead
				MQEnvironment.enableTracing(traceLevel);
			}
		} // end if level!=null
	}

	public MQQueueManager getQueueManager() throws MQException {
		int connectionOptions = CMQC.MQCNO_NONE;

		final String bindings = Config.parms.getString("jt");
		if (bindings.equalsIgnoreCase("mqb"))
			connectionOptions = CMQC.MQCNO_STANDARD_BINDING;
		else
		if (bindings.equalsIgnoreCase("mqbf"))
			connectionOptions = CMQC.MQCNO_FASTPATH_BINDING;
		else
		if (bindings.equalsIgnoreCase("mqc")) {
			// client connections
			final int jp = Config.parms.getInt("jp");
			final String jc = Config.parms.getString("jc");
			final String jh = Config.parms.getString("jh");
			final String jl = Config.parms.getString("jl");
			MQEnvironment.port = jp;
			if (null != jc && !jc.equals(""))
				MQEnvironment.channel = jc;
			if (null != jh && !jh.equals(""))
				MQEnvironment.hostname = jh;
			// This check is required for MQ 7.1 and above. By setting "" it causes an MQRC 2400 error
			// This was not the case in previous versions of MQ.
			if (null != jl && !jl.equals(""))
				MQEnvironment.sslCipherSuite = jl;
		} else {
			Config.logger.warning("-jt must be one of mqc, mqb, mqbf");
			// TODO move this detection to static initialiser
		}

		return new MQQueueManager(Config.parms.getString("jb"), connectionOptions);
	}
	public MQGetMessageOptions getGMO() {
		/* TODO: ADD OPTIONS:
		 * MQC.MQGMO_ACCEPT_TRUNCATED_MSG;
		 * Also should only read tx and cm options once not both here and PMO methods
		 * */

		MQGetMessageOptions gmo = new MQGetMessageOptions();

		gmo.options = CMQC.MQGMO_WAIT | CMQC.MQGMO_FAIL_IF_QUIESCING;

		final int timeout = (int)(Config.parms.getDouble("to") * 1000);
		if (timeout == 0)
			gmo.waitInterval = CMQC.MQWI_UNLIMITED;
		else
			gmo.waitInterval = timeout;

		final boolean correlateMsg = Config.parms.getBoolean("co");
		final boolean getByMsgId = Config.parms.getBoolean("mi"); 
		
		// Only accept either -co (get by CorrelId) or -mi (get by MsgId), but not both
		if (correlateMsg && getByMsgId) {
			Config.logger.severe("Specify either -co (get by CorrelId) or -mi (get by MsgId), but not both.");
			Config.exit();
		}

		final boolean transactionResponseStats = Config.isRegistered(ResponseTimeStats.class);
		final int numberOfThreads = Config.parms.getInt("nt");
		
		if (transactionResponseStats && (!getByMsgId && !correlateMsg) && numberOfThreads > 1) {
			Config.logger.severe("If specifying the ResponseTimeStats module to report transaction response times, and running with more than 1 thread (-nt), -mi (get by Msg ID) or -co (get by CorrelID) must be specicified.");
			Config.exit();
		}

		
		final int msgsToSendBeforeGetResp = Config.parms.getInt("ir"); //input to out put ratio, def =1  e.g. 3 means send 3 then get 1.
		if (msgsToSendBeforeGetResp > 1 && getByMsgId) {
			Config.logger.severe("-mi (get by MsgId) is not supported with the input ratio (-ir) option.");
			Config.exit();
		}

		if (correlateMsg)
			gmo.matchOptions = CMQC.MQMO_MATCH_CORREL_ID;
		else
		if (getByMsgId)
			gmo.matchOptions = CMQC.MQMO_MATCH_MSG_ID;
		else
			gmo.matchOptions = CMQC.MQMO_NONE;

		final boolean transacted = Config.parms.getBoolean("tx");
		gmo.options |= transacted ? CMQC.MQGMO_SYNCPOINT : CMQC.MQGMO_NO_SYNCPOINT;

		return gmo;
	}

	public MQPutMessageOptions getPMO() {
		MQPutMessageOptions pmo = new MQPutMessageOptions();
		pmo.options = CMQC.MQPMO_FAIL_IF_QUIESCING | CMQC.MQPMO_NEW_MSG_ID;

		final boolean correlateMsg = Config.parms.getBoolean("co");
		if (correlateMsg)
			pmo.options |= CMQC.MQPMO_NEW_CORREL_ID;

		final boolean transacted = Config.parms.getBoolean("tx");
		pmo.options |= transacted ? CMQC.MQPMO_SYNCPOINT : CMQC.MQPMO_NO_SYNCPOINT;
		return pmo;
	}

	public final int getMsgFileToUse() {
		final int val = msgFileToUse.get();
		if (val < messagesList.length) {
			msgFileToUse.set( val+1 );
			return val;
		}
		msgFileToUse.set(0 + 1);
		return 0;
	}

	private String getMessageFileName() {
		String messageFile = Config.parms.getString( "mf" );
		if (messageFile != null) {
			if (messagesList == null) {//Inialise the messagesList for all threads.
				// Here is the core - replace the regex with
				//whatever delimiter you have. Since '|' is
				//a special character in regular expression,
				//we need to escape it.
				messagesList = messageFile.split("\\|");

				final int numTokens = messagesList.length;
				System.out.println("Number of tokens: " + numTokens);

			}
			messageFile = messagesList[getMsgFileToUse()];
		}
		return messageFile;
	}

	public MQMessage createMessage(String lead) throws Exception {
		MQMessage m = null;
		final String messageFile = getMessageFileName();
		System.out.println("Sending file " + messageFile);

		final String messageFormat = Config.parms.getString("mq", CMQC.MQFMT_STRING);
		final int messageCharacterSet = Config.parms.getInt("mc",1208);
		final int messageEncoding = Config.parms.getInt("me",546);

		String messageString = null;
		byte[] bytesRead = null;

		if (messageFile != null && !messageFile.equals("")) {
			// use contents of file as message

			// Read in the application data
			try {
				final InputStream instream = new BufferedInputStream( new FileInputStream( messageFile ) );
				try {
					int size = instream.available();
					bytesRead = new byte[size];
					instream.read(bytesRead); 
					messageString = new String( bytesRead );
				}
				finally {
					instream.close();
				}
			}
			catch (IOException ioe) {
				Log.logger.log(Level.SEVERE, "Cannot read file " + messageFile, ioe);
				System.exit(1);
			}
		} else {
			// Generate our own random message
			StringBuffer sb = new StringBuffer(makeBigString(Config.parms.getInt("ms")));
			if ((lead != null) && (sb.length() >= lead.length())) {
				sb.replace(0,lead.length(),lead);
			}
			messageString = sb.toString();
		}
		m =  new MQMessage();

		final int RFHFormat = Config.parms.getInt("rf");
		if (RFHFormat == 1) {
			m.characterSet = messageCharacterSet;
			m.encoding = messageEncoding;
			m.format = CMQC.MQFMT_RF_HEADER_1;
			m.write(bytesRead,0,bytesRead.length);	
		} else
		if (RFHFormat == 2) {
			m.characterSet = messageCharacterSet;
			m.encoding = messageEncoding;
			m.format = CMQC.MQFMT_RF_HEADER_2;
			m.write(bytesRead, 0, bytesRead.length);	
		} else
		if (RFHFormat == 3) {
			// For the case where we don't want an RFH which is why we allow 0. But we want to write the data to the message
			// as bytes not using the default writeString method which we would use otherwise. 
			m.characterSet = messageCharacterSet;
			m.encoding = messageEncoding;
			m.format = messageFormat;
			m.write(bytesRead, 0, bytesRead.length);
		} else {
			m.characterSet = messageCharacterSet;
			m.encoding = messageEncoding;
			m.format = messageFormat;
			m.writeString(messageString);
		}

		if (Config.parms.getBoolean("pp"))
			m.persistence = CMQC.MQPER_PERSISTENT;
		else
			m.persistence = CMQC.MQPER_NOT_PERSISTENT;

		// Check if a correlId is provided and pass it to MQ
		final String correlateValue = Config.parms.getString("cv");

		if (correlateValue != null && !correlateValue.equals(""))
			m.correlationId = convertStringToByte(correlateValue); // String must be converted to byte array for correlation ID
		else
			m.correlationId = m.messageId;

		//This is the default for all msgs but i need to set this to datagram if we using pubsub, which use -rf 2 flag so i have to move this
		m.messageType = CMQC.MQMT_REQUEST;
		if (RFHFormat == 2)
			m.messageType = CMQC.MQMT_DATAGRAM;

		return m;
	} // end public static Message createMessage

	/**
	 * Method makeBigString.
	 * @return String
	 */
	protected String makeBigString(int size) {
		final StringBuffer sb = new StringBuffer(size);
		char c = 65;
		int index = size;
		while (index-- != 0) {
			sb.append(c++);
			if (c > 122)
				c = 65;
		}
		return sb.toString();
	}
	private byte[] convertStringToByte(String value) {
		final int valueLength = value.length();
		final byte[] byteArray = new byte[24];
		int i = 0;

		for (; i < valueLength; i = i + 2)
			byteArray[i / 2] = (byte)Integer.parseInt(value.substring(i, i + 2), 16);

		if ((valueLength % 2) != 0)
			byteArray[i / 2] = (byte)Integer.parseInt(value.substring(valueLength - 1) + "0", 16);

		return byteArray;
	}
}
