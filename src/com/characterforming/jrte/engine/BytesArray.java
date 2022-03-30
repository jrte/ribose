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
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.util.Arrays;

import com.characterforming.jrte.base.Bytes;

/**
 * Wraps an array of Bytes
 * 
 * @author Kim Briggs
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

	private int hash() {
		int h = this.bytesArray.length;
		for (final Bytes element : this.bytesArray) {
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
		return other == this || other != null && other instanceof BytesArray && Arrays.equals(((BytesArray) other).bytesArray, this.bytesArray);
	}
}
