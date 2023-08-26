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
package com.characterforming.ribose;

import com.characterforming.ribose.base.Bytes;

/**
 * Snapshot wrapper for volatile fields accessible in {@link IOutput}
 * views. These correspond to {@code `~field`} references in transducer patterns.
 * Effector instances receive an {@link IOutput} instance to provide access
 * to runtime fields held by the transductor. {@code IField} instances contain
 * direct references to the transduction field buffers and their length and content
 * will change according to transductor actions. Field names are global in ribose
 * models and semantics are guaranteed to be stable only as long as the transducer
 * stack is not pushed or popped.
 * 
 * @author Kim Briggs
 */
public interface IField {
	/**
	 * Get the field name
	 * 
	 * @return the field name
	 */
	Bytes getName();

	/**
	 * Get the field ordinal.
	 * 
	 * @return the field index
	 */
	int getOrdinal();

	/**
	 * Get the number of bytes in the value array as of the time of the call
	 * 
	 * @return the number of bytes in the value array
	 */
	int getLength();

	/**
	 * Decode a UTF-8 encoded value using the default charset.
	 * 
	 * @return a Unicode char[] array holding the decoded value
	 */
	char[] decodeValue();

	/**
	 * Get a copy of the value, trimmed to actual length.
	 * 
	 * @return a trimmed copy of the contents of the value array
	 */
	byte[] copyValue();
	
	/**
	 * Extract value as String
	 * 
	 * @return a string representation of the value
	 */
	String asString();
	
	/**
	 * Extract value as long integer
	 * 
	 * @return a integral representation of the value
	 */
	long asInteger();
	
	/**
	 * Extract value as floating point number
	 * 
	 * @return a floating point representation of the value
	 */
	double asReal();
}
