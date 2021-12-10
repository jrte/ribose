/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.characterforming.jrte.CompilationException;
import com.characterforming.jrte.GearboxException;
import com.characterforming.jrte.compile.array.Ints;
import com.characterforming.jrte.compile.array.IntsArray;
import com.characterforming.jrte.engine.BaseNamedValueEffector;
import com.characterforming.jrte.engine.Gearbox;
import com.characterforming.jrte.engine.Transduction;

public final class TransducerCompiler extends Automaton {
	private final Gearbox gearbox;
	private final TargetCompiler targetCompiler;
	private final HashMap<Ints, Integer> effectorVectorMap;
	private final ArrayList<Integer> effectorVectorList;
	private int[][][] kernelMatrix;
	private int[] inputEquivalenceIndex;

	public TransducerCompiler(final String name, final Charset charset, final Gearbox gearbox, final TargetCompiler targetCompiler) {
		super(name, charset);
		this.gearbox = gearbox;
		this.targetCompiler = targetCompiler;
		this.effectorVectorMap = new HashMap<Ints, Integer>(1024);
		this.effectorVectorList = new ArrayList<Integer>(8196);
		this.inputEquivalenceIndex = null;
	}

	@Override
	public void load(final File inrfile) throws IOException, CompilationException {
		super.load(inrfile);
		if (super.getErrors().size() > 0) {
			throw new CompilationException(String.format("Unable to load %1$s, call Automaton.getErrors() to view problems", super.getName()));
		}

		final byte[][] inputBytes = super.getSymbolBytes(0);
		for (final byte[] inputByte : inputBytes) {
			final char[] inputChars = super.getChars(inputByte);
			if (inputChars.length > 1) {
				this.gearbox.putSignalOrdinal(new String(inputChars));
			}
		}

		final Integer[] inrInputStates = super.getInrStates(0);

		final int[][][] transitionMatrix = new int[this.gearbox.getSignalLimit()][inrInputStates.length][2];
		for (int i = 0; i < transitionMatrix.length; i++) {
			for (int j = 0; j < inrInputStates.length; j++) {
				transitionMatrix[i][j][0] = j;
				transitionMatrix[i][j][1] = 0;
			}
		}

		this.effectorVectorList.add(0);
		for (final Integer inrInputState : inrInputStates) {
			for (final Transition t : super.getInrTransitions(inrInputState)) {
				if (t.getTape() == 0) {
					final int rteState = super.getRteState(0, t.getInS());
					final int inputOrdinal = this.gearbox.putInputOrdinal(t.getString());
					final Chain chain = this.chain(t);
					if (chain != null) {
						final int[] effectVector = chain.getEffectVector();
						transitionMatrix[inputOrdinal][rteState][0] = super.getRteState(0, chain.getOutS());
						if (chain.isEmpty()) {
							transitionMatrix[inputOrdinal][rteState][1] = 1;
						} else if (chain.isScalar()) {
							transitionMatrix[inputOrdinal][rteState][1] = effectVector[0];
						} else {
							final Ints vector = new Ints(effectVector);
							Integer v = this.effectorVectorMap.get(vector);
							if (v == null) {
								v = this.effectorVectorList.size();
								for (final int element : effectVector) {
									this.effectorVectorList.add(element);
								}
								this.effectorVectorMap.put(vector,  v);
							}
							transitionMatrix[inputOrdinal][rteState][1] = -v;
						}
					}
				} else if (!t.isFinal()) {
					super.error(String.format("Ambiguous state %1$d", t.getInS()));
				}
			}
		}

		this.factor(transitionMatrix);

		if (super.getErrors().size() > 0) {
			throw new CompilationException(String.format("Unable to load %1$s, call Automaton.getErrors() to view problems", super.getName()));
		}
	}

	public void save(final Gearbox gearbox, final String targetName) throws GearboxException {
		int effectIndex = 0;
		final int[] effectorVector = new int[this.effectorVectorList.size()];
		for (final int effect : this.effectorVectorList) {
			effectorVector[effectIndex++] = effect;
		}
		gearbox.putString(super.getName());
		gearbox.putString(targetName);
		gearbox.putIntArray(this.inputEquivalenceIndex);
		gearbox.putTransitionMatrix(this.kernelMatrix);
		gearbox.putIntArray(effectorVector);
		int transitions = 0;
		for (final int[][] row : this.kernelMatrix) {
			for (final int[] col : row) {
				if (col[1] != 0) {
					transitions++;
				}
			}
		}
		System.out.println(String.format("Transducer %1$s: %2$d input equivalence classes, %3$d states, %4$d transitions",
				super.getName(), this.kernelMatrix.length, this.kernelMatrix[0].length, transitions));
	}

	private void factor(final int[][][] transitionMatrix) {
		final HashMap<IntsArray, HashSet<Integer>> rowEquivalenceMap = new HashMap<IntsArray, HashSet<Integer>>(transitionMatrix.length);
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
		assert (transition.getTape() == 0) : String.format("Invalid tape number for chain(Transition) : %1$d)", transition.getTape());
		int effectorOrdinal = -1;
		int effectorPos = 0;
		int[] effectorVector = new int[8];
		int parameterPos = 0;
		byte[][] parameterList = new byte[8][];
		ArrayList<Transition> outT = super.getInrTransitions(transition.getOutS());
		while (outT != null && outT.size() == 1 && outT.get(0).getTape() > 0) {
			final Transition t = outT.get(0);
			switch (t.getTape()) {
				case 1:
					if ((effectorPos + 2) >= effectorVector.length) {
						effectorVector = Arrays.copyOf(effectorVector, (effectorVector.length * 3) >> 1);
					}
					if (parameterPos > 0) {
						assert((effectorPos >= 0) && (effectorOrdinal == effectorVector[effectorPos - 1])); 
						final byte[][] parameters = Arrays.copyOf(parameterList, parameterPos);
						effectorVector[effectorPos - 1] *= -1;
						effectorVector[effectorPos++] = this.targetCompiler.getParametersIndex(effectorOrdinal, parameters);
						parameterList = new byte[8][];
						parameterPos = 0;
					}
					effectorOrdinal = this.targetCompiler.getEffectorOrdinal(t.getString());
					effectorVector[effectorPos++] = effectorOrdinal;
					break;
				case 2:
					if (parameterPos >= parameterList.length) {
						parameterList = Arrays.copyOf(parameterList, (parameterList.length * 3) >> 1);
					}
					parameterList[parameterPos++] = compileParameterTransition(effectorOrdinal, t.getBytes());
					break;
				default:
					super.error(String.format("Invalid tape number in transducer : %1$s", t.toString()));
					break;
			}
			outT = super.getInrTransitions(t.getOutS());
		}
		if ((effectorPos + 2) >= effectorVector.length) {
			effectorVector = Arrays.copyOf(effectorVector, effectorPos + 2);
		}
		if (parameterPos > 0) {
			final int parameterizedEffectorOrdinal = effectorVector[effectorPos - 1];
			final byte[][] parameters = Arrays.copyOf(parameterList, parameterPos);
			effectorVector[effectorPos - 1] *= -1;
			effectorVector[effectorPos++] = this.targetCompiler.getParametersIndex(parameterizedEffectorOrdinal, parameters);
		}
		if (effectorPos > 1) {
			effectorVector[effectorPos++] = 0;
		}
		if (outT == null || outT.size() == 0 || outT.size() == 1 && outT.get(0).isFinal()) {
			return new Chain(Arrays.copyOf(effectorVector, effectorPos), 0);
		} else if (outT.size() == 1 && outT.get(0).getTape() == 0) {
			return new Chain(Arrays.copyOf(effectorVector, effectorPos), outT.get(0).getInS());
		} else if (outT.get(0).isFinal() || outT.get(0).getTape() == 0) {
			int outS = -1, errors = 0;
			for (final Transition t : outT) {
				if (t.getTape() > 0) {
					super.error(String.format("Ambiguous state %1$d", t.getInS()));
					++errors;
				} else {
					outS = t.getInS();
				}
			}
			return errors == 0 ? new Chain(Arrays.copyOf(effectorVector, effectorPos), outS) : null;
		} else {
			for (final Transition t : outT) {
				super.error(String.format("Ambiguous state %1$d", t.getInS()));
			}
			return null;
		}
	}

	public byte[] compileParameterTransition(int effectorOrdinal, byte[] bytes) {
		if (this.targetCompiler.getEffector(effectorOrdinal) instanceof BaseNamedValueEffector) {
			if ((bytes.length == 1)&& (bytes[0]== Transduction.ANONYMOUS_VALUE_COMPILER[0][0])) {
				return Transduction.ANONYMOUS_VALUE_RUNTIME[0]; 
			}
		}
		return bytes;
	}
}
