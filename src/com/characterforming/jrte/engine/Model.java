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
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.CompilationException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * @author Kim Briggs
 */
public final class Model implements AutoCloseable {
	static final int ANONYMOUS_FIELD_ORDINAL = 0;
	static final int CLEAR_ANONYMOUS_FIELD = 1;
	static final byte[] EMPTY = {};
	static final byte[] ANONYMOUS_FIELD_NAME = Model.EMPTY;
	static final byte[] ALL_FIELDS_NAME = { '*' };

	public enum TargetMode {
		NONE, COMPILE, RUN;
	}

	private final String ioMode;
	private final File modelPath;
	private final ITarget proxyTarget;
	private final Logger rtcLogger;
	private final CharsetEncoder encoder = Base.newCharsetEncoder();
	private final CharsetDecoder decoder = Base.newCharsetDecoder();
	private HashMap<Bytes, Integer> signalOrdinalMap;
	private HashMap<Bytes, Integer> fieldOrdinalMap;
	private HashMap<Bytes, Integer> transducerOrdinalMap;
	private HashMap<Bytes, Integer> effectorOrdinalMap;
	private ArrayList<HashMap<BytesArray, Integer>> effectorParametersMaps;
	private IEffector<?>[] proxyEffectors;
	private String modelVersion;

	private AtomicIntegerArray transducerAccessIndex;
	private AtomicReferenceArray<Transducer> transducerObjectIndex;
	private Bytes[] transducerNameIndex;
	private long[] transducerOffsetIndex;

	private RandomAccessFile io;
	private boolean deleteOnClose;
	private byte[][] fieldsNames;
	private byte[][] signalNames;
	private byte[][] transducerNames;

	private Model(final File modelPath, String targetClassname) throws ModelException {
		this.ioMode = "rw";
		this.deleteOnClose = true;
		this.modelPath = modelPath;
		this.rtcLogger = Base.getCompileLogger();
		this.modelVersion = Base.RTE_VERSION;
		try {
			Class<?> targetClass = Class.forName(targetClassname);
			this.proxyTarget = (ITarget) targetClass.getDeclaredConstructor().newInstance();
			this.io = new RandomAccessFile(this.modelPath, this.ioMode);
			assert this.modelPath.length() == 0;
			this.putLong(0);
			this.putString(this.modelVersion);
			this.putString(targetClassname);
		} catch (FileNotFoundException e) {
			throw new ModelException(String.format("Unable to create model file '%s'.",
				this.modelPath.toPath().toString()), e);
		} catch (ClassNotFoundException e) {
			throw new ModelException(String.format("Unable to instantiate target class '%s'.",
				targetClassname), e);
		} catch (Exception e) {
			throw new ModelException(String.format("Class '%s' does not implement the ITarget interface.",
				targetClassname), e);
		}
	}

	private Model(final File modelPath) throws ModelException {
		this.ioMode = "r";
		this.deleteOnClose = false;
		this.modelPath = modelPath;
		this.rtcLogger = Base.getCompileLogger();
		Base.getRuntimeLogger();
		String targetClassname = "?";
		try {
			this.io = new RandomAccessFile(this.modelPath, this.ioMode);
			this.io.seek(0);
			this.getLong();
			this.modelVersion = this.getString();
			targetClassname = this.getString();
			Class<?> targetClass = Class.forName(targetClassname);
			this.proxyTarget = (ITarget) targetClass.getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException e) {
			throw new ModelException(String.format("Unable to instantiate target class '%s' from model file %s.",
				targetClassname, this.modelPath.toPath().toString()), e);
		} catch (IOException e) {
			throw new ModelException(String.format("Failed to read preamble from model file %s.",
				this.modelPath.toPath().toString()), e);
		} catch (Exception e) {
			throw new ModelException(String.format("Class '%s' from model file %s does not implement the ITarget interface.",
				targetClassname, this.modelPath.toPath().toString()), e);
		}
	}

	private void initialize(TargetMode targetMode) throws ModelException {
		Transductor proxyTransductor = new Transductor(this, targetMode);
		proxyTransductor.setFieldOrdinalMap(this.fieldOrdinalMap);
		IEffector<?>[] trexFx = proxyTransductor.getEffectors();
		IEffector<?>[] targetFx = this.proxyTarget.getEffectors();
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

	/**
	 * Determine model version (current or previous (deprecated), else obsolete)
	 *
	 * @return the model version string
	 */
	String getModelVersion() {
		return this.modelVersion;
	}

	/**
	 * @param modelFile the model file
	 * @param targetClassname the fully qualified name of the target class
	 * @return the new model instance
	 * @throws ModelException on error
	 */
	public static Model create(final File modelPath, String targetClassname) throws ModelException {
		Model model = new Model(modelPath, targetClassname);
		assert model.modelPath.exists();
		assert model.ioMode.equals("rw");
		model.signalOrdinalMap = new HashMap<>(256);
		model.transducerOrdinalMap = new HashMap<>(256);
		model.fieldOrdinalMap = new HashMap<>(256);
		for (int ordinal = 0; ordinal < Base.RTE_SIGNAL_BASE; ordinal++) {
			Bytes name = new Bytes(new byte[] { 0, (byte) ordinal });
			model.signalOrdinalMap.put(name, ordinal);
		}
		for (Signal signal : Signal.values()) {
			assert model.getSignalLimit() == signal.signal();
			model.signalOrdinalMap.put(signal.key(), signal.signal());
		}
		assert model.signalOrdinalMap.size() == (Base.RTE_SIGNAL_BASE + Signal.values().length);
		model.fieldOrdinalMap.put(new Bytes(Model.ANONYMOUS_FIELD_NAME), Model.ANONYMOUS_FIELD_ORDINAL);
		model.fieldOrdinalMap.put(new Bytes(Model.ALL_FIELDS_NAME), Model.CLEAR_ANONYMOUS_FIELD);
		model.transducerObjectIndex = null;
		model.transducerOffsetIndex = new long[256];
		model.transducerNameIndex = new Bytes[256];
		model.initialize(TargetMode.COMPILE);
		return model;
	}

	/**
	 * @return false if compilation fails
	 * @throws ModelException on error
	 */
	boolean save() throws ModelException {
		File mapFile = new File(this.modelPath.getPath().replaceAll(".model", ".map"));
		try {
			long filePosition = this.seek(-1);
			int targetOrdinal = this.addTransducer(new Bytes(this.proxyTarget.getName().getBytes()));
			this.setTransducerOffset(targetOrdinal, filePosition);
			long indexPosition = this.io.getFilePointer();
			assert indexPosition == this.io.length();
			this.putOrdinalMap(signalOrdinalMap);
			this.putOrdinalMap(fieldOrdinalMap);
			this.putOrdinalMap(effectorOrdinalMap);
			this.putOrdinalMap(transducerOrdinalMap);
			int transducerCount = this.transducerOrdinalMap.size();
			for (int index = 0; index < transducerCount; index++) {
				assert this.transducerOffsetIndex[index] > 0;
				this.putBytes(this.transducerNameIndex[index]);
				this.putLong(this.transducerOffsetIndex[index]);
			}
			this.compileModelParameters();
			this.seek(0);
			this.putLong(indexPosition);
			saveMapFile(mapFile);
			setDeleteOnClose(false);
			String msg = String.format(
				"%1$s: target class %2$s%n%3$d transducers; %4$d effectors; %5$d fields; %6$d signal ordinals%n",
				this.modelPath.getPath(), this.proxyTarget.getClass().getName(), this.transducerOrdinalMap.size() - 1,
				this.effectorOrdinalMap.size(),	this.fieldOrdinalMap.size(), this.getSignalCount());
			this.rtcLogger.log(Level.INFO, msg);
		} catch (IOException e) {
			throw new ModelException(
				String.format("IOException caught compiling model file '%1$s'", this.modelPath.getPath()), e);
		} catch (RiboseException e) {
			throw new ModelException(
				String.format("RteException caught compiling model file '%1$s'", this.modelPath.getPath()), e);
		} finally {
			if (this.transducerOrdinalMap.size() <= 1) {
				this.rtcLogger.log(Level.SEVERE, "No transducers compiled to {0}", this.modelPath.getPath());
				this.deleteOnClose = true;
			}
			if (this.deleteOnClose) {
				this.rtcLogger.log(Level.SEVERE,"Compilation failed for model {0}",	this.modelPath.getPath());
				if (!mapFile.delete()) {
					this.rtcLogger.log(Level.WARNING, "Unable to delete {0}", mapFile.getPath());
				}
			}
			this.close();
		}
		return !this.deleteOnClose;
	}

	/**
	 * Bind target instance to runtime model.
	 *
	 * @param modelFile the model file
	 * @return the loaded model instance
	 * @throws ModelException on error
	 */
	public static Model load(File modelPath) throws ModelException {
		Model model = new Model(modelPath);
		model.seek(0);
		long indexPosition = model.getLong();
		final String loadedVersion = model.getString();
		if (!loadedVersion.equals(Base.RTE_VERSION) && !loadedVersion.equals(Base.RTE_PREVIOUS)) {
			throw new ModelException(String.format("Current model version '%1$s' does not match version string '%2$s' from model file '%3$s'",
				Base.RTE_VERSION, loadedVersion, model.modelPath.getPath()));
		}
		final String targetClassname = model.getString();
		if (!targetClassname.equals(model.proxyTarget.getClass().getName())) {
			throw new ModelException(
				String.format("Can't load model for target class '%1$s'; '%2$s' is target class for model file '%3$s'",
					model.proxyTarget.getName(), targetClassname, model.modelPath.getPath()));
		}
		model.modelVersion = loadedVersion;
		model.seek(indexPosition);
		model.signalOrdinalMap = model.getOrdinalMap();
		model.signalNames = model.getValueNames(model.signalOrdinalMap);
		model.fieldOrdinalMap = model.getOrdinalMap();
		model.fieldsNames = model.getValueNames(model.fieldOrdinalMap);
		model.effectorOrdinalMap = model.getOrdinalMap();
		model.transducerOrdinalMap = model.getOrdinalMap();
		model.transducerNames = model.getValueNames(model.fieldOrdinalMap);
		model.initialize(TargetMode.RUN);
		assert model.effectorOrdinalMap.size() == model.proxyEffectors.length;
		if (!model.transducerOrdinalMap.containsKey(Bytes.encode(model.encoder, model.proxyTarget.getName()))) {
			throw new ModelException(String.format("Target name '%1$s' not found in name offset map for model file '%2$s'",
				model.proxyTarget.getName(), model.modelPath.getPath()));
		}
		int transducerCount = model.transducerOrdinalMap.size();
		model.transducerNameIndex = new Bytes[transducerCount];
		model.transducerAccessIndex = new AtomicIntegerArray(transducerCount);
		model.transducerObjectIndex = new AtomicReferenceArray<>(transducerCount);
		model.transducerOffsetIndex = new long[transducerCount];
		for (int transducerOrdinal = 0; transducerOrdinal < transducerCount; transducerOrdinal++) {
			model.transducerNameIndex[transducerOrdinal] = new Bytes(model.getBytes());
			model.transducerOffsetIndex[transducerOrdinal] = model.getLong();
			assert model.transducerOrdinalMap.get(model.transducerNameIndex[transducerOrdinal]) == transducerOrdinal;
		}
		for (int effectorOrdinal = 0; effectorOrdinal < model.effectorOrdinalMap.size(); effectorOrdinal++) {
			byte[][][] effectorParameters = model.getBytesArrays();
			assert effectorParameters != null;
			if (model.proxyEffectors[effectorOrdinal] instanceof IParameterizedEffector<?,?>) {
				assert model.proxyEffectors[effectorOrdinal] instanceof BaseParameterizedEffector<?,?>;
				BaseParameterizedEffector<?,?> effector = (BaseParameterizedEffector<?,?>)model.proxyEffectors[effectorOrdinal];
				effector.compileParameters(effectorParameters);
			}
		}
		return model;
	}

	private byte[][] getValueNames(HashMap<Bytes, Integer> nameOrdinalMap) {
		byte[][] names = new byte[nameOrdinalMap.size()][];
		for (Entry<Bytes, Integer> e : nameOrdinalMap.entrySet()) {
			names[e.getValue()] = e.getKey().getData();
		}
		return names;
	}

	byte[] getFieldName(int fieldOrdinal) {
		return this.fieldsNames[fieldOrdinal];
	}

	byte[] getSignalName(int signalOrdinal) {
		return this.signalNames[signalOrdinal];
	}

	byte[] getTransducerName(int transducerOrdinal) {
		return this.transducerNames[transducerOrdinal];
	}

	/**
	 * Instantiate a new {@code Transductor} and bind it to a runtime target.
	 *
	 * @param target the runtime target to bind the transductor to
	 * @return the bound transductor instance
	 */
	public Transductor bindTransductor(ITarget target) throws ModelException {
		assert !this.ioMode.equals("rw");
		Class<? extends ITarget> targetClass = target.getClass();
		Class<? extends ITarget> modelClass = this.proxyTarget.getClass();
		if (!modelClass.isAssignableFrom(targetClass)) {
			throw new ModelException(
				String.format("Cannot bind instance of target class '%1$s', can only bind to model target class '%2$s'",
					target.getClass().getName(), this.proxyTarget.getClass().getName()));
		}
		if (target == this.proxyTarget) {
			throw new ModelException(String.format("Cannot use model target instance as runtime target: $%s",
				this.proxyTarget.getClass().getName()));
		}
		Transductor trex = new Transductor(this, TargetMode.RUN);
		IEffector<?>[] trexFx = trex.getEffectors();
		IEffector<?>[] targetFx = target.getEffectors();
		IEffector<?>[] boundFx = new IEffector<?>[trexFx.length + targetFx.length];
		System.arraycopy(trexFx, 0, boundFx, 0, trexFx.length);
		System.arraycopy(targetFx, 0, boundFx, trexFx.length, targetFx.length);
		checkTargetEffectors(trex, boundFx);
		trex.setFieldOrdinalMap(this.fieldOrdinalMap);
		this.bindParameters(trex, boundFx);
		return trex;
	}

	@Override
	public void close() {
		try {
			if (this.io != null) {
				this.io.close();
			}
		} catch (IOException e) {
			this.rtcLogger.log(Level.SEVERE, String.format("Unable to close model file %1$s", 
				this.modelPath.getPath()), e);
		} finally {
			this.io = null;
			if (this.deleteOnClose && this.modelPath.exists() && !this.modelPath.delete()) {
				this.rtcLogger.log(Level.WARNING, "Unable to delete invalid model file%1$s{0}",
					this.modelPath.getPath());
			}
		}
	}

	private void saveMapFile(File mapFile) {
		try (PrintWriter mapWriter = new PrintWriter(mapFile)) {
			mapWriter.println(String.format("version\t%1$s", this.modelVersion));
			mapWriter.println(String.format("target\t%1$s", this.proxyTarget.getClass().getName()));
			Bytes[] transducerIndex = new Bytes[this.transducerOrdinalMap.size()];
			for (Map.Entry<Bytes, Integer> m : this.transducerOrdinalMap.entrySet()) {
				transducerIndex[m.getValue()] = m.getKey();
			}
			for (int i = 0; i < (transducerIndex.length - 1); i++) {
				mapWriter.println("transducer\t" + transducerIndex[i] + "\t" + i);
			}
			Bytes[] signalIndex = new Bytes[this.signalOrdinalMap.size()];
			for (Map.Entry<Bytes, Integer> m : this.signalOrdinalMap.entrySet()) {
				signalIndex[m.getValue()] = m.getKey();
			}
			for (int i = Base.RTE_SIGNAL_BASE; i < signalIndex.length; i++) {
				mapWriter.println("signal\t" + signalIndex[i] + "\t" + i);
			}
			Bytes[] effectorIndex = new Bytes[this.effectorOrdinalMap.size()];
			for (Map.Entry<Bytes, Integer> m : this.effectorOrdinalMap.entrySet()) {
				effectorIndex[m.getValue()] = m.getKey();
			}
			for (int i = 0; i < effectorIndex.length; i++) {
				mapWriter.println("effector\t" + effectorIndex[i] + "\t" + i);
			}
			Bytes[] fieldIndex = new Bytes[this.fieldOrdinalMap.size()];
			for (Map.Entry<Bytes, Integer> m : this.fieldOrdinalMap.entrySet()) {
				fieldIndex[m.getValue()] = m.getKey();
			}
			for (int i = 0; i < fieldIndex.length; i++) {
				mapWriter.println("field\t" + fieldIndex[i] + "\t" + i);
			}
			mapWriter.flush();
		} catch (final IOException e) {
			this.rtcLogger.log(Level.SEVERE, e, () -> "Model unable to create map file " + mapFile.getPath());
		}
	}

	private void checkTargetEffectors(ITarget target, IEffector<?>[] boundFx) throws ModelException {
		boolean checked = boundFx.length == this.proxyEffectors.length;
		for (int i = 0; checked && i < boundFx.length; i++) {
			checked &= boundFx[i].equivalent(this.proxyEffectors[i]);
		}
		if (!checked) {
			StringBuilder msg = new StringBuilder(256);
			msg.append("Target ").append(target.getName()).append(" effectors do not match proxy effectors.")
					.append(Base.LINEEND).append("\tTarget:");
			for (IEffector<?> fx : boundFx) {
				msg.append(' ').append(fx.getName());
			}
			msg.append(Base.LINEEND).append("\tProxy:");
			for (IEffector<?> fx : this.proxyEffectors) {
				msg.append(' ').append(fx.getName());
			}
			throw new ModelException(msg.toString());
		}
	}

	private boolean compileModelParameters() throws ModelException {
		boolean fail = false;
		final Map<Bytes, Integer> effectorMap = this.getEffectorOrdinalMap();
		for (int effectorOrdinal = 0; effectorOrdinal < this.proxyEffectors.length; effectorOrdinal++) {
			IEffector<?> effector = this.proxyEffectors[effectorOrdinal];
			HashMap<BytesArray, Integer> parameters = this.effectorParametersMaps.get(effectorOrdinal);
			if (parameters != null) {
				if (effector instanceof IParameterizedEffector<?,?>) {
					assert effector instanceof BaseParameterizedEffector<?,?>;
					final BaseParameterizedEffector<?,?> parameterizedEffector = (BaseParameterizedEffector<?,?>)effector;
					byte[][][] effectorParameterTokens = new byte[parameters.size()][][];
					for (Map.Entry<BytesArray, Integer> e : parameters.entrySet()) {
						effectorParameterTokens[e.getValue()] = e.getKey().getBytes();
					}
					parameterizedEffector.compileParameters(effectorParameterTokens);
					this.putBytesArrays(effectorParameterTokens);
				} else if (parameters.size() > 0) {
					this.rtcLogger.log(Level.SEVERE, () -> String.format("%1$s.%2$s: effector does not accept parameters",
						this.proxyTarget.getName(), effector.getName()));
					fail = true;
				} else {
					this.putInt(-1);
				}
			} else {
				this.putInt(-1);
			}
		}
		for (final Map.Entry<Bytes, Integer> entry : effectorMap.entrySet()) {
			if (this.proxyEffectors[entry.getValue()] == null) {
				this.rtcLogger.log(Level.SEVERE, () -> String.format("%1$s.%2$s: effector ordinal not found",
					this.proxyTarget.getName(), entry.getKey().toString()));
				fail = true;
			}
		}
		return !fail;
	}

	int compileParameters(final int effectorOrdinal, final byte[][] parameterBytes) {
		HashMap<BytesArray, Integer> parametersMap = this.effectorParametersMaps.get(effectorOrdinal);
		if (parametersMap == null) {
			parametersMap = new HashMap<>(10);
			this.effectorParametersMaps.set(effectorOrdinal, parametersMap);
		}
		final int mapSize = parametersMap.size();
		return parametersMap.computeIfAbsent(new BytesArray(parameterBytes), absent -> mapSize);
	}

	private IEffector<?>[] bindParameters(Transductor trex, IEffector<?>[] runtimeEffectors) throws TargetBindingException {
		assert runtimeEffectors.length == this.proxyEffectors.length;
		for (int i = 0; i < this.proxyEffectors.length; i++) {
			runtimeEffectors[i].setOutput(trex);
			if (this.proxyEffectors[i] instanceof IParameterizedEffector<?, ?>) {
				IParameterizedEffector<?, ?> proxyEffector = (IParameterizedEffector<?, ?>) this.proxyEffectors[i];
				IParameterizedEffector<?, ?> boundEffector = (IParameterizedEffector<?, ?>) runtimeEffectors[i];
				boundEffector.setParameters(proxyEffector);
			}
		}		
		trex.setEffectors(runtimeEffectors);
		return runtimeEffectors;
	}

	Map<Bytes, Integer> getEffectorOrdinalMap() {
		return Collections.unmodifiableMap(this.effectorOrdinalMap);
	}

	int getEffectorOrdinal(Bytes bytes) {
		return this.effectorOrdinalMap.getOrDefault(bytes, -1);
	}

	File getModelPath() {
		return this.modelPath;
	}

	Integer getInputOrdinal(final byte[] input) throws CompilationException {
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

	int getSignalCount() {
		return this.signalOrdinalMap.size() - Base.RTE_SIGNAL_BASE;
	}

	int getSignalLimit() {
		return this.signalOrdinalMap.size();
	}

	Integer getSignalOrdinal(final Bytes name) {
		return this.signalOrdinalMap.get(name);
	}

	Map<Bytes,Integer> getFieldMap() {
		return Collections.unmodifiableMap(this.fieldOrdinalMap);
	}

	public String showParameter(int effectorOrdinal, int parameterIndex) {
		if (this.proxyEffectors[effectorOrdinal] instanceof IParameterizedEffector) {
			IParameterizedEffector<?,?> effector = (IParameterizedEffector<?,?>)this.proxyEffectors[effectorOrdinal];
			return effector.showParameter(parameterIndex);
		}
		return "VOID";
	}

	int addField(Bytes fieldName) {
		final int mapSize = this.fieldOrdinalMap.size();
		return this.fieldOrdinalMap.computeIfAbsent(fieldName, absent-> mapSize);
	}

	int addSignal(Bytes signalName) {
		final int mapSize = this.signalOrdinalMap.size();
		return this.signalOrdinalMap.computeIfAbsent(signalName, absent-> mapSize);
	}

	int addTransducer(Bytes transducerName) {
		assert null == this.transducerObjectIndex;
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

	public void setTransducerOffset(int transducerOrdinal, long offset) {
		this.transducerOffsetIndex[transducerOrdinal] = offset;
	}

	int getTransducerOrdinal(Bytes transducerName) {
		Integer ordinal = this.transducerOrdinalMap.get(transducerName);
		return (null != ordinal) ? ordinal.intValue() : -1;
	}

	Transducer loadTransducer(final Integer transducerOrdinal) throws ModelException {
		if (0 > transducerOrdinal || transducerOrdinal >= this.transducerAccessIndex.length()) {
			throw new ModelException(String.format("RuntimeModel.loadTransducer(ordinal:%d) ordinal out of range [0,%d)",
				transducerOrdinal, this.transducerObjectIndex.length()));
		}
		if (0 == this.transducerAccessIndex.compareAndExchange(transducerOrdinal, 0, 1)) {
			try {
				this.io.seek(transducerOffsetIndex[transducerOrdinal]);
			} catch (final IOException e) {
				throw new ModelException(
					String.format("RuntimeModel.loadTransducer(ordinal:%d) caught an IOException after seek to %d",
					transducerOrdinal, transducerOffsetIndex[transducerOrdinal]), e);
			}
			final String name = this.getString();
			final String target = this.getString();
			final int[] inputs = this.getIntArray();
			final long[] transitions = this.getTransitionMatrix();
			final int[] effects = this.getIntArray();
			final Transducer newt = new Transducer(name, target, inputs, transitions, effects);
			final Transducer oldt = this.transducerObjectIndex.compareAndExchange(transducerOrdinal, null, newt);
			final int access = this.transducerAccessIndex.compareAndExchange(transducerOrdinal, 1, 2);
			assert null == oldt && 1 == access;
		} else while (1 == this.transducerAccessIndex.compareAndExchange(transducerOrdinal, 1, 1)) {
			java.lang.Thread.onSpinWait();
		}
		final Transducer t = this.transducerObjectIndex.get(transducerOrdinal);
		assert t != null && 2 == this.transducerAccessIndex.get(transducerOrdinal);
		return t;
	}

	int getInt() throws ModelException {
		long position = 0;
		try {
			position = this.io.getFilePointer();
			return this.io.readInt();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.getInt caught an IOException reading int at file position %1$d", position), e);
		}
	}

	long getLong() throws ModelException {
		long position = 0;
		try {
			position = this.io.getFilePointer();
			return this.io.readLong();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.getInt caught an IOException reading long at file position %1$d", position), e);
		}
	}

	int[] getIntArray() throws ModelException {
		int[] ints = {};
		long position = 0;
		try {
			position = this.io.getFilePointer();
			ints = new int[this.io.readInt()];
			for (int i = 0; i < ints.length; i++) {
				ints[i] = this.io.readInt();
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.getBytes caught an IOException reading %1$d bytes at file position %2$d  after file position %3$d",
				ints.length, position, this.getSafeFilePosition()), e);
		}
		return ints;
	}

	void putTransitionMatrix(final int[][][] matrix) throws ModelException {
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
				"RuntimeModel.putTransitionMatrix caught an IOException writing transition matrix starting at file position %1$d after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
	}

	long[] getTransitionMatrix() throws ModelException {
		long[] matrix;
		long position = 0;
		try {
			position = this.io.getFilePointer();
			final int rows = this.io.readInt();
			final int columns = this.io.readInt();
			matrix = new long[columns * rows];
			// matrix is an ExS array, column index ranges over E input equivalence ordinals, row index over S states
			for (int column = 0; column < columns; column++) {
				for (int row = 0; row < rows; row++) {
					// Preset to invoke nul() effector on domain error, injects nul signal for next input
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
				"RuntimeModel.getTransitionMatrix caught an IOException reading transition matrix starting at file position %1$d after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
		return matrix;
	}

	byte[] getBytes() throws ModelException {
		byte[] bytes = {};
		long position = 0;
		int read = -1;
		try {
			position = this.io.getFilePointer();
			int length = this.io.readInt();
			if (length >= 0) {
				bytes = new byte[length];
				read = this.io.read(bytes);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.getBytes caught an IOException reading %1$d bytes at file position %2$d  after file position %3$d",
				bytes.length, position, this.getSafeFilePosition()), e);
		}
		if (read >= 0 && read != bytes.length) {
			throw new ModelException(String.format(
				"RuntimeModel.getBytes expected %1$d bytes at file position %2$d but read only %3$d", bytes.length,
				position, read));
		}
		return bytes;
	}

	byte[][] getBytesArray() throws ModelException {
		byte[][] bytesArray = {};
		long position = 0;
		try {
			position = this.io.getFilePointer();
			int length = this.io.readInt();
			if (length >= 0) {
				bytesArray = new byte[length][];
				for (int i = 0; i < bytesArray.length; i++) {
					bytesArray[i] = this.getBytes();
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.getBytesArray caught an IOException at file position %1$s reading bytes array starting at file position %2$d",
				this.getSafeFilePosition(), position), e);
		}
		return bytesArray;
	}

	HashMap<Bytes, Integer> getOrdinalMap() throws ModelException {
		byte[][] bytesArray = this.getBytesArray();
		HashMap<Bytes, Integer> map = new HashMap<>((bytesArray.length * 5) >> 2);
		for (int ordinal = 0; ordinal < bytesArray.length; ordinal++) {
			map.put(new Bytes(bytesArray[ordinal]), ordinal);
		}
		return map;
	}

	byte[][][] getBytesArrays() throws ModelException {
		byte[][][] bytesArrays = null;
		long position = 0;
		try {
			position = this.io.getFilePointer();
			int length = this.io.readInt();
			if (length >= 0) {
				bytesArrays = new byte[length][][];
				for (int i = 0; i < bytesArrays.length; i++) {
					bytesArrays[i] = this.getBytesArray();
				}
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.getBytesArrays caught an IOException at file position %1$d reading bytes array starting at file position %2$d",
				this.getSafeFilePosition(), position), e);
		}
		return (bytesArrays != null) ? bytesArrays : new byte[][][] {};
	}

	String getString() throws ModelException {
		byte[] bytes = this.getBytes();
		return Bytes.decode(this.decoder, bytes, bytes.length).toString();
	}

	String[] getStringArray() throws ModelException {
		final byte[][] bytesArray = this.getBytesArray();
		final String[] stringArray = new String[bytesArray.length];
		for (int i = 0; i < bytesArray.length; i++) {
			stringArray[i] = Bytes.decode(this.decoder, bytesArray[i], bytesArray[i].length).toString();
		}
		return stringArray;
	}

	long getSafeFilePosition() {
		try {
			return this.io.getFilePointer();
		} catch (final IOException e) {
			return -1;
		}
	}

	void putBytes(final byte[] bytes) throws ModelException {
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
				"RuntimeModel.putBytes caught an IOException writing %1$d bytes at file position %2$d after file position %3$d",
				bytes != null ? bytes.length : 0, position, this.getSafeFilePosition()), e);
		}
	}

	void putBytes(final Bytes bytes) throws ModelException {
		if (bytes != null) {
			this.putBytes(bytes.getData());
		}
	}

	void putBytes(final ByteBuffer byteBuffer) throws ModelException {
		byte[] bytes = null;
		if (byteBuffer != null) {
			bytes = new byte[byteBuffer.limit() - byteBuffer.position()];
			byteBuffer.get(bytes, byteBuffer.position(), byteBuffer.limit());
			this.putBytes(bytes);
		}
	}

	void putBytesArray(final Bytes[] bytesArray) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (bytesArray != null) {
				this.io.writeInt(bytesArray.length);
				for (final Bytes element : bytesArray) {
					this.putBytes(element);
				}
			} else {
				this.io.writeInt(-1);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.putBytesArray(Bytes[]) caught an IOException at file position %1$d writing byte[][] array starting at file position %2$d",
				this.getSafeFilePosition(), position), e);
		}
	}

	void putBytesArray(final byte[][] bytesArray) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (bytesArray != null) {
				this.io.writeInt(bytesArray.length);
				for (final byte[] element : bytesArray) {
					this.putBytes(element);
				}
			} else {
				this.io.writeInt(-1);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.putBytesArray(byte[][]) caught an IOException at file position %1$d writing byte[][] array starting at file position %2$d",
				this.getSafeFilePosition(), position), e);
		}
	}

	void putBytesArrays(final byte[][][] bytesArrays) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			if (bytesArrays != null) {
				this.io.writeInt(bytesArrays.length);
				for (final byte[][] bytesArray : bytesArrays) {
					this.putBytesArray(bytesArray);
				}
			} else {
				this.io.writeInt(-1);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.putBytesArray(Byte[][][]) caught an IOException at file position %1$d writing byte[][][] array starting at file position %2$d",
				this.getSafeFilePosition(), position), e);
		}
	}

	void putOrdinalMap(final Map<Bytes, Integer> map) throws ModelException {
		byte[][] names = new byte[map.size()][];
		for (Entry<Bytes, Integer> entry : map.entrySet()) {
			names[entry.getValue()] = entry.getKey().getData();
		}
		this.putBytesArray(names);
	}

	void putInt(final int i) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeInt(i);
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.putInt caught an IOException writing %1$d at file position %2$d after file position %3$d",
				i, position, this.getSafeFilePosition()), e);
		}
	}

	void putLong(final long i) throws ModelException {
		try {
			this.io.writeLong(i);
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.putLong caught an IOException writing %1$d at file position %2$d",
				i, this.getSafeFilePosition()), e);
		}
	}

	public void putIntArray(final int[] ints) throws ModelException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeInt(ints.length);
			for (final int j : ints) {
				this.putInt(j);
			}
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.putIntArray caught an IOException at file position %1$d writing int array starting at file position %2$d",
				this.getSafeFilePosition(), position), e);
		}
	}

	public void putString(final String s) throws ModelException {
		this.putBytes(Bytes.encode(this.encoder, s));
	}

	public long seek(final long filePosition) throws ModelException {
		try {
			this.io.seek(filePosition != -1 ? filePosition : this.io.length());
			return this.io.getFilePointer();
		} catch (final IOException e) {
			throw new ModelException(String.format(
				"RuntimeModel.seek caught an IOException seeking to file posiiton %1$d", filePosition), e);
		}
	}

	void setDeleteOnClose(boolean deleteOnClose) {
		this.deleteOnClose = deleteOnClose;
	}

	public String getTargetClassname() {
		return this.proxyTarget.getClass().getName();
	}
}
