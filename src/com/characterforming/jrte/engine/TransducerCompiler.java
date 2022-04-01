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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.characterforming.jrte.CompilationException;
import com.characterforming.jrte.ModelException;
import com.characterforming.jrte.base.Bytes;

public final class TransducerCompiler extends Automaton {
	private final HashMap<Ints, Integer> effectorVectorMap;
	private final ArrayList<Integer> effectorVectorList;
	private int[][][] kernelMatrix;
	private int[] inputEquivalenceIndex;

	public TransducerCompiler(final Bytes name, final RuntimeModel model) {
		super(name, model);
		this.effectorVectorMap = new HashMap<Ints, Integer>(1024);
		this.effectorVectorList = new ArrayList<Integer>(8196);
		this.inputEquivalenceIndex = null;
	}

	@Override
	public Bytes load(final File inrfile) throws IOException, CompilationException {
		Bytes inrVersion = super.load(inrfile);
		if (super.getErrors().size() > 0) {
			throw new CompilationException("Unable to load " + super.getName());
		}

		final Integer[] inrInputStates = super.getInrStates(0);

		final int[][][] transitionMatrix = new int[this.model.getSignalLimit()][inrInputStates.length][2];
		for (int i = 0; i < transitionMatrix.length; i++) {
			for (int j = 0; j < inrInputStates.length; j++) {
				transitionMatrix[i][j][0] = j;
				transitionMatrix[i][j][1] = 0;
			}
		}

		this.effectorVectorList.add(0);
		for (final Integer inrInputState : inrInputStates) {
			for (final Transition t : super.getInrTransitions(inrInputState)) {
				if (!t.isFinal()) {
					if (t.getTape() == 0) {
						final int rteState = super.getRteState(0, t.getInS());
						final int inputOrdinal = this.model.getInputOrdinal(t.getBytes());
						final Chain chain = this.chain(t);
						if (chain != null) {
							final int[] effectVector = chain.getEffectVector();
							transitionMatrix[inputOrdinal][rteState][0] = super.getRteState(0, chain.getOutS());
							if (chain.isEmpty()) {
								transitionMatrix[inputOrdinal][rteState][1] = 1;
							} else if (chain.isScalar()) {
								transitionMatrix[inputOrdinal][rteState][1] = effectVector[0];
							} else {
								Ints vector = new Ints(effectVector);
								Integer vectorOrdinal = this.effectorVectorMap.get(vector);
								if (vectorOrdinal == null) {
									vectorOrdinal = this.effectorVectorList.size();
									for (final int element : effectVector) {
										this.effectorVectorList.add(element);
									}
									this.effectorVectorMap.put(vector, vectorOrdinal);
								}
								transitionMatrix[inputOrdinal][rteState][1] = -vectorOrdinal;
							}
						}
					} else {
						super.error(String.format("Ambiguous state %1$d", t.getInS()));
					} 
				}
			}
		}

		this.factor(transitionMatrix);

		if (super.getErrors().size() > 0) {
			throw new CompilationException(String.format("Unable to load %1$s, call Automaton.getErrors() to view problems", super.getName()));
		}
		return inrVersion;
	}

	void save(final RuntimeModel model, final String targetName) throws ModelException {
		int effectIndex = 0;
		final int[] effectorVector = new int[this.effectorVectorList.size()];
		for (final int effect : this.effectorVectorList) {
			effectorVector[effectIndex++] = effect;
		}
		model.putString(super.getName());
		model.putString(targetName);
		model.putIntArray(this.inputEquivalenceIndex);
		model.putTransitionMatrix(this.kernelMatrix);
		model.putIntArray(effectorVector);
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
		assert transition.isValid() : "Invalid transition for chain(Transition) : " + transition.toString();
		assert transition.getTape() != 1 && transition.getTape() != 2 : "Invalid tape number for chain(Transition) : " + transition.toString();
		if (transition.isFinal()) {
			return null;
		}
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
						assert((effectorPos > 0) && (effectorOrdinal == effectorVector[effectorPos - 1])); 
						effectorVector[effectorPos - 1] *= -1;
						final byte[][] parameters = Arrays.copyOf(parameterList, parameterPos);
						int parameterOrdinal = this.model.compileParameters(effectorOrdinal, parameters);
						effectorVector[effectorPos] = parameterOrdinal;
						parameterList = new byte[8][];
						parameterPos = 0;
						++effectorPos;
					}
					effectorOrdinal = this.model.getEffectorOrdinal(new Bytes(t.getBytes()));
					effectorVector[effectorPos] = effectorOrdinal;
					++effectorPos;
					break;
				case 2:
					if (parameterPos >= parameterList.length) {
						parameterList = Arrays.copyOf(parameterList, (parameterList.length * 3) >> 1);
					}
					parameterList[parameterPos] = t.getBytes();
					++parameterPos;
					break;
				default:
					super.error(String.format("Invalid tape number in transducer : %1$s", t.toString()));
					break;
			}
			outT = super.getInrTransitions(t.getOutS());
		}
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
			if (errors == 0) {
				return new Chain(Arrays.copyOf(effectorVector, effectorPos), outS);
			}
		} else {
			for (final Transition t : outT) {
				super.error(String.format("Ambiguous state %1$d", t.getInS()));
			}
		}
		return null;
	}
}
