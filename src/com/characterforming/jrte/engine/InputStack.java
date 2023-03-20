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
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kim Briggs
 */
final class InputStack {
	enum MarkState { clear, marked, reset };
	private final Logger logger;
	private final byte[][] signals;
	private final byte[][] values;
	private Input[] stack;
	private int tos;
	private int markLimit;
	private MarkState markState;
	private Input[] marked;
	private int bom, tom;
	private long bytesPopped;
	
	class MarkStack extends LinkedList<Input> {
		private static final long serialVersionUID = 1L;
	}
	class FreeStack extends LinkedList<byte[]> {
		private static final long serialVersionUID = 1L;
	}
	
	InputStack(final int initialSize, final int signalCount, final int valueCount) {
		this.logger = Base.getCompileLogger();
		this.signals = new byte[signalCount][]; 
		for (int i = 0; i < signalCount; i++) {
			this.signals[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, Base.RTE_SIGNAL_BASE + i);
		}
		this.values = new byte[valueCount][]; 
		for (int i = 0; i < valueCount; i++) {
			this.values[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_VALUE, i);
		}
		this.stack = Input.stack(initialSize);
		this.marked = Input.stack(initialSize);
		this.markState = MarkState.clear;
		this.markLimit = 2;
		this.bom = this.tom = 0;
		this.bytesPopped = 0;
		this.tos = -1;
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
		top.copy(frame);
		top.mark = -1;
		return top;
	}

	/** 
	 * Insert data into frames in fifo order at top of stack
	 * 
	 * @param input array of input data
	 */
	public void put(byte[][] data) {
		int i = data.length;
		while (--i >= 0) {
			this.push(data[i]);
		}
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
		assert this.stack.length <= Transductor.INITIAL_STACK_SIZE;
 		Input input = Input.empty;
		while (this.tos >= 0) {
			input = this.stack[this.tos];
			if (input.hasRemaining()) {
				if (this.markState == MarkState.reset
				&& !input.hasMark() && this.tos == 0
				) {
					assert this.tom >= 0;
					assert !Base.isReferenceOrdinal(input.array);
					this.addMarked(this.stack[this.tos--]);
					input.copy(this.getMarked());
				} else if (this.tos == 0 && input.position == 0) {
						this.bytesPopped += (input.limit - input.position);
				}
				return input;
			}
			--this.tos;
		}
		assert this.tos == -1;
		assert !input.hasRemaining();
		input = Input.empty;
		switch (this.markState) {
		case reset:
			assert this.bom != this.tom;
			assert this.stack[0].array == this.marked[this.bom].array;
			input = this.push(this.getMarked());
			break;
		case marked:
			Input marked = this.addMarked(this.stack[0]);
			marked.position = Math.max(0, marked.mark);
			marked.mark = -1;
			break;
		default:
			break;
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
			this.stack[0].mark = this.stack[0].position;
			this.markState = MarkState.marked;
			this.bom = this.tom;
		}
	}
	
	/** 
	 * Clear the mark state and null out all data references in the mark stack. 
	 */
	void unmark() {
		this.stack[0].mark = -1;
		this.bom = this.tom = 0;
		this.markState = MarkState.clear;
		for (Input input : this.marked) {
			input.clear();
		}
	}
	
	/** 
	 * Reset position in marked frame to a mark point. The reset will be 
	 * effected if/when the marked frame is/becomes top frame. 
	 * 
	 * @return true if reset effected immediately
	 */
	Input reset() {
		if (this.markState == MarkState.marked) {
			assert this.tos >= 0;
			Input bos = this.stack[0];
			if (bos.mark >= 0) {
				this.bom = this.tom;
				bos.position = bos.mark;
				bos.mark = -1;
				this.markState = MarkState.clear;
				return bos;
			} else {
				assert bos.mark == -1;
				assert this.bom != this.tom;
				this.markState = MarkState.reset;
				if (this.tos == 0) {
					this.tos = -1;
					this.addMarked(bos).position = 0;
					return this.push(this.getMarked());
				}
			}
		}
		return this.peek();
	}

	/**
	 * Get a recycled data buffer that has been released from the mark set
	 *
	 * @param bytes buffer most recently pushed to empty stack
	 * @return {@code bytes} or a data buffer recently released from the mark set or null
	 */
	byte[] recycle(byte[] bytes) {
		assert this.tos < 0;
		if (!resident(bytes)) {
			return bytes;
		}
		final boolean empty = this.bom == this.tom;
		final int tom = this.nextMarked(this.tom);
		final int start = empty ? 0 : tom;
		final int bom = this.nextMarked(this.bom);
		final int end = empty ? this.marked.length : bom;
		for (int i = start; i != end; i = empty ? (i + 1) : this.nextMarked(i)) {
			if (this.marked[i].array != null) {
				if (!resident(this.marked[i].array)) {
					bytes = this.marked[i].array;
					this.marked[i].clear();
					return bytes;
				} else {
					this.marked[i].clear();
				}
			}
		}
		return new byte[bytes.length];
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
	 * Get and reset the number of bytes popped since last sample.
	 * @return The number of bytes popped since count last reset
	 */
	public long getBytesCount() {
		long bytes = this.bytesPopped;
		this.bytesPopped = 0;
		return bytes;
  }

	private Input stackcheck() {
		this.tos += 1;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, (this.tos * 5) >> 2);
			for (int pos = this.tos; pos < this.stack.length; pos++) {
				this.stack[pos] = new Input();
			}
		}
		return this.peek();
	}
	
	private Input markcheck() {
		this.tom = this.nextMarked(this.tom);
		if (this.tom == this.bom) {
			Input[] marked = new Input[(this.marked.length * 5) >> 2];
			for (int pos = 0; pos < this.marked.length; pos++) {
				marked[pos] = this.marked[this.bom];
				this.bom = this.nextMarked(this.bom);
			}
			for (int pos = this.marked.length; pos < marked.length; pos++) {
				marked[pos] = new Input();
			}
			this.tom = this.marked.length;
			this.marked = marked;
			this.bom = 0;
		}
		int markSize = this.tom < this.bom
		? this.tom + this.marked.length - this.bom
		: this.tom - this.bom;
		if (markSize > this.markLimit) {
			this.logger.log(Level.WARNING, String.format("Mark limit %d exceeded. Try increasing ribose.inbuffer.size to exceed maximal expected marked extent.",
				this.markLimit));
			this.markLimit = markSize;
		}
		return this.marked[this.tom];
	}
	
	private boolean resident(byte[] bytes) {
		for (int i = 0; i < this.tos; i++) {
			if (bytes == this.stack[i].array) {
				return true;
			}
		}
		final int bom = this.nextMarked(this.bom);
		final int tom = this.nextMarked(this.tom);
		for (int i = bom; i != tom; i = this.nextMarked(i)) {
			if (bytes == this.marked[i].array) {
				return true;
			}
		}
		return false;
	}
	
	private Input addMarked(Input input) {
		this.markcheck().copy(input);
		assert this.bom != this.tom;
		return this.marked[this.tom];
	}
	
	private Input getMarked() {
		if (this.bom != this.tom) {
			this.bom = this.nextMarked(this.bom);
			if (this.bom == this.tom) {
				this.markState = MarkState.clear;
			}
			return this.marked[this.bom];
		}
		return null;
	}
	
	private int nextMarked(int index) {
		return ++index < this.marked.length ? index : 0;
	}
}
