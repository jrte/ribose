/**
 * Component interfaces and runnable classes for the ribose model compiler and runtime. 
 * <br><br>
 * The ribose model compiler {@link TCompile} can be run from the command line using
 * {@link TCompile#main(String[])}. It is also accessible in the JVM using 
 * {@link Ribose#compileRiboseModel(Class, File, File)}. {@link TRun} implements a 
 * simple target presenting the core built-in effectors which can be used to build
 * models that do not require specialized targets and effectors. It also presents a 
 * {@link TRun#main(String[])} method to load compiled ribose models and run 
 * transductions from the command line.  
 * <br><br>
 * To use ribose in an application or service, call {@link Ribose#loadRiboseModel(File)}
 * to load an {@link IRuntime} instance from a compiled ribose model. Use {@link IRuntime#newTransductor(ITarget)}
 * to bind targets to ribose transductors, which encapsulate runtime transductions. The
 * {@link IRuntime#transduce(ITarget, Bytes, java.io.InputStream, java.io.OutputStream)} 
 * method offers generic support for setting up and running stream-oriented transductions.
 * For more fine-grained control of transductions, apply {@link ITransductor} methods
 * directly to set up inputs and transducers and run transductions.
 * <br><br>
 * Run {@link TCompile#main(String[])} to build a ribose model specifying an {@link ITarget}
 * implementaion class (eg, {@link TRun}) as target class, the path to the automata directory 
 * and the path and name of the file to contain the compiled model.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TCompile usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose.0.0.0.jar com.characterforming.ribose.TCompile <i>target automata model</i></td></tr>
 * <tr><td style="text-align:right"><i>target</i></td><td>Fully qualified name of the model target class.</td></tr>
 * <tr><td style="text-align:right"><i>automata</i></td><td>The path to the directory containing automata (*.dfa) to include in the model.</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the file to contain the compiled model.</td></tr>
 * </table>
 * <br>
 * Use {@link TRun#main(String[])} to load a ribose model for a target class and
 * run a transduction on a UTF-8 input stream. Output from the {@code out[..]} effector
 * is written as UTF-8 byte stream unless {@code -Djrte.out.enabled=false} is selected
 * in the java command options. This option is provided to allow benchmarking to proceed
 * without incurring delays and heap overhead relating to the I/O subsystem.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TRun usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose.0.0.0.jar com.characterforming.ribose.TRun <i>model transducer input [output]</i></td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the model file containing the transducer.</td></tr>
 * <tr><td style="text-align:right"><i>transducer</i></td><td>The name of the transducer to start the transduction.</td></tr>
 * <tr><td style="text-align:right"><i>input</i></td><td>The path to the input file.</td></tr>
 * <tr><td style="text-align:right"><i>output</i></td><td>The path to the output file.</td></tr>
 * </table>
 * <br>
 * Default output is System.out.
 * 
 * @author Kim Briggs
 */
package com.characterforming.ribose;
