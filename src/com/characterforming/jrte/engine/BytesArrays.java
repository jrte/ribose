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

/**
 * Wraps an array of BytesArray
 * 
 * @author Kim Briggs
 */
final class BytesArrays {
	private final BytesArray[] bytesArrays;
	private int hash;

	BytesArrays(final byte[][][] bytesArrays) {
		this.bytesArrays = new BytesArray[bytesArrays.length];
		for (int i = 0; i < this.bytesArrays.length; i++) {
			this.bytesArrays[i] = new BytesArray(bytesArrays[i]);
		}
		this.hash = 0;
	}

	byte[][][] getBytesArrays() {
		final byte[][][] bytes = new byte[this.bytesArrays.length][][];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = this.bytesArrays[i].getBytes();
		}
		return bytes;
	}

	byte[][] getBytesArray(final int index) {
		return this.bytesArrays[index].getBytes();
	}

	private int hash() {
		int h = this.bytesArrays.length;
		for (final BytesArray bytesArray : this.bytesArrays) {
			h = h * 31 + bytesArray.hashCode();
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
		return other == this || other != null && other instanceof BytesArrays && Arrays.equals(((BytesArrays) other).bytesArrays, this.bytesArrays);
	}
}
