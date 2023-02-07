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

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * This {@code Base} class provides commonly used defintions that are
 * used across the ribose framework.
 *
 * @author Kim Briggs
 */
public class Base {
	/** 'ribose-0.0.0', current version */
	public static final String RTE_VERSION = "ribose-0.0.1";
	public static final String RTE_PREVIOUS = "ribose-0.0.0";

	/** '.dfa', filename suffix for saved ginr automata */
	public static final String AUTOMATON_FILE_SUFFIX = ".dfa";
	/** (65536), least upper bound for transducer/effectors/value/signal enumerators */
	public static final int MAX_ORDINAL = Short.MAX_VALUE;
	/** (256 = {@code !nul}), least signal ordinal value */
	public static final int RTE_SIGNAL_BASE = 256;

	/** type decoration prefix (type decorations declared here are for internal use) */
	public static final byte TYPE_ORDINAL_INDICATOR = (byte)0xff;
	/** type decoration for ginr tokens representing transducers in ribose patterns */
	public static final byte TYPE_REFERENCE_TRANSDUCER = '@';
	/** type decoration for ginr tokens representing signals in ribose patterns */
	public static final byte TYPE_REFERENCE_SIGNAL = '!';
	/** type decoration for ginr tokens representing values in ribose patterns */
	public static final byte TYPE_REFERENCE_VALUE = '~';
	/** null value for type decoration */
	public static final byte TYPE_REFERENCE_NONE = (byte)0x0;

	/** End of line sequence */
	public static final String lineEnd = System.getProperty("line.separator", "\n");

	private static final int FILE_LOGGER_COUNT = 2;
	private static final int FILE_LOGGER_LIMIT = 1024*1024;
	private static final int INPUT_BUFFER_SIZE = Integer.parseInt(System.getProperty("ribose.inbuffer.size", "65536"));
	private static final int OUTPUT_BUFFER_SIZE = Integer.parseInt(System.getProperty("ribose.outbuffer.size", "8196"));
	private static final Charset runtimeCharset = Charset.forName(System.getProperty("ribose.runtime.charset", "UTF-8"));
	private static final CharsetEncoder encoder = runtimeCharset.newEncoder();
	static final Bytes[] RTE_SIGNAL_NAMES = {
		Bytes.encode(Base.encoder, "nul"),
		Bytes.encode(Base.encoder, "nil"),
		Bytes.encode(Base.encoder, "eol"),
		Bytes.encode(Base.encoder, "eos")
	};

	private static Logger rtcLogger = null;
	private static Logger rteLogger = null;
	private static Logger rtmLogger = null;

	private static final String RTE_LOGGER_NAME = "ribose-runtime";
	private static final String RTC_LOGGER_NAME = "ribose-compile";
	private static final String RTM_LOGGER_NAME = "ribose-metrics";

	/**
	 * Get a reference to the compiler logger. This should be used only
	 * in compilation contexts.
	 *
	 * @return the compiler logger
	 */
	public static Logger getCompileLogger() {
		if (Base.rtcLogger == null) {
			synchronized (Base.RTE_VERSION) {
				if (Base.rtcLogger == null) {
					Base.rtcLogger = Logger.getLogger(Base.RTC_LOGGER_NAME);
					try {
						for (Handler h : Base.rtcLogger.getHandlers()) {
							h.setLevel(Level.INFO);
						}
						FileHandler rtcHandler = new FileHandler(Base.rtcLogger.getName() + "%g.log",
							Base.FILE_LOGGER_LIMIT, Base.FILE_LOGGER_COUNT, true);
						rtcHandler.setFormatter(new SimpleFormatter());
						rtcHandler.setLevel(Level.FINE);
						Base.rtcLogger.addHandler(rtcHandler);
					} catch (Exception e) {
						Base.rtcLogger.getParent().log(Level.SEVERE, "Unable to attach file log handler for " + Base.rtcLogger.getName());
					}
					Base.rtcLogger.setLevel(Level.FINE);
				}
			}
		}
		return Base.rtcLogger;
	}

	/**
	 * Get a reference to the runtime logger.
	 *
	 * @return the runtime logger
	 */
	public static Logger getRuntimeLogger() {
		if (Base.rteLogger == null) {
			synchronized (Base.RTE_VERSION) {
				if (Base.rteLogger == null) {
					Base.rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
					try {
						for (Handler h : Base.rteLogger.getHandlers()) {
							h.setLevel(Level.INFO);
						}
						FileHandler rteHandler = new FileHandler(Base.rteLogger.getName() + "%g.log",
							Base.FILE_LOGGER_LIMIT, Base.FILE_LOGGER_COUNT, true);
						rteHandler.setFormatter(new SimpleFormatter());
						rteHandler.setLevel(Level.FINE);
						Base.rteLogger.addHandler(rteHandler);
					} catch (Exception e) {
						Base.rteLogger.getParent().log(Level.SEVERE, "Unable to attach file log handler for " + Base.rteLogger.getName());
					}
					Base.rteLogger.setLevel(Level.FINE);
				}
			}
		}
		return Base.rteLogger;
	}

	/**
	 * Get a reference to the compiler logger. This should be used only
	 * in compilation contexts.
	 *
	 * @return the compiler logger
	 */
	public static Logger getMetricsLogger() {
		if (Base.rtmLogger == null) {
			synchronized (Base.RTE_VERSION) {
				if (Base.rtmLogger == null) {
					Base.rtmLogger = Logger.getLogger(Base.RTM_LOGGER_NAME);
					try {
						FileHandler rtmHandler = new FileHandler(Base.rtmLogger.getName() + "%g.log",
						Base.FILE_LOGGER_LIMIT, Base.FILE_LOGGER_COUNT, true);
						rtmHandler.setFormatter(new SimpleFormatter());
						rtmHandler.setLevel(Level.FINE);
						Base.rtmLogger.addHandler(rtmHandler);
						Base.rtmLogger.setUseParentHandlers(false);
					} catch (Exception e) {
						Base.rtmLogger.getParent().log(Level.SEVERE, "Unable to attach file log handler for " + Base.rtmLogger.getName());
					}
					Base.rtmLogger.setLevel(Level.FINE);
				}
			}
		}
		return Base.rtmLogger;
	}

	/**
	 * Instantiate a new {@code CharsetDecoder}. All textual data in ribose models
	 * are represented in encoded form (eg, UTF-8 byte arrays).
	 *
	 * @return a new CharsetDecoder insstance
	 */
	static public CharsetDecoder newCharsetDecoder() {
		return Base.runtimeCharset.newDecoder();
	}

	/**
	 * Instantiate a new {@code CharsetEncoder}. All textual data in ribose models
	 * are represented in encoded form (eg, UTF-8 byte arrays).
	 *
	 * @return a new CharsetEncoder instance
	 */
	static public CharsetEncoder newCharsetEncoder() {
		return Base.runtimeCharset.newEncoder();
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
	 * Check for reference ordinal (a 4-byte encoding of a value, signal
	 * or transducer ordinal).
	 *
	 * @param bytes Encoded reference ordinal
	 * @return true if {@code bytes} encodes a reference ordinal
	 */
	static public boolean isReferenceOrdinal(final byte bytes[]) {
		return (bytes.length == 4) && (bytes[0] == TYPE_ORDINAL_INDICATOR);
	}

	/**
	 * Get reference type from an encoded reference ordinal with type indicator prefix
	 * (eg {@code [\ff ! 256]} encodes {@code `!nul`} as an effector parameter).
	 *
	 * @param bytes Encoded reference ordinal
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
	 * Get reference type from an encoded referent ordinal without type indicator prefix
	 * (eg, [! 256] encodes {@code nul} as an input signal).
	 *
	 * @param bytes Encoded reference ordinal
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
	 * Check for reference name (a ginr token with a type prefix byte, eg {@code `!nil`}).
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
	 * @return the enc oded reference ordinal
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