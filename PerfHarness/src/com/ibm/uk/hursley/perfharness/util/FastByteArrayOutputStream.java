/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

/*
 *  History:
 *  Date        ID, Company               Description
 *  ----------  -----------------------   ----------------------------------------------------------------------
 *  2006/12/00                            Release Candidate 2 
 */
package com.ibm.uk.hursley.perfharness.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A lighter version of the classic BAOS which is unsynchronised and
 * pass-by-reference. Make sure to expect that the returned byte array is longer
 * than its valid content! Also ensure that the object and buffer array are
 * released before anything re-accesses the object.
 * 
 */
public final class FastByteArrayOutputStream extends OutputStream {

	// Why bother with getters in this case!
	private final ByteArray data;
	
	public FastByteArrayOutputStream( int initialSize ) {
		data = new ByteArray( initialSize );
	}
	
	@Override
	public void write(int b) throws IOException {

		data.ensureCapacity( data.length+1 );
		data.buf[data.length++]=(byte)b;
		
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		
		// Even though we don't currently change the length before the
		// arraycopy, this line may save a defect being raised in future.
		int oldLength = data.length;
		
		data.ensureCapacity( data.length + len );
		System.arraycopy(b, off, data.buf, oldLength, len);
		data.length += len;
		
	}
	
	/**
	 * Will return more bytes than the actual content. Make sure to use
	 * {@link #size()} in conjunction with this data.
	 * 
	 * @return Pass-by-ref of internal buffer.
	 */
    public ByteArray getFastByteArray() {
    	return data;
    }
    
    /**
     * How many bytes of the current buffer have valid content.
     */
    public int size() {
    	return data.length;
    }
    
    /**
	 * Reset this object. Internal array is never shortened, to do this you must
	 * delete and recreate the object.
	 */
    public void reset() {
    	data.length = 0;
    }    

}
