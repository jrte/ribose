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
 * Maps transducer fields to Java primitive fields expressed by an effector that
 * subclasses {@link BaseReceptorEffectore}. The {@code defaultValue} parameter
 * is supplied by the initial value of the field in the receptor effector.
 * <ul>
 * <li><i>type:</i> ({@link FieldType}) Java primitive type, byte[] or char[] array</li>
 * <li><i>field:</i> ({@link Field}) the subclass field, reflected back</li>
 * <li><i>defaultValue:</i> (Object) default field value, applied to {@code field} on reset</li>
 * <li><i>fieldIndex:</i> (int) ofset to field value in transducer stack frame</li>
 * </ul>
 */
record Receiver(FieldType type, Field field, Object defaultValue, int fieldIndex) {

	/** Enumeration of receiver field types */
	enum FieldType {
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

	@Override
	public String toString() {
		return String.format("%1$s %2$s: field index=%3$d, default=%4$s",
			type.toString(), field.getName(), fieldIndex, defaultValue);
	}
}