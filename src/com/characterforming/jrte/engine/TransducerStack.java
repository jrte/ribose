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

package com.characterforming.jrte.engine;

import java.util.Arrays;

/**
 * @author Kim Briggs
 */
final class TransducerStack {
	private Value[] values;
	private TransducerState[] stack;
	private int tos, bof;

	TransducerStack(final int initialSize) {
		int size = initialSize > 4 ? initialSize : 4;
		this.stack = new TransducerState[size];
		for (int i = size - 1; i >= 0; i--)
			this.stack[i] = new TransducerState();
		this.values = new Value[size << 3];
		for (int i = 0; i < this.values.length; i++)
			this.values[i] = new Value(128);
		this.tos = this.bof = -1;
	}

	private TransducerState stackcheck() {
		this.tos += 1;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, this.tos + (this.tos >> 1));
			for (int pos = this.tos; pos < this.stack.length; pos++)
				this.stack[pos] = new TransducerState();
		}
		return this.stack[this.tos];
	}

	/**
	 * Get the index of the top of stack
	 *
	 * @return the index of the top of stack
	 */
	int tos() {
		return this.tos;
	}

	/**
	 * Push an transducer onto the stack.
	 *
	 * @param transducer The transducer to push
	 */
	TransducerState push(final Transducer transducer) {
		assert this.bof >= 0 || (this.bof == -1) && (this.tos == -1);
		this.bof += this.bof >= 0 ? this.stack[this.tos].transducer.getFieldCount() : 1;
		TransducerState t = this.stackcheck().set(transducer, this.bof);
		int tof = this.bof + transducer.getFieldCount();
		int size = this.values.length;
		if (tof > size) {
			this.values = Arrays.copyOf(this.values, tof + (size >> 1));
			while (size < this.values.length)
				this.values[size++] = new Value(128);
		}
		while (tof > this.bof)
			this.values[--tof].clear();
		return t;
	}

	/**
	 * Get the item on the top of the stack
	 *
	 * @return The item on the top of the stack, or null if empty
	 */
	TransducerState peek() {
		return this.tos >= 0 ? this.stack[this.tos] : null;
	}

	/**
	 * Pop the stack
	 *
	 * @return The item on top of the stack after the pop
	 */
	TransducerState pop() {
		assert this.tos >= 0;
		TransducerState top = this.tos-- > 0 ? this.stack[this.tos] : null;
		this.bof = top != null ? top.frame : -1;
		return top;
	}

	/**
	 * Get the Nth item from the stack counting up from bottom if +ve index
	 * or down from top if index -ve.
	 *
	 * @param index The index of the item to get, relative to bottom or top of stack
	 * @return The Nth item from the stack counting frm bottom (+) or top (-)
	 */
	TransducerState get(int index) {
		if (this.tos >= 0) {
			if (index < 0)
				index = this.tos + index;
			if (index >= 0 && index <= this.tos)
				return this.stack[index];
		}
		return null;
	}

	/**
	 * Get the number of items on the stack
	 *
	 * @return the number of items on the stack
	 */
	int size() {
		return this.tos + 1;
	}

	/**
	 * Test for empty stack
	 *
	 * @return True if the stack is empty
	 */
	boolean isEmpty() {
		return this.tos < 0;
	}

	/**
	 * Clear all values in the current frame
	 */
	void clear() {
		if (this.bof >= 0)
			for (int tof = this.bof + this.stack[tos].transducer.getFieldCount() - 1; tof >= this.bof; --tof)
				this.values[tof].clear();
	}

	/**
	 * Clear a value in the current frame
	 *
	 * @param ordinal The index of the value to clear
	 */
	void clear(int ordinal) {
		assert this.bof >= 0 && ordinal < this.stack[this.tos].transducer.getFieldCount()
		: String.format("TransducerStack.clear(ordinal=%1$d): bof=%2$d, %3$d fields in frame",
				ordinal, this.bof, this.tos >= 0 ? this.stack[this.tos].transducer.getFieldCount() : 0);
		this.values[this.bof + ordinal].clear();
	}

	/**
	 * Get a value in the current frame
	 *
	 * @param ordinal The index of the value in the current frame
	 * @return The value
	 */
	Value value(int ordinal) {
		assert this.bof >= 0 && ordinal < this.stack[this.tos].transducer.getFieldCount()
		: String.format("TransducerStack.value(ordinal=%1$d): bof=%2$d, %3$d fields in frame",
				ordinal, this.bof, this.tos >= 0 ? this.stack[this.tos].transducer.getFieldCount() : 0);
		return this.values[this.bof + ordinal];
	}
}
