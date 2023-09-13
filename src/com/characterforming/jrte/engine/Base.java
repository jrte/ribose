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
import java.nio.charset.CodingErrorAction;
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
public final class Base {
	private Base() {
	}

	/** previous and current version strings */
	public static final String RTE_VERSION = "ribose-0.0.2";
	public static final String RTE_PREVIOUS = "ribose-0.0.1";

	/** '.dfa', filename suffix for saved ginr automata */
	public static final String AUTOMATON_FILE_SUFFIX = ".dfa";
	/** (65536), least upper bound for transducer/effectors/field/signal enumerators */
	public static final int MAX_ORDINAL = Short.MAX_VALUE;
	/** (256 = {@code !nul}), least signal ordinal value */
	public static final int RTE_SIGNAL_BASE = 256;

	/** End of line sequence */
	public static final String LINEEND = System.getProperty("line.separator", "\n");

	private static final int FILE_LOGGER_COUNT = 2;
	private static final int FILE_LOGGER_LIMIT = 1024 * 1024;
	private static final int INPUT_BUFFER_SIZE = Integer.parseInt(System.getProperty("ribose.inbuffer.size", "65536"));
	private static final int OUTPUT_BUFFER_SIZE = Integer.parseInt(System.getProperty("ribose.outbuffer.size", "8196"));
	private static final Charset runtimeCharset = Charset.forName(System.getProperty("ribose.runtime.charset", "UTF-8"));
	private static final Logger rtcLogger = Logger.getLogger("ribose-compile");
	private static final Logger rteLogger = Logger.getLogger("ribose-runtime");

	/**
	 * Get a reference to the compiler logger. This should be used only
	 * in compilation contexts.
	 *
	 * @return the compiler logger
	 */
	public static void startLogging() {
		Base.startLogger(Base.rtcLogger, false);
		Base.startLogger(Base.rteLogger, false);
	}

	/**
	 * Get a reference to the runtime logger.
	 *
	 * @return the runtime logger
	 */
	public static Logger getCompileLogger() {
		return Base.rtcLogger;
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
	 * Finalize all loggers
	 */
	public static void endLogging() {
		Base.endLogger(Base.rtcLogger);
		Base.endLogger(Base.rteLogger);
	}

	/**
	 * Instantiate a new {@code CharsetDecoder}. All textual data in ribose models
	 * are represented in encoded form (eg, UTF-8 byte arrays).
	 *
	 * @return a new CharsetDecoder insstance
	 */
	public static CharsetDecoder newCharsetDecoder() {
		return Base.runtimeCharset.newDecoder()
			.onUnmappableCharacter(CodingErrorAction.REPLACE)
			.onMalformedInput(CodingErrorAction.REPLACE);
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
	 * Decode an integer from a UTF-8 byte array.
	 *
	 * @param bytes  The UTF-8 byte array
	 * @param length The number of bytes to decode, starting from 0
	 * @return the decoded integer
	 * @throws NumberFormatException on error
	 */
	public static int decodeInt(final byte[] bytes, int length) throws NumberFormatException {
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
			FileHandler fh = new FileHandler(logger.getName() + "-%g.log",
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