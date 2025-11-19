/*
 * Created on 02-Nov-2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author icraggs
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
import java.util.logging.Level;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.ControlThread;
import com.ibm.uk.hursley.perfharness.Log;

/**
 * Start the system with the specified arguments, this is just a wrapper onto ControlThread
 * This class is actually required as ControlThread cannot have it's own main method
 * due to the static-access-based access to Config.register()
 * @author Marc Carter, IBM
 */
public final class MQTTPerfHarness {

	/**
	 * Earliest recorded time in the JVMs lifecycle.  May be useful for certain metrics. 
	 */
	static final long time = System.currentTimeMillis();
	
	/**
	 * Main method 
	 * @param args The commandline.
	 */
	public static void main(String[] args) {
		System.out.println("PAUL");
		Config.init( args, ControlThread.class );
		
		ControlThread ct;
		try {
			ct = ControlThread.getInstance();
			ct.start();    	
		} catch (Exception e) {
			Log.logger.log( Level.SEVERE, "Uncaught exception.", e );
		}
		
	}

}