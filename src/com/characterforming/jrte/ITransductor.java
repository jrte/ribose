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

import com.characterforming.jrte.base.Bytes;

/**
 * Interface for runtime transductors. A transductor binds an IInput stack,
 * transducer stack, and an ITarget instance. When the run() method is called
 * the transduction will read input ordinals from the IInput on the top of the
 * input stack and invoke the effectors triggered by each input transition
 * until one of the following conditions is satisfied:
 * <ol>
 * <li>the input stack is empty
 * <li>the transducer stack is empty
 * <li>an effector returns RTE_PAUSE
 * <li>an exception is thrown
 * </ol>
 * As long as the input and transducer stacks are not empty it may be possible
 * to call the run() method again after it returns if, for example, the pause
 * effector causes the previous call to return or a DomainErrorException was
 * thrown and you want to persevere after driving the input to a recognizable
 * location (eg, end of nearest containing loop).
 * <p>
 * Domain errors (inputs with no transition defined) are handled by emitting a
 * nul signal, giving the transduction an opportunity to handle it with an
 * explicit transition on nul. For most text transducers, domain errors can be 
 * handled by transducing the transducer. For example, with line-oriented text, 
 * all possible interleavings of domain errors in the input can be modeled by replacing
 * each non-nl input (x) with (x|nul) in the original transducer definition. The 
 * resulting transducer can then be pruned to produce a hardened transducer that accepts
 * ((any - nl)* nl)* and silently resynchronizes with the input after a domain error. If 
 * a domain error occurs on a nul signal, a {@link DomainErrorException} is thrown. 
 * <p>
 * IInput.get() will return null and force eos whenever it is called after the
 * last input ordinal is returned from the input stack. Transducers can explicitly handle
 * this by including a transition on eos. If eos is not explicitly handled the transduction
 * will simply stop and {@link status()} will return {@link Status#STOPPED}.
 * 
 * @author Kim Briggs
 */
public interface ITransductor extends ITarget {
	
	/**
	 * Transduction status.
	 * 
	 * @author Kim Briggs
	 */
	enum Status {
		/**
		 * Transduction is invalid and inoperable in runtime
		 */
		NULL,
	
		/**
		 * Transduction stack is empty, input stack may or may not be empty  
		 */
		STOPPED,
	
		/**
		 * Transduction stack not empty, input stack is empty.
		 */
		PAUSED,
	
		/**
		 * Transduction stack not empty, input stack not empty.
		 */
		RUNNABLE
	} 

	/**
	 * Test the status of the transduction's input and transducer stacks.
	 * 
	 * A RUNNABLE transduction can be resumed immediately by calling run(). A PAUSED 
	 * transduction can be resumed when new input is pushed. Transducers may deliberately
	 * invoke the {@code pause} effector to break out of run() with transducer and input
	 * stacks not empty to allow the caller to take some action before calling run() to 
	 * resume the transduction. A STOPPED transduction can be reused to start a new 
	 * transduction with a different transducer stack and new input after calling stop()
	 * to reset the transduction stack to its original bound state.
	 * 
	 * @return Transduction status
	 */
	public Status status();
	
	/**
	 * Set up or reset a transduction with a specified transducer at its start
	 * state on top of the transducer stack. To provide input, call the
	 * {@link #input(IInput[])} method before running the transduction.
	 * 
	 * @param transducer The name of the transducer to start
	 * @return Run status of transduction at point of return 
	 * @throws ModelException 
	 */
	public Status start(Bytes transducer) throws ModelException;

	/**
	 * Set up transduction inputs.
	 * 
	 * @param inputs Initial (or additional) inputs in LIFO order, inputs[0] is last out
	 * @return Run status of transduction at point of return 
	 */
	public Status input(IInput[] inputs);

	/**
	 * Run the transduction with current input until the input or transduction
	 * stack is empty, or an effector returns RTE_PAUSE, or an exception is thrown.
	 * 
	 * @return Run status of transduction at point of return 
	 * @see #status()
	 * @throws RteException 
	 * @throws DomainErrorException 
	 */
	public Status run() throws RteException, DomainErrorException;
	
	/**
	 * Return the number of domain errors counted in the most recent run() call. A
	 * domain error occurs when no transition is defined from current state for 
	 * current input. 
	 * 
	 * @return the number of domain errors counted in the most recent run() call.
	 */
	public int getErrorCount();

	/**
	 * Clear input and transducter stacks. A {@code stop()} will be sent
	 * to any inputs that are popped from the input stack. This resets
	 * the transductor to original factory state ready for reuse.
	 * 
	 * @return {@link Status#STOPPED} 
	 * @see #status()
	 */
	public Status stop();
}
