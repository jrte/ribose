/**
 * 
 */
package com.characterforming.jrte.base;

import java.nio.CharBuffer;
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
	private ArrayList<CharBuffer> marked;
	private CharBuffer current;

	protected BaseInput() {
		this.marked = null;
		this.current = null;
	}
	
	public CharBuffer current() {
		return this.current;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#isEmpty()
	 */
	public boolean isEmpty() throws InputException {
		while ((this.current == null) || !this.current.hasRemaining()) {
			this.current = this.get();
		}
		return this.current == null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#get()
	 */
	public abstract CharBuffer get() throws InputException;

	/**
	 * Subclasses must call this from their get() method passing the buffer (or
	 * null) to be returned from get(). Has no effect unless this has a mark.
	 * 
	 * @param buffer The buffer to be returned from get() 
	 */
	protected void got(CharBuffer buffer) {
		if ((this.marked != null) && (buffer != null)) {
			if (buffer != this.marked.get(this.marked.size() - 1)) {
				this.marked.add(buffer);
			}
		}
		this.current = buffer;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#mark()
	 */
	public boolean mark() throws InputException {
		if (this.current != null) {
			this.marked = new ArrayList<CharBuffer>();
			this.marked.add(this.current);
			current.mark();
			return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#reset()
	 */
	public final CharBuffer[] reset() {
		if (this.marked != null) {
			CharBuffer[] buffers = this.marked.toArray(new CharBuffer[this.marked.size()]);
			this.marked = null;
			return buffers;
		}
		return null;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.BaseInput#rewind()
	 */
	public void rewind() throws InputException {
		throw new InputException(String.format("Rewind not supported by %1$s", super.getClass().getName()));
	}
}
