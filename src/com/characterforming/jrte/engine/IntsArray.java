/***
 * Ribose is a recursive transduction engine for Java
 * 
 * Copyright (C) 2011,2022 Kim Briggs
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program (LICENSE-gpl-3.0). If not, see
 * <http://www.gnu.org/licenses/#GPL>.
 */

package com.characterforming.jrte.engine;

import java.util.Arrays;

/**
 * Wraps an array of Ints
 * 
 * @author Kim Briggs
 */
final class IntsArray {
	private final Ints[] data;
	private int hash;

	public IntsArray(final int[][] intsArray) {
		this.data = new Ints[intsArray.length];
		for (int i = 0; i < this.data.length; i++) {
			this.data[i] = new Ints(intsArray[i]);
		}
		this.hash = 0;
	}

	int[][] getInts() {
		final int[][] ints = new int[this.data.length][];
		for (int i = 0; i < ints.length; i++) {
			ints[i] = this.data[i].getData();
		}
		return ints;
	}

	int[] getInts(final int index) {
		return this.data[index].getData();
	}

	private int hash() {
		int h = this.data.length;
		for (final Ints element : this.data) {
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
		return other == this || other instanceof IntsArray o
		&& o.hashCode() == this.hashCode()
		&& Arrays.equals(o.data, this.data);
	}
}
