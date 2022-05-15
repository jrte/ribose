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
	enum MarkState { clear, marked, reset };
	private final byte[][] signals;
	private final byte[][] values;
	private final MarkStack marked;
	private final FreeStack freed;
	private MarkState markState;
	private Input[] stack;
	private int tos;
	
	@SuppressWarnings("serial")
	class MarkStack extends LinkedList<Input> { }
	@SuppressWarnings("serial")
	class FreeStack extends LinkedList<byte[]> { }
	
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
		this.markState = MarkState.clear;
		this.marked = new MarkStack();
		this.freed = new FreeStack();
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
		Input top = this.stackcheck();
		top.array = data;
		top.limit = top.length = top.array.length;
		top.position = 0;
		top.mark = -1;
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
		top.limit = frame.limit;
		top.length = frame.length;
		top.position = frame.position;
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
		return this.tos >= 0 ? this.stack[this.tos] : Input.empty;
	}

	/**
	 * Pop the stack
	 * 
	 * @return The item on top of the stack after the pop
	 */
	Input pop() {
 		Input input = this.peek();
		while (input != Input.empty && !input.hasRemaining()) {
			input = --this.tos >= 0 ? this.stack[this.tos] : Input.empty;
		}
		if (this.tos == 0) {
			if (this.markState == MarkState.reset) {
				if (!input.hasMark()) {
					assert !this.marked.isEmpty();
					input = new Input(this.stack[this.tos--]);
					this.marked.add(input);
					input = this.marked.pop();
					assert input != null;
				}
			}
		} else if (this.tos < 0) {
			assert input == Input.empty;
			if (this.markState == MarkState.reset) {
				this.freed.add(this.stack[0].array);
				this.stack[0].clear();
				if (!this.marked.isEmpty()) {
					input = this.push(this.marked.pop());
				} else {
					this.markState = MarkState.clear;
				}
			} else if (this.markState == MarkState.marked) {
				Input marked = new Input(this.stack[0]);
				assert marked.mark >= 0;
				marked.position = marked.mark;
				marked.mark = -1;
				this.marked.add(marked);
			}
		}
		return input;
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
		return Input.empty;
	}

	/**
	 * Create a mark point. This will be retained until {@code reset()}
	 * or {@code unmark()} is called. 
	 */
	void mark() {
		if (this.tos >= 0) {
			this.marked.clear();
			this.stack[0].mark = this.stack[0].position;
			this.markState = MarkState.marked;
		}
	}
	
	/** 
	 * Reset position in marked frame to a mark point. The reset will be 
	 * effected if/when the marked frame is/becomes top frame. 
	 * 
	 * @return true if reset effected immediately
	 */
	boolean reset() {
		if (this.markState == MarkState.marked) {
			assert this.tos >= 0;
			Input bos = this.stack[0];
			if (bos.mark >= 0) {
				this.markState = MarkState.clear;
				bos.position = bos.mark;
				bos.mark = -1;
			} else {
				assert bos.mark == -1;
				assert this.marked.size() > 0;
				this.markState = MarkState.reset;
				if (this.tos == 0) {
					--this.tos;
					bos.position = 0;
					this.marked.add(new Input(bos));
					this.push(this.marked.pop());
				}
			}
			return this.tos <= 0;
		}
		return false;
	}
	
	/** 
	 * Get a recycled data buffer that has been released from the mark set
	 *
	 * @param bytes buffer most recently pushed to empty stack 
	 * @return {@code bytes} or a data buffer recently released from the mark set or null
	 */
	byte[] recycle(byte[] bytes) {
		byte[] free = this.freed.peek();
		assert bytes != null && bytes != free;
		if (bytes == this.stack[0].array && this.stack[0].mark >= 0) {
			return this.freed.isEmpty() ? null : this.freed.pop();
		}
		for (Input marked : this.marked) {
			if (bytes == marked.array) {
				return this.freed.isEmpty() ? null : this.freed.pop();
			}
		}
		if (!this.freed.isEmpty()) {
			 return this.freed.pop();
		}
		return bytes;
	}
	
	/** 
	 * Create a mark point. This will be retained until {@code reset()}
	 * or {@code unmark()} is called. 
	 * 
	 * @param input
	 * @return true if the stack pointer changed
	 */
	void unmark() {
		this.markState = MarkState.clear;
		this.stack[0].mark = -1;
		this.marked.clear();
	}
	
	/**
	 * Check for marked input
	 * 
	 * @return true if input is marked
	 */
	boolean hasMark() {
		return this.markState != MarkState.clear;
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
	
	/**
	 * Get the data buffer most recently in bottom frame if not marked
	 * 
	 * @return most recent unmarked input buffer, or null if marking/resetting marked data segments
	 */
	byte[] data() {
		return this.markState == MarkState.clear ? this.stack[0].array : null;
	}

	private Input stackcheck() {
		this.tos += 1;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, (this.tos * 5) >> 2);
		}
		return this.peek();
	}
}
