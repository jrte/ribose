/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile.array;

import java.util.Arrays;

/**
 * Wraps an array of Bytes
 * 
 * @author kb
 */
public final class BytesArray {
	private final Bytes[] bytesArray;
	private int hash;

	public BytesArray(final byte[][] bytesArray) {
		this.bytesArray = new Bytes[bytesArray.length];
		for (int i = 0; i < this.bytesArray.length; i++) {
			this.bytesArray[i] = new Bytes(bytesArray[i]);
		}
		this.hash = 0;
	}

	public byte[][] getBytes() {
		final byte[][] bytes = new byte[this.bytesArray.length][];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = this.bytesArray[i].getBytes();
		}
		return bytes;
	}

	public byte[] getBytes(final int index) {
		return this.bytesArray[index].getBytes();
	}

	@Override
	public int hashCode() {
		if (this.hash == 0) {
			this.hash = this.hash();
		}
		return this.hash;
	}

	private int hash() {
		int h = this.bytesArray.length;
		for (final Bytes element : this.bytesArray) {
			h = h * 31 + element.hashCode();
		}
		return h != 0 ? h : -1;
	}

	@Override
	public boolean equals(final Object other) {
		return other == this || other != null && other instanceof BytesArray && Arrays.equals(((BytesArray) other).bytesArray, this.bytesArray);
	}
}
