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

import com.characterforming.ribose.base.SimpleTarget;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for transduction target classes, which express {@link IEffector}
 * and {@link IParameterizedEffector} instances that are invoked from runtime
 * transductions. The runtime {@code Transductor} class, which encapsulates
 * ribose transductions, implements {@code ITarget} and provides the base ribose
 * effectors that are accessible to all transducers. These are listed in the
 * {@link ITransductor} documentation. The {@link SimpleTarget} class exposes
 * <i>only</i> the {@code Transductor} effectors and is included to support
 * simple models that do not require specialized effectors. Specialized 
 * {@code ITarget} implementations may supplement these with additional
 * effectors.
 * <br><br>
 * {@code ITarget} implementations must present a public default constructor
 * with no arguments. This is used to instantiate proxy instances to enumerate
 * effectors and precompile effector parameters. A proxy target instantiates
 * an array of proxy effectors that are used only for parameter precompilation.
 * See the {@link IParameterizedEffector} documentation for more information
 * regarding the effector parameter binding protocol. At runtime, a live target
 * instance is bound to a transductor and the binding persists for the lifetime
 * of the transductor. Live runtime targets are instantiated externally and
 * passed to the {@link IModel} methods to create {@code ITransductor}
 * instances.
 * Live effectors receive proecompiled parameters from the proxy effectors
 * retained in the model.
 * <br><br>
 * Targets need not be monolithic. In fact, every ribose transduction involves a
 * composite target comprised of the {@link ITransductor} implementation class,
 * which implements the {@code ITarget} interface and exports the base ribose
 * effectors, and at least one other {@code ITarget instance} (eg, {@link
 * SimpleTarget}). To construct a composite target, select one target class as
 * the representative target to present to the transduction. The representative
 * target calls {@link #getEffectors()} on each of the subordinate {@code ITarget}
 * instances and merges their effectors with its own effectors into a single
 * array to return when its {@link #getEffectors()} method is called from the
 * ribose compiler or runtime. Composite targets may be especially useful for
 * separating concerns within complex semantic domains, as they encapsulate
 * discrete semantic models in tightly focussed and reusable target classes.
 * <br><br>
 * For example, a validation model containing a collection of transducers that
 * syntactically recognize domain artifacts would be bound to a target expressing
 * effectors to support semantic validation. The validation model and target, 
 * supplied by the service provider, can then be combined with specialized models
 * in receiving domains to obtain a composite model including provider validation
 * and consumer reception models and effectors. With some ginr magic reception
 * patterns can be joined with corresponding validation patterns to obtain
 * transductors that validate input in stepwise synchrony with reception and
 * assimilation into the receiving domain. 
 * <br><br>
 * The {@link SimpleTarget} class is included in the ribose jar file and exposes
 * the base transductor effectors listed in the {@link ITransductor}
 * documentation. This target can be used to construct models that contain only
 * streaming transducers, which use only the transductor effectors and stream
 * transduction output through the {@code out[..]} effector.
 *
 * @author Kim Briggs
 */
public interface ITarget {
	/**
	 * Get the name of the target as referenced in transducers that use the
	 * target.
	 *
	 * @return the name of of this target
	 */
	String getName();

	/**
	 * Presents the effectors expressed by the target class for binding to the runtime
	 * model. Implementation classes must include their effectors in the IEffector
	 * array returned. After runtime binding is complete effectors are invoked when
	 * triggered by running transductions in response to syntactic cues in the
	 * input stream.
	 * <br><br>
	 * Each effector should be uniquely referenced from the returned array and used
	 * solely by the {@link ITransductor} instance bound to the target. It is
	 * unwise and unnecessary for the target to interact with its effectors unless
	 * the interaction is driven from an effector invokation.
	 *
	 * @return an array of IEffector instances bound to the target instance
	 * @throws TargetBindingException if things don't work out
	 */
	IEffector<?>[] getEffectors() throws TargetBindingException;
}
