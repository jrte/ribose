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

package com.characterforming.jrte.engine;

import java.util.Arrays;

/**
 * @author Kim Briggs
 */
final class TransducerStack {
	private TransducerState[] stack;
	private int tos;

	TransducerStack(final int initialSize) {
		this.stack = new TransducerState[initialSize];
		for (int i = initialSize - 1; i >= 0; i--) {
			this.stack[i] = new TransducerState(0, null);
		}
		this.tos = - 1;
	}

	private void stackcheck(final int itemCount) {
		this.tos += itemCount;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, this.tos > 2 ? (this.tos * 3) >> 1 : 4);
		}
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
	void push(final Transducer transducer) {
		this.stackcheck(1);
		this.stack[this.tos].transducer = transducer;
		this.stack[this.tos].state = 0;
	}

	/**
	 * Get the transducer on the top of the stack
	 * 
	 * @return The transducer on the top of the stack, or null if empty
	 */
	TransducerState peek() {
		return (this.tos >= 0) ? this.stack[this.tos] : null;
	}

	/**
	 * Get the Nth transducer relative to bottom of the stack
	 * 
	 * @param index The index of the item to get
	 * @return The Nth item from the stack
	 */
	TransducerState peek(final int index) {
		return (index >= 0 && index <= this.tos) ? this.stack[index] : null;
	}

	/**
	 * Pop the stack
	 * 
	 * @return The item on top of the stack after the pop
	 */
	TransducerState pop() {
		if (this.tos >= 0) {
			this.stack[this.tos--].reset(0, null);
		}
		return this.peek();
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
}
