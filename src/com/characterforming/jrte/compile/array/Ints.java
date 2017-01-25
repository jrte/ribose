/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile.array;

import java.util.Arrays;

/**
 * Wraps an array of int
 * 
 * @author kb
 */
public final class Ints {
	private final int[] ints;
	private int hash;

	public Ints(final int[] ints) {
		this.ints = ints;
		this.hash = 0;
	}

	public int[] getInts() {
		return this.ints;
	}

	public int getInt(final int index) {
		return this.ints[index];
	}

	private int hash() {
		int h = this.ints.length;
		for (final int j : this.ints) {
			h = h * 31 + j;
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
		return other == this || other != null && other instanceof Ints
				&& ((Ints) other).hashCode() == this.hashCode()
				&& Arrays.equals(((Ints) other).ints, this.ints);
	}
}
