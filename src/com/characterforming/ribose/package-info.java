/**
 * Component interfaces and runnable classes for the ribose model compiler and runtime.
 * The runnable {@link Ribose#main(String[] args)} method enables model compilation,
 * decompilation and stream transduction from the shell. The {@code ribose} bash script
 * is available to simplify running ribose from the shell. See the {@link Ribose}
 * documentation or type {@code ./ribose help} in the shell to see parameters and
 * options for the available commands. The {@link IModel} interface provides static
 * methods for compiling and loading ribose models in the Java runtime and non-static
 * methods for instantiating and binding {@link ITransductor} to live {@link ITarget}
 * instances, permitting fine-grained control of transductions, and more granular
 * methods for stream transduction.
 * <br><br>
 * A running {@link ITransductor} drives a stream of input bytes onto a live {@link
 * ITarget} instance. Input and output are treated as raw byte streams. UTF-8 input
 * is transduced as binary and passes through to UTF-8 output without decoding. 
 * Each transductor implements {@link ITarget} and injects itself and its effectors
 * into its bound {@code ITarget}. The transductor effectors, listed in {@link
 * ITransductor}, effect control of the transductor's input and transducer stacks
 * and enable byte field extraction and composition. Simple models rely
 * solely on the transductor effectors and express output through the {@code
 * out[..]} effector. Domain specific {@link ITarget} implementations may extend
 * the range of the transductor target with specialized {@link IEffector} and 
 * {@link IParameterizedEffector} implementations. All effectors receive {@link
 * IOutput} views to access extracted {@link IField} output as raw bytes, common
 * Java primitive, or Unicode text. 
 * <br><br>
 * {@link IEffector} and {@link IParameterizedEffector} classes are typically
 * implemented as private inner classes of the target class. Every target class
 * must present a default constructor that instantiates a <i>proxy</i> instance
 * for effector parameter compilation. Parameters are compiled once when the 
 * model is compiled, to validate the supplied parameters, and once again every
 * time the model is loaded for runtime use. Parameters are represented as lists of 
 * backquoted Unicode {@code `tokens`} in ribose patterns and presented as arrays
 * of UTF-8 {@code bytes} to parameterized effectors to compile. A parameterized
 * effector {@code E<T,P>} compiles from each token array an instance of {@code P}.
 * The compiled parameters are retained by the runtime model and supplied to live
 * target effectors when live targets are bound to transductors. The relationship
 * between model and target is 1..1 (model-proxy target) and transient in compile
 * contexts and 1..N (model-live targets) in runtime contexts, persisting for
 * the lifetime of the transductor. See {@link ITarget}, {@link IEffector} and 
 * {@link IParameterizedEffector} for more information regarding these classes
 * in model compilation and runtime contexts. 
 * <br><br>
 * A ribose model is <i>simple</i> if the model target class defines no specialized
 * effectors, so the model transducers use only the built-in {@link ITransductor} 
 * effectors, <i>or</i> if proxy <i>and</i> live target instances can be instantiated
 * using a default constructor. Simple models encapsulate regular and context-free
 * transductions that only write transduction output through the {@code out}
 * effector. They typically use {@link SimpleTarget} as target class, which
 * provides access to the {@link ITransductor} effectors. {@link SimpleTarget}
 * is {@code final} and exists only to expose the transductor effectors as these
 * are otherwise inaccessible. For <i>simple</i> models both live and proxy
 * targets are instantiated with the default constructor. 
 * <br><br>
 * A model is <i>fancy</i> if the model target class presents specialized effectors
 * and/or requires a non-default constructor to instantiate live target instances. 
 * These domain-specific {@link ITarget} implementations implicitly inherit the
 * {@link ITransductor} effectors and may define additional effectors to assimilate
 * transduction output into the target domain. For <i>fancy</i> models proxy targets
 * are instantiated with the default constructor but live targets must be instantiated
 * outside the ribose runtime. All effectors receive a reference to their enclosing
 * target as well as an {@link IOutput} view of the transductor's fields. Fields
 * are represented by the {@link IField} interface, which provides methods for
 * decoding extracted field contents as Java primitives.
 * <br><br>
 * Transductions and the involved objects (transductor, target, effectors, fields) are
 * assumed to be single-threaded. Concurrent transductions should run on separate
 * threads, although a single thread can safely multiplex over &gt;1 live transductors.
 * Concurrent transductors can interact using {@code PipedInputStream} and {@code
 * PipedOutputStream} if desired. See the {@link IModel} and {@link ITransductor}
 * documentation for details regarding running transductions in the ribose runtime.
 * <br><br>
 * The ribose transducer decompiler can be used to list the input equivalence map and kernel
 * transitions for any compiled transducer. It can be run from the ribose script, or directly
 * from the java command line, specifying the containing model and the transducer to decompile.
 *
 * @author Kim Briggs
 * @see IModel
 * @see ITransductor
 */
package com.characterforming.ribose;

import com.characterforming.ribose.base.*;
