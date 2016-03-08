package com.ibm.uk.hursley.perfharness;

import java.io.IOException;
import java.io.OutputStream;

public class HeadOutputStream extends OutputStream {
	public HeadOutputStream(byte[] b) {
		ofs = 0;
		overflow = false;
		buf = b;
	}
	public int getOffset() {
		return ofs;
	}
	public boolean getOverflow() {
		return overflow;
	}
	@Override
	public void close() throws IOException {
	}
	@Override
	public void flush() throws IOException {
	}
	@Override
	public void write(byte[] b) throws IOException {
		if (ofs < buf.length) {
			final int n = Math.min(b.length, buf.length - ofs);
			System.arraycopy(b, 0, buf, ofs, n);
			ofs += n;
		}
	}
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (ofs < buf.length) {
			final int n = Math.min(Math.min(b.length, len), buf.length - ofs);
			System.arraycopy(b, off, buf, ofs, n);
			ofs += n;
		}
	}
	@Override
	public void write(int b) throws IOException {
		if (ofs < buf.length)
			buf[ofs++] = (byte)b;
	}
	private int ofs;
	private boolean overflow;
	private final byte[] buf;
}
