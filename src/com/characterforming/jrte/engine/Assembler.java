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

import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import com.characterforming.jrte.engine.Model.Argument;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.Signal;

final class Assembler {
	static final int NUL = 0;
	static final int NIL = 1;

	record Assembly(int[] inputEquivalents, int[][][] transitions, int[] effects) {
		@Override
		public int hashCode() {
			return Arrays.hashCode(inputEquivalents) ^ Arrays.hashCode(transitions) ^ Arrays.hashCode(effects);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Assembly a
			&& Arrays.equals(inputEquivalents, a.inputEquivalents)
			&& Arrays.equals(transitions, a.transitions)
			&& Arrays.equals(effects, a.effects);
		}

		@Override
		public String toString() {
			return String.format("Assembly@%d: %d input equivalents; %d states; %d effects",
				hashCode(), inputEquivalents.length, transitions.length, effects.length);
		}
	}

	private record Fst(int[] inputEqIndex, HashSet<Integer>[] inputEqSets, State[] states, int[][][] matrix) {
		@Override
		public int hashCode() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean equals(Object other) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return String.format("Fst: %d inputs; %d equivalents; %d states",
					inputEqIndex.length, inputEqSets.length, states.length);
		}
	}

	private record Product(int endstate, ArrayList<Integer> product) {
		@Override
		public int hashCode() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean equals(Object other) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return String.format("Product: %d length; %d endstate", product.size(), endstate);
		}
	}

	private final ModelCompiler compiler;
	private final boolean setTraps;
	private final int msumOrdinal;
	private final int mproductOrdinal;
	private final int mscanOrdinal;
	private HashSet<Integer>[] inputEquivalenceSets;
	private HashMap<Ints, Integer> effectVectorMap;
	private ArrayList<int[]> effectVectors;

	Assembler(ModelCompiler compiler)
	throws CharacterCodingException {
		this.compiler = compiler;
		this.msumOrdinal = this.compiler.getEffectorOrdinal(Codec.encode("msum"));
		this.mproductOrdinal = this.compiler.getEffectorOrdinal(Codec.encode("mproduct"));
		this.mscanOrdinal = this.compiler.getEffectorOrdinal(Codec.encode("mscan"));
		this.setTraps = ModelCompiler.MIN_SUM_SIZE >= 0 && ModelCompiler.MIN_PRODUCT_LENGTH >= 0;
		this.reset();
	}

	private void reset() {
		this.inputEquivalenceSets = null;
		this.effectVectorMap = null;
		this.effectVectors = null;
	}

	Assembly assemble(final int[][][] transitionMatrix, HashMap<Ints, Integer> effectorVectorMap) {
		this.reset();

		// compute byte|signal input equivalence classes and index
		Fst fst = this.reduceEquivalentInputs(transitionMatrix);
		this.inputEquivalenceSets = fst.inputEqSets();
		int nInputs = fst.inputEqSets.length;
		int nStates = fst.states().length;

		// construct incoming effect vector enumeration
		this.effectVectors = new ArrayList<>(effectorVectorMap.size());
		for (int i = 0; i < effectorVectorMap.size(); i++)
			this.effectVectors.add(null);
		for (Entry<Ints, Integer> entry : effectorVectorMap.entrySet())
			this.effectVectors.set(entry.getValue(), entry.getKey().getData());
		final boolean[] markedEffects = new boolean[nStates * nInputs];
		Arrays.fill(markedEffects, !this.setTraps);
		this.effectVectorMap = effectorVectorMap;
		markedEffects[0] = true;

		//	inject msum & mscan effectors
		if (this.setTraps) {
			for (State state : fst.states()) {
				for (int eq = 0; eq < nInputs; eq++) {
					int[] transition = fst.matrix()[state.ordinal][eq];
					if (state.isNotInstrumented()) {
						State nextState = fst.states()[transition[0]];
						if (nextState.isScanState()) {
							assert nextState.outboundByte >= 0 && nextState.outboundByte< Signal.NUL.signal();
							transition[1] = this.injectScanEffector(nextState.outboundByte,
								fst.matrix(), state, eq);
						} else if (nextState.isSumState()) {
							transition[1] = this.injectSumEffector(nextState.idempotentBytes,
								fst.matrix(), state, eq);
						} else if (nextState.isProductState()) {
							Product product = this.product(nextState, fst);
							if (product != null) {
								assert nextState.idempotentCount >= 255;
								transition[0] = product.endstate();
								transition[1] = this.injectProductEffector(product,
									fst.matrix(), state, eq);
							}
						}
						if (transition[1] < 0)
							markedEffects[-1 * transition[1]] = true;
					}
				}
			}

			// eliminate transitional states mapped to product vectors
			int[] retainedStateMap = new int[nStates];
			Arrays.fill(retainedStateMap, -1);
			boolean[] markedStates = new boolean[nStates];
			Arrays.fill(markedStates, false);
			final int marked = this.mark(fst.matrix(), markedStates);
			int retainedState = -1;
			for (int state = 0; state < fst.matrix().length; state++)
				if (markedStates[state]) {
					retainedStateMap[state] = ++retainedState;
					fst.matrix()[retainedState] = fst.matrix()[state];
					fst.states()[retainedState] = fst.states()[state];
				}
			nStates = ++retainedState;
			assert marked == nStates;
			for (int state = 0; state < nStates; state++) {
				int[][] transitions = fst.matrix()[state];
				for (int eq = 0; eq < nInputs; eq++) {
					assert retainedStateMap[transitions[eq][0]] >= 0
					&& retainedStateMap[transitions[eq][0]] < nStates;
					transitions[eq][0] = retainedStateMap[transitions[eq][0]];
				}
			}
		}

		// lay out effect vectors and construct vector offset index
		int size = 0, offset = 0;
		for (int v = 0; v < this.effectVectors.size(); v++)
			if (markedEffects[v])
				size += this.effectVectors.get(v).length;
		int[] effectVectorArray = new int[size];
		int[] vectorOffsetMap = new int[this.effectVectors.size()];
		for (int v = 0; v < this.effectVectors.size(); v++)
			if (markedEffects[v]) {
				final int length = this.effectVectors.get(v).length;
				System.arraycopy(effectVectors.get(v), 0,
					effectVectorArray, offset, length);
				vectorOffsetMap[v] = offset;
				offset += length;
			}
		assert effectVectorArray.length > 0 && effectVectorArray[0] == NUL;
		assert effectVectorArray[effectVectorArray.length - 1] == NUL;

		// rewrite effect vector ordinals with offsets in kernel matrix
		for (int state = 0; state < nStates; state++)
			for (int eq = 0; eq < nInputs; eq++)
				if (fst.matrix()[state][eq][1] < 0) {
					assert -1 * fst.matrix()[state][eq][1] < vectorOffsetMap.length
						: String.format("state:%d; eq:%d; action:%d; length:%d",
							state, eq, fst.matrix()[state][eq][1], vectorOffsetMap.length);
					fst.matrix()[state][eq][1] =
						-1 * vectorOffsetMap[-1 * fst.matrix()[state][eq][1]];
				}

		// if not instrumenting traps return the unintrumewnted fst
		if (!this.setTraps)
			return new Assembly(fst.inputEqIndex(), fst.matrix(), effectVectorArray);

		// otherwise reduce kernel matrix and input equivalence modulo input product vectorization
		final int[][][] transposedMatrix = new int[nInputs][nStates][2];
		for (int eq = 0; eq < nInputs; eq++)
			for (int state = 0; state < nStates; state++)
				transposedMatrix[eq][state] = fst.matrix()[state][eq];
		Fst finalFst = this.reduceEquivalentInputs(transposedMatrix);
		final int[] finalEquivalents = new int[this.compiler.getSignalLimit()];
		for (int eq = 0; eq < finalFst.inputEqSets().length; eq++) {
			HashSet<Integer> inputs = new HashSet<>();
			for (int e : finalFst.inputEqSets()[eq])
				inputs.addAll(this.inputEquivalenceSets[e]);
			for (int token : inputs)
				finalEquivalents[token] = eq;
		}
		return new Assembly(finalEquivalents, finalFst.matrix(), effectVectorArray);
	}

	private Fst reduceEquivalentInputs(int[][][] transitionMatrix) {
		// factor matrix modulo input equivalence
		int[] index = new int[transitionMatrix.length];
		final HashMap<IntsArray, HashSet<Integer>> equivalenceSets = new HashMap<>((5 * index.length) >> 2);
		for (int token = 0; token < transitionMatrix.length; token++) {
			assert transitionMatrix[token].length == transitionMatrix[0].length;
			final IntsArray transitions = new IntsArray(transitionMatrix[token]);
			HashSet<Integer> equivalentInputOrdinals = equivalenceSets.computeIfAbsent(
				transitions, absent -> new HashSet<>(10));
			if (equivalentInputOrdinals.isEmpty())
				equivalenceSets.put(transitions, equivalentInputOrdinals);
			equivalentInputOrdinals.add(token);
		}

		// group equivalent inputs
		int equivalenceIndex = 0;
		final int nInputs = equivalenceSets.size();
		final HashSet<Integer>[] equiv = this.allocateHashSetArray(nInputs);
		for (HashSet<Integer> equivalents : equivalenceSets.values()) {
			for (int token : equivalents)
				index[token] = equivalenceIndex;
			equiv[equivalenceIndex++] = equivalents;
		}

		// construct transposed states x input groups transition matrix
		final int nStates = transitionMatrix[0].length;
		final int[][][] matrix = new int[nStates][nInputs][2];
		final State[] states = new State[nStates];
		for (int state = 0; state < nStates; state++) {
			for (int eq = 0; eq < nInputs; eq++)
				matrix[state][eq] = transitionMatrix[equiv[eq].iterator().next().intValue()][state];
			states[state] = new State(state, matrix[state], equiv, this.compiler.getSignalLimit());
		}

		return new Fst(index, equiv, states, matrix);
	}

	private int injectEffector(int action, int effector, int parameter) {
		if (action == NIL)
			return Transducer.action(effector, parameter);
		int[] key = null;
		if (action > 0x10000)
			key = new int[] {
				-1 * Transducer.action(action),
				Transducer.parameter(action),
				-1 * effector, parameter, 0 };
		else if (action < NUL) {
			key = this.effectVectors.get(-1 * action);
			key = Arrays.copyOf(key, key.length + 2);
			key[key.length - 3] = -1 * effector;
			key[key.length - 2] = parameter;
			key[key.length - 1] = 0;
		} else if (action > NUL)
			key = new int[] { action, -1 * effector, parameter, 0 };
		if (key != null) {
			action = -1 * this.effectVectorMap.computeIfAbsent(
				new Ints(key), absent -> this.effectVectorMap.size());
			if (this.effectVectors.size() < this.effectVectorMap.size())
				this.effectVectors.add(key);
		}
		return action;
	}

	private int injectScanEffector(int token, int[][][] matrix, State state, int eq) {
		byte[] scan = new byte[] { Token.escape(), (byte) (token & 0xff) };
		Argument argument = new Argument(-1, new BytesArray(new byte[][] { scan }));
		return this.injectEffector(matrix[state.ordinal][eq][1], mscanOrdinal,
			this.compiler.compileParameters(mscanOrdinal, argument));
	}

	private int injectSumEffector(long[] bitmap, int[][][] matrix, State state, int eq) {
		int n = 0;
		byte[] sum = new byte[256];
		sum[n++] = Token.escape();
		for (int word = 0; word < bitmap.length; word++)
			for (int bit = 0; bit < 64; bit++)
				if (0 != ((1L << bit) & bitmap[word]))
					sum[n++] = (byte) (64 * word + bit);
		sum = Arrays.copyOf(sum, n);
		Argument argument = new Argument(-1, new BytesArray(new byte[][] { sum }));
		return this.injectEffector(matrix[state.ordinal][eq][1], msumOrdinal,
			this.compiler.compileParameters(msumOrdinal, argument));
	}

	private int injectProductEffector(Product walkResult, int[][][] matrix, State state, int eq) {
		byte[] product = new byte[walkResult.product.size()];
		product[0] = Token.escape();
		for (int i = 1; i < product.length; i++)
			product[i] = (byte) (walkResult.product().get(i - 1).intValue() & 0xff);
		Argument argument = new Argument(-1, new BytesArray(new byte[][] { product }));
		return this.injectEffector(matrix[state.ordinal][eq][1], mproductOrdinal,
			this.compiler.compileParameters(mproductOrdinal, argument));
	}

	private Product product(State nextState, Fst fst) {
		ArrayList<Integer> walkedBytes = new ArrayList<>(32);
		ArrayList<Integer> walkedStates = new ArrayList<>(32);
		boolean[] markedStates = new boolean[fst.states().length];
		Arrays.fill(markedStates, false);
		int[] transition = nextState.transitions[fst.inputEqIndex[Signal.NUL.signal()]];
		int[] tx = new int[] { transition[0] != nextState.ordinal ? transition[0] : -1, transition[1] };
		int[] ty = new int[] { Integer.MIN_VALUE, Integer.MIN_VALUE };
		while (nextState.isProductState() && !markedStates[nextState.ordinal]) {
			assert nextState.outboundByte == (nextState.outboundByte & 0xff);
			transition = nextState.transitions[fst.inputEqIndex[Signal.NUL.signal()]];
			ty[0] = transition[0] != nextState.ordinal ? transition[0] : -1;
			ty[1] = transition[1];
			if ((tx[0] != ty[0]) || (tx[1] != ty[1]))
				break;
			walkedStates.add(nextState.ordinal);
			markedStates[nextState.ordinal] = true;
			walkedBytes.add(nextState.outboundByte);
			int eq = fst.inputEqIndex()[nextState.outboundByte];
			nextState = fst.states[fst.matrix[nextState.ordinal][eq][0]];
		}
		return walkedBytes.size() >= ModelCompiler.MIN_PRODUCT_LENGTH
		? new Product(walkedStates.get(walkedStates.size() - 2), walkedBytes)
		: null;
	}

	private int mark(int[][][] matrix, boolean[] markedStates) {
		Arrays.fill(markedStates, false);
		final StateStack stack = new StateStack(matrix.length);
		stack.push(0);
		int marked = 0;
		while (!stack.isEmpty()) {
			int state = stack.pop();
			if (!markedStates[state]) {
				markedStates[state] = true;
				for (int[] transition : matrix[state])
					stack.push(transition[0]);
				++marked;
			}
		}
		return marked;
	}

	@SuppressWarnings("unchecked")
	private HashSet<Integer>[] allocateHashSetArray(int size) {
		return (HashSet<Integer>[])new HashSet<?>[size];
	}
}
