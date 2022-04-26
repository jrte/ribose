/**
 * Component interfaces for the ribose compiler and runtime. The 
 * compiler can be run from the command line using {@link TCompile#main(String[])}. 
 * Compiled transducers can be run in the ribose runtime from the command line
 * using {@link TRun#main(String[])}. 
 * 
 * To use ribose in an application or service, load an {@link IRuntime} 
 * instance from a compiled ribose runtime file using {@link 
 * Ribose#loadRiboseRuntime(java.io.File, ITarget)}. Use {@link IRuntime#newTransductor(ITarget)}
 * to bind the target to a ribose transduction stack and apply {@link ITransductor} 
 * methods to set up transduction input and transducer and run the transduction. 
 *
 * @author Kim Briggs
 */
package com.characterforming.ribose;
