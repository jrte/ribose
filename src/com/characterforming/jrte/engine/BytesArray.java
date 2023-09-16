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

import com.characterforming.ribose.base.Bytes;

/**
 * Wraps an array of Bytes
 * 
 * @author Kim Briggs
 */
final class BytesArray {
	private final Bytes[] data;
	private int hash;

	BytesArray(final byte[][] bytesArray) {
		this.data = new Bytes[bytesArray.length];
		for (int i = 0; i < this.data.length; i++) {
			this.data[i] = new Bytes(bytesArray[i]);
		}
		this.hash = 0;
	}

	byte[][] getBytes() {
		final byte[][] bytes = new byte[this.data.length][];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = this.data[i].bytes();
		}
		return bytes;
	}

	byte[] getBytes(final int index) {
		return this.data[index].bytes();
	}

	private int hash() {
		int h = this.data.length;
		for (final Bytes element : this.data) {
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
		return other == this
		|| other instanceof BytesArray o && Arrays.equals(o.data, this.data);
	}
}
