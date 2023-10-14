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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 *
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.	See
 * LICENSE-gpl-3.0. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.IModel;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransduction;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.IToken.Type;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.CompilationException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/** Model compiler implements ITarget for the ribose compiler model. */
public final class ModelCompiler extends Model implements ITarget, AutoCloseable {

	private static final long VERSION = 210;
	private static final String AUTOMATON = "Automaton";
	private static final String AMBIGUOUS_STATE_MESSAGE = "%1$s: Ambiguous state %2$d";
	private static final int MIN_PRODUCT_LENGTH = Integer.parseInt(System.getProperty("ribose.product.threshold", "12"));
	private static final int MIN_SUM_SIZE = Integer.parseInt(System.getProperty("ribose.sum.threshold", "128"));
	
	private Bytes transducerName;
	private int transducerOrdinal;
	private ITransductor transductor;
	private HashMap<Integer, Integer> inputStateMap;
	private HashMap<Integer, ArrayList<Transition>> stateTransitionMap;
	private HashMap<Ints, Integer> effectorVectorMap;
	private ArrayList<HashSet<Token>> tapeTokens;
	private int[] effectorVectors;
	private List<String> errors;
	private Header header = null;
	private Transition[] transitions = null;
	private int[] inputEquivalenceIndex;
	private int[][][] kernelMatrix;
	private int transition;
	

	record Transition (int from, int to, int tape, Bytes symbol, boolean isFinal) {}

	record Header (int version, int tapes, int transitions, int states, int symbols) {}

	final class HeaderEffector extends BaseEffector<ModelCompiler> {
		private static final String[] fields = new String[] {
			"version", "tapes", "transitions", "states", "symbols"
		};
		private final int[] fieldOrdinals;

		HeaderEffector(ModelCompiler compiler) throws CharacterCodingException {
			super(compiler, "header");
			this.fieldOrdinals = new int[fields.length];
		}

		@Override
		public void setOutput(IOutput output) throws EffectorException {
			super.setOutput(output);
			try {
				for (int i = 0; i < fields.length; i++) {
					this.fieldOrdinals[i] = super.output.getLocalizedFieldIndex(ModelCompiler.AUTOMATON, fields[i]);
				}
			} catch (CharacterCodingException e) {
				throw new EffectorException(e);
			}
		}

		@Override
		public int invoke() throws EffectorException {
			ModelCompiler.this.putHeader(new Header(
				(int)super.output.asInteger(this.fieldOrdinals[0]),
				(int)super.output.asInteger(this.fieldOrdinals[1]),
				(int)super.output.asInteger(this.fieldOrdinals[2]),
				(int)super.output.asInteger(this.fieldOrdinals[3]),
				(int)super.output.asInteger(this.fieldOrdinals[4])));
			return IEffector.RTX_NONE;
		}
	}

	final class TransitionEffector extends BaseEffector<ModelCompiler> {
		private static final String[] fields = new String[] {
			"from", "to", "tape", "symbol"
		};
		private final int[] fieldOrdinals;

		TransitionEffector(ModelCompiler compiler) throws CharacterCodingException {
			super(compiler, "transition");
			this.fieldOrdinals = new int[fields.length];
		}

		@Override
		public void setOutput(IOutput output) throws EffectorException {
			super.setOutput(output);
			try {
				for (int i = 0; i < fields.length; i++) {
					this.fieldOrdinals[i] = super.output.getLocalizedFieldIndex(ModelCompiler.AUTOMATON, fields[i]);
				}
			} catch (CharacterCodingException e) {
				throw new EffectorException(e);
			}
		}

		@Override
		public int invoke() throws EffectorException {
			int from = (int)super.output.asInteger(this.fieldOrdinals[0]);
			int to = (int)super.output.asInteger(this.fieldOrdinals[1]);
			int tape = (int)super.output.asInteger(this.fieldOrdinals[2]);
			Bytes symbol = new Bytes(super.output.asBytes(this.fieldOrdinals[3]));
			boolean isFinal = to == 1 && tape == 0 && symbol.getLength() == 0;
			ModelCompiler.this.putTransition(new Transition(
				from, to, tape, symbol, isFinal));
			return IEffector.RTX_NONE;
		}
	}

	final class AutomatonEffector extends BaseEffector<ModelCompiler> {
		AutomatonEffector(ModelCompiler compiler) throws CharacterCodingException {
			super(compiler, "automaton");
		}

		@Override
		public int invoke() throws EffectorException {
			try {
				ModelCompiler.this.putAutomaton();
			} catch (CharacterCodingException e) {
				throw new EffectorException(e);
			}
			return IEffector.RTX_NONE;
		}
	}

	public ModelCompiler() {
		super();
	}

	private ModelCompiler(final File modelPath, Class<?> targetClass, TargetMode targetMode)
	throws ModelException {
		super(modelPath, targetClass, targetMode);
		assert super.modelPath.exists();
		assert super.targetMode.isLive();
		this.transductor = null;
		this.inputStateMap = null;
		this.stateTransitionMap = null;
		this.transducerName = null;
		this.transducerOrdinal = -1;
		this.effectorVectorMap = null;
		this.inputEquivalenceIndex = null;
		this.kernelMatrix = null;
		this.header = null;
		this.transitions = null;
		this.transition = 0;
		this.errors = new ArrayList<>();
		this.tapeTokens = new ArrayList<>(3);
		for (int tape = 0; tape < 3; tape++) {
			this.tapeTokens.add(tape, new HashSet<>(128));
		}
		HashSet<Token> parameterTokens = this.tapeTokens.get(2);
		parameterTokens.add(new Token(Model.ALL_FIELDS_NAME, Model.ALL_FIELDS_ORDINAL));
		for (Signal signal : Signal.values()) {
			if (!signal.isNone()) {
				parameterTokens.add(new Token(signal.reference().bytes(), signal.signal()));
			}
		}
	}

	@Override // com.characterforming.ribose.IModel.getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override // com.characterforming.ribose.IModel.getEffectors()
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		try {
			return new IEffector<?>[] {
				new HeaderEffector(this),
				new TransitionEffector(this),
				new AutomatonEffector(this)
			};
		} catch (CharacterCodingException e) {
			throw new TargetBindingException(e);
		}
	}

	@Override // AutoCloseable.close()
	public void close() {
		super.close();
	}

	public static boolean compileAutomata(Class<?> targetClass, File riboseModelFile, File inrAutomataDirectory) throws ModelException {
		Logger rtcLogger = Base.getCompileLogger();

		try (ModelCompiler proxyCompiler = new ModelCompiler()) {
			if (!proxyCompiler.createModelFile(riboseModelFile, rtcLogger)) {
				return false;
			}

			if (targetClass == ModelCompiler.class) {
				boolean saved = false;
				try (ModelCompiler compiler = new ModelCompiler(riboseModelFile, targetClass, TargetMode.LIVE_COMPILER)) {
					if (compiler.compileCompiler(inrAutomataDirectory)) {
						Argument[][] compiledParameters = compiler.compileModelParameters(compiler.errors);
						saved = compiler.validate() && compiler.save(compiledParameters);
						assert saved || compiler.deleteOnClose;
						assert saved == !compiler.hasErrors();
						if (!saved) {
							for (String error : compiler.getErrors()) {
								rtcLogger.severe(error);
							}
						}
					}
				} catch (Exception e) {
					rtcLogger.log(Level.SEVERE, e, () -> String.format("%1$s caught compiling ribose compiler directory '%2$s'.",
						e.getClass().getSimpleName(), inrAutomataDirectory.getPath()));
				}
				return saved;
			}

			File compilerModelFile =  proxyCompiler.lookupCompilerModel();
			if (compilerModelFile == null || !compilerModelFile.exists()) {
				rtcLogger.log(Level.SEVERE, "TCompile.model not found in ribose jar.");
				return false;
			}

			boolean saved = false;
			try (
				IModel compilerRuntime = IModel.loadRiboseModel(compilerModelFile);
				ModelCompiler compiler = new ModelCompiler(riboseModelFile, targetClass, TargetMode.LIVE_TARGET);
			) {
				compiler.setTransductor(compilerRuntime.transductor(compiler));
				for (final String filename : inrAutomataDirectory.list()) {
					if (filename.endsWith(Base.AUTOMATON_FILE_SUFFIX)) {
						compiler.compileTransducer(new File(inrAutomataDirectory, filename));
					}
				}
				Argument[][] compiledParameters = compiler.compileModelParameters(compiler.errors);
				saved = compiler.validate() && compiler.save(compiledParameters);
				assert saved || compiler.deleteOnClose;
				assert saved == !compiler.hasErrors();
				if (!saved) {
					for (String error : compiler.getErrors()) {
						rtcLogger.severe(error);
					}
				}
			} catch (Exception e) {
				rtcLogger.log(Level.SEVERE, e, () -> String.format("%1$s caught compiling automata directory '%2$s'.",
					e.getClass().getSimpleName(), inrAutomataDirectory.getPath()));
			} finally {
				assert saved || !riboseModelFile.exists();
			}
			return saved;
		}
	}

	private boolean compileCompiler(File inrAutomataDirectory) {
		Automaton automaton = new Automaton(this, super.rtcLogger);

		return automaton.assemble(inrAutomataDirectory);
	}

	private File lookupCompilerModel() {
		File compilerModelFile = null;
		String compilerModelPath = ModelCompiler.class.getPackageName().replace('.', '/') + "/TCompile.model";
		if (ModelCompiler.class.getClassLoader().getResource(compilerModelPath) != null) {
			try {
				compilerModelFile = File.createTempFile("TCompile", ".model");
				compilerModelFile.deleteOnExit();
				try (
					InputStream mis = ModelCompiler.class.getClassLoader().getResourceAsStream(compilerModelPath);
					OutputStream mos = new FileOutputStream(compilerModelFile)
				) {
					byte[] data = new byte[4096];
					int read = -1;
					do {
						read = mis.read(data);
						if (read > 0) {
							mos.write(data, 0, read);
						}
					} while (read >= 0);
				}
			} catch (IOException e) {
				compilerModelFile = null;
			}
		}
		return compilerModelFile;
	}

	ModelCompiler reset(File inrFile) throws CharacterCodingException {
		String name = inrFile.getName();
		name = name.substring(0, name.length() - Base.AUTOMATON_FILE_SUFFIX.length());
		this.transducerName = Codec.encode(name);
		this.transducerOrdinal = super.addTransducer(this.transducerName);
		this.inputStateMap = new HashMap<>(256);
		this.stateTransitionMap = new HashMap<>(1024);
		this.effectorVectorMap = null;
		this.inputEquivalenceIndex = null;
		this.kernelMatrix = null;
		this.header = null;
		this.transitions = null;
		this.transition = 0;
		return this;
	}

	private boolean createModelFile(File riboseModelFile, Logger rtcLogger) {
		try {
			if (riboseModelFile.createNewFile()) {
				return true;
			} else {
				rtcLogger.log(Level.SEVERE, () -> String.format("Can't overwrite existing model file : %1$s",
					riboseModelFile.getPath()));
			}
		} catch (IOException e) {
			rtcLogger.log(Level.SEVERE, e, () -> String.format("Exception caught creating model file : %1$s",
				riboseModelFile.getPath()));
		}		
		return false;
	}

	private boolean compileTransducer(File inrFile) {
		int size = (int)inrFile.length();
		byte[] bytes = null;
		try (DataInputStream f = new DataInputStream(new FileInputStream(inrFile))) {
			this.reset(inrFile);
			int position = 0, length = size;
			bytes = new byte[length];
			while (length > 0) {
				int read = f.read(bytes, position, length);
				position += read;
				length -= read;
			}
			assert position == size;
		} catch (FileNotFoundException e) {
			this.addError(String.format("%1$s: File not found '%2$s'",
				this.transducerName, inrFile.getPath()));
			return false;
		} catch (IOException e) {
			this.addError(String.format("%1$s: IOException compiling '%2$s'; %3$s",
				this.transducerName, inrFile.getPath(), e.getMessage()));
			return false;
		}
		try (ITransduction transduction = super.transduction(this.transductor)) {
			transduction.reset();
			Bytes automaton = Codec.encode("Automaton");
			if (this.transductor.signal(Signal.NIL).push(bytes, size).start(automaton).status().isRunnable()
			&& this.transductor.run().status().isPaused()) {
				this.transductor.signal(Signal.EOS).run();
			}
			assert !this.transductor.status().isRunnable();
			this.saveTransducer();
		} catch (Exception e) {
			this.addError(String.format("%1$s: Failed to compile '%2$s'; %3$s",
				this.transducerName, inrFile.getPath(), e.getMessage()));
		}
		assert this.transductor.status().isStopped();
		return true;
	}

	private boolean validate() {
		for (Token token : this.tapeTokens.get(0)) {
			if (token.getType() != Type.LITERAL && token.getType() != Type.SIGNAL) {
				this.addError(String.format("Error: Invalid %2$s token '%1$s' on tape 0",
					token.asString(), token.getTypeName()));
			} else if (token.getSymbol().bytes().length > 1
				&& !this.tapeTokens.get(2).contains(token)) {
				this.addError(String.format("Error: Unrecognized signal reference '%1$s' on tape 0",
					token.asString()));
			}
		}
		for (Token token : this.tapeTokens.get(1)) {
			if (token.getType() != Type.LITERAL) {
				this.addError(String.format("Error: Invalid %2$s token '%1$s' on tape 1",
					token.asString(), token.getTypeName()));
			} else if (this.getEffectorOrdinal(token.getSymbol()) < 0) {
				this.addError(String.format("Error: Unrecognized effector token '%1$s' on tape 1",
					token.asString()));
			}
		}
		for (Token token : this.tapeTokens.get(2)) {
			if (token.getType() == Type.TRANSDUCER
				&& super.getTransducerOrdinal(token.getSymbol()) < 0) {
				this.addError(String.format("Error: Unrecognized transducer token '%1$s' on tape 1",
					token.asString()));
			} else if (token.getType() == Type.SIGNAL
				&& super.getSignalOrdinal(token.getSymbol()) > Signal.EOS.signal()
				&& !this.tapeTokens.get(0).contains(token)) {
				this.addError(String.format("Error: Signal token '%1$s' on tape 2 is never referenced on tape 0",
					token.asString()));
			}
		}
		for (Entry<Bytes, Integer> e : super.transducerOrdinalMap.entrySet()) {
			int ordinal = e.getValue();
			if (super.transducerNameIndex[ordinal] == null
			|| !super.transducerNameIndex[ordinal].equals(e.getKey())
			|| super.transducerOffsetIndex[ordinal] <= 0) {
				this.addError(String.format("'%1$s': referenced but not compiled in model",
					e.getKey().asString()));
			}
		}
		
		if (super.transducerOrdinalMap.isEmpty()) {
			this.addError("Error: The model is empty");
		}
		return !this.hasErrors();
	}

	void saveTransducer() throws ModelException, CharacterCodingException {
		HashMap<Integer, Integer> localFieldMap = this.transducerFieldMaps.get(this.transducerOrdinal);
		int[] fields = new int[localFieldMap.size()];
		for (Entry<Integer, Integer> e : localFieldMap.entrySet()) {
			fields[e.getValue()] = e.getKey();
		}
		super.writeTransducer(this.transducerName, this.transducerOrdinal, fields, this.inputEquivalenceIndex, this.kernelMatrix, this.effectorVectors);
		int nInputs = this.kernelMatrix.length;
		int nStates = this.kernelMatrix[0].length;
		int nTransitions = 0;
		for (int input = 0; input < nInputs; input++) 
			for (int state = 0; state < nStates; state++) 
				if (this.kernelMatrix[input][state][1] != 0) 
					nTransitions++;
		final int transitionCount = nTransitions;
		final int fieldCount = super.getFieldCount(this.transducerOrdinal);
		double sparsity = 100 * (1 - ((double)nTransitions / (double)(nStates * nInputs)));
		String info = String.format(
			"%1$21s %2$5d input classes %3$5d states %4$5d transitions; %5$5d fields; (%6$.0f%% nul)",
			this.getTransducerName()+":", nInputs, nStates, transitionCount, fieldCount, sparsity);
		super.rtcLogger.log(Level.INFO, () -> info);
		System.out.println(info);
	}

	private String getTransducerName() {
		return this.transducerName.asString();
	}

	void putHeader(Header header) {
		if (header.version != ModelCompiler.VERSION) {
			this.addError(String.format("%1$s: Invalid INR version %2$d",
				getTransducerName(), header.version));
		}
		if (header.tapes > 3) {
			this.addError(String.format("%1$s: Invalid tape count %2$d",
				getTransducerName(), header.tapes));
		}
		this.header = header;
		this.transitions = new Transition[header.transitions];
		stateTransitionMap = new HashMap<>((header.states * 5) >> 2);
	}

	void putTransition(Transition transition) {
		assert this.header.transitions == this.transitions.length;
		if (!transition.isFinal) {
			if (transition.tape < 0) {
				this.addError(String.format("%1$s: Epsilon transition from state %2$d to %3$d (use :dfamin to remove these)",
					this.getTransducerName(), transition.from, transition.to));
			} else if (transition.symbol.getLength() == 0) {
				this.addError(String.format("%1$s: Empty symbol on tape %2$d",
					this.getTransducerName(), transition.tape));
			} else {
				this.transitions[this.transition++] = transition;
				if (transition.tape == 0) {
					this.inputStateMap.putIfAbsent(transition.from, this.inputStateMap.size());
				}
				if (transition.tape == 1 || transition.symbol.getLength() > 1) {
					Token token = new Token(transition.symbol.bytes(), -1, transducerOrdinal);
					Bytes symbol = token.getSymbol();
					Type type = token.getType();
					if (transition.tape == 0 && type == Type.LITERAL && transition.symbol.getLength() > 1) {
						super.addSignal(symbol);
						this.tapeTokens.get(0).add(new Token(token.getReference(Type.SIGNAL, symbol.bytes())));
					} else if (transition.tape == 1) {
						this.tapeTokens.get(1).add(token);
					} else if (transition.tape == 2 && type != Type.LITERAL) {
						if (type == Type.FIELD) {
							token.setOrdinal(super.addLocalField(this.transducerOrdinal, super.addField(symbol)));
						} else if (type == Type.TRANSDUCER) {
							token.setOrdinal(super.addTransducer(symbol));
						} else if (type == Type.SIGNAL) {
							token.setOrdinal(super.addSignal(symbol));
						}
						this.tapeTokens.get(2).add(token);
					}
				}
				ArrayList<Transition> outgoing = this.stateTransitionMap.get(transition.from);
				if (outgoing == null) {
					outgoing = new ArrayList<>(16);
					this.stateTransitionMap.put(transition.from, outgoing);
				}
				outgoing.add(transition);
			}
		}
	}

	void putAutomaton() throws CharacterCodingException {
		final Integer[] inrInputStates = this.getInrStates();
		if (inrInputStates == null) {
			this.addError("Empty automaton " + this.getTransducerName());
			return;
		}

		for (final ArrayList<Transition> transitionList : this.stateTransitionMap.values()) {
			transitionList.trimToSize();
		}

		final int[][][] transitionMatrix = new int[super.getSignalLimit()][inrInputStates.length][2];
		for (int i = 0; i < transitionMatrix.length; i++) {
			for (int j = 0; j < inrInputStates.length; j++) {
				transitionMatrix[i][j][0] = j;
				transitionMatrix[i][j][1] = 0;
			}
		}

		this.effectorVectorMap = new HashMap<>(1024);
		this.effectorVectorMap.put(new Ints(new int[] { 0 }), 0);
		for (final Integer inrInputState : inrInputStates) {
			for (final Transition t : this.getTransitions(inrInputState)) {
				if (t.tape == 0 && !t.isFinal) {
					try {
						final int rteState = this.inputStateMap.get(t.from);
						final int inputOrdinal = super.getInputOrdinal(t.symbol.bytes());
						final Chain chain = this.chain(t);
						if (chain != null) {
							final int[] effectVector = chain.getEffectVector();
							transitionMatrix[inputOrdinal][rteState][0] = this.inputStateMap.get(chain.getOutS());
							if (chain.isEmpty()) {
								transitionMatrix[inputOrdinal][rteState][1] = 1;
							} else if (chain.isEffector()) {
								transitionMatrix[inputOrdinal][rteState][1] = effectVector[0];
							} else if (chain.isParameterizedEffector()) {
								transitionMatrix[inputOrdinal][rteState][1] = Transducer.action(-1 * effectVector[0], effectVector[1]);
							} else {
								Integer vectorOrdinal = this.effectorVectorMap.computeIfAbsent(
									new Ints(effectVector), absent -> this.effectorVectorMap.size());
								transitionMatrix[inputOrdinal][rteState][1] = -1 * vectorOrdinal;
							}
						}
					} catch (CompilationException e) {
						this.addError(String.format("%1$s: %2$s",
							this.getTransducerName(), e.getMessage()));
					}
				} else if (t.tape != 0) {
					this.addError(String.format(ModelCompiler.AMBIGUOUS_STATE_MESSAGE,
						this.getTransducerName(), t.from));
				}
			}
		}

		this.factor(transitionMatrix);
	}

	private boolean hasErrors() {
		return !this.errors.isEmpty();
	}

	void addError(final String message) {
		if (!this.errors.contains(message)) {
			this.errors.add(message);
		}
	}

	private List<String> getErrors() {
		return this.errors;
	}

	private void factor(final int[][][] transitionMatrix) throws CharacterCodingException {
		// factor matrix modulo input equivalence
		final HashMap<IntsArray, HashSet<Integer>> rowEquivalenceMap =
			new HashMap<>((5 * transitionMatrix.length) >> 2);
		for (int token = 0; token < transitionMatrix.length; token++) {
			assert transitionMatrix[token].length == transitionMatrix[0].length;
			final IntsArray row = new IntsArray(transitionMatrix[token]);
			HashSet<Integer> equivalentInputOrdinals = rowEquivalenceMap.get(row);
			if (equivalentInputOrdinals == null) {
				equivalentInputOrdinals = new HashSet<>(16);
				rowEquivalenceMap.put(row, equivalentInputOrdinals);
			}
			equivalentInputOrdinals.add(token);
		}
		// construct kernel matrix from input equivalents
		this.inputEquivalenceIndex = new int[transitionMatrix.length];
		this.kernelMatrix = new int[rowEquivalenceMap.size()][][];
		int equivalenceIndex = 0;
		for (final Map.Entry<IntsArray, HashSet<Integer>> entry : rowEquivalenceMap.entrySet()) {
			final IntsArray row = entry.getKey();
			for (final int inputOrdinal : entry.getValue()) {
				this.inputEquivalenceIndex[inputOrdinal] = equivalenceIndex;
			}
			this.kernelMatrix[equivalenceIndex++] = row.getInts();
		}
		// instrument sum and product traps, compress kernel matrix and extract effect vectors
		final int nInputs = equivalenceIndex;
		final int nStates = transitionMatrix[0].length;
		final int nulSignal = Signal.NUL.signal();
		final int nulEquivalent = this.inputEquivalenceIndex[nulSignal];
		final int msumOrdinal = super.getEffectorOrdinal(Codec.encode("msum"));
		final int mproductOrdinal = super.getEffectorOrdinal(Codec.encode("mproduct"));
		final int mscanOrdinal = super.getEffectorOrdinal(Codec.encode("mscan"));
		final int[][] msumStateEffects = new int[nStates][];
		final int[][] mproductStateEffects = new int[nStates][];
		final int[][] mproductEndpoints = new int[nStates][2];
		final int[][] mscanStateEffects = new int[nStates][];
		Arrays.fill(msumStateEffects, null);
		Arrays.fill(mproductStateEffects, null);
		Arrays.fill(mscanStateEffects, null);
	// msum instrumentation
		byte[][] equivalentInputs = new byte[nInputs][transitionMatrix.length];
		int[] equivalenceLengths = new int[nInputs];
		Arrays.fill(equivalenceLengths, 0);
		for (int token = 0; token < nulSignal; token++) {
			int input = this.inputEquivalenceIndex[token];
			equivalentInputs[input][equivalenceLengths[input]++] = (byte)token;
		}
		for (int state = 0; state < nStates; state++) {
			int selfIndex = 0, selfCount = 0;
			byte[] selfBytes = new byte[nulSignal];
			Arrays.fill(selfBytes, (byte)0);
			boolean[] allBytes = new boolean[nulSignal];
			Arrays.fill(allBytes, false);
			for (int input = 0; input < nInputs; input++) {
				int[] cell = this.kernelMatrix[input][state];
				if (cell[0] == state && cell[1] == 1) {
					for (int index = 0; index < equivalenceLengths[input]; index++) {
						selfBytes[selfIndex] = equivalentInputs[input][index];
						allBytes[Byte.toUnsignedInt(selfBytes[selfIndex])] = true;
						selfIndex++;
					}
					selfCount += equivalenceLengths[input];
				}
			}
			if (selfCount >= 255) {
				assert mscanStateEffects[state] == null;
				for (int token = 0; token < nulSignal; token++) {
					if (!allBytes[token]) {
						Argument argument = new Argument(-1, new BytesArray(new byte[][] { { (byte) token } }));
						int mscanParameterIndex = super.compileParameters(mscanOrdinal, argument);
						mscanStateEffects[state] = new int[] { -1 * mscanOrdinal, mscanParameterIndex, 0 };
						break;
					}
				}
			} else if (selfCount > ModelCompiler.MIN_SUM_SIZE) {
				assert msumStateEffects[state] == null;
				Argument argument = new Argument(-1, new BytesArray(new byte[][] { Arrays.copyOf(selfBytes, selfCount) }));
				int msumParameterIndex = super.compileParameters(msumOrdinal, argument);
				msumStateEffects[state] = new int[] { -1 * msumOrdinal, msumParameterIndex, 0 };
			}
		}
		// mproduct instrumentation
		int[] inputEquivalentCardinality = new int[nInputs];
		int[] inputEquivalenceToken = new int[nInputs];
		Arrays.fill(inputEquivalenceToken, -1);
		for (int token = 0; token < this.inputEquivalenceIndex.length; token++) {
			int equivalent = this.inputEquivalenceIndex[token];
			if (++inputEquivalentCardinality[equivalent] == 1
			&& inputEquivalenceToken[equivalent] == -1
			&& token < nulSignal) {
				inputEquivalenceToken[equivalent] = token;
			} else {
				inputEquivalenceToken[equivalent] = -2;
			}
			assert (token >= nulSignal)
			|| (inputEquivalentCardinality[equivalent] == 1) == (inputEquivalenceToken[equivalent] == token);
			assert (token < nulSignal) || (inputEquivalenceToken[equivalent] < 0);
		}
		int[] exitEquivalent = new int[nStates];
		for (int state = 0; state < nStates; state++) {
			exitEquivalent[state] = -1;
			for (int input = 0; input < nInputs; input++) {
				if (input != nulEquivalent) {
					int[] cell = this.kernelMatrix[input][state];
					if (cell[0] != state) {
						if (cell[1] == 1 && exitEquivalent[state] < 0
						&& inputEquivalenceToken[input] >= 0) {
							assert exitEquivalent[state] == -1;
							exitEquivalent[state] = input;
						} else {
							exitEquivalent[state] = -1;
							break;
						}
					} else if (cell[1] != 0) {
							exitEquivalent[state] = -1;
							break;
					}
				}
			}
			assert (exitEquivalent[state] < 0)
			|| (this.kernelMatrix[exitEquivalent[state]][state][0] != state
				&& this.kernelMatrix[exitEquivalent[state]][state][1] == 1);
		}
		assertKernelSanity();
		boolean[] walkedStates = new boolean[nStates];
		Arrays.fill(walkedStates, false);
		StateStack walkStack = new StateStack(nStates);
		byte[] walkedBytes = new byte[nStates];
		int[] walkResult = new int[] { 0, 0, 0 };
		walkedStates[0] = true;
		walkStack.push(0);
		while (walkStack.size() > 0) {
			int fromState = walkStack.pop();
			for (int input = 0; input < nInputs; input++) {
				int toState = this.kernelMatrix[input][fromState][0];
				if (exitEquivalent[toState] >= 0) {
					assert inputEquivalenceToken[exitEquivalent[toState]] >= 0;
					assert inputEquivalenceToken[exitEquivalent[toState]] < nulSignal;
					int nextState = this.walk(toState, walkedBytes, walkResult, exitEquivalent, inputEquivalenceToken);
					if (walkResult[0] > ModelCompiler.MIN_PRODUCT_LENGTH) {
						assert this.inputEquivalenceIndex[walkedBytes[0]] == exitEquivalent[toState];
						if (mproductStateEffects[toState] == null) {
							byte[][] product = new byte[][] { Arrays.copyOfRange(walkedBytes, 0, walkResult[0]) };
							int effects = super.compileParameters(mproductOrdinal, new Argument(-1, new BytesArray(product)));
							mproductStateEffects[toState] = new int[] { -1 * mproductOrdinal, effects, 0 };
							mproductEndpoints[toState][0] = nextState;
							mproductEndpoints[toState][1] = walkResult[2];
						} else {
							assert mproductEndpoints[toState][0] == nextState;
							assert mproductEndpoints[toState][1] == walkResult[2];
						}
						assert this.inputEquivalenceIndex[walkedBytes[walkResult[0] - 1]] == walkResult[2];
						toState = nextState;
					}
					if (walkResult[0] > 0) {
						int state = this.kernelMatrix[input][fromState][0];
						for (int i = 0; i < walkResult[0] - 1; i++) {
							assert exitEquivalent[state] >= 0;
							assert walkedBytes[i] == inputEquivalenceToken[exitEquivalent[state]];
							assert this.inputEquivalenceIndex[walkedBytes[i]] == exitEquivalent[state];
							int[] cell = this.kernelMatrix[exitEquivalent[state]][state];
							assert cell[1] == 1;
							state = cell[0];
						}
						assert exitEquivalent[state] == walkResult[2];
						assert this.inputEquivalenceIndex[walkedBytes[walkResult[0] - 1]] == exitEquivalent[state];
						assert this.kernelMatrix[exitEquivalent[state]][state][1] == 1;
						assert nextState == this.kernelMatrix[exitEquivalent[state]][state][0];
					}
				}
				if (!walkedStates[toState]) {
					walkedStates[toState] = true;
					walkStack.push(toState);
				}
			}
		}
		// effect vector construction
		assertKernelSanity();
		int vectorCount = this.effectorVectorMap.size();
		int[][] effectVectors = new int[vectorCount > 4 ? (vectorCount * 3) >> 1 : 5][];
		for (Entry<Ints, Integer> entry : this.effectorVectorMap.entrySet())  {
			int[] vector = entry.getKey().getData();
			effectVectors[entry.getValue()] = vector;
		}
		effectVectors = instrumentSumVectors(msumOrdinal, effectVectors, msumStateEffects);
		assertKernelSanity();
		effectVectors = instrumentSumVectors(mscanOrdinal, effectVectors, mscanStateEffects);
		assertKernelSanity();
		effectVectors = instrumentProductVectors(mproductOrdinal, effectVectors, mproductStateEffects, mproductEndpoints);
		assertKernelSanity();
		int[] effectorVectorPosition = new int[this.effectorVectorMap.size()];
		Arrays.fill(effectorVectorPosition, -1);
		int position = 1; int size = 0;
		for (int input = 0; input < nInputs; input++) {
			for (int state = 0; state < nStates; state++) {
				int effectOrdinal = this.kernelMatrix[input][state][1];
				if (effectOrdinal < 0) {
					size += effectVectors[-1 * effectOrdinal].length;
				}
			}
		}
		this.effectorVectors = new int[size + 1];
		this.effectorVectors[0] = 0;
		for (int input = 0; input < nInputs; input++) {
			for (int state = 0; state < nStates; state++) {
				int[] cell = this.kernelMatrix[input][state];
				int effectOrdinal = cell[1];
				if (effectOrdinal < 0) {
					effectOrdinal *= -1;
					if (effectorVectorPosition[effectOrdinal] < 0) {
						System.arraycopy(effectVectors[effectOrdinal], 0, this.effectorVectors, position, effectVectors[effectOrdinal].length);
						effectorVectorPosition[effectOrdinal] = position;
						position += effectVectors[effectOrdinal].length;
					}
					cell[1] = -1 * effectorVectorPosition[effectOrdinal];
				}
			}
		}
		if (position < this.effectorVectors.length) {
			this.effectorVectors = Arrays.copyOf(this.effectorVectors, position);
		}
		assertKernelSanity();
		// redundant state elimination
		for (int state = 0; state < nStates; state++) {
			assert (mproductEndpoints[state] != null) || (mproductStateEffects[state] == null);
			assert (exitEquivalent[state] >= 0) || (mproductStateEffects[state] == null);
			if (mproductStateEffects[state] != null
			&& mproductEndpoints[state][1] != exitEquivalent[state]) {
				int[] cell = this.kernelMatrix[exitEquivalent[state]][state];
				if (cell[1] > 0) {
					cell[0] = state;
					cell[1] = 0;
				}
			}
		}
		assertKernelSanity();
		int[] stateMap = new int[nStates];
		Arrays.fill(stateMap, -1);
		Arrays.fill(walkedStates, false);
		assert walkStack.size() == 0;
		int mStates = 0;
		walkedStates[0] = true;
		walkStack.push(0);
		while (walkStack.size() > 0) {
			int state = walkStack.pop();
			stateMap[state] = mStates++;
			for (int input = 0; input < nInputs; input++) {
				int nextState = this.kernelMatrix[input][state][0];
				if (nextState != state && !walkedStates[nextState]) {
					walkedStates[nextState] = true;
					walkStack.push(nextState);
				}
			}
		}
		int[][][] finalMatrix = new int[rowEquivalenceMap.size()][mStates][2];
		for (int input = 0; input < nInputs; input++) {
			int[][] row = this.kernelMatrix[input];
			for (int state = 0; state < nStates; state++) {
				if (walkedStates[state]) {
					int[] cell = finalMatrix[input][stateMap[state]];
					cell[0] = stateMap[row[state][0]];
					cell[1] = row[state][1];
				}
			}
		}
		this.kernelMatrix = finalMatrix;
		assertKernelSanity();
		// Coalesce equivalence classes
		rowEquivalenceMap.clear();
		for (int input = 0; input < this.kernelMatrix.length; input++) {
			assert this.kernelMatrix[input].length == this.kernelMatrix[0].length;
			final IntsArray row = new IntsArray(this.kernelMatrix[input]);
			HashSet<Integer> equivalentClassOrdinals = rowEquivalenceMap.computeIfAbsent(
				row, absent -> new HashSet<>(16));
			if (equivalentClassOrdinals.isEmpty()) {
				rowEquivalenceMap.put(row, equivalentClassOrdinals);
			}
			equivalentClassOrdinals.add(input);
		}
		int[] classEquivalenceIndex = new int[this.kernelMatrix.length];
		int[][][] matrix = new int[rowEquivalenceMap.size()][][];
		int equivalenceClassIndex = 0;
		for (final Map.Entry<IntsArray, HashSet<Integer>> entry : rowEquivalenceMap.entrySet()) {
			final IntsArray row = entry.getKey();
			for (final int inputOrdinal : entry.getValue()) {
				classEquivalenceIndex[inputOrdinal] = equivalenceClassIndex;
			}
			matrix[equivalenceClassIndex++] = row.getInts();
		}
		for (int token = 0; token < this.inputEquivalenceIndex.length; token++) {
			this.inputEquivalenceIndex[token] = classEquivalenceIndex[this.inputEquivalenceIndex[token]];
		}
		this.kernelMatrix = matrix;
	}

	private void assertKernelSanity() {
		for (int input = 0; input < this.kernelMatrix.length; input++) {
			assert this.kernelMatrix[0].length == this.kernelMatrix[input].length;
			for (int state = 0; state < this.kernelMatrix[input].length; state++) {
				assert (this.kernelMatrix[input][state][1] != 0)
				|| (this.kernelMatrix[input][state][0] == state)
				: String.format("sanity: state[%d->%d] input[%d->%d]", 
					state, this.kernelMatrix[input][state][0], 
					input, this.kernelMatrix[input][state][1]);
			}
		}
	}

	private int walk(int fromState, byte[] walkedBytes, int[] walkResult, int[] exitEquivalent, int[] singletonEquivalenceMap) {
		int nulEquivalent = this.inputEquivalenceIndex[Signal.NUL.signal()];
		int[] nulTransition = this.kernelMatrix[nulEquivalent][fromState];
		int[] matchTransition = new int[] { 
			nulTransition[0] != fromState ? nulTransition[0] : Integer.MIN_VALUE, 
			nulTransition[1] 
		};
		int walkLength = 0;
		int walkedInput = -1;
		int walkState = fromState;
		Arrays.fill(walkResult, 0);
		assert exitEquivalent[walkState] >= 0;
		int exitInput = exitEquivalent[walkState];
		while (exitInput >= 0 && walkLength < walkedBytes.length
		&& this.kernelMatrix[exitInput][walkState][1] == 1) {
			assert singletonEquivalenceMap[exitInput] >= 0
			&& singletonEquivalenceMap[exitInput] < Signal.NUL.signal();
			int[] errorTransition = this.kernelMatrix[nulEquivalent][walkState];
			int errorState = matchTransition[0] != Integer.MIN_VALUE ? matchTransition[0] : walkState;
			if (errorTransition[1] == matchTransition[1] && errorTransition[0] == errorState) {
				walkedInput = exitInput;
				walkedBytes[walkLength] = (byte)singletonEquivalenceMap[walkedInput];
				walkState = this.kernelMatrix[walkedInput][walkState][0];
				exitInput = exitEquivalent[walkState];
				++walkLength;
			} else {
				break;
			}
		}
		if (walkLength > 0) {
			walkResult[0] = walkLength;
			walkResult[1] = walkState;
			walkResult[2] = walkedInput;
			return walkState;
		}
		return fromState;
	}

	private int[][] instrumentSumVectors(int msumOrdinal, int[][] effectVectors, int[][] msumEffects) {
		int nInputs = this.kernelMatrix.length;
		int nStates = this.kernelMatrix[0].length;
		for (int input = 0; input < nInputs; input++) {
			for (int state = 0; state < nStates; state++) {
				int[] cell = this.kernelMatrix[input][state];
				if ((cell[0] == state) || (cell[1] == 0) || (msumEffects[cell[0]] == null)) {
					continue;
				}
				int vectorLength = msumEffects[cell[0]].length;
				int vectorOrdinal = cell[1];
				if ((vectorOrdinal > 0)
				|| (effectVectors[-1 * vectorOrdinal][effectVectors[-1 * vectorOrdinal].length - vectorLength] != msumOrdinal)) {
					int[] effect = msumEffects[cell[0]];
					int[] vector = vectorOrdinal > 0
					? (vectorOrdinal > 1 ? new int[] { vectorOrdinal, 0 } : new int[] { vectorOrdinal })
					: effectVectors[-1 * vectorOrdinal];
					int[] vectorex = Arrays.copyOf(vector, vector.length + effect.length - 1);
					System.arraycopy(effect, 0, vectorex, vector.length - 1, effect.length);
					Ints vxkey = new Ints(vectorex);
					if (this.effectorVectorMap.containsKey(vxkey)) {
						vectorOrdinal = this.effectorVectorMap.get(vxkey);
					} else {
						vectorOrdinal = this.effectorVectorMap.size();
						this.effectorVectorMap.put(vxkey, vectorOrdinal);
						if (vectorOrdinal >= effectVectors.length) {
							int[][] newv = new int[vectorOrdinal > 4 ? (vectorOrdinal * 3) >> 1 : 5][];
							System.arraycopy(effectVectors, 0, newv, 0, effectVectors.length);
							effectVectors = newv;
						}
						effectVectors[vectorOrdinal] = vectorex;
					}
					cell[1] = -1 * vectorOrdinal;
				}
			}
		}
		return effectVectors;
	}

	private int[][] instrumentProductVectors(int mproductOrdinal, int[][] effectVectors, int[][] mproductEffects, int[][] mproductEndpoints) {
		int nInputs = this.kernelMatrix.length;
		int nStates = this.kernelMatrix[0].length;
		for (int input = 0; input < nInputs; input++) {
			for (int state = 0; state < nStates; state++) {
				int[] cell = this.kernelMatrix[input][state];
				if ((cell[1] == 0) || (mproductEffects[cell[0]] == null)) {
					continue;
				}
				int startState = cell[0];
				int vectorLength = mproductEffects[startState].length;
				int vectorOrdinal = cell[1];
				if ((vectorOrdinal > 0)
				|| (effectVectors[-1 * vectorOrdinal][effectVectors[-1 * vectorOrdinal].length - vectorLength] != mproductOrdinal)) {
					int endState = mproductEndpoints[startState][0];
					int endInput = mproductEndpoints[startState][1];
					this.kernelMatrix[endInput][startState][0] = endState;
					this.kernelMatrix[endInput][startState][1] = 1;
					int[] vector = vectorOrdinal > 0
					? (vectorOrdinal > 1 ? new int[] { vectorOrdinal, 0 } : new int[] { vectorOrdinal })
					: effectVectors[-1 * vectorOrdinal];
					int[] mproductEffect = mproductEffects[startState];
					int[] vectorex = Arrays.copyOf(vector, vector.length + mproductEffect.length - 1);
					System.arraycopy(mproductEffect, 0, vectorex, vector.length - 1, mproductEffect.length);
					Ints vxkey = new Ints(vectorex);
					if (this.effectorVectorMap.containsKey(vxkey)) {
						vectorOrdinal = this.effectorVectorMap.get(vxkey);
					} else {
						vectorOrdinal = this.effectorVectorMap.size();
						this.effectorVectorMap.put(vxkey, vectorOrdinal);
						if (vectorOrdinal >= effectVectors.length) {
							int[][] newv = new int[vectorOrdinal > 4 ? (vectorOrdinal * 3) >> 1 : 5][];
							System.arraycopy(effectVectors, 0, newv, 0, effectVectors.length);
							effectVectors = newv;
						}
						effectVectors[vectorOrdinal] = vectorex;
					}
					cell[1] = -1 * vectorOrdinal;
					assert vectorOrdinal > 0;
				}
			}
		}
		return effectVectors;
	}

	private Chain chain(final Transition transition) {
		assert transition.tape != 1 && transition.tape != 2
		: "Invalid tape number for chain(InrTransition) : " + transition.toString();
		if (transition.isFinal) {
			return null;
		}
		boolean fail = false;
		int effectorOrdinal = -1;
		int effectorPos = 0;
		int[] effectorVector = new int[8];
		int parameterPos = 0;
		byte[][] parameterList = new byte[8][];
		ArrayList<Transition> outT = this.getTransitions(transition.to);
		while (outT != null && outT.size() == 1 && outT.get(0).tape > 0) {
			final Transition t = outT.get(0);
			switch (t.tape) {
				case 1:
					if ((effectorPos + 2) >= effectorVector.length) {
						int newLength = effectorVector.length > 4 ? (effectorVector.length * 3) >> 1 : 5;
						effectorVector = Arrays.copyOf(effectorVector, newLength);
					}
					if (effectorOrdinal >= 0 && parameterPos > 0) {
						assert((effectorPos > 0) && (effectorOrdinal == effectorVector[effectorPos - 1]));
						effectorVector[effectorPos - 1] *= -1;
						final Argument argument = new Argument(this.transducerOrdinal, new BytesArray(Arrays.copyOf(parameterList, parameterPos)));
						int parameterOrdinal = super.compileParameters(effectorOrdinal, argument);
						effectorVector[effectorPos] = parameterOrdinal;
						parameterList = new byte[8][];
						++effectorPos;
					}
					Bytes effectorSymbol = t.symbol;
					effectorOrdinal = super.getEffectorOrdinal(effectorSymbol);
					if (effectorOrdinal >= 0) {
						effectorVector[effectorPos] = effectorOrdinal;
						++effectorPos;
					} else {
						fail = true;
					}
					parameterPos = 0;
					break;
				case 2:
					if (effectorOrdinal >= 0) {
						if (parameterPos >= parameterList.length) {
							int newLength = parameterList.length > 4 ? (parameterList.length * 3) >> 1 : 5;
							parameterList = Arrays.copyOf(parameterList, newLength);
						}
						parameterList[parameterPos] = t.symbol.bytes();
						++parameterPos;
					}
					break;
				default:
					this.addError(String.format("%1$s: Invalid tape number %2$d",
						this.getTransducerName(), t.tape));
					fail = true;
					break;
			}
			outT = this.getTransitions(t.to);
		}
		if (!fail) {
			assert effectorPos > 0 || parameterPos == 0;
			assert parameterPos == 0 || effectorPos > 0;
			int vectorLength = effectorPos + 1;
			if (effectorPos > 0 && parameterPos > 0) {
				++vectorLength;
			}
			if (vectorLength != effectorVector.length) {
				effectorVector = Arrays.copyOf(effectorVector, vectorLength);
			}
			if (parameterPos > 0) {
				assert((effectorPos > 0) && (effectorOrdinal == effectorVector[effectorPos - 1]));
				final BytesArray parameters = new BytesArray(Arrays.copyOf(parameterList, parameterPos));
				final Argument argument = new Argument(this.transducerOrdinal, parameters);
				int parameterOrdinal = super.compileParameters(effectorOrdinal, argument);
				effectorVector[effectorPos] = parameterOrdinal;
				effectorVector[effectorPos - 1] *= -1;
				++effectorPos;
			}
			if (effectorPos > 0) {
				effectorVector[effectorPos] = 0;
				++effectorPos;
			}
			if (outT == null || outT.isEmpty() || outT.size() == 1 && outT.get(0).isFinal) {
				return new Chain(Arrays.copyOf(effectorVector, effectorPos), 0);
			} else if (outT.size() == 1 && outT.get(0).tape == 0) {
				return new Chain(Arrays.copyOf(effectorVector, effectorPos), outT.get(0).from);
			} else if (outT.get(0).isFinal || outT.get(0).tape == 0) {
				int outS = -1;
				for (final Transition t : outT) {
					if (t.tape > 0) {
						this.addError(String.format(AMBIGUOUS_STATE_MESSAGE, this.getTransducerName(), t.from));
						fail = true;
					} else {
						outS = t.from;
					}
				}
				if (!fail) {
					return new Chain(Arrays.copyOf(effectorVector, effectorPos), outS);
				}
			} else {
				for (final Transition t : outT) {
					this.addError(String.format(AMBIGUOUS_STATE_MESSAGE, this.getTransducerName(), t.from));
				}
			}
		}
		return null;
	}

	private Integer[] getInrStates() {
		Integer[] inrStates = new Integer[this.inputStateMap.size()];
		inrStates = this.inputStateMap.keySet().toArray(inrStates);
		Arrays.sort(inrStates);
		return inrStates;
	}

	private ArrayList<Transition> getTransitions(final int inrState) {
		return this.stateTransitionMap.get(inrState);
	}

	private void setTransductor(ITransductor transductor) {
		this.transductor = transductor;
	}
}
