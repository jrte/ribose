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
	 * Return RTE_EFFECT_NONE from effector.invoke() methods that do not
	 * affect the ITransductor transducer stack or input stack.
	 */
	public static final int RTE_EFFECT_NONE = 0;
	/**
	 * Return RTE_EFFECT_START from effector.invoke() methods that push the
	 * ITransductor transducer stack.
	 */
	public static final int RTE_EFFECT_START = 1;
	/**
	 * Return RTE_EFFECT_STOP from effector.invoke() methods that pop the
	 * ITransductor transducer stack.
	 */
	public static final int RTE_EFFECT_STOP = 2;
	/**
	 * Return RTE_EFFECT_SHIFT from effector.invoke() methods that replace
	 * the top transducer on the ITransductor transducer stack.
	 */
	public static final int RTE_EFFECT_SHIFT = 4;
	/**
	 * Return RTE_EFFECT_PUSH from effector.invoke() methods that push the
	 * ITransductor input stack.
	 */
	public static final int RTE_EFFECT_PUSH = 8;
	/**
	 * Return RTE_EFFECT_POP from effector.invoke() methods that pop the
	 * ITransductor input stack.
	 */
	public static final int RTE_EFFECT_POP = 16;
	/**
	 * Return RTE_EFFECT_PAUSE from an effector.invoke() method to force
	 * immediate and resumable exit from run().
	 */
	public static final int RTE_EFFECT_PAUSE = 32;
	/**
	 * Return RTE_EFFECT_STOPPED from an effector.invoke() method to force
	 * immediate and final exit from run().
	 */
	public static final int RTE_EFFECT_STOPPED = 64;

	/**
	 * Returns the target that expresses the effector
	 * 
	 * @return The target that expresses the effector
	 */
	public T getTarget();

	/**
	 * Returns the effector name. The name of the effector is the token 
	 * used to reference it in transducer definitions.
	 * 
	 * @return The effector name.
	 */
	public Bytes getName();

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
	public void setOutput(IOutput output) throws TargetBindingException;


	/**
	 * This method is invoked at runtime when triggered by an input transition.
	 * 
	 * @return User-defined effectors should return 0
	 * @throws EffectorException on error
	 */
	public int invoke() throws EffectorException;
}
