/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine.input;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import com.characterforming.jrte.InputException;
import com.characterforming.jrte.base.BaseInput;

/**
 * @author kb
 */
public class StreamInput extends BaseInput {
	private final InputStream stream;
	private final CharsetDecoder decoder;
	private final ByteBuffer bytes;
	private boolean eof;

	/**
	 * Constructor
	 * 
	 * @throws InputException On error
	 */
	public StreamInput(final InputStream input, final Charset charset) throws InputException {
		this.stream = input;
		this.decoder = charset.newDecoder();
		this.bytes = ByteBuffer.wrap(new byte[4096]);
		this.bytes.limit(0);
		this.eof = false;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#get()
	 */
	public CharBuffer get() throws InputException {
		if (!this.eof) {
			try {
				final byte[] input = this.bytes.array();
				int count = this.stream.read(input, 0, input.length);
				if (count > 0) {
					this.bytes.position(0);
					this.bytes.limit(count);
					char[] chars = new char[count];
					CharBuffer buffer = CharBuffer.wrap(chars);
					this.decoder.decode(this.bytes, buffer, false);
					if (this.bytes.remaining() == 0) {
						super.got(buffer);
						return buffer;
					}
					throw new InputException(String.format("%1$d undecoded bytes remaining in at end of stream", this.bytes.remaining()));
				} else {
					this.eof = true;
				}
			} catch (final IOException e) {
				throw new InputException("Caught an IOException reading from InputStream", e);
			}
		}
		return null;
	}
}
