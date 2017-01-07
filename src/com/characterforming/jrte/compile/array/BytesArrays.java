/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile.array;

import java.util.Arrays;

/**
 * Wraps an array of BytesArray
 * 
 * @author kb
 */
public final class BytesArrays {
	private final BytesArray[] bytesArrays;
	private int hash;

	public BytesArrays(final byte[][][] bytesArrays) {
		this.bytesArrays = new BytesArray[bytesArrays.length];
		for (int i = 0; i < this.bytesArrays.length; i++) {
			this.bytesArrays[i] = new BytesArray(bytesArrays[i]);
		}
		this.hash = 0;
	}

	public byte[][][] getBytesArrays() {
		final byte[][][] bytes = new byte[this.bytesArrays.length][][];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = this.bytesArrays[i].getBytes();
		}
		return bytes;
	}

	public byte[][] getBytesArray(final int index) {
		return this.bytesArrays[index].getBytes();
	}

	@Override
	public int hashCode() {
		if (this.hash == 0) {
			this.hash = this.hash();
		}
		return this.hash;
	}

	private int hash() {
		int h = this.bytesArrays.length;
		for (final BytesArray bytesArray : this.bytesArrays) {
			h = h * 31 + bytesArray.hashCode();
		}
		return h != 0 ? h : -1;
	}

	@Override
	public boolean equals(final Object other) {
		return other == this || other != null && other instanceof BytesArrays && Arrays.equals(((BytesArrays) other).bytesArrays, this.bytesArrays);
	}
}
