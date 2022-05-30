/***
 * JRTE is a recursive transduction engine for Java
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
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.INamedValue;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.ITransductor.Status;
import com.characterforming.ribose.Ribose;
import com.characterforming.ribose.TCompile;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.CompilationException;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.TargetBindingException;

public class ModelCompiler implements ITarget {

	/**
	 * The model to be compiled.
	 */
	protected final Model model;
	
	private static final long VERSION = 210;
	private final ArrayList<String> errors;
	private Bytes transducerName;
	private ITransductor transductor;
	private HashMap<Integer, Integer>[] stateMaps;
	private HashMap<Integer, ArrayList<Transition>> stateTransitionMap;
	private HashMap<Ints, Integer> effectorVectorMap;
	private ArrayList<Integer> effectorVectorList;
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
		this.reset();
	}
	
	public static boolean compileAutomata(Model targetModel, File inrAutomataDirectory) throws ModelException {
		File workingDirectory = new File(System.getProperty("user.dir"));
		File compilerModelFile = new File(workingDirectory, "TCompile.model");
		try (IRuntime compilerRuntime = Ribose.loadRiboseModel(compilerModelFile, new TCompile())) {
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
						int transducerOrdinal = targetModel.addTransducer(Bytes.encode(transducerName));
						targetModel.setTransducerOffset(transducerOrdinal, filePosition);
					} else {
						for (String error : compiler.getErrors()) {
							Model.rtcLogger.severe(error);
						}
					}
				} catch (Exception e) {
					String msg = String.format("Exception caught compiling transducer '%1$s'", filename);
					Model.rtcLogger.log(Level.SEVERE, msg, e);
					return false;
				}
			}
			return compiler.getErrors().isEmpty();
		} catch (ModelException e) {
			String msg = String.format("Exception caught compiling automata directrory '%1$s'", inrAutomataDirectory.getPath());
			Model.rtcLogger.log(Level.SEVERE, msg, e);
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
		
	class HeaderEffector extends BaseEffector<ModelCompiler> {
		INamedValue fields[];
		
		HeaderEffector(ModelCompiler automaton) {
			super(automaton, Bytes.encode("header"));
	 }
		
		@Override
		public void setOutput(IOutput output) throws TargetBindingException {
			super.setOutput(output);
			fields = new INamedValue[] {
				super.output.getNamedValue(Bytes.encode("version")),
				super.output.getNamedValue(Bytes.encode("tapes")),
				super.output.getNamedValue(Bytes.encode("transitions")),
				super.output.getNamedValue(Bytes.encode("states")),
				super.output.getNamedValue(Bytes.encode("symbols"))
			};
		}
		
		@Override
		public int invoke() throws EffectorException {
			Header h = new Header(
				(int)fields[0].asInteger(),
				(int)fields[1].asInteger(),
				(int)fields[2].asInteger(),
				(int)fields[3].asInteger(),
				(int)fields[4].asInteger()
			);
			target.stateTransitionMap = new HashMap<Integer, ArrayList<Transition>>((h.states * 5) >> 2);
			if (h.version != ModelCompiler.VERSION) {
				target.error(String.format("%1$s: Invalid INR version %2$d", 
					target.getTransducerName(), h.version));
			}
			if ((h.tapes < 2) || (h.tapes > 3)) {
				target.error(String.format("%1$s: Invalid tape count %2$d", 
					target.getTransducerName(), h.tapes));
			}
			target.header = h;
			target.transitions = new Transition[h.transitions];
			return IEffector.RTE_EFFECT_NONE;
		}
	}

	public class TransitionEffector extends BaseEffector<ModelCompiler> {
		INamedValue fields[];
		
		TransitionEffector(ModelCompiler automaton) {
			super(automaton, Bytes.encode("transition"));
		}
		
		@Override
		public void setOutput(IOutput output) throws TargetBindingException {
			super.setOutput(output);
			fields = new INamedValue[] {
				super.output.getNamedValue(Bytes.encode("from")),
				super.output.getNamedValue(Bytes.encode("to")),
				super.output.getNamedValue(Bytes.encode("tape")),
				super.output.getNamedValue(Bytes.encode("length")),
				super.output.getNamedValue(Bytes.encode("symbol"))
			};
		}
		
		@Override
		public int invoke() throws EffectorException {
			Transition t = new Transition(
				(int)fields[0].asInteger(),
				(int)fields[1].asInteger(),
				(int)fields[2].asInteger(),
				fields[4].copyValue()
			);
			if (t.isFinal) {
				return IEffector.RTE_EFFECT_NONE;
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
			return IEffector.RTE_EFFECT_NONE;
		}
	}

	public class AutomatonEffector extends BaseEffector<ModelCompiler> {		
		public AutomatonEffector(ModelCompiler target) {
			super(target, Bytes.encode("automaton"));
		}

		@Override
		public int invoke() throws EffectorException {
			final Integer[] inrInputStates = target.getInrStates(0);
			if (inrInputStates == null) {
				String msg = "Empty automaton " + target.getTransducerName();
				Model.rtcLogger.log(Level.SEVERE, msg);
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
			target.effectorVectorList = new ArrayList<Integer>(8196);
			target.effectorVectorList.add(Transductor.RTE_EFFECTOR_NUL);
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
									vectorOrdinal = target.effectorVectorList.size();
									for (final int element : effectVector) {
										target.effectorVectorList.add(element);
									}
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
				target.effectorVectorList.trimToSize();
				target.factor(transitionMatrix);
			}
			
			return IEffector.RTE_EFFECT_NONE;
		}
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public IEffector<?>[] bindEffectors() throws TargetBindingException {
		return new IEffector<?>[] {
			new HeaderEffector(this),
			new TransitionEffector(this),
			new AutomatonEffector(this)
		};
	}

	protected void setTransductor(ITransductor transductor) {
		this.transductor = transductor;
	}
	
	private void reset() {
		this.transducerName = null;
		this.stateMaps = null;
		this.stateTransitionMap = null;
		this.effectorVectorMap = null;
		this.effectorVectorList = null;
		this.inputEquivalenceIndex = null;
		this.kernelMatrix = null;
		this.header = null;
		this.transitions = null;
		this.transition = 0;
		this.errors.clear();
	}
	
	@SuppressWarnings("unchecked")
	protected boolean compile(File inrFile) throws ModelException {
		this.reset();
		String name = inrFile.getName();
		name = name.substring(0, name.length() - Base.AUTOMATON_FILE_SUFFIX.length());
		this.transducerName = Bytes.encode(name);
		int size = (int)inrFile.length();
		byte bytes[] = null;
		try (
			FileInputStream s = new FileInputStream(inrFile);
			DataInputStream f = new DataInputStream(s);
		) {
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
			this.transductor.stop();
			this.transductor.input(bytes, size);
			this.transductor.signal(Signal.nil);
			Status status = this.transductor.start(Bytes.encode("Automaton"));
			while (status == Status.RUNNABLE) {
				status = this.transductor.run();
			}
			this.transductor.stop();
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
		} catch (AssertionError e) {
			throw e;
		} finally {
			this.transductor.stop();
		}
	}

	private void factor(final int[][][] transitionMatrix) {
		final HashMap<IntsArray, HashSet<Integer>> rowEquivalenceMap = 
			new HashMap<IntsArray, HashSet<Integer>>((5 * transitionMatrix.length) >> 2);
		for (int i = 0; i < transitionMatrix.length; i++) {
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
	}

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
		int effectIndex = 0;
		final int[] effectorVector = new int[this.effectorVectorList.size()];
		for (final int effect : this.effectorVectorList) {
			effectorVector[effectIndex++] = effect;
		}
		this.model.putString(this.getTransducerName());
		this.model.putString(this.getName());
		this.model.putIntArray(this.inputEquivalenceIndex);
		this.model.putTransitionMatrix(this.kernelMatrix);
		this.model.putIntArray(effectorVector);
		int transitions = 0;
		for (final int[][] row : this.kernelMatrix) {
			for (final int[] col : row) {
				if (col[1] != 0) {
					transitions++;
				}
			}
		}
		double density = (double)(transitions)/(double)(this.kernelMatrix.length * this.kernelMatrix[0].length);
		System.out.println(String.format("%1$s: %2$d input equivalence classes, %3$d states, %4$d transitions (%5$5.3f)",
			this.getTransducerName(), this.kernelMatrix.length, this.kernelMatrix[0].length, transitions, density));
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
				this.getTransducerName(), Bytes.decode(bytes, bytes.length), type));
		}
	}
	
	private void compileEffectorToken(byte[] bytes) {
		assert (bytes.length > 0);
		if (0 > this.model.getEffectorOrdinal(new Bytes(bytes))) {
			this.error(String.format("%1$s: Unknown effector token '%2$s' on tape 1",
				this.getTransducerName(), Bytes.decode(bytes, bytes.length)));
		}
	}

	private void compileParameterToken(byte[] bytes) {
		assert (bytes.length > 0);
		if (bytes.length > 1) {
			switch (bytes[0]) {
			case Base.TYPE_REFERENCE_TRANSDUCER:
				this.model.addTransducer(Bytes.getBytes(bytes, 1, bytes.length));
				break;
			case Base.TYPE_REFERENCE_VALUE:
				this.model.addNamedValue(Bytes.getBytes(bytes, 1, bytes.length));
				break;
			case Base.TYPE_REFERENCE_SIGNAL:
				this.model.addSignal(Bytes.getBytes(bytes, 1, bytes.length));
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
