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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.*;

/**
 * Base statistics class, this is meant to be extended by subclasses.
 * 
 */
public abstract class Statistics {
	
	private class allThreadsData {
		
		private int[]  iterations;
		//private long[] minResponseTimes;
		//private long[] maxResponseTimes;
		private long[] totalResponseTimes;
		//private double[] responseTimeStdDev;
		private int size = 0;
		
		public void setIterationValue(int index, int value) {
			iterations[index] = value;
		}
		//public void setMinResponseTimeValue(int index, long value) {
		//	minResponseTimes[index] = value;
		//}
		//public void setMaxResponseTimeValue(int index, long value) {
		//	maxResponseTimes[index] = value;
		//}
		public void setTotalResponseTimesValue(int index, long value) {
			totalResponseTimes[index] = value;
		}

		//public void setResponseTimeStdDevValue(int index, double value) {
		//	responseTimeStdDev[index] = value;
		//}
		
		public void setSize(int newSize) {
			iterations = new int[newSize];
			//minResponseTimes = new long[newSize];
			//maxResponseTimes = new long[newSize];
			totalResponseTimes = new long[newSize];
			responseTimeStdDev = new double[newSize];
			size = newSize;
		}
		public int getSize() {
			return size;
		}
		public void setIterations(int[] iterations) {
			this.iterations = iterations;
			size = iterations.length;
			//minResponseTimes = new long[size];
			//maxResponseTimes = new long[size];
			totalResponseTimes = new long[size];
			responseTimeStdDev = new double[size];
		}
		public int[] getIterations() {
			return iterations;
		}
		//public long[] getMinResponseTime() {
		//	return minResponseTimes;
		//}
		//public long[] getMaxResponseTime() {
		//	return maxResponseTimes;
		//}
		public long[] getTotalResponseTimes() {
			return totalResponseTimes;
		}
		public double[] getResponseTimeStdDev() {
			return responseTimeStdDev;
		}
		
	}

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;  
	
	static final String STATS_TLF = "tlf";
	static final String STATS_NORMAL = "normal";	

	protected static final java.text.NumberFormat numberFormat =
		java.text.NumberFormat.getInstance();
	private static Class<? extends Statistics> statsclazz;	
	
	/**
	 * Earliest recorded time in this JVM.  May be used for tlf mode.
	 */
	protected long JVMStartTime;

	/**
	 * Time of last performance operation. Used when a thread does not give its
	 * own end time.
	 */
	protected long endTime = 0;	
	
	protected Runtime runtime = Runtime.getRuntime();

	protected String do_id = Config.parms.getString( "id" );
	protected boolean do_perThread = Config.parms.getBoolean( "sp" );	
	
	/**
	 * Reporting interval in millis.
	 */
	protected final int interval = Config.parms.getInt("ss") * 1000;
	
	
	// iteration counts from this and previous measurement
	protected int[] prev = new int[0];
	protected int[] curr = new int[0];

	protected long[] minResponseTime = new long[0];
	protected long[] maxResponseTime = new long[0];
	protected long[] totalResponseTime = new long[0];
	protected double[] responseTimeStdDev = new double[0];

	// time stamps of this and previous measurement
	protected long prevMeasurementTime;
	protected long currMeasurementTime;
	
	protected ArrayList<WorkerThread> workers = null;
	protected ControlThread parent = null;
	
	
	protected int trimInterval = Config.parms.getInt( "sw" );
	protected long trimTime = 0;
	protected int[] trimValues = null;
	protected TimerTask trimTask = null;
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		
		Config.registerSelf( Statistics.class );
			
		String sd = Config.parms.getString( "sd" );
		if ( !sd.equals( STATS_NORMAL) && !sd.equals( STATS_TLF ) ) {
			Config.logger.warning( "-sd must be one of {"+STATS_NORMAL+","+STATS_TLF+"}");
		}
		if ( Config.parms.getInt("ss")<0 ) {
			Config.logger.warning("-ss must be at least 1 (or 0 for off)");
		}
		
		int runlength = Config.parms.getInt( "rl" );
		int trim = Config.parms.getInt( "sw" );
		if ( runlength>0 && trim>=runlength ) {
			Config.logger
					.log(
							Level.SEVERE,
							"Runlength (rl={0}) is shorter than the discard period (sw={1}).",
							new Object[]{runlength, trim});
		}
		
		numberFormat.setMinimumFractionDigits(2);
		numberFormat.setMaximumFractionDigits(2);
		numberFormat.setGroupingUsed(false);
		
		if ( statsclazz==null ) {
			statsclazz = Config.parms.<Statistics>getClazz( "sc" );
			Config.registerAnother( statsclazz );
		}
		
	}	
	
	protected Statistics(ControlThread parent) {
		this.parent = parent;
		this.workers = parent.getWorkers(); // keep a reference
	}
	
	/**
	 * Read the latest iteration counts from all available WorkerThreads,
	 * keeping a copy of the previous readings. This method ensures no
	 * iterations are lost "between the cracks".
	 */
	protected void updateValues() {
		
		synchronized ( workers ) {
						
			// Copy current values to previous
			if ( prev.length==curr.length ) {
				// If lengths match, copy entries
				System.arraycopy( curr, 0, prev,0, prev.length );
				// Possibly (probably) re-use current array as well
				allThreadsData data = new allThreadsData();
				data.setIterations(curr);
				data = readValues( data );
				curr = data.getIterations();
				//minResponseTime = data.getMinResponseTime();
				//maxResponseTime = data.getMaxResponseTime();
				totalResponseTime = data.getTotalResponseTimes();
				responseTimeStdDev = data.getResponseTimeStdDev();
			} else {
				// array is not the same size, we allocate a new array object.
				prev = curr;
				allThreadsData data = new allThreadsData();
				data.setIterations(curr);
				data = readValues( (allThreadsData)null );
				curr = data.getIterations();
				//minResponseTime = data.getMinResponseTime();
				//maxResponseTime = data.getMaxResponseTime();
				totalResponseTime = data.getTotalResponseTimes();
				responseTimeStdDev = data.getResponseTimeStdDev();
			}
			
			prevMeasurementTime = currMeasurementTime;
			currMeasurementTime = System.currentTimeMillis();
			
		} // end sync
		
	} // end getValues
	
	/**
	 * Read the latest iteration counts from all available WorkerThreads.
	 * 
	 * @param values
	 *            In input array to use. If this is null or is not the correct
	 *            size, a new aray is created. Therefore when passing an input
	 *            array to this method, always use the return array.
	 * @return An array of current values. The length of the array will always
	 *         be correct.
	 */
	protected allThreadsData readValues( allThreadsData values ) {
		
		synchronized( workers ) {
			
			// Hmmpf. WE must synch to avoid JVM throwing ConcurrentModificationException during startup
			int count = workers.size();
			if ( values==null ) {
				values = new allThreadsData();
				values.setSize(count);
				
			} else if ( values.getSize()!=count ) {
				// Reuse array where possible.
				values.setSize(count);	
			}
			
			int i = 0;
			final Iterator<WorkerThread> iter = workers.iterator();
			while (iter.hasNext() && i<values.getSize()) {
				WorkerThread worker = (WorkerThread) iter.next();
				int index = i++;
				values.setIterationValue(index, worker.getIterations());
				//values.setMinResponseTimeValue(index, worker.resetMinTime());
				//values.setMaxResponseTimeValue(index, worker.resetMaxTime());
				values.setTotalResponseTimesValue(index, worker.resetTotalTime());
				//values.setResponseTimeStdDevValue(index, worker.resetResponseTimeStdDev());
			}
			
		} // end sync
		
		return values;
		
	}

	/**
	 * Read the latest iteration counts from all available WorkerThreads.
	 * 
	 * @param values
	 *            In input array to use. If this is null or is not the correct
	 *            size, a new aray is created. Therefore when passing an input
	 *            array to this method, always use the return array.
	 * @return An array of current values. The length of the array will always
	 *         be correct.
	 */
	protected int[] readValues( int[] values ) {
		
		synchronized( workers ) {
			
			// Hmmpf. WE must synch to avoid JVM throwing ConcurrentModificationException during startup
			int count = workers.size();
			if ( values==null || values.length!=count ) {
				// Reuse array where possible.
				values = new int[count];	
			}
			
			int i = 0;
			final Iterator<WorkerThread> iter = workers.iterator();
			while (iter.hasNext() && i < values.length) {
				final WorkerThread worker = iter.next();
				values[i++] = worker.getIterations();
			}
			
		} // end sync
		
		return values;
		
	}

	/**
	 * Singleton style accessor for Statistics class (and subclasses)
	 * @param parent
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	public static Statistics newInstance(ControlThread parent) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		
		final Constructor<? extends Statistics> c = statsclazz.getConstructor( new Class[] { ControlThread.class } );
		Statistics st = c.newInstance( new Object[] { parent } );
		return st;
		
	}

	/**
	 * Sets a time when we think operations may be finishing, this is only used
	 * when the WorkerThreads do not report any end times themselves.
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.getEndTime()
	 * @param time
	 */
	public void setDefaultEndTime( long time ) {
		endTime = time;
	}

	// these should be an interface !

	/**
	 * Cease measuring or reporting statistics.
	 */
	public void stop() {
		
		if ( trimTask!=null ) {
			trimTask.cancel();
		}
		
	}
	
	public abstract void printFinalSummary();
	public abstract String requestStatistics(); // reports stats on request

	/**
	 * This should be set to the earliest recorded time.  It is used in the "tlf" mode
	 * of operation which includes setup time. 
	 * @param staticStartTime
	 */
	public void setStaticStartTime(long staticStartTime) {
		this.JVMStartTime = staticStartTime;
	}
	
	/**
	 * Informs the stats class that the main run period has commenced. This is
	 * used when ignoring N seconds of early output.
	 */
	public void timerStarted() {
		
		if ( trimInterval!=0 && trimTask==null ) {
			ControlThread.getTaskScheduler().schedule( trimTask = new Trim(), trimInterval*1000 );
		}
		
	}
	
	/**
	 * Called to indicate the beginning of the main measurement period. There
	 * may or may not have been a discard period.
	 */
	protected void notifyMeasurementPeriod() {
		
		trimTime = System.currentTimeMillis();
		trimValues = readValues( (int[])null );

	}
	
	/**
	 * Reports if activity (iterations) was observed in the last statistics cycle for the given thread.
	 * This method is therefore somewhat tied to output reporting frequency!
	 * @param wt
	 * @return True if activity was monitored.
	 */
	public boolean reportActivity( WorkerThread wt ) {
		// Note: dont be tied to output reporting frequency !
		
		try {
			int number= wt.getThreadNum();
			
			int count = curr[number]- prev[number];
			
			return count>0;
			
		} catch (Exception e) {
			// probably array out of bounds etc
			return false;
		}
		
	}
		
	private class Trim extends TimerTask {
		
		public void run() {
			
			if ( Log.logger.isLoggable(Level.FINEST) ) {
				Log.logger.finest( "Statistical discard period (sw) passed." );
			}			
			notifyMeasurementPeriod();
			
		}
		
	}

}
