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

public class Base {
	public static String RTE_VERSION = "jrte-HEAD";

	public static final String RTE_LOGGER_NAME = "jrte";
	public static final String RTC_LOGGER_NAME = "jrtc";
	
	public final static String AUTOMATON_FILE_SUFFIX = ".dfa";
	
	public static final int MAX_ORDINAL = Short.MAX_VALUE;
	public static final byte TYPE_ORDINAL_INDICATOR = (byte)0xff;
	public static final byte TYPE_REFERENCE_NONE = (byte)0x0;
	public static final byte TYPE_REFERENCE_TRANSDUCER = '@';
	public static final byte TYPE_REFERENCE_SIGNAL = '!';
	public static final byte TYPE_REFERENCE_VALUE = '~';
	
	public static final int RTE_SIGNAL_BASE = 256;
	public static final byte[][] RTE_SIGNAL_NAMES = {
		{ 'n', 'u', 'l' },
		{ 'n', 'i', 'l' },
		{ 'e', 'o', 'l' },
		{ 'e', 'o', 's' }
	};
	public enum Signal {
		nul, nil, eos, eol;
		public int signal() {
			return RTE_SIGNAL_BASE + ordinal();
		}
	};
	
	public static final byte[] EMPTY = { };
	public static final byte[] ANONYMOUS_VALUE_NAME = EMPTY;
	public static final byte[] ANONYMOUS_VALUE_REFERENCE = { TYPE_REFERENCE_VALUE };
	public static final byte[][] ANONYMOUS_VALUE_PARAMETER = { ANONYMOUS_VALUE_REFERENCE };
	public static final int ANONYMOUS_VALUE_ORDINAL = 0;
	
	public Base() {
		super();
	}
	
	static public boolean isAnonymousValueReference(final byte bytes[]) {
		return (bytes.length == 1) && (bytes[0] == TYPE_REFERENCE_VALUE);
	}
	
	static public boolean isReferenceOrdinal(final byte bytes[]) {
		return (bytes.length == 4) && (bytes[0] == TYPE_ORDINAL_INDICATOR);
	}
	
	static public byte getReferenceType(final byte bytes[]) {
		if (bytes.length > 0) {
			int typePosition = isReferenceOrdinal(bytes) ? 1 : 0;
			switch (bytes[typePosition]) {
			case TYPE_REFERENCE_TRANSDUCER:
			case TYPE_REFERENCE_SIGNAL:
			case TYPE_REFERENCE_VALUE:
				return bytes[typePosition];
			default:
				break;
			}
		}
		return TYPE_REFERENCE_NONE;
	}
	
	static public byte[] getNameReference(final byte reference[], byte type) {
		if (!isReferenceOrdinal(reference) && getReferenceType(reference) != TYPE_REFERENCE_NONE) {
			byte[] name = new byte[reference.length - 1];
			System.arraycopy(reference, 1, name, 0, name.length);
		}
		return null;
	}

	static public byte[] getReferenceName(final byte reference[]) {
		if (!isReferenceOrdinal(reference) && getReferenceType(reference) != TYPE_REFERENCE_NONE) {
			byte[] name = new byte[reference.length - 1];
			System.arraycopy(reference, 1, name, 0, name.length);
			return name;
		}
		return null;
	}
	
	static public int decodeReferenceOrdinal(int type, final byte bytes[]) {
		if (isReferenceOrdinal(bytes) && getReferenceType(bytes) == type) {
			switch (bytes[1]) {
			case TYPE_REFERENCE_TRANSDUCER:
			case TYPE_REFERENCE_SIGNAL:
			case TYPE_REFERENCE_VALUE:
				return (Byte.toUnsignedInt(bytes[2]) << 8) | Byte.toUnsignedInt(bytes[3]);
			default:
				break;
			}
		}
		return Integer.MIN_VALUE;
	}
	
	static public byte[] encodeReferenceOrdinal(byte type, int ordinal) {
		assert ordinal <= Base.MAX_ORDINAL;
		byte bytes[] = new byte[] { TYPE_ORDINAL_INDICATOR, type, (byte)((ordinal & 0xff00) >> 8), (byte)(ordinal & 0xff)};
		assert ordinal == decodeReferenceOrdinal(type, bytes);
		return bytes;
	}
	
	static public int decodeInt(final byte bytes[], final int length) throws NumberFormatException {
		int value = 0, sign = 1;
		for (int i = 0; i < length; i++) {
			if (bytes[i] == '-') {
				sign = (value == 0) ? -1 : 0;
			} else if (bytes[i] >= '0' && bytes[i] <= '9') {
				value = (value * 10) + (bytes[i] - '0');
			} else {
				sign = 0;
			}
			if (sign == 0) {
				throw new NumberFormatException("Not a number " + Bytes.decode(bytes, length));
			}
		}
		return sign * value;
	}
}