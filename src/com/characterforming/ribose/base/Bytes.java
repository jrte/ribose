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

package com.characterforming.ribose.base;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Wraps an array of bytes
 * 
 * @author Kim Briggs
 */
public final class Bytes {
	public static Charset charset = Charset.defaultCharset();
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();
	private final byte[] bytes;
	private int hash;

	public Bytes(final byte[] bytes) {
		this.bytes = bytes;
		this.hash = 0;
	}
	
	public static String decode(final byte[] bytes) {
		return charset.decode(ByteBuffer.wrap(bytes).limit(bytes.length)).toString();
	}

	public static String decode(final byte[] bytes, final int length) {
		return charset.decode(ByteBuffer.wrap(bytes).limit(Math.min(length, bytes.length))).toString();
	}

	public static Bytes encode(final String chars) {
		return Bytes.encode(CharBuffer.wrap(chars.toCharArray()));
	}

	public static Bytes encode(final CharBuffer chars) {
		ByteBuffer buffer = charset.encode(chars);
		byte bytes[] = new byte[buffer.limit()];
		buffer.get(bytes, 0, bytes.length);
		return new Bytes(bytes);
	}
	
	public static Bytes getBytes(final byte[] from, int offset, int length) {
		assert from.length >= offset;
		int size = from.length - offset;
		byte bytes[] = new byte[size];
		if (size > 0) {
			System.arraycopy(from, offset, bytes, 0, size);
		}
		return new Bytes(bytes);
	}
	
	public int getLength() {
		int length = 0;
		while (length < bytes.length && this.bytes[length] != 0) {
			++length;
		}
		return length;
	}
	
	public byte[] getBytes() {
		return this.bytes;
	}

	@Override
	public String toString() {
		return Bytes.decode(this.getBytes(), this.getLength());
	}
	
	public String toHexString() {
    char[] hex = new char[2 * this.bytes.length];
    for (int j = 0; j < this.bytes.length && this.bytes[j] != 0; j++) {
      int k = this.bytes[j] & 0xFF;
      hex[j * 2] = HEX[k >> 4];
      hex[j * 2 + 1] = HEX[k & 0x0F];
    }
    assert hex[hex.length - 1] != 0;
    return new String(hex);
	}

	private int hash() {
		assert this.bytes != null;
		int h = 0;
		if (this.bytes != null) {
			h = this.bytes.length;
			for (final byte b : this.bytes) {
				h = h * 31 + b;
			}
		}
		if (h == 0) {
			h = -1;
		}
		return h;
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
		return other instanceof Bytes && Arrays.equals(((Bytes) other).bytes, this.bytes);
	}
}
