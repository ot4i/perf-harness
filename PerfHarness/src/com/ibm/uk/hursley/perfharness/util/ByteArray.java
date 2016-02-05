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
 * Expandable byte[]
 * 
 */
public final class ByteArray {

	public int length;
	public byte[] buf;

	public ByteArray() {
		// No data set, must be initialised externally (implies reuse of this
		// container object)
	}

	public ByteArray(int capacity) {
		buf = new byte[capacity];
		this.length = 0;
	}

	public ByteArray(byte[] bytes) {
		this.buf = bytes;
		this.length = bytes.length;
	}

	public void ensureCapacity(int size) {

		if (buf.length < size) {
			byte[] newbuf = new byte[Math.max(size, buf.length * 2)];
			System.arraycopy(buf, 0, newbuf, 0, buf.length);
			buf = newbuf;
		}

	}

	public void trimToSize() {

	}

	public static String toHexString(byte[] a) {
		return toHexString(a, 0, a.length);
	}
	public static String toHexString(byte[] a, int start, int length) {
		final StringBuilder sb = new StringBuilder();
		for (int i = start; i < length; i++)
			sb.append(String.format("%02X", a[i]));
		return sb.toString();
	}
}
