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
package com.characterforming.ribose.base;

import java.lang.reflect.Field;

/***
 * Maps transducer fields to Java primitive fields expressed by an
 * effector that subclasses {@link BaseReceptorEffectore {@code
 * fieldIndex} parameter indicates the offset to the field within the
 * transductor stack frame. {@code Receptor} instances are instantiated
 * and used internally and should not be used directly by application code.
 * The {@code defaultValue} parameter is supplied by the initial value of
 * the field in the receptor effector.
 */
record Receiver(FieldType type, Field field, Object defaultValue, int fieldIndex) {
	/** Enumeration of receiver field types */
	public enum FieldType {
		/** Unknown type */
		UNKNOWN,
		/** boolean type */
		BOOLEAN,
		/** byte type */
		BYTE,
		/** byte[] type */
		BYTES,
		/** char type */
		CHAR,
		/** char[] type */
		CHARS,
		/** String type */
		STRING,
		/** short type */
		SHORT,
		/** int type */
		INT,
		/** long type */
		LONG,
		/** float type */
		FLOAT,
		/** double type */
		DOUBLE
	}
}