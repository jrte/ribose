/**
 * Component interfaces and runnable classes for the ribose model compiler and runtime. The
 * {@link IModel} interface provides static methods for compiling and loading ribose models in
 * the Java VM. The runnable {@link Ribose} class provides a {@code main(String[] args)} 
 * method enabling model compilation, decompilation and transduction from the shell. The
 * {@code ribose} bash script is available to simplify running ribose from the shell. See 
 * the {@code Ribose} documentation or type {@code ./ribose help} in the shell to see
 * parameters and options for the available commands. Each model is bound to an {@link
 * ITarget} implementation class <b>T</b>, which must provide a default constructor 
 * <b>T</b>() that is used to instantiate a proxy target for model compilation. Live
 * target instances, for binding to runtime transductors, are instantiated outside
 * the ribose runtime and may present specialized constructors for this purpose. The
 * relationship between model and target is 1..1 (model-proxy target) in compile
 * contexts and 1..N (model-live targets) in runtime contexts.
 * <br><br>
 * A ribose model is <i>simple</i> if the model target class defines no specialized
 * effectors, so the model transducers use only the built-in {@link ITransductor} effectors,
 * and if live and proxy target instances can be instantiated using a default constructor.
 * Simple models use {@link SimpleTarget} as target class. This provides access to the
 * {@link ITransductor} effectors, which should be sufficient for regular and context-free
 * transductions that only write transduction output through the {@code out} effector. This
 * target class is {@code final} and exists only to expose the transductor effectors as these
 * are otherwise inaccessible. Domain-specific {@link ITarget} implementations implicitly
 * inherit the transductor effectors and may define additional specialized effectors that
 * assimilate transduction output into the target domain. A model is <i>fancy</i> if the
 * model target class presents specialized effectors and/or requires a non-default 
 * constructor to instantiate live target instances. For <i>simple</i> models live targets
 * are instantiated with a default constructor. For <i>fancy</i> models live targets must
 * be instantiated outside the ribose runtime. All targets, simple or fancy, must present
 * a default contructor to the ribose model compiler and loader for effector parameter
 * compilation. These proxy target instances, and their proxy effectors, are used solely 
 * for parameter compilation and the effectors are never otherwise invoked.
 * <br><br>
 * All effectors receive a reference to their enclosing target as well as an {@link IOutput}
 * view of the transductor's fields. Fields are represented by the {@link IField} interface,
 * which provides methods for decoding extracted field contents as Java primitives. 
 * <br><br>
 * The IModel interface also provides non-static methods for instantiating {@link ITransductor}
 * and binding it to a live target instance, permitting fine-grained control of transductions.
 * The binding persists for the lifetime of the transductor, which can run any of the
 * transducers contained in the model. Additionally, {@code IModel} provides more granular
 * methods for streaming and transforming input onto an output stream in simple models and for
 * streaming input onto a specialized target and/or an output stream in fancy models.
 * Transduction input and output are treated as raw byte streams; UTF-8 input is transduced
 * to UTF-8 output without decoding. {@link IField} provides methods for converting extracted
 * bytes to common Java primitives, including decoding UTF-8 bytes to Unicode text. See the
 * {@link IModel}, {@link ITarget} and {@link ITransductor} documentation for more details 
 * regarding running transductions in the ribose runtime.
 * <br><br>
 * Transductions and the involved objects (transductor, target, effectors, fields) are assumed
 * to be single-threaded. Concurrent transductions should run on separate threads, although a
 * single thread can safely multiplex over &gt;1 live transductors. Concurrent transductors
 * can interact using {@code PipedInputStream} and {@code PipedOutputStream} if desired.
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
