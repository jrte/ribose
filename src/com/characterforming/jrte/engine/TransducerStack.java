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
	private TransducerState[] stack;
	private int tos;

	TransducerStack(final int initialSize) {
		this.stack = TransducerStack.stack(initialSize);
		this.tos = -1;
	}

	private static TransducerState[] stack(final int initialSize) {
		int size = initialSize > 4 ? initialSize : 4;
		TransducerState[] stack = new TransducerState[size];
		for (int i = size - 1; i >= 0; i--) {
			stack[i] = new TransducerState();
		}
		return stack;
	}
	
	private TransducerState stackcheck() {
		this.tos += 1;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, (this.tos * 5) >> 2);
			for (int pos = this.tos; pos < this.stack.length; pos++) {
				this.stack[pos] = new TransducerState();
			}
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
		return this.stackcheck().transducer(transducer);
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
		return this.tos-- > 0 ? this.stack[this.tos] : null;
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
			if (index < 0) {
				index = this.tos + index;
			}
			if (index >= 0 && index <= this.tos) {
				return this.stack[index];
			}
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
}
