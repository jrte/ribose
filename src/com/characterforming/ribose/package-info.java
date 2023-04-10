/**
 * Component interfaces and runnable classes for the ribose model compiler and runtime. The
 * {@link Ribose} class provides static methods for compiling and loading ribose models in
 * the Java VM. The runnable classes {@link TCompile} and {@link TRun} enable model compilation
 * and transduction to be run from the shell.
 * <br><br>
 * A collection of related ribose patterns are compiled to automata by ginr. The automata
 * ({@code *.dfa}) files are saved to directory and the ribose model compiler packages them
 * into a ribose model ({@code *.model}) file for use in the ribose runtime. An {@link ITarget}
 * implementation class is bound to the model when it is compiled. The default 
 * {@link com.characterforming.ribose.base.BaseTarget} class provides access to the built-in
 * effectors described in the {@link ITransductor} documentation, whuch should be sufficient
 * for regular and context-free transductions that only write transduction output through
 * the {@code out} effector. Domain-specific {@code ITarget} implementations implicitly
 * include these built-in effectors and may define additional specialized effectors that
 * assimilate transduction output into the target domain. 
 * <br><br>
 * The ribose model compiler {@link TCompile} can be run from the command line using
 * {@link TCompile#main(String[])} specifying an {@link ITarget} implementation class
 * (eg, {@link com.characterforming.ribose.base.BaseTarget}) as target class, the path
 * to the automata directory and the path and name of the file to contain the compiled
 * model. The compiler is also accessible in the JVM using {@link Ribose#compileRiboseModel(Class, File, File)}.
 * The  default {@link com.characterforming.ribose.base.BaseTarget} class will be used as
 * target if {@code --target} and {@code --target-path} are not specified. In any case, a
 * proxy instance of the model target class will be instantiated, using its default constructor,
 * to precompile effector parameters. See the {@link ITarget} documentation for details
 * regarding this process.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TCompile usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TCompile --target <i>classname automata model</i></td></tr>
 * <tr><td style="text-align:right">--target <i>classname</i></td><td>Fully qualified name of the model target class.</td></tr>
 * <tr><td style="text-align:right">--target-path <i>paths:to:jars</i></td><td>Classpath containing jars for target class and dependencies.</td></tr>
 * <tr><td style="text-align:right"><i>automata</i></td><td>The path to the directory containing automata (*.dfa) to include in the model.</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the file to contain the compiled model.</td></tr>
 * </table>
 * <br>
 * For {@link com.characterforming.ribose.base.BaseTarget} the default (nullary) constructor of the
 * target class, which the model compiler requires for proxy target instantiation, is also used for
 * live target instantiation in the ribose runtime. For any {@link ITarget} implementation capable of
 * live target instantiation from the default constructor {@link TRun#main(String[])} can be run to
 * load a target model and transduce a UTF-8 input stream. Output from the {@code out[..]} effector
 * is written as UTF-8 byte stream unless {@code -Djrte.out.enabled=false} is selected in the java
 * command options (allowing benchmarking to proceed without I/O overhead). The {@code --target-path}
 * argument need not be specified if the model target is {@link com.characterforming.ribose.base.BaseTarget}.
 * In any case, the target class must have a default constructor for both proxy and live target
 * instances. A proxy instance will be instantiated to precompile effector parameters and live 
 * instance will be instantiated and bound to the transduction. See the {@link ITarget} documentation
 * for details regarding proxy and live targets in the ribose runtime. Default output is System.out.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TRun usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TRun [--nil] [--target <i>classname</i>] <i>model transducer input|- [output]</i></td></tr>
 * <tr><td style="text-align:right">--target-path <i>paths:to:jars</i></td><td>Classpath containing jars for target class and dependencies.</td></tr>
 * <tr><td style="text-align:right">--nil</td><td>Push {@link com.characterforming.ribose.base.Signal#nil} to start the transduction (recommended).</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the model file containing the transducer.</td></tr>
 * <tr><td style="text-align:right"><i>transducer</i></td><td>The name of the transducer to start the transduction.</td></tr>
 * <tr><td style="text-align:right"><i>input</i></td><td>The path to the input file (use {@code -} to read {@code System.in}).</td></tr>
 * <tr><td style="text-align:right"><i>output</i></td><td>The path to the output file (default is {@code System.out}).</td></tr>
 * </table>
 * <br>
 * The model compiler reduces the NxM transition matrix (N&ge;260 input
 * bytes or signals, M states) for ginr transducer <b>H</b> to an Nx1 input
 * equivalence transducer <b>F</b> mapping byte and signal ordinals to K&lt;N
 * input equivalence classes and a KxM kernel transducer <b>G</b> 
 * equivalent to <b>H</b> modulo <b>F*</b> (so <b>H</b>(x)&nbsp;=&nbsp;<b>(G&deg;F*)</b>(x)).
 * The ribose transducer decompiler {@link TDecompile} can be used to list the input equivalence
 * map and kernel transitions for any compiled transducer. It can be run from the command line
 * using {@link TDecompile#main(String[])} specifying the containing model and the transducer
 * to decompile.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TDecompile usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TDecompile <i>model transducer</i></td></tr>
 * <tr><td style="text-align:right">--target-path <i>paths:to:jars</i></td><td>Classpath containing jars for target class and dependencies.</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the file to contain the compiled model.</td></tr>
 * <tr><td style="text-align:right"><i>transducer</i></td><td>The name of the transducer to decompile.</td></tr>
 * </table>
 * <br>
 * To use ribose in an application or service, call {@link Ribose#loadRiboseModel(File)}
 * to load an {@link IRuntime} instance from a compiled ribose model. In this context 
 * live targets are instantiated externally (a proxy target in instantiated from the
 * default constructor of a model's target class when the model is loaded into the ribose
 * runtime). The {@link IRuntime#transduce(ITarget, Bytes, java.io.InputStream, java.io.OutputStream)}
 * method offers generic support for setting up and running a stream-oriented transduction
 * with a live target instance. For more fine-grained transduction control, use
 * {@link IRuntime#newTransductor(ITarget)} to bind a live target to a transductor
 * and apply {@link ITransductor} methods directly to set up inputs and transducers
 * and run transductions. Transductions and the involved objects (transductor, target,
 * effectors, fields) are assumed to be single-threaded. Concurrent transductions should
 * run on separate threads, although a single thread can safely multiplex over &gt;1 live
 * transductors.
 *
 * @author Kim Briggs
 * @see IRuntime#transduce(ITarget, Bytes, java.io.InputStream, java.io.OutputStream)
 * @see ITransductor
 */
package com.characterforming.ribose;
