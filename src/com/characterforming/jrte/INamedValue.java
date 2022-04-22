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

import com.characterforming.jrte.base.Bytes;

/**
 * Snapshot wrapper for volatile named values. These contain direct references
 * to the transduction value buffers and their length and content will change 
 * according to transducer actions. ITarget instance may retain INamedValue
 * instances for the entire lifetime of an ITransductor instance and query 
 * them periodically when they synchronize with the transduction process under
 * effector direction.
 * 
 * @author Kim Briggs
 */
public interface INamedValue {
	/**
	 * Get the value name
	 * 
	 * @return The value name
	 */
	public Bytes getName();

	/**
	 * Get the value ordinal.
	 * 
	 * @return The value index
	 */
	public int getOrdinal();

	/**
	 * Get the number of bytes in the value array as of the time of the call
	 * 
	 * @return The number of bytes in the value array
	 */
	public int getLength();

	/**
	 * Decode a UTF-8 encoded value using the default charset.
	 * 
	 * @return A Unicode char[] array holding the decoded value
	 */
	public char[] decodeValue();

	/**
	 * Get a copy of the value, trimmed to actual length.
	 * 
	 * @return A copy of the contents of the value array
	 */
	public byte[] copyValue();
	
	/**
	 * Extract value as String
	 * 
	 * @return A string representation of the value
	 */
	public String asString();
	
	/**
	 * Extract value as long integer
	 * 
	 * @return A integral representation of the value
	 */
	public long asInteger();
	
	/**
	 * Extract value as floating point number
	 * 
	 * @return A floating point representation of the value
	 */
	public double asReal();
}
