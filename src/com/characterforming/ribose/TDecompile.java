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

 package com.characterforming.ribose;

import java.io.File;

import com.characterforming.jrte.engine.ModelDecompiler;
import com.characterforming.ribose.base.ModelException;

/**
 * Provides a {@link TDecompile#main(String[])} method to decompile a transducer from a
 * ribose model to {@code System.out}. This enumerates and lists the members of the input
 * equivalence classes and lists the nonnull transitions in the kernel matrix of the 
 * transducer.
 * 
 * Space, 7-bit control and 8-bit byte and signal ordinal inputs are presented in {@code ^hex}
 * format in the input equivalence class index. The {@code nil} effector is represented as 
 * {@code 1} and effector parameters are represented by parameter ordinals in kernel transitions. 
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TDecompile usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TDecompile <i>model transducer</i></td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the model file containing the transducer.</td></tr>
 * <tr><td style="text-align:right"><i>transducer</i></td><td>The name of the transducer to decompile.</td></tr>
 * </table>
 * <hr>
 * <pre>
 * $ java -ea -cp jars/ribose-0.0.1.jar com.characterforming.ribose.TDecompile build/Test.model Fibonacci
 * 
 * Fibonacci
 * 
 * Input equivalents (equivalent: input...)
 * 
 *    0: ^a
 *    1: ^101
 *    2: ^0-^9 ^b-/ 1-^100 ^102 ^104-^109
 *    3: ^103
 *    4: 0
 * 
 * State transitions (from equivalent -&gt; to effect...)
 * 
 *   0   1 -&gt;   1 clear[0]
 *   0   3 -&gt;   0 stop
 *   1   0 -&gt;   0 paste out signal[4]
 *   1   4 -&gt;   2 select[3] paste[12]
 *   2   0 -&gt;   0 paste out signal[4]
 *   2   4 -&gt;   2 select[4] cut[0] select[5] copy[0] select[3] cut[1]</pre>
 */
public class TDecompile {
  public static void main(final String[] args) throws ModelException {
    if (args.length != 2) {
      System.out.println("Usage: requires two arguments - (1) path to model file (2) name of transducer to decompile");
      System.exit(1);
    }
    File modelFile = new File(args[0]);
    if (!modelFile.exists()) {
      System.out.printf("Invalid model path: %s\n", args[0]);
      System.exit(1);
    }
    String transducerName = args[1];
    ModelDecompiler decompiler = new ModelDecompiler(modelFile);
    decompiler.decompile(transducerName);
  }
}
