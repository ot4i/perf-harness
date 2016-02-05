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

/**
 * A lighter version of the classic BAOS which is unsynchronised and
 * pass-by-reference. Also ensure that the object and buffer array are
 * released before anything re-accesses the object.
 * 
 */
public class FastByteArrayInputStream extends java.io.InputStream {

	private ByteArray data;
	private int position;
	private int mark;
	
	public FastByteArrayInputStream( ByteArray byteArray ) {
		data = byteArray;
		position = 0;
	}
	
	@Override
    public int read() {
		return (position < data.length) ? (data.buf[position++] & 0xff) : -1;
	}
	
    public int read(byte b[], int off, int len) {
 
    	if (position >= data.length) {
			return -1;
		}
		if (position + len > data.length) {
			len = data.length - position;
		}
		if (len <= 0) {
			return 0;
		}
		System.arraycopy(data.buf, position, b, off, len);
		position += len;
		return len;
		
	}
	
    public long skip(long n) {
		if (position + n > data.length) {
			n = data.length - position;
		}
		if (n < 0) {
			return 0;
		}
		position += n;
		return n;
	}
	
	public int available() {
		return data.length - position;
	}
	
	public boolean markSupported() {
		return true;
	}
	
	public void mark(int readAheadLimit) {
		mark = position;
	}
	
	public void reset() {
		position = mark;
	}

}
