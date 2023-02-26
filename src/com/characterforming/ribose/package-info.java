/**
 * Component interfaces and runnable classes for the ribose model compiler and runtime.
 * <br><br>
 * A collection of related ribose patterns are compiled to automata by ginr. The automata
 * ({@code *.dfa}) files are saved to directory and the ribose model compiler packages them
 * into a ribose model ({@code *.model}) file for use in the ribose runtime. The ribose
 * model compiler {@link TCompile} can be run from the command line using
 * {@link TCompile#main(String[])} to build a ribose model specifying an {@link ITarget}
 * implementation class (eg, {@link TRun}) as target class, the path to the automata directory
 * and the path and name of the file to contain the compiled model. The compiler is also
 * accessible in the JVM using {@link Ribose#compileRiboseModel(Class, File, File)}.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TCompile usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TCompile --target <i>classname automata model</i></td></tr>
 * <tr><td style="text-align:right">--target <i>classname</i></td><td>Fully qualified name of the model target class.</td></tr>
 * <tr><td style="text-align:right"><i>automata</i></td><td>The path to the directory containing automata (*.dfa) to include in the model.</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the file to contain the compiled model.</td></tr>
 * </table>
 * <br>
 * {@link TRun} implements a simple target presenting the base ribose effectors
 * which can be used to build models that do not require specialized targets and effectors.
 * It also presents a {@link TRun#main(String[])} method to load compiled ribose models
 * and run transductions from the command line. Use {@link TRun#main(String[])} to load
 * a ribose model for a target class and run transductions on a UTF-8 input streams. Output
 * from the {@code out[..]} effector is written as UTF-8 byte stream unless
 * {@code -Djrte.out.enabled=false} is selected in the java command options. This option
 * is provided to allow benchmarking to proceed without incurring delays and heap
 * overhead relating to the I/O subsystem.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TRun usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TRun [--nil] [--target <i>classname</i>] <i>model transducer input [output]</i></td></tr>
 * <tr><td style="text-align:right">--nil</td><td>Push {@link com.characterforming.ribose.base.Signal#nil} to start the transduction (recommended).</td></tr>
 * <tr><td style="text-align:right">--target <i>classname</i></td><td>Fully qualified name of a target class with a nullary constructor (default is {@link TRun}).</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the model file containing the transducer.</td></tr>
 * <tr><td style="text-align:right"><i>transducer</i></td><td>The name of the transducer to start the transduction.</td></tr>
 * <tr><td style="text-align:right"><i>input</i></td><td>The path to the input file.</td></tr>
 * <tr><td style="text-align:right"><i>output</i></td><td>The path to the output file (default is {@code System.out}).</td></tr>
 * </table>
 * <br>
 * To use ribose in an application or service, call {@link Ribose#loadRiboseModel(File)}
 * to load an {@link IRuntime} instance from a compiled ribose model. Use {@link IRuntime#newTransductor(ITarget)}
 * to bind targets to ribose transductors, which encapsulate runtime transductions. The
 * {@link IRuntime#transduce(ITarget, Bytes, java.io.InputStream, java.io.OutputStream)}
 * method offers generic support for setting up and running stream-oriented transductions.
 * For more fine-grained control of transductions, apply {@link ITransductor} methods
 * directly to set up inputs and transducers and run transductions. Transductions and
 * the involved objects (transductor, target, effectors, values) are assumed to be
 * single-threaded. Concurrent transductions should run on separate threads, although
 * a single thread can safely multiplex over &gt;1 live transductors.
 *
 * @author Kim Briggs
 * @see IRuntime#transduce(ITarget, Bytes, java.io.InputStream, java.io.OutputStream)
 * @see ITransductor
 */
package com.characterforming.ribose;
