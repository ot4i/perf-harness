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
import java.io.InputStream;

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
	public static int readUntil(InputStream s, byte[] symbol, byte[] buf, int offset, int length) throws IOException {
		int nread = 0;
		int nmatched = 0;
		while ((nread < length) && (nmatched < symbol.length)) {
			final int c = s.read();
			if (c < 0)
				// End of stream
				return nread;

			final byte b = (byte)c;
			buf[offset + nread] = b;
			if (b == symbol[nmatched])
				++nmatched;
			else
				nmatched = 0;

			++nread;
		}
		return nread;
	}
	public static boolean endsWith(byte[] b, int offset, int length, byte[] pattern) {
		if (length < pattern.length)
			return false;
		for (int k = 0, bo = offset + length - pattern.length; k < pattern.length; k++, bo++)
			if (b[bo] != pattern[k])
				return false;
		return true;
	}
}
