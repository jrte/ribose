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

import com.characterforming.ribose.base.BaseParametricEffector;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for parametric effectors extends {@link IEffector} with a monadic
 * {@link #invoke(int p)} method that selects a <b>P</b> instance from an enumerated
 * set of immutable parameter instances. Implementation classes must extend
 * {@link BaseParametricEffector}, which implements the parameter compilation,
 * binding and access protocols and exposes {@code BaseParametricEffector.P[]}
 * as a protected field for direct subclass access to compiled parameters.
 * <br><br>
 * Each {@link IParametricEffector} implementation is a template for an indexed
 * collection of {@link IEffector}. In ribose models, they are specialized by the
 * union of parameter lists bound to their effectors in transducer patterns. In
 * ribose transducer patterns effector parameters are represented as lists of one
 * or more backquoted tokens, eg {@code out[`~field` `,`]}. In ginr compiled FSTs
 * and in ribose transducers these tokens are represented as arrays of raw bytes
 * which may contain UTF-8 encoded text, binary data encoded using hexadecimal
 * {@code \xHH} representation for unprintable bytes, or field (`~field`),
 * transducer (`@transducer`) or signal (`!signal`) references. Tokens are
 * presented in the {@link IToken} interface.
 * <br><br>
 * Parametric effectors compile their parameters when a model is created and
 * again whenever it is loaded for runtime use. Each parameter is compiled from
 * an array of {@link IToken} ({@code IToken[] -> P}) to an immutable instance of
 * <b>P</b>. Parameter compilation is performed using <i>proxy</i> target,
 * transductor and effector instances, which are never involved in live
 * transductions. A simple parameter compilation protocol is implemented in
 * {@link BaseParametricEffector}, which all {@code IParametricEffector}
 * implementations must extend:
 * <ol>
 * <li> model calls <i>subclassEffector</i>.allocateParameters(int n)
 * <ul><li>allocates new P[n]</ul>
 * <li> model calls <i>superclassEffector</i>.compileParameters(IToken[][], List)
 * <ul><li>sets P[i] = <i>subclassEffector</i>.compileParameter(IToken[]) for i&lt;n</ul>
 * <li> model calls <i>subclassEffector</i>.passivate()
 * <ul><li>if overridden, <i>subclassEffector</i> must call <i>superclassEffector</i>.passivate()
 * 	<ul><li><i>superclassEffector</i>.target = null<li><i>superclassEffector</i>.output = null</ul>
 * </ul>
 * </ol>
 * Proxy effectors may receive calls to {@link #showParameterType()} and {@link
 * #showParameterTokens(int)} from the model decompiler; these methods are implemented
 * in {@link BaseParametricEffector} but may be overridden by subclasses. The
 * {@link IEffector#invoke()} and {@link #invoke(int)} methods are never called for
 * proxy effectors.
 * <br><br>
 * For example:
 * <br><pre>
 * private final class DateEffector extends BaseParametricEffector&lt;Target, SimpleDateFormat&gt; {
 *   public SimpleDateFormat[] allocateParameters(int size) {
 *     super.parameters = new SimpleDateFormat[size];
 *   }
 * ... // in proxy effector
 *   public SimpleDateFormat compileParameter(IToken[] parameterTokens) {
 *     String format = parameterTokens[0].asString();
 *     return new SimpleDateFormat(format);
 *   }
 * ... // in live effector
 *   int invoke(int index) {
 *     SimpleDateFormat formatter = super.parameters[index];
 *     // get some field contents and format as date
 *     return RTX_NONE;
 *   }
 * }</pre>
 * In live contexts the precompiled parameters are retained in the live model
 * and are available for referential sharing across live transductors bound to the
 * model. <i>Live</i> parametric effectors receive a reference to the precompiled
 * parameters {@code P[]} via {@link BaseParametricEffector#setParameters(IParametricEffector)}
 * when they become bound to a transductor. Live effectors are never passivated and
 * may receive a series of {@link #invoke()} and {@link #invoke(int p)} methods,
 * which return an {@code RTX} code as for {@link IEffector}.
 * <br><br>
 * All {@code IParametricEffector} implementations must be subclasses of
 * {@link BaseParametricEffector}. Other than immutability ribose places
 * no constraints on effector implementation. Most effector implementations
 * are light weight, tightly focused and single threaded. A parallel array
 * of derivative objects can be allocated and instantiated along with the
 * parameters array if associated data are required.
 *
 * @author Kim Briggs
 * @param <T> the effector target type
 * @param <P> the effector parameter type, constructible from byte[][] (eg new P(byte[][]))
 * @see IEffector
 * @see BaseParametricEffector
 */
public interface IParametricEffector<T extends ITarget, P> extends IEffector<T> {
	/**
	 * Parametric effector invocation receives the index of the <b>P</b>
	 * instance to apply for the invocation. Normally implementation will return
	 * {@code IEffector.RTX_NONE}, which has no effect. In some cases a signal may
	 * be encoded in the return value using {@code IEffector.signal(sig)}, where
	 * {@code sig} is the ordinal value (&gt;255) of the signal. In that case the
	 * decoded signal will be used to trigger the next transition.
	 *
	 * @param parameterIndex the index of the parameter object to be applied
	 * @return {@code IEffector.RTX_NONE} or {@code IEffector.signal(signalOrdinal)}
	 * @throws EffectorException if things don't work out
	 */
	int invoke(int parameterIndex)
	throws EffectorException;

	/**
	 * Allocate an array (<b>P</b>[]) to hold precompiled parameter objects
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
	 * @throws TargetBindingException if things don't work out
	 */
	P compileParameter(IToken[] parameterTokens)
	throws TargetBindingException;

	/**
	 * Return the type of the effector's parameter object, to support decompilation
	 * (generic parameter type is difficult to obtain by reflection).
	 *
	 * @return a printable string representing the effector's parameter object type.
	 * This is implemented in {@link BaseParametricEffector} but may be overriden
	 * by subclasses.
	 */
	String showParameterType();

	/**
	 * Render tokens for a parameter object in a printable format, to support
	 * decompilation. This is implemented in {@link BaseParametricEffector}
	 * but may be overriden by subclasses.
	 *
	 * @param parameterIndex the parameter index
	 * @return a printable string of space-delimited raw parameter tokens
	 */
	String showParameterTokens(int parameterIndex);
}
