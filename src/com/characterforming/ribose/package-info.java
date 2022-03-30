/**
 * The ribose compiler and runtime provide applications with component
 * interfaces for compiling transducers and instantiating runtime transduction
 * stacks.
 * 
 * Main component interfaces for the ribose compiler and runtime. The 
 * compiler can be run from the command line using {@link 
 * com.characterforming.jrte.GearboxCompiler#main(String[])} or from application
 * process space using an {@link IRiboseCompiler} instance. Compiled
 * transducers can be run in the ribose runtime from the command line
 * using {@link com.characterforming.jrte.Jrte#main(String[])} or from application
 * process space using an {@link IRiboseRuntime} instance.
 * 
 * A target class can be bound to more than one gearbox, each containing specialized 
 * transducers. {@link IRiboseCompiler} instantiates a nullary model target is to 
 * obtain instances of its effectors and compile effector parameters to create a model
 * transduction. The model transduction is then bound to the transducers compiled
 * from the input automata. 
 * 
 * The ribose runtime instantiates the model transduction to recompile effector parameters
 * when it loads the gearbox. The model transduction provides precompiled effector parameters
 * to bind to new {@link com.characterforming.jrte.ITransduction} stacks on demand. See the 
 * {@link com.characterforming.jrte} package for an overview of runtime operation.
 *
 * @author Kim Briggs
 */
package com.characterforming.ribose;