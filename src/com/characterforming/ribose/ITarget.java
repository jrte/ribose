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
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for transduction target classes, which express {@link IEffector}
 * and {@link IParameterizedEffector} instances that are invoked from runtime
 * transductions. The runtime {@code Transductor} class, which encapsulates
 * ribose transductions, implements {@code ITarget} and presents the base ribose
 * effectors that are accessible to all transductions. Specialized {@code ITarget}
 * implementations may supplement these with specialized effectors.
 * <br><br>
 * {@code ITarget} implementations must present a public default constructor
 * with no arguments. This is used to instantiate proxy instances to enumerate
 * effectors and precompile effector parameters. Proxy targets are instantiated
 * using the default constructor for the target class. The proxy target instantiates
 * an array of proy effectors that are used only for parameter precompilation. The
 * {@link ITarget#getEffectors()} method is called to obtain an enumeration of the
 * target's effectors. In the proxy target, for each parameterized proxy effector, 
 * {@link IParameterizedEffector#allocateParameters(int)} is called first, to allocate
 * an array to contain the precompiled parameters. The model compiler or loader then calls 
 * the proxy effector's {@link IParameterizedEffector#compileParameter(byte[][])}
 * method for each parameter. No other methods are called on the proxy target or
 * effectors during model compilation and loading, and proxy target and effector
 * instances are never involved in live transduction processes.
 * <br><br>
 * At runtime, a live target instance is bound to a transductor and the binding
 * persists for the lifetime of the transductor. Live runtime targets are instantiated
 * externally and passed to the {@link IRuntime#transductor(ITarget)} method
 * to create {@code ITransductor} instances. Live target effectors receive precompiled
 * parameters via {@link IParameterizedEffector#setParameters(Object)} method, which 
 * is fully implemented in {@link BaseParameterizedEffector} for all {@code IParameterizedEffector}
 * implemenations. Subsequently, only the {@link IParameterizedEffector#invoke()} and
 * {@link IParameterizedEffector#invoke(int)} methods are called on live effector instances.
 * <br><br>
 * Targets need not be monolithic. In fact, every ribose transduction involves a
 * composite target comprised of the {@link ITransductor} implementation class, which
 * implements the {@code ITarget} interface and exports the base ribose effectors, and
 * at least one other {@code ITarget instance} (eg, {@link TCompile}, {@link TRun}).
 * To construct a composite target, select one target class as the representative
 * target to present to the transduction. The representative target instantiates and
 * calls {@link #getEffectors()} on each of the subordinate {@code ITarget}
 * instances and merges their effectors with its own effectors into a single array
 * to return when its {@link #getEffectors()} method is called from the ribose
 * compiler or runtime. Composite targets may be especially useful for separating
 * concerns within complex semantic domains, as they encapsulate discrete semantic
 * models in tightly focussed and reusable target classes.
 *
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
	 * Lists the effectors expressed by the target class for binding to the runtime model.
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
}
