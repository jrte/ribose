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

import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;

/**
 * Interface for runtime transductors. A transductor binds an IInput stack,
 * transducer stack, and an ITarget instance. When the run() method is called
 * the transduction will read input and invoke the effectors triggered by each
 * input transition until one of the following conditions is satisfied:
 * <br><br>
 * <ol>
 * <li>the input stack is empty
 * <li>the transducer stack is empty
 * <li>an effector returns {@code IEffector.RTE_EFFECT_PAUSE}
 * <li>an exception is thrown
 * </ol>
 * <br><br>
 * As long as the input and transducer stacks are not empty it may be possible
 * to call the run() method again after it returns if, for example, the pause
 * effector causes the previous call to return or a DomainErrorException was
 * thrown and you want to persevere after driving the input to a recognizable
 * location (eg, end of nearest containing loop).
 * <br><br>
 * Domain errors (inputs with no transition defined) are handled by emitting a
 * nul signal, giving the transduction an opportunity to handle it with an
 * explicit transition on nul. For most text transducers, domain errors can be 
 * handled by transducing the transducer. For example, with line-oriented text, 
 * all possible interleavings of domain errors in the input can be modeled by replacing
 * each non-nl input (x) with (x|nul) in the original transducer definition. The 
 * resulting transducer can then be pruned to produce a hardened transducer that accepts
 * ((any - nl)* nl)* and silently resynchronizes with the input after a domain error. If 
 * a domain error occurs on a nul signal, a {@link DomainErrorException} is thrown. 
 * <br><br>
 * The transductor will send an {@code eos} signal to the transduction the input stack runs
 * dry. Transducers can explicitly handle  this by including a transition on {@code eos}.
 * If eos is not explicitly handled the transduction will simply stop and {@link status()}
 * will return {@link Status#STOPPED}.
 * 
 * @author Kim Briggs
 */
public interface ITransductor extends ITarget {
	
	/**
	 * Transduction status.
	 * 
	 * @author Kim Briggs
	 */
	public enum Status {
		/**
		 * Transduction stack not empty, input stack not empty.
		 */
		RUNNABLE,
		/**
		 * Transduction stack empty, input stack not empty  
		 */
		WAITING,
		/**
		 * Transduction stack not empty, input stack empty.
		 */
		PAUSED,
		/**
		 * Transduction stack empty, input stack empty  
		 */
		STOPPED,
		/**
		 * Transduction is invalid and inoperable in runtime
		 */
		NULL;
		
		public boolean isRunnable() {
			return this.equals(RUNNABLE);
		}
		
		public boolean hasInput() {
			return this.equals(RUNNABLE) || this.equals(PAUSED);
		}
		
		public boolean hasTransducer() {
			return this.equals(RUNNABLE) || this.equals(WAITING);
		}
		
		public boolean isStopped() {
			return this.equals(STOPPED);
		}
	} 

	/**
	 * Test the status of the transduction's input and transducer stacks.
	 * 
	 * A RUNNABLE or PAUSED transduction can be resumed immediately by calling run(). A 
	 * WAITING transduction can be resumed when new input is pushed. Transducers may 
	 * deliberately invoke the {@code pause} effector to break out of run() with transducer
	 * and input stacks not empty to allow the caller to take some action before calling
	 * run() to resume the transduction. A STOPPED transduction can be reused to start a new 
	 * transduction with a different transducer stack and new input after calling stop()
	 * to reset the transduction stack to its original bound state.
	 * 
	 * @return Transduction status
	 */
	public Status status();

	/**
	 * Push an initial segment {@code [0..limit)} of a data array onto the 
	 * transduction input stack. Data pushed onto empty stack constitute the
	 * primary input stream for the purpose or marking and resetting via
	 * the `mark` and `reset` effectors. These effectors project their effect 
	 * onto the primary input stream if they are invoked while other data are
	 * on top of the primary input stack frame.  
	 * 
	 * @param input data to push onto input stack for immediate transduction
	 * @param limit truncate effective input range at {@code max(limit, data.length)}  
	 * @return Run status of transduction at point of return 
	 */
	public Status input(byte[] input, int limit);
	
	/**
	 * Push a transducer onto the transductor's transducer stack and set it state
	 * to the initial state. The topmost (last) pushed transducer will be activated 
	 * when the {@code run()} method is called.
	 * 
	 * @param transducer The name of the transducer to push
	 * @return Run status of transduction at point of return 
	 * @throws ModelException on error
	 */
	public Status start(Bytes transducer) throws ModelException;

	/**
	 * Push a signal onto the transductor's input stack to trigger next transition. 
	 * 
	 * @param signal the signal to push 
	 * @return Run status of transduction at point of return 
	 */
	public Status signal(Signal signal);

	/**
	 * Run the transduction with current input until the input or transduction
	 * stack is empty, trabsduction paues or stops, or an exception is thrown. 
	 * This method should be called repeatedly until {@code status().hasInput()}
	 * returns {@code false}, although this check can be ignored if it is known 
	 * that the {@code pause} effector is not engaged in the transduction.
	 * <br><br>
	 * If a mark is set, it applies to the primary input stream and marked input
	 * buffers held in the transduction mark set cannot be reused by the caller 
	 * until a new mark is set or the input has been reset and all marked buffers
	 * have been transduced. Call {@link ITransductor#hasMark()} before reusing 
	 * data buffers if the transduction involves backtracking with mark/reset. 
	 * 
	 * @return status of transduction at point of return 
	 * @throws RiboseException on error
	 * @throws DomainErrorException on error
	 * @see #status()
	 */
	public Status run() throws RiboseException, DomainErrorException;

	/**
	 * Clear input and transductor stacks and reset all named values to
	 * an empty state. This resets the transductor to original state 
	 * ready for reuse.
	 * 
	 * @return {@link Status#STOPPED} 
	 * @see #status()
	 */
	public Status stop();
	
	/**
	 * Check whether the input stack is marked. Byte arrays passed as input
	 * to the transductor after a mark is set are retained by the transductor
	 * and MUST NOT be subsequently reused as input containers. They will be
	 * released for garbase collection when the input stack is reset to the
	 * mark or the mark is cleared. 
	 * 
	 * Call this method after calling run() if status().hasInput() returns
	 * false and do not reuse any data buffers that were passed as input() 
	 * since marking commenced. 
	 * 
	 * @return true if a mark is set
	 */
	public boolean hasMark();
	
	/**
	 * If the {@code bytes} buffer is in the mark set try to recycle the buffer
	 * from a previously reset input, or return null to force caller to acquire
	 * a new buffer. Otherwise return the unmarked {@code bytes} buffer.
	 * 
	 * @param bytes the most recently used buffer
	 * @return an empty data buffer or null
	 */
	public byte[] recycle(byte[] bytes);
	
	/**
	 * Return the number of domain errors counted in the most recent run() call. A
	 * domain error occurs when no transition is defined from current state for 
	 * current input. 
	 * 
	 * @return the number of domain errors counted in the most recent run() call.
	 */
	public int getErrorCount();
}
