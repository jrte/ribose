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

import com.characterforming.ribose.base.ModelException;

/**
 * The ribose runtime provides a capability to instantiate runtime transductors. A 
 * transductor is a capability to host runtime transductions. A runtime transduction
 * is a pattern-driven process mapping sequential input onto effectors expressed by
 * a target object that extracts and assimilates features of interest from input.
 * <p/>
 * Transductions map syntactic features onto effectors under the direction of a stack
 * of finite state transducers compiled from patterns in a domain-specific
 * {@code (<feature-syntax>, <feature-sematics>)*} semi-ring and collected
 * in a ribose runtime model.
 * <p/>
 * Each ribose runtime model is compiled from a collection of ginr automata produced 
 * from semi-ring patterns mapping input features to patterns of effector invocations.
 * The ribose model compiler compresses and assembles these into ribose transducers 
 * persistent in a ribose model file, binding them with the model target class that 
 * expresses the model effectors. 
 * <p/>
 * Governance:
 * <p/>
 * {@code (input* newTransductor input*)* close}
 * 
 * @author Kim Briggs
 *
 */
public interface IRiboseRuntime extends AutoCloseable{
	/**
	 * Instantiate a new transductor. 
	 * 
	 * @param target The target instance to bind to the transductor.
	 * @return A new transductor
	 * @throws ModelException
	 */
	public ITransductor newTransductor(ITarget target) throws ModelException;
	
	/**
	 * Instantiate a thread local transductor. 
	 * 
	 * @param target The target instance to bind to the transductor.
	 * @return A thread-bound transductor
	 * @throws ModelException
	 */
	public ThreadLocal<ITransductor> tlsTransductor(ITarget target) throws ModelException;
	
	/**
	 * Close the runtime model and file.
	 * 
	 * @throws ModelException
	 */
	@Override
	public void close() throws ModelException;
}
