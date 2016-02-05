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
package com.ibm.uk.hursley.perfharness.sequencing;

import java.util.Iterator;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Copyright;
import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.stats.BasicStats;

/**
 * An implemenation of the statistics module which reports additional sequencing
 * information.  Highly experimental.
 */
public class SequenceStats extends BasicStats {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT; // IGNORE compiler warning
	
	public static void registerConfig() {
		// static validation of parameters
		
		Config.registerSelf( SequenceStats.class );
		
	}	
	
	public SequenceStats(ControlThread parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}
	
	Sequence seq[] = null; 
	
	/**
	 * Reimplementation which also takes a copy of the sequence object in the workerthread.
	 * @see com.ibm.uk.hursley.perfharness.stats.Statistics#readValues(int[])
	 */
	protected int[] readValues( int[] values ) {
		
		synchronized( workers ) {
			
			// WE must synch to avoid throwing ConcurrentModificationException during startup
			int count = workers.size();
			if ( values==null || values.length!=count ) {
				// Reuse array where possible.
				values = new int[count];	
			}
			
			if ( seq==null || seq.length!=count ) {
				seq = new Sequence[count];
			}
			
			int i = 0;
			final Iterator<WorkerThread> iter = workers.iterator();
			while (iter.hasNext() && i<curr.length) {
				final WorkerThread worker = iter.next();
				if ( seq[i]==null ) {
					seq[i] = ((SequentialWorker)worker).getSequence();
				}
				values[i++] = worker.getIterations();
			}
			
		} // end sync
		
		return values;
		
	}	
	
	protected void makeStringBuffer( StringBuffer sb ) {
		
		super.makeStringBuffer(sb);
		int missing = 0;
		int ordering = 0;
		int duplicates = 0;
		int count = 0;
		int error = 0;
		int i=0;
		for( ; i<seq.length; i++ ) {
			missing += seq[i].getMissing();
			ordering += seq[i].getOrdering();
			duplicates += seq[i].getDuplicates();
			count += seq[i].getCount();
			error += seq[i].getErrors();
		}
		sb.append(",miss=").append(missing);
		sb.append(",ord=").append(numberFormat.format((double) ordering / i));
		sb.append(",dup=").append(duplicates);
		sb.append(",err=").append(error);
		sb.append(",cnt=").append(count);
		
	}
	
}
