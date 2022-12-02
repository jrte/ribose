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

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for transduction target classes, which express IEffector instances
 * that are invoked from runtime transductions. The runtime Transductor class,
 * which encapsulate ribose transductions, presents a core set of built-in
 * effectors that are accessible to all transductions. Specialized ITarget
 * implementations supplement these with specialized effectors.
 * <br><br>
 * Target classes must present a public default constructor with no arguments. A
 * proxy instance of the target implementation class is instantiated during
 * model compilation to determine the names and types of the target effectors
 * and to compile and validate effector parameters. At that time, for each effector,
 * the {@link IParameterizedEffector#newParameters(int)} will be called first and
 * then {@link IParameterizedEffector#compileParameter(int, byte[][])} will be
 * called for each IParameterizedEffector.
 * <br><br>
 * At runtime, each target is bound only once to a transductor and the binding
 * persists for the lifetime of the transductor. Note that transductors are 
 * restartable and reuseable and may serially run more than one transduction
 * using the same target. Each transduction, including an ITransductor, an 
 * ITarget and its IEffectors, is intended to be a single-threaded process. 
 * Explicit synchronization is required to ensure safety if multiple threads
 * are involved.
 * <br><br>
 * {@code ITarget} implementations must provide a nullary constructor receiving no 
 * arguments. Target classes are instantiated in ribose compilation contexts, where
 * they serve as proxies for effector enumeration and compilation of effector 
 * parameters, and in runtime contexts. In compilation context, the ribose compiler
 * calls {@code getEffectors()} to enumerate the target effectors and calls the 
 * {@code IEffector} methods {@code newParameters(int)} (to set parameter array in 
 * the effector) and {@code compileParameter(int, bye[][])} for each {@code 
 * ParameterizedEffector}. No other {@code ITarget} methods are called during
 * compilation. No other methods are called on the proxy target or effectors
 * during model compilation. 
 * <br><br>
 * In runtime contexts, a proxy target is instantiated when a ribose model is loaded.
 * The proxy recompiles all parammeterized effector parameters to make them available
 * to runtime targets and is otherwise not involved in runtime model use. Runtime targets
 * are instantiated externally and passed to the runtime to create {@code ITransductor}
 * instances to run transductions. When a runtime target is bound to a transductor each
 * parameterized effector receives a call to {@code setParameter(int, Object)} to set
 * the precompiled parameter for the specified parameter index. 
 * @author Kim Briggs
 */
public interface ITarget {
	/**
	 * Get the name of the target as referenced in transducers that use the
	 * target.
	 * 
	 * @return The name of of this target
	 */
	String getName();
	
	/**
	 * Lists the names and types of the effectors expressed by the target class
	 * for binding to the runtime model.
	 * <br><br>
	 * Implementation classes must include their effectors in the IEffector 
	 * array returned. Targets may be composite, arranged in an inheritance
	 * chain or encapsulated in discrete component classes, or a mixture of
	 * these. In any case, the top-level target class is responsible for gathering
	 * effectors from all involved target instances into a single array to present
	 * when the target is bound to a runtime model. 
	 * 
	 * After runtime binding is complete effectors are invoked when triggered
	 * by running transductions in response to cues in the input stream.
	 * 
	 * @return An array of IEffector instances bound to the target instance
	 * @throws TargetBindingException on error
	 */
	IEffector<?>[] getEffectors() throws TargetBindingException;

	/**
	 * Return a new @{code CharsetDecoder} instance for use by the target and
	 * the effectors bound to the target. This is a shared instance, safe for
	 * use within single-threaded transductions. Effectors should access this
	 * instance through {@link com.characterforming.ribose.base.BaseEffector#decoder},
	 * whish 
	 * 
	 * @return the @{code CharsetDecoder} to be used with this target instance
	 */
	CharsetDecoder getCharsetDecoder();

	/**
	 * Return a new @{code CharsetEncoder} instance for use by the target and
	 * the effectors bound to the target. This is a shared instance, safe for
	 * use within single-threaded transductions. Effectors should access this
	 * instance through {@link com.characterforming.ribose.base.BaseEffector#encoder}.
	 * 
	 * @return the @{code CharsetEncoder} to be used with this target instance
	 */
	CharsetEncoder getCharsetEncoder();
}
