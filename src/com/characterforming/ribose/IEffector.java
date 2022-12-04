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

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for simple effectors. Effectors are invoked at runtime in response to state 
 * transitions in a running transduction. They are typically implemented as anonymous
 * inner classes withinin a specialized {@link ITarget} implementation class.
 * <br><br>
 * Simple effectors present an {@link invoke()} method that is called from running 
 * transductions. Paramterized effectors implementing {@link IParameterizedEffector} may
 * also be included in ribose targets. The ribose {@link ITransductor} implmentation
 * also presents a core suite of built-in effectors that are accessible to all targets.
 * @param <T> The effector target type
 * @author Kim Briggs
 */
public interface IEffector<T extends ITarget> {

	/**
	 * RTX bits are additive and accumulate as the effect vector is 
	 * executed for a transition. Most RTX bits reflect the action 
	 * of built-in effectors. Domain specific effectors should return
	 * only {@code RTX_NONE} (to continue transduction normally).
	 * 
	 * Return RTX_NONE from effector.invoke() methods that do not
	 * affect the {@link ITransductor} transducer stack or input 
	 * stack.
	 */
	static final int RTX_NONE = 0;
	/**
	 * Transducer pushed onto the transducer stack.
	 */
	static final int RTX_START = 1;
	/**
	 * Transducer stack popped.
	 */
	static final int RTX_STOP = 2;
	/**
	 * Input (or signal) pushed onto the input stack.
	 */
	static final int RTX_PUSH = 4;
	/**
	 * ITransductor input stack popped.
	 */
	static final int RTX_POP = 8;
	/**
	 * Counter for the current transducer decremented to 0.
	 */
	static final int RTX_COUNT = 16;
	/**
	 * Force immediate and resumable exit from ITransductor.run().
	 */
	static final int RTX_PAUSE = 32;
	/**
	 * Force immediate and final exit from ITransductor.run().
	 */
	static final int RTX_STOPPED = 64;

	/**
	 * This method is invoked at runtime when triggered by an input transition.
	 * 
	 * @return User-defined effectors should return {@code IEffector.RTX_NONE}
	 * @throws EffectorException on error
	 */
	int invoke() throws EffectorException;

	/**
	 * Receive an IOutput view of transduction named values. Named values are
	 * arrays of bytes extracted from transduction input. The transduction 
	 * will typically call an effector method to indicate when the values are
	 * available and the effector will use IOutput methods to extract the 
	 * value and assimilate the value into the target.
	 * 
	 * @param output A object that provides a view or transduction runtime values
	 * @throws TargetBindingException on error
	 */
	void setOutput(IOutput output) throws TargetBindingException;

	/**
	 * Returns the target that expresses the effector
	 * 
	 * @return The target that expresses the effector
	 */
	T getTarget();

	/**
	 * Returns the effector name. The name of the effector is the token 
	 * used to reference it in transducer patterns.
	 * 
	 * @return The effector name.
	 */
	Bytes getName();

	/**
	 * Test two effector instances for functional equivalence. Effector
	 * instances are equivalent if they have identical classes and effector
	 * names. This is strict equivalence -- will not hold if there is an
	 * inheritance relationship between this and other classes -- but does
	 * not guarantee or presume {@code this.equals(other)}.
	 * 
	 * @param other the other effector instance
	 * @return true if {@code this} is equivalent to {@code other}
	 */
	default boolean equivalent(final IEffector<?> other) {
		return this.getClass().equals(other.getClass())
		&& this.getName().equals(other.getName());
	}
}
