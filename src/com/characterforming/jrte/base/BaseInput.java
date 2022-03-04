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

package com.characterforming.jrte.base;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.InputException;

/**
 * Base {@link IInput} implementation class implements mark() and reset().
 * Subclasses must implement {@link #get()} to return the next
 * sequential block of input.
 * 
 * @author kb
 */
public abstract class BaseInput implements IInput {
	private ArrayList<ByteBuffer> marked;
	private ByteBuffer current;

	protected BaseInput() {
		this.marked = null;
		this.current = null;
	}
	
	@Override
	public ByteBuffer current() {
		return this.current;
	}

	/**
	 * Subclasses must call this from their get() method passing the buffer (or
	 * null) to be returned from get(). Has no effect unless this has a mark.
	 * 
	 * @param buffer The buffer to be returned from get() 
	 */
	protected ByteBuffer got(ByteBuffer buffer) {
		if ((this.marked != null) && (buffer != null)) {
			if (buffer != this.marked.get(this.marked.size() - 1)) {
				this.marked.add(buffer.mark());
			}
		}
		this.current = buffer;
		return this.current;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#mark()
	 */
	@Override
	public boolean mark() throws InputException {
		if (this.current != null) {
			if (this.marked == null) {
				this.marked = new ArrayList<ByteBuffer>();
			} else if (this.marked.contains(this.current())) {
				assert this.current == this.marked.get(this.marked.size() - 1);
				this.current.mark();
				return true;
			}
			this.marked.add(this.current.mark());
			return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#reset()
	 */
	@Override
	public final ByteBuffer[] reset() {
		ByteBuffer[] buffers = null;
		if (this.marked != null) {
			assert this.marked.size() > 0 && this.marked.get(this.marked.size() - 1) == this.current;
			if (this.marked.size() > 1) {
				buffers = new ByteBuffer[this.marked.size() - 1];
				for (int i = 0; i < buffers.length; i++) {
					buffers[i] = this.marked.get(i);
					buffers[i].reset();
				}
			}
			this.marked.get(this.marked.size() - 1).reset();
		}
		this.marked = null;
		return buffers;
	}
}
