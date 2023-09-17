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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;

import com.characterforming.jrte.engine.Base;

/**
 * Wraps an immutable array of bytes. Ribose transductions operate in the {@code byte}
 * domain, and transduction input and outputs are represented as byte arrays.
 * 
 * @author Kim Briggs
 */
public final class Bytes {
	/** A singleton empty byte array */
	public static final byte[] EMPTY_BYTES = new byte[] { };

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();
	private static final CharBuffer DECODER_ERROR = CharBuffer.wrap(new char[] { '?', '?', '?' });
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
	 * Decode UTF-8 bytes to a String.
	 * 
	 * @param decoder the decoder to use
	 * @param bytes the bytes to decode
	 * @param length the number of bytes to decode
	 * @return a CharBuffer containing the decoded text
	 */
	public static CharBuffer decode(CharsetDecoder decoder, final byte[] bytes, final int length) {
		assert 0 <= length && length <= bytes.length;
		int size = Math.max(Math.min(length, bytes.length), 0);
		try {
			return decoder.reset().decode(ByteBuffer.wrap(bytes, 0, size));
		} catch (CharacterCodingException e) {
			assert false;
			System.err.printf("Bytes.decode(CharsetDecoder, byte[], int, int): %1$s",  e.getMessage());
		}
		return Bytes.DECODER_ERROR;
	}

	/**
	 * Encode a String.
	 * 
	 * @param encoder the encoder to use
	 * @param chars the string to encode
	 * @return the encoded Bytes
	 */
	public static Bytes encode(CharsetEncoder encoder, final String chars) {
		return Bytes.encode(encoder, CharBuffer.wrap(chars.toCharArray()));
	}

	/**
	 * Encode a CharBuffer.
	 * 
	 * @param encoder the encoder to use
	 * @param chars the CharBuffer to encode
	 * @return the encoded Bytes
	 */
	public static Bytes encode(CharsetEncoder encoder, final CharBuffer chars) {
		Bytes bytes = null;
		try {
			ByteBuffer buffer = encoder.reset().encode(chars);
			byte[] b = new byte[buffer.limit()];
			buffer.get(b, 0, b.length);
			bytes = new Bytes(b);
		} catch (CharacterCodingException e) {
			assert false;
			System.err.println("Bytes.encode(CharsetEncoder,CharBuffer): " + e.getMessage());
		}
		return bytes;
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
	 * Get the contained bytes. This is a direct reference to the wrapped array. Sadly
	 * there is no way for Java to confer immutability to the returned value.
	 * 
	 * @return the contained bytes
	 */
	public byte[] bytes() {
		return this.data;
	}

	/**
	 * Decode contents to a String.
	 * 
	 * @param decoder the decoder to use
	 * @return a Unicode string
	 */
	public String toString(CharsetDecoder decoder) {
		try {
			return decoder.reset().decode(ByteBuffer.wrap(this.bytes())).toString();
		} catch (Exception e) {
			return this.toHexString();
		}
	}

	/**
	 * Decode contents to a String.
	 * 
	 * @return a Unicode string
	 */
	@Override
	public String toString() {
		try {
			return Bytes.decode(Base.newCharsetDecoder(), this.bytes(), this.getLength()).toString();
		} catch (Exception e) {
			return this.toHexString();
		}
	}

	/** 
	 * Lazy hash code evaluation. 
	 * 
	 * @return a hash based on the contents
	 */
	@Override
	public int hashCode() {
		if (this.hash == 0) {
			this.hash = this.hash();
		}
		return this.hash;
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
	
	/**
	 * Like toString(), but the result is a hexadecimal representation of the contents. 
	 * 
	 * @return the hex string
	 */
	public String toHexString() {
		char[] hex = new char[2 * this.getLength()];
		for (int j = 0; j < this.getLength(); j++) {
			int k = this.data[j] & 0xFF;
			hex[j * 2] = HEX[k >> 4];
			hex[j * 2 + 1] = HEX[k & 0x0F];
		}
		return new String(hex);
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
