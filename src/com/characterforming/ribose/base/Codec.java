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
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import com.characterforming.ribose.IModel;

/**
 * Thread local charset encoder/decoder. This is attached to a ribose thread on first use
 * and detached when the thread closes a model (directly or implicitly via autoclose)
 * or explicitly calls {@link IModel#detach()}. Static methods for encoding and decoding
 * are provided. Codecs are sticky, if any codec method is invoked after {@link #detach()}
 * or {@link IModel#close()} a new Codec will be attached (and require detaching).
 */
public class Codec {
	private static final Charset CHARSET = Charset.forName(System.getProperty("ribose.runtime.charset", "UTF-8"));
	private static final ThreadLocal<Codec> LOCAL = ThreadLocal.withInitial(Codec::new);
	private CharsetDecoder decoder;
	private CharsetEncoder encoder;

	private Codec() {
		this.decoder = Codec.CHARSET.newDecoder()
			.onUnmappableCharacter(CodingErrorAction.REPLACE)
			.onMalformedInput(CodingErrorAction.REPLACE)
			.replaceWith("~")
			.reset();
		this.encoder = Codec.CHARSET.newEncoder()
			.onUnmappableCharacter(CodingErrorAction.REPLACE)
			.onMalformedInput(CodingErrorAction.REPLACE)
			.replaceWith("~".getBytes())
			.reset();
	}

	private static Codec set() {
		Codec codec = Codec.LOCAL.get();
		if (codec == null) {
			codec = new Codec();
			Codec.LOCAL.set(codec);
		}
		return codec;
	}

	private static Codec get() {
		Codec codec = Codec.LOCAL.get();
		if (codec == null) {
			codec = Codec.set();
		}
		return codec;
	}

	/**
	 * Explicitly detach Codec from thread.
	 */
	public static void detach() {
		Codec.LOCAL.remove();
	}

	/**
	 * Decode some UTF-8 bytes to a String.
	 * 
	 * @param bytes the bytes to decode
	 * @param length the number of bytes to decode from offset 0
	 * @return a String containing the decoded text
	 * @throws CharacterCodingException if decoding fails
	 */
	public static String decode(final byte[] bytes, final int length)
	throws CharacterCodingException {
		assert 0 <= length && length <= bytes.length;
		int size = Math.max(Math.min(length, bytes.length), 0);
		return Codec.get().decoder.reset().decode(ByteBuffer.wrap(bytes, 0, size)).toString();
	}

	/**
	 * Decode all UTF-8 bytes to a String.
	 * 
	 * @param bytes the bytes to decode
	 * @return a String containing the decoded text
	 * @throws CharacterCodingException if decoding fails
	 */
	public static String decode(final byte[] bytes)
	throws CharacterCodingException {
		return Codec.decode(bytes, bytes.length);
	}

	/**
	 * Encode a String to UTF-8.
	 * 
	 * @param chars the string to encode
	 * @return the encoded Bytes
	 * @throws CharacterCodingException if encoding fails
	 */
	public static Bytes encode(final String chars)
	throws CharacterCodingException {
		ByteBuffer buffer = Codec.get().encoder.reset().encode(CharBuffer.wrap(chars.toCharArray()));
		byte[] bytes = new byte[buffer.limit()];
		buffer.get(bytes, 0, bytes.length);
		return new Bytes(bytes);
	}
}
