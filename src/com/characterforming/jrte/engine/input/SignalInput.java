/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine.input;

import java.nio.CharBuffer;

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
	public SignalInput(final CharBuffer inputs[]) throws InputException {
		this.inputs = inputs;
		this.index = 0;
	}

	/**
	 * Signal constructor
	 * 
	 * @param signals The inputs
	 * @throws InputException On error
	 */
	public SignalInput(final char[][] inputs) throws InputException {
		this.inputs = new CharBuffer[inputs.length];
		for (int i = 0; i < this.inputs.length; i++) {
			this.inputs[i] = CharBuffer.wrap(inputs[i]);
		}
		this.index = 0;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#get()
	 */
	@Override
	public CharBuffer get() throws InputException {
		while (this.index < this.inputs.length) {
			if (this.inputs[this.index].hasRemaining()) {
				super.got(this.inputs[this.index]);
				return this.inputs[this.index];
			}
			this.index += 1;
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#rewind()
	 */
	@Override
	public void rewind() {
		while (this.index > 0) {
			this.inputs[--this.index].rewind();
		}
	}
}
