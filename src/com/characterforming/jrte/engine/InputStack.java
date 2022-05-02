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
import java.util.LinkedList;

import com.characterforming.ribose.base.Base;

/**
 * @author Kim Briggs
 */
final class InputStack {
	private final byte[][] signals;
	private final byte[][] values;
	private final MarkStack marked;
	private Input[] stack;
	private int tos;
	
	@SuppressWarnings("serial")
	class MarkStack extends LinkedList<Input> { }
	
	InputStack(final int initialSize, final int signalCount, final int valueCount) {
		this.signals = new byte[signalCount][]; 
		for (int i = 0; i < signalCount; i++) {
			this.signals[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, Base.RTE_SIGNAL_BASE + i);
		}
		this.values = new byte[valueCount][]; 
		for (int i = 0; i < valueCount; i++) {
			this.values[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_VALUE, i);
		}
		this.stack = InputStack.stack(initialSize);
		this.marked = new MarkStack();
		this.tos = -1;
	}
	
	private static Input[] stack(final int initialSize) {
		Input stack[] = new Input[initialSize];
		for (int i = initialSize - 1; i >= 0; i--) {
			stack[i] = new Input();
		}
		return stack;
	}

	/**
	 * Push data onto the stack 
	 * 
	 * @param data The data to push for immediate transduction
	 */
	Input push(byte[] data) {
		int mark = (this.tos < 0 && this.stack[0].mark < -1) ? 0 : -1;
		Input top = this.stackcheck();
		top.array = data;
		top.limit = top.length = top.array.length;
		top.position = 0;
		top.mark = mark;
		return top;
	}

	/**
	 * Push a marked frame onto the stack 
	 * 
	 * @param frame The marked frame
	 */
	Input push(Input frame) {
		Input top = this.stackcheck();
		top.array = frame.array;
		top.limit = top.length = frame.length;
		top.position = Math.max(0, frame.mark);
		top.mark = -1;
		return top;
	}

	/**
	 * Push a signal onto the stack 
	 * 
	 * @param signal The signal ordinal
	 */
	void signal(int signal) {
		this.push(this.signals[signal - Base.RTE_SIGNAL_BASE]);
	}
	
	/**
	 * Push a value onto the stack 
	 * @param value 
	 * 
	 * @param value The value ordinal
	 */
	void value(byte[] value, int length) {
		Input top = this.push(value);
		top.limit = top.length = length;
	}

	/**
	 * Get the item on the top of the stack
	 * 
	 * @return The item on the top of the stack, or null if empty
	 */
	Input peek() {
		return this.tos >= 0 ? this.stack[this.tos] : null;
	}

	/**
	 * Pop the stack
	 * 
	 * @return The item on top of the stack after the pop
	 */
	Input pop() {
 		Input top = this.peek();
		while (top != null && !top.hasRemaining()) {
			top = --this.tos >= 0 ? this.stack[this.tos] : null;
		}
		if (this.tos <= 0 && this.stack[0].mark >= 0) {
			this.tos = 0;
			top = this.stack[0];
			top.position = top.mark;
			top.mark = -1;
			if (top.hasRemaining()) {
				this.marked.addLast(new Input(top));
			}
			if (!this.marked.isEmpty()) {
				for (Input input : this.marked) {
					this.push(input);
				}
				this.unmark();
			}
		}
		return this.peek();
	}

	/**
	 * Get the Nth item from the stack counting up from bottom if +ve index
	 * or down from top if index -ve. 
	 * 
	 * @param index The index of the item to get, relative to bottom or top of stack
	 * @return The Nth item from the stack counting frm bottom (+) or top (-)
	 */
	Input get(int index) {
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
	 * Create a mark point. This will be retained until {@code reset()}
	 * or {@code unmark()} is called. 
	 */
	void mark() {
		if (this.tos >= 0) {
			this.stack[0].mark = -1*(2 + this.stack[0].position);
			this.marked.clear();
		}
	}
	
	/** 
	 * Reset position in marked frame to a mark point. The reset will be 
	 * effected if/when the marked frame is/becomes top frame. 
	 * 
	 * @return true if reset effected immediately
	 */
	boolean reset() {
		if (this.tos >= 0 && this.stack[0].mark < -1) {
			this.stack[0].mark = -1*(this.stack[0].mark + 2);
			if (this.tos == 0) {
				this.stack[0].position = this.stack[0].mark;
				this.stack[0].mark = -1;
				return true;
			}
		}
		return false;
	}
	
	/** 
	 * Create a mark point. This will be retained until {@code reset()}
	 * or {@code unmark()} is called. 
	 * 
	 * @param input
	 * @return true if the stack pointer changed
	 */
	void unmark() {
		this.stack[0].mark = -1;
		this.marked.clear();
	}
	
	/**
	 * Check for marked input
	 * 
	 * @return true if input is marked
	 */
	boolean hasMark() {
		return this.tos >= 0 && this.stack[0].mark < -1;
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
	 * Get the index of the top of stack
	 * 
	 * @return the index of the top of stack
	 */
	int tos() {
		return this.tos;
	}

	private Input stackcheck() {
		this.tos += 1;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, (this.tos * 5) >> 2);
		}
		return this.peek();
	}
}
