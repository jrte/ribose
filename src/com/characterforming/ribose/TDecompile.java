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
 * format in the input equivalence class index. The {@code nil} effector is represented as {@code 0}.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TDecompile usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TDecompile <i>model transducer</i></td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the model file containing the transducer.</td></tr>
 * <tr><td style="text-align:right"><i>transducer</i></td><td>The name of the transducer to decompile.</td></tr>
 * </table>
 * <hr>
 * <pre>
 * LinuxKernel
 * 
 * Input equivalents (equivalent: input...)
 * 
 *    0: k
 *    1: ^100
 *    2: ^102 ^103 ^104 ^105 ^106 ^107 ^108
 *    3: R
 *    4: 0 1 2 3 4 5 6 7 8 9
 *    5: .
 *    6: S
 *    7: N
 *    8: E
 *    9: ^0 ^1 ^2 ^3 ^4 ^5 ^6 ^7 ^8 ^9 ^b ^c ^d ^e ^f ^10 ^11 ^12 ^13 ^14 ^15 ^16 ^17 ^18 ^19 ^1a ^1b ^1c ^1d ^1e ^1f ^7f ^80 ... ^ff
 *   10: I
 *   11: =
 *   12: L
 *   13: ^a
 *   14: F G H J K Q V W X Y Z
 *   15: T
 *   16: P
 *   17: U
 *   18: D
 *   19: :
 *   20: O
 *   21: A
 *   22: ]
 *   23: ! " # $ % &amp; ' ( ) * + , - / ;  &lt; &gt; ? @ \ ^ _ ` { | } ~
 *   24: a b c d f g h i j m o p q s t u v w x y z
 *   25: ^20
 *   26: r
 *   27: ^101
 *   28: B
 *   29: C
 *   30: l
 *   31: n
 *   32: e
 *   33: [
 *   34: M
 * 
 * State transitions (from equivalent -&gt; to effect...)
 * 
 *   0  27 -&gt;   1 clear[0] select[6]
 *   1   1 -&gt;   2 1
 *   1   3 -&gt;   3 paste
 *   1   6 -&gt;   3 paste
 *   1   7 -&gt;   3 paste
 *   1   8 -&gt;   3 paste
 *   1  10 -&gt;   3 paste
 *   1  12 -&gt;   3 paste
 *   1  14 -&gt;   3 paste
 *   1  15 -&gt;   3 paste
 *   1  16 -&gt;   3 paste
 *   1  17 -&gt;   3 paste
 *   1  18 -&gt;   3 paste
 *   1  20 -&gt;   3 paste
 *   1  21 -&gt;   3 paste
 *   1  28 -&gt;   3 paste
 *   1  29 -&gt;   3 paste
 *   1  34 -&gt;   3 paste
 *   2   0 -&gt;   2 msum[1]
 *   2   3 -&gt;   2 msum[1]
 *   2   4 -&gt;   2 msum[1]
 *   2   5 -&gt;   2 msum[1]
 *   2   6 -&gt;   2 msum[1]
 *   2   7 -&gt;   2 msum[1]
 *   2   8 -&gt;   2 msum[1]
 *   2   9 -&gt;   2 msum[1]
 *   2  10 -&gt;   2 msum[1]
 *   2  11 -&gt;   2 msum[1]
 *   2  12 -&gt;   2 msum[1]
 *   2  13 -&gt;   1 clear[0] select[6]
 *   2  14 -&gt;   2 msum[1]
 *   2  15 -&gt;   2 msum[1]
 *   2  16 -&gt;   2 msum[1]
 *   2  17 -&gt;   2 msum[1]
 *   2  18 -&gt;   2 msum[1]
 *   2  19 -&gt;   2 msum[1]
 *   2  20 -&gt;   2 msum[1]
 *   2  21 -&gt;   2 msum[1]
 *   2  22 -&gt;   2 msum[1]
 *   2  23 -&gt;   2 msum[1]
 *   2  24 -&gt;   2 msum[1]
 *   2  25 -&gt;   2 msum[1]
 *   2  26 -&gt;   2 msum[1]
 *   2  28 -&gt;   2 msum[1]
 *   2  29 -&gt;   2 msum[1]
 *   2  30 -&gt;   2 msum[1]
 *   2  31 -&gt;   2 msum[1]
 *   2  32 -&gt;   2 msum[1]
 *   2  33 -&gt;   2 msum[1]
 *   2  34 -&gt;   2 msum[1]
 *   3   0 -&gt;   4 paste
 *   3   1 -&gt;   2 1
 *   3  24 -&gt;   4 paste
 *   3  26 -&gt;   4 paste
 *   3  30 -&gt;   4 paste
 *   3  31 -&gt;   4 paste
 *   3  32 -&gt;   4 paste
 * </pre>
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
