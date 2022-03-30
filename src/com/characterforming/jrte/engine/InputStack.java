/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
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

	public InputStack(final int initialSize) {
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
	public int tos() {
		return this.tos;
	}

	/**
	 * Push an item onto the stack.
	 * 
	 * @param item The item to push
	 */
	public void push(final IInput item) {
		this.stackcheck(1);
		this.stack[this.tos] = item;
	}

	/**
	 * Put an item at the bottom of the stack.
	 * 
	 * @param item The item to put
	 */
	public void put(final IInput item) {
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
	public void put(final IInput[] items) {
		this.stackcheck(items.length);
		System.arraycopy(this.stack, 0, this.stack, items.length, 1 + this.tos - items.length);
		System.arraycopy(items, 0, this.stack, 0, items.length);
	}

	/**
	 * Get the item on the top of the stack
	 * 
	 * @return The item on the top of the stack, or null if empty
	 */
	public IInput peek() {
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
	public IInput pop() throws InputException {
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
	public IInput get(final int index) {
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
	public int size() {
		return this.tos + 1;
	}

	/**
	 * Test for empty stack
	 * 
	 * @return True if the stack is empty
	 */
	public boolean isEmpty() {
		return this.tos < 0;
	}
}
