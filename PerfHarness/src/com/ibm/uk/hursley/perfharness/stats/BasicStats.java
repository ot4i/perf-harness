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

import java.util.ListIterator;
import java.util.TimerTask;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.WorkerThread;


/**
 * The original statistics module. This prints out the instantaneous rate on a
 * periodic basis. The default is now set to use rolling averages as this
 * provides more information to the casual user.
 * 
 */
public class BasicStats extends Statistics {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	protected boolean do_memory = Config.parms.getBoolean( "mu" );

	private TimerTask displayTask;
	
	private final StringBuffer sb = new StringBuffer(128);
	private final Object sbmutex = new Object(); // make sure we don't try & read sb while it's being updated

	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {

		Config.registerSelf( BasicStats.class );		
		
	}
	
	public BasicStats(ControlThread parent) {
		
		super(parent);
		if ( interval>0 ) {
			updateValues();
			ControlThread.getTaskScheduler().schedule( displayTask = new Display(), interval, interval );
		} else {
			// if interval == 0, we'll report stats only on request and
			// it will show data since last request
			updateValues(); 
		}
		
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
		// comment these out to avoid printing per-thread data
		if ( do_perThread ) sb.append(" (");
		int shortest = curr.length<prev.length?curr.length:prev.length;
		for (int j = 0; j < shortest; j++) {
			diff = curr[j] - prev[j];
			if ( do_perThread ) sb.append(diff).append("\t");
			total += diff;
		}
		// Add on new threads
		for (int j = shortest; j<curr.length; j++ ) {
			if ( do_perThread ) sb.append(curr[j]).append("\t");
			total += curr[j];
		}
		if ( do_perThread ) sb.append(") ");
		
		long period = (currMeasurementTime-prevMeasurementTime);
		
		if (interval > 0) {
			// If within 0.5% of expected value
			if ( (Math.abs(period-interval)*1000)/interval<5 ) {
				// force a "perfect" value
				period=interval;
			}
		}
		
		sb.append("rate=").append(numberFormat.format((double) (total*1000) / period));
		sb.append(",total messages=").append(total);
		sb.append(",Snapshot period=").append((int)period / 1000);
		sb.append(",threads=").append( parent.getRunningWorkers() );
		
	}

	public void printFinalSummary() {
		
		boolean time_to_last_fire = Config.parms.getString( "sd" ).toLowerCase().equals( Statistics.STATS_TLF );

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

   			System.out.println("totalIterations=" + totalIterations
   					+ ",avgDuration=" + numberFormat.format(totalDuration/(1000*counted))
   					+ ",totalRate=" + numberFormat.format(totalRate));
		} // end if su
		
	} // end printFinalSummary
		
	public void stop() {
		
		super.stop();
		if ( displayTask!=null ) {
			displayTask.cancel();
			displayTask = null;
		}
		
	}

	
	private class Display extends TimerTask {
		public void run() {
			
			synchronized(sbmutex) {
				
				sb.setLength(0); // reset stringbuffer
				makeStringBuffer(sb);
				System.out.println(sb.toString());
				
			}
	
		} // end run()
	}
	
}
