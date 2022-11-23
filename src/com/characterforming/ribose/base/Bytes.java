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

package com.characterforming.ribose.base;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;

/**
 * Wraps an array of bytes
 * 
 * @author Kim Briggs
 */
public final class Bytes {
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();
	private static final char[] EMPTY_CHARS = new char[] { };
	private final byte[] bytes;
	private int hash;

	/**
	 * Constructor wraps an array of bytes
	 * 
	 * @param bytes The data to wrap
	 */
	public Bytes(final byte[] bytes) {
		this.bytes = bytes;
		this.hash = 0;
	}
	
	/**
	 * Constructor copies and wraps an segment of an array of bytes
	 * 
	 * @param from The source array
	 * @param offset The position in the source array to copy from
	 * @param length The number of bytes to copy
	 */
	public Bytes(final byte[] from, int offset, int length) {
		assert from.length >= offset && from.length >= (offset + length);
		offset = from.length >= offset ? offset : from.length;
		length = from.length >= (offset + length) ? length : from.length - offset;
		this.bytes = new byte[length];
		System.arraycopy(from, offset, bytes, 0, length);
		this.hash = 0;
	}

	/**
	 * Decode UTF-8 bytes to a String.
	 * 
	 * @param decoder The decoder to use
	 * @param bytes The bytes to decode
	 * @param length The number of bytes to decode
	 * @return a CharBuffer containing the decoded text
	 */
	public static CharBuffer decode(CharsetDecoder decoder, final byte[] bytes, final int length) {
		assert 0 <= length && length <= bytes.length;
		int size = Math.max(Math.min(length, bytes.length), 0);
		try {
			return decoder.decode(ByteBuffer.wrap(bytes, 0, size));
		} catch (CharacterCodingException e) {
			System.err.println("NamedValue.decode(CharsetDecoder, byte[], int, int): " + e.getMessage());
			assert false;
		}
		return CharBuffer.wrap(Bytes.EMPTY_CHARS);
	}

	/**
	 * Encode a String.
	 * 
	 * @param encoder the encoder to use
	 * @param chars The string to encode
	 * @return the encoded Bytes
	 */
	public static Bytes encode(CharsetEncoder encoder, final String chars) {
		return Bytes.encode(encoder, CharBuffer.wrap(chars.toCharArray()));
	}

	/**
	 * Encode a CharBuffer.
	 * 
	 * @param encoder the encoder to use
	 * @param chars The CharBuffer to encode
	 * @return the encoded Bytes
	 */
	public static Bytes encode(CharsetEncoder encoder, final CharBuffer chars) {
		Bytes bytes = null;
		try {
			ByteBuffer buffer = encoder.encode(chars);
			byte b[] = new byte[buffer.limit()];
			buffer.get(b, 0, b.length);
			bytes = new Bytes(b);
		} catch (CharacterCodingException e) {
			System.err.println("NamedValue.encode(CharsetEncoder,CharBuffer): " + e.getMessage());
			assert false;
		}
		return bytes;
	}
	
	/**
	 * Get the number of contained bytes.
	 * 
	 * @return the number of contained bytes
	 */
	public int getLength() {
		int length = 0;
		while (length < bytes.length && this.bytes[length] != 0) {
			++length;
		}
		return length;
	}
	
	/**
	 * Get the contained bytes.
	 * 
	 * @return the contained bytes
	 */
	public byte[] getBytes() {
		return this.bytes;
	}

	@Override
	public String toString() {
		return Bytes.decode(Base.getRuntimeCharset().newDecoder(), this.getBytes(), this.getLength()).toString();
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
