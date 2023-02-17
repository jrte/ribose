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
import java.io.IOException;
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
import com.characterforming.ribose.INamedValue;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.Ribose;
import com.characterforming.ribose.TCompile;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.CompilationException;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

public class ModelCompiler implements ITarget {

	/**
	 * The model to be compiled.
	 */
	protected final Model model;

	private static final long VERSION = 210;
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
	private Transition transitions[] = null;
	private int[] inputEquivalenceIndex;
	private int[][][] kernelMatrix;
	private int transition = 0;

	protected ModelCompiler() {
		this(null);
	}

	protected ModelCompiler(final Model model) {
		this.model = model;
		this.transductor = null;
		this.errors = new ArrayList<String>();
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
		File workingDirectory = new File(System.getProperty("user.dir", "."));
		File compilerModelFile = new File(workingDirectory, "TCompile.model");
		Logger rtcLogger = Base.getCompileLogger();
		try (IRuntime compilerRuntime = Ribose.loadRiboseModel(compilerModelFile)) {
			final CharsetEncoder encoder = Base.newCharsetEncoder();
			TCompile compiler = new TCompile(targetModel);
			compiler.setTransductor(compilerRuntime.newTransductor(compiler));
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
			return compiler.getErrors().isEmpty();
		} catch (ModelException e) {
			String msg = String.format("Exception caught compiling automata directory '%1$s'", inrAutomataDirectory.getPath());
			rtcLogger.log(Level.SEVERE, msg, e);
			return false;
		}
	}

	class Header {
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
		}
	}

	class HeaderEffector extends BaseEffector<ModelCompiler> {
		INamedValue fields[];

		HeaderEffector(ModelCompiler automaton) {
			super(automaton, "header");
	 }

		@Override
		public
		void setOutput(IOutput output) throws TargetBindingException {
			assert target.model != null;
			super.setOutput(output);
			fields = new INamedValue[] {
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "version")),
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "tapes")),
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "transitions")),
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "states")),
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "symbols"))
			};
		}

		@Override
		public
		int invoke() throws EffectorException {
			assert target.model != null;
			Header h = new Header(
				(int)fields[0].asInteger(),
				(int)fields[1].asInteger(),
				(int)fields[2].asInteger(),
				(int)fields[3].asInteger(),
				(int)fields[4].asInteger()
			);
			target.header = h;
			if (target.header.version != ModelCompiler.VERSION) {
				target.error(String.format("%1$s: Invalid INR version %2$d",
					target.getTransducerName(), h.version));
			}
			if ((h.tapes < 2) || (h.tapes > 3)) {
				target.error(String.format("%1$s: Invalid tape count %2$d",
					target.getTransducerName(), h.tapes));
			}
			target.transitions = new Transition[h.transitions];
			target.stateTransitionMap = new HashMap<Integer, ArrayList<Transition>>((h.states * 5) >> 2);
			return IEffector.RTX_NONE;
		}
	}

	class Transition {
		final int from;
		final int to;
		final int tape;
		final byte symbol[];
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
		INamedValue fields[];

		TransitionEffector(ModelCompiler automaton) {
			super(automaton, "transition");
		}

		@Override
		public
		void setOutput(IOutput output) throws TargetBindingException {
			assert target.model != null;
			super.setOutput(output);
			fields = new INamedValue[] {
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "from")),
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "to")),
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "tape")),
				super.output.getNamedValue(Bytes.encode(super.output.getCharsetEncoder(), "symbol"))
			};
		}

		@Override
		public
		int invoke() throws EffectorException {
			assert target.model != null;
			Transition t = new Transition(
				(int)fields[0].asInteger(),
				(int)fields[1].asInteger(),
				(int)fields[2].asInteger(),
				fields[3].copyValue()
			);
			if (t.isFinal) {
				return IEffector.RTX_NONE;
			}	else if (t.tape < 0) {
				target.error(String.format("%1$s: Epsilon transition from state %2$d to %3$d (use :dfamin to remove these)",
					target.getTransducerName(), t.from, t.to));
			} else if (t.symbol.length == 0) {
				target.error(String.format("%1$s: Empty symbol on tape %2$d",
					target.getTransducerName(), t.tape));
			} else {
				HashMap<Integer, Integer> rteStates = target.stateMaps[t.tape];
				if (rteStates == null) {
					rteStates = target.stateMaps[t.tape] = new HashMap<Integer, Integer>(256);
				}
				if (!rteStates.containsKey(t.from)) {
					rteStates.put(t.from, rteStates.size());
				}
				switch (t.tape) {
				case 0:
					target.compileInputToken(t.symbol);
					break;
				case 1:
					target.compileEffectorToken(t.symbol);
				break;
				case 2:
					target.compileParameterToken(t.symbol);
					break;
				}
				ArrayList<Transition> outgoing = target.stateTransitionMap.get(t.from);
				if (outgoing == null) {
					outgoing = new ArrayList<Transition>(16);
					target.stateTransitionMap.put(t.from, outgoing);
				}
				outgoing.add(t);
				target.transitions[target.transition] = t;
				++target.transition;
			}
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
			final Integer[] inrInputStates = target.getInrStates(0);
			if (inrInputStates == null) {
				String msg = "Empty automaton " + target.getTransducerName();
				super.output.getRtcLogger().log(Level.SEVERE, msg);
				throw new EffectorException(msg);
			}

			for (final ArrayList<Transition> transitions : target.stateTransitionMap.values()) {
				transitions.trimToSize();
			}

			final int[][][] transitionMatrix = new int[target.model.getSignalLimit()][inrInputStates.length][2];
			for (int i = 0; i < transitionMatrix.length; i++) {
				for (int j = 0; j < inrInputStates.length; j++) {
					transitionMatrix[i][j][0] = j;
					transitionMatrix[i][j][1] = 0;
				}
			}

			target.effectorVectorMap = new HashMap<Ints, Integer>(1024);
			target.effectorVectorMap.put(new Ints(new int[] {0}), 0);
			for (final Integer inrInputState : inrInputStates) {
				for (final Transition t : target.getTransitions(inrInputState)) {
					if (t.isFinal) {
						continue;
					}
					if (t.tape != 0) {
						target.error(String.format("%1$s: Ambiguous state %2$d",
							target.getTransducerName(), t.from));
						continue;
					}
					try {
						final int rteState = target.getRteState(0, t.from);
						final int inputOrdinal = target.model.getInputOrdinal(t.symbol);
						final Chain chain = target.chain(t);
						if (chain != null) {
							final int[] effectVector = chain.getEffectVector();
							transitionMatrix[inputOrdinal][rteState][0] = target.getRteState(0, chain.getOutS());
							if (chain.isEmpty()) {
								transitionMatrix[inputOrdinal][rteState][1] = 1;
							} else if (chain.isScalar()) {
								transitionMatrix[inputOrdinal][rteState][1] = effectVector[0];
							} else {
								Ints vector = new Ints(effectVector);
								Integer vectorOrdinal = target.effectorVectorMap.get(vector);
								if (vectorOrdinal == null) {
									vectorOrdinal = target.effectorVectorMap.size();
									target.effectorVectorMap.put(vector, vectorOrdinal);
								}
								transitionMatrix[inputOrdinal][rteState][1] = -vectorOrdinal;
							}
						}
					} catch (CompilationException e) {
						target.error(String.format("%1$s: %2$s",
							target.getTransducerName(), e.getMessage()));
					}
				}
			}

			if (target.errors.isEmpty()) {
				target.factor(transitionMatrix);
			}

			return IEffector.RTX_NONE;
		}
	}

	protected void setTransductor(ITransductor transductor) {
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
	protected boolean compile(File inrFile) throws RiboseException {
		this.reset();
		String name = inrFile.getName();
		name = name.substring(0, name.length() - Base.AUTOMATON_FILE_SUFFIX.length());
		this.transducerName = Bytes.encode(this.encoder, name);
		int size = (int)inrFile.length();
		byte bytes[] = null;
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
			this.stateTransitionMap = new HashMap<Integer, ArrayList<Transition>>(size >> 3);
			Bytes automaton = Bytes.encode(this.encoder, "Automaton");
			if (this.transductor.stop().push(bytes, size).push(Signal.nil).start(automaton).status().isRunnable()) {
				if (this.transductor.run().status().isPaused()) {
					this.transductor.push(Signal.eos).run();
				}
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

	private void factor(final int[][][] transitionMatrix) {
		// factor matrix modulo input equivalence
		final int nStates = transitionMatrix[0].length;
		final HashMap<IntsArray, HashSet<Integer>> rowEquivalenceMap =
			new HashMap<IntsArray, HashSet<Integer>>((5 * transitionMatrix.length) >> 2);
		for (int i = 0; i < transitionMatrix.length; i++) {
			assert transitionMatrix[i].length == nStates;
			final IntsArray row = new IntsArray(transitionMatrix[i]);
			HashSet<Integer> equivalentInputOrdinals = rowEquivalenceMap.get(row);
			if (equivalentInputOrdinals == null) {
				equivalentInputOrdinals = new HashSet<Integer>(16);
				rowEquivalenceMap.put(row, equivalentInputOrdinals);
			}
			equivalentInputOrdinals.add(i);
		}
		int equivalenceIndex = 0;
		this.inputEquivalenceIndex = new int[transitionMatrix.length];
		this.kernelMatrix = new int[rowEquivalenceMap.size()][][];
		for (final Map.Entry<IntsArray, HashSet<Integer>> entry : rowEquivalenceMap.entrySet()) {
			final IntsArray row = entry.getKey();
			for (final int inputOrdinal : entry.getValue()) {
				this.inputEquivalenceIndex[inputOrdinal] = equivalenceIndex;
			}
			this.kernelMatrix[equivalenceIndex++] = row.getInts();
		}
		// msum instrumentation
		final int nInputs = equivalenceIndex;
		final int nulSignal = Signal.nul.signal();
		final int msumOrdinal = this.model.getEffectorOrdinal(Bytes.encode(this.encoder, "msum"));
		int[][] msumStateEffects = new int[nStates][];
		Arrays.fill(msumStateEffects, null);
		byte[][] equivalentInputs = new byte[nInputs][transitionMatrix.length];
		int[] equivalenceLengths = new int[nInputs];
		Arrays.fill(equivalenceLengths, 0);
		for (int inputOrdinal = 0; inputOrdinal < transitionMatrix.length; inputOrdinal++) {
			int q = this.inputEquivalenceIndex[inputOrdinal];
			equivalentInputs[q][equivalenceLengths[q]++] = (byte)inputOrdinal;
		}
		for (int state = 0; state < nStates; state++) {
			int selfIndex = 0, selfCount = 0;
			byte[] selfBytes = new byte[nulSignal];
			Arrays.fill(selfBytes, (byte)0);
			for (int input = 0; input < nInputs; input++) {
				int[] transition = this.kernelMatrix[input][state];
				if (transition[0] == state && transition[1] == 1) {
					for (int i = 0; i < equivalenceLengths[input]; i++) {
						selfBytes[selfIndex++] = equivalentInputs[input][i];
					}
					selfCount += equivalenceLengths[input];
				}
			}
			if (selfCount > 64) {
				byte[][] msumParameterBytes = { Arrays.copyOf(selfBytes, selfCount) };
				int msumParameterIndex = this.model.compileParameters(msumOrdinal, msumParameterBytes);
				msumStateEffects[state] = new int[] { -1 * msumOrdinal, msumParameterIndex, 0 };
			}
		}
		// effect vector mapping
		int effectCount = 0;
		int vectorCount = this.effectorVectorMap.size();
		int[][] effectVectors = new int[(vectorCount * 3) >> 1][];
		for (Entry<Ints, Integer> entry : this.effectorVectorMap.entrySet())  {
			int[] vector = entry.getKey().getInts();
			effectVectors[entry.getValue()] = vector;
			effectCount += vector.length;
		}
		for (int input = 0; input < nInputs; input++) {
			for (int state = 0; state < nStates; state++) {
				int[] transition = this.kernelMatrix[input][state];
				int msumLength = msumStateEffects[transition[0]] != null ? msumStateEffects[transition[0]].length : 0;
				int vectorOrdinal = transition[1];
				if (vectorOrdinal != 0 && transition[0] != state && msumStateEffects[transition[0]] != null
				&& ((vectorOrdinal > 0) || (effectVectors[-1 * vectorOrdinal][effectVectors[-1 * vectorOrdinal].length - msumLength] != msumOrdinal))) {
					int[] msumEffect = msumStateEffects[transition[0]];
					int[] vector = vectorOrdinal > 0
						? (vectorOrdinal > 1 ? new int[] { vectorOrdinal, 0 } : new int[] { vectorOrdinal })
						: effectVectors[-1 * vectorOrdinal];
					int[] vectorex = Arrays.copyOf(vector, vector.length + msumEffect.length - 1);
					System.arraycopy(msumEffect, 0, vectorex, vector.length - 1, msumEffect.length);
					Ints vxkey = new Ints(vectorex);
					if (this.effectorVectorMap.containsKey(vxkey)) {
						vectorOrdinal = this.effectorVectorMap.get(vxkey);
					} else {
						vectorOrdinal = this.effectorVectorMap.size();
						this.effectorVectorMap.put(vxkey, vectorOrdinal);
						if (vectorOrdinal >= effectVectors.length) {
							int[][] newv = new int[vectorOrdinal > 1 ? (vectorOrdinal * 3) >> 1 : 2][];
							for (int i = 0; i < effectVectors.length; i++) {
								newv[i] = effectVectors[i];
							}
							effectVectors = newv;
						}
						effectVectors[vectorOrdinal] = vectorex;
						effectCount += vectorex.length;
						vectorCount += 1;
					}
					transition[1] = -1 * vectorOrdinal;
				}
			}
		}
		final int[] effectorVectorPosition = new int[this.effectorVectorMap.size()];
		Arrays.fill(effectorVectorPosition, -1);
		this.effectorVectors = new int[effectCount];
		this.effectorVectors[0] = 0;
		int position = 1;
		for (int input = 0; input < nInputs; input++) {
			for (int state = 0; state < nStates; state++) {
				int[] transition = this.kernelMatrix[input][state];
				int ordinal = transition[1];
				if (ordinal < 0) {
					ordinal *= -1;
					if (effectorVectorPosition[ordinal] < 0) {
						System.arraycopy(effectVectors[ordinal], 0, this.effectorVectors, position, effectVectors[ordinal].length);
						transition[1] = -1 * position;
						effectorVectorPosition[ordinal] = position;
						position += effectVectors[ordinal].length;
					} else {
						transition[1] = -1 * effectorVectorPosition[ordinal];
					}
				}
			}
		}
		if (position < this.effectorVectors.length) {
			this.effectorVectors = Arrays.copyOf(this.effectorVectors, position);
		}
		// mproduct instrumentation
		// final int nulEquivalent = this.inputEquivalenceIndex[nulSignal];
		// final int[] outInputs = new int[nStates + 1];
		// final int[] outDegree = new int[nStates];
		// Arrays.fill(outInputs, -1);
		// Arrays.fill(outDegree, 0);
		// for (int input = 0; input < nInputs; input++) {
		// 	for (int state = 0; state < nStates; state++) {
		// 		int[] transition = this.kernelMatrix[input][state];
		// 		if (transition[1] != 0 && input != nulEquivalent) {
		// 			outInputs[state] = input;
		// 			outDegree[state] += 1;
		// 		}
		// 	}
		// }
		// StateStack walkStack = new StateStack(nStates);
		// int[] walkedInputs = new int[nStates];
		// walkStack.push(0);
		// while (walkStack.size() > 0) {
		// 	int walkState = walkStack.pop();
		// 	if (outDegree[walkState] == 1) {
		//		walkedInputs[0] = 0;
		// 		int nulState = this.kernelMatrix[nulEquivalent][walkState][0];
		// 		int walkEndState = this.walk(walkState, nulState, walkedInputs);
		// 		if (walkEndState >= 0) {
		//			...
		// 			this.kernelMatrix[outInputs[walkState]][walkState][0] = walkEndState;
		// 		}
		// 	} else for (int input = 0; input < nInputs; input++) {
		// 		if (input != nulEquivalent) {
		// 			int[] transition = this.kernelMatrix[input][walkState];
		// 			if (transition[0] != walkState) {
		// 				walkStack.push(transition[0]);
		// 				assert transition[1] != 0;
		// 			}
		// 		}
		// 	}
		// }
	}

	// private int walk(int fromState, int nulState, int[] walked) {
	// 	return 0;
	// }

	private Chain chain(final Transition transition) {
		assert transition.tape != 1 && transition.tape != 2 : "Invalid tape number for chain(InrTransition) : " + transition.toString();
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
						effectorVector = Arrays.copyOf(effectorVector, (effectorVector.length * 3) >> 1);
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
						parameterList = Arrays.copyOf(parameterList, (parameterList.length * 3) >> 1);
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
			if (outT == null || outT.size() == 0 || outT.size() == 1 && outT.get(0).isFinal) {
				return new Chain(Arrays.copyOf(effectorVector, effectorPos), 0);
			} else if (outT.size() == 1 && outT.get(0).tape == 0) {
				return new Chain(Arrays.copyOf(effectorVector, effectorPos), outT.get(0).from);
			} else if (outT.get(0).isFinal || outT.get(0).tape == 0) {
				int outS = -1;
				for (final Transition t : outT) {
					if (t.tape > 0) {
						this.error(String.format("%1$s: Ambiguous state %2$d", this.getName(), t.from));
					} else {
						outS = t.from;
					}
				}
				if (this.errors.size() == 0) {
					return new Chain(Arrays.copyOf(effectorVector, effectorPos), outS);
				}
			} else {
				for (final Transition t : outT) {
					this.error(String.format("%1$s: Ambiguous state %2$d", this.getName(), t.from));
				}
			}
		}
		return null;
	}

	protected ArrayList<String> getErrors() {
		return this.errors;
	}

	private void save() throws ModelException {
		assert this.errors.isEmpty();
		this.model.putString(this.getTransducerName());
		this.model.putString(this.getName());
		this.model.putIntArray(this.inputEquivalenceIndex);
		this.model.putTransitionMatrix(this.kernelMatrix);
		this.model.putIntArray(this.effectorVectors);
		int transitions = 0;
		for (final int[][] row : this.kernelMatrix) {
			for (final int[] col : row) {
				if (col[1] != 0) {
					transitions++;
				}
			}
		}
		double sparsity = 100 - (double)(100 * transitions)/(double)(this.kernelMatrix.length * this.kernelMatrix[0].length);
		this.rtcLogger.log(Level.INFO, String.format("%1$20s: %2$5d input classes %3$5d states %4$5d transitions (%5$.0f%% nul)",
			this.getTransducerName(), this.kernelMatrix.length, this.kernelMatrix[0].length, transitions, sparsity));
		System.out.flush();
	}

	private String getTransducerName() {
		return this.transducerName.toString();
	}

	private void compileInputToken(byte[] bytes) {
		if (bytes.length > 1) {
			String type = null;
			switch(bytes[0]) {
			case Base.TYPE_ORDINAL_INDICATOR:
				type = "reference ordinal";
				break;
			case Base.TYPE_REFERENCE_TRANSDUCER:
				type = "transducer";
				break;
			case Base.TYPE_REFERENCE_VALUE:
				type = "value name";
				break;
			case Base.TYPE_REFERENCE_SIGNAL:
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
			case Base.TYPE_REFERENCE_TRANSDUCER:
				this.model.addTransducer(token);
				break;
			case Base.TYPE_REFERENCE_VALUE:
				this.model.addNamedValue(token);
				break;
			case Base.TYPE_REFERENCE_SIGNAL:
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
}
