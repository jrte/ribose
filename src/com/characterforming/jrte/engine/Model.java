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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See
 * LICENSE-gpl-3.0. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.IToken;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.CompilationException;
import com.characterforming.ribose.base.ModelException;

/**
 * @author Kim Briggs
 */
sealed class Model permits ModelCompiler, ModelLoader {
	static final int ANONYMOUS_FIELD_ORDINAL = 0;
	static final int CLEAR_ALL_FIELDS_ORDINAL = 1;
	static final byte[] EMPTY = {};
	static final byte[] ANONYMOUS_FIELD_NAME = Model.EMPTY;
	static final byte[] ALL_FIELDS_NAME = { '*' };

	public enum TargetMode {
		COMPILE, RUN;
	}

	protected String targetName;
	protected final TargetMode targetMode;
	protected final Class<?> targetClass;
	protected final Logger rtcLogger;
	protected final Logger rteLogger;
	protected final CharsetEncoder encoder = Base.newCharsetEncoder();
	protected HashMap<Bytes, Integer> signalOrdinalMap;
	protected HashMap<Bytes, Integer> fieldOrdinalMap;
	protected HashMap<Bytes, Integer> effectorOrdinalMap;
	protected HashMap<Bytes, Integer> transducerOrdinalMap;
	protected IEffector<?>[] proxyEffectors;
	protected long[] transducerOffsetIndex;
	protected Bytes[] transducerNameIndex;
	protected String modelVersion;
	protected boolean deleteOnClose;
	protected RandomAccessFile io;
	protected File modelPath;
	
	private ArrayList<HashMap<BytesArray, Integer>> effectorParametersMaps;
	private final CharsetDecoder decoder = Base.newCharsetDecoder();

	/** Proxy compiler or ribose model for effector paramter compilation */
	public Model() {
		this.targetMode = TargetMode.COMPILE;
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.modelVersion = Base.RTE_VERSION;
		this.targetClass = this.getClass();
		this.deleteOnClose = false;
	}
	
	/** Live compiler model loading to compile new target model */
	protected Model(final File modelPath, Class<?> targetClass) throws ModelException {
		this.targetClass = targetClass;
		this.targetMode = TargetMode.RUN;
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.modelVersion = Base.RTE_VERSION;
		this.modelPath = modelPath;
		this.deleteOnClose = true;
		try {
			this.io = new RandomAccessFile(this.modelPath, "rw");
		} catch (FileNotFoundException e) {
			throw new ModelException(String.format("Unable to create model file '%s'.",
				this.modelPath.toPath().toString()), e);
		}
	}

	/** Live ribose model loading to run transductions */
	protected Model(final File modelPath) throws ModelException {
		this.targetMode = TargetMode.RUN;
		this.modelPath = modelPath;
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.deleteOnClose = false;
		String targetClassname = "?";
		try {
			this.io = new RandomAccessFile(this.modelPath, "r");
			this.io.seek(0);
			this.readLong();
			this.modelVersion = this.readString();
			targetClassname = this.readString();
			this.targetClass = Class.forName(targetClassname);
		} catch (ClassNotFoundException e) {
			throw new ModelException(String.format("Unable to instantiate target class '%s' from model file %s.",
				targetClassname, this.modelPath.toPath().toString()), e);
		} catch (IOException e) {
			throw new ModelException(String.format("Failed to read preamble from model file %s.",
				this.modelPath.toPath().toString()), e);
		}
	}

	protected void initializeProxyEffectors() throws ModelException {
		ITarget proxyTarget = null;
		try {
			Object proxyInstance = this.targetClass.getDeclaredConstructor().newInstance();
			if (proxyInstance instanceof ITarget) {
				proxyTarget = (ITarget) proxyInstance;
			} else {
				throw new ModelException(String.format("Class '%s' from model file %s does not implement the ITarget interface.",
					this.targetClass.getName(), this.modelPath.toPath().toString()));
			}
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
			| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new ModelException(String.format("Unable to instantiate proxy target '%s' from model file %s.",
				this.targetClass.getName(), this.modelPath.toPath().toString()), e);
		}
		this.targetName = proxyTarget.getName();
		Transductor proxyTransductor = new Transductor();
		proxyTransductor.setFieldOrdinalMap(this.fieldOrdinalMap);
		IEffector<?>[] trexFx = proxyTransductor.getEffectors();
		IEffector<?>[] targetFx = proxyTarget.getEffectors();
		this.proxyEffectors = new IEffector<?>[trexFx.length + targetFx.length];
		System.arraycopy(trexFx, 0, this.proxyEffectors, 0, trexFx.length);
		System.arraycopy(targetFx, 0, this.proxyEffectors, trexFx.length, targetFx.length);
		this.effectorParametersMaps = new ArrayList<>(this.proxyEffectors.length);
		this.effectorOrdinalMap = new HashMap<>((this.proxyEffectors.length * 5) >> 2);
		for (int effectorOrdinal = 0; effectorOrdinal < this.proxyEffectors.length; effectorOrdinal++) {
			this.effectorOrdinalMap.put(this.proxyEffectors[effectorOrdinal].getName(), effectorOrdinal);
			this.effectorParametersMaps.add(null);
		}
		assert proxyTransductor == (Transductor)this.proxyEffectors[0].getTarget();
	}

	protected void close() {
		try {
			if (this.io != null) {
				this.io.close();
			}
		} catch (IOException e) {
			this.rtcLogger.log(Level.SEVERE, e, () -> String.format("Unable to close model file %1$s",
				this.modelPath.getPath()));
			this.deleteOnClose |= this.targetMode == TargetMode.RUN;
		} finally {
			assert !this.deleteOnClose || this.targetMode == TargetMode.RUN;
			if (this.deleteOnClose && this.targetMode == TargetMode.RUN
			&& this.modelPath.exists() && !this.modelPath.delete()) {
				this.rtcLogger.log(Level.WARNING, () -> String.format("Unable to delete model file %1$s",
					this.modelPath.getPath()));
			}
		}
	}

	/**
	 * Determine model version (current or previous (deprecated), else obsolete)
	 *
	 * @return the model version string
	 */
	public String getModelVersion() {
		return this.modelVersion;
	}

	public String showParameter(int effectorOrdinal, int parameterIndex) {
		if (this.proxyEffectors[effectorOrdinal] instanceof IParameterizedEffector) {
			IParameterizedEffector<?, ?> effector = (IParameterizedEffector<?, ?>) this.proxyEffectors[effectorOrdinal];
			return effector.showParameterTokens(parameterIndex);
		}
		return "VOID";
	}

	byte[] getTransducerName(int transducerOrdinal) {
		return this.transducerNameIndex[transducerOrdinal].bytes();
	}

	public void setTransducerOffset(int transducerOrdinal, long offset) {
		this.transducerOffsetIndex[transducerOrdinal] = offset;
	}

	public String getTargetClassname() {
		return this.targetClass.getName();
	}

	protected boolean checkTargetEffectors(ITarget target, IEffector<?>[] boundFx) {
		boolean checked = true;
		if (boundFx.length != this.proxyEffectors.length) {
			this.rtcLogger.log(Level.SEVERE, () -> String.format(
				"Proxy effector count (%1$d) does not match target %3$s effector count (%2$d)", 
					this.proxyEffectors.length, boundFx.length, target.getName()));
			checked = false;
		} else {
			for (int i = 0; checked && i < boundFx.length; i++) {
				if (!boundFx[i].equivalent(this.proxyEffectors[i])) {
					final int index = i;
					this.rtcLogger.log(Level.SEVERE, () -> String.format(
						"Proxy effector %1$s() does not match target %3$s effector %2$s)",
							this.proxyEffectors[index].getName(), boundFx[index].getName(), target.getName()));
					checked = false;
				}
			}
		}
		return checked;
	}

	protected byte[][][][] compileModelParameters(List<String> errors) {
		byte[][][][] effectorParameters = new byte[this.proxyEffectors.length][][][];
		final Map<Bytes, Integer> effectorMap = this.getEffectorOrdinalMap();
		for (int effectorOrdinal = 0; effectorOrdinal < this.proxyEffectors.length; effectorOrdinal++) {
			IEffector<?> effector = this.proxyEffectors[effectorOrdinal];
			HashMap<BytesArray, Integer> parametersMap = this.effectorParametersMaps.get(effectorOrdinal);
			if (effector instanceof IParameterizedEffector<?,?>) {
				if (parametersMap != null) {
					assert parametersMap != null : String.format("Effector parameters map is null for %1$s effector", effector.getName());
					assert effector instanceof BaseParameterizedEffector<?,?>;
					final BaseParameterizedEffector<?, ?> parameterizedEffector = (BaseParameterizedEffector<?, ?>) effector;
					byte[][][] effectorParameterBytes = new byte[parametersMap.size()][][];
					IToken[][] effectorParameterTokens = new IToken[parametersMap.size()][];
					for (Map.Entry<BytesArray, Integer> e : parametersMap.entrySet()) {
						int ordinal = e.getValue();
						byte[][] tokens = e.getKey().getBytes();
						effectorParameterTokens[ordinal] = Token.getParameterTokens(this, tokens);
						effectorParameterBytes[ordinal] = tokens;
					}
					parameterizedEffector.compileParameters(effectorParameterTokens, errors);
					effectorParameters[effectorOrdinal] = effectorParameterBytes;
				} else {
					effectorParameters[effectorOrdinal] = new byte[0][][];
				}
			} else {
				if (parametersMap != null && parametersMap.size() > 0) {
					errors.add(String.format("%1$s.%2$s: effector does not accept parameters",
						this.targetName, effector.getName()));
				}
				effectorParameters[effectorOrdinal] = new byte[0][][];
			}
			effector.passivate();
		}
		for (final Map.Entry<Bytes, Integer> entry : effectorMap.entrySet()) {
			if (this.proxyEffectors[entry.getValue()] == null) {
				this.rtcLogger.log(Level.SEVERE, () -> String.format("%1$s.%2$s: effector ordinal not found",
					this.targetName, entry.getKey().toString()));
			}
		}
		return effectorParameters;
	}

	protected int compileParameters(final int effectorOrdinal, final byte[][] parameterBytes) {
		HashMap<BytesArray, Integer> parametersMap = this.effectorParametersMaps.get(effectorOrdinal);
		if (parametersMap == null) {
			parametersMap = new HashMap<>(10);
			this.effectorParametersMaps.set(effectorOrdinal, parametersMap);
		}
		final int mapSize = parametersMap.size();
		return parametersMap.computeIfAbsent(new BytesArray(parameterBytes), absent -> mapSize);
	}

	Map<Bytes, Integer> getEffectorOrdinalMap() {
		return Collections.unmodifiableMap(this.effectorOrdinalMap);
	}

	protected int getEffectorOrdinal(Bytes bytes) {
		return this.effectorOrdinalMap.getOrDefault(bytes, -1);
	}

	protected File getModelPath() {
		return this.modelPath;
	}

	protected Integer getInputOrdinal(final byte[] input) throws CompilationException {
		if (input.length == 1) {
			return Byte.toUnsignedInt(input[0]);
		} else {
			Integer ordinal = this.getSignalOrdinal(new Bytes(input));
			if (ordinal < 0) {
				throw new CompilationException(String.format("Invalid input token %s",
					Bytes.decode(this.decoder, input, input.length)));
			}
			return ordinal;
		}
	}

	protected int getSignalCount() {
		return this.signalOrdinalMap.size();
	}

	protected int getSignalLimit() {
		return Base.RTE_SIGNAL_BASE + this.signalOrdinalMap.size();
	}

	protected Integer getSignalOrdinal(final Bytes name) {
		return this.signalOrdinalMap.get(name);
	}

	protected Map<Bytes,Integer> getFieldMap() {
		return Collections.unmodifiableMap(this.fieldOrdinalMap);
	}

	protected int addField(Bytes fieldName) {
		final int mapSize = this.fieldOrdinalMap.size();
		return this.fieldOrdinalMap.computeIfAbsent(fieldName, absent-> mapSize);
	}

	protected int addSignal(Bytes signalName) {
		final int mapSize = this.signalOrdinalMap.size();
		return this.signalOrdinalMap.computeIfAbsent(signalName, 
			absent -> Base.RTE_SIGNAL_BASE + mapSize);
	}

	protected int addTransducer(Bytes transducerName) {
		Integer ordinal = this.transducerOrdinalMap.get(transducerName);
		if (ordinal == null) {
			ordinal = this.transducerOrdinalMap.size();
			this.transducerOrdinalMap.put(transducerName, ordinal);
			if (ordinal >= this.transducerNameIndex.length) {
				int length = ordinal + (ordinal << 1);
				this.transducerNameIndex = Arrays.copyOf(this.transducerNameIndex, length);
				this.transducerOffsetIndex = Arrays.copyOf(this.transducerOffsetIndex, length);
			}
			this.transducerNameIndex[ordinal] = transducerName;
		} else {
			assert this.transducerNameIndex[ordinal].equals(transducerName);
		}
		return ordinal;
	}

	protected int getTransducerOrdinal(Bytes transducerName) {
		Integer ordinal = this.transducerOrdinalMap.get(transducerName);
		return (null != ordinal) ? ordinal.intValue() : -1;
	}

	protected long seek(final long filePosition) throws ModelException {
		try {
			this.io.seek(filePosition != -1 ? filePosition : this.io.length());
			return this.io.getFilePointer();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.seek() IOException seeking to file posiiton %1$d", filePosition), e);
		}
	}

	protected int readInt() throws ModelException {
		try {
			return this.io.readInt();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.readInt() IOException reading int at file position %1$d", 
					this.getSafeFilePosition()), e);
		}
	}

	protected long readLong() throws ModelException {
		try {
			return this.io.readLong();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.readLong() IOException reading long at file position %1$d", 
					this.getSafeFilePosition()), e);
		}
	}

	protected int[] readIntArray() throws ModelException {
		int[] ints = {};
		long position = this.getSafeFilePosition();
		try {
			ints = new int[this.io.readInt()];
			for (int i = 0; i < ints.length; i++) {
				ints[i] = this.io.readInt();
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.readIntsArray() IOException at file position %3$d reading int[%1$d] array starting at file position %2$d",
					ints.length, position, this.getSafeFilePosition()), e);
		}
		return ints;
	}

	protected byte[] readBytes() throws ModelException {
		byte[] bytes = {};
		long position = this.getSafeFilePosition();
		int read = -1;
		try {
			int length = this.io.readInt();
			if (length >= 0) {
				bytes = new byte[length];
				read = this.io.read(bytes);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.readBytes() IOException at file position %3$d reading %1$d bytes at file position %2$d",
					bytes.length, position, this.getSafeFilePosition()), e);
		}
		if (read >= 0 && read != bytes.length) {
			throw new ModelException(String.format(
				"RuntimeModel.readBytes expected %1$d bytes at file position %2$d but read only %3$d", 
					bytes.length,	position, read));
		}
		return bytes;
	}

	protected byte[][] readBytesArray() throws ModelException {
		byte[][] bytesArray = {};
		long position = this.getSafeFilePosition();
		try {
			int length = this.io.readInt();
			if (length >= 0) {
				bytesArray = new byte[length][];
				for (int i = 0; i < bytesArray.length; i++) {
					bytesArray[i] = this.readBytes();
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.readBytesArray() IOException at file position %1$s reading bytes array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
		return bytesArray;
	}

	protected HashMap<Bytes, Integer> readOrdinalMap(int offset) throws ModelException {
		byte[][] bytesArray = this.readBytesArray();
		HashMap<Bytes, Integer> map = new HashMap<>((bytesArray.length * 5) >> 2);
		int limit = offset + bytesArray.length;
		for (int ordinal = offset; ordinal < limit; ordinal++) {
			map.put(new Bytes(bytesArray[ordinal - offset]), ordinal);
		}
		return map;
	}

	protected byte[][][] readBytesArrays() throws ModelException {
		byte[][][] bytesArrays = null;
		long position = this.getSafeFilePosition();
		try {
			int length = this.io.readInt();
			if (length >= 0) {
				bytesArrays = new byte[length][][];
				for (int i = 0; i < bytesArrays.length; i++) {
					bytesArrays[i] = this.readBytesArray();
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.readBytesArrays() IOException at file position %1$d reading bytes array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
		return (bytesArrays != null) ? bytesArrays : new byte[][][] {};
	}

	protected String readString() throws ModelException {
		byte[] bytes = this.readBytes();
		return Bytes.decode(this.decoder, bytes, bytes.length).toString();
	}

	protected String[] readStringArray() throws ModelException {
		final byte[][] bytesArray = this.readBytesArray();
		final String[] stringArray = new String[bytesArray.length];
		for (int i = 0; i < bytesArray.length; i++) {
			stringArray[i] = Bytes.decode(this.decoder, bytesArray[i], bytesArray[i].length).toString();
		}
		return stringArray;
	}

	protected long[] readTransitionMatrix() throws ModelException {
		long[] matrix;
		long position = this.getSafeFilePosition();
		try {
			final int rows = this.io.readInt();
			final int columns = this.io.readInt();
			matrix = new long[columns * rows];
			// matrix is an ExS array, columns range over inputs, rows over states
			for (int column = 0; column < columns; column++) {
				for (int row = 0; row < rows; row++) {
					// Preset to invoke nul() effector on domain error
					final int toState = column * rows;
					matrix[toState + row] = Transducer.transition(toState, 0);
				}
			}
			for (int row = 0; row < rows; row++) {
				final int count = this.io.readInt();
				for (int i = 0; i < count; i++) {
					final int column = this.io.readInt();
					final int fromState = column * rows;
					final int toState = this.io.readInt() * rows;
					final int effect = this.io.readInt();
					matrix[fromState + row] = Transducer.transition(toState, effect);
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.readTransitionMatrix() IOException at file position %2$d reading transition matrix starting at file position %1$d",
					position, this.getSafeFilePosition()), e);
		}
		return matrix;
	}

	protected long getSafeFilePosition() {
		try {
			return this.io.getFilePointer();
		} catch (final IOException e) {
			return -1;
		}
	}

	protected void writeBytes(final byte[] bytes) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (bytes != null) {
				this.io.writeInt(bytes.length);
				this.io.write(bytes);
			} else {
				this.io.writeInt(-1);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.writeBytes() IOException at file position %2$d trying to write %1$d bytes starting at file position %3$d",
					bytes != null ? bytes.length : 0, this.getSafeFilePosition(), position), e);
		}
	}

	protected void writeBytes(final Bytes bytes) throws ModelException {
		if (bytes != null) {
			this.writeBytes(bytes.bytes());
		}
	}

	protected void writeBytes(final ByteBuffer byteBuffer) throws ModelException {
		byte[] bytes = null;
		if (byteBuffer != null) {
			bytes = new byte[byteBuffer.limit() - byteBuffer.position()];
			byteBuffer.get(bytes, byteBuffer.position(), byteBuffer.limit());
			this.writeBytes(bytes);
		}
	}

	protected void writeBytesArray(final byte[][] bytesArray) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (bytesArray != null) {
				this.io.writeInt(bytesArray.length);
				for (final byte[] element : bytesArray) {
					this.writeBytes(element);
				}
			} else {
				this.io.writeInt(-1);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.writeBytesArray() IOException at file position %1$d writing byte[][] array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
	}

	protected void writeBytesArrays(final byte[][][] bytesArrays) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (bytesArrays != null) {
				this.io.writeInt(bytesArrays.length);
				for (final byte[][] bytesArray : bytesArrays) {
					this.writeBytesArray(bytesArray);
				}
			} else {
				this.io.writeInt(-1);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.writeBytesArrays() IOException at file position %1$d writing byte[][][] array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
	}

	protected void writeOrdinalMap(final Map<Bytes, Integer> map, int offset) throws ModelException {
		byte[][] names = new byte[map.size()][];
		for (Entry<Bytes, Integer> entry : map.entrySet()) {
			names[entry.getValue() - offset] = entry.getKey().bytes();
		}
		this.writeBytesArray(names);
	}

	protected void writeInt(final int i) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeInt(i);
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.writeInt() IOException writing int at file position %1$d",
					position), e);
		}
	}

	protected void writeLong(final long i) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeLong(i);
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.writeLong() IOException writing long at file position %1$d",
					position), e);
		}
	}

	protected void writeIntArray(final int[] ints) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeInt(ints.length);
			for (final int j : ints) {
				this.writeInt(j);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.writeIntArray() IOException at file position %1$d writing int array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
	}

	protected void writeString(final String s) throws ModelException {
		this.writeBytes(Bytes.encode(this.encoder, s));
	}

	protected void writeTransitionMatrix(final int[][][] matrix) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			final int rows = matrix.length;
			final int columns = rows > 0 ? matrix[0].length : 0;
			this.io.writeInt(rows);
			this.io.writeInt(columns);
			for (int row = 0; row < rows; row++) {
				int count = 0;
				for (int column = 0; column < columns; column++) {
					if (matrix[row][column][1] != 0) {
						count++;
					}
				}
				this.io.writeInt(count);
				for (int column = 0; column < columns; column++) {
					if (matrix[row][column][1] != 0) {
						this.io.writeInt(column);
						this.io.writeInt(matrix[row][column][0]);
						this.io.writeInt(matrix[row][column][1]);
					}
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.writeTransitionMatrix() IOException at file position %2$d reading transition matrix starting at file position %1$d",
					position, this.getSafeFilePosition()), e);
		}
	}
}
