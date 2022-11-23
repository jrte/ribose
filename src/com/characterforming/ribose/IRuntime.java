/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.ribose;

import java.io.InputStream;
import java.io.OutputStream;

import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;

/**
 * The ribose runtime provides a capability to instantiate runtime transductors from
 * a runtime model and bind them to instances of the model target class. A transductor
 * provides a capability to run serial transductions. A transduction is a pattern-driven
 * process that selects and invokes target effectors serially to extract and assimilate
 * features of interest into the target in response to syntactic cues in the input.
 * <br><br>
 * Transductors operate a transducer stack and an input stack. Transductions map syntactic
 * features onto effectors under the direction of a stack of finite state transducers 
 * compiled from patterns in a domain-specific {@code (<feature-syntax>, <feature-sematics>)*} 
 * semi-ring and collected in a ribose runtime model.
 * <br><br>
 * Each ribose runtime model is compiled from a collection of ginr automata produced 
 * from semi-ring patterns mapping input features to patterns of effector invocations.
 * The ribose model compiler compresses and assembles these into ribose transducers 
 * persistent in a ribose model file, binding them with the model target class that 
 * expresses the model effectors. 
 * 
 * @author Kim Briggs
 *
 */
public interface IRuntime extends AutoCloseable{
	/**
	 * Transduce a byte input stream onto a target instance. The input stream is presumed to be 
	 * buffered, with a buffer size not less than the maximal expected marked extent. If the
	 * transduction marks the stream the buffer containing the mark point and all subsequent
	 * buffers are held in the transductor's mark set and are not reusable as input buffers
	 * until they are recycled out of the mark set.
	 * <br><br> 
	 * Typical marking scenarios maintain an empty mark set until a mark is set near the end of
	 * a buffer which then must be added to the marked set until the stream is reset after a 
	 * short run in the next sequential buffer. Consuming an entire input buffer while holding
	 * a nonempty mark set is considered an anomaly and a one-time warning is logged for the
	 * first such event. This warning may signal a runaway mark or a marked feature of large 
	 * extent. In the latter case consider increasing the JVM property {@code ribose.inbuffer.size}
	 * to exceed the maximal expected marked extent. The default block size is 65536 bytes.
	 * 
	 * The transduction will assume UTF-8 encoding for the input and output streams.
	 *  
	 * @param target the model target instance to bind to the transduction
	 * @param transducer the name of the transducer to start the transduction
	 * @param in the input stream to transduce
	 * @param out the output stream to render output to
	 * @return true if either transducer or input stack is empty
	 * @throws RiboseException on error
	 * @see ITransductor#status()
	 */
	boolean transduce(ITarget target, Bytes transducer, InputStream in, OutputStream out) throws RiboseException;
	
	/**
	 * Catenate and transduce an initial signal (eg, {@code Signal.nil}) and a byte input 
	 * stream onto a target instance. The transduction will assume UTF-8 encoding for the
	 * input and output streams.
	 * 
	 * @param target the model target instance to bind to the transduction
	 * @param transducer the name of the transducer to start the transduction
	 * @param prologue signal to transduce prior to {@code in}
	 * @param in the input stream to transduce
	 * @param out the output stream to render output to
	 * @return true if either transducer or input stack is empty
	 * @throws RiboseException on error
	 * @see IRuntime#transduce(ITarget, Bytes, InputStream, OutputStream)
	 * @see ITransductor#status()
	 */
	boolean transduce(ITarget target, Bytes transducer, Signal prologue, InputStream in, OutputStream out) throws RiboseException;

	/**
	 * Instantiate a new transductor and bind it to a target instance. The UTF-8 Charset
	 * will be used to decode/encode UTF-8 bytes in input/output streams.
	 * 
	 * @param target The target instance to bind to the transductor.
	 * @return A new transductor
	 * @throws ModelException on error
	 */
	public ITransductor newTransductor(ITarget target) throws ModelException;
	
	/**
	 * Close the runtime model and file.
	 * 
	 * @throws ModelException on error
	 */
	@Override
	public void close() throws ModelException;
}
