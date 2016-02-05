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

import java.io.*;
import java.util.concurrent.*;

/**
 * <p>
 * Uses multiple threads to encapsulate and parallelise the ugly act of
 * serialising and deserialising the given object. It uses a ThreadPoolExecutor
 * to avoid having to recreate threads if called multiple times.
 * </p>
 * <p>
 * It is assumed that this is to be used when objects are known to be large in
 * terms of memory.
 * </p>
 * 
 */
public class ObjectCopier<E> {

	private static final boolean USE_DIRECT = true;

	private static final boolean DEBUG = false;

	private ExecutorService exec;

	private int BUFFERSIZE;
	private Exchanger<ByteArray> exchanger;
	private ByteArray initialEmptyBuffer; 
	private ByteArray initialFullBuffer;

	// statistics

	private long deserialiserWaitNanos;
	private long serialiserWaitNanos;
	private int serialisedBytes;
	private int arrayCopiedBytes;
	private int exchanges;
	private int directExchanges;

	/**
	 * Creates an ObjectCopier with a default 20k buffer and two threads.
	 */
	public ObjectCopier() {

		this(20480);

	}

	/**
	 * Creates an ObjectCopier with two threads.
	 * 
	 * @param buffersize
	 *            Two internal swap buffers are created of this size.
	 */
	public ObjectCopier(int buffersize) {

		this(Executors.newFixedThreadPool(2), buffersize);

	}

	/**
	 * Creates an ObjectCopier with the given ExecutionService. This service
	 * must be multi-threaded or ObjectCopier will deadlock.
	 * 
	 * @param exec
	 *            A multi-threaded pool service
	 * @param buffersize
	 *            Two internal swap buffers are created of this size.
	 */
	public ObjectCopier(ExecutorService exec, int buffersize) {

		this.exec = exec;
		BUFFERSIZE = buffersize;
		exchanger = new Exchanger<ByteArray>();
		initialEmptyBuffer = new ByteArray(BUFFERSIZE);
		initialFullBuffer = new ByteArray(BUFFERSIZE);
		
		reset();

	}

	public void reset() {

		deserialiserWaitNanos = 0;
		serialiserWaitNanos = 0;
		serialisedBytes = 0;
		arrayCopiedBytes = 0;
		exchanges = 0;
		directExchanges = 0;

		initialEmptyBuffer.length = 0;
		initialFullBuffer.length = 0;

	}

	class Deserialser extends InputStream implements Callable<E> {

		private ByteArray currentBuffer = initialFullBuffer;
		private int readPointer = 0;

		@SuppressWarnings("unchecked")
		public E call() throws Exception {
			ObjectInputStream ois = null;
			try {
				ois = new ObjectInputStream(this);
				return (E) ois.readObject();
			} finally {
				if (ois != null) {
					ois.close();
				}
			}
		}

		@Override
		public int read() throws IOException {

			int remaining = currentBuffer.length - readPointer;
			while (remaining == 0) {
				exchange();
				remaining = currentBuffer.length - readPointer;
			}

			if (DEBUG)
				System.out.print("r");
			return currentBuffer.buf[readPointer++];

		}

		@Override
		public void close() throws IOException {
			if (DEBUG)
				System.out.println("\ndeserialiser closed");
			super.close();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {

			int remaining = currentBuffer.length - readPointer;
			while (remaining == 0) {
				exchange();
				remaining = currentBuffer.length - readPointer;
			}

			if (len <= remaining) {
				// we have more than requested
				System.arraycopy(currentBuffer.buf, readPointer, b, off, len);
				readPointer += len;
				if (DEBUG)
					System.out.print(len >= 10 ? "R" : "r");
				return len;
			} else {
				// They have a bigger buffer than us, we can only provide a
				// partial fulfilment.
				System.arraycopy(currentBuffer.buf, readPointer, b, off,
						remaining);
				// We could exchange here, but choose to defer it until the next
				// call (after all, it may never happen)
				readPointer += remaining;
				if (DEBUG)
					System.out.print(remaining >= 10 ? "R" : "r");
				return remaining;
			}

		}

		@Override
		public int available() throws IOException {
			return currentBuffer.length;
		}

		private void exchange() throws IOException {

			try {

				if (DEBUG)
					System.out.println("\ndeserialiser waiting for bytes");
				currentBuffer.length = 0;

				long start = System.nanoTime();
				currentBuffer = exchanger.exchange(currentBuffer);
				deserialiserWaitNanos += System.nanoTime() - start;

				readPointer = 0;
				exchanges++;

				if (DEBUG)
					System.out.println("\ndeserialiser received "
							+ currentBuffer.length + " bytes");

			} catch (InterruptedException e) {
				throw new IOException("Copy (read) Interrupted.");
			}

		}

	}

	class Serialiser extends OutputStream implements Callable<E> {

		private ByteArray currentBuffer = initialEmptyBuffer;
		private E src = null;
		private ByteArray direct = new ByteArray();

		public Serialiser(E src) {
			this.src = src;
		}

		public E call() throws Exception {

			ObjectOutputStream oos = null;

			// Don't hold a reference to the src object
			Object intSrc = src;
			src = null;

			try {
				oos = new ObjectOutputStream(this);
				oos.writeObject(intSrc);
				return null;
			} finally {
				if (oos != null) {
					oos.flush();
					oos.close();
				}
			}
		}

		@Override
		public void write(int b) throws IOException {
			if (DEBUG)
				System.out.print("w");
			currentBuffer.buf[currentBuffer.length++] = (byte) b;
			arrayCopiedBytes++;
			if (currentBuffer.length == currentBuffer.buf.length) {
				exchange(currentBuffer);
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {

			while (len > 0) {
				int remaining = BUFFERSIZE - currentBuffer.length;
				if (len < remaining) {
					// All fits into the buffer
					if (DEBUG)
						System.out.print(len >= 10 ? "W" : "w");
					System.arraycopy(b, off, currentBuffer.buf,
							currentBuffer.length, len);
					currentBuffer.length += len;
					arrayCopiedBytes += len;
					len = 0;
				} else {
					// Too much for our buffer so may need to make several
					// exchanges

					if (USE_DIRECT && (len - remaining) >= BUFFERSIZE) {

						// CHOICE : pass serialiser's byte array directly as it
						// will save on copy/exchange loops (exch=3,copy=0)

						direct.buf = b;
						direct.length = len;
						// send what we already have
						if (currentBuffer.length >= 0) {
							exchange(currentBuffer);
						}
						// The buffer which would have been sent to the
						// counterparty. After two exchanges we should have it
						// back again.
						ByteArray myBuf = currentBuffer;
						// Send serialiser's internal array
						exchange(direct);
						directExchanges++;
						// At this point we hold both internal buffers, both are
						// empty. To get back the direct buffer we must give the
						// reader a buffer we know to be empty meaning he will
						// block immediately.
						exchange(currentBuffer);
						currentBuffer = myBuf;
						direct.buf = null;
						len = 0;

					} else {

						// CHOICE : copy data to unblock the serialiser
						// (exch=1,copy=2)

						System.arraycopy(b, off, currentBuffer.buf,
								currentBuffer.length, remaining);
						currentBuffer.length += remaining;
						arrayCopiedBytes += remaining;
						if (DEBUG)
							System.out.print(remaining >= 10 ? "W" : "w");
						off += remaining;
						len -= remaining;
						exchange(currentBuffer);

					}

				}
			} // end while

		}

		@Override
		public void flush() throws IOException {
			if (currentBuffer.length > 0) {
				exchange(currentBuffer);
			}
		}

		@Override
		public void close() throws IOException {
			if (DEBUG)
				System.out.println("\nserialiser closed");
			flush();
		}

		private void exchange(ByteArray toSend) throws IOException {

			try {

				serialisedBytes += toSend.length;

				int oldBytes = toSend.length;
				if (DEBUG)
					System.out.println("\nserialiser waiting to send "
							+ oldBytes + " bytes");

				long start = System.nanoTime();
				currentBuffer = exchanger.exchange(toSend);
				serialiserWaitNanos += System.nanoTime() - start;

				if (DEBUG)
					System.out.println("\nserialiser sent " + oldBytes
							+ " bytes");

			} catch (InterruptedException e) {
				throw new IOException("Copy (write) Interrupted.");
			}

		}
	}

	/**
	 * Makes a copy of the given object. Note that only one caller at a time can
	 * be using this instance of ObjectCopier.
	 * 
	 * @param src
	 * @return
	 * @throws Exception
	 */
	public synchronized E copy(E src) throws Exception {

		reset();

		Future<E> serResult = exec.submit(new Serialiser(src));
		Future<E> desResult = exec.submit(new Deserialser());

		E result = null;

		try {
			serResult.get();
		} catch (Exception e) {
			throw new Exception("Copy failed in serialiser", e);
		}

		try {
			result = desResult.get();
		} catch (Exception e) {
			throw new Exception("Copy failed in deserialiser", e);
		}

		return result;

	}

}
