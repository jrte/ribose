/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile.array;

import java.util.Arrays;

/**
 * Wraps an array of Ints
 * 
 * @author kb
 */
public final class IntsArray {
	private final Ints[] intsArray;
	private int hash;

	public IntsArray(final int[][] intsArray) {
		this.intsArray = new Ints[intsArray.length];
		for (int i = 0; i < this.intsArray.length; i++) {
			this.intsArray[i] = new Ints(intsArray[i]);
		}
		this.hash = 0;
	}

	public int[][] getInts() {
		final int[][] ints = new int[this.intsArray.length][];
		for (int i = 0; i < ints.length; i++) {
			ints[i] = this.intsArray[i].getInts();
		}
		return ints;
	}

	public int[] getInts(final int index) {
		return this.intsArray[index].getInts();
	}

	private int hash() {
		int h = this.intsArray.length;
		for (final Ints element : this.intsArray) {
			h = h * 31 + element.hashCode();
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
		return other == this || other != null && other instanceof IntsArray
				&& ((IntsArray) other).hashCode() == this.hashCode()
				&& Arrays.equals(((IntsArray) other).intsArray, this.intsArray);
	}
}
