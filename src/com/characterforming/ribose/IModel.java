/***
 * Ribose is a recursive transduction engine for Java
 *
 * Copyright (C) 2011,2022 Kim Briggs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program (LICENSE-gpl-3.0). If not, see
 * <http://www.gnu.org/licenses/#GPL>.
 */

package com.characterforming.ribose;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import com.characterforming.jrte.engine.ModelCompiler;
import com.characterforming.jrte.engine.ModelLoader;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.SimpleTarget;

/**
 * The {@code IModel} interface provides static methods for compiling and loading 
 * ribose models into the Java runtime and encapsulates a runtime model instance.
 * The model compiler assembles ribose models from collections of ginr automata.
 * The model loader implements threadsafe instantiation of {@link ITransductor} 
 * for fine-grained transduction workflows and more granular methods for
 * transducing input streams.  
 * <br><br>
 * Model files are compiled atomically and support multiple concurrent loaders. 
 * Each model loader serializes one-time loading of transducers on first use in
 * multithreaded contexts. In all other respects, ribose objects are not threadsafe.
 * Runtime {@code IModel} instances are {@link AutoCloseable} but clients must
 * call {@link #close()} for models that are instantiated outside a try-with-resources
 * block.
 *
 * @author Kim Briggs
 *
 */
public interface IModel extends AutoCloseable {
/**
 * Compile a collection of ginr DFAs from an automata directory into a ribose model file
 * and bind them to an {@link ITarget} class. Ginr compiles ribose patterns (*.inr files)
 * to DFAs as the first step in building a ribose model. The ribose compiler cruches the
 * DFAs to produce ribose transducers and asembles them in a into the model file for 
 * runtime use.
 * 
 * @param targetClassname the name of the Target implementation class will be instantiated as model target
 * @param ginrAutomataDirectory directory containing DFAs compiled by ginr to be included in the model
 * @param riboseModelFile path indicating where to create the model file
 * @return true if the model was created successfully
 * @throws ClassNotFoundException if {@code targetClassname} not found
 * @throws ModelException if compilation fails
 */
	public static boolean compileRiboseModel(String targetClassname, File ginrAutomataDirectory, File riboseModelFile)
	throws ClassNotFoundException, ModelException {
		Class<?> targetClass = Class.forName(targetClassname);
		if (ITarget.class.isAssignableFrom(targetClass)) {
			return ModelCompiler.compileAutomata(targetClass, riboseModelFile, ginrAutomataDirectory);
		}
		throw new ModelException(String.format("%1$s does not implement ITarget", targetClassname));
	}

	/**
	 * Load a ribose runtime model from persistent store and bind it to a model
	 * target instance. A runtime model can be used to instantiate transductors.
	 * 
	 * @param riboseModelFile path to the runtime model to load
	 * @return a live ribose runtime model instance
	 * @throws ModelException if the model could not be loaded
	 */
	public static IModel loadRiboseModel(File riboseModelFile)
	throws ModelException {
		return ModelLoader.loadModel(riboseModelFile);
	}	

	/**
	 * Instantiate a new transductor and bind it to a live target instance. Use this method to obtain
	 * fine-grained control of transduction processes. See the {@link ITransductor} documentation
	 * for an example.
	 *
	 * @param target the target instance to bind to the transductor
	 * @return a new transductor
	 * @throws ModelException if things don't work out
	 * @see ITransductor
	 */
	ITransductor transductor(ITarget target)
	throws ModelException;

	/**
	 * Transduce an input stream onto an output stream. The streams are read and
	 * written as raw byte streams and may contain UTF-8 and/or binary data as
	 * permitted by the transduction input patterns. This method can be applied
	 * only for simple ribose models, bound to target classes like {@link SimpleTarget}
	 * that present a default constructor for instantiating both live and proxy
	 * target instances. For live targets that are instantiated externally
	 * use {@link #stream(Bytes, ITarget, Signal, InputStream, OutputStream)}.
	 * <br><br>
	 * The {@code transducer} is pushed to start the transduction. If the
	 * {@code prologue} signal is not {@link Signal#NONE} it will be applied as
	 * input to the first transition when the transductor is run. This may be used
	 * to trigger some preliminary effects before the input stream is transduced.
	 * <br><br>
	 * The {@code stream()} methods use a default size of 64kb for I/O buffers.
	 * This can be changed by defining {@code -Dribose.inbuffer.size=Nbytes} and/or
	 * {@code -Dribose.outbuffer.size=Nbytes} in the JVM arguments.
	 *
	 * @param transducer the UTF-8 encoded name of the transducer to start the transduction
	 * @param prologue start signal to send to {@code transducer}
	 * @param in the input stream to transduce (eg, {@code System.in})
	 * @param out the output stream to render output to (eg, {@code System.out})
	 * @return true if transduction is complete, otherwise false
	 * @throws RiboseException if things don't work out
	 */
	boolean stream(Bytes transducer, Signal prologue, InputStream in, OutputStream out)
	throws RiboseException;

	/**
	 * Bind a live {@link ITarget} to an transductor and transduce a byte input
	 * stream onto it. The signal and input and output streams and I/O buffers are
	 * treated as for {@link #stream(Bytes, Signal, InputStream, OutputStream)}.
	 * 
	 * @param transducer the UTF-8 encoded name of the transducer to start
	 * @param target the transduction target instance
	 * @param prologue start signal to send to {@code transducer} (if not @link Signal#NONE})
	 * @param in the input stream to transduce (eg, {@code System.in})
	 * @param out the output stream to render output to (eg, {@code System.out})
	 * @return true if target is an instance of the model target class and transduction is complete
	 * @throws RiboseException if things don't work out
	 */
	boolean stream(Bytes transducer, ITarget target, Signal prologue, InputStream in, OutputStream out)
	throws RiboseException;

	/** 
	 * Decompile a transducer to System.out
	 * 
	 * @param transducerName the transducer name as aUnicode string
	 * @throws ModelException if things don't work out
	 */
	void decompile(String transducerName)
	throws ModelException;

	/** 
	 * Print the model map to System.out
	 * 
	 * @return true unless an uncaught exception is thrown
	 * @throws ModelException if things don't work out
	 */
	boolean map()
	throws ModelException;

	/**
	 * Get the fully qualified name of the model target.
	 * 
	 * @return the fully qualified name of the model target
	 */
	String getTargetClassname();

	/**
	 * Close the runtime model and file.
	 *
	 * @throws ModelException if things don't work out
	 */
	@Override
	void close()
	throws ModelException;
}
