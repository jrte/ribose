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

package com.characterforming.jrte;

/**
 * Interface for transduction target classes, which express IEffector instances
 * that are invoked from runtime transductions. Target implementations must
 * subclass and extend BaseTarget with specialized effectors.
 * <p>
 * Target classes must present a public default constructor with no arguments. A
 * proxy instance of the target implementation class is instantiated during
 * gearbox compilation to determine the names and types of the target effectors.
 * <p>
 * The {@link #bindEffectors()} method will be called on the proxy target at
 * the end of the gearbox compilation process to verify effector binding and
 * effector parameter compilation. At that time the
 * {@link IParameterizedEffector#newParameters(int)} and
 * {@link IParameterizedEffector#compileParameter(int, byte[][])} methods will be
 * called for each IParameterizedEffector.
 * <p>
 * At runtime, targets are bound only once to a transduction and the binding
 * persists for the lifetime of the target object. Applications use the
 * transduction to start and restart transducers and run them against available
 * inputs. The target effectors update the target state when the transduction is
 * run.
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
	public String getName();
	
	/**
	 * Bind a transduction and list all effectors presented by this target. This
	 * method is called to list the names and types of the effectors expressed
	 * by the target class and to bind the target effectors and effector
	 * parameters to the transduction.
	 * <p>
	 * Subclasses must include their effectors in the IEffector array returned.
	 * Each subclass in the target class hierarchy rooted in BaseTarget must
	 * add the number of effectors exported by the subclass to the count of 
	 * effectors exported by all of its subclasses and report the result to
	 * its superclass. BaseTarget class will allocate IEffector array and 
	 * fill in base transduction effectors, subclasses install their effectors
	 * on top of the superclass effectors, as shown below.
	 * 
	 * public IEffector&lt;?&gt;[] bindEffectors(int effectorCount) {
	 *    effectorCount += this.effectors.length;
	 *    IEffector&lt;?&gt;[] effectors = super.bindEffectors(effectorCount);
	 *    int effectorBase = effectors.length - effectorCount;
	 *    System.arrayCopy(this.effectors, 0, effectors, effectorBase, this.effectors.length);
	 *    return effectors; 
	 * }
	 * 
	 * After runtime binding, effectors are invoked when triggered by input
	 * transitions.
	 * 
	 * @return An array of IEffector instances bound to the target instance
	 * @throws TargetBindingException if an effector fails to bind
	 */
	public IEffector<?>[] bindEffectors() throws TargetBindingException;
}
