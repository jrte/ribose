/**
 * Component interfaces for the ribose compiler and runtime. The 
 * compiler can be run from the command line using {@link Ribose#main(String[])}. 
 * Compiled transducers can be run in the ribose runtime from the command line
 * using {@link RiboseRuntime#main(String[])}. 
 * 
 * To use ribose in an application or service, load an {@link IRiboseRuntime} 
 * instance from a compiled ribose runtime file using {@link 
 * Ribose#loadRiboseRuntime(java.io.File, com.characterforming.jrte.ITarget)}. 
 * Use {@link IRiboseRuntime#newTransductor(com.characterforming.jrte.ITarget)}
 * to bind the target to a ribose transduction stack and apply 
 * {@link com.characterforming.jrte.ITransductor} methods to set up transduction
 * input and transducer and run the transduction. 
 *
 * @author Kim Briggs
 */
package com.characterforming.ribose;