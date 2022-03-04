/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.characterforming.jrte.CompilationException;
import com.characterforming.jrte.GearboxException;
import com.characterforming.jrte.IEffector;
import com.characterforming.jrte.IParameterizedEffector;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.TransducerNotFoundException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.Bytes;

/**
 * @author kb
 */
public final class Gearbox  implements AutoCloseable {
	private final static Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
	private final static Logger rtcLogger = Logger.getLogger(Base.RTC_LOGGER_NAME);

	private RandomAccessFile io;
	private File gearboxPath;
	private ITarget modelTarget;
	private IEffector<?>[] modelEffectors;
	private Transduction modelTransduction;
	private HashMap<Bytes, Integer> signalOrdinalMap;
	private HashMap<Bytes, Integer> namedValueOrdinalMap;
	private HashMap<Bytes, Integer> transducerOrdinalMap;
	private HashMap<Bytes, Integer> effectorOrdinalMap;
	private ArrayList<HashMap<BytesArray, Integer>> effectorParametersMaps;
	private String ioMode;

	private Bytes transducerNameIndex[];
	private Transducer transducerObjectIndex[];
	private long transducerOffsetIndex[];

	private boolean deleteOnClose;

	
	public enum Gear { compile, run; }
	
	private void initialize() throws TargetBindingException {
		try {
			Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
			final FileHandler rteHandler = new FileHandler("jrte.log");
			rteHandler.setFormatter(new SimpleFormatter());
			rteLogger.addHandler(rteHandler);
			Logger rtcLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
			final FileHandler rtcHandler = new FileHandler("jrtc.log");
			rtcHandler.setFormatter(new SimpleFormatter());
			rtcLogger.addHandler(rtcHandler);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		IEffector<?>[] trexFx = this.modelTransduction.bindEffectors();
		IEffector<?>[] targetFx = this.modelTarget.bindEffectors();
		this.modelEffectors = new IEffector<?>[trexFx.length + targetFx.length];
		System.arraycopy(trexFx, 0, this.modelEffectors, 0, trexFx.length);
		System.arraycopy(targetFx, 0, this.modelEffectors, trexFx.length, targetFx.length);
		this.effectorOrdinalMap = new HashMap<Bytes, Integer>((this.modelEffectors.length * 5) / 4);
		this.effectorParametersMaps = new ArrayList<HashMap<BytesArray, Integer>>(this.modelEffectors.length);
		this.namedValueOrdinalMap = new HashMap<Bytes, Integer>(256);
		this.modelTransduction.setNamedValueOrdinalMap(Collections.unmodifiableMap(this.namedValueOrdinalMap));
		this.modelTransduction.setEffectors(this.modelEffectors);
		for (int effectorOrdinal = 0; effectorOrdinal < this.modelEffectors.length; effectorOrdinal++) {
			this.effectorParametersMaps.add(null);
			this.effectorOrdinalMap.put(this.modelEffectors[effectorOrdinal].getName(), effectorOrdinal);
		}
	}

	public Gearbox(Gear compileOrRun, final File gearboxPath, final ITarget target) throws GearboxException {
		this.modelTarget = target;
		this.gearboxPath = gearboxPath;
		this.modelTransduction = new Transduction(this);
		this.deleteOnClose = false;
		this.initialize();
		if (compileOrRun == Gear.run) {
			this.bind(target);
		}
	}

	/**
	 * @param target
	 * @return 
	 * @throws GearboxException
	 */
	boolean bind(final ITarget target) throws GearboxException {
		boolean abort = true;
		try {
			this.ioMode = "rw";
			this.io = new RandomAccessFile(this.gearboxPath, this.ioMode);
		} catch (final IOException e) {
			throw new GearboxException(String.format("Gearbox caught an IOException attempting to open '%1$s'",
				this.gearboxPath.getPath()), e);
		}
		try {
			long indexPosition = this.getLong();
			final String fileVersion = this.getString();
			if (!fileVersion.equals(Base.RTE_VERSION)) {
				throw new GearboxException(String.format("Current this version '%1$s' does not match version string '%2$s' from file '%3$s'", Base.RTE_VERSION, fileVersion, this.gearboxPath.getPath()));
			}
			final String targetClassname = this.getString();
			if (target == null || !targetClassname.equals(target.getClass().getName())) {
				throw new GearboxException(String.format("Wrong target class name '%1$s' -- target class is '%2$s' in this file '%3$s'", target != null ? target.getName() : "null", targetClassname, this.gearboxPath.getPath()));
			}
			this.modelTarget = target;
			try {
				this.io.seek(indexPosition);
				this.signalOrdinalMap = this.getOrdinalMap();
				this.namedValueOrdinalMap = this.getOrdinalMap();
				this.effectorOrdinalMap = this.getOrdinalMap();
				assert this.effectorOrdinalMap.size() == this.modelEffectors.length;
				this.transducerOrdinalMap = this.getOrdinalMap(); 
				if (!this.transducerOrdinalMap.containsKey(Bytes.encode(this.modelTarget.getName()))) {
					throw new GearboxException(String.format("Target name '%1$s' not found in name offset map for this file '%2$s'", this.modelTarget.getName(), this.gearboxPath.getPath()));
				}
				int transducerCount = this.transducerOrdinalMap.size();
				this.transducerNameIndex = new Bytes[transducerCount];
				this.transducerObjectIndex = new Transducer[transducerCount];
				this.transducerOffsetIndex = new long[transducerCount];
				for (int transducerOrdinal = 0; transducerOrdinal < transducerCount; transducerOrdinal++) {
					this.transducerNameIndex[transducerOrdinal] = new Bytes(this.getBytes());
					this.transducerOffsetIndex[transducerOrdinal] = this.getLong();
					this.transducerObjectIndex[transducerOrdinal] = null;
					assert this.transducerOrdinalMap.get(this.transducerNameIndex[transducerOrdinal]) == transducerOrdinal;
				}
				assert this.modelTransduction == (Transduction)this.modelEffectors[0].getTarget();
				this.modelTransduction.setNamedValueOrdinalMap(namedValueOrdinalMap);
				this.modelTransduction.setEffectors(this.modelEffectors);
				for (int effectorOrdinal = 0; effectorOrdinal < this.effectorOrdinalMap.size(); effectorOrdinal++) {
					byte[][][] effectorParameters = this.getBytesArrays();
					assert effectorParameters != null;
					if (this.modelEffectors[effectorOrdinal] instanceof IParameterizedEffector<?,?>) {
						IParameterizedEffector<?,?> effector = (IParameterizedEffector<?,?>)this.modelEffectors[effectorOrdinal];
						effector.newParameters(effectorParameters.length);
						for (int index = 0; index < effectorParameters.length; index++) {
							effector.compileParameter(index, effectorParameters[index]);
						}
					}
				}
			} catch (final IOException e) {
				throw new GearboxException(String.format("Gearbox caught an IOException attempting to seek to file position %1$d in this file '%2$s'",
					indexPosition, this.gearboxPath.getPath()), e);
			} catch (final Exception e) {
				long position = -1;
				try { position = this.io.getFilePointer(); } catch (IOException io) { }
				throw new GearboxException(String.format("Gearbox caught an Exception at file position %1$d attempting to load gearbox file '%2$s'",
						position, this.gearboxPath.getPath()), e);
			}
			abort = false;
		} finally {
			if (abort && this.io != null) {
				try {
					this.io.close();
				} catch (final IOException e) {
					e.printStackTrace(System.err);
				}
			}
		}
		return !abort;
	}

	/**
	 * @return false if compibindlation fails
	 * @throws GearboxException
	 */
	public boolean compile(File inrAutomataDirectory) throws GearboxException {
		if (!inrAutomataDirectory.isDirectory()) {
			throw new GearboxException(String.format("Not an automata directory '%1$s'",
				inrAutomataDirectory));			
		}
		try {
			if (this.gearboxPath.exists()) {
				this.gearboxPath.delete();
			}
			this.ioMode = "rw";
			this.gearboxPath.createNewFile();
			this.io = new RandomAccessFile(this.gearboxPath, this.ioMode);
		} catch (final IOException e) {
			throw new GearboxException(String.format("Gearbox caught an IOException attempting to open '%1$s'",
				this.gearboxPath.getPath()), e);
		}
		this.putLong(0);
		this.putString(Base.RTE_VERSION);
		this.putString(this.modelTarget.getClass().getName());
		this.signalOrdinalMap = new HashMap<Bytes, Integer>(Base.RTE_SIGNAL_BASE + 256);
		for (int ordinal = 0; ordinal < Base.RTE_SIGNAL_BASE; ordinal++) {
			Bytes name = new Bytes(new byte[] { 0, (byte)ordinal });
			this.signalOrdinalMap.put(name, ordinal);
		}
		for (Base.Signal signal : Base.Signal.values()) {
			final Bytes name = new Bytes(Base.RTE_SIGNAL_NAMES[signal.ordinal()]);
			final Integer ordinal = this.getSignalLimit();
			assert ordinal == signal.signal();
			this.signalOrdinalMap.put(name, ordinal);
		}
		assert this.signalOrdinalMap.size() == (Base.RTE_SIGNAL_BASE + Base.RTE_SIGNAL_NAMES.length);
		this.namedValueOrdinalMap = new HashMap<Bytes, Integer>(256);
		this.namedValueOrdinalMap.put(new Bytes(Base.ANONYMOUS_VALUE_NAME), Base.ANONYMOUS_VALUE_ORDINAL);
		this.transducerOrdinalMap = new HashMap<Bytes, Integer>(256);
		this.transducerObjectIndex = new Transducer[256];
		this.transducerOffsetIndex = new long[256];
		this.transducerNameIndex = new Bytes[256];
		try {
//			this.gearboxCompiler.compile(this.modelTarget, this.modelEffectors);
			this.gearboxPath.createNewFile();
			final ArrayList<String> errors = new ArrayList<String>(32);
			for (final String filename : inrAutomataDirectory.list()) {
				if (!filename.endsWith(Base.AUTOMATON_FILE_SUFFIX)) {
					continue;
				}
				try {
					final File inrAutomatonFile = new File(inrAutomataDirectory, filename);
					final String transducerFilename = filename.substring(0, filename.length() - Base.AUTOMATON_FILE_SUFFIX.length());
					final Bytes transducerToken = Bytes.encode(transducerFilename);
					errors.addAll(this.compileTransducer(transducerToken, inrAutomatonFile));
				} catch (Exception e) {
					String msg = String.format("Caught Exception compiling transducer '%1$s'", filename);
					Gearbox.rtcLogger.log(Level.SEVERE, msg, e);
					errors.add(msg);
				}
			}

			this.deleteOnClose &= (errors.size() == 0);
			if (!this.deleteOnClose) {
				try {
					this.deleteOnClose = true;
					long filePosition = this.seek(-1);
					int targetOrdinal = this.addTransducer(new Bytes(this.modelTarget.getName().getBytes()));
					this.setTransducerOffset(targetOrdinal, filePosition);
					long indexPosition = this.io.getFilePointer();
					assert indexPosition == this.io.length();
					this.putOrdinalMap(signalOrdinalMap);
					this.putOrdinalMap(namedValueOrdinalMap);
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
					this.deleteOnClose = false;
					saveMapFile(new File(this.gearboxPath.getPath().replaceAll(".gears", ".map")));
					System.out.println(String.format("Target class %1$s: %2$d text ordinals, %3$d signal ordinals, %4$d  effectors",
						this.modelTarget.getName(), Base.RTE_SIGNAL_BASE, this.getSignalCount(), this.effectorOrdinalMap.size()));
					System.out.println(String.format("Target package %1$s", 
						this.modelTarget.getClass().getPackage().getName()));
				} catch (GearboxException e) {
					String msg = String.format("Caught GearboxException compiling target for '%1$s'", this.gearboxPath.getPath());
					Gearbox.rtcLogger.log(Level.SEVERE, msg, e);
					throw e;
				} finally {
					setDeleteOnClose(!errors.isEmpty());
				}
			} else {
				for (final String error : errors) {
					Gearbox.rtcLogger.log(Level.SEVERE, error);
				}
				setDeleteOnClose(true);
				throw new GearboxException(String.format("Build failed for gearbox '%1$s'", this.gearboxPath.getPath()));
			}
		} catch (final IOException e) {
			throw new GearboxException("Gearbox caught an IOException attempting to close '%1$s' " + this.gearboxPath.getPath(), e);
		}
		return true;
	}

	public void saveMapFile(File mapFile) {
		PrintWriter mapWriter = null;
		try {
			mapWriter = new PrintWriter(mapFile);
			mapWriter.println(String.format("target\t%1$s\t#[T=%2$d;S=%3$d;E=%4$d;V=%4$d]",
				this.modelTarget.getName(), this.transducerOrdinalMap.size(), this.signalOrdinalMap.size() - Base.RTE_SIGNAL_BASE,
				this.effectorOrdinalMap.size(), this.namedValueOrdinalMap.size()));
			for (Map.Entry<Bytes, Integer> m : this.transducerOrdinalMap.entrySet()) {
				if (m.getValue() != (this.transducerOrdinalMap.size() - 1)) {
					mapWriter.println("transducer\t" + m.getKey() + "\t" + m.getValue());
				}
			}
			for (Map.Entry<Bytes, Integer> m : this.signalOrdinalMap.entrySet()) {
				if (m.getValue() > 255) {
					mapWriter.println("signal\t" + m.getKey() + "\t" + m.getValue());
				}
			}
			for (Map.Entry<Bytes, Integer> m : this.effectorOrdinalMap.entrySet()) {
				mapWriter.println("effector\t" + m.getKey() + "\t" + m.getValue());
			}
			for (Map.Entry<Bytes, Integer> m : this.namedValueOrdinalMap.entrySet()) {
				mapWriter.println("value" + m.getKey() + "\t" + m.getValue());
			}
			mapWriter.flush();
		} catch (final IOException e) {
			Gearbox.rtcLogger.log(Level.SEVERE, "Gearbox unable to create map file " + mapFile.getPath(), e);
		} finally {
			if (mapWriter != null) {
				mapWriter.close();
			}
		}
	}

	private ArrayList<String> compileTransducer(Bytes transducerName, File inrAutomatonFile) throws IOException, GearboxException {
		final TransducerCompiler transducerCompiler = new TransducerCompiler(transducerName, this);
		try {
			transducerCompiler.load(inrAutomatonFile);
			long filePosition = this.seek(-1);
			transducerCompiler.save(this, this.modelTarget.getName());
			int targetOrdinal = this.addTransducer(transducerName);
			this.setTransducerOffset(targetOrdinal, filePosition);
		} catch (CompilationException e) {
			return transducerCompiler.getErrors();
		}
		return new ArrayList<String>(0);
	}

	private void compileModelParameters() throws GearboxException {
		final Map<Bytes, Integer> effectorOrdinalMap = this.getEffectorOrdinalMap();
		final IEffector<?>[] effectors = this.getModelEffectors();
		final ArrayList<String> unbound = new ArrayList<String>();
		modelTransduction.setEffectors(effectors);
		modelTransduction.setNamedValueOrdinalMap(this.namedValueOrdinalMap);
		for (int effectorOrdinal = 0; effectorOrdinal < effectors.length; effectorOrdinal++) {
			IEffector<?> effector = effectors[effectorOrdinal];
			HashMap<BytesArray, Integer> parameters = this.effectorParametersMaps.get(effectorOrdinal);
			if (parameters != null) {
				if (effector instanceof IParameterizedEffector<?,?>) {
					final IParameterizedEffector<?,?> parameterizedEffector = (IParameterizedEffector<?,?>) effector;
					parameterizedEffector.newParameters(parameters.size());
					byte[][][] effectorParameters = new byte[parameters.size()][][];
					for (HashMap.Entry<BytesArray, Integer> e : parameters.entrySet()) {
						try {
							int v = e.getValue();
							byte[][] p = e.getKey().getBytes();
							parameterizedEffector.compileParameter(v, p);
							effectorParameters[v] = p;
						} catch (TargetBindingException x) {
							final String message = String.format("Unable to compile parameters for effector '%1$s'",
								parameterizedEffector.getName());
							Gearbox.rtcLogger.log(Level.SEVERE, message, x);
							unbound.add(message);
						}
					}
					this.putBytesArrays(effectorParameters);
				} else if (parameters.size() > 0) {
					final String message = String.format("%1$s.%2$s: effector does not accept parameters\n",
						this.modelTarget.getName(), effector.getName());
					Gearbox.rtcLogger.severe(message);
					unbound.add(message);
				} else {
					this.putInt(-1);
				}
			} else {
				this.putInt(-1);
			}
		}
		for (final Map.Entry<Bytes, Integer> entry : effectorOrdinalMap.entrySet()) {
			if (effectors[entry.getValue()] == null) {
				unbound.add(String.format("%1$s.%2$s: effector not found in target", this.modelTarget.getName(), entry.getKey()));
			}
		}
		if (unbound.size() > 0) {
			final TargetBindingException e = new TargetBindingException(this.modelTarget.getName());
			e.setUnboundEffectorList(unbound);
			throw e;
		}
	}


	public int compileParameters(final int effectorOrdinal, final byte[][] parameterBytes) {
		HashMap<BytesArray, Integer> parametersMap = this.effectorParametersMaps.get(effectorOrdinal);
		if (parametersMap == null) {
			parametersMap = new HashMap<BytesArray, Integer>(10);
			this.effectorParametersMaps.set(effectorOrdinal, parametersMap);
		}
		byte[][] parameters = parameterBytes;
		if ((this.modelEffectors[effectorOrdinal] instanceof BaseNamedValueEffector)
		|| (this.modelEffectors[effectorOrdinal] instanceof BaseInputOutputEffector)) {
			if (Base.isAnonymousValueReference(parameterBytes[0])) {
				parameters = Base.ANONYMOUS_VALUE_PARAMETER; 
			}
		}
		final BytesArray parametersArray = new BytesArray(parameters);
		Integer parametersIndex = parametersMap.get(parametersArray);
		if (parametersIndex == null) {
			parametersIndex = parametersMap.size();
			parametersMap.put(parametersArray, parametersIndex);
		}
		return parametersIndex;
	}

	public void bindParameters(IEffector<?>[] runtimeEffectors) {
		assert runtimeEffectors.length == this.modelEffectors.length;
		for (int i = 0; i < this.modelEffectors.length; i++) {
			if (this.modelEffectors[i] instanceof IParameterizedEffector<?,?>) {
				IParameterizedEffector<?,?> modelEffector = (IParameterizedEffector<?,?>)this.modelEffectors[i];
				IParameterizedEffector<?,?> boundEffector = (IParameterizedEffector<?,?>)runtimeEffectors[i];
				int parameterCount = modelEffector.getParameterCount();
				boundEffector.newParameters(parameterCount);
				for (int j = 0; j < parameterCount; j++) {
					boundEffector.setParameter(j, modelEffector.getParameter(j));
				}
			}
		}
	}

	@Override
	public void close() throws GearboxException {
		if (this.io != null) {
			try {
				this.io.close();
			} catch (IOException e) {
				throw new GearboxException("Unable to close gearbox file %1$s " + this.gearboxPath.getPath(), e);
			} finally {
				if (this.deleteOnClose && this.gearboxPath.exists() && !this.gearboxPath.delete()) {
						Gearbox.rteLogger.warning("Unable to delete invalid gearbox file %1$s " + this.gearboxPath.getPath());
				}
			}
		}
	}
	
	Map<Bytes, Integer> getEffectorOrdinalMap() {
		return Collections.unmodifiableMap(this.effectorOrdinalMap);
	}

	public int getEffectorOrdinal(Bytes bytes) {
		return this.effectorOrdinalMap.getOrDefault(bytes, -1);
	}

	String parameterToString(final byte[][] parameterBytes) {
		final StringBuilder strings = new StringBuilder();
		for (final byte[] bytes : parameterBytes) {
			strings.append('[');
			strings.append(Bytes.decode(bytes, bytes.length));
			strings.append(']');
		}
		return strings.toString();
	}

	File getGearboxPath() {
		return this.gearboxPath;
	}

	public Integer getInputOrdinal(final byte[] input) {
		if (input.length == 1) {
			return Byte.toUnsignedInt(input[0]);
		} else if (input[0] == Base.TYPE_REFERENCE_SIGNAL){
			return this.addSignal(new Bytes(input, 1, input.length -1));
		} else {
			return this.addSignal(new Bytes(input));
		}
	}

	public int getSignalCount() {
		return this.signalOrdinalMap.size();
	}

	public int getSignalLimit() {
		return this.signalOrdinalMap.size();
	}

	Integer getSignalOrdinal(final Bytes name) {
		return this.signalOrdinalMap.get(name);
	}

	public IEffector<?>[] getModelEffectors() {
		return this.modelEffectors;
	}

	Map<Bytes,Integer> getNamedValueMap() {
		return Collections.unmodifiableMap(this.namedValueOrdinalMap);
	}

	public ITarget getTarget() {
		return this.modelTarget;
	}

	public int addNamedValue(Bytes valueName) {
		Integer ordinal = this.namedValueOrdinalMap.get(valueName);
		if (ordinal == null) {
			ordinal = this.namedValueOrdinalMap.size();
			this.namedValueOrdinalMap.put(valueName, ordinal);
		}
		return ordinal;
	}

	public int addSignal(Bytes signalName) {
		Integer ordinal = this.signalOrdinalMap.get(signalName);
		if (ordinal == null) {
			ordinal = this.getSignalLimit();
			this.signalOrdinalMap.put(signalName, ordinal);
		}
		return ordinal;
	}

	public int addTransducer(Bytes transducerName) {
		Integer ordinal = this.transducerOrdinalMap.get(transducerName);
		if (ordinal == null) {
			ordinal = this.transducerOrdinalMap.size();
			this.transducerOrdinalMap.put(transducerName, ordinal);
			if (ordinal >= this.transducerNameIndex.length) {
				int length = ordinal + (ordinal << 1);
				this.transducerNameIndex = Arrays.copyOf(this.transducerNameIndex, length);
				this.transducerOffsetIndex = Arrays.copyOf(this.transducerOffsetIndex, length);
				this.transducerObjectIndex = Arrays.copyOf(this.transducerObjectIndex, length);
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
	
	Bytes getTransducerName(int transducerOrdinal) {
		return (transducerOrdinal < this.transducerNameIndex.length) ? this.transducerNameIndex[transducerOrdinal] : null;
	}

	long getTransducerOffset(int transducerOrdinal) {
		return (transducerOrdinal < this.transducerOffsetIndex.length) ? this.transducerOffsetIndex[transducerOrdinal] : null;
	}

	Transducer getTransducer(int transducerOrdinal) {
		return (transducerOrdinal < this.transducerObjectIndex.length) ? this.transducerObjectIndex[transducerOrdinal] : null;
	}

	Transducer loadTransducer(final Integer transducerOrdinal) throws TransducerNotFoundException, GearboxException {
		if ((0 <= transducerOrdinal) && (transducerOrdinal < this.transducerOrdinalMap.size())) {
			synchronized (this) {
				if (this.transducerObjectIndex[transducerOrdinal] == null) {
					try {
						this.io.seek(transducerOffsetIndex[transducerOrdinal]);
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

	int getInt() throws GearboxException {
		long position = 0;
		try {
			position = this.io.getFilePointer();
			return this.io.readInt();
		} catch (final IOException e) {
			throw new GearboxException(String.format(
				"Gearbox.getInt caught an IOException reading int at file position %1$d", position), e);
		}
	}

	long getLong() throws GearboxException {
		long position = 0;
		try {
			position = this.io.getFilePointer();
			return this.io.readLong();
		} catch (final IOException e) {
			throw new GearboxException(String.format(
				"Gearbox.getInt caught an IOException reading long at file position %1$d", position), e);
		}
	}

	int[] getIntArray() throws GearboxException {
		int[] ints = null;
		long position = 0;
		try {
			position = this.io.getFilePointer();
			ints = new int[this.io.readInt()];
			for (int i = 0; i < ints.length; i++) {
				ints[i] = this.io.readInt();
			}
		} catch (final IOException e) {
			throw new GearboxException(String.format(
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
			throw new GearboxException(String.format(
				"Gearbox.putTransitionMatrix caught an IOException writing transition matrix starting at file position %1$d after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
	}

	int[][] getTransitionMatrix() throws GearboxException {
		int[][] matrix;
		long position = 0;
		try {
			position = this.io.getFilePointer();
			final int rows = this.io.readInt();
			final int columns = this.io.readInt();
			matrix = new int[columns * rows][2];
			// matrix is an ExS array, column index ranges over E input equivalence ordinals, row index over S states 
			for (int column = 0; column < columns; column++) {
				for (int row = 0; row < rows; row++) {
					final int state = column * rows;
					final int cell = state + row;
					// Preset to invoke nul() effector on domain error, injects nul signal for next input
					matrix[cell][0] = state;
					matrix[cell][1] = Transduction.RTE_EFFECTOR_NUL;
				}
			}
			for (int row = 0; row < rows; row++) {
				final int count = this.io.readInt();
				for (int i = 0; i < count; i++) {
					final int column = this.io.readInt();
					final int fromState = column * rows;
					final int toState = this.io.readInt() * rows;
					final int effect = this.io.readInt();
					matrix[fromState + row] = new int[] { toState, effect };
				}
			}
		} catch (final IOException e) {
			throw new GearboxException(String.format(
				"Gearbox.getTransitionMatrix caught an IOException reading transition matrix starting at file position %1$d after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
		return matrix;
	}

	byte[] getBytes() throws GearboxException {
		byte[] bytes = null;
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
			throw new GearboxException(String.format(
				"Gearbox.getBytes caught an IOException reading %1$d bytes at file position %2$d  after file position %3$d",
				bytes.length, position, this.getSafeFilePosition()), e);
		}
		if (read >= 0 && read != bytes.length) {
			throw new GearboxException(String.format(
				"Gearbox.getBytes expected %1$d bytes at file position %2$d but read only %3$d", bytes.length,
				position, read));
		}
		return bytes;
	}

	byte[][] getBytesArray() throws GearboxException {
		byte[][] bytesArray = null;
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
			throw new GearboxException(String.format(
				"Gearbox.getBytesArray caught an IOException reading bytes array starting at file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
		return bytesArray;
	}

	HashMap<Bytes, Integer> getOrdinalMap() throws GearboxException {
		byte[][] bytesArray = this.getBytesArray();
		int capacity = (bytesArray != null) ? (bytesArray.length * 5) / 4 : 256;
		HashMap<Bytes, Integer> map = new HashMap<Bytes, Integer>(capacity);
		if (bytesArray != null) {
			for (int ordinal = 0; ordinal < bytesArray.length; ordinal++) {
				map.put(new Bytes(bytesArray[ordinal]), ordinal);
			} 
		}
		return map;
	}

	byte[][][] getBytesArrays() throws GearboxException {
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
			throw new GearboxException(String.format(
				"Gearbox.getBytesArray caught an IOException reading bytes array starting at file position after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
		return (bytesArrays != null) ? bytesArrays : new byte[][][] {};
	}

	String getString() throws GearboxException {
		byte bytes[] = this.getBytes();
		return Bytes.decode(bytes, bytes.length);
	}

	String[] getStringArray() throws GearboxException {
		final byte[][] bytesArray = this.getBytesArray();
		final String[] stringArray = new String[bytesArray.length];
		for (int i = 0; i < bytesArray.length; i++) {
			stringArray[i++] = Bytes.decode(bytesArray[i], bytesArray[i].length);
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

	void putBytes(final byte[] bytes) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			if (bytes != null) {
				this.io.writeInt(bytes.length);
				this.io.write(bytes);
			} else {
				this.io.writeInt(-1);
			}
		} catch (final IOException e) {
			throw new GearboxException(String.format(
				"Gearbox.putBytes caught an IOException writing %1$d bytes at file position %2$d after file position %3$d",
				bytes.length, position, this.getSafeFilePosition()), e);
		}
	}

	void putBytes(final Bytes bytes) throws GearboxException {
		this.putBytes((bytes != null) ? bytes.getBytes() : null);
	}

	void putBytes(final ByteBuffer byteBuffer) throws GearboxException {
		byte bytes[] = null;
		if (byteBuffer != null) {
			bytes = new byte[byteBuffer.limit() - byteBuffer.position()];
			byteBuffer.get(bytes, byteBuffer.position(), byteBuffer.limit());
		}
		this.putBytes(bytes);
	}

	void putBytesArray(final Bytes[] bytesArray) throws GearboxException {
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
			throw new GearboxException(String.format(
				"Gearbox.putBytesArray caught an IOException writing bytes array starting at file position after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
	}

	void putBytesArray(final byte[][] bytesArray) throws GearboxException {
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
			throw new GearboxException(String.format(
				"Gearbox.putBytesArray caught an IOException writing bytes array starting at file position after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
	}

	void putBytesArrays(final byte[][][] bytesArrays) throws GearboxException {
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
			throw new GearboxException(String.format(
				"Gearbox.putBytesArrays caught an IOException writing bytes arrays starting at file position after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
	}

	void putOrdinalMap(final Map<Bytes, Integer> map) throws GearboxException {
		byte names[][] = new byte[map.size()][];
		for (Entry<Bytes, Integer> entry : map.entrySet()) {
			names[entry.getValue()] = entry.getKey().getBytes();
		}
		this.putBytesArray(names);
	}

	void putInt(final int i) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeInt(i);
		} catch (final IOException e) {
			throw new GearboxException(String.format(
				"Gearbox.putInt caught an IOException writing %1$d at file position %2$d after file position %3$d",
				i, position, this.getSafeFilePosition()), e);
		}
	}

	void putLong(final long i) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeLong(i);
		} catch (final IOException e) {
			throw new GearboxException(String.format(
				"Gearbox.putLong caught an IOException writing %1$d at file position %2$d after file position %3$d",
				i, position, this.getSafeFilePosition()), e);
		}
	}

	public void putIntArray(final int[] ints) throws GearboxException {
		final long position = this.getSafeFilePosition();
		try {
			this.io.writeInt(ints.length);
			for (final int j : ints) {
				this.putInt(j);
			}
		} catch (final IOException e) {
			throw new GearboxException(String.format(
				"Gearbox.putIntArray caught an IOException writing int array starting at file position after file position %2$d",
				position, this.getSafeFilePosition()), e);
		}
	}

	public void putString(final String s) throws GearboxException {
		this.putBytes(Bytes.encode(s));
	}

	public long seek(final long filePosition) throws GearboxException {
		try {
			this.io.seek(filePosition != -1 ? filePosition : this.io.length());
			return this.io.getFilePointer();
		} catch (final IOException e) {
			throw new GearboxException(String.format(
				"Gearbox.seek caught an IOException seeking to file posiiton %1$d", filePosition), e);
		}
	}

	public Transduction bindTransduction(ITarget target) throws GearboxException {
		Transduction trex = new Transduction(this);
		IEffector<?>[] trexFx = trex.bindEffectors();
		IEffector<?>[] targetFx = target.bindEffectors();
		IEffector<?>[] boundFx = new IEffector<?>[trexFx.length + targetFx.length];
		System.arraycopy(trexFx, 0, boundFx, 0, trexFx.length);
		System.arraycopy(targetFx, 0, boundFx, trexFx.length, targetFx.length);
		trex.setNamedValueOrdinalMap(this.getNamedValueOrdinalMap());
		this.bindParameters(boundFx);
		trex.setEffectors(boundFx);
		return trex;
	}

	public Map<Bytes, Integer> getNamedValueOrdinalMap() {
		return Collections.unmodifiableMap(this.namedValueOrdinalMap);
	}

	public void setDeleteOnClose(boolean deletaOnClose) {
		this.deleteOnClose = deletaOnClose;
	}
}
