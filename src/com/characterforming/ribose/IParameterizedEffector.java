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

import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for parameterized effectors extends {@link IEffector} with a monadic
 * {@link #invoke(int)} method. Parameters are compiled from arrays of byte arrays
 * into an array of some parameter type <b>P</b> that is constructible from 
 * <code>byte[][]</code>. Compiled parameter instances are referenced by their index in
 * the resulting array. At runtime, the {@link #invoke(int)} method is called
 * with the parameter index to indicate which instance of <b>P</b> to apply
 * to the invocation. This method returns an {@link IEffector} {@code RTX} value
 * as for {@link IEffector#invoke()}; see the javadoc comments for {@link IEffector}
 * for instructions regarding {@code RTX} codes.
 * <br><br>
 * Parameterized effectors are required to allocate an array <b>P[]</b> of parameter
 * instances and populate this array with compiled <b>P</b> instances. The runtime 
 * will call {@link #allocateParameters(int)} to obtain the parameter array and then
 * call {@link #compileParameter(IToken[])} for each  parameter to populate the array.
 * In ribose transducer patterns parameters are presented to effectors on tape 2
 * (the parameter tape) as a list of one or more backquoted tokens, eg {@code out[`~field` `,`]}.
 * Parameter tokens are represented as arrays of raw bytes which may contain text, which
 * ginr encodes as UTF-8 bytes, binary data encoded using {@code \xHH} hexadecimal
 * representation for unprintable bytes, or fields (`~field`), transducer names 
 * (`@transducer`) or signals (`!signal`). 
 * <br><br>
 * Precompiled parameters are maintained in the model at runtime and are shared as
 * immutable (or thread safe) singletons among active transducers. If associated
 * data are required per transductor, a parallel array of derivative mutable 
 * objects can be instantiated in the effector instance, since each transductor
 * maintains its own effector instances. For example, <code>DateEffector&lt;MyTarget, SimpleDateFormat&gt;</code>
 * is unsafe because multiple concurrent transductors may access the parametric 
 * <code>SimpleDateFormat</code> singletons simultaneously. However, 
 * <code>DateEffector&lt;MyTarget, String&gt;</code> may maintain its own mutable
 * array of <code>SimpleDateFormat</code> using the <code>String</code> parameters
 * to instantiate specialized <code>SimpleDateFormat</code> instances.
 * <br><br>
 * All {@code IParameterizedEffector} implementations must be subclasses of
 * {@link BaseParameterizedEffector}, which provides support for parameter
 * compilation and distribution.
 *
 * @author Kim Briggs
 * @param <T> The effector target type
 * @param <P> The effector parameter type, constructible from byte[][] (eg new
 *           P(byte[][]))
 */
public interface IParameterizedEffector<T extends ITarget, P> extends IEffector<T> {
	/**
	 * Parameterized effector invocation receives the index of the {@code P}
	 * instance to apply for the invocation.
	 *
	 * @param parameterIndex The index of the parameter object to be applied
	 * @return User-defined effectors should return 0
	 * @throws EffectorException on error
	 */
	int invoke(int parameterIndex) throws EffectorException;

	/**
	 * Allocate an array (<code>P[]</code>) to hold precompiled parameter objects
	 * 
	 * @param parameterCount the size of parameter array to allocate
	 * @return the allocated array
	 */
	P[] allocateParameters(int parameterCount);

	/**
	 * Compile a parameter value from effector arguments specified in
	 * model transducers. The parameter value, which may be a scalar or an
	 * array, is to be compiled from an array of byte arrays. 
	 *
	 * @param parameterTokens An array of parameters, where each parameter is an array of bytes.
	 * @return the compiled parameter value object
	 * @throws TargetBindingException on error
	 */
	P compileParameter(IToken[] parameterTokens) throws TargetBindingException;

	/**
	 * Get the raw parameter tokens array for an effector parameter.
	 *
	 * @param parameterIndex The index of the parameter 
	 * @return The raw parameter tokens array
	 */
	IToken[] getParameterTokens(int parameterIndex);

	/**
	 * Get the compiled parameter array.
	 *
	 * @return The parameter array
	 */
	P[] getParameters();

	/**
	 * Set precompiled parameters from proxy effector.
	 *
	 * @param proxy The proxy effector (<code>IParameterizedEffector&lt;T,P&gt;</code>) instance maintaining the precompiled paramwter objects
	 */
	void setParameters(Object proxy);

	/**
	 * Return the type of object compiled from, to support decompilation 
	 * 
	 * @param parameterIndex the parameter index
	 * @return a printable string representing the effector's parameter object type
	 */
	String showParameterType(int parameterIndex);

	/**
	 * Render tokens for a parameter object in a printable format, to support
	 * decompilation
	 * 
	 * @param parameterIndex the parameter index
	 * @return a printable string of space-delimited raw parameter tokens
	 */
	String showParameterTokens(int parameterIndex);
}
