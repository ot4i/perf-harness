package com.ibm.uk.hursley.perfharness;

import java.util.ArrayList;

/**
 * For WorkerThreads that can access (either publish or subscribe to) multiple destinations
 * @author fentono
 *
 */
public class MultiDestinationFactory implements DestinationFactory {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning
	
	protected static final int MODE_SINGLE = 0;
	protected static final int MODE_DIST = 1;
	protected static int mode = MODE_SINGLE;

	private static int destBase;
	private static int destMax;
	private static int destNumber; // per thread
	private static int destStart;
	private static String destPrefix;
	
	private static int nextDest;
	
	public static void registerConfig() {
		
		Config.registerSelf( MultiDestinationFactory.class );
	
		if ( ! Config.isInvalid() ) {
			destBase = Config.parms.getInt( "db" );
			destMax = Config.parms.getInt( "dx" );
			destNumber = Config.parms.getInt( "dn" );
			destStart = Config.parms.getInt( "ds" );
			
			destPrefix = Config.parms.getString("d");
		
			if (destNumber > 0) {
				
				mode = MODE_DIST;
				
				if (destBase < 0) {
					destBase = 1;
					Config.logger.warning( "Destination base is negative." );
				}
				
				if (destStart < destBase) {
					destStart = destBase;
					nextDest = destStart;
				} else {
					nextDest = destStart;
					if ( destStart>destMax ) {
						Config.logger.warning("-ds must be within the specified bounds of -db and -dx");
					}
				}
				
				if (destMax > 0 && destBase > destMax) {
					destMax = destBase;
					Config.logger.warning("-dx must be >= -db");
				}
		
			}
		}	
	}
	
	public String[] generateDestinations( int id ) {
		ArrayList<String> dests = new ArrayList<String>();
		
		for (int i = 0; i < destNumber; i++) {
			dests.add(generateDestination(id));
		}
		
		return (String[]) dests.toArray();
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
