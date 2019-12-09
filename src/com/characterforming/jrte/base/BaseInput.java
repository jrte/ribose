/**
 * 
 */
package com.characterforming.jrte.base;

import java.nio.CharBuffer;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.InputException;
import com.characterforming.jrte.MarkLimitExceededException;

/**
 * Base {@link IInput} implementation class implements mark() and reset().
 * Subclasses must implement {@link #next(CharBuffer)} to return the next
 * sequential block of input.
 * 
 * @author kb
 */
public abstract class BaseInput implements IInput {
	private static final int EMPTY = 0, NORMAL = 1, MARKED = 2, RESET = 3, EOF = 4;
	private static final String[] STATES = {
			"EMPTY", "NORMAL", "MARKED", "RESET", "EOF"
	};

	private final int bufferLimit;
	private final int bufferLength;
	private final CharBuffer[] buffer;

	private int state;
	private int limit;
	private int position;
	private boolean overflow;

	protected BaseInput(final int bufferLimit, final int bufferLength) throws InputException {
		if (bufferLimit <= 0 || bufferLength < 0) {
			throw new InputException(String.format("BaseInput buffer limit %1$d must be > 0, buffer length %2$d must not be < 0", bufferLimit, bufferLength));
		}
		this.bufferLimit = bufferLimit;
		this.bufferLength = bufferLength;
		this.buffer = new CharBuffer[this.bufferLimit];
		for (int i = 0; i < this.buffer.length; i++) {
			this.buffer[i] = null;
		}
		this.limit = this.position = -1;
		this.state = BaseInput.EMPTY;
		this.overflow = false;
	}

	/**
	 * Subclasses must implement this to return the next sequential block of
	 * input.
	 * 
	 * @param empty A free buffer with a fixed backing char[] array that input
	 *           can be read into
	 * @return The next sequential block of input, or null if at end of input
	 * @throws InputException On error
	 */
	protected abstract CharBuffer next(CharBuffer empty) throws InputException;
	
	private void next() throws InputException {
		this.position = 0;
		this.buffer[0] = this.next(this.empty());
		this.state = this.buffer[0] != null ? BaseInput.NORMAL : BaseInput.EOF;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#get()
	 */
	@Override
	public CharBuffer get() throws InputException {
		switch (this.state) {
			case BaseInput.EMPTY:
			case BaseInput.NORMAL:
				this.next();
				break;
			case BaseInput.MARKED:
				if (!this.buffer[this.position].hasRemaining()) {
					if (++this.position < this.bufferLimit) {
						this.buffer[this.position] = this.next(this.empty());
						while (this.buffer[this.position] != null && !this.buffer[this.position].hasRemaining()) {
							this.buffer[this.position] = this.next(this.empty());
						}
						if (this.buffer[this.position] != null) {
							this.buffer[this.position].mark();
						} else {
							this.state = BaseInput.EOF;
							this.limit = this.position = -1;
						}
					} else {
						this.next();
						this.overflow = true;
					}
				}
				break;
			case BaseInput.RESET:
				if (!this.buffer[this.position].hasRemaining()) {
					do {
						++this.position;
					} while (this.position < this.limit && !this.buffer[this.position].reset().hasRemaining());
					if (this.position >= this.limit) {
						this.limit = -1;
						this.next();
					}
				}
				break;
			case BaseInput.EOF:
				this.limit = this.position = -1;
				break;
		}
		return this.state != BaseInput.EOF ? this.buffer[this.position] : null;
	}

	private CharBuffer empty() {
		if (this.bufferLength > 0) {
			if (this.buffer[this.position] == null) {
				this.buffer[this.position] = CharBuffer.wrap(new char[this.bufferLength]);
			} else {
				this.buffer[this.position].rewind();
				this.buffer[this.position].limit(this.bufferLength);
			}
			return this.buffer[this.position];
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#mark()
	 */
	@Override
	public void mark() throws InputException {
		if (this.state == BaseInput.NORMAL || this.state == BaseInput.MARKED || this.state == BaseInput.RESET) {
			this.state = BaseInput.MARKED;
			if (this.position > 0) {
				final CharBuffer b0 = this.buffer[0];
				this.buffer[0] = this.buffer[this.position];
				this.buffer[this.position] = b0;
			}
			this.position = 0;
			this.limit = -1;
			this.buffer[0].mark();
			this.overflow = false;
		} else if (this.state != BaseInput.EOF) {
			throw new InputException(String.format("Invalid state %1$s for mark()", BaseInput.STATES[this.state]));
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.IInput#reset()
	 */
	@Override
	public int reset() throws InputException, MarkLimitExceededException {
		if (this.state == BaseInput.MARKED) {
			this.state = BaseInput.RESET;
			this.limit = this.position + 1;
			this.position = 0;
			this.buffer[0].reset();
			return this.buffer[0].position();
		} else if (this.overflow) {
			this.overflow = false;
			throw new MarkLimitExceededException("Input marked list overflowed since last limit()");
		} else {
			throw new InputException(String.format("Invalid state %1$s for position()", BaseInput.STATES[this.state]));
		}
	}

	/**
	 * Rewind stream input and clear the mark and end of stream.
	 * 
	 * @return This IInput instance
	 */
	protected IInput rewind() {
		this.limit = this.position = -1;
		this.state = BaseInput.EMPTY;
		this.overflow = false;
		return this;
	}
}
