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
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for parameterized effectors extends {@link IEffector} with a monadic
 * {@link #invoke(int)} method. In ribose transducer patterns parameters are
 * represented as lists of one or more backquoted tokens, eg {@code out[`~field` `,`]}.
 * In ginr automata and ribose transducers ginr tokens are represented as arrays
 * of raw bytes which may contain text, which ginr encodes as UTF-8 bytes, binary
 * data encoded using hexadecimal {@code \xHH} representation for unprintable
 * bytes, or field (`~field`), transducer (`@transducer`) or signal (`!signal`)
 * references.
 * <br><br>
 * <i>Proxy</i> parameterized effectors compile parameters from arrays of {@link
 * IToken} when a model is compiled and when it is loaded for runtime use. Each
 * token array is compiled to an immutable instance of the effector's parameter
 * type <b>P</b>, which must be constructible from {@code IToken[]}. The model
 * will call {@link #allocateParameters(int)} to obtain an array <b>P[]</b> of
 * parameter instances and {@link #compileParameter(IToken[])} for each parameter
 * to populate the array. When parameter compilation is complete the proxy effector
 * is passivated with a call to {@link #passivate()} and its raw tokens and compiled
 * parameters are retained for binding to live effectors. The {@link #invoke()}
 * and {@link #invoke(int)} methods are never called for proxy effectors. Proxy
 * effectors receive calls to {@link #showParameterTokens(int)} and {@link
 * #showParameterType()} from the model decompiler; these methods are implemented
 * in {@link BaseParameterizedEFfector} but may be overriden by subclasses.
 * <br><br>
 * 
 * <br><br>
 * For example:
 * <br><pre>
 * private final class DateEffector extends BaseParameterizedEffector&lt;Target, SimpleDateFormat&gt; {
 *   public SimpleDateFormat[] allocateParameters(int size) {
 *     super.parameters = new SimpleDateFormat[size];
 *   }
 * ...
 *   public SimpleDateFormat compileParameter(IToken[] parameterTokens) {
 *     String format = parameterTokens[0].getLiteral().toString(super.getDecoder());
 *     return new SimpleDateFormat(format);
 *   }
 * ...
 *   int invoke(int index) {
 *     SimpleDateFormat formater = super.getParameter(index);
 *     // get some field contents and format as date
 *     return RTX_NONE;
 *   }
 * }</pre>
 * <i>Live</i> parameterized effectors populate an array <b>P[]</b> of precompiled
 * parameters from their proxies via {@link #setParameters(Object)} when the model is
 * loaded into the runtime. Live effectors are not otherwise involved in parameter
 * compilation or decompilation and are never passivated. They reference their
 * parameters by array index in their {@link #invoke(int)} methods, which return an
 * {@code IEffector RTX} code as for {@link IEffector#invoke()}. Effectors normally
 * return {@code IEffector.RTX_NONE}, indicating no special condition, but may also
 * encode a {@link Signal} in the returned RTX code. See the javadoc comments for
 * {@link IEffector} for more information regarding effector {@code RTX} codes.
 * <br><br>
 * All {@code IParameterizedEffector} implementations must be subclasses of
 * {@link BaseParameterizedEffector}, which implements the parameter compilation
 * protocol and provides default implementations for some of the interface. Other
 * than immutability ribose places no constraints on effector implementation.
 * Most effector implementations are light weight, tightly focused and single
 * threaded. A parallel array of derivative objects can be allocated and
 * instantiated along with the parameters array if associated data are required.
 *
 * @author Kim Briggs
 * @param <T> the effector target type
 * @param <P> the effector parameter type, constructible from byte[][] (eg new P(byte[][]))
 */
public interface IParameterizedEffector<T extends ITarget, P> extends IEffector<T> {
	/**
	 * Parameterized effector invocation receives the index of the {@code P}
	 * instance to apply for the invocation.
	 *
	 * @param parameterIndex the index of the parameter object to be applied
	 * @return user-defined effectors should return 0 (RTX_SI)
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
	 * @param parameterTokens an array of parameters, where each parameter is an array of bytes.
	 * @return the compiled parameter value object
	 * @throws TargetBindingException on error
	 */
	P compileParameter(IToken[] parameterTokens) throws TargetBindingException;

	/**
	 * Return the type of the effector's parameter object, to support decompilation
	 * (generic parameter type is difficult to obtain by reflection).
	 * 
	 * @return a printable string representing the effector's parameter object type.
	 * This is implemented in {@link BaseParameterizedEffector} but may be overriden
	 * by subclasses.
	 */
	String showParameterType();

	/**
	 * Render tokens for a parameter object in a printable format, to support
	 * decompilation. This is implemented in {@link BaseParameterizedEffector}
	 * but may be overriden by subclasses.
	 * 
	 * @param parameterIndex the parameter index
	 * @return a printable string of space-delimited raw parameter tokens
	 */
	String showParameterTokens(int parameterIndex);
}
