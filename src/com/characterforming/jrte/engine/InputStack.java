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

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.InputException;

/**
 * @author Kim Briggs
 */
public final class InputStack {
	private IInput[] stack;
	private int tos;

	InputStack(final int initialSize) {
		this.stack = new IInput[initialSize];
		this.tos = -1;
	}

	private void stackcheck(final int itemCount) {
		this.tos += itemCount;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, Math.max((this.tos * 5) >> 2, this.tos << 1));
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
	 * Push an item onto the stack.
	 * 
	 * @param item The item to push
	 */
	void push(final IInput item) {
		this.stackcheck(1);
		this.stack[this.tos] = item;
	}

	/**
	 * Put an item at the bottom of the stack.
	 * 
	 * @param item The item to put
	 */
	void put(final IInput item) {
		this.stackcheck(1);
		System.arraycopy(this.stack, 0, this.stack, 1, this.tos);
		this.stack[0] = item;
	}

	/**
	 * Put an array of item at the bottom of the stack. Items are
	 * pushed in array order, items[0] is first in and last out.
	 * 
	 * @param items The items to put
	 */
	void put(final IInput[] items) {
		this.stackcheck(items.length);
		System.arraycopy(this.stack, 0, this.stack, items.length, 1 + this.tos - items.length);
		System.arraycopy(items, 0, this.stack, 0, items.length);
	}

	/**
	 * Get the item on the top of the stack
	 * 
	 * @return The item on the top of the stack, or null if empty
	 */
	IInput peek() {
		if (this.tos >= 0) {
			return this.stack[this.tos];
		} else {
			return null;
		}
	}

	/**
	 * Pop the stack
	 * 
	 * @return The item on top of the stack after the pop
	 * @throws InputException 
	 */
	IInput pop() throws InputException {
		if (this.tos >= 0) {
			this.stack[this.tos].stop();
			this.stack[this.tos] = null;
			if (--this.tos >= 0 ) {
				return this.stack[this.tos];
			}
		}
		return null;
	}

	/**
	 * Get the Nth item from the stack
	 * 
	 * @param index The index of the item to get
	 * @return The Nth item from the stack
	 */
	IInput get(final int index) {
		if (index >= 0 && index <= this.tos) {
			return this.stack[index];
		} else {
			return null;
		}
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
