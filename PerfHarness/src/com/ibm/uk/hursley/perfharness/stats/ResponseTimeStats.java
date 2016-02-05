/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
/*

 * PerfHarness $Name$
 */
package com.ibm.uk.hursley.perfharness.stats;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.TimerTask;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.WorkerThread;


/**
 * This statistics module reports response times.
 * 
 */
public class ResponseTimeStats extends Statistics {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	protected boolean do_memory = Config.parms.getBoolean( "mu" );
	
	private final StringBuffer sb = new StringBuffer(128);
	private final Object sbmutex = new Object(); // make sure we don't try & read sb while it's being updated

	private TimerTask displayTask;

	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {

		Config.registerSelf( ResponseTimeStats.class );		
		
	}
	
	public ResponseTimeStats(ControlThread parent) {
		
		super(parent);
		if ( interval>0 ) {
			updateValues();
			ControlThread.getTaskScheduler().schedule( displayTask = new Display(), interval, interval );
		}
		
	}

	protected void makeStringBuffer( StringBuffer sb ) {
		
		if ( do_id.length()>0 ) {
			// Add our process id to output
			sb.append("id=").append(do_id).append(",");
		}
		if ( do_memory ) {
			// Print memory in use
			sb.append("memory=").append(
				numberFormat.format(
					(double) (runtime.totalMemory()
					- runtime.freeMemory())
				)
			).append(",");
		}

		updateValues();
		int total = 0;
		int diff;
		
		long totalTime = 0;
		
		// comment these out to avoid printing per-thread data
		if ( do_perThread ) sb.append(" (");
		
		int shortest = curr.length<prev.length?curr.length:prev.length;
		for (int j = 0; j < shortest; j++) {
			diff = curr[j] - prev[j];
			total += diff;
			
			long tTotalTime = totalResponseTime[j];

			if ( do_perThread ) {
				sb.append(diff);
				if (diff > 0) {
					sb.append(","+tTotalTime/diff+"(micros)");
				}
				sb.append("\t");
			}

			totalTime = totalTime + totalResponseTime[j];
		}

		// Add on new threads
		for (int j = shortest; j<curr.length; j++ ) {
			
			total += curr[j];

			long tTotalTime = totalResponseTime[j];
			
			if ( do_perThread ) {
				sb.append(curr[j]);
				if (total > 0) {
					sb.append(","+tTotalTime/total+"(micros)");
				}
				sb.append("\t");
			}
			
			totalTime = totalTime + totalResponseTime[j];
		}
		if ( do_perThread ) sb.append(") ");
		
		long period = (currMeasurementTime-prevMeasurementTime);
		// If within 0.5% of expected value
		if ( (Math.abs(period-interval)*1000)/interval<5 ) {
			// force a "perfect" value
			period=interval;
		}
		
		sb.append("tps=").append(numberFormat.format((double) (total*1000) / period));
		sb.append(",avgResponse(micros)");

		if (total > 0) {
			sb.append("=").append(totalTime/total);
		}
		sb.append(",threads=").append( parent.getRunningWorkers() );
		
	}

	public void printFinalSummary() {
		
		boolean time_to_last_fire = Config.parms.getString( "sd" ).toLowerCase().equals( Statistics.STATS_TLF );

	    long minOverallResponseTime = 999999999;
		long maxOverallResponseTime = 0;
		long totalOverallResponseTime = 0;
		long minTime = 999999999;
		long maxTime = 0;
		long totalTime = 0;
		double stdDev = 0;
		long totalIterations = 0;
		double totalDuration = 0;
		double totalRate = 0;
		int counted = 0;
		
		DecimalFormat df = new DecimalFormat("#.0");
		
		ArrayList<StringBuilder> workerStats = new ArrayList<StringBuilder>();
		
		if ( Config.parms.getBoolean("su") ) {
			
			final ListIterator<WorkerThread> iter = workers.listIterator();
			while ( iter.hasNext() ) {
				
				WorkerThread worker = (WorkerThread)iter.next();
				StringBuilder sb = new StringBuilder();
				
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

				// Process total time for this thread
				totalTime = worker.getOverallTotalTime();
				totalOverallResponseTime = totalOverallResponseTime+totalTime;
				
				minTime = worker.getMinTime();
				
				if (minTime < minOverallResponseTime) {
					minOverallResponseTime = minTime;
				}

				maxTime = worker.getMaxTime();

				if (maxTime > maxOverallResponseTime) {
					maxOverallResponseTime = maxTime;
				}
				
				stdDev = worker.getResponseTimeStdDev();

				// Add data to entry
				sb.append(pad(worker.getThreadNum()));
				sb.append(pad(iterations));
				sb.append(pad(df.format(duration/(1000))));
				sb.append(pad(df.format(rate)));
				sb.append("|");
				sb.append(pad(totalTime/iterations));				
				sb = (minTime == 999999999)? sb.append(pad("N/A")) : sb.append(pad(minTime));
				sb = (maxTime == 0)? sb.append(pad("N/A")) : sb.append(pad(maxTime));				
				sb.append(pad(df.format(stdDev)));
				sb.append("|");
				
				workerStats.add(sb);
				
			
				totalIterations += iterations;
				totalDuration += duration;
				totalRate += rate;
				counted++;

			} // end while workers
			 			
   			System.out.println("==================");
   			System.out.println("--------------------------------------------------------------------------------|--------------------------------------------------------------------------------|");
   			System.out.println("                                                                                |                                 ResponseTimes                                  |");
			System.out.println(pad("Thread Number") + pad("Iterations") + pad("Duration") + pad("Average MsgPerSec") + "|"+ pad("Average") + pad("Minimum") + pad("Maximum") + pad("Standard Deviation") + "|");
			System.out.println("--------------------------------------------------------------------------------|--------------------------------------------------------------------------------|");
   			
			for(StringBuilder sb : workerStats) {
   				System.out.println(sb.toString());
   			}
			
			System.out.println("--------------------------------------------------------------------------------|--------------------------------------------------------------------------------|");
			System.out.println(pad("OVERALL:") +
					pad(totalIterations) +
					pad(numberFormat.format(totalDuration/(1000*counted))) +
					pad(numberFormat.format(totalRate)) +
					"|" +
					pad("" + totalOverallResponseTime/totalIterations) +
					pad("" + minOverallResponseTime) +
					pad("" + maxOverallResponseTime) +
					pad("---"));
			
		} // end if su
		
	} // end printFinalSummary
	
	private String pad(long l) {
		return pad("" + l);
	}
	
	private String pad(int i) {
		return pad("" + i);
	}
	
	private String pad(String s) {
		String s2 = String.format("%1$"+20+ "s", s);
		return s2;
	}
		
	public void stop() {
		
		super.stop();
		if ( displayTask!=null ) {
			displayTask.cancel();
			displayTask = null;
		}
		
	}

	private class Display extends TimerTask {
		public void run() {
			
			sb.setLength(0); // reset stringbuffer
			makeStringBuffer(sb);
			System.out.println(sb.toString());
	
		} // end run()
	}
	
	/**
	 * A method for other parts of the application to get stats
	 * The command processor uses this to report stats on request
	 */
	public String requestStatistics() {
		// if interval is > 0, we'll report the last collected stats set
		//    otherwise we'll get data since last collection
		if (interval > 0) {
			synchronized (sbmutex) {
				return sb.toString();
			}
		} else {
			// don't need the mutex here as there's no timer
			sb.setLength(0); // reset stringbuffer
			makeStringBuffer(sb);
			return sb.toString();
		}
	}
}
