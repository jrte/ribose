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

import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for parameterized effectors extends {@link IEffector} with a monadic
 * {@link #invoke(int)} method. Parameters are compiled from arrays of byte arrays
 * into an array of some parameter type <b>P</b> that is constructible from one or more
 * arrays of bytes. Compiled parameter values are referenced by their index in
 * the resulting array. At runtime, the {@link #invoke(int)} method is called
 * with the parameter index to indicate which instance of <b>P</b> to apply
 * to the invocation. This method returns an {@link IEffector} {@code RTX} value
 * as for {@link IEffector#invoke()}; see the javadoc comments for {@link IEffector}
 * for instructions regarding {@code RTX} codes.
 * <br><br>
 * Parameterized effectors are required to construct an array <b>P[]</b> of parameter
 * instances when a ribose model is compiled and again when a ribose model is loaded
 * into a ribose runtime. The runtime will call {@link #newParameters(int)} to
 * set the size of the parameter array and then call {@link #compileParameter(int, byte[][])}
 * for each parameter list. In ribose transducer patterns parameters are presented
 * to effectors on tape 2 (the parameter tape) as a list of one or more backquoted
 * tokens, eg {@code out[`~field` `,`]}. Parameter tokens may contain text, which
 * ginr encodes as UTF-8 bytes, binary data encoded using {@code \xHH} hexadecimal
 * representation for unprintable bytes, or fields (`~field`), transducer
 * names (`@transducer`) or signals (`!signal`). In compiled ribose models parameters
 * are rendered as arrays of byte arrays ({@code byte[][]}) to be presented to
 * {@link #compileParameter(int, byte[][])}. The effector implementation must compile
 * each parameter list to an instance of the effectors parameter type <b>P</b>.
 * <br><br>
 * For example, a {@code date[]} effector might accept date format strings as parameters
 * and compile them to {@code DateFormat} instances in its parameter array. At runtime,
 * the date effector will be invoked with an integer indicating the index of the
 * date formatter to be applied to render the UTF-8 bytes in the selected field
 * ({@link IOutput#getSelectedField()}) canonically as a long integer.
 * <br><br>
 * In runtime contexts the effector is invoked in a proxy target to precompile
 * its parameters as above. The precompiled parameters are passed on to the
 * effector instance in the live runtime target via {@link #setParameter(int, Object)}.
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
	 * @param parameterIndex The array index in the parameters array P[] to set with the parameter value
	 * @param parameterList An array of parameters, where each parameter is an array of bytes.
	 * @return the compiled parameter value object
	 * @throws TargetBindingException on error
	 */
	P compileParameter(int parameterIndex, byte[][] parameterList) throws TargetBindingException;

	/**
	 * Set a precompiled parameter value.
	 *
	 * @param parameterIndex The array index in the parameters array P[] to set with the parameter value
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
	 * Get the compiled parameter array.
	 *
	 * @return The parameter array
	 */
	P[] getParameters();

	/**
	 * Render a parameter object in a printable format
	 * @param parameterIndex the parameter index
	 * @return a printable string
	 */
	default String showParameter(int parameterIndex) {
		return Integer.toString(parameterIndex);
	}

	/**
	 * Parameterized effector invocation receives the index of the {@code P}
	 * instance to apply for the invocation.
	 *
	 * @param parameterIndex The index of the parameter object to be applied
	 * @return User-defined effectors should return 0
	 * @throws EffectorException on error
	 */
	int invoke(int parameterIndex) throws EffectorException;
}
