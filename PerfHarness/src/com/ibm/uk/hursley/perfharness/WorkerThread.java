/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.stats.ResponseTimeStats;
import com.sun.jndi.toolkit.ctx.AtomicDirContext;

/**
 * Base class for all varieties of test.  This class implements a several variations
 * on general pacing algorithms for those tests that wish to use it.  The performance
 * overhead of this should be fairly minimal.
 *  
 */
public abstract class WorkerThread extends java.lang.Thread {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	public static final int sCREATED = 1;
	public static final int sCONNECTING = 2;
	public static final int sRUNNING = 4;
	public static final int sERROR = 8;
	public static final int sENDING = 16;
	public static final int sENDED = 32;
	private static Class<? extends WorkerThread> workerclazz = null;

	private static boolean logarithmic; 
	
	// Running count of iterations executed.
	private final AtomicInteger iterations = new AtomicInteger(0);
	private final AtomicLong    minTime = new AtomicLong(999999999); 
	private final AtomicLong    maxTime = new AtomicLong(0); 
	private final AtomicLong    totalTime = new AtomicLong(0); 
	private long   overallTotalTime = 0; 
	private double overallM2 = 0; 
	
	private final boolean transactionResponseStats = Config.isRegistered(ResponseTimeStats.class);

	private long  previousTime = 0;

	protected long startTime = 0; // time thread started iterating (excluding setup time)
	protected long endTime = 0; // time thread was stopped (excluding shutdown time) 

	private long responseStartTime = 0;
	private long responseEndTime = 0;
	private long responseTime = 0;
	private boolean responseTimeStarted = false;

	// Online variance
	double onlineVarianceMean = 0;
	double onlineVarianceM2 = 0;
	double onlineVarianceDelta = 0;

	/**
	 * Bitmask containing status information.
	 */
	protected volatile int status = sCREATED | sENDED;

	/**
	 * If true, this represents that this thread is now attempting to shut down (probably
	 * as part of a global shutdown).
	 */	
	protected volatile boolean shutdown = false;

	private Object mutexWaiting = null; // Lock for frozen/finished threads.

	private int threadnum;
	private static AtomicInteger nextThreadNum = new AtomicInteger(1);

	/**
	 * The rate we want to pace at. Note, it can change
	 */
	protected volatile boolean rateUpdated = false;
	protected volatile double rate = 0.0;

	/**
	 * Unsynchonised random generator for this thread.
	 */
	protected java.util.Random localRandom = new java.util.Random();

	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		// static validation of parameters

		Config.registerSelf(WorkerThread.class);
		/*
		 * TODO: Java 8 support target type inference, so when moving to Java 8 can
		 * replace the following line with:
		 * workerclazz = Config.parms.<WorkerThread>getClazz("tc");
		 */
		workerclazz = Config.parms.getClazz("tc");
		if (!Config.isInvalid()) {
			final double rate = Config.parms.getDouble("rt");
			if (rate < 0)
				Config.logger.log( Level.WARNING, "Rate (rt={0}) must be non-negative (or 0 for off)", rate );
			if (workerclazz != null && !Paceable.class.isAssignableFrom(workerclazz)) {
				// block use of rp, rt, yd, mg if not using a paceable workerthread
				if (Config.parms.getInt("rp") != 0
						|| Config.parms.getInt("rt") != 0
						|| Config.parms.getInt("yd") != 0
						|| Config.parms.getInt("mg") != 0) {
					Config.logger.warning("Cannot use rp, rt, yd, mg if not using a paceable WorkerThread");
				}
			}

			logarithmic = Config.parms.getBoolean("ln");
			if (logarithmic && rate == 0)
				Config.logger.warning( "Logarithmic distirbution (-ln) has no effect when rate=0." );

			final int rampTime = Config.parms.getInt("rp");
			if (rampTime < 0)
				Config.logger.log(Level.WARNING, "Ramp time (rp={0} must be at least 1 (or 0 for off)", rampTime);

			// -mg should be generic iterations
			final int iterations = Config.parms.getInt("mg");
			if (iterations < 0)
				Config.logger.log(Level.WARNING, "Iterations (mg={0}) must be at least 1 (or 0 for off)", iterations);

			if (workerclazz != null)
				Config.registerAnother(workerclazz);
		}
	}

	/**
	 * Creates a WorkerThread named &quot;dummy&quot;.
	 */
	protected WorkerThread() {
		// We have to specify a name to the superclass.  It is expected subclasses will immediately change this.
		super(ControlThread.getWorkerThreadGroup(), "dummy");
		threadnum = nextThreadNum.getAndIncrement();
	}

	/**
	 * Create a named and numbered WorkerThread. Auto generated names will be
	 * &lt;Name&gt;&lt;Number&gt; where Name is the instance classname and
	 * Number is auto assigned.
	 * 
	 * @param name
	 *            If null, the name is auto-generated otherwise this is the name
	 *            of this specific WorkerThread.
	 */
	protected WorkerThread(String name) {
		this();
		setName(name == null ? (this.getClass().getSimpleName() + getThreadNum()) : name);
	}

	/**
	 * Sets the defaultStarttime for this thread then calls the normal
	 * Thread.start(). Be careful not to override this without calling it or
	 * repeating its operation.
	 */
	public void start() {
		// As an emergency measure should the thread not set the starttime itself.
		startTime = System.currentTimeMillis();
		super.start();
	}

	protected final int incIterations() {
		// The following was measured as 1.6x faster than incrementAndGet (on a
		// single-core x86). This only works because this method can only be
		// called by its owning thread.
		final int val = iterations.get() + 1;
		iterations.set(val);

		// Only record if tracking response times
		if (transactionResponseStats) {
			// Check if response time period completed, if not end it now
			if (responseTimeStarted) {
				responseEndTime = System.nanoTime();
				responseTimeStarted = false;
			}
			responseTime = (responseEndTime - responseStartTime) / 1000;
			// Update the best response time for this thread
			minTime(responseTime);

			// Update the worst response time for this thread
			maxTime(responseTime);

			// Update the worst response time for this thread
			totalTime(responseTime);

			// calculate online variance
			onlineVarianceDelta = responseTime - onlineVarianceMean;
			onlineVarianceMean = onlineVarianceMean + (onlineVarianceDelta/(double)iterations.get());
			onlineVarianceM2 = onlineVarianceM2 + onlineVarianceDelta*(responseTime-onlineVarianceMean);
			overallM2 = onlineVarianceM2;

			responseTime = 0;
			responseStartTime = 0;
			responseEndTime = 0;
		}
		return val;
	}

	protected final void startResponseTimePeriod() {
		// Only record if tracking response times
		if (transactionResponseStats) {
			responseStartTime = System.nanoTime();
			responseTimeStarted = true;
		}
	}

	protected final void endResponseTimePeriod() {
		// Only record if tracking response times
		if (transactionResponseStats) {
			responseEndTime = System.nanoTime();
			responseTimeStarted = false;
		}
	}

	protected final void minTime(long time) {
		previousTime = minTime.get();
		if (time < previousTime)
			minTime.set(time);
	}

	protected final void maxTime(long time) {
		previousTime = maxTime.get();
		if (time > previousTime) {
			maxTime.set( time );
		}
	}

	protected final void totalTime(long time) {
		previousTime = totalTime.get();
		totalTime.set(previousTime + time);
		overallTotalTime += time;
	}

	public final double getResponseTimeStdDev() {
		if (iterations.get() > 1) {
			final double variance = onlineVarianceM2 / (iterations.get() - 1);
			return Math.sqrt(variance);
		} else {
			return 0;
		}
	}

	public final long getMinTime() {
		return minTime.get();
	}

	public final long getOverallTotalTime() {
		return overallTotalTime;
	}

	public final long getMaxTime() {
		return maxTime.get();
	}

	public final long resetTotalTime() {
		return totalTime.getAndSet(0);
	}

	public final int getIterations() {
		return iterations.get();
	}

	public long getStartTime() {
		return startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public void updateRate(double newRate) {
		this.rate = newRate;
		this.rateUpdated = true;
	}

	public void signalShutdown() {
		shutdown = true;
		if (usesAsynchronousShutdownSignal()) {
			synchronized (mutexWaiting) {
				mutexWaiting.notifyAll();
			}
		} // end if
	}

	/**
	 * Method getStatus.
	 * @return int
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * Method getThreadNum.
	 * @return int
	 */
	public final int getThreadNum() {
		return threadnum;
	}

	private static final long TIME_PRECISION = 1000000000; // nanos	
	
	/**
	 * Only a class that implements the Paceable interface can be used here. If
	 * not using the Paceable interface, it is the responsibility of the class to
	 * set startTime and endTime accordingly. Failure to do this will result in
	 * approximate times being used by the ControlThread.  Note that the Paceable
	 * is responsible for incrementing WorkerThread.iterations upon successful
	 * completion of its task.
	 * 
	 * @param p
	 *            A paceable WorkerThread.
	 * @param rate
	 *            Throttle operation to at most this rate.
	 * @param totalIterations
	 *            Exact number of operations to pace.
	 * @param yieldRate
	 *            How frequently to explicitly call Thread.yield().
	 * @param rampTime
	 *            Time in milliseconds to bring the system up to speed.
	 * @throws Exception
	 */
	protected void pace(final Paceable p, double rate, int totalIterations, final int yieldRate, long rampTime) throws Exception {
		final boolean usingMG = totalIterations > 0;

		// As of this point we have entered the performance section
		// try to keep code to a minimum and, where possible, "unroll"
		// loops and "pull up" conditional checks 

		startTime = System.currentTimeMillis();


		do { // this loop is in case we change the rate, we have to break the
				// whole pacing thing and start again

			if (rateUpdated) {
				rate = this.rate;
				rateUpdated = false;
			}
			

		if (rate == 0) {
			// We are not trying to fix the rate.
			if (rampTime>0)
				Log.logger.warning("Throughput ramping not supported when no expected rate has been set.");
				while (!shutdown && (!usingMG || totalIterations-- > 0)
						&& !rateUpdated) {
				p.oneIteration();
				if (yieldRate != 0 && totalIterations % yieldRate == 0)
					Thread.yield();
			}
			} else {
				// throttled operation

				rampTime *= (TIME_PRECISION / 1000);

				double rateI = rate;
				long rampTimeI = rampTime;

				// not using startTime in case it gets reset by client.
				long ramp_start = now();
				long delay = (long) (TIME_PRECISION / rate);
				boolean first = true;

				try {

					while (!shutdown && (!usingMG || totalIterations-- > 0)
							&& !rateUpdated) {

						long before = now();

						// RAMPING CODE
						if (rampTimeI > 0) {

							rampTimeI = rampTime - (before - ramp_start);
							if (rampTimeI <= 0) {
								// Ramping over
								rateI = rate;
							Log.logger.finest( "Throughput ramping period (rp) over" );
						} else {
							rateI = rate * ( 1d - (double)rampTimeI/rampTime );
						}

						if (first) {
							// Since the calculated rate at time 0 is ~0, the
							// delay is ~infinity. We counter this by
							// calculating the exact delay required before the
							// (continuous) rate needs to be recalculated.
							delay = (long)(TIME_PRECISION * Math.sqrt((2 * (rampTime/TIME_PRECISION))/rate));
							first = false;
						} else {
							// Imagining the rate is a stair graph, we just pretend
							// at each moment that the rate is, and always will be,
							// flat.
							delay = (long)(TIME_PRECISION / rateI);
						}
					} // end if ramping

					p.oneIteration();
					if (logarithmic)
						doSleep(logarithmicDistribution(delay, delay * 5));
					else
						doSleep(delay);

					if (yieldRate != 0 && totalIterations++ % yieldRate == 0)
						Thread.yield();
				} // end while !shutdown
				endTime = System.currentTimeMillis();
			}
			catch (InterruptedException e) {
				if (!shutdown)
					throw e;
				// else swallowed
				// This is an expected part of the shutdown sequence 
			}
		} // end if throttled operation

		} while (!shutdown && (!usingMG || totalIterations-- > 0));

		// As of this point we have finished the performance section
		if (endTime==0)
			endTime = System.currentTimeMillis();
	}

	private static final long now() {
		// hopefully this will be inlined by javac or JIT!
		return System.nanoTime();
	}
	
	/**
	 * Use a logarithmic distibution to randomise the delay between successive
	 * pacing iterations.
	 * 
	 * @param mean
	 *            The expected average delay
	 * @param max
	 *            Computed delays will be truncated below this value.
	 * @return Selected time until the next operation.
	 */
	private final long logarithmicDistribution(long mean, long max) {
		// 0 <= x < 1
		final double x = localRandom.nextDouble();
		if (x == 0d) {
			// if x=0 then ln(x) = infinity which (if we could calculate it) would be
			// truncated to the max value.
			return max;
		}
		return Math.min((long)(mean * -Math.log(x)), max);
	}

	private long windowbase = 0;
	private long windowcount = 0;
	private static final long WINDOWSIZE = 4 * TIME_PRECISION;

	/**
	 * Tries to sleep intelligently base upon the requested delay period. The
	 * actual sleep period depends on several factors and does not have to be
	 * the delay given (indeed no sleep may be required at all).
	 * 
	 * @param delay
	 * @return The time the method was called (not completed)
	 * @throws InterruptedException
	 */
	private final long doSleep(long delay) throws InterruptedException {
		final long now = now();

		// to overcome loss of precision in timers (and bumps in the system), we
		// use a window and calculate the target time of where we should be for
		// the next operation.
		long windowposition = now - windowbase;
		if (windowposition > WINDOWSIZE) {
			windowcount = 0;
			windowbase = now;
			windowposition = 0;
		}

		windowcount += delay;

		// The time between now and our target time
		final long sleep = windowcount - windowposition;
		if (sleep > 0) {
			final int sleepNanos = (int)(sleep % 1000000);
			final long sleepMillis = (long)((sleep - sleepNanos)* (1000.0 / TIME_PRECISION));
			Thread.sleep(sleepMillis, sleepNanos);
		}
		return now;
	}

	/**
	 * This method implements flags such as -mg -rt -yd and -rp.
	 * @param p A paceable WorkerThread.
	 * @throws Exception
	 */
	protected void pace( Paceable p ) throws Exception {
		final double rate = Config.parms.getDouble("rt");
		int iterations = Config.parms.getInt("mg");
		final int yieldRate = Config.parms.getInt("yd");
		final long rampTime = Config.parms.getInt("rp") * 1000;
		pace(p, rate, iterations, yieldRate, rampTime);
	}

	/**
	 * @return A new instance of the selected workerthread class. Note that the
	 *         class (currently) has to implement a constructor with a string
	 *         argument (threadname)
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws IllegalArgumentException
	 */
	public static WorkerThread newInstance() throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		/*
		 * TODO: Java 8 support target type inference, so when moving to Java 8 can
		 * replace the following lines with:
		 * final Constructor<? extends WorkerThread> c = workerclazz.getConstructor(new Class[] { String.class });
		 * WorkerThread worker = c.newInstance(new Object[] { (String) null });
		 */
		final Constructor<?> c = workerclazz.getConstructor( new Class[] { String.class } );
		WorkerThread worker = (WorkerThread)c.newInstance( new Object[] { (String)null } );		
		// TODO WorkerThread.newInstance is not threadsafe !
		// TODO allow default constructors in WorkerThread implementations
		worker.setDaemon(true);  // TODO This is an assumption!  Should be done by each thread, not by controlthread
		// If this is removed from here, then uncomment relevant section from ControlThread#doShutdown 
		return worker;
	}

	/**
	 * Whether this thread expects an notification signal to shutdown. Note this
	 * will still return true after the signal has been sent.
	 */
	public boolean usesAsynchronousShutdownSignal() {
		return mutexWaiting != null;
	}

	/**
	 * Causes the current thread to block until signalShutdown is called. This
	 * is used by WorkerThreads which are not actively polling the shutdown
	 * field.
	 * 
	 * @throws InterruptedException
	 *             This is thrown if the thread is interrupted outside of the
	 *             expected shutdown period.
	 */
	protected void waitForShutdownSignal() throws InterruptedException {
		if (mutexWaiting == null)
			mutexWaiting = new Object();
		try {
			synchronized (mutexWaiting) {
				mutexWaiting.wait();
			}
		}
		catch (InterruptedException e) {
			if (!shutdown)
				throw e;
			// else swallowed
		}
		
	}

	/**
	 * Implementing WorkerThreads may use the pace method.
	 * @see WorkerThread#pace
	 */
	public interface Paceable {
		// Note: use Callable interface (Java5)
		public boolean oneIteration() throws Exception;
	}
	
}
