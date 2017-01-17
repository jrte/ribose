/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.characterforming.jrte.GearboxException;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.TargetNotFoundException;
import com.characterforming.jrte.TransducerNotFoundException;
import com.characterforming.jrte.compile.array.Chars;

/**
 * @author kb
 */
public final class Gearbox {
	private final static Logger logger = Logger.getLogger(Gearbox.class.getName());

	public static final String VERSION = "0.0.0.HEAD";

	private final File gearboxPath;
	private final ITarget target;
	private final RandomAccessFile in;
	private final HashMap<String, Integer> transducerOrdinalMap;
	private final HashMap<String, Integer> signalOrdinalMap;
	private final int signalBase;
	private final Charset charset;
	private final String ioMode;

	private String transducerNameIndex[];
	private Transducer transducerObjectIndex[];
	private long transducerOffsetIndex[];
	private Map<String, Integer> effectorOrdinalMap;
	private byte[][][][] effectorParameterIndex;

	public Gearbox(final File gearboxPath, final Charset charset, final ITarget target, final int signalBase) throws GearboxException, TargetBindingException, TargetNotFoundException {
		this.gearboxPath = gearboxPath;
		this.target = target;
		this.signalBase = signalBase;
		try {
			if (this.gearboxPath.exists() && !this.gearboxPath.delete()) {
				Gearbox.logger.warning(String.format("Unable to delete existing gearbox file %1$s", this.gearboxPath.getPath()));
			}
			if (!this.gearboxPath.createNewFile()) {
				throw new GearboxException(String.format("Unable to create gearbox file %1$s", this.gearboxPath.getPath()));
			}
			this.ioMode = "rw";
			this.in = new RandomAccessFile(this.gearboxPath, this.ioMode);
		} catch (final IOException e) {
			throw new GearboxException(String.format("Gearbox caught an IOException attempting to open '%1$s'",
					this.gearboxPath.getPath()), e);
		}
		boolean abort = true;
		try {
			this.putLong(0);
			this.putInt(this.signalBase);
			this.putString(charset.displayName());
			this.charset = charset;
			this.putString(Gearbox.VERSION);
			this.putString(target.getClass().getName());
			this.signalOrdinalMap = new HashMap<String, Integer>(this.signalBase >> 1);
			for (final String signal : Transduction.RTE_SIGNAL_NAMES) {
				final Integer ordinal = this.signalBase + this.signalOrdinalMap.size();
				this.signalOrdinalMap.put(signal, ordinal);
			}
			this.transducerOrdinalMap = new HashMap<String, Integer>(256);
			this.transducerNameIndex = new String[256];
			this.transducerObjectIndex = new Transducer[256];
			this.transducerOffsetIndex = new long[256];
			abort = false;
		} finally {
			if (abort && this.in != null) {
				try {
					this.in.close();
				} catch (final IOException e) {
					e.printStackTrace(System.err);
				}
			}
		}
	}

	public Gearbox(final File gearboxPath, final ITarget target) throws GearboxException, TargetBindingException {
		this.gearboxPath = gearboxPath;
		try {
			this.ioMode = "r";
			this.in = new RandomAccessFile(this.gearboxPath, this.ioMode);
		} catch (final IOException e) {
			throw new GearboxException(String.format("Gearbox caught an IOException attempting to open '%1$s'",
					this.gearboxPath.getPath()), e);
		}
		// 0 --
		// L:Absolute offset to name/offset index
		// I:Base signal ordinal
		// S:Charset name
		// S:Gearbox version
		// S:Target class name
		// name/offset index --
		// I: index length
		// [S:(target|transducer)name, L:absolute offset to object]: for each
		// indexed object
		// I: Signal count
		// [S: Signal name, I: signal ordinal]: for each signal
		boolean abort = true;
		try {
			long indexPosition = this.getLong();
			this.signalBase = this.getInt();
			final String charsetName = this.getString();
			try {
				this.charset = Charset.forName(charsetName);
			} catch (final IllegalCharsetNameException e) {
				throw new GearboxException(String.format("Unable to instantiate Charset '%1$s' from gearbox file '%2$s'", charsetName, gearboxPath.getPath()), e);
			} catch (final IllegalArgumentException e) {
				throw new GearboxException(String.format("Unable to instantiate Charset '%1$s' from gearbox file '%2$s'", charsetName, gearboxPath.getPath()), e);
			}
			final String fileVersion = this.getString();
			if (!fileVersion.equals(Gearbox.VERSION)) {
				throw new GearboxException(String.format("Current gearbox version '%1$s' does not match version string '%2$s' from file '%3$s'", Gearbox.VERSION, fileVersion, gearboxPath.getPath()));
			}
			final String targetClassname = this.getString();
			if (target == null || !targetClassname.equals(target.getClass().getName())) {
				throw new GearboxException(String.format("Wrong target class name '%1$s' -- target class is '%2$s' in gearbox file '%3$s'", target != null ? target.getName() : "null", targetClassname, gearboxPath.getPath()));
			}
			this.target = target;
			try {
				this.in.seek(indexPosition);
				final int indexSize = this.getInt();
				this.transducerOrdinalMap = new HashMap<String, Integer>(indexSize);
				this.transducerNameIndex = new String[indexSize];
				this.transducerObjectIndex = new Transducer[indexSize];
				this.transducerOffsetIndex = new long[indexSize];
				
				for (int transducerOrdinal = 0; transducerOrdinal < indexSize; transducerOrdinal++) {
					String name = this.getString();
					long offset = this.getLong();
					this.transducerOrdinalMap.put(name, transducerOrdinal);
					this.transducerNameIndex[transducerOrdinal] = name;
					this.transducerOffsetIndex[transducerOrdinal] = offset;
					this.transducerObjectIndex[transducerOrdinal] = null;
				}

				final int signalsSize = this.getInt();
				this.signalOrdinalMap = new HashMap<String, Integer>(signalsSize);
				for (int i = 0; i < signalsSize; i++) {
					final String symbol = this.getString();
					final Integer ordinal = this.getInt();
					this.signalOrdinalMap.put(symbol, ordinal);
				}
			} catch (final IOException e) {
				throw new GearboxException(String.format("Gearbox caught an IOException attempting to seek to file position %1$d in gearbox file '%2$s'",
						indexPosition, this.gearboxPath.getPath()), e);
			}
			if (!this.transducerOrdinalMap.containsKey(this.target.getName())) {
				throw new GearboxException(String.format("Target name '%1$s' not found in name offset map for gearbox file '%2$s'", this.target.getName(), this.gearboxPath.getPath()));
			}
			indexPosition = this.transducerOffsetIndex[this.transducerOrdinalMap.get(this.target.getName())];
			try {
				this.in.seek(indexPosition);
				final String targetName = this.getString();
				if (!targetName.equals(target.getName())) {
					throw new TargetBindingException(String.format("Target name '%1$s' does not match gearbox target name '%2$s'", target.getName(), targetName));
				}
				final String[] gearboxEffectorIndex = this.getStringArray();
				this.effectorOrdinalMap = new HashMap<String, Integer>(gearboxEffectorIndex.length);
				for (int i = 0; i < gearboxEffectorIndex.length; i++) {
					this.effectorOrdinalMap.put(gearboxEffectorIndex[i], i);
				}
				final int parameterizedEffectorCount = this.getInt();
				this.effectorParameterIndex = new byte[gearboxEffectorIndex.length][][][];
				for (int i = 0; i < parameterizedEffectorCount; i++) {
					this.effectorParameterIndex[this.effectorOrdinalMap.get(this.getString())] = this.getBytesArrays();
				}
			} catch (final IOException e) {
				throw new GearboxException(String.format("Gearbox caught an IOException attempting to seek to file position %1$d in gearbox file '%2$s'",
						indexPosition, this.gearboxPath.getPath()), e);
			}
			abort = false;
		} finally {
			if (abort && this.in != null) {
				try {
					this.in.close();
				} catch (final IOException e) {
					e.printStackTrace(System.err);
				}
			}
		}
	}

	public void close(boolean commit) throws GearboxException {
		if (this.in != null) {
			try {
				if (commit && this.ioMode.equals("rw")) {
					commit = false;
					final long indexPosition = this.in.length();
					this.putInt(this.transducerOrdinalMap.size());
					int targetNameIndex = this.transducerOrdinalMap.size() - 1;
					for (int transducerNameIndex = 0; transducerNameIndex <= targetNameIndex; transducerNameIndex++) {
						this.putString(this.transducerNameIndex[transducerNameIndex]);
						this.putLong(this.transducerOffsetIndex[transducerNameIndex]);
					}
					this.putInt(this.signalOrdinalMap.size());
					for (final String signal : this.signalOrdinalMap.keySet()) {
						this.putString(signal);
						this.putInt(this.signalOrdinalMap.get(signal));
					}
					this.seek(0);
					this.putLong(indexPosition);
					commit = true;
				}
			} catch (final IOException e) {
				e.printStackTrace(System.err);
			} finally {
				try {
					this.in.close();
				} catch (final IOException e) {
					e.printStackTrace(System.err);
				}
			}
		}
		if (!commit && this.ioMode.equals("rw") && !this.gearboxPath.delete()) {
			Gearbox.logger.warning(String.format("Unable to delete invalid gearbox file %1$s", this.gearboxPath.getPath()));
		}
	}
	
	public Map<String, Integer> getEffectorOrdinalMap() {
		return Collections.unmodifiableMap(this.effectorOrdinalMap);
	}

	public void setEffectorOrdinalMap(final Map<String, Integer> effectorOrdinalMap) {
		this.effectorOrdinalMap = effectorOrdinalMap;
	}

	public byte[][][][] getEffectorParametersIndex() {
		return this.effectorParameterIndex;
	}

	public void setEffectorParametersIndex(final byte[][][][] effectorParameterIndex) {
		this.effectorParameterIndex = effectorParameterIndex;
	}

	public String parameterToString(final byte[][] parameterBytes) {
		final StringBuilder strings = new StringBuilder();
		for (final byte[] bytes : parameterBytes) {
			strings.append('[');
			strings.append(this.getCharset().decode(ByteBuffer.wrap(bytes)).toString());
			strings.append(']');
		}
		return strings.toString();
	}

	public File getGearboxPath() {
		return this.gearboxPath;
	}

	public Charset getCharset() {
		return this.charset;
	}

	public Integer putInputOrdinal(final String input) {
		if (input.length() == 1) {
			return (int) input.charAt(0);
		} else {
			return this.putSignalOrdinal(input);
		}
	}

	public int getSignalBase() {
		return this.signalBase;
	}

	public int getSignalCount() {
		return this.signalOrdinalMap.size();
	}

	public int getSignalLimit() {
		return this.signalBase + this.signalOrdinalMap.size();
	}

	public Integer getSignalOrdinal(final String signal) {
		return this.signalOrdinalMap.get(signal);
	}

	public Integer putSignalOrdinal(final String signal) {
		Integer ordinal = this.signalOrdinalMap.get(signal);
		if (ordinal == null) {
			ordinal = this.signalBase + this.signalOrdinalMap.size();
			this.signalOrdinalMap.put(signal, ordinal);
		}
		return ordinal;
	}

	public char[] getSignalReference(final char[] chars) {
		if (chars != null && chars.length > 1 && chars[0] == Transduction.TYPE_REFERENCE_SIGNAL) {
			String name = new String(chars, 1, chars.length - 1);
			Integer signal = this.putSignalOrdinal(name);
			return new char[] { (char) signal.intValue() };
		}
		return null;
	}

	public String getTransducerReference(final char[] chars) {
		if (chars != null && chars.length > 1 && chars[0] == Transduction.TYPE_REFERENCE_TRANSDUCER) {
			return new String(chars, 1, chars.length - 1);
		}
		return null;
	}

	public ITarget getTarget() {
		return this.target;
	}

	public int addTransducer(String transducerName, long offset) {
		Integer ordinal = transducerOrdinalMap.size();
		if (null == this.transducerOrdinalMap.put(transducerName, ordinal)) {
			if (ordinal >= this.transducerNameIndex.length) {
				int length = ordinal + (ordinal << 1);
				this.transducerNameIndex = Arrays.copyOf(this.transducerNameIndex, length);
				this.transducerOffsetIndex = Arrays.copyOf(this.transducerOffsetIndex, length);
				this.transducerObjectIndex = Arrays.copyOf(this.transducerObjectIndex, length);
			}
			this.transducerNameIndex[ordinal] = transducerName;
			this.transducerOffsetIndex[ordinal] = Long.valueOf(offset);
			this.transducerObjectIndex[ordinal] = null;
		}
		return ordinal.intValue();
	}
	
	public int getTransducerOrdinal(String transducerName) {
		Integer ordinal = this.transducerOrdinalMap.get(transducerName);
		return (null != ordinal) ? ordinal.intValue() : -1;
	}
	
	public String getTransducerName(int transducerOrdinal) {
		return (transducerOrdinal < this.transducerOrdinalMap.size()) ? this.transducerNameIndex[transducerOrdinal] : null;
	}

	public long getTransducerOffset(int transducerOrdinal) {
		return (transducerOrdinal < this.transducerOrdinalMap.size()) ? this.transducerOffsetIndex[transducerOrdinal] : null;
	}

	public Transducer getTransducer(int transducerOrdinal) {
		return (transducerOrdinal < this.transducerOrdinalMap.size()) ? this.transducerObjectIndex[transducerOrdinal] : null;
	}

	public Transducer loadTransducer(final Integer transducerOrdinal) throws TransducerNotFoundException, GearboxException {
		if (transducerOrdinal < this.transducerOrdinalMap.size()) {
			synchronized (this) {
				if (this.transducerObjectIndex[transducerOrdinal] == null) {
					try {
						this.in.seek(transducerOffsetIndex[transducerOrdinal]);
						final String name = this.getString();
						final String targetName = this.getString();
						final int[] inputFilter = this.getIntArray();
						final int[][] transitionMatrix = this.getTransitionMatrix();
						final int[] effectorVector = this.getIntArray();
						this.transducerObjectIndex[transducerOrdinal] = new Transducer(name, targetName, inputFilter, transitionMatrix, effectorVector);
					} catch (final IOException e) {
						throw new GearboxException(
								String.format("Gearbox.loadTransducer(%d) caught an IOException after seek to %d", transducerOrdinal, transducerOffsetIndex[transducerOrdinal]), e);
					} 
				}
				return this.transducerObjectIndex[transducerOrdinal];
			}
		} else {
			return null;
		}
	}

	public int getInt() throws GearboxException {
		long position = 0;
		try {
			position = this.in.getFilePointer();
			return this.in.readInt();
		} catch (final IOException e) {
			throw new GearboxException(String.format(
					"Gearbox.getInt caught an IOException reading int at file position %1$d", position), e);
		}
	}

	public long getLong() throws GearboxException {
		long position = 0;
		try {
			position = this.in.getFilePointer();
			return this.in.readLong();
		} catch (final IOException e) {
			throw new GearboxException(String.format(
					"Gearbox.getInt caught an IOException reading long at file position %1$d", position), e);
		}
	}

	public int[] getIntArray() throws GearboxException {
		int[] ints = null;
		long position = 0;
		try {
			position = this.in.getFilePointer();
			ints = new int[this.in.readInt()];
			for (int i = 0; i < ints.length; i++) {
				ints[i] = this.in.readInt();
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.getBytes caught an IOException reading %1$d bytes at file position %2$d  after file position %3$d",
							ints.length, position, this.getSafeFilePosition()), e);
		}
		return ints;
	}

	public void putTransitionMatrix(final int[][][] matrix) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			final int rows = matrix.length;
			final int columns = rows > 0 ? matrix[0].length : 0;
			this.in.writeInt(rows);
			this.in.writeInt(columns);
			for (int row = 0; row < rows; row++) {
				int count = 0;
				for (int column = 0; column < columns; column++) {
					if (matrix[row][column][1] != 0) {
						count++;
					}
				}
				this.in.writeInt(count);
				for (int column = 0; column < columns; column++) {
					if (matrix[row][column][1] != 0) {
						this.in.writeInt(column);
						this.in.writeInt(matrix[row][column][0]);
						this.in.writeInt(matrix[row][column][1]);
					}
				}
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.putTransitionMatrix caught an IOException writing transition matrix starting at file position %1$d after file position %2$d",
							position, this.getSafeFilePosition()), e);
		}
	}

	public int[][] getTransitionMatrix() throws GearboxException {
		int[][] matrix;
		long position = 0;
		try {
			position = this.in.getFilePointer();
			final int rows = this.in.readInt();
			final int columns = this.in.readInt();
			matrix = new int[rows * columns][2];
			for (int column = 0; column < columns; column++) {
				for (int row = 0; row < rows; row++) {
					final int cell = column * rows + row;
					matrix[cell][0] = column * rows;
					matrix[cell][1] = 0;
				}
			}
			for (int row = 0; row < rows; row++) {
				final int count = this.in.readInt();
				for (int i = 0; i < count; i++) {
					final int column = this.in.readInt();
					final int toState = this.in.readInt();
					final int effect = this.in.readInt();
					matrix[column * rows + row] = new int[] { toState * rows, effect };
				}
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.getTransitionMatrix caught an IOException reading transition matrix starting at file position %1$d after file position %2$d",
							position, this.getSafeFilePosition()), e);
		}
		return matrix;
	}

	public Chars getChars() throws GearboxException {
		return new Chars(this.charset.decode(ByteBuffer.wrap(this.getBytes())));
	}

	public byte[] getBytes() throws GearboxException {
		byte[] bytes = null;
		long position = 0;
		int read = 0;
		try {
			position = this.in.getFilePointer();
			bytes = new byte[this.in.readInt()];
			read = this.in.read(bytes);
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.getBytes caught an IOException reading %1$d bytes at file position %2$d  after file position %3$d",
							bytes.length, position, this.getSafeFilePosition()), e);
		}
		if (read != bytes.length) {
			throw new GearboxException(String.format(
					"Gearbox.getBytes expected %1$d bytes at file position %2$d but read only %3$d", bytes.length,
					position, read));
		}
		return bytes;
	}

	public byte[][] getBytesArray() throws GearboxException {
		byte[][] bytesArray = null;
		long position = 0;
		try {
			position = this.in.getFilePointer();
			bytesArray = new byte[this.in.readInt()][];
			for (int i = 0; i < bytesArray.length; i++) {
				bytesArray[i] = this.getBytes();
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.getBytesArray caught an IOException reading bytes array starting at file position after file position %2$d",
							position, this.getSafeFilePosition()), e);
		}
		return bytesArray;
	}

	public byte[][][] getBytesArrays() throws GearboxException {
		byte[][][] bytesArrays = null;
		long position = 0;
		try {
			position = this.in.getFilePointer();
			bytesArrays = new byte[this.in.readInt()][][];
			for (int i = 0; i < bytesArrays.length; i++) {
				bytesArrays[i] = this.getBytesArray();
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.getBytesArray caught an IOException reading bytes array starting at file position after file position %2$d",
							position, this.getSafeFilePosition()), e);
		}
		return bytesArrays;
	}

	public String getString() throws GearboxException {
		final Charset charset = this.charset != null ? this.charset : Charset.defaultCharset();
		return charset.decode(ByteBuffer.wrap(this.getBytes())).toString();
	}

	public String[] getStringArray() throws GearboxException {
		final byte[][] bytesArray = this.getBytesArray();
		final String[] stringArray = new String[bytesArray.length];
		int i = 0;
		for (final byte[] bytes : bytesArray) {
			stringArray[i++] = this.charset.decode(ByteBuffer.wrap(bytes)).toString();
		}
		return stringArray;
	}

	public long getSafeFilePosition() {
		try {
			return this.in.getFilePointer();
		} catch (final IOException e) {
			return -1;
		}
	}

	public void putChars(final Chars symbol) throws GearboxException {
		this.putBytes(this.charset.encode(CharBuffer.wrap(symbol.getchars())));
	}

	public void putBytes(final byte[] bytes) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.in.writeInt(bytes.length);
			this.in.write(bytes);
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.putBytes caught an IOException writing %1$d bytes at file position %2$d after file position %3$d",
							bytes.length, position, this.getSafeFilePosition()), e);
		}
	}

	public void putBytes(final ByteBuffer byteBuffer) throws GearboxException {
		final byte[] bytes = new byte[byteBuffer.limit() - byteBuffer.position()];
		byteBuffer.get(bytes, byteBuffer.position(), byteBuffer.limit());
		this.putBytes(bytes);
	}

	public void putBytesArray(final byte[][] bytesArray) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.in.writeInt(bytesArray.length);
			for (final byte[] element : bytesArray) {
				this.putBytes(element);
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.putBytesArray caught an IOException writing bytes array starting at file position after file position %2$d",
							position, this.getSafeFilePosition()), e);
		}
	}

	public void putBytesArrays(final byte[][][] bytesArrays) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.in.writeInt(bytesArrays.length);
			for (final byte[][] bytesArray : bytesArrays) {
				this.putBytesArray(bytesArray);
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.putBytesArrays caught an IOException writing bytes arrays starting at file position after file position %2$d",
							position, this.getSafeFilePosition()), e);
		}
	}

	public void putInt(final int i) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.in.writeInt(i);
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.putInt caught an IOException writing %1$d at file position %2$d after file position %3$d",
							i, position, this.getSafeFilePosition()), e);
		}
	}

	public void putLong(final long i) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.in.writeLong(i);
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.putLong caught an IOException writing %1$d at file position %2$d after file position %3$d",
							i, position, this.getSafeFilePosition()), e);
		}
	}

	public void putIntArray(final int[] ints) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.in.writeInt(ints.length);
			for (final int j : ints) {
				this.putInt(j);
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.putIntArray caught an IOException writing int array starting at file position after file position %2$d",
							position, this.getSafeFilePosition()), e);
		}
	}

	public void putString(final String s) throws GearboxException {
		final Charset charset = this.charset != null ? this.charset : Charset.defaultCharset();
		final ByteBuffer byteBuffer = charset.encode(s);
		final byte[] bytes = new byte[byteBuffer.limit()];
		byteBuffer.get(bytes);
		this.putBytes(bytes);
	}

	public void putStringArray(final String[] strings) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.in.writeInt(strings.length);
			for (final String string : strings) {
				this.putString(string);
			}
		} catch (final IOException e) {
			throw new GearboxException(
					String.format(
							"Gearbox.putStringArray caught an IOException writing string array starting at file position after file position %2$d",
							position, this.getSafeFilePosition()), e);
		}
	}

	public long seek(final long filePosition) throws GearboxException {
		try {
			this.in.seek(filePosition != -1 ? filePosition : this.in.length());
			return this.in.getFilePointer();
		} catch (final IOException e) {
			throw new GearboxException(String.format(
					"Gearbox.seek caught an IOException seeking to file posiiton %1$d", filePosition), e);
		}
	}
}
