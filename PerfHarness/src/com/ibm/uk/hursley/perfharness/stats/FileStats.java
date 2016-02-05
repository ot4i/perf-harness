/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
/*
 * Created on 14-Mar-2007
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.ibm.uk.hursley.perfharness.stats;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ListIterator;
import java.util.Locale;
import java.util.TimerTask;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.util.TypedPropertyException;

/**
 * @author jms
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FileStats extends Statistics {

	//Limit per thread reporting to 128 threads
	int diffarray[] =  new int[128];

	final String sdfDatePattern = "yyyy/MM/dd";
	final String sdfTimePattern = "HH:mm:ss";
	final SimpleDateFormat sdfDate = new SimpleDateFormat(sdfDatePattern, Locale.US); 
	final SimpleDateFormat sdfTime = new SimpleDateFormat(sdfTimePattern, Locale.US); 

	//Handle for writing summary.txt run summaries
	private BufferedWriter bw = null;

	//Task providing period updates to system.out
	private TimerTask displayTask;

	public static void registerConfig() {
		// static validation of parameters
		Config.registerSelf( FileStats.class );		
	}
	
	public FileStats(ControlThread parent) {
		super(parent);
		
		if (interval > 0) {
			updateValues();
			ControlThread.getTaskScheduler().schedule( displayTask = new Display(), interval, interval );
		}
	}

	protected void openSummaryFile() {
		String filename = Config.parms.getString("zf");
		try {
			bw = new BufferedWriter(new FileWriter(filename, true));
		} catch (IOException ioe) {
			Log.logger.log(Level.SEVERE,"Cannot open CSV Stats output file:" + filename);
			bw = null;
		}
	}

	protected void closeSummaryFile() {
		try {
			bw.flush();
			bw.close();
		} catch (IOException ioe) {
			Log.logger.log(Level.SEVERE,"Cannot flush/close CSV Stats output file");
		}
		bw = null;
	}
	
	protected void writeSummary(StringBuffer sb) {
		openSummaryFile();
		if (bw != null) {
			try {
				bw.write(sb.toString());
				bw.newLine();
				bw.flush();
			} catch (IOException ioe) {
				Log.logger.log(Level.SEVERE,"Cannot write to output file");
				bw = null;
			}
		}
		closeSummaryFile();
	}
	
	public void timerStarted() {
		super.timerStarted();

		if (trimInterval==0) {
			System.out.println("Collecting message rate data from beginning of run");
		}
	}
	
	protected void notifyMeasurementPeriod() {
		super.notifyMeasurementPeriod();
		System.out.println("Warmup period ended; Message rate data collection starts");
	}
	
	public void stop() {
		super.stop();
		displayTask.cancel();
	}

	protected void makeStringBuffer( StringBuffer sb ) {
		if ( do_id.length()>0 ) {
			// Add our process id to output
			sb.append("id=").append(do_id).append(",");
		}
		updateValues();
		int total = 0;
		int diff;
		
		int shortest = curr.length<prev.length?curr.length:prev.length;
		for (int j = 0; j < shortest; j++) {
			diff = curr[j] - prev[j];
			diffarray[j] = diff;
			total += diff;
		}
		// Add on new threads
		for (int j = shortest; j<curr.length; j++ ) {
			total += curr[j];
			diffarray[j] = curr[j];
		}
		
		long period = (currMeasurementTime-prevMeasurementTime);
		// If within 0.5% of expected value
		if ( (Math.abs(period-interval)*1000)/interval<5 ) {
			// force a "perfect" value
			period=interval;
		}

		sb.append(getTimeStamp());
		sb.append(",Threads,").append( parent.getRunningWorkers() );
		sb.append(",MsgRate,").append(numberFormat.format((double) (total*1000) / period));
		if (do_perThread) {
			sb.append(",IndThreads");
			for (int i=0; i<curr.length; i++) {
				sb.append("," + diffarray[i]);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.stats.Statistics#printFinalSummary()
	 */
	public void printFinalSummary() {
		// TODO Auto-generated method stub
		boolean time_to_last_fire = Config.parms.getString("sd").toLowerCase().equals( Statistics.STATS_TLF );
		
		StringBuffer finalSummary = new StringBuffer();
		long totalIterations = 0;
		double totalDuration = 0;
		double totalRate = 0;
		int counted = 0;
		
		if ( Config.parms.getBoolean("su") ) {
			final ListIterator<WorkerThread> iter = workers.listIterator();
			while ( iter.hasNext() ) {
				final WorkerThread worker = iter.next();
				
				long threadEndTime = worker.getEndTime();
				if ( threadEndTime==0 ) {
					// Use approximate value if we cannot find a specific answer.
					threadEndTime = endTime;
				}
				
				long threadStartTime;
				if ( trimInterval==0 ) {
					threadStartTime = worker.getStartTime();
				} else {
					threadStartTime = trimTime;
				}
				
				// Override the above for different timing modes
				if ( time_to_last_fire ) {
					threadStartTime = JVMStartTime;
				}
				
				long iterations = worker.getIterations();
				
				if ( trimTime!=0 ) {
					iterations -= trimValues[ workers.indexOf( worker ) ];
				}
				
				long duration = threadEndTime-threadStartTime;
				double rate = (double) (iterations * 1000) / duration;
				
				if ( threadStartTime==0 ) {
					duration = 0;
				}
				
				totalIterations += iterations;
				totalDuration += duration;
				totalRate += rate;
				counted++;

			} // end while workers
			
			// Maybe we should test to see if average duration > 90% intended
			// duration, else set MsgRate to zero. Shortened run implies exception
			// Config.rl() - Config.sw = intended
			double avgDuration = totalDuration / (1000 * counted);
			int targetTime = (Config.parms.getInt("rl") - trimInterval);
			if (avgDuration < (targetTime * 0.9)) {
					totalRate = 0;
					Log.logger.fine("Msg Rate set to 0 as test duration not valid; avgDuration: " + avgDuration + "; targetTime: " + targetTime + "; minTime: " + (0.9 * targetTime));
			}		
			String messageFile = Config.parms.getString("mf");
			String messageSize = extractMsgSize(messageFile);
			
			finalSummary.append(getTimeStamp());
			finalSummary.append("," + getTarget());
			finalSummary.append(",MsgSize," + messageSize);
			finalSummary.append(",Threads," + Config.parms.getInt("nt"));
			finalSummary.append(",MsgRate," + numberFormat.format(totalRate));
			finalSummary.append(",Iterations," + totalIterations);
			finalSummary.append(",AvgDuration," + numberFormat.format(avgDuration));
			finalSummary.append(",Tx," + getTx());
			finalSummary.append(",Persistent," + getPp());
			finalSummary.append(",UseCorrId," + getCorrID());

			writeSummary(finalSummary);
			System.out.println(finalSummary);
		} // end if su
	}

	public String extractMsgSize(String s) {
		if (s == null) return "";
		int i = s.length()-1;
		if (i < 0) return ""; 
		while (i>=0 && !(Character.isDigit(s.charAt(i)))) {
			i--;
		}
		int endindex = i + 2; // capturing b, K or M
		while (i>=0 && Character.isDigit(s.charAt(i))) {
			i--;
		}
		int startindex = i + 1; //need to move startindex to start of number

		if ((startindex >= 0) && 
			(endindex >= 0) &&
			(startindex <= endindex)) {
			return s.substring(startindex,endindex);
		}
		return "";
	}
	
	/**
	 * A method for other parts of the application to get stats
	 * The command processor uses this to report stats on request
	 */
	public String requestStatistics() {
		// I've not had a chance to play with this class yet so not got round to writing this
		// Please feel free
		return "unsupported";
	}
	
	private class Display extends TimerTask {
		private final StringBuffer sb = new StringBuffer(128);
		public void run() {
			sb.setLength(0);
			makeStringBuffer(sb);
			System.out.println(sb.toString());
		} 
	}
	
	public String getTimeStamp() {
		Date d = new Date();
		StringBuffer sb = new StringBuffer();
		sb.append(sdfDate.format(d));
		sb.append(",");
		sb.append(sdfTime.format(d));
		
		return sb.toString();
	}
	public String getTarget() {
		String dest="null";
		try {
			dest = Config.parms.getString("iq");
		} catch (TypedPropertyException tpe) {
			try {
				dest = Config.parms.getString("ur");
			} catch (TypedPropertyException tpe2) { 
			}
		}
		return dest;
	}
	public Boolean getTx() {
		Boolean tx = false;
		try {
			tx = Config.parms.getBoolean("tx");
		} catch (TypedPropertyException tpe) {
		}
		return tx;
	}
	public Boolean getPp() {
		Boolean pp = false;
		try {
			pp = Config.parms.getBoolean("pp");
		} catch (TypedPropertyException tpe) {
		}
		return pp;
	}
	public Boolean getCorrID() {
		Boolean co = false;
		try {
			co = Config.parms.getBoolean("co");
		} catch (TypedPropertyException tpe) {
		}
		return co;
	}	
}
