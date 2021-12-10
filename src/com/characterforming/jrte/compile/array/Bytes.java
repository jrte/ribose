/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile.array;

import java.util.Arrays;

/**
 * Wraps an array of byte
 * 
 * @author kb
 */
public final class Bytes {
	private final byte[] bytes;
	private int hash;

	public Bytes(final byte[] bytes) {
		this.bytes = bytes;
		this.hash = 0;
	}

	public byte[] getBytes() {
		return this.bytes;
	}

	private int hash() {
		int h = this.bytes.length;
		for (final byte b : this.bytes) {
			h = h * 31 + b;
		}
		return h != 0 ? h : -1;
	}

	@Override
	public int hashCode() {
		if (this.hash == 0) {
			this.hash = this.hash();
		}
		return this.hash;
	}

	@Override
	public boolean equals(final Object other) {
		return other == this || other != null && other instanceof Bytes && Arrays.equals(((Bytes) other).bytes, this.bytes);
	}
}
