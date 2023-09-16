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
 * Wraps an array of int
 * 
 * @author Kim Briggs
 */
final class Ints {
	private final int[] data;
	private int hash;

	public Ints(final int[] ints) {
		this.data = ints;
		this.hash = 0;
	}

	public int[] getData() {
		return this.data;
	}

	public int getInt(final int index) {
		return this.data[index];
	}

	private int hash() {
		int h = this.data.length;
		for (final int j : this.data) {
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
		return other == this
		|| other instanceof Ints o
		&& o.hashCode() == this.hashCode()
		&& Arrays.equals(o.data, this.data);
	}
}
