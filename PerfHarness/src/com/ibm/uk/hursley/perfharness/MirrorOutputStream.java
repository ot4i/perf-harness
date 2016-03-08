package com.ibm.uk.hursley.perfharness;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;

public class MirrorOutputStream extends OutputStream {
	public MirrorOutputStream() {
		l = new LinkedList<OutputStream>();
	}
	public MirrorOutputStream(OutputStream os) {
		this();
		l.add(os);
	}
	public MirrorOutputStream(OutputStream os0, OutputStream os1) {
		this();
		l.add(os0);
		l.add(os1);
	}
	public void add(OutputStream os) {
		l.add(os);
	}
	@Override
	public void close() throws IOException {
		Iterator<OutputStream> i = l.iterator();
		while (i.hasNext())
			i.next().close();
	}
	@Override
	public void flush() throws IOException {
		Iterator<OutputStream> i = l.iterator();
		while (i.hasNext())
			i.next().flush();
	}
	@Override
	public void write(byte[] b) throws IOException {
		Iterator<OutputStream> i = l.iterator();
		while (i.hasNext())
			i.next().write(b);
	}
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		Iterator<OutputStream> i = l.iterator();
		while (i.hasNext())
			i.next().write(b, off, len);
	}
	@Override
	public void write(int b) throws IOException {
		Iterator<OutputStream> i = l.iterator();
		while (i.hasNext())
			i.next().write(b);
	}
	private final LinkedList<OutputStream> l;
}
