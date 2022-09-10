/**
 * Component interfaces for the ribose model compiler and runtime. 
 * <br><br>
 * The model compiler can be run from the command line using {@link TCompile#main(String[])}.
 * It is also accessible in the JVM using {@link Ribose#compileRiboseModel(Class, File, File)}.
 * Transducers from text transduction models built with {@link TRun} as model target class
 * can be run from the command line using {@link TRun#main(String[])}. 
 * <br><br>
 * To use ribose in an application or service, call {@link Ribose#loadRiboseModel(File, ITarget)}
 * to load an {@link IRuntime} instance from a compiled ribose model. Use {@link IRuntime#newTransductor(ITarget)}
 * to bind targets to ribose transductors and apply {@link ITransductor} methods to set up
 * inputs and transducers and run transductions. 
 *
 * @author Kim Briggs
 */
package com.characterforming.ribose;
