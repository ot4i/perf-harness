/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.ibm.uk.hursley.perfharness.util.SelectableConsoleHandler;
import com.ibm.uk.hursley.perfharness.util.ThreadFormatter;

/**
 * Sets up a JDK logger framework
 * 
 */
public class Log {

	@SuppressWarnings("unused")
	private static final String c = Copyright.COPYRIGHT;
	
	public final static Logger logger;
	protected static Handler loggerHandler = null; 
	protected static Level loggerLevel = null;
	protected static LogThreadFormatter logFormatter = null;

    private static boolean errorsReported = false;
    
    static {
    	logger = Logger.getLogger( "com.ibm.uk.hursley.perfharness" );
    	
		// Only set up the logger if it does not exist already. This is
		// primarily here for the case where multiple copies of this class are
		// loaded via different classloaders. LogManager (as a java.util class)
		// will always be shared at the highest level so we do not want to add
		// multiple copies of the same handler.
    	if ( logger.getHandlers().length==0 ) {
			loggerHandler = new SelectableConsoleHandler(System.out);
			loggerHandler.setFormatter(logFormatter = new LogThreadFormatter());		
			logger.setUseParentHandlers( false );
			logger.addHandler( loggerHandler );
			logger.setLevel( Level.INFO );
			loggerHandler.setLevel( Level.INFO );
    	} else {
    		// Even if we do not own the active LogHandler, we need to get error
			// notifications specific to this instance.
    		loggerHandler = new NotifyingLogHandler();
    		loggerHandler.setLevel( Level.ALL );
    		logger.addHandler( loggerHandler );
    		loggerHandler=null;
    	}
    }
    
	/**
	 * Register our presence and look up any required parameters for this class. 
	 * @see Config#registerSelf(Class)
	 */
    public static void registerConfig() {	
		
		// Register ourselves.
		Config.registerSelf( Log.class );

		// Now we can use the Log parameters
		if (loggerHandler != null) {
			loggerHandler.setFormatter(logFormatter = new LogThreadFormatter());

			logFormatter.setSquashExceptions(!Config.parms.getBoolean("st"));

			try {
				loggerLevel = Level.parse(Config.parms.getString("vo"));
				logger.setLevel(loggerLevel);
				loggerHandler.setLevel(loggerLevel);
			} catch (IllegalArgumentException e) {
				Config.logger
						.log(Level.WARNING, "-vo is out of valid range", e);
			}
		} // end if loggerHandler!=null

	}
    
    /**
	 * java.util.logging closes its loggers in a shutdown handler. This tool
	 * finds it prefereable NOT to do that and therefore has to restart the
	 * logging facility when it tries to shutdown.
	 */
    public static void reviveLogger() {
		
		if (loggerHandler != null) {
			synchronized (loggerHandler) {

				// recreate missing handler if needed
				if (logger.getHandlers().length == 0) {
					logger.addHandler( loggerHandler );
				}
				logger.setLevel( loggerLevel );

			} // end sync
		} // end if
		
    }
	
    /**
     * Does not print any output.
     */
    public static final class NotifyingLogHandler extends Handler {

    	@Override
    	public void close() throws SecurityException {
    	}

    	@Override
    	public void flush() {
    	}

    	@Override
    	public void publish(LogRecord record) {
    		
    		if (!isLoggable(record)) {
    		    return;
    		}
    		if ( record.getLevel().intValue()>=Level.SEVERE.intValue() ) {
    			// Note that ALL loghandlers will get an error reported when any
				// one of them fails. This is better than none of them!
				errorsReported = true;
			}
    		
    	}
    	
    }
    
	public static final class LogThreadFormatter extends ThreadFormatter {
		private boolean squashExceptions = false;
		public String format(LogRecord record) {
			if ( record.getLevel().intValue()>=Level.SEVERE.intValue() ) {
				errorsReported = true;
			}
			if ( squashExceptions ) {
				record.setThrown(null);
			}
			return super.format(record);
		}
		public void setSquashExceptions( boolean squash ) {
			this.squashExceptions = squash;
		}
	}

    /**
     * @return True if anything has been logged using the SEVERE constant.
     */
	public static boolean errorsReported() {
		return errorsReported;
	}
	
}
