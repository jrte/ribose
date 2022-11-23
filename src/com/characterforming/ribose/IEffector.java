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

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for simple effectors. Effectors are invoked at runtime in response to state transitions in a running
 * transduction. They are typically anonymous inner classes defined in ITarget.bind().
 * 
 * @param <T> The effector target type
 * @author Kim Briggs
 */
public interface IEffector<T extends ITarget> {

	/**
	 * RTX bits are additive and accumulate as the effect vector is 
	 * executed for a transition. Most RTX bits reflect the action 
	 * of built-in effectors. Domain specific effectors should return
	 * only {@code RTX_NONE} (to continue transduction normally) or 
	 * {@code RTX_PAUSE} (to force resumable exit from run()).
	 * 
	 * Return RTX_NONE from effector.invoke() methods that do not
	 * affect the {@link ITransductor} transducer stack or input 
	 * stack.
	 */
	static final int RTX_NONE = 0;
	/**
	 * Return RTX_START only if invoke() action is to push the
	 * ITransductor transducer stack.
	 */
	static final int RTX_START = 1;
	/**
	 * Return RTX_STOP only if invoke() action is to pop the
	 * ITransductor transducer stack.
	 */
	static final int RTX_STOP = 2;
	/**
	 * Return RTX_PUSH only if invoke() action is to push the
	 * ITransductor input stack.
	 */
	static final int RTX_PUSH = 4;
	/**
	 * Return RTX_POP only if invoke() action is to pop the
	 * ITransductor input stack.
	 */
	static final int RTX_POP = 8;
	/**
	 * Return RTX_COUNT if invoke() action decremented the 
	 * counter for the current transducer to 0.
	 */
	static final int RTX_COUNT = 16;
	/**
	 * Return RTX_PAUSE from invoke() to force immediate and 
	 * resumable exit from run().
	 */
	static final int RTX_PAUSE = 32;
	/**
	 * Return RTX_STOPPED from invoke() to force immediate and 
	 * final exit from run().
	 */
	static final int RTX_STOPPED = 64;

	/**
	 * Returns the target that expresses the effector
	 * 
	 * @return The target that expresses the effector
	 */
	T getTarget();

	/**
	 * Returns the effector name. The name of the effector is the token 
	 * used to reference it in transducer definitions.
	 * 
	 * @return The effector name.
	 */
	Bytes getName();

	/**
	 * Receive an IOutput view of transduction named values. Named values are
	 * arrays of bytes extracted from transduction input. The transduction 
	 * will typically call an effector method to indicate when the value is
	 * available and the effector will use IOutput methods to extract the 
	 * value and assimilate the value into the target.
	 * 
	 * @param output A object that provides a view or transduction runtime values
	 * @throws TargetBindingException on error
	 */
	void setOutput(IOutput output) throws TargetBindingException;


	/**
	 * This method is invoked at runtime when triggered by an input transition.
	 * 
	 * @return User-defined effectors should return 0
	 * @throws EffectorException on error
	 */
	int invoke() throws EffectorException;
}
