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

import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for transduction target classes, which express IEffector instances
 * that are invoked from runtime transductions. Target implementations supplement
 * BaseTarget with specialized effectors.
 * <br>
 * Target classes must present a public default constructor with no arguments. A
 * proxy instance of the target implementation class is instantiated during
 * model compilation to determine the names and types of the target effectors
 * and to compile and validate effector parameters. At that time the
 * {@link IParameterizedEffector#newParameters(int)} will be called first and then
 * {@link IParameterizedEffector#compileParameter(int, byte[][])} methods will be
 * called for each IParameterizedEffector.
 * <br>
 * At runtime, targets are bound only once to a transductor and the binding
 * persists for the lifetime of the transductor. Note that transductors are 
 * restartable and reuseable and may serially run more than one transduction.   
 * @author Kim Briggs
 */
public interface ITarget {
	/**
	 * Get the name of the target as referenced in transducers that use the
	 * target.
	 * 
	 * @return The name of of this target
	 */
	public String getName();
	
	/**
	 * Lists the names and types of the effectors expressed by the target class
	 * for binding to the runtime model.
	 * <br>
	 * Implementation classes must include their effectors in the IEffector 
	 * array returned. Targets may be composite, arranged in an inhetritance
	 * chain or encapsulated in discrete component classes, or a mixture of
	 * these. In any case, a top-level target class is responsible for gathering
	 * effectors from all involved target instances and it is this target that 
	 * must be presented for effector binding. 
	 * 
	 * After runtime binding is complete effectors are invoked when triggered
	 * by running transductions in response to cues in the input stream.
	 * 
	 * @return An array of IEffector instances bound to the target instance
	 * @throws TargetBindingException on error
	 */
	public IEffector<?>[] bindEffectors() throws TargetBindingException;
}
