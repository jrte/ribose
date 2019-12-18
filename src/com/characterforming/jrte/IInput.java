/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

import java.nio.CharBuffer;

/**
 * Interface for input sources that can be included on the input stack of a running
 * Transduction instance. Inputs are stackable, and implementation classes must
 * retain the result of each get() call (including position and limit) until the
 * client application calls setPosition(position) with position = limit, indicating
 * that the client has exhausted the previous result and needs a new char[].
 * 
 * @author kb
 */
public interface IInput {
	/**
	 * Test for end of input.
	 * 
	 * @return true if no more input
	 * @throws InputException On error
	 */
	public boolean isEmpty() throws InputException;
	
	/**
	 * Get the next block of input from the source. The returned buffer may contain
	 * mixed text (Unicode) and signal ordinal values. If the position returned with
	 * the previous call is less than the limit returned from that call, the previous
	 * buffer should be returned and with the updated position and limit.
	 * 
	 * @return The next block of input char, or null
	 * @throws InputException On error
	 */
	public CharBuffer get() throws InputException;
	
	/**
	 * Peek at the current buffer. Implementations must retain this reference to the most 
	 * recent return from get().
	 * 
	 * @return The current buffer, which may be spent, or null if get() not yet called.
	 */
	public CharBuffer current(); 

	/**
	 * Mark a position in the input stream. The implementation class may set an upper
	 * bound on the number of marked characters. Calls to reset() may fail after
	 * this limit is reached.
	 * 
	 * @return true unless at end of stream
	 * 
	 * @throws InputException On error
	 */
	public boolean mark() throws InputException;

	/**
	 * Reset stream input at a previously marked position and clear the mark.
	 * 
	 * @return An array of buffers containing marked input up to current position
	 * @throws InputException On error
	 */
	public CharBuffer[] reset() throws InputException;

	/**
	 * Rewind stream input to beginning and clear the mark.
	 * 
	 * @throws InputException On error
	 */
	public void rewind() throws InputException;
}
