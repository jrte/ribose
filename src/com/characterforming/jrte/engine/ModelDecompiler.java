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
 * You should have received a copy of the GNU General Public License
 * along with this program (LICENSE-gpl-3.0). If not, see
 * <http://www.gnu.org/licenses/#GPL>.
 */

package com.characterforming.jrte.engine;

import java.io.File;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Map;
import java.util.Set;

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.ModelException;

public class ModelDecompiler {
	private final File modelFile;
	private final CharsetDecoder decoder;
	private final CharsetEncoder encoder;

	public ModelDecompiler(File modelFile) {
		this.modelFile = modelFile;
		this.decoder = Base.newCharsetDecoder();
		this.encoder = Base.newCharsetEncoder();
	}

	public void decompile(final String transducerName) throws ModelException {
		try (Model model = Model.load(this.modelFile)) {
			Transducer trex = model.loadTransducer(model.getTransducerOrdinal(Bytes.encode(encoder, transducerName)));
			int[] effectorVectors = trex.getEffectorVector();
			int[] inputEquivalenceIndex = trex.getInputFilter();
			long[] transitionMatrix = trex.getTransitionMatrix();
			int inputEquivalentCount = trex.getInputEquivalentsCount();
			Set<Map.Entry<Bytes, Integer>> effectorOrdinalMap = model.getEffectorOrdinalMap().entrySet();
			String[] effectorNames = new String[effectorOrdinalMap.size()];
			for (Map.Entry<Bytes, Integer> entry : effectorOrdinalMap) {
				effectorNames[entry.getValue()] = Bytes.decode(this.decoder, entry.getKey().getData(), entry.getKey().getLength()).toString();
			}
			System.out.printf("%s%n%nInput equivalents (equivalent: input...)%n%n", transducerName);
			for (int i = 0; i < inputEquivalentCount; i++) {
				int startToken = -1;
				System.out.printf("%4d:", i);
				for (int j = 0; j < inputEquivalenceIndex.length; j++) {
					if (inputEquivalenceIndex[j] != i) {
						if (startToken >= 0) {
							if (startToken < (j-2)) {
								this.printStart(startToken);
								this.printEnd(j-1);
							} else {
								this.printStart(startToken);
							}
						}
						startToken = -1;
					} else if (startToken < 0) {
						startToken = j;
					}
				}
				if (startToken >= 0) {
					int endToken = inputEquivalenceIndex.length - 1;
					this.printStart(startToken);
					if (startToken < endToken) {
						this.printEnd(endToken);
					}
				}
				System.out.printf("%n");
			}
			System.out.printf("%nState transitions (from equivalent -> to effect...)%n%n");
			for (int i = 0; i < transitionMatrix.length; i++) {
				int from = i / inputEquivalentCount;
				int equivalent = i % inputEquivalentCount;
				int to = Transducer.state(transitionMatrix[i]) / inputEquivalentCount;
				int effect = Transducer.action(transitionMatrix[i]);
				assert (effect != 0) || (to == from);
				if ((to != from) || (effect != 0)) {
					System.out.printf("%1$d %2$d -> %3$d", from, equivalent, to);
					if (effect >= 0x10000) {
						int effector = Transducer.effector(effect);
						int parameter = Transducer.parameter(effect);
						System.out.printf(" %s[", effectorNames[effector]);
						System.out.printf(" %s ]", model.showParameter(effector, parameter));
					} else if (effect >= 0) {
						if (effect > 1) {
							System.out.printf(" %s", effectorNames[effect]);
						}
					} else {
						for (int e = (-1 * effect); effectorVectors[e] != 0; e++) {
							if (effectorVectors[e] > 0) {
								System.out.printf(" %s", effectorNames[effectorVectors[e]]);
							} else {
								int effector = -1 * effectorVectors[e++];
								System.out.printf(" %s[", effectorNames[effector]);
								System.out.printf(" %s ]", model.showParameter(effector, effectorVectors[e]));
							}
						}
					}
					System.out.printf("%n");
				}
			}
		}
	}

	private void printStart(int startByte) {
		if (startByte > 32 && startByte <127) {
			System.out.printf(" %c", (char)startByte);
		} else {
			System.out.printf(" #%x", startByte);
		}
	}

	private void printEnd(int endByte) {
		if (endByte > 32 && endByte <127) {
			System.out.printf("-%c", (char)endByte);
		} else {
			System.out.printf("-#%x", endByte);
		}
	}
}