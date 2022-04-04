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

package com.characterforming.jrte;

import java.nio.ByteBuffer;

import com.characterforming.jrte.base.BaseInput;

/**
 * Wraps an input array that contains mixed text and signal ordinals.
 * 
 * @author Kim Briggs
 */
public final class ByteInput extends BaseInput {
	private ByteBuffer[] input;
	private int buffer;

	/**
	 * Constructor
	 * 
	 * @param inputs Array of {@code ByteBuffer} to input in sequence
	 * @throws InputException On error
	 */
	public ByteInput(final ByteBuffer inputs[]) throws InputException {
		this.input = inputs;
		this.buffer = 0;
	}

	/**
	 * Constructor 
	 * 
	 * @param inputs Array of @code byte[]} to input in sequence
	 * @throws InputException On error
	 */
	public ByteInput(final byte[][] inputs) {
		this.input = new ByteBuffer[inputs.length];
		for (int i = 0; i < this.input.length; i++) {
			this.input[i] = ByteBuffer.wrap(inputs[i]);
		}
		this.buffer = 0;
	}

	/**
	 * Constructor 
	 * 
	 * @param input Array of {@code byte[]} to input in sequence
	 * @throws InputException On error
	 */
	public ByteInput(final byte[] input) throws InputException {
		this.input[0] = ByteBuffer.wrap(input);
		this.buffer = 0;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#isEmpty()
	 */
	@Override
	public boolean isEmpty() throws InputException {
		if (this.buffer >= 0) {
			for (int i = this.buffer; i < this.input.length; i++) {
				if (this.input[i].hasRemaining()) {
					return false;
				}
			} 
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#get()
	 */
	@Override
	public ByteBuffer get() throws InputException {
		if (this.buffer >= 0) {
			while (this.buffer < this.input.length) {
				if (this.input[this.buffer].hasRemaining()) {
					return super.got(this.input[this.buffer]);
				}
				this.buffer++;
			} 
		}
		return super.got(null);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#rewind()
	 */
	@Override
	public IInput rewind() {
		while (this.buffer >= 0) {
			if (this.buffer < this.input.length) {
				this.input[this.buffer].rewind();
			}
			this.buffer--;
		}
		this.buffer = 0;
		super.got(null);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#stop()
	 */
	@Override
	public void stop() {
		this.rewind();
	}
}
