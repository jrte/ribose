/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import java.util.Arrays;
import java.util.EmptyStackException;

/**
 * @author kb
 */
public final class TransducerStack {
	private Transduction.TransducerState[] stack;
	private int tos;

	public TransducerStack(final int initialSize) {
		this.stack = new Transduction.TransducerState[initialSize];
		for (int i = initialSize - 1; i >= 0; i--) {
			this.stack[i] = new Transduction.TransducerState(0, null);
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
	 * Push an transducer onto the stack.
	 * 
	 * @param transducer The transducer to push
	 */
	public void push(final Transducer transducer) {
		this.stackcheck(1);
		this.stack[this.tos].transducer = transducer;
		this.stack[this.tos].state = 0;
	}

	/**
	 * Get the transducer on the top of the stack
	 * 
	 * @return The transducer on the top of the stack, or null if empty
	 */
	public Transduction.TransducerState peek() {
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
	 */
	public Transduction.TransducerState pop() {
		if (this.tos >= 0) {
			this.stack[this.tos].reset(0, null);
			return --this.tos >= 0 ? this.stack[this.tos] : null;
		} else {
			throw new EmptyStackException();
		}
	}

	/**
	 * Get the Nth item from the stack
	 * 
	 * @param index The index of the item to get
	 * @return The Nth item from the stack
	 */
	public Transduction.TransducerState get(final int index) {
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
