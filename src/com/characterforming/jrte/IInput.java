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

package com.characterforming.jrte;

import java.nio.ByteBuffer;

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
	public ByteBuffer get() throws InputException;
	
	/**
	 * Peek at the current buffer. Implementations must retain this reference to the most 
	 * recent return from get().
	 * 
	 * @return The current buffer, which may be spent, or null if get() not yet called.
	 */
	public ByteBuffer current(); 

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
	public ByteBuffer[] reset() throws InputException;

	/**
	 * Rewind stream input to beginning and clear the mark.
	 * 
	 * @return the receiving IInput instance, rewound to beginning
	 * @throws InputException On error
	 */
	public IInput rewind() throws InputException;
	
	/***
	 * Stop() message received from transduction when input is popped.
	 * @throws InputException 
	 */
	public void stop() throws InputException;
}
