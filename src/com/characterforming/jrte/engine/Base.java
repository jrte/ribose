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

package com.characterforming.jrte.engine;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.characterforming.ribose.base.Bytes;

/**
 * This {@code Base} class provides commonly used defintions that are
 * used across the ribose framework.
 *
 * @author Kim Briggs
 */
public final class Base {
	private Base() {
	}

	/** previous and current version strings */
	public static final String RTE_VERSION = "ribose-0.0.2";
	public static final String RTE_PREVIOUS = "ribose-0.0.1";

	/** '.dfa', filename suffix for saved ginr automata */
	public static final String AUTOMATON_FILE_SUFFIX = ".dfa";
	/**
	 * (65536), least upper bound for transducer/effectors/field/signal enumerators
	 */
	public static final int MAX_ORDINAL = Short.MAX_VALUE;
	/** (256 = {@code !nul}), least signal ordinal value */
	public static final int RTE_SIGNAL_BASE = 256;

	/**
	 * type decoration prefix (type decorations declared here are for internal use)
	 */
	public static final byte TYPE_ORDINAL_INDICATOR = (byte) 0xff;
	/**
	 * type decoration for ginr tokens representing transducers in ribose patterns
	 */
	public static final byte TYPE_REFERENCE_TRANSDUCER = '@';
	/** type decoration for ginr tokens representing signals in ribose patterns */
	public static final byte TYPE_REFERENCE_SIGNAL = '!';
	/** type decoration for ginr tokens representing fields in ribose patterns */
	public static final byte TYPE_REFERENCE_FIELD = '~';
	/** null value for type decoration */
	public static final byte TYPE_REFERENCE_NONE = (byte) 0x0;

	/** End of line sequence */
	public static final String LINEEND = System.getProperty("line.separator", "\n");

	private static final int FILE_LOGGER_COUNT = 2;
	private static final int FILE_LOGGER_LIMIT = 1024 * 1024;
	private static final int INPUT_BUFFER_SIZE = Integer.parseInt(System.getProperty("ribose.inbuffer.size", "65536"));
	private static final int OUTPUT_BUFFER_SIZE = Integer.parseInt(System.getProperty("ribose.outbuffer.size", "8196"));
	private static final Charset runtimeCharset = Charset.forName(System.getProperty("ribose.runtime.charset", "UTF-8"));
	private static final CharsetEncoder encoder = runtimeCharset.newEncoder();
	private static final Bytes[] RTE_SIGNAL_NAMES = {
		Bytes.encode(Base.encoder, "nul"),
		Bytes.encode(Base.encoder, "nil"),
		Bytes.encode(Base.encoder, "eol"),
		Bytes.encode(Base.encoder, "eos")
	};
	
	private static final Logger rtcLogger = Logger.getLogger("ribose-compile");
	private static final Logger rteLogger = Logger.getLogger("ribose-runtime");
	private static final Logger rtmLogger = Logger.getLogger("ribose-metrics");

	/**
	 * Get a signal name given its 0-based ordinal
	 * 
	 * @param signal 0-based (nul == 0) signal ordinal
	 * @return the UTF-8 encoded name of the signal
	 */
	public static Bytes getSignalName(int signal) {
		return Base.RTE_SIGNAL_NAMES[signal];
	}
	/**
	 * Get a reference to the compiler logger. This should be used only
	 * in compilation contexts.
	 *
	 * @return the compiler logger
	 */
	public static void startLogging() {
		Base.startLogger(Base.rtcLogger, true);
		Base.startLogger(Base.rteLogger, true);
		Base.startLogger(Base.rtmLogger, false);
	}

	/**
	 * Get a reference to the runtime logger.
	 *
	 * @return the runtime logger
	 */
	public static Logger getCompileLogger() {
		return Base.rteLogger;
	}

	/**
	 * Get a reference to the runtime logger.
	 *
	 * @return the runtime logger
	 */
	public static Logger getRuntimeLogger() {
		return Base.rteLogger;
	}

	/**
	 * Get a reference to the compiler logger. This should be used only
	 * in compilation contexts.
	 *
	 * @return the compiler logger
	 */
	public static Logger getMetricsLogger() {
		return Base.rtmLogger;
	}

	/**
	 * Finalize all loggers
	 */
	public static void endLogging() {
		Base.endLogger(Base.rtcLogger);
		Base.endLogger(Base.rteLogger);
		Base.endLogger(Base.rtmLogger);
	}

	/**
	 * Instantiate a new {@code CharsetDecoder}. All textual data in ribose models
	 * are represented in encoded form (eg, UTF-8 byte arrays).
	 *
	 * @return a new CharsetDecoder insstance
	 */
	public static CharsetDecoder newCharsetDecoder() {
		return Base.runtimeCharset.newDecoder();
	}

	/**
	 * Instantiate a new {@code CharsetEncoder}. All textual data in ribose models
	 * are represented in encoded form (eg, UTF-8 byte arrays).
	 *
	 * @return a new CharsetEncoder instance
	 */
	public static CharsetEncoder newCharsetEncoder() {
		return Base.runtimeCharset.newEncoder();
	}

	/**
	 * Get the size (in bytes) to use for input buffers.
	 *
	 * @return input buffer size in bytes
	 */
	public static int getInBufferSize() {
		return Base.INPUT_BUFFER_SIZE;
	}

	/**
	 * Get the size (in bytes) to use for output buffers.
	 *
	 * @return output buffer size in bytes
	 */
	public static int getOutBufferSize() {
		return Base.OUTPUT_BUFFER_SIZE;
	}

	/**
	 * Check for reference ordinal (a 4-byte encoding of a field, signal
	 * or transducer ordinal).
	 *
	 * @param bytes Encoded reference ordinal
	 * @return true if {@code bytes} encodes a reference ordinal
	 */
	public static boolean isReferenceOrdinal(final byte[] bytes) {
		return (bytes != null) && (bytes.length == 4) && (bytes[0] == TYPE_ORDINAL_INDICATOR);
	}

	/**
	 * Get reference type from an encoded reference ordinal with type indicator
	 * prefix
	 * (eg {@code [\ff ! 256]} encodes {@code `!nul`} as an effector parameter).
	 *
	 * @param bytes Encoded reference ordinal
	 * @return a {@code byte} representing the reference type
	 * @see TYPE_REFERENCE_SIGNAL
	 * @see TYPE_REFERENCE_TRANSDUCER
	 * @see TYPE_REFERENCE_VALUE
	 */
	public static byte getReferenceType(final byte[] bytes) {
		if (isReferenceOrdinal(bytes)) {
			switch (bytes[1]) {
				case TYPE_REFERENCE_TRANSDUCER:
				case TYPE_REFERENCE_SIGNAL:
				case TYPE_REFERENCE_FIELD:
					return bytes[1];
				default:
					break;
			}
		}
		return TYPE_REFERENCE_NONE;
	}

	/**
	 * Get reference type from an encoded referent ordinal without type indicator
	 * prefix
	 * (eg, [! 256] encodes {@code nul} as an input signal).
	 *
	 * @param bytes Encoded reference ordinal
	 * @return a {@code byte} representing the reference type
	 * @see TYPE_REFERENCE_SIGNAL
	 * @see TYPE_REFERENCE_TRANSDUCER
	 * @see TYPE_REFERENCE_VALUE
	 */
	static public byte getReferentType(final byte bytes[]) {
		if (bytes != null && !isReferenceOrdinal(bytes)) {
			switch (bytes[0]) {
				case TYPE_REFERENCE_TRANSDUCER:
				case TYPE_REFERENCE_SIGNAL:
				case TYPE_REFERENCE_FIELD:
					return bytes[0];
				default:
					break;
			}
		}
		return TYPE_REFERENCE_NONE;
	}

	/**
	 * Check for reference name (a ginr token with a type prefix byte, eg
	 * {@code `!nil`}).
	 *
	 * @param reference Bytes to check
	 * @return the reference name if {@code bytes} encodes a reference, or null
	 */
	public static byte[] getReferenceName(final byte reference[]) {
		if (reference != null) {
			switch (getReferentType(reference)) {
			case TYPE_REFERENCE_TRANSDUCER:
			case TYPE_REFERENCE_SIGNAL:
			case TYPE_REFERENCE_FIELD:
				byte[] name = new byte[reference.length - 1];
				System.arraycopy(reference, 1, name, 0, name.length);
				return name;
			default:
				break;
			}
		}
		return Bytes.EMPTY_BYTES;
	}

	/**
	 * Decode a reference ordinal. This will assert if assertions are enabled
	 * and the {@code bytes} array is not 4-byte encoded reference ordinal
	 * but will otherwise presume that reference is well-formed and decode
	 * the last 2 bytes as a big-endian 16-bit reference ordinal.
	 *
	 * @param type  Expected reference type
	 * @param bytes Bytes to check
	 * @return the reference ordinal
	 * @throws RiboseException if bytes do not contain a well formed reference ordinal
	 */
	public static int decodeReferenceOrdinal(int type, final byte bytes[]) {
		assert bytes != null 
		&& getReferenceType(bytes) == type
		&& ((bytes[1] == TYPE_REFERENCE_FIELD)
			|| (bytes[1] == TYPE_REFERENCE_SIGNAL)
			|| (bytes[1] == TYPE_REFERENCE_TRANSDUCER));
		return (Byte.toUnsignedInt(bytes[2]) << 8) | Byte.toUnsignedInt(bytes[3]);
	}

	/**
	 * Encode a reference ordinal.
	 *
	 * @param type    The reference type
	 * @param ordinal The reference ordinal
	 * @return the enc oded reference ordinal
	 * @throws RiboseException
	 */
	public static byte[] encodeReferenceOrdinal(byte type, int ordinal) {
		byte bytes[] = ordinal <= Base.MAX_ORDINAL
		? new byte[] { TYPE_ORDINAL_INDICATOR, type, (byte) ((ordinal & 0xff00) >> 8), (byte) (ordinal & 0xff) }
		: Bytes.EMPTY_BYTES;
		return ordinal == decodeReferenceOrdinal(type, bytes) ? bytes : Bytes.EMPTY_BYTES;
	}

	/**
	 * Decode an integer from a UTF-8 byte array.
	 *
	 * @param bytes  The UTF-8 byte array
	 * @param length The number of bytes to decode, starting from 0
	 * @return the decoded integer
	 * @throws NumberFormatException on error
	 */
	static public int decodeInt(final byte bytes[], int length) throws NumberFormatException {
		int value = 0;
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

	private static final void startLogger(Logger logger, boolean useParentHandler) {
		try {
			FileHandler fh = new FileHandler(logger.getName() + "%g.log",
				Base.FILE_LOGGER_LIMIT, Base.FILE_LOGGER_COUNT, true);
			fh.setFormatter(new SimpleFormatter());
			fh.setLevel(Level.FINE);
			logger.addHandler(fh);
			logger.setUseParentHandlers(useParentHandler);
		} catch (Exception e) {
			logger.getParent().log(Level.SEVERE, e, () -> String.format("Unable to attach file log handler for %1$s",
				logger.getName()));
		}
		logger.setLevel(Level.FINE);
	}

	private static final void endLogger(Logger logger) {
		for (Handler h : logger.getHandlers()) {
			h.close();
		}
	}
}