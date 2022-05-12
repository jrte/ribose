/**
 * Component interfaces for the ribose model compiler and runtime. 
 * <p/>
 * The model compiler can be run from the command line using {@link TCompile#main(String[])}.
 * It is also accessible in the JVM using {@link Ribose#compileRiboseModel(Class<?>, File, File)}.
 * Transducers from text transduction models built with {@link TRun} as model target class
 * can be run from the command line using {@link TRun#main(String[])}. 
 * <p/>
 * To use ribose in an application or service, load an {@link IRuntime} instance from a compiled 
 * ribose model {@link Ribose#loadRiboseModel(File, ITarget)}. Use {@link IRuntime#newTransductor(ITarget)}
 * to bind the target to a ribose transduction stack and apply {@link ITransductor} 
 * methods to set up transduction input and transducer and run the transduction. 
 *
 * @see {@link Ribose}
 * @see {@link TCompile}
 * @see {@link TRun}
 * @author Kim Briggs
 */
package com.characterforming.ribose;
