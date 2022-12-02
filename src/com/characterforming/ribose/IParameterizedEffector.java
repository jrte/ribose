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

import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for parameterized effectors. Parameters are compiled from arrays of
 * byte arrays into an array of some parameter type P that is constructible from one
 * or more arrays of bytes. Compiled parameter values are referenced by their
 * index in the resulting array. At runtime, the invoke(int) method is called
 * with the parameter index to indicate which P[] to apply to the invocation.
 * 
 * @author Kim Briggs
 * @param <T> The effector target type
 * @param <P> The effector parameter type, constructible from byte[][] (eg new
 *           P(byte[][]))
 */
public interface IParameterizedEffector<T extends ITarget, P> extends IEffector<T> {
	/**
	 * Create a parameters array (P[]) with capacity for a specified number of P
	 * instances. This array is populated in with enumerated parameter instances
	 * in subsequent calls to {@link #compileParameter(int, byte[][])} (when 
	 * compiling or loading ribose models) or {@link #setParameter(int, Object)}
	 * (when setting up a runtime transductor).
	 * 
	 * @param parameterCount The size of the array to create
	 */
	void newParameters(int parameterCount);

	/**
	 * Get the number of compiled parameters that are bound to the effector.
	 * 
	 * @return The number of compiled parameters
	 */
	int getParameterCount();

	/**
	 * Compile and set a parameter value from effector arguments specified in
	 * model transducers. The parameter value, which may be a scalar or an
	 * array, is to be compiled from an array of byte arrays. The implementation
	 * class must call {@link #setParameter(int, Object)} to set the result in  
	 * the P[] array instantiated in the base class by {@link #newParameters(int)}.
	 * 
	 * @param parameterIndex The array index in the parameters array P[] to set
	 *           with the parameter value
	 * @param parameterList An array of parameters, where each parameter is an
	 *           array of bytes.
	 * @return the compiled parameter value object 
	 * @throws TargetBindingException on error
	 */
	P compileParameter(int parameterIndex, byte[][] parameterList) throws TargetBindingException;

	/**
	 * Set a precompiled parameter value.
	 * 
	 * @param parameterIndex The array index in the parameters array P[] to set
	 *           with the parameter value
	 * @param parameter the parameter value to set
	 */
	void setParameter(int parameterIndex, Object parameter);

	/**
	 * Get a compiled parameter value.
	 * 
	 * @param parameterIndex The parameter index
	 * @return The parameter value at the index
	 */
	P getParameter(int parameterIndex);

	/**
	 * Parameterized effector invocation receivews the index of the {@code P} 
	 * instance to apply for the invocation.
	 * 
	 * @param parameterIndex The index of the parameter object to be applied
	 * @return User-defined effectors should return 0
	 * @throws EffectorException on error
	 */
	int invoke(int parameterIndex) throws EffectorException;
}
