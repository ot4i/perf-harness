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
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * An implementation of the statistics module which presents a rolling average
 * window. This allows us to present the maximum of recorded window values in
 * the summary. This value will be a pretty accurate single-metric for cases
 * where performance is level (and doesn't require any further work from the
 * user to determine the average rate).
 * 
 */
public class RollingAvgStats extends Statistics {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;

	private int[] samples;
	private static int numSamples;
	
	private int samplePtr = 0; // newest used slot in the samples array
	private int earlySamples = 0;
	
	private int sum = 0; // This is the sum of the samples (without dividing by the number of samples)
	private int maxSum = Integer.MIN_VALUE;
	
	private TimerTask displayTask;
	private TimerTask sampleTask;
	
	protected int[] perThreadPrev = null;
	
	private final StringBuffer sb = new StringBuffer(128);
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		
		Config.registerSelf( RollingAvgStats.class );
		
		numSamples = Config.parms.getInt("sr"); 
		if ( numSamples<=0 ) {
			Config.logger.log(Level.WARNING,
					"Statistical sample window (sr={0}) must be at least 1",
					numSamples);
		}
		
		int runlength = Config.parms.getInt( "rl" );
		if ( runlength>0 && numSamples>=runlength ) {
			Config.logger
					.log(
							Level.WARNING,
							"Runlength is shorter than the statistical sample window (-sr={0}), try using BasicStats instead.",
							numSamples);
		}
		
	}	
	
	public RollingAvgStats(ControlThread parent) {
		
		super(parent);
		
		samples = new int[ numSamples>0?numSamples:0 ];
		updateValues();
		ControlThread.getTaskScheduler().schedule( sampleTask = new Sample(), 1000, 1000 );
		if ( interval>0 ) {
			ControlThread.getTaskScheduler().schedule( displayTask = new Display(), interval, interval );
			if ( do_perThread ) {
				perThreadPrev = new int[0];
			}
		}

	}

	private final void makeStringBuffer( StringBuffer sb ) {

		if ( do_id.length()>0 ) {
			// Add our process id to output
			sb.append("id=").append(do_id).append(",");
		}
		
		if ( do_perThread ) {
			
			// Calls to getValues are being done every 1s. Therefore we need to
			// keep our own snapshot of the iteration counts between display
			// intervals (rather than the latest sample interval). This is held
			// in perThreadPrev.
			
			int diff;
			// comment these out to avoid printing per-thread data
			sb.append(" (");
			int shortest = curr.length<perThreadPrev.length?curr.length:perThreadPrev.length;
			for (int j = 0; j < shortest; j++) {
				diff = curr[j] - perThreadPrev[j];
				sb.append(diff).append("\t");
			}
			// Add on new threads
			for (int j = shortest; j<curr.length; j++ ) {
				sb.append(curr[j]).append("\t");
			}
			sb.append(") ");
			
			perThreadPrev = curr.clone(); // Take full clone, otherwise we are only copying pointers to the same int objects
			
		} // End perThread 
		
		sb.append("rateR=").append( numberFormat.format(((double) sum) / (earlySamples<numSamples?earlySamples:numSamples) ) );
		sb.append(",threads=").append( parent.getRunningWorkers() );
		
	}

	public void printFinalSummary() {
		
		if ( Config.parms.getBoolean("su") ) {
			
			boolean time_to_last_fire = Config.parms.getString( "sd" ).toLowerCase().equals( Statistics.STATS_TLF );

			long totalIterations = 0;
			double totalDuration = 0;
			double totalRate = 0;
			int counted = 0;			
			StringBuffer totalMsgs = new StringBuffer();
			totalMsgs.append("Total msgs per thread                ");
			String separator = "";
		
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
				totalMsgs.append(separator);
				totalMsgs.append(iterations);
				if ( trimTime!=0 ) {
					iterations -= trimValues[ workers.indexOf( worker ) ];
				}
				
				separator = " , ";
				
				long duration = threadEndTime-threadStartTime;
				
				totalIterations += iterations;
				totalDuration += duration;
				counted++;

			} // end while workers
			
			totalRate = (double) maxSum
			/ (earlySamples < numSamples
					? earlySamples
					: numSamples);
        
            if ( do_perThread ) {
				System.out.println("\n"+totalMsgs);
            }

            System.out.println("totalIterations=" + totalIterations
				+ ",avgDuration=" + numberFormat.format(totalDuration/(1000*counted))
				+ ",maxrateR=" + numberFormat.format(totalRate));
		}
	}

	private final int numNewOperations() {
		
		updateValues();
		int diff;
		int total = 0;
		int shortest = curr.length<prev.length?curr.length:prev.length;
		for (int j = 0; j < shortest; j++) {
			diff = curr[j] - prev[j];
			total += diff;
		}
		// Add on new threads
		for (int j = shortest; j<curr.length; j++ ) {
			total += curr[j];
		}
		return total;
		
	}
	
	public void stop() {
		
		super.stop();
		if ( displayTask!=null ) {
			displayTask.cancel();
			displayTask = null;
		}
		if ( sampleTask!=null ) {
			sampleTask.cancel();
			sampleTask = null;
		}
		
	}
	
	/**
	 * Called to indicate the beginning of the main measurement period. There
	 * may or may not have been a discard period.
	 */
	protected void notifyMeasurementPeriod() {
		
		super.notifyMeasurementPeriod();
		maxSum = Integer.MIN_VALUE;

	}	
	
	private class Sample extends TimerTask {
		
		public void run() {

			// We can be sneaky here and minimise the number of operations performed
			sum -= samples[samplePtr];
			samples[samplePtr] = numNewOperations();
			sum += samples[samplePtr];
			samplePtr = ++samplePtr % numSamples;
			earlySamples++; // beware of wrap around ?

			if (sum > maxSum) {
				maxSum = sum;
				//Log.logger.fine( "new MAXIMUM = "+sum );
			}
			
		}
		
	}
	
	/**
	 * A method for other parts of the application to get stats
	 * The command processor uses this to report stats on request
	 */
	public String requestStatistics() {
		
			sb.setLength(0); // reset stringbuffer
			makeStringBuffer(sb);
			return sb.toString();
		
	}
	
	private class Display extends TimerTask {
		private final StringBuffer sb = new StringBuffer(128);
		public void run() {

			// Report the performance
			sb.setLength(0); // reset stringbuffer
			makeStringBuffer(sb);
			System.out.println(sb.toString());

		} // end run()
	}
	
}
