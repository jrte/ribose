/*
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

/**
 * Main package provides the transduction factory class Jrte and the interfaces that 
 * define the application integration points.
 * <p>
 * <img alt="Jrte" src="{@docRoot}/resources/Jrte.png">
 * <p>
 * Client applications use the {@link com.characterforming.jrte.Jrte#Jrte(File, ITarget)} 
 * constructor to load a Gearbox compiled from ginr automata. The Gearbox instantiates 
 * a proxy instance of an {@link com.characterforming.jrte.ITarget} implementation 
 * class to verify that the target class and its effectors match the target class 
 * referenced during gearbox compilation. Applications can then use the 
 * {@link com.characterforming.jrte.Jrte#transduction(ITarget)} 
 * method to instantiate an {@link com.characterforming.jrte.ITransduction}. 
 * <p>
 * A transduction binds a stack of {@link com.characterforming.jrte.IInput}, a transducer 
 * stack, and an ITarget instance. The starting transducer reads input ordinals, which 
 * may be Unicode character ordinals or signal ordinals, from the IInput on
 * the top of the input stack, popping the input stack as each IInput is 
 * exhausted. Each input drives a transition in the transducer at the top
 * of the transducer stack, which determines a (possibly empty) vector of
 * target effectors to invoke.
 * <p>
 * The {@link com.characterforming.jrte.base} package provides three IInput 
 * implementations. The {@link com.characterforming.jrte.Jrte#input(byte[][])}
 * method receives an array of byte[] arrays, each containing either a signal name
 * or a text sequence. It maps signal names to corresponding signal ordinals and 
 * copies text ordinals from text sequences to produce a single char[] array as
 * the input source. 
 * <p>
 * For example, it is sometimes useful to provide an initial nil signal to a 
 * transduction to provide a starting transition that sets up the transduction:
 * <pre class="code">
 * 
 * Jrte jrte = new Jrte(new File(".\jrte.gears", "com.characterforming.jrte.base.BaseTarget");
 * IInput[] inputs = new IInput[] {
 *    jrte.input(new char[][] { new String("!nil").toCharArray() }),
 *    jrte.input(new InputStreamReader(System.in))
 * };
 * </pre>
 * In the code example above, the Jrte BaseTarget class is selected as the target. 
 * The <a href="overview-summary.html">overview</a> documentation describes 
 * the built-in effectors expressed by the base transduction target class, which are 
 * sufficient for simple transductions that write to System.out. The built-in 
 * effectors are available to all target classes, even if they do not explicitly 
 * subclass BaseTarget, along with any additional effectors defined by specialized
 * target classes.
 * <p>
 * To continue the example above:
 * <pre class="code">
 * 
 * ITarget target = new BaseTarget(false);
 * ITransduction transduction = jrte.bind(target);
 * transduction.start("HelloWorld");
 * transduction.input(inputs);
 * while (transduction.status() == ITransduction.RUNNABLE) {
 *    transduction.run();
 * }
 * </pre>
 * If System.in contains "hello world" this transduction will write "(-: hello world :-)"
 * to System.out. The transducer HelloWorld is a 3-tape finite state automaton, where
 * the 1st tape matches the input, the 2nd tape specifies the target effectors
 * to invoke on input transitions, and the 3rd tape holds parameter values for
 * parameterised effectors. It is compiled from a regular expression by ginr and 
 * saved to a .dfa file for input to the Jrte gearbox compiler.
 * <pre class="code">
 * 
 * HelloWorld = (
 *    (nil, clear paste[`(-: `]) ('hello world' @@ PasteAny) (eos, paste[` :-)`] out outln clear)
 * );
 * HelloWorld :save `_HelloWorld.dfa`;
 * </pre>
 * <p>
 * In the ginr expression above, the PasteAny transducer acts as a general-purpose method to append 
 * input into the current selection. When it is applied to a specific input pattern using ginr's 
 * join operator --<pre class="code">PasteAny @@ 'hello world'</pre>-- its domain is restricted 
 * to the input pattern, so the application in HelloWorld is equivalent to:<pre class="code">
 * (h,paste)(e,paste)(l,paste)...(r,paste)(l,paste)(d,paste)</pre>
 * <p>
 * A synopsis of the operators provided by ginr can be viewed <a href="{@docRoot}/../doc/resources/ginr.html" target="_blank">
 * here</a>.
 */
package com.characterforming.jrte;

