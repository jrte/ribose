/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine.input;

import java.nio.CharBuffer;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.InputException;
import com.characterforming.jrte.base.BaseInput;

/**
 * Wraps an input array that contains mixed text and signal ordinals.
 * 
 * @author kb
 */
public final class SignalInput extends BaseInput {
	private final CharBuffer[] inputs;
	private int index;

	/**
	 * Signal constructor
	 * 
	 * @param signals The inputs
	 * @throws InputException On error
	 */
	public SignalInput(final char[][] inputs) throws InputException {
		super(inputs.length, 0);
		this.index = 0;
		this.inputs = new CharBuffer[inputs.length];
		for (int i = 0; i < this.inputs.length; i++) {
			this.inputs[i] = CharBuffer.wrap(inputs[i]);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#next()
	 */
	@Override
	public CharBuffer next(final CharBuffer empty) throws InputException {
		while (this.index < this.inputs.length && !this.inputs[this.index].hasRemaining()) {
			this.index++;
		}
		return this.index < this.inputs.length ? this.inputs[this.index] : null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#rewind()
	 */
	@Override
	public IInput rewind() {
		while (this.index > 0) {
			this.inputs[--this.index].rewind();
		}
		return super.rewind();
	}
}
