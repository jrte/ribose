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
	 * @throws InputException
	 */
	public ReaderInput(final Reader input) throws InputException {
		super(4, 4096);
		this.reader = input;
		this.eof = this.reader == null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#next()
	 */
	@Override
	public CharBuffer next(final CharBuffer empty) throws InputException {
		if (!this.eof) {
			try {
				final int count = this.reader.read(empty.array());
				if (count > 0) {
					empty.position(0);
					empty.limit(count);
					return empty;
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
