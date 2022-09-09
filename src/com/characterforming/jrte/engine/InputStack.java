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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.base.Base;

/**
 * @author Kim Briggs
 */
final class InputStack {
	public final static int BLOCK_SIZE = Integer.parseInt(System.getProperty("ribose.block.size", "65536"));
	private static final Logger logger = Logger.getLogger(Base.RTE_LOGGER_NAME);
	enum MarkState { clear, marked, reset };
	private final byte[][] signals;
	private final byte[][] values;
	private Input[] stack;
	private int tos;
	private boolean markLimitFlagged;
	private MarkState markState;
	private Input[] marked;
	private int bom, tom;
	
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
		this.marked = InputStack.stack(initialSize);
		this.markState = MarkState.clear;
		this.markLimitFlagged = false;
		this.bom = this.tom = 0;
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
 		Input input = this.peek();
		while (input != Input.empty && !input.hasRemaining()) {
			input = --this.tos >= 0 ? this.stack[this.tos] : Input.empty;
		}
		if (this.tos == 0) {
			if (this.markState == MarkState.reset) {
				if (!input.hasMark()) {
					assert this.tom >= 0;
					assert !Base.isReferenceOrdinal(input.array);
					this.addMarked(this.stack[this.tos--]);
					input.copy(this.getMarked());
				}
			}
		} else if (this.tos < 0) {
			assert input == Input.empty;
			if (this.markState == MarkState.reset) {
				assert this.bom != this.tom;
				assert this.stack[0].array == this.marked[this.bom].array;
				input = this.push(this.getMarked());
				if (this.bom == this.tom) {
					this.markState = MarkState.clear;
				} 
			} else if (this.markState == MarkState.marked) {
				Input marked = this.addMarked(this.stack[0]);
				marked.position = Math.max(0, marked.mark);
				marked.mark = -1;
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
			this.stack[0].mark = this.stack[0].position;
			this.markState = MarkState.marked;
			this.bom = this.tom;
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
				this.bom = this.tom;
				bos.position = bos.mark;
				bos.mark = -1;
				this.markState = MarkState.clear;
				return true;
			} else {
				assert bos.mark == -1;
				assert this.bom != this.tom;
				this.markState = MarkState.reset;
				if (this.tos == 0) {
					this.tos = -1;
					this.addMarked(bos).position = 0;
					this.push(this.getMarked());
					return true;
				}
			}
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
		if (bytes == this.stack[0].array && this.markState != MarkState.clear) {
			bytes = null;
		}
		if (bytes != null && this.bom != this.tom) {
			int end = this.nextMarked(this.bom);
			int start = this.nextMarked(this.tom);
			for (int i = start; i != end; i = this.nextMarked(i)) {
				if (bytes == this.marked[i].array) {
					bytes = null;
					break;
				}
			} 
		}
		if (bytes == null) {
			int end = this.nextMarked(this.bom);
			int start = this.nextMarked(this.tom);
			boolean noneMarked = start == end;
			if (noneMarked) {
				end = this.marked.length;
				start = 0;
			}
			for (int i = start; i != end; i = noneMarked ? (i + 1) : this.nextMarked(i)) {
				if (this.marked[i].array != null) {
					for (int j = this.nextMarked(this.bom); j != this.nextMarked(this.tom); j = nextMarked(j)) {
						if (this.marked[j].array == this.marked[i].array) {
							this.marked[i].array = null;
							break;
						}
					}
					if (this.marked[i].array != null) {
						bytes = this.marked[i].array;
						this.marked[i].array = null;
						return bytes;
					}
				}
			}
		}
		return bytes;
	}
	
	/** 
	 * Clear the mark state and null out all data references in the mark stack. 
	 */
	void unmark() {
		this.stack[0].mark = -1;
		this.markState = MarkState.clear;
		for (Input marked : this.marked) {
			marked.clear();
		}
		this.bom = this.tom;
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

	private Input stackcheck() {
		this.tos += 1;
		if (this.tos >= this.stack.length) {
			this.stack = Arrays.copyOf(this.stack, (this.tos * 5) >> 2);
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
			this.bom = 0;
			this.tom = this.marked.length;
			this.marked = marked;
		}
		if ((this.tom > this.bom) && (this.tom - this.bom) > 2
		|| ((this.tom < this.bom) && ((this.marked.length - this.bom) + this.tom) > 2)
		) {
			if (!this.markLimitFlagged) {
				logger.log(Level.WARNING,
					"Mark limit exceeded. Try increasing ribose.block.size to exceed maximal expected marked extent.");
				this.markLimitFlagged = true;
			}
		}
		return this.marked[this.tom];
	}
	
	private Input addMarked(Input input) {
		this.markcheck().copy(input);
		assert this.bom != this.tom;
		return this.marked[this.tom];
	}
	public final static int MARK_LIMIT = Integer.parseInt(System.getProperty("ribose.mark.limit", "65536"));

	private Input getMarked() {
		if (this.bom != this.tom) {
			this.bom = this.nextMarked(this.bom);
			return this.marked[this.bom];
		}
		return null;
	}
	
	private int nextMarked(int index) {
		return ++index < this.marked.length ? index : 0;
	}
}
