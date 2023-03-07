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
  private final Model model;
  private final CharsetDecoder decoder;
  private final CharsetEncoder encoder;

  public ModelDecompiler(File modelFile) throws ModelException {
    this.model = new Model(modelFile);
    this.model.load();
    this.decoder = Base.newCharsetDecoder();
    this.encoder = Base.newCharsetEncoder();
  }

  public void decompile(final String transducerName) throws ModelException {
    Transducer trex = this.model.loadTransducer(this.model.getTransducerOrdinal(Bytes.encode(encoder, transducerName)));
    int mproductOrdinal = this.model.getEffectorOrdinal(Bytes.encode(this.encoder, "mproduct"));
    
    int[] effectorVectors = trex.getEffectorVector();
    int[] inputEquivalenceIndex = trex.getInputFilter();
    int[][] transitionMatrix = trex.getTransitionMatrix();
    int inputEquivalentCount = trex.getInputEquivalentsCount();
    Set<Map.Entry<Bytes, Integer>> effectorOrdinalMap = this.model.getEffectorOrdinalMap().entrySet();
    String[] effectorNames = new String[effectorOrdinalMap.size()];
    for (Map.Entry<Bytes, Integer> entry : effectorOrdinalMap) {
      effectorNames[(int)entry.getValue()] = Bytes.decode(this.decoder, entry.getKey().getBytes(), entry.getKey().getLength()).toString();
    }
    System.out.printf("%s\n\nInput equivalents (equivalent: input...)\n\n", transducerName);
    for (int i = 0; i < inputEquivalentCount; i++) {
      System.out.printf("%4d:", i);
      for (int j = 0; j < inputEquivalenceIndex.length; j++) {
        if (inputEquivalenceIndex[j] == i) {
          if (j > 32 && j <127) {
            System.out.printf(" %c", (char)j);
          } else {
            System.out.printf(" ^%x", j);
          }
        }
      }
      System.out.printf("\n");
    }
    System.out.printf("\nState transitions (from equivalent -> to effect...)\n\n");
    for (int i = 0; i < transitionMatrix.length; i++) {
      int from = i / inputEquivalentCount;
      int equivalent = i % inputEquivalentCount;
      int to = transitionMatrix[i][0] / inputEquivalentCount;
      int effect = transitionMatrix[i][1];
      assert (effect != 0) || (to == from);
      if ((to != from) || (effect != 0)) {
        System.out.printf("%3d %3d -> %3d", from, equivalent, to);
        if (effect >= 0) {
          System.out.printf(" %s", effectorNames[effect]);
        } else {
          for (int e = (-1 * effect); effectorVectors[e] != 0; e++) {
            if (effectorVectors[e] > 0) {
              System.out.printf(" %s", effectorNames[effectorVectors[e]]);
            } else {
              int effector = -1 * effectorVectors[e++];
              System.out.printf(" %s[", effectorNames[effector]);
              if (effector == mproductOrdinal) {
                byte[] product = this.model.getProductParameter(effectorVectors[e]);
                for (int j = 0; j < product.length; j++) {
                  if (32 < product[j] && 127 > product[j]) {
                    System.out.printf(" %c", (char)product[j]);
                  } else {
                    System.out.printf(" ^%d", Byte.toUnsignedInt(product[j]));
                  }
                }
                System.out.printf(" ]");
              } else {
                System.out.printf("%d]", effectorVectors[e]);
              }
            }
          }
        }
        System.out.printf("\n");
      }
    }
  }
}