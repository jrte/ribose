/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import java.util.Arrays;
import java.util.EmptyStackException;

/**
 * @author kb
 */
public final class Stack<T> {
	private final boolean free;
	private T[] stack;
	private int tos;

	public Stack(final T[] initialItems, final boolean free) {
		final int initialSize = initialItems.length > 2 ? (initialItems.length * 3) >> 1 : 4;
		this.stack = Arrays.copyOf(initialItems, initialSize);
		this.tos = initialItems.length - 1;
		this.free = free;
	}

	private void stackcheck(final int itemCount) {
		this.tos += itemCount;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, this.tos > 2 ? (this.tos * 3) >> 1 : 4);
		}
	}

	/**
	 * Push an item onto the stack.
	 * 
	 * @param item The item to push
	 */
	public void push(final T item) {
		this.stackcheck(1);
		this.stack[this.tos] = item;
	}

	/**
	 * Push an array of items onto the stack. Items are
	 * pushed in array order, items[0] is first in and last out.
	 * 
	 * @param items The items to push
	 */
	public void push(final T[] items) {
		this.stackcheck(items.length);
		System.arraycopy(items, 0, this.stack, 1 + this.tos - items.length, items.length);
	}

	/**
	 * Push a empty item onto the stack and return it.
	 * 
	 * @param item The item to push
	 */
	public T next() {
		this.stackcheck(1);
		final T item = this.stack[this.tos];
		if (item == null) {
			--this.tos;
		}
		return item;
	}

	/**
	 * Put an item at the bottom of the stack.
	 * 
	 * @param item The item to put
	 */
	public void put(final T item) {
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
	public void put(final T[] items) {
		this.stackcheck(items.length);
		System.arraycopy(this.stack, 0, this.stack, items.length, 1 + this.tos - items.length);
		System.arraycopy(items, 0, this.stack, 0, items.length);
	}

	/**
	 * Get the item on the top of the stack
	 * 
	 * @return The item on the top of the stack, or null if empty
	 */
	public T peek() {
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
	public T pop() {
		if (this.tos >= 0) {
			final T top = --this.tos >= 0 ? this.stack[this.tos] : null;
			if (this.free) {
				this.stack[this.tos + 1] = null;
			}
			return top;
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
	public T get(final int index) {
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
