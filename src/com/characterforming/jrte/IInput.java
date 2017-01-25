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
	 * Get the next block of input from the source. The returned buffer may contain
	 * mixed text (Unicode) and signal ordinal values. If the position returned with
	 * the previous call is less than the limit returned from that call, the previous
	 * buffer should be returned and with the updated position and limit.
	 * 
	 * @return The next block of input char, or null
	 * @throws InputException
	 */
	public CharBuffer get() throws InputException;

	/**
	 * Mark a position in the input stream. The implementation class may set an upper
	 * bound on the number of marked characters. Calls to reset() may fail after
	 * this limit is reached.
	 * 
	 * @throws InputException If input has been reset but previous marked input remains
	 */
	public void mark() throws InputException;

	/**
	 * Reset stream input at a previously marked position and clear the mark.
	 * 
	 * @throws InputException If no previous mark is set
	 * @return The new position at the mark
	 * @throws MarkLimitExceededException If the mark limit has been exceeded
	 */
	public int reset() throws InputException, MarkLimitExceededException;
}
