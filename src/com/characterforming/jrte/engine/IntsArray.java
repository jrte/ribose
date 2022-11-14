/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.util.Arrays;

/**
 * Wraps an array of Ints
 * 
 * @author Kim Briggs
 */
final class IntsArray {
	private final Ints[] intsArray;
	private int hash;

	public IntsArray(final int[][] intsArray) {
		this.intsArray = new Ints[intsArray.length];
		for (int i = 0; i < this.intsArray.length; i++) {
			this.intsArray[i] = new Ints(intsArray[i]);
		}
		this.hash = 0;
	}

	int[][] getInts() {
		final int[][] ints = new int[this.intsArray.length][];
		for (int i = 0; i < ints.length; i++) {
			ints[i] = this.intsArray[i].getInts();
		}
		return ints;
	}

	int[] getInts(final int index) {
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
