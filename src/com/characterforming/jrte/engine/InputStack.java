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

import com.characterforming.ribose.IEffector;

/**
 * @author Kim Briggs
 */
final class InputStack {
	private static final int clear = 0;
	private static final int marked = 1;
	private static final int reset = 2;
	private final Logger logger;
	private Input[] stack;
	private int tos;
	private int markHigh;
	private int markLimit;
	private int markState;
	private Input[] markList;
	private int bom, tom;
	private long bytesPushed;
	private long bytesAllocated;
	
	final class MarkStack extends LinkedList<Input> {
		private static final long serialVersionUID = 1L;
	}
	final class FreeStack extends LinkedList<byte[]> {
		private static final long serialVersionUID = 1L;
	}
	
	InputStack(final int initialSize, final int signalCount, final int fieldCount) {
		this.logger = Base.getCompileLogger();
		this.stack = Input.stack(initialSize);
		this.markList = Input.stack(initialSize);
		this.markState = InputStack.clear;
		this.markLimit = initialSize;
		this.markHigh = 0;
		this.bom = this.tom = 0;
		this.bytesAllocated = 0;
		this.bytesPushed = 0;
		this.tos = -1;
	}
	
	/**
	 * Push data onto the stack 
	 * 
	 * @param data The data to push for immediate transduction
	 */
	Input push(byte[] data, int limit) {
		assert data.length >= limit;
		Input top = this.stackcheck();
		if (this.tos == 0) {
			this.bytesPushed += limit;
		}
		top.array = data;
		top.length = data.length;
		top.limit = limit;
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
			this.push(data[i], data[i].length);
		}
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
				if (this.markState == InputStack.reset
				&& !input.hasMark() && this.tos == 0
				) {
					assert this.tom >= 0;
					assert !Base.isReferenceOrdinal(input.array);
					this.addMarked(this.stack[this.tos--]);
					input.copy(this.getMarked());
				}
				return input;
			}
			--this.tos;
		}
		assert this.tos == -1;
		assert !input.hasRemaining();
		input = Input.empty;
		switch (this.markState) {
		case InputStack.reset:
			assert this.bom != this.tom;
			assert this.stack[0].array == this.markList[this.bom].array;
			input = this.push(this.getMarked());
			break;
		case InputStack.marked:
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
			this.markState = InputStack.marked;
			this.bom = this.tom;
		}
	}
	
	/** 
	 * Clear the mark state and null out all data references in the mark stack. 
	 */
	void unmark() {
		if (this.markHigh > Integer.min(this.markLimit, 32)) {
			this.logger.log(Level.WARNING, String.format(
				"Mark limit %d was extended past %d. Try increasing ribose.inbuffer.size to exceed maximal expected marked extent.",
				this.markLimit, Integer.min(this.markLimit, 32)));
		}
		this.markHigh = 0;
		this.stack[0].mark = -1;
		this.bom = this.tom = 0;
		this.markState = InputStack.clear;
		for (Input input : this.markList) {
			input.clear();
		}
	}
	
	/** 
	 * Reset position in marked frame to a mark point. The reset will be 
	 * effected if/when the marked frame is/becomes top frame. 
	 * 
	 * @return {@link IEffector#RTX_INPUT} if reset effected immediately
	 */
	int reset() {
		if (this.markState == InputStack.marked) {
			assert this.tos >= 0;
			Input bos = this.stack[0];
			if (bos.mark >= 0) {
				this.bom = this.tom;
				bos.position = bos.mark;
				bos.mark = -1;
				this.markState = InputStack.clear;
				if (this.tos == 0) {
					return IEffector.RTX_INPUT;
				}
			} else {
				assert bos.mark == -1;
				assert this.bom != this.tom;
				this.markState = InputStack.reset;
				if (this.tos == 0) {
					this.tos = -1;
					this.addMarked(bos).position = 0;
					this.push(this.getMarked());
					return IEffector.RTX_INPUT;
				}
			}
		}
		return IEffector.RTX_NONE;
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
		final int bom = this.nextMarked(this.bom);
		final int end = empty ? this.markList.length : bom;
		for (int i = empty ? 0 : tom; i != end; i = empty ? i + 1 : this.nextMarked(i)) {
			if (this.markList[i].array != null) {
				if (!resident(this.markList[i].array)) {
					bytes = this.markList[i].array;
					this.markList[i].clear();
					return bytes;
				} else {
					this.markList[i].clear();
				}
			}
		}
		this.bytesAllocated += bytes.length;
		return new byte[bytes.length];
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
	 * Get and reset the number of allocated in {@link #recycle()}
	 * since the last call to this method.
	 * 
	 * @return The number of bytes allocated since count last reset
	 */
	public long getBytesAllocated() {
		long allocated = this.bytesAllocated;
		this.bytesAllocated = 0;
		return allocated;
  }

  /**
	 * Get and reset the number of bytes of primary input read since
	 * the last call to the method.
	 * 
	 * @return The number of bytes read since count last reset
	 */
	public long getBytesRead() {
		long read = this.bytesPushed;
		this.bytesPushed = 0;
		return read;
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
			Input[] marked = new Input[(this.markList.length * 5) >> 2];
			for (int pos = 0; pos < this.markList.length; pos++) {
				marked[pos] = this.markList[this.bom];
				this.bom = this.nextMarked(this.bom);
			}
			for (int pos = this.markList.length; pos < marked.length; pos++) {
				marked[pos] = new Input();
			}
			this.tom = this.markHigh = this.markList.length;
			this.markList = marked;
			this.bom = 0;
		}
		return this.markList[this.tom];
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
			if (bytes == this.markList[i].array) {
				return true;
			}
		}
		return false;
	}
	
	private Input addMarked(Input input) {
		this.markcheck().copy(input);
		assert this.bom != this.tom;
		return this.markList[this.tom];
	}
	
	private Input getMarked() {
		if (this.bom != this.tom) {
			this.bom = this.nextMarked(this.bom);
			if (this.bom == this.tom) {
				this.markState = InputStack.clear;
			}
			return this.markList[this.bom];
		}
		return null;
	}
	
	private int nextMarked(int index) {
		return ++index < this.markList.length ? index : 0;
	}
}
