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

import java.io.OutputStream;

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;

/**
 * Interface for runtime transductors. A transductor binds an IInput stack,
 * transducer stack, and an ITarget instance. When the {@link run()} method
 * is called the transductor will read input and invoke the effectors triggered
 * by each input transition until {@link status()} indicates that the transduction
 * is not runnable. Then one of the following conditions is satisfied:
 * <br><br>
 * <ul>
 * <li>the input stack is empty ({@link Status#PAUSED})
 * <li>the transducer stack is empty ({@link Status#WAITING})
 * <li>input and transducer stack are both empty ({@link Status#STOPPED})
 * <li>a transducer invokes the {@code pause} effector {@link Status#PAUSED}
 * <li>an exception is thrown
 * </ul>
 * <br>
 * A paused transduction can be resumed when input is available ({@code push()}).
 * A waiting transduction can be resumed by starting a transducer ({@code start()}).
 * A stopped transducer can be reused to start a new transduction by pushing new
 * input and transducers. The {@code run()} method has no effect when the transducer
 * or input stack is empty. The {@code stop()} method should be called before starting
 * a new transduction, to ensure that the transducer and input stacks and all values
 * are cleared. The {@code stop() method will throw a {@code RiboseException} when 
 * it is called on a proxy transductor (a transductor instantiated for parameter 
 * compilation in model construction or runtime model loading contexts) or otherwise
 * nonfunctional transducer. This condition can be checked at any time by testing 
 * {@code status() == Status.NULL}.
 * <br><br>
 * <pre>
 * proxyTarget = new Target(); runTarget = new Target();
 * runtime = Ribose.loadRuntimeModel(modelFile,proxyTarget);
 * trex = runtime.newTransductor(runTarget);
 * if (trex.stop().push(data,limit).push(signal).start(transducer).status().isRunnable()) {
 *   do {
 *     if (trex.run().status().isPaused()) {
 *       data = recycle(data);
 *       if (0 &lt; input.read(data,limit)) {
 *         trex.push(data);
 *       else {
 *         break;
 *       }
 *     }
 *   } while (trex.status().isRunnable());
 *   trex.stop();
 * }
 * </pre>
 * Domain errors (inputs with no transition defined) are handled by emitting a
 * {@code} nul signal, giving the transduction an opportunity to handle it with an
 * explicit transition on {@code nul}. Typically this involves searching without effect
 * for a synchronization pattern and resuming with effect after synchronizing. If 
 * {@code nul} is not handled a {@code DomainException} is thrown. The transductor
 * will sent an {@code eos} signal to the transduction when the input stack runs dry.
 * If input is segmented and presented with a series of {@code push(byte[], int)}
 * method calls, {@code eos} will be raised at the end of each buffer. Transducers
 * can explicitly handle {@code eos} by including a transition on {@code eos}.
 * If eos is not explicitly handled the {@code run()} method will return with 
 * {@code Status.PAUSED} and {@code DomainErrorException} will not be raised.
 * <br><br>
 * Signals like {@code nul} and {@code eos} are raised asynchronously and are difficult
 * to address in the expression of transduction patterns. However, if they are ignored
 * in the original pattern expression, the expression can be modified after the fact 
 * by transducing the pattern itself to inject behaviors relating to asynchronpus signals.
 * See <a href="https://github.com/jrte/ribose#navigating-noisy-inputs-nullification">
 * Navigating Noisy Inputs (Nullification)</a> for an example showing how this can be done.
 * <br><br>
 * The runtime ITransductor implementation provides a core set of built-in effectors,
 * listed below, that are available to all ribose transducers.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>Built-in ribose effectors</b></caption>
 * <tr><th style="text-align:right">syntax</th><th style="text-align:left">semantics</th></tr>
 * <tr><td style="text-align:right"><i>nul</i></td><td>Signal <b>nul</b> to indicate no transition defined for current input</td></tr>
 * <tr><td style="text-align:right"><i>nil</i></td><td>Does nothing</td></tr>
 * <tr><td style="text-align:right"><i>paste</i></td><td>Append current input to selected named value</td></tr>
 * <tr><td style="text-align:right"><i>paste[(`~name`|`...`)+]</i></td><td>Paste literal data and/or named values into selected named value</td></tr>
 * <tr><td style="text-align:right"><i>select</i></td><td>Select the anonymous named value</td></tr>
 * <tr><td style="text-align:right"><i>select[`~name`]</i></td><td>Select a named value</td></tr>
 * <tr><td style="text-align:right"><i>copy</i></td><td>Copy the anonymous named value into selected named value</td></tr>
 * <tr><td style="text-align:right"><i>copy[`~name`]</i></td><td>Copy a named value into selected named value</td></tr>
 * <tr><td style="text-align:right"><i>cut</i></td><td>Cut the anonymous named value into selected named value</td></tr>
 * <tr><td style="text-align:right"><i>cut[`~name`]</i></td><td>Cut a named value into selected named value</td></tr>
 * <tr><td style="text-align:right"><i>clear</i></td><td>Clear the selected named value</td></tr>
 * <tr><td style="text-align:right"><i>clear[`~name`]</i></td><td>Clear a named value </td></tr>
 * <tr><td style="text-align:right"><i>count</i></td><td>Decrement the active counter and signal when counter drops to 0</td></tr>
 * <tr><td style="text-align:right"><i>count[`~name` `!signal`]</i></td><td>Set up a counter and signal from numeric named value</td></tr>
 * <tr><td style="text-align:right"><i>in</i></td><td>Push the selected value onto the input stack</td></tr>
 * <tr><td style="text-align:right"><i>in[`!signal`|(`~name`|`...`)+]</i></td><td>Push a signal or a concatenation of literal data and/or named values onto the input stack</td></tr>
 * <tr><td style="text-align:right"><i>out</i></td><td>Write the selected value onto the output stream</td></tr>
 * <tr><td style="text-align:right"><i>out[(`~name`|`...`)+]</i></td><td>Write literal data and/or named values onto the output stream</td></tr>
 * <tr><td style="text-align:right"><i>mark</i></td><td>Mark a position in the input stream</td></tr>
 * <tr><td style="text-align:right"><i>reset</i></td><td>Reset position to most recent mark (if any)</td></tr>
 * <tr><td style="text-align:right"><i>start[`@transducer`]</i></td><td>Push a transducer onto the transducer stack</td></tr>
 * <tr><td style="text-align:right"><i>pause</i></td><td>Force immediate return from {@code ITransductor.run()}</td></tr>
 * <tr><td style="text-align:right"><i>stop</i></td><td>Pop the transducer stack</td></tr>
 * </table>
* 
 * @author Kim Briggs
 * @see Status
 */
public interface ITransductor extends ITarget {
	
	/**
	 * Transduction status.
	 * 
	 * @author Kim Briggs
	 */
	enum Status {
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
		
		public boolean isPaused() {
			return this.equals(PAUSED);
		}
		
		public boolean isWaiting() {
			return this.equals(WAITING);
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
	Status status();

	/**
	 * Set the output stream to be used for {@code out[]} effector. The only method
	 * called on this stream is {@code write(byte[] data, int offset, int length)}.
	 * Caller is responsible for conducting all other stream operations. 
	 * 
	 * The default output stream is set to {@code System.out}. This may filter output
	 * to convert line endings, flush frequently, etc. For raw binary output (including
	 * UTF-8 text) use a {@code BufferedOutputStream}.
	 * 
	 * @param output the output stream to write to
	 * @return The previous output stream  
	 */
	OutputStream output(OutputStream output);
	
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
	 * @return Run this 
	 */
	ITransductor push(byte[] input, int limit);

	/**
	 * Push a signal onto the transductor's input stack to trigger next transition. 
	 * 
	 * @param signal the signal to push 
	 * @return Run this 
	 */
	ITransductor push(Signal signal);

	/**
	 * Push a transducer onto the transductor's transducer stack and set it state
	 * to the initial state. The topmost (last) pushed transducer will be activated 
	 * when the {@code run()} method is called.
	 * 
	 * @param transducer The name of the transducer to push
	 * @return Run this 
	 * @throws ModelException on error
	 */
	ITransductor start(Bytes transducer) throws ModelException;

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
	 * @return this 
	 * @throws RiboseException on error
	 * @throws DomainErrorException on error
	 * @see #status()
	 */
	ITransductor run() throws RiboseException, DomainErrorException;

	/**
	 * Clear input and transductor stacks and reset all named values to
	 * an empty state. This resets the transductor to original state 
	 * ready for reuse.
	 * 
	 * @return this 
	 * @throws RiboseException if transductor is proxy for parameter compilation
	 * @see #status()
	 */
	ITransductor stop() throws RiboseException;
	
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
	boolean hasMark();
	
	/**
	 * Allocate and return a new byte[] buffer if the previous buffer ({@code bytes})
	 * is marked in the input stack. Otherwise return the unmarked {@code bytes} buffer.
	 * 
	 * @param bytes a recently used input buffer
	 * @return the input buffer ({@code bytes}), or a new biffer of equal size if {@code bytes} is marked
	 */
	byte[] recycle(byte[] bytes);
	
	/**
	 * Return the number of domain errors counted in the most recent run() call. A
	 * domain error occurs when no transition is defined from current state for 
	 * current input. 
	 * 
	 * @return the number of domain errors counted in the most recent run() call.
	 */
	int getErrorCount();
}
