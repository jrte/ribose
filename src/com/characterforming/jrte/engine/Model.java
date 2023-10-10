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
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
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
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.CompilationException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.Signal;

/**
 * @author Kim Briggs
 */
sealed class Model permits ModelCompiler, ModelLoader {
	static final int ANONYMOUS_FIELD_ORDINAL = 0;
	static final int ALL_FIELDS_ORDINAL = 1;
	static final byte[] EMPTY = {};
	static final byte[] ANONYMOUS_FIELD_NAME = Model.EMPTY;
	static final Bytes ANONYMOUS_FIELD_BYTES = new Bytes(Model.ANONYMOUS_FIELD_NAME);
	static final byte[] ALL_FIELDS_NAME = { '*' };
	static final Bytes ALL_FIELDS_BYTES = new Bytes(Model.ALL_FIELDS_NAME);

	public enum TargetMode {
		PROXY_COMPILER, LIVE_COMPILER, PROXY_TARGET, LIVE_TARGET;

		boolean isLive() {
			return this == LIVE_COMPILER || this == LIVE_TARGET;
		}

		boolean isProxy() {
			return this == PROXY_COMPILER || this == PROXY_TARGET;
		}
	}

	// an array of tokens bound to an effector invocation from a transducer
	public record Argument(int transducerOrdinal, BytesArray tokens) {}

	protected String targetName;
	protected final TargetMode targetMode;
	protected final Class<?> targetClass;
	protected final Logger rtcLogger;
	protected final Logger rteLogger;
	protected HashMap<Bytes, Integer> signalOrdinalMap;
	protected HashMap<Bytes, Integer> fieldOrdinalMap;
	protected HashMap<Bytes, Integer> effectorOrdinalMap;
	protected HashMap<Bytes, Integer> transducerOrdinalMap;
	protected HashMap<Integer, HashMap<Integer, Integer>> transducerFieldMaps;
	protected IEffector<?>[] proxyEffectors;
	protected long[] transducerOffsetIndex;
	protected Bytes[] transducerNameIndex;
	protected String modelVersion;
	protected boolean deleteOnClose;
	protected RandomAccessFile io;
	protected File modelPath;
	
	private ArrayList<HashMap<Argument, Integer>> effectorParametersMaps;
	private Transductor proxyTransductor;

	/** Proxy compiler model for effector parameter compilation */
	public Model() {
		this.targetMode = TargetMode.PROXY_COMPILER;
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.modelVersion = Base.RTE_VERSION;
		this.targetClass = this.getClass();
		this.deleteOnClose = false;
	}
	
	/** Live compiler model loading to compile new target model */
	protected Model(final File modelPath, Class<?> targetClass, TargetMode targetMode) throws ModelException {
		this.targetClass = targetClass;
		this.targetMode = targetMode;
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.modelVersion = Base.RTE_VERSION;
		this.modelPath = modelPath;
		this.deleteOnClose = true;
		this.fieldOrdinalMap = new HashMap<>(256);
		this.signalOrdinalMap = new HashMap<>(256);
		this.transducerOrdinalMap = new HashMap<>(256);
		this.transducerOffsetIndex = new long[256];
		this.transducerNameIndex = new Bytes[256];
		this.transducerFieldMaps = new HashMap<>(256);
		this.fieldOrdinalMap.put(new Bytes(Model.ANONYMOUS_FIELD_NAME), Model.ANONYMOUS_FIELD_ORDINAL);
		this.fieldOrdinalMap.put(new Bytes(Model.ALL_FIELDS_NAME), Model.ALL_FIELDS_ORDINAL);
		this.initializeProxyEffectors();
		for (Signal signal : Signal.values()) {
			if (!signal.isNone()) {
				assert this.getSignalLimit() == signal.signal();
				this.signalOrdinalMap.put(signal.symbol(), signal.signal());
			}
		}
		try {
			this.io = new RandomAccessFile(this.modelPath, "rw");
			this.writeLong(0);
			this.writeString(this.modelVersion);
			this.writeString(this.targetClass.getName());
		} catch (FileNotFoundException | CharacterCodingException e) {
			throw new ModelException(String.format("Unable to create model file '%s'.",
				this.modelPath.toPath().toString()), e);
		}
	}

	/** Live ribose model loading to run transductions */
	protected Model(final File modelPath) throws ModelException {
		this.targetMode = TargetMode.LIVE_TARGET;
		this.modelPath = modelPath;
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.transducerFieldMaps = new HashMap<>(256);
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
		try {
			if (this.targetClass.getDeclaredConstructor().newInstance() instanceof ITarget proxyTarget) {
				this.targetName = proxyTarget.getName();
				this.proxyTransductor = new Transductor();
				this.proxyTransductor.setModel(this);
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
			} else throw new ModelException(String.format(
					"Class '%s' from model file %s does not implement the ITarget interface.",
						this.targetClass.getName(), this.modelPath.toPath().toString()));
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
			| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new ModelException(String.format("Unable to instantiate proxy target '%s' from model file %s.",
				this.targetClass.getName(), this.modelPath.toPath().toString()), e);
		}
	}

	protected void close() {
		try {
			if (this.io != null) {
				this.io.close();
			}
		} catch (IOException e) {
			this.rtcLogger.log(Level.SEVERE, e, () -> String.format("Unable to close model file %1$s",
				this.modelPath.getPath()));
			this.deleteOnClose |= this.targetMode.isLive();
		} finally {
			Codec.detach();
			assert !this.deleteOnClose || this.targetMode.isLive();
			if (this.deleteOnClose && this.targetMode.isLive()
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

	/**
	 * Commit model to persistent store
	 * 
	 * @param effectorParameters the union of all effector parameters
	 * @return true if model saved
	 */
	protected boolean save(Argument[][] effectorParameters) {
		long indexPosition = this.getSafeFilePosition();
		File mapFile = new File(this.modelPath.getPath().replaceAll(".model", ".map"));
		try {
			assert indexPosition == this.io.length();
			if (this.transducerOrdinalMap.size() > 0) {
				this.writeOrdinalMap(signalOrdinalMap, Base.RTE_SIGNAL_BASE);
				this.writeOrdinalMap(effectorOrdinalMap, 0);
				this.writeOrdinalMap(this.fieldOrdinalMap, 0);
				this.writeOrdinalMap(transducerOrdinalMap, 0);
				int transducerCount = this.transducerOrdinalMap.size();
				for (int transducerOrdinal = 0; transducerOrdinal < transducerCount; transducerOrdinal++) {
					this.writeBytes(this.transducerNameIndex[transducerOrdinal].bytes());
					this.writeLong(this.transducerOffsetIndex[transducerOrdinal]);
					HashMap<Integer, Integer> localFieldMap = this.transducerFieldMaps.get(transducerOrdinal);
					int[] localFields = new int[localFieldMap.size()];
					for (Entry<Integer, Integer> e : localFieldMap.entrySet()) {
						localFields[e.getValue()] = e.getKey();
					}
					this.writeIntArray(localFields);
				}
				for (int effectorOrdinal = 0; effectorOrdinal < this.effectorOrdinalMap.size(); effectorOrdinal++) {
					Argument[] arguments = effectorParameters[effectorOrdinal];
					if (arguments != null && arguments.length > 0) {
						assert this.proxyEffectors[effectorOrdinal] instanceof IParameterizedEffector<?, ?>;
						this.writeArguments(arguments);
					} else {
						this.writeInt(-1);
					}
				}
				this.seek(0);
				this.writeLong(indexPosition);
				this.map(mapFile);
				String stats = String.format(
					"%1$s: target class %2$s%n%3$d transducers; %4$d effectors; %5$d fields; %6$d signal ordinals%n",
					this.modelPath.getPath(), this.targetClass.getName(), transducerCount,
					this.effectorOrdinalMap.size(), this.getFieldCount(), this.getSignalCount());
				this.rtcLogger.log(Level.INFO, () -> stats);
				this.deleteOnClose = false;
				System.out.println(stats);
				System.out.flush();
			}
			if (this.transducerOrdinalMap.size() == 0) {
				this.rtcLogger.log(Level.SEVERE, "No transducers compiled to {0}", this.modelPath.getPath());
			}
		} catch (IOException | ModelException e) {
			this.rtcLogger.log(Level.SEVERE, e, () -> String.format("Failed to save model '%1$s'",
				this.modelPath.getPath()));
		} finally {
			if (this.deleteOnClose) {
				this.rtcLogger.log(Level.SEVERE, () -> String.format("Compilation failed for model '%1$s'",
					this.modelPath.getPath()));
				if (mapFile.exists() && !mapFile.delete()) {
					this.rtcLogger.log(Level.WARNING, () -> String.format("Unable to delete model '%1$s'",
						mapFile.getPath()));
				}
			}
		}
		return !this.deleteOnClose;
	}

	protected Model load() throws ModelException, CharacterCodingException {
		this.seek(0);
		long indexPosition = this.readLong();
		final String loadedVersion = this.readString();
		if (!loadedVersion.equals(Base.RTE_VERSION)) {
			throw new ModelException(String.format(
				"Current this version '%1$s' does not match version string '%2$s' from this file '%3$s'",
					Base.RTE_VERSION, loadedVersion, this.modelPath.getPath()));
		}
		final String targetClassname = this.readString();
		if (!targetClassname.equals(this.targetClass.getName())) {
			throw new ModelException(String.format(
				"Can't load this for target class '%1$s'; '%2$s' is target class for this file '%3$s'",
					this.targetName, targetClassname, this.modelPath.getPath()));
		}
		this.modelVersion = loadedVersion;
		this.seek(indexPosition);
		this.signalOrdinalMap = this.readOrdinalMap(Base.RTE_SIGNAL_BASE);
		this.effectorOrdinalMap = this.readOrdinalMap(0);
		this.fieldOrdinalMap = this.readOrdinalMap(0);
		this.transducerOrdinalMap = this.readOrdinalMap(0);
		int transducerCount = this.transducerOrdinalMap.size();
		this.transducerNameIndex = new Bytes[transducerCount];
		this.transducerOffsetIndex = new long[transducerCount];
		for (int transducerOrdinal = 0; transducerOrdinal < transducerCount; transducerOrdinal++) {
			this.transducerNameIndex[transducerOrdinal] = new Bytes(this.readBytes());
			this.transducerOffsetIndex[transducerOrdinal] = this.readLong();
			int[] fields = this.readIntArray();
			HashMap<Integer, Integer> localFieldMap = new HashMap<>(fields.length);
			for (int i = 0; i < fields.length; i++) {
				localFieldMap.computeIfAbsent(fields[i], absent -> localFieldMap.size());
				assert i == localFieldMap.getOrDefault(fields[i], -1);
			}
			this.transducerFieldMaps.put(transducerOrdinal, localFieldMap);
			assert this.transducerOrdinalMap.get(this.transducerNameIndex[transducerOrdinal]) == transducerOrdinal;
		}
		this.initializeProxyEffectors();
		List<String> errors = new ArrayList<>(32);
		for (int effectorOrdinal = 0; effectorOrdinal < this.effectorOrdinalMap.size(); effectorOrdinal++) {
			Argument[] effectorArguments = this.readArguments();
			IToken[][] parameterTokens = new IToken[effectorArguments.length][];
			for (int i = 0; i < effectorArguments.length; i++) {
				parameterTokens[i] = Token.getParameterTokens(this, effectorArguments[i]);
			}
			if (this.proxyEffectors[effectorOrdinal] instanceof BaseParameterizedEffector<?, ?> effector) {
				effector.compileParameters(parameterTokens, errors);
			}
			if (this.targetMode.isLive()) {
				this.proxyEffectors[effectorOrdinal].passivate();
			}
		}
		this.proxyTransductor = null;
		if (!errors.isEmpty()) {
			for (String error : errors) {
				this.rtcLogger.log(Level.SEVERE, error);
			}
			throw new ModelException(String.format(
				"Failed to load '%1$s', effector parameter precompilation failed.",
				modelPath.getAbsolutePath()));
		}
		assert this.targetMode.isLive();
		return this;
	}

	/**
	 * Print the model map to a file
	 */
	public boolean map(File mapFile) {
		boolean mapped = false;
		try (PrintStream mapWriter = new PrintStream(mapFile)) {
			mapped = this.map(mapWriter);
		} catch (FileNotFoundException e) {
			this.rtcLogger.log(Level.SEVERE, e, () -> String.format(
				"Unable to create map file '%1$s'", mapFile.getPath()));
		}
		return mapped;
	}

	/**
	 * Print the model map to System.out
	 */
	public boolean map(PrintStream mapWriter) {
		mapWriter.println(String.format("version %1$s", this.modelVersion));
		mapWriter.println(String.format("target %1$s", this.targetClass.getName()));
		Bytes[] signalIndex = new Bytes[this.signalOrdinalMap.size()];
		for (Map.Entry<Bytes, Integer> m : this.signalOrdinalMap.entrySet()) {
			signalIndex[m.getValue() - Base.RTE_SIGNAL_BASE] = m.getKey();
		}
		for (int i = 0; i < signalIndex.length; i++) {
			mapWriter.printf("%1$6d signal %2$s%n", i + Base.RTE_SIGNAL_BASE, 
				signalIndex[i].asString());
		}
		Bytes[] fieldIndex = new Bytes[this.fieldOrdinalMap.size()];
		for (Map.Entry<Bytes, Integer> m : this.fieldOrdinalMap.entrySet()) {
			fieldIndex[m.getValue()] = m.getKey();
		}
		Bytes[] transducerIndex = new Bytes[this.transducerOrdinalMap.size()];
		for (Map.Entry<Bytes, Integer> m : this.transducerOrdinalMap.entrySet()) {
			transducerIndex[m.getValue()] = m.getKey();
		}
		for (int transducerOrdinal = 0; transducerOrdinal < transducerIndex.length; transducerOrdinal++) {
			mapWriter.printf("%1$6d transducer %2$s%n", transducerOrdinal, 
				transducerIndex[transducerOrdinal].asString());
			Map<Integer, Integer> fieldMap = this.transducerFieldMaps.get(transducerOrdinal);
			Bytes[] fields = new Bytes[fieldMap.size()];
			for (Entry<Integer, Integer> e : fieldMap.entrySet()) {
				fields[e.getValue()] = fieldIndex[e.getKey()];
			}
			for (int field = 0; field < fields.length; field++) {
				mapWriter.printf("%1$6d field ~%2$s%n", field,
					fields[field].asString());
			}
		}
		Bytes[] effectorIndex = new Bytes[this.effectorOrdinalMap.size()];
		for (Map.Entry<Bytes, Integer> m : this.effectorOrdinalMap.entrySet()) {
			effectorIndex[m.getValue()] = m.getKey();
		}
		for (int effector = 0; effector < effectorIndex.length; effector++) {
			mapWriter.printf("%1$6d effector %2$s", effector,
				effectorIndex[effector].asString());
			if (this.proxyEffectors[effector] instanceof BaseParameterizedEffector<?, ?> proxyEffector) {
				mapWriter.printf(" [ %1$s ]%n", proxyEffector.showParameterType());
				for (int parameter = 0; parameter < proxyEffector.getParameterCount(); parameter++) {
					mapWriter.printf("%1$6d parameter %2$s%n", parameter, proxyEffector.showParameterTokens(parameter));
				}
			} else {
				mapWriter.println();
			}
		}
		mapWriter.flush();
		return true;
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

	protected Argument[][] compileModelParameters(List<String> errors) throws EffectorException {
		Argument[][] effectorArguments = new Argument[this.proxyEffectors.length][];
		final Map<Bytes, Integer> effectorMap = this.getEffectorOrdinalMap();
		for (int effectorOrdinal = 0; effectorOrdinal < this.proxyEffectors.length; effectorOrdinal++) {
			HashMap<Argument, Integer> parametersMap = this.effectorParametersMaps.get(effectorOrdinal);
			this.proxyEffectors[effectorOrdinal].setOutput(this.proxyTransductor);
			if (this.proxyEffectors[effectorOrdinal] instanceof BaseParameterizedEffector<?,?> parameterizedEffector) {
				if (parametersMap != null) {
					assert parametersMap != null: String.format("Effector parameters map is null for %1$s effector", 
						parameterizedEffector.getName());
					Argument[] arguments = new Argument[parametersMap.size()];
					IToken[][] tokens = new IToken[arguments.length][];
					for (Map.Entry<Argument, Integer> e : parametersMap.entrySet()) {
						int ordinal = e.getValue(); Argument argument = e.getKey();
						tokens[ordinal] = Token.getParameterTokens(this, argument);
						arguments[ordinal] = argument;
					}
					parameterizedEffector.compileParameters(tokens, errors);
					effectorArguments[effectorOrdinal] = arguments;
				} else {
					effectorArguments[effectorOrdinal] = new Argument[0];
				}
			} else if (this.proxyEffectors[effectorOrdinal] instanceof BaseEffector<?> effector) {
				if (parametersMap != null && parametersMap.size() > 0) {
					errors.add(String.format("%1$s.%2$s: effector does not accept parameters",
					this.targetName, effector.getName()));
				}
				effectorArguments[effectorOrdinal] = new Argument[0];
			} else {
				assert false;
			}
		}
		for (final Map.Entry<Bytes, Integer> entry : effectorMap.entrySet()) {
			if (this.proxyEffectors[entry.getValue()] == null) {
				this.rtcLogger.log(Level.SEVERE, () -> String.format("%1$s.%2$s: effector ordinal not found",
					this.targetName, entry.getKey().asString()));
			}
		}
		return effectorArguments;
	}

	protected int compileParameters(final int effectorOrdinal, final Argument parameterBytes) {
		HashMap<Argument, Integer> parametersMap = this.effectorParametersMaps.get(effectorOrdinal);
		if (parametersMap == null) {
			parametersMap = new HashMap<>(10);
			this.effectorParametersMaps.set(effectorOrdinal, parametersMap);
		}
		final HashMap<Argument, Integer> effectiveMap = parametersMap;
		return effectiveMap.computeIfAbsent(parameterBytes, absent -> effectiveMap.size());
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

	protected Integer getInputOrdinal(final byte[] input) throws CompilationException, CharacterCodingException {
		if (input.length == 1) {
			return Byte.toUnsignedInt(input[0]);
		} else {
			Integer ordinal = this.getSignalOrdinal(new Bytes(input));
			if (ordinal < 0) {
				throw new CompilationException(String.format("Invalid input token %s",
					Codec.decode(input, input.length)));
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

	protected int getFieldCount() {
		return this.fieldOrdinalMap.size();
	}

	protected int getFieldCount(int transducerOrdinal) {
		return this.transducerFieldMaps.containsKey(transducerOrdinal)
		? this.transducerFieldMaps.get(transducerOrdinal).size()
		: 0;
	}

	protected Map<Bytes, Integer> getFieldMap() {
		return Collections.unmodifiableMap(this.fieldOrdinalMap);
	}

	protected int getFieldOrdinal(Bytes fieldName) {
		return this.fieldOrdinalMap.getOrDefault(fieldName, -1);
	}

	protected int addField(Bytes fieldName) {
		final int mapSize = this.fieldOrdinalMap.size();
		return this.fieldOrdinalMap.computeIfAbsent(fieldName, absent-> mapSize);
	}

	protected HashMap<Integer, Integer> newLocalField(int transducerOrdinal) {
		HashMap<Integer, Integer> localFieldMap = this.transducerFieldMaps.computeIfAbsent(
		transducerOrdinal, absent -> new HashMap<>(256));
		if (localFieldMap.isEmpty()) {
			localFieldMap.put(this.fieldOrdinalMap.get(Model.ANONYMOUS_FIELD_BYTES),
				Model.ANONYMOUS_FIELD_ORDINAL);
			localFieldMap.put(this.fieldOrdinalMap.get(Model.ALL_FIELDS_BYTES),
				Model.ALL_FIELDS_ORDINAL);
		}
		return localFieldMap;
	}

	protected int addLocalField(int transducerOrdinal, int fieldOrdinal) {
		HashMap<Integer, Integer> localFieldMap = this.newLocalField(transducerOrdinal);
		assert localFieldMap.get(Model.ANONYMOUS_FIELD_ORDINAL) == Model.ANONYMOUS_FIELD_ORDINAL;
		assert localFieldMap.get(Model.ALL_FIELDS_ORDINAL) == Model.ALL_FIELDS_ORDINAL;
		return localFieldMap.computeIfAbsent(fieldOrdinal, absent -> localFieldMap.size());
	}

	protected int getLocalField(int transducerOrdinal, int fieldOrdinal) {
		return this.newLocalField(transducerOrdinal).getOrDefault(fieldOrdinal, -1);
	}

	protected int addSignal(Bytes signalName) {
		final int mapSize = this.signalOrdinalMap.size();
		return this.signalOrdinalMap.computeIfAbsent(signalName, 
			absent -> Base.RTE_SIGNAL_BASE + mapSize);
	}

	protected int addTransducer(Bytes transducerName) {
		assert !this.transducerOrdinalMap.containsKey(transducerName)
		|| this.transducerNameIndex[this.transducerOrdinalMap.get(transducerName)].equals(transducerName)
		|| this.transducerOffsetIndex[this.transducerOrdinalMap.get(transducerName)] < 0;
		final int count = this.transducerOrdinalMap.size();
		Integer ordinal = this.transducerOrdinalMap.computeIfAbsent(transducerName, absent -> count);
		if (ordinal >= count) {
			assert !this.transducerFieldMaps.containsKey(ordinal);
			assert transducerNameIndex[ordinal] == null;
			assert this.transducerOffsetIndex[ordinal] == 0;
			if (ordinal >= this.transducerNameIndex.length) {
				int length = ordinal + (Math.max(ordinal, 16) >> 1);
				this.transducerNameIndex = Arrays.copyOf(this.transducerNameIndex, length);
				this.transducerOffsetIndex = Arrays.copyOf(this.transducerOffsetIndex, length);
			}
			this.newLocalField(ordinal);
			this.transducerNameIndex[ordinal] = transducerName;
			this.transducerOffsetIndex[ordinal] = -1;
		}
		return ordinal;
	}

	protected int getTransducerOrdinal(Bytes transducerName) {
		return this.transducerOrdinalMap.getOrDefault(transducerName, -1);
	}

	protected long seek(final long filePosition) throws ModelException {
		try {
			this.io.seek(filePosition != -1 ? filePosition : this.io.length());
			return this.io.getFilePointer();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.seek() IOException seeking to file posiiton %1$d", filePosition), e);
		}
	}

	protected int readInt() throws ModelException {
		try {
			return this.io.readInt();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.readInt() IOException reading int at file position %1$d", 
					this.getSafeFilePosition()), e);
		}
	}

	protected long readLong() throws ModelException {
		try {
			return this.io.readLong();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.readLong() IOException reading long at file position %1$d", 
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
				"Model.readIntsArray() IOException at file position %3$d reading int[%1$d] array starting at file position %2$d",
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
				"Model.readBytes() IOException at file position %3$d reading %1$d bytes at file position %2$d",
					bytes.length, position, this.getSafeFilePosition()), e);
		}
		if (read >= 0 && read != bytes.length) {
			throw new ModelException(String.format(
				"Model.readBytes expected %1$d bytes at file position %2$d but read only %3$d", 
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
				"Model.readBytesArray() IOException at file position %1$s reading bytes array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
		return bytesArray;
	}

	protected HashMap<Bytes, Integer> readOrdinalMap(int offset) throws ModelException {
		byte[][] bytesArray = this.readBytesArray();
		HashMap<Bytes, Integer> map = new HashMap<>((Math.max(48, bytesArray.length) * 5) >> 2);
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
				"Model.readBytesArrays() IOException at file position %1$d reading bytes array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
		return (bytesArrays != null) ? bytesArrays : new byte[][][] {};
	}

	protected Argument readArgument() throws ModelException {
		Argument argument = null;
		long position = this.getSafeFilePosition();
		try {
			int transducerOrdinal = this.io.readInt();
			if (transducerOrdinal > Integer.MIN_VALUE) {
				argument = new Argument(transducerOrdinal, new BytesArray(this.readBytesArray()));
			} else {
				argument = new Argument(-1, new BytesArray(new byte[][] {}));
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.readBytesArray() IOException at file position %1$s reading bytes array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
		return argument;
	}

	protected Argument[] readArguments() throws ModelException {
		Argument[] arguments = null;
		long position = this.getSafeFilePosition();
		try {
			int length = this.io.readInt();
			if (length >= 0) {
				arguments = new Argument[length];
				for (int i = 0; i < length; i++) {
					arguments[i] = this.readArgument();
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.readArguments() IOException at file position %1$d reading Argument array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
		return (arguments != null) ? arguments : new Argument[] {};
	}

	protected String readString() throws ModelException, CharacterCodingException {
		byte[] bytes = this.readBytes();
		return Codec.decode(bytes);
	}

	protected String[] readStringArray() throws ModelException, CharacterCodingException {
		final byte[][] bytesArray = this.readBytesArray();
		final String[] stringArray = new String[bytesArray.length];
		for (int i = 0; i < bytesArray.length; i++) {
			stringArray[i] = Codec.decode(bytesArray[i]);
		}
		return stringArray;
	}

	protected long[] readTransitionMatrix() throws ModelException {
		// matrix is flattened to a 1-D array indexed as matrix[state * nInputs + input]
		final long position = this.getSafeFilePosition();
		long[] matrix;
		try {
			final int nStates = this.io.readInt();
			final int nInputs = this.io.readInt();
			matrix = new long[nStates * nInputs];
			// preset all cells to signal NUL without changing state
			for (int state = 0; state < nStates; state++) {
				final int base = state * nInputs, toState = base;
				final long nul = Transducer.transition(toState, Signal.NUL.ordinal());
				for (int input = 0; input < nInputs; input++) {
					matrix[base + input] = nul;
				}
			}
			// fill in defined transitions
			for (int state = 0; state < nStates; state++) {
				final int base = state * nInputs;
				final int transitions = this.io.readInt();
				for (int i = 0; i < transitions; i++) {
					final int input = this.io.readInt() ;
					final int toState = this.io.readInt() * nInputs;
					final int effect = this.io.readInt();
					matrix[base + input] = Transducer.transition(toState, effect);
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.readTransitionMatrix() IOException at file position %2$d reading transition matrix starting at file position %1$d",
					position, this.getSafeFilePosition()), e);
		}
		return matrix;
	}

	protected Transducer readTransducer(int transducerOrdinal) throws ModelException {
		try {
			this.io.seek(transducerOffsetIndex[transducerOrdinal]);
			Transducer transducer = new Transducer(
				this.readString(),
				this.readInt(),
				this.readIntArray(),
				this.readIntArray(),
				this.readTransitionMatrix(),
				this.readIntArray()
			);
			assert transducer.getOrdinal() == transducerOrdinal;
			return transducer;
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.readTransducer(ordinal:%d) IOException after seek to %d",
					transducerOrdinal, transducerOffsetIndex[transducerOrdinal]), e);
		}
	}

	protected long getSafeFilePosition() {
		try {
			return this.io.getFilePointer();
		} catch (final IOException e) {
			return -1;
		}
	}

	protected void writeBytes(final byte[] bytes, int length) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (bytes != null) {
				this.io.writeInt(length);
				this.io.write(bytes, 0, length);
			} else {
				this.io.writeInt(-1);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.writeBytes() IOException at file position %2$d trying to write %1$d bytes starting at file position %3$d",
				bytes != null ? bytes.length : 0, this.getSafeFilePosition(), position), e);
		}
	}

	protected void writeBytes(final byte[] bytes) throws ModelException {
		this.writeBytes(bytes, bytes.length);
	}

	protected void writeBytes(final ByteBuffer byteBuffer) throws ModelException {
		if (byteBuffer != null) {
			byte[] bytes = new byte[byteBuffer.limit() - byteBuffer.position()];
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
				"Model.writeBytesArray() IOException at file position %1$d writing byte[][] array starting at file position %2$d",
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
				"Model.writeBytesArrays() IOException at file position %1$d writing byte[][][] array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
	}

	protected void writeArgument(final Argument argument) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (argument != null) {
				this.io.writeInt(argument.transducerOrdinal);
				this.writeBytesArray(argument.tokens.getBytes());
			} else {
				this.io.writeInt(Integer.MIN_VALUE);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.writeArgument() IOException at file position %1$d writing Argument starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
	}

	protected void writeArguments(final Argument[] arguments) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (arguments != null) {
				this.writeInt(arguments.length);
				for (Argument argument : arguments) {
					this.writeArgument(argument);
				}
			} else {
				this.io.writeInt(0);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.writeArguments() IOException at file position %1$d writing Arguments starting at file position %2$d",
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
				"Model.writeInt() IOException writing int at file position %1$d",
					position), e);
		}
	}

	protected void writeLong(final long i) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeLong(i);
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.writeLong() IOException writing long at file position %1$d",
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
				"Model.writeIntArray() IOException at file position %1$d writing int array starting at file position %2$d",
					this.getSafeFilePosition(), position), e);
		}
	}

	protected void writeString(final String s) throws ModelException, CharacterCodingException {
		this.writeBytes(Codec.encode(s).bytes());
	}

	protected void writeTransitionMatrix(final int[][][] matrix) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			final int nInputs = matrix.length;
			final int nStates = nInputs > 0 ? matrix[0].length : 0;
			this.io.writeInt(nStates);
			this.io.writeInt(nInputs);
			for (int state = 0; state < nStates; state++) {
				int transitions = 0;
				for (int input = 0; input < nInputs; input++) {
					if (matrix[input][state][1] != 0) {
						transitions++;
					}
				}
				this.io.writeInt(transitions);
				for (int input = 0; input < nInputs; input++) {
					if (matrix[input][state][1] != 0) {
						this.io.writeInt(input);
						this.io.writeInt(matrix[input][state][0]);
						this.io.writeInt(matrix[input][state][1]);
					}
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"Model.writeTransitionMatrix() IOException at file position %2$d reading transition matrix starting at file position %1$d",
					position, this.getSafeFilePosition()), e);
		}
	}

	protected void writeTransducer(Bytes transducerName, int transducerOrdinal, int[] fields, int[] inputEquivalenceIndex, int[][][] kernelMatrix, int[] effectorVectors)
	throws ModelException, CharacterCodingException {
		this.setTransducerOffset(transducerOrdinal, this.seek(-1));
		this.writeString(transducerName.asString());
		this.writeInt(transducerOrdinal);
		this.writeIntArray(fields);
		this.writeIntArray(inputEquivalenceIndex);
		this.writeTransitionMatrix(kernelMatrix);
		this.writeIntArray(effectorVectors);
	}
}
//String name, int ordinal, int[] fields, int[] inputFilter, long[] transitionMatrix, int[] effectorVector