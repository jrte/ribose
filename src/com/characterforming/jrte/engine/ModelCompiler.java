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
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IField;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.IToken;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.Ribose;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.CompilationException;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/** Model compiler implements ITarget for the ribose compiler model. */
public final class ModelCompiler implements ITarget {

	/**
	 * The model to be compiled.
	 */
	private final Model model;

	private static final long VERSION = 210;
	private static final String AMBIGUOUS_STATE_MESSAGE = "%1$s: Ambiguous state %2$d";

	private final CharsetEncoder encoder;
	private final CharsetDecoder decoder;
	private final ArrayList<String> errors;
	private final Logger rtcLogger;
	private Bytes transducerName;
	private ITransductor transductor;
	private HashMap<Integer, Integer>[] stateMaps;
	private HashMap<Integer, ArrayList<Transition>> stateTransitionMap;
	private HashMap<Ints, Integer> effectorVectorMap;
	private int[] effectorVectors;
	private Header header = null;
	private Transition[] transitions = null;
	private int[] inputEquivalenceIndex;
	private int[][][] kernelMatrix;
	private int transition = 0;

	protected ModelCompiler() {
		this(null);
	}

	protected ModelCompiler(final Model model) {
		this.model = model;
		this.transductor = null;
		this.errors = new ArrayList<>();
		this.encoder = Base.newCharsetEncoder();
		this.decoder = Base.newCharsetDecoder();
		this.rtcLogger = Base.getCompileLogger();
		this.reset();
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		return new IEffector<?>[] {
			new HeaderEffector(this),
			new TransitionEffector(this),
			new AutomatonEffector(this)
		};
	}

	public static boolean compileAutomata(Model targetModel, File inrAutomataDirectory) throws ModelException {
		Logger rtcLogger = Base.getCompileLogger();
		File workingDirectory = new File(System.getProperty("user.dir", "."));
		String compilerModelPath = ModelCompiler.class.getPackageName() + "/TCompile.model";
		File compilerModelFile = null;
		if (ModelCompiler.class.getResource(compilerModelPath) != null) {
			try {
				compilerModelFile = File.createTempFile("TCompile", ".model");
				try (
					InputStream mis = ModelCompiler.class.getResourceAsStream(compilerModelPath);
					OutputStream mos =  new FileOutputStream(compilerModelFile);
				) {
					compilerModelFile.deleteOnExit();
					byte[] data = new byte[65536];
					int read = -1;
					do {
						read = mis.read(data);
						if (read > 0) {
							mos.write(data, 0, read);
						}
					} while (read >= 0);
				}
			} catch (IOException e) {
				compilerModelFile = new File(workingDirectory, "TCompile.model");
			}
		} else {
			compilerModelFile = new File(workingDirectory, "TCompile.model");
		}
		if (!compilerModelFile.exists()) {
			String msg = String.format(
				"TCompile.model not found in ribose jar or in working directory (%s).",
				workingDirectory);
			rtcLogger.log(Level.SEVERE, msg);
			return false;
		}

		try (IRuntime compilerRuntime = Ribose.loadRiboseModel(compilerModelFile)) {
			final CharsetEncoder encoder = Base.newCharsetEncoder();
			ModelCompiler compiler = new ModelCompiler(targetModel);
			compiler.setTransductor(compilerRuntime.transductor(compiler));
			for (final String filename : inrAutomataDirectory.list()) {
				if (!filename.endsWith(Base.AUTOMATON_FILE_SUFFIX)) {
					continue;
				}
				try {
					long filePosition = targetModel.seek(-1);
					if (compiler.compile(new File(inrAutomataDirectory, filename))) {
						String transducerName = filename.substring(0, filename.length() - Base.AUTOMATON_FILE_SUFFIX.length());
						int transducerOrdinal = targetModel.addTransducer(Bytes.encode(encoder, transducerName));
						targetModel.setTransducerOffset(transducerOrdinal, filePosition);
					} else {
						for (String error : compiler.getErrors()) {
							rtcLogger.severe(error);
						}
					}
				} catch (Exception e) {
					String msg = String.format("Exception caught compiling transducer '%1$s'", filename);
					rtcLogger.log(Level.SEVERE, msg, e);
					return false;
				}
			}
			return compiler.getErrors().isEmpty() && targetModel.save();
		} catch (ModelException e) {
			String msg = String.format("Exception caught compiling automata directory '%1$s'",
				inrAutomataDirectory.getPath());
			rtcLogger.log(Level.SEVERE, msg, e);
			return false;
		}
	}

	private class Header {
		final int version;
		final int tapes;
		final int transitions;
		final int states;
		final int symbols;

		Header(int version, int tapes, int transitions, int states, int symbols) {
			this.version = version;
			this.tapes = tapes;
			this.transitions = transitions;
			this.states = states;
			this.symbols = symbols;
			assert this.symbols > 0;
		}
	}

	class HeaderEffector extends BaseEffector<ModelCompiler> {
		IField[] fields;

		HeaderEffector(ModelCompiler automaton) {
			super(automaton, "header");
	 }

		@Override
		public
		void setOutput(IOutput output) throws TargetBindingException {
			super.setOutput(output);
			this.fields = new IField[] {
				super.output.getField(Bytes.encode(super.getEncoder(), "version")),
				super.output.getField(Bytes.encode(super.getEncoder(), "tapes")),
				super.output.getField(Bytes.encode(super.getEncoder(), "transitions")),
				super.output.getField(Bytes.encode(super.getEncoder(), "states")),
				super.output.getField(Bytes.encode(super.getEncoder(), "symbols"))
			};
		}

		@Override
		public
		int invoke() throws EffectorException {
			assert target.model != null;
			target.putHeader(this.fields);
			return IEffector.RTX_NONE;
		}
	}

	private class Transition {
		final int from;
		final int to;
		final int tape;
		final byte[] symbol;
		final boolean isFinal;

		Transition(int from, int to, int tape, byte[] symbol) {
			this.isFinal = (to == 1 && tape == 0 && symbol.length == 0);
			this.from = from;
			this.to = to;
			this.tape = tape;
			this.symbol = symbol;
		}
	}

	class TransitionEffector extends BaseEffector<ModelCompiler> {
		IField[] fields;

		TransitionEffector(ModelCompiler automaton) {
			super(automaton, "transition");
		}

		@Override
		public
		void setOutput(IOutput output) throws TargetBindingException {
			super.setOutput(output);
			fields = new IField[] {
				super.output.getField(Bytes.encode(super.getEncoder(), "from")),
				super.output.getField(Bytes.encode(super.getEncoder(), "to")),
				super.output.getField(Bytes.encode(super.getEncoder(), "tape")),
				super.output.getField(Bytes.encode(super.getEncoder(), "symbol"))
			};
		}

		@Override
		public
		int invoke() throws EffectorException {
			assert target.model != null;
			target.putTransition(this.fields);
			return IEffector.RTX_NONE;
		}
	}

	class AutomatonEffector extends BaseEffector<ModelCompiler> {
		AutomatonEffector(ModelCompiler target) {
			super(target, "automaton");
		}

		@Override
		public
		int invoke() throws EffectorException {
			assert target.model != null;
			target.putTransitionMatrix();
			return IEffector.RTX_NONE;
		}
	}

	private void setTransductor(ITransductor transductor) {
		this.transductor = transductor;
	}

	private void reset() {
		this.transducerName = null;
		this.stateMaps = null;
		this.stateTransitionMap = null;
		this.effectorVectorMap = null;
		this.inputEquivalenceIndex = null;
		this.kernelMatrix = null;
		this.header = null;
		this.transitions = null;
		this.transition = 0;
		this.errors.clear();
	}

	@SuppressWarnings("unchecked")
	private boolean compile(File inrFile) throws RiboseException {
		this.reset();
		String name = inrFile.getName();
		name = name.substring(0, name.length() - Base.AUTOMATON_FILE_SUFFIX.length());
		this.transducerName = Bytes.encode(this.encoder, name);
		int size = (int)inrFile.length();
		byte[] bytes = null;
		try (DataInputStream f = new DataInputStream(new FileInputStream(inrFile))) {
			int position = 0, length = size;
			bytes = new byte[length];
			while (length > 0) {
				int read = f.read(bytes, position, length);
				position += read;
				length -= read;
			}
			assert position == size;
		} catch (FileNotFoundException e) {
			this.error(String.format("%1$s: File not found '%2$s'",
				name, inrFile.getPath()));
			return false;
		} catch (IOException e) {
			this.error(String.format("%1$s: IOException compiling '%2$s'; %3$s",
				name, inrFile.getPath(), e.getMessage()));
			return false;
		}
		try {
			this.stateMaps = (HashMap<Integer, Integer>[])new HashMap<?,?>[3];
			this.stateTransitionMap = new HashMap<>(size >> 3);
			Bytes automaton = Bytes.encode(this.encoder, "Automaton");
			if (this.transductor.stop().push(bytes, size).signal(Signal.NIL).start(automaton).status().isRunnable()
			&& this.transductor.run().status().isPaused()) {
				this.transductor.signal(Signal.EOS).run();
			}
			assert !this.transductor.status().isRunnable();
			this.transductor.stop();
			assert this.transductor.status().isStopped();
			if (this.errors.isEmpty()) {
				this.save();
				return true;
			} else {
				this.error(String.format(String.format("%1$s: Compilation halted with status '%2$s'",
					name, this.transductor.status().toString())));
				return false;
			}
		} catch (ModelException e) {
			this.error(String.format("%1$s: ModelException compiling '%2$s'; %3$s",
				name, inrFile.getPath(), e.getMessage()));
			return false;
		} catch (DomainErrorException e) {
			this.error(String.format("%1$s: DomainErrorException compiling '%2$s'; %3$s",
				name, inrFile.getPath(), e.getMessage()));
			return false;
		} catch (RiboseException e) {
			this.error(String.format("%1$s: RteException compiling '%2$s'; %3$s",
				name, inrFile.getPath(), e.getMessage()));
			return false;
		} finally {
			this.transductor.stop();
			assert this.transductor.status().isStopped();
		}
	}

	private void putHeader(IField[] fields) {
		assert model != null;
		Header h = new Header(
			(int) fields[0].asInteger(),
			(int) fields[1].asInteger(),
			(int) fields[2].asInteger(),
			(int) fields[3].asInteger(),
			(int) fields[4].asInteger());
		if (h.version != ModelCompiler.VERSION) {
			this.error(String.format("%1$s: Invalid INR version %2$d",
				getTransducerName(), h.version));
		}
		if (h.tapes > 3) {
			this.error(String.format("%1$s: Invalid tape count %2$d",
				getTransducerName(), h.tapes));
		}
		this.header = h;
		this.transitions = new Transition[h.transitions];
		stateTransitionMap = new HashMap<>((h.states * 5) >> 2);
	}

	private void putTransition(IField[] fields) {
		assert this.header.transitions == this.transitions.length;
		Transition t = new Transition(
			(int) fields[0].asInteger(),
			(int) fields[1].asInteger(),
			(int) fields[2].asInteger(),
			fields[3].copyValue());
		if (!t.isFinal) {
			if (t.tape < 0) {
				this.error(String.format("%1$s: Epsilon transition from state %2$d to %3$d (use :dfamin to remove these)",
					this.getTransducerName(), t.from, t.to));
			} else if (t.symbol.length == 0) {
				this.error(String.format("%1$s: Empty symbol on tape %2$d",
					this.getTransducerName(), t.tape));
			} else {
				this.transitions[this.transition++] = t;
				switch (t.tape) {
					case 0:
						this.compileInputToken(t.symbol);
						break;
					case 1:
						this.compileEffectorToken(t.symbol);
						break;
					case 2:
						this.compileParameterToken(t.symbol);
						break;
					default:
						assert false;
				}
				HashMap<Integer, Integer> rteStates = this.stateMaps[t.tape];
				if (rteStates == null) {
					rteStates = this.stateMaps[t.tape] = new HashMap<>(256);
				}
				if (!rteStates.containsKey(t.from)) {
					rteStates.put(t.from, rteStates.size());
				}
				ArrayList<Transition> outgoing = this.stateTransitionMap.get(t.from);
				if (outgoing == null) {
					outgoing = new ArrayList<>(16);
					this.stateTransitionMap.put(t.from, outgoing);
				}
				outgoing.add(t);
			}
		}
	}

	private void putTransitionMatrix() {
		final Integer[] inrInputStates = this.getInrStates(0);
		if (inrInputStates == null) {
			this.error("Empty automaton " + this.getTransducerName());
			return;
		}

		for (final ArrayList<Transition> transitionList : this.stateTransitionMap.values()) {
			transitionList.trimToSize();
		}

		final int[][][] transitionMatrix = new int[this.model.getSignalLimit()][inrInputStates.length][2];
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
				if (!t.isFinal) {
					if (t.tape != 0) {
						this.error(String.format(ModelCompiler.AMBIGUOUS_STATE_MESSAGE,
								this.getTransducerName(), t.from));
						continue;
					}
					try {
						final int rteState = this.getRteState(0, t.from);
						final int inputOrdinal = this.model.getInputOrdinal(t.symbol);
						final Chain chain = this.chain(t);
						if (chain != null) {
							final int[] effectVector = chain.getEffectVector();
							transitionMatrix[inputOrdinal][rteState][0] = this.getRteState(0, chain.getOutS());
							if (chain.isEmpty()) {
								transitionMatrix[inputOrdinal][rteState][1] = 1;
							} else if (chain.isScalar()) {
								transitionMatrix[inputOrdinal][rteState][1] = effectVector[0];
							} else if (chain.isParameterized()) {
								transitionMatrix[inputOrdinal][rteState][1] = Transducer.action(-1 * effectVector[0], effectVector[1]);
							} else {
								Ints vector = new Ints(effectVector);
								Integer vectorOrdinal = this.effectorVectorMap.get(vector);
								if (vectorOrdinal == null) {
									vectorOrdinal = this.effectorVectorMap.size();
									this.effectorVectorMap.put(vector, vectorOrdinal);
								}
								transitionMatrix[inputOrdinal][rteState][1] = -vectorOrdinal;
							}
						}
					} catch (CompilationException e) {
						this.error(String.format("%1$s: %2$s",
								this.getTransducerName(), e.getMessage()));
					}
				}
			}
		}

		if (this.errors.isEmpty()) {
			this.factor(transitionMatrix);
		}
	}

	private void factor(final int[][][] transitionMatrix) {
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
		final int msumOrdinal = this.model.getEffectorOrdinal(Bytes.encode(this.encoder, "msum"));
		final int mproductOrdinal = this.model.getEffectorOrdinal(Bytes.encode(this.encoder, "mproduct"));
		final int mscanOrdinal = this.model.getEffectorOrdinal(Bytes.encode(this.encoder, "mscan"));
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
						byte[][] mscanParameterBytes = { new byte[] { (byte)token } };
						int mscanParameterIndex = this.model.compileParameters(mscanOrdinal, mscanParameterBytes);
						mscanStateEffects[state] = new int[] { -1 * mscanOrdinal, mscanParameterIndex, 0 };
						break;
					}
				}
			} else if (selfCount > 64) {
				assert msumStateEffects[state] == null;
				byte[][] msumParameterBytes = { Arrays.copyOf(selfBytes, selfCount) };
				int msumParameterIndex = this.model.compileParameters(msumOrdinal, msumParameterBytes);
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
					if (walkResult[0] > 3) {
						assert this.inputEquivalenceIndex[walkedBytes[0]] == exitEquivalent[toState];
						if (mproductStateEffects[toState] == null) {
							mproductStateEffects[toState] = new int[] { 
								-1 * mproductOrdinal, 
								this.model.compileParameters(
									mproductOrdinal, new byte[][] { Arrays.copyOfRange(walkedBytes, 0, walkResult[0]) }
								), 
								0
							};
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
			HashSet<Integer> equivalentClassOrdinals = rowEquivalenceMap.get(row);
			if (equivalentClassOrdinals == null) {
				equivalentClassOrdinals = new HashSet<>(16);
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
					if (parameterPos > 0) {
						assert((effectorPos > 0) && (effectorOrdinal == effectorVector[effectorPos - 1]));
						effectorVector[effectorPos - 1] *= -1;
						final byte[][] parameters = Arrays.copyOf(parameterList, parameterPos);
						int parameterOrdinal = this.model.compileParameters(effectorOrdinal, parameters);
						effectorVector[effectorPos] = parameterOrdinal;
						parameterList = new byte[8][];
						parameterPos = 0;
						++effectorPos;
					}
					effectorOrdinal = this.model.getEffectorOrdinal(new Bytes(t.symbol));
					assert effectorOrdinal >= 0;
					effectorVector[effectorPos] = effectorOrdinal;
					++effectorPos;
					break;
				case 2:
					if (parameterPos >= parameterList.length) {
						int newLength = parameterList.length > 4 ? (parameterList.length * 3) >> 1 : 5;
						parameterList = Arrays.copyOf(parameterList, newLength);
					}
					parameterList[parameterPos] = t.symbol;
					++parameterPos;
					break;
				default:
					this.error(String.format("%1$s: Invalid tape number %2$d",
						this.getName(), t.tape));
					break;
			}
			outT = this.getTransitions(t.to);
		}
		if (this.errors.isEmpty()) {
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
				final byte[][] parameters = Arrays.copyOf(parameterList, parameterPos);
				int parameterOrdinal = this.model.compileParameters(effectorOrdinal, parameters);
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
						this.error(String.format(AMBIGUOUS_STATE_MESSAGE, this.getName(), t.from));
					} else {
						outS = t.from;
					}
				}
				if (this.errors.isEmpty()) {
					return new Chain(Arrays.copyOf(effectorVector, effectorPos), outS);
				}
			} else {
				for (final Transition t : outT) {
					this.error(String.format(AMBIGUOUS_STATE_MESSAGE, this.getName(), t.from));
				}
			}
		}
		return null;
	}

	private ArrayList<String> getErrors() {
		return this.errors;
	}

	private void save() throws ModelException {
		assert this.errors.isEmpty();
		this.model.putString(this.getTransducerName());
		this.model.putString(this.getName());
		this.model.putIntArray(this.inputEquivalenceIndex);
		this.model.putTransitionMatrix(this.kernelMatrix);
		this.model.putIntArray(this.effectorVectors);
		int t = 0;
		for (final int[][] row : this.kernelMatrix) {
			for (final int[] col : row) {
				if (col[1] != 0) {
					t++;
				}
			}
		}
		final int transitionCount = t;
		double sparsity = 100 - (double)(100 * transitionCount)/(double)(this.kernelMatrix.length * this.kernelMatrix[0].length);
		this.rtcLogger.log(Level.INFO, () -> String.format("%1$20s: %2$5d input classes %3$5d states %4$5d transitions (%5$.0f%% nul)",
			this.getTransducerName(), this.kernelMatrix.length, this.kernelMatrix[0].length, transitionCount, sparsity));
		System.err.flush();
	}

	private String getTransducerName() {
		return this.transducerName.toString();
	}

	private void compileInputToken(byte[] bytes) {
		if (bytes.length > 1) {
			String type = null;
			switch(bytes[0]) {
			case IToken.TRANSDUCER_TYPE:
				type = "transducer";
				break;
			case IToken.FIELD_TYPE:
				type = "field";
				break;
			case IToken.SIGNAL_TYPE:
				type = "signal";
				break;
			default:
				this.model.addSignal(new Bytes(bytes));
				return;
			}
			this.error(String.format("%1$s: Invalid input token '%2$s' of %3$s type on tape 0",
				this.getTransducerName(), Bytes.decode(this.decoder, bytes, bytes.length), type));
		}
	}

	private void compileEffectorToken(byte[] bytes) {
		assert (bytes.length > 0);
		if (0 > this.model.getEffectorOrdinal(new Bytes(bytes))) {
			this.error(String.format("%1$s: Unknown effector token '%2$s' on tape 1",
				this.getTransducerName(), Bytes.decode(this.decoder, bytes, bytes.length)));
		}
	}

	private void compileParameterToken(byte[] bytes) {
		if (bytes.length > 1) {
			Bytes token = new Bytes(bytes, 1, bytes.length - 1);
			switch (bytes[0]) {
			case IToken.TRANSDUCER_TYPE:
				this.model.addTransducer(token);
				break;
			case IToken.FIELD_TYPE:
				this.model.addField(token);
				break;
			case IToken.SIGNAL_TYPE:
				this.model.addSignal(token);
				break;
			default:
				break;
			}
		}
	}

	private int getRteState(final int tape, final int inrState) throws CompilationException {
		final Integer rteInS = tape < this.stateMaps.length && this.stateMaps[tape] != null ? this.stateMaps[tape].get(inrState) : null;
		if (rteInS == null) {
			throw new CompilationException(String.format("No RTE state for INR tape %1$d state %2$d", tape, inrState));
		}
		return rteInS;
	}

	private Integer[] getInrStates(final int tape) {
		final HashMap<Integer, Integer> inrRteStateMap = tape < this.stateMaps.length && this.stateMaps[tape] != null ? this.stateMaps[tape] : null;
		if (inrRteStateMap != null) {
			Integer[] inrStates = new Integer[inrRteStateMap.size()];
			inrStates = inrRteStateMap.keySet().toArray(inrStates);
			Arrays.sort(inrStates);
			return inrStates;
		}
		return null;
	}

	private ArrayList<Transition> getTransitions(final int inrState) {
		return this.stateTransitionMap.get(inrState);
	}

	private void error(final String message) {
		if (!this.errors.contains(message)) {
			this.errors.add(message);
		}
	}

	@SuppressWarnings("unused")
	private int[][][] transpose(int[][][] m) {
		int[][][] t = new int[m[0].length][m.length][m[0][0].length];
		for(int i=0 ; i<m.length; i++) {
			for(int j=0 ; j<(m[i].length) ; j++) {
				t[j][i] = m[i][j];
			}
		}
		return t;
	}
}
