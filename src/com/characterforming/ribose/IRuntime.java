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
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.ribose;

import java.io.InputStream;

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
 * <br>
 * Transductors operate a transducer stack and an input stack.  syntactic features onto effectors under the direction of a stack
 * of finite state transducers compiled from patterns in a domain-specific
 * {@code (<feature-syntax>, <feature-sematics>)*} semi-ring and collected
 * in a ribose runtime model.
 * <br>
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
	 * Transduce a byte input stream onto a target instance.
	 * 
	 * @param target the model target instance to bind to the transduction
	 * @param transducer the name of the transducer to start the transduction
	 * @param in the input stream to transduce
	 * @return true if either transducer or input stack is empty
	 * @throws RiboseException on error
	 * @see ITransductor#status()
	 */
	boolean transduce(ITarget target, Bytes transducer, InputStream in) throws RiboseException;
	
	/**
	 * Catenate and transduce a signal (eg, {@code nil}) and a byte input stream onto a target instance.
	 * 
	 * @param target the model target instance to bind to the transduction
	 * @param transducer the name of the transducer to start the transduction
	 * @param prologue signal to transduce prior to {@code in}
	 * @param in the input stream to transduce
	 * @return true if either transducer or input stack is empty
	 * @throws RiboseException on error
	 * @see ITransductor#status()
	 */
	boolean transduce(ITarget target, Bytes transducer, Signal prologue, InputStream in) throws RiboseException;

	/**
	 * Instantiate a new transductor and bind it to an instance of the . 
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
