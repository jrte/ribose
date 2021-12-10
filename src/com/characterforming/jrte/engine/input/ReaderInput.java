/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine.input;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

import com.characterforming.jrte.InputException;
import com.characterforming.jrte.base.BaseInput;

/**
 * Wraps a java.io.Reader source
 * 
 * @author kb
 */
public final class ReaderInput extends BaseInput {
	private final Reader reader;
	private boolean eof;

	/**
	 * Constructor
	 * 
	 * @param input The input source
	 * @throws InputException On error
	 */
	public ReaderInput(final Reader input) throws InputException {
		this.eof = (input == null);
		this.reader = input;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#next()
	 */
	@Override
	public CharBuffer get() throws InputException {
		if (!this.eof) {
			try {
				char[] chars = new char[1<<16];
				final int count = this.reader.read(chars);
				if (count > 0) {
					CharBuffer buffer = CharBuffer.wrap(chars);
					buffer.position(0);
					buffer.limit(count);
					super.got(buffer);
					return buffer;
				} else {
					this.eof = true;
				}
			} catch (final IOException e) {
				throw new InputException("Caught an IOException reading from Reader", e);
			}
		}
		return null;
	}
}
