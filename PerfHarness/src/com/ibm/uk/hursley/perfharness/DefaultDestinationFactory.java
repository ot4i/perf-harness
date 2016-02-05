/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness;

import java.util.Random;

/**
 * This handles addressing of multiple destinations for pub/sub and point-to-point domains.  It should be
 * noted these options only control the *names* given to destinations.  Telling the
 * tool <code>-d TOPIC</code> does not make it use pub/sub (<code>-tc jms.r11.Publisher -d TOPIC</code> does that).
 */
public class DefaultDestinationFactory implements DestinationFactory {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	
	protected static final int MODE_SINGLE = 0;
	protected static final int MODE_DIST = 1;
	protected static final int MODE_RANDOM = 2;
	protected static int mode = MODE_SINGLE;

	private static int destBase;
	private static int destMax;
	private static int destNumber; 
	private static int destRandomRange; 
	private static String destPrefix;
	
	private static int nextDest; 
	
	// random number generator used when 'dr' == true 
	private static Random randomGenerator = new Random();

	/**
	 * Validate destination parameters are logical (mostly those looking like -d?).
	 */
	public static void registerConfig() {
		
		Config.registerSelf( DefaultDestinationFactory.class );
		
		if ( ! Config.isInvalid() ) {
			destBase = Config.parms.getInt( "db" );
			destMax = Config.parms.getInt( "dx" );
			destNumber = Config.parms.getInt( "dn" );
			
			destPrefix = Config.parms.getString("d");
			
	
			if ( destBase>0 && destMax>0 && destNumber>0 ) {
				
				// Special case: if *all* elements are set we use -dn as the
				// start point in the loop
				mode = MODE_DIST;
				nextDest = destNumber;
				destNumber = destMax - destBase + 1;
				
				if ( destNumber<destBase || destNumber>destMax ) {
					Config.logger.warning("-dn must be within the specified bounds of -db and -dx");
				}
				
			} else if ( destBase>0 || destMax>0 || destNumber>0 ) {
				// If we have specified any the elements ...
				
				mode = MODE_DIST;
				
				if ( destBase==0 ) { 
					// we need to calculate the base
					if ( destMax>0 && destNumber>0 ) {
						destBase = destMax - destNumber + 1;
					} else {
						destBase = 1;
					}
				}
	
				// We know topicBase is set...
				nextDest = destBase;
				
				if ( destMax==0 ) {
					// we need to calculate the max
					if ( destNumber>0 ) {
						destMax = destNumber + destBase - 1;
					} else {
						// There is no max !  This implies an ever increasing count
					}
				}
				
				if ( destNumber==0 ) {
					// we need to set topicNumber
					if ( destMax>0 ) {
						destNumber = destMax - destBase + 1;
					} else {
						// There is no max!
					}
				}
				
			} // end if any multi-destination settings 
			
			// enforce destination constraints
			if ( destBase<0 || (destMax!=0 && destMax<destBase) ) {
				Config.logger.warning( "Destination range is negative." );
			}
			
			// Check if we have been asked for random destination generation
			if ( Config.parms.getBoolean("dr" ) == true  )
			{
				mode = MODE_RANDOM;
				if(destMax==0 ){
					Config.logger.warning( "-dx and -dn cannot both be 0 in random mode" );
				}
				destRandomRange = destMax-destBase + 1;
			}
		}		
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.DestinationFactory#generateDestination(int)
	 */
	public String generateDestination( int id ) {
		
		String ret=null;
		switch ( mode ) {
			case MODE_SINGLE:
				ret=destPrefix;
				break;
			case MODE_DIST:
				StringBuilder sb = new StringBuilder( destPrefix );
				ret=sb.append( generateDestinationID( id ) ).toString();
				break;
			case MODE_RANDOM:
				StringBuilder rsb = new StringBuilder( destPrefix );
				// generates a pseudo-random integer in range 0 
				// to destRandomRange-1 inclusive
				ret=rsb.append( randomGenerator.nextInt(destRandomRange) + destBase ).toString();
				break;				
		} // end switch
		return ret;
		
	}


	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.DestinationFactory#generateDestinationID(int)
	 */
	public int generateDestinationID( int id ) {
		
		int num;
		switch ( mode ) {
			case MODE_DIST :
				synchronized (DefaultDestinationFactory.class) {
					num = nextDest++;
					// Note loose comparison here allows Max=0 to
					// leave an infinite sequence.
					if (nextDest == (destMax + 1)) {
						nextDest = destBase;
					}
				}
				return num;
			default :
				return -1;
		} // end case
		
	}
	
}
