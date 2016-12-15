/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.cmd.Command;
import com.ibm.uk.hursley.perfharness.stats.Statistics;
import com.ibm.uk.hursley.perfharness.util.ShutdownHook;
import com.ibm.uk.hursley.perfharness.util.ShutdownSocketHandler;

/**
 * This class is responsible for managing the lifecycle of all WorkerThreads.
 * @see com.ibm.uk.hursley.perfharness.WorkerThread
 */
public class ControlThread extends Thread {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	protected static long staticStartTime;	
	
	/**
	 * If true, this represents that the system is now attempting to shut down.
	 */
	protected static boolean shutdown = false;
	
	private static ThreadGroup controlThreadGroup = new ThreadGroup( "PerfHarness" );
	private static ArrayList<ControlThread> controllers = new ArrayList<ControlThread>( 1 );
	private static int nextControlNumber = 1;
	private static Timer timer = null;
	
	/**
	 * All workers threads, regardless of status.  <b>Access to this array leading to alterations MUST be synchronised.</b>
	 */
	protected final ArrayList<WorkerThread> workers = new ArrayList<WorkerThread>();
	
	/**
	 * Not to be trusted 100% but this gives the number of currently operating workers.
	 */
	protected volatile int runningWorkers = 0;

	/**
	 * The thread implementing statistics aggregation and reporting.
	 */
	protected Statistics stats = null;
	
	/**
	 * The thread implementing in-flight command processing.
	 */
	protected Command cmd = null;
	
	/**
	 * The thread implementing the runlength timer.
	 */
	private TimerTask runlengthTimer = null;

	/**
	 * All WorkerThreads are added to this group for ease of monitoring.
	 */
	protected static ThreadGroup workerThreadGroup = null;

	/**
	 * Worker name
	 */
	private	static String workerClass;	
	
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
	public static void registerConfig() {
		
		// static validation of parameters
		Config.registerSelf( ControlThread.class );
		
		if ( ! Config.isInvalid() ) {
			int threads = Config.parms.getInt("nt");
			if ( threads<=0 ) {
				Config.logger.log(Level.WARNING, "Number of workers (nt={0}) must be at least 1", threads );
			}
			int runLength =  Config.parms.getInt("rl");
			if ( runLength<0 ) {
				Config.logger.log(Level.WARNING, "Runlength (rl={0}) must be at least 1 (or 0 for off)", runLength );
			}
			
			final Class<? extends ControlThread> ctClazz = Config.parms.<ControlThread>getClazz( "ct" );
			if ( ctClazz!=ControlThread.class ) {
				Config.registerAnother( ctClazz );
			}

		}

		WorkerThread.registerConfig();
		Statistics.registerConfig();
		Command.registerConfig();
			
		workerClass = Config.parms.getClazz("tc").getSimpleName();
	}
	
	protected ControlThread() {
		super( controlThreadGroup, "ControlThread"+getNextControlNumber());
		addController( this );
	}

	protected ControlThread(String s) {
		super( controlThreadGroup,s );
		addController( this );
	}
	
	private static synchronized int getNextControlNumber() {
		return nextControlNumber++;
	}

	/**
	 * Create a new ControlThread as specified by "ct".
	 * @throws Exception
	 */
	public static ControlThread getInstance() throws Exception {
		/*
		 * TODO: Java 8 support target type inference, so when moving to Java 8 can
		 * replace the following line with:
		 * return Config.parms.<ControlThread>getClazz( "ct" ).newInstance();
		 */
		return (ControlThread) Config.parms.getClazz( "ct" ).newInstance();
			
	}	
	
	public static void joinAllControllers() {

		ArrayList<ControlThread> cloneList;
		synchronized ( controllers ) {
			// This avoids concurrentmodifcationexception as controlthreads remove
			// themselves from the list
			cloneList = new ArrayList<ControlThread>(controllers);
		}
		
		for (final Iterator<ControlThread> iter = cloneList.iterator(); iter.hasNext();) {
			final ControlThread ct = iter.next();
			try {
				Log.logger.log( Level.FINER, "Calling Thread.join() on {0}", ct.getName() );
				ct.join();
			} catch (InterruptedException e) {
				Log.logger.log( Level.WARNING, "Problem during controlled shutdown", e );
			}
		} // end for		

	}

	/**
	 * Poke the ControlThread out of any sleep it is in.  This should
	 * help it react promptly to threads ending normally or abnormally.
	 */
	private static synchronized void signalAllControllers() {

		synchronized ( controllers ) {
	
			for (final Iterator<ControlThread> iter = controllers.iterator(); iter.hasNext();) {
				final ControlThread ct = iter.next();
				Log.logger.log( Level.FINE, "Calling Thread.notifyAll() on {0}",ct.getName() ); 
				synchronized (ct) {
					ct.notifyAll();
				} // end sych
			} // end for
			
		} // end sync
		
	}
	
	/**
	 * Register a new ControlThread.
	 * @param ct
	 */
	private static void addController( ControlThread ct ) {
		
		synchronized ( controllers ) {
			controllers.add( ct );
		} // end sync
		
	}
	
	/**
	 * Deregister a ControlThread.
	 * @param ct
	 */
	private static void removeController( ControlThread ct ) {
		
		synchronized ( controllers ) {
			controllers.remove( ct );
			if ( controllers.isEmpty() ) { 
				// We are the last one and are exiting.
				if ( timer!=null ) {
					timer.cancel();
					timer = null;
				}
			}
		} // end sync
		
	}	

	/**
	 * Sets up timer and statistics threads then starts the required number of WorkerThreads and waits for them to end or for
	 * shutdown to be signalled.  ControlThread then tries to terminate remaining threads as nicely
	 * as possible.
	 */
	public void run() {
		// Flag to indicate that we have found threads that match our worker class
		boolean workerThreadsPresent = true;
		
		// Inform config module to finalise syntax checking
		Config.markLoaded();
		
		if ( Log.errorsReported() ) {
			System.exit(1);
		}

		try {
			Log.logger.log( Level.INFO, "START");
			installShutdownMethods();
			int numworkers = Config.parms.getInt( "nt" );

			// Start (anonymous inner class) stats thread
			startStatsThread();

			// Start command listener thread
			startCmdThread();			
			
			// Special, if we are running DoNothingThread then we
			// are measuring the memory usage per thread.
			if ( Config.parms.getString( "tc" ).indexOf("Nothing")>=0 ) {
				run_ThreadSizeLoop();
			} else {
				// 3b.
				// Start threads sequentially
				setNumWorkers(numworkers);
				
				if ( startWorkers(null) ) {
					startTimerThread();
					// Sleep for remainder of testrun
					synchronized( this ) {
						while (!shutdown && (workerThreadGroup.activeCount() > 0) && workerThreadsPresent) {
							int count = workerThreadGroup.activeCount();
							int workerCount = 0;
							Log.logger.log(Level.FINE, "ActiveCount: " + count);
							//Workers are already started, so shouldnt have to do any complicated retesting logic here
							//We are only looking for at least one thread that matches the required worker class							
							if (count > 0) {
								Thread threads[] = new Thread[count];
								int rc = workerThreadGroup.enumerate(threads);
								for (int t = 0; t < rc; t++) {
									Log.logger.log(Level.FINEST, "ThreadName: " + threads[t].getName());
									if (threads[t].getName().startsWith(workerClass)) {
										workerCount++;
									}
								}
							}
							Log.logger.log(Level.FINE, "Worker Count: " + workerCount);
							workerThreadsPresent = (workerCount != 0);
							try {
								this.wait(5 * 1000);
							} catch (InterruptedException e) {
								// Swallowed
							}
						} //end while
					} //end sync
				} // end if startworkers
			} // end if threadsizeloop

		// Handle a fatal error
		} catch (Exception e) {
			Log.logger.log( Level.SEVERE, "Fatal Error.", e );

		// Clear up code carefully in fair weather or foul.
		} finally {
			// Set rough approximation of endTime 
			stats.setDefaultEndTime( System.currentTimeMillis() );
			stats.setStaticStartTime( staticStartTime );
			
			// Kill all workers
			doShutdown();
			stats.printFinalSummary();
			Log.logger.log( Level.INFO, "STOP");
		}

	} // End public void run()

	/**
	 * Handles shutdown sequence of WorkerThreads.  The current implementation notfies each thread
	 * of the shutdown status then interrupts it.  It waits for (default) 2 minutes for these threads
	 * before exiting anyway.
	 */
	protected void doShutdown() {
		
		// may be redundant in many circumstance, but not all.
		shutdown = true;
		
		// remove shutdownHook if not already invoked
		if ( shutdownHookThread!=null && !shutdownHookThread.isAlive() ) {
			try {
				Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
			} catch ( IllegalStateException e ) {
				// No-op
			}
		}
				
		stopWorkers( workers );
		
		if (stats!=null) {
			stats.stop();
		}
		
		if ( runlengthTimer!=null ) {
			runlengthTimer.cancel();
		}

		// ... then wait for them for 2 minutes
		int count = Config.parms.getInt( "wk" );
		StringBuffer activethreads = new StringBuffer();
		waiting : while ((count-- != 0)) {

			activethreads.setLength(0);
			final Iterator<WorkerThread> iter = workers.iterator();
			while (iter.hasNext()) {
				final WorkerThread worker = iter.next();
				if ( (worker.getStatus()&WorkerThread.sENDED)==0 ) {
					
					activethreads.append(worker.getName());
					activethreads.append(" ");
					
					if ( (worker.getStatus()&WorkerThread.sENDING)==0 && count==5 ) {
						// if the worker is not even "ending" yet it must be stuck in the main code
						// so we will get it a shock.
						if (!worker.usesAsynchronousShutdownSignal()) {
							Log.logger.log(Level.FINER,
									"Interrupting inactive WorkerThread {0}",
									worker.getName());
							worker.interrupt();
						}
					}
					
				}
			}
			
			if (activethreads.length() > 0) {
				if (count % 10 == 0) {
					Log.logger.log(Level.WARNING, "Waiting for ( {0} )",
							activethreads);
				}
			} else {
				break waiting;
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				//no op
			}
		} // end while counting down
		
		removeController( this );
		
	}

	/**
	 * Signals the given workers to end.  Note that this method does not validate
	 * that the signal is being acted upon.
	 * @param workers The list of workers to peruse.  Providing null implies to use ControlThread.workers.
	 */
	protected void stopWorkers( Collection<WorkerThread> workers ) {
		
		if ( workers==null ) {
			workers = this.workers;
		}		
		
		final Iterator<WorkerThread> iter = workers.iterator();
		while (iter.hasNext()) {
			final WorkerThread worker = iter.next();
			// if not ending
			if ( (worker.getStatus()&(WorkerThread.sENDED|WorkerThread.sENDING))==0 ) {
				
				worker.signalShutdown();
				runningWorkers--; // this may not be accurate if the thread ignores the signal

			} // end if ! ending
		} // end while
		
	}


	private static ShutdownHook shutdownHookThread = null;
	
	/**
	 * Adds any configured shutdown methods.
	 */
	protected synchronized static void installShutdownMethods() {
		
		installShutdownHook();
		installShutdownSocketHandler();
		
	}

	/**
	 * Adds a signal handler which will attempt to intiate a controlled shutdown.
	 * Of course this might take a long time and the handler is stuck in a Thread.join
	 * with the ControlThread.   
	 */	
	protected synchronized static void installShutdownHook() {
		
		if (Config.parms.getBoolean("sh") && shutdownHookThread == null) {

			Runtime.getRuntime().addShutdownHook(
					shutdownHookThread = new ShutdownHook());
			Log.logger.fine("Signal handler installed");

		} // end if sh
		
	}
	
	private static ShutdownSocketHandler socketThread = null;
	
	/**
	 * Adds a socket listener which will attempt to intiate a controlled shutdown.
	 */		
	private synchronized static boolean installShutdownSocketHandler() {

	    final int shutdownSocket = Config.parms.getInt("so");
	  
		if ( shutdownSocket != 0 && socketThread==null ) {
	
			socketThread = new ShutdownSocketHandler( shutdownSocket );
			socketThread.start();
			
		} // end if socketShutdown > 0

		return true;
		
	}
		
	/**
	 * A simple method to do nothing very much. This is used in determining
	 * static memory consumption.
	 */
	protected void run_ThreadSizeLoop() throws Exception {
		
		for( int i=25; i<=100; i+=25 ) {
			setNumWorkers(i);
			try {
				Thread.sleep(60 * 1000);
			} catch (InterruptedException e) {
			}
		}
		
	}

	/**
	 * a stats pass-through method to get stats on request
	 */
	public String requestStatistics() {
		if (stats != null) {
			return stats.requestStatistics();
		} else {
			return null;
		}
	}

	/**
	 * Alter the number of running workers (used by the Command Processor) and
	 * start / stop them.
	 * 
	 * @param number
	 *            the count to increase or decrease by (+ve or -ve)
	 * @return
	 * @throws Exception
	 */
	public boolean changeWorkerCount(int number) throws Exception {
		if (number == 0) {
			return true;
		}

		int workerCount = getWorkers().size();
		setNumWorkers(workerCount + number);
		if (number > 0) {
			return startWorkers(null);
		} else { // stop workers?
			setNumWorkers(workerCount + number);
			return true;
		}
	}
	
	/**
	 * Alter the number of running workers from the current level to the specified
	 * level.  Does not currently support reduction as this feature is unused. 
	 * @param number
	 * @throws Exception
	 */
	protected void setNumWorkers(int number) throws Exception {
		final int initial = workers.size();
		if ( number>initial ) {
			// Create some more workers
			addWorkers( number-initial );
		} else {
			// remove workers
			Log.logger.log( Level.SEVERE, "thread reduction not yet implemented" );
		}
	}
	
	protected final boolean startCmdThread() throws Exception {

		try {
			cmd = Command.newInstance(this);
			cmd.start();
		} catch (Exception e) {
			Log.logger.log(Level.WARNING, "ERROR creating command server. Command server not available: {0}", e.getMessage());
		}

		return true;

	}


	protected final boolean startStatsThread() throws Exception {

		stats = Statistics.newInstance(this);
		return true;
	}

	public static synchronized Timer getTaskScheduler() {
		if ( timer==null ) {
			timer= new Timer();
		}
		return timer;
	}

	protected final boolean startTimerThread() throws Exception {
	
	    final long time = Config.parms.getInt("rl");
	
		if ( time > 0 ) {
	
			runlengthTimer = new TimerTask() {
				public void run() {
					Log.logger.fine( "Runlength expired" );
					signalShutdown();
				}
			};
			getTaskScheduler().schedule( runlengthTimer, time * 1000 );
			Log.logger.fine( "Runlength timer started" );
			
		} // end if time > 0
		
		if ( stats!=null ) { 
			stats.timerStarted();
		}

		return true;
	}

	/**
	 * Starts any WorkerThreads in the workers array which are not yet started.
	 * It does this using a startup pool of threads. Start is issued to multiple
	 * threads to fill the pool. As threads start, they get removed from the pool
	 * and the start command can be issued to a new thread.
	 * 
	 * @param newWorkers
	 *            If not null the given workers are started and are added to the
	 *            main list of workers.
	 * @param new_qm signifies it's the new version of this method.
	 * @return boolean True if all workers were started without error.
	 */
	protected final boolean startWorkers(Collection<WorkerThread> newWorkers, boolean new_qm)
			throws Exception {
		
		final class StartingWorker implements Comparable<StartingWorker> {
			WorkerThread wt;
			Long started;

			StartingWorker(WorkerThread wt, Long started) {
				this.wt = wt;
				this.started = started;
			}

			public int compareTo(StartingWorker sw) {
				return (started.compareTo(sw.started));
			}
		}

		final long waitPeriod = ((long) Config.parms.getInt("wt")) * 1000;
		// this now becomes the pause between adding new workers to the pool
		final int workerInc = Config.parms.getInt("wi");
		
		final int poolmax = Config.parms.getInt("wc", 0);
		ArrayList<StartingWorker> startPool = new ArrayList<StartingWorker>(poolmax);
		
		int status = 0;

		if (newWorkers != null) {
			addWorkers(newWorkers);
		}

		Iterator<WorkerThread> iter = workers.iterator();
		long looptime;

		// for some stats
		Long timetostart, fastest = Long.MAX_VALUE, slowest = 0L, sumtostart = 0L;
		Itera: while (!shutdown && iter.hasNext()) {
			WorkerThread worker = (WorkerThread) iter.next();

			// Skip any already running threads
			if ((worker.getStatus() & WorkerThread.sCREATED) == 0) {
				continue Itera;
			}

			looptime = System.currentTimeMillis();

			worker.start();			
			StartingWorker sw = new StartingWorker(worker, looptime);
			startPool.add(sw);

			/*
			 * if the pool's not full, start more threads
			 */
			if (startPool.size() < poolmax) {
				if (iter.hasNext()) {
					Thread.sleep(workerInc);
					continue Itera;
				}
			}

			/*
			 * If the pool's full, wait until either one has started or the
			 * oldest one times out
			 */
			while (!shutdown && startPool.size() > 0) {
				
				/*
				 * check if the oldest starting worker has
				 * exceeded the wait time (note: the array is sorted by start time)
				 */
				if ((looptime - startPool.get(0).started) > waitPeriod) {
					Log.logger
							.log(Level.SEVERE,
									"WorkerThread start interval (-wt={0}) exceeded with no response from {1}",
									new Object[] { waitPeriod / 1000,
											startPool.get(0).wt.getName() });

					// Note: thread may start later and threads=n output will not be
					// updated
					return false;
				}
				
				for (int i = 0; i < startPool.size(); i++) {
					if (shutdown)
						break;
						
					
					StartingWorker ref_sw = startPool.get(i);
					status = ref_sw.wt.getStatus();

					// It failed ...
					if ((status & WorkerThread.sERROR) != 0) {
						Log.logger.log(Level.SEVERE,
								"Error starting WorkerThread {0}",
								worker.getName());
						return false;
					}

					// if it's up and running, either .... 
					// i. remove from the list & go to the top to start another
					// or ii. remove from the list & continue looping until all have started 
					if ((status & WorkerThread.sCREATED) == 0) {
						// Note, sCONNECTING is ignored (we wait until it is running)
						if ((status & WorkerThread.sRUNNING) != 0) {

							runningWorkers++;
							
							timetostart = looptime - ref_sw.started;
							if (timetostart < fastest) { fastest = timetostart; }
							if (timetostart > slowest) { slowest = timetostart; }
							sumtostart += timetostart;
							
							startPool.remove(i);

							// re-sort the list. This should be fast as it
							// should be nearly sorted before we start
							Collections.sort(startPool);

							if (iter.hasNext()) {
								Thread.sleep(workerInc);
								continue Itera;
							}
						}
					}
				} // end for(...
				
				// Either the pool is still full or we're waiting for the last threads to start
				// If we get here, startup is SLOW... expect long connect times
				if (startPool.size() > 0) {
					Thread.sleep(200);
					looptime = System.currentTimeMillis();
				}
			} // end while pool.size() > 0
		} // end while iter

		String msg = "threads=" + runningWorkers + ",fastest_conn=" + fastest + ",slowest_conn=" + slowest + ",avg_conn=" + ((runningWorkers > 0) ? sumtostart/runningWorkers : 0);
		Log.logger.log(Level.INFO, msg);
		cmd.sendMessage(msg);
			
		return true;
	}
	
	/**
	 * Starts any WorkerThreads in the workers array which are not yet started.  It does this in a 
	 * controlled sequential manner, waiting for each thread's status to change to RUNNING before
	 * starting the next one.
	 * @param newWorkers If not null the given workers are started and are added to the main list of workers. 
	 * @return boolean True if all workers were started without error.
	 */
	protected final boolean startWorkers( Collection<WorkerThread> newWorkers ) throws Exception {
		
		// do we use the new startup pooling option?
		if (true == Config.parms.getBoolean("wp", false)) {
			return startWorkers(newWorkers, true);
		}
		
		final int workerInc = Config.parms.getInt( "wi" );
		final int waitPeriod = Config.parms.getInt( "wt" ) * 1000;
		int status = 0;

		if ( newWorkers!=null ) {
			addWorkers( newWorkers );
		}
		
		final Iterator<WorkerThread> iter = workers.iterator();
		Itera: while ( !shutdown && iter.hasNext() ) {
			final WorkerThread worker = iter.next();
			
			// Skip any already running threads
			if ( (worker.getStatus() & WorkerThread.sCREATED)==0 ) {
				continue Itera;
			}
			
			worker.start();
			
			// poll thread for -wt seconds
			
			if ( waitPeriod>0 ) {
				
				// start waiting for the thread to signal it is running				
				long started = System.currentTimeMillis();				
				while ( System.currentTimeMillis()-started < waitPeriod) {
					if ( shutdown ) {
						return false;
					}
					status = worker.getStatus();
					// It failed ...
					if ((status & WorkerThread.sERROR) != 0) {
						Log.logger.log(Level.SEVERE,
								"Error starting WorkerThread {0}", worker
										.getName());
						return false;
					}
					// if it's up and running .... check next one
					if ( (status&WorkerThread.sCREATED)==0) {
						// Note, sCONNECTING is ignored (we wait until it is running)
						if ((status & WorkerThread.sRUNNING) != 0) {
							
							runningWorkers++;
							
							if ( iter.hasNext() ) {
								// if not the last thread then sleep for wi millis
								try {
									Thread.sleep(workerInc);
								} catch (InterruptedException e) {
									//no op
								}
							} // end if
							
							continue Itera;
						} else if ( (status&(WorkerThread.sENDING|WorkerThread.sENDED)) !=0 ) {
							// Note: WorkerThread has started and finished already ! maybe we should
							// report this as an error
						 	continue Itera;
						}
					} // end if
					
					try {
						// Polling sleep
						Thread.sleep(200);
					} catch (InterruptedException e) {
						//no op
					}
					
				} // end while waiting for this thread to start

				Log.logger
						.log(
								Level.SEVERE,
								"WorkerThread start interval (-wt={0}) exceeded with no response from {1}",
								new Object[]{waitPeriod / 1000,
										worker.getName()});
					
				// Note: thread may start later and threads=n output will not be updated				
				return false;				
				
			} // end if waitime>0

		} // Endfor server iteration
		return true;

	}

	/**
	 * Creates N WorkerThread instances without starting them. 
	 * @see #startWorkers
	 */
	protected final void addWorkers( int number ) throws Exception {

		for (int i = 0; i < number; i++) {

			WorkerThread worker = WorkerThread.newInstance();
			addWorker( worker );

		} // End for threads

	}
	
	/**
	 * Adds the given thread to the worker list but does not start it.
	 * @param worker
	 */
	protected final void addWorker( WorkerThread worker ) {
		
		synchronized ( workers ) {
			// synch to avoid ConcurrentModificationException during startup			
			workers.add( worker );
		}
		
	}
	
	protected final void removeWorkers(int number) {
		if (number >= workers.size()) {
			signalShutdown();
			return;
		}

		// stop workers 1 @ a time
		while (number-- > 0) {
			WorkerThread w = workers.get(workers.size() - 1);
			removeWorker(w);
		}
	}

	protected final void removeWorker(WorkerThread worker) {

		if (worker == null) {
			return;
		}

		// make sure the worker is stopped
		List<WorkerThread> l = Arrays.asList(worker);
		stopWorkers(l);

		synchronized (workers) {
			workers.remove(worker);
		}
	}
	
	/**
	 * Adds all of the given workers to the worker list but does not start them.
	 * @param workers
	 */
	protected final void addWorkers( Collection<WorkerThread> workers ) {
		
		for (final Iterator<WorkerThread> iter = workers.iterator(); iter.hasNext();) {
			final WorkerThread worker = iter.next();
			addWorker( worker );
		}
		
	}

	/**
	 * Called by any portion of the tool who wishes to initiate a global shutdown.
	 */
	public static void signalShutdown() {
		shutdown = true;
		signalAllControllers();
	}

	/**
	 * Lazy-creation of a threadgroup. 
	 */
	public synchronized static ThreadGroup getWorkerThreadGroup() {
		
		if ( workerThreadGroup==null ) {
			workerThreadGroup = new ThreadGroup( "Workers" );
		}
		return workerThreadGroup;
		
	}

	/**
	 * Informs the control thread of the time the application started, for TLF mode. 
	 * @param time
	 */
	public void setStaticTime(long time) {
		staticStartTime = time;
	}

	public static boolean isShuttingDown() {
		return shutdown;
	}

	/**
	 * Encapsulation-busting access to workers list used by the statistics module.
	 */
	public ArrayList<WorkerThread> getWorkers() {
		return workers;
	}

	public int getRunningWorkers() {
		return runningWorkers;
	}
	
	
}
