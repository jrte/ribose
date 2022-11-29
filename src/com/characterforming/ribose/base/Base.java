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
 * LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.ribose.base;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import com.characterforming.ribose.ITransductor;

/**
 * This {@code Base} class provides commonly used defintions that are
 * used across the ribose framework.
 * 
 * @author Kim Briggs
 */
public class Base {
	public static final String RTE_VERSION = "ribose-HEAD";
	
	public static final String RTE_LOGGER_NAME = "ribose-runtime";
	public static final String RTC_LOGGER_NAME = "ribose-compile";
	
	public static final String AUTOMATON_FILE_SUFFIX = ".dfa";
	public static final int MAX_ORDINAL = Short.MAX_VALUE;
	public static final byte TYPE_ORDINAL_INDICATOR = (byte)0xff;
	public static final byte TYPE_REFERENCE_NONE = (byte)0x0;
	public static final byte TYPE_REFERENCE_TRANSDUCER = '@';
	public static final byte TYPE_REFERENCE_SIGNAL = '!';
	public static final byte TYPE_REFERENCE_VALUE = '~';
	
	private static int INPUT_BUFFER_SIZE = Integer.parseInt(System.getProperty("ribose.inbuffer.size", "64436"));
	private static int OUTPUT_BUFFER_SIZE = Integer.parseInt(System.getProperty("ribose.outbuffer.size", "8196"));
	private static final Charset runtimeCharset = Charset.forName(System.getProperty("ribose.runtime.charset", "UTF-8"));
	private static final CharsetEncoder encoder = runtimeCharset.newEncoder();
	public static final Bytes[] RTE_SIGNAL_NAMES = {
		Bytes.encode(Base.encoder, "nul"),
		Bytes.encode(Base.encoder, "nil"),
		Bytes.encode(Base.encoder, "eol"),
		Bytes.encode(Base.encoder, "eos")
	};
	public static final int RTE_SIGNAL_BASE = 256;
	
	/**
	 * General signals available in all ribose models.
	 * <br><br>
	 * Transductors send {@code nul} to transductions when no transition is defined for
	 * the current symbol from the input stream. This gives the transduction a first chance
   * synchronize with the input. If no transition is defined for the received {@code nul}
   * the {@link ITransductor#run()} method will throw {@code DomainErrorException}. 
	 * <br><br>
	 * Transductors send {@code eos} to transductions when the input stack runs dry. 
	 * If the receiving transduction has no transition defined for received {@code eos}
	 * it is ignored. In any case {@code run()} will return normally with {@code Status.PAUSED}
	 * after sending {@code eos}.
	 * 
	 * @author kb
	 *
	 */
	public enum Signal {
		/**
		 * Signals first chance to handle missing transition on current input symbol
		 */
		nul, 
		/**
		 * Signals anything, used as a generic out-of-band prompt to trigger actions 
		 */
		nil, 
		/**
		 * Signals end of feature, used as a generic feature delimiter
		 */
		eol,
		/**
		 * Signals end of transduction input
		 */
		eos;
		
		/**
		 * Signal name.
		 * 
		 * @return The signal name as lookup key
		 */
		public Bytes key() { return Base.RTE_SIGNAL_NAMES[this.ordinal()]; }
		
		/**
		 * Signal ordinal values are mapped to the end of the base {@code (0x0..0xff)} 
		 * input range. This range can be further extended with additional signal 
		 * ordinal values defined for domain-specific transduction models.
		 *  
		 * @return the signal ordinal value
		 */
		public int signal() {
			return RTE_SIGNAL_BASE + ordinal();
		}
	};
	
	public static final byte[] EMPTY = { };
	public static final int CLEAR_ALL_VALUES = 2;
	public static final int CLEAR_ANONYMOUS_VALUE = 1;
	public static final int ANONYMOUS_VALUE_ORDINAL = 0;
	public static final byte[] ANONYMOUS_VALUE_NAME = EMPTY;
	public static final byte[] ANONYMOUS_VALUE_REFERENCE = { TYPE_REFERENCE_VALUE };
	public static final byte[][] ANONYMOUS_VALUE_PARAMETER = { ANONYMOUS_VALUE_REFERENCE };
	public static final byte[] ALL = { '~', '*' };
	public static final byte[] ALL_VALUE_NAME = { '*' };
	public static final byte[] ALL_VALUE_PARAMETER = ALL;

	public Base() {
		super();
	}
	
	/**
	 * Get a runtime charset instance. This is used to encode and decode ribose 
	 * runtime resources (effector names, transducer names, all textual data in
	 * ribose models are represented in encoded form, eg UTF-8 byte arrays).
	 * 
	 * @return a reference to the runtime charset insstance
	 */
	static public Charset getRuntimeCharset() {
		return Base.runtimeCharset;
	}

	/**
	 * Get the size (in bytes) to use for input buffers.
	 * 
	 * @return input buffer size in bytes
	 */
	static public int getInBufferSize() {
		return Base.INPUT_BUFFER_SIZE;
	}

	/**
	 * Get the size (in bytes) to use for output buffers.
	 * 
	 * @return output buffer size in bytes
	 */
	static public int getOutBufferSize() {
		return Base.OUTPUT_BUFFER_SIZE;
	}

	/**
	 * Check for value reference (a 4-byte encoding of a value ordinal).
	 * 
	 * @param bytes Bytes to check
	 * @return true if {@code bytes} contains a value reference
	 */
	static public boolean isAnonymousValueReference(final byte bytes[]) {
		return (bytes.length == 1) && (bytes[0] == TYPE_REFERENCE_VALUE);
	}
	
	/**
	 * Check for reference ordinal (a 4-byte encoding of a value, signal
	 * or transducer ordinal).
	 * 
	 * @param bytes Bytes to check
	 * @return true if {@code bytes} encodes a reference ordinal
	 */
	static public boolean isReferenceOrdinal(final byte bytes[]) {
		return (bytes.length == 4) && (bytes[0] == TYPE_ORDINAL_INDICATOR);
	}
	
	/**
	 * Get reference type from and encoded refernce ordinal.
	 * 
	 * @param bytes Bytes to check
	 * @return a {@code byte} representing the reference type
	 * @see TYPE_REFERENCE_SIGNAL
	 * @see TYPE_REFERENCE_TRANSDUCER
	 * @see TYPE_REFERENCE_VALUE
	 */
	static public byte getReferenceType(final byte bytes[]) {
		if (isReferenceOrdinal(bytes)) {
			switch (bytes[1]) {
			case TYPE_REFERENCE_TRANSDUCER:
			case TYPE_REFERENCE_SIGNAL:
			case TYPE_REFERENCE_VALUE:
				return bytes[1];
			default:
				break;
			}
		}
		return TYPE_REFERENCE_NONE;
	}
	
	/**
	 * Get reference type from and encoded refernce ordinal.
	 * 
	 * @param bytes Bytes to check
	 * @return a {@code byte} representing the reference type
	 * @see TYPE_REFERENCE_SIGNAL
	 * @see TYPE_REFERENCE_TRANSDUCER
	 * @see TYPE_REFERENCE_VALUE
	 */
	static public byte getReferentType(final byte bytes[]) {
		if (!isReferenceOrdinal(bytes)) {
			switch (bytes[0]) {
			case TYPE_REFERENCE_TRANSDUCER:
			case TYPE_REFERENCE_SIGNAL:
			case TYPE_REFERENCE_VALUE:
				return bytes[0];
			default:
				break;
			}
		}
		return TYPE_REFERENCE_NONE;
	}
	
	/**
	 * Check for reference name (a byte array with a type prefix byte).
	 * 
	 * @param reference Bytes to check
	 * @return the reference name if {@code bytes} encodes a reference, or null
	 */
	static public byte[] getReferenceName(final byte reference[]) {
		switch (getReferentType(reference)) {
		case TYPE_REFERENCE_TRANSDUCER:
		case TYPE_REFERENCE_SIGNAL:
		case TYPE_REFERENCE_VALUE:
			byte[] name = new byte[reference.length - 1];
			System.arraycopy(reference, 1, name, 0, name.length);
			return name;
		default:
			break;
		}
		return null;
	}
	
	/**
	 * Decode a reference ordinal.
	 * 
	 * @param type Expected reference type
	 * @param bytes Bytes to check
	 * @return the reference ordinal or a negative integer if none
	 */
	static public int decodeReferenceOrdinal(int type, final byte bytes[]) {
		assert isReferenceOrdinal(bytes);
		if (getReferenceType(bytes) == type) {
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
	
	/**
	 * Encode a reference ordinal.
	 * 
	 * @param type The reference type 
	 * @param ordinal The reference ordinal
	 * @return the reference ordinal or a negative integer if none
	 */
	static public byte[] encodeReferenceOrdinal(byte type, int ordinal) {
		assert ordinal <= Base.MAX_ORDINAL;
		byte bytes[] = new byte[] { TYPE_ORDINAL_INDICATOR, type, (byte)((ordinal & 0xff00) >> 8), (byte)(ordinal & 0xff) };
		assert ordinal == decodeReferenceOrdinal(type, bytes);
		return bytes;
	}
	
	/**
	 * Decode an integer from a UTF-8 byte array.
	 * 
	 * @param bytes The UTF-8 byte array 
	 * @param length The number of bytes to decode, starting from 0
	 * @return the decoded integer
	 * @throws NumberFormatException on error
	 */
	static public int decodeInt(final byte bytes[], int length) throws NumberFormatException {
		int value = 0;
		assert bytes.length >= length;
		if (length > bytes.length) {
			length = bytes.length;
		}
		int sign = (length > 0 && bytes[0] == '-') ? -1 : 1;
		for (int i = (sign > 0) ? 0 : 1; i < length && sign != 0; i++) {
			if (bytes[i] >= '0' && bytes[i] <= '9') {
				value = (value * 10) + (bytes[i] - '0');
			} else {
				sign = 0;
			}
		}
		if (sign == 0) {
			throw new NumberFormatException("Base::decodeInt(): Not a number");
		}
		return sign * value;
	}
}