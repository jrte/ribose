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

package com.characterforming.ribose.base;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Arrays;

/**
 * Wraps an immutable array of bytes. Ribose transductions operate in the {@code byte}
 * domain, and transduction input and outputs are represented as byte arrays.
 *
 * @author Kim Briggs
 */
public final class Bytes {
	/** A singleton empty byte array */
	public static final byte[] EMPTY_BYTES = new byte[] {};
	/** A singleton Bytes instance wrapping an empty byte array */
	public static final Bytes EMPTY = new Bytes(Bytes.EMPTY_BYTES);

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();
	private final byte[] data;
	private int hash;

	/**
	 * Constructor wraps an array of bytes
	 *
	 * @param bytes the data to wrap
	 */
	public Bytes(final byte[] bytes) {
		this.data = bytes;
		this.hash = 0;
	}

	/**
	 * Constructor copies and wraps a segment of an array of bytes
	 *
	 * @param from the source array
	 * @param offset the position in the source array to copy from
	 * @param length the number of bytes to copy
	 */
	public Bytes(final byte[] from, int offset, int length) {
		assert from.length >= offset && from.length >= (offset + length);
		offset = from.length >= offset ? offset : from.length;
		length = from.length >= (offset + length) ? length : from.length - offset;
		this.data = new byte[length];
		System.arraycopy(from, offset, data, 0, length);
		this.hash = 0;
	}

	/**
	 * Get the number of contained bytes.
	 *
	 * @return the number of contained bytes
	 */
	public int getLength() {
		return data.length;
	}

	/**
	 * Get the backing array for the contained bytes. This is a direct reference to the
	 * wrapped array, which may not be completely filled; use Bytes.getLength() to determine
	 * actual length of data. Sadly there is no way for Java to confer immutability to the
	 * returned value, so don't mess with the contents unless you think you know what you
	 * are going.
	 *
	 * @return the contained bytes
	 */
	public byte[] bytes() {
		return this.data;
	}

	/**
	 * Decode contents to a String.
	 *
	 * @return a Unicode string
	 */
	public String asString() {
		try {
			return Codec.decode(this.bytes(), this.getLength());
		} catch (CharacterCodingException e) {
			return this.toHexString();
		}
	}

	/**
	 * Lazy hash code evaluation.
	 *
	 * @return a hash based on the contents
	 */
	public int hashCode() {
		if (this.hash == 0) {
			this.hash = this.hash();
		}
		return this.hash;
	}

	/**
	 * Like toString(), but bytes not in the printable ASCII range [32..127) are represented
	 * in hexadecimal escape sequences {@code \xHH}.
	 *
	 * @return the hex string
	 */
	public String toHexString() {
		char[] hex = new char[4 * this.getLength()];
		int i = 0;
		for (int j = 0; j < this.getLength(); j++) {
			byte b = this.data[j];
			if (b >= 32) {
				hex[i++] = (char)b;
			} else {
				hex[i++] = '\\';
				hex[i++] = 'x';
				hex[i++] = HEX[b >>> 4];
				hex[i++] = HEX[b & 0x0F];
			}
		}
		return CharBuffer.wrap(hex, 0, i).toString();
	}

	/**
	 * Override
	 *
	 * @return decoded string
	 */
	public String toString() {
		return this.asString();
	}

	/**
	 * Test for equality of contents.
	 *
	 * @param other the other {@code Bytes} instance
	 * @return true if contents are identical
	 */
	@Override
	public boolean equals(final Object other) {
		return other instanceof Bytes o && Arrays.equals(o.data, this.data);
	}

	private int hash() {
		int h = 0;
		if (this.data != null) {
			h = this.data.length;
			for (final byte b : this.data) {
				h = h * 31 + b;
			}
		}
		if (h == 0) {
			h = -1;
		}
		return h;
	}
}
