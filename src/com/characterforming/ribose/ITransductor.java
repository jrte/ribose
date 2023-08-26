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
import java.io.InputStream;

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;

/**
 * Interface for runtime transductors. A transductor binds an {@link ITarget} instance
 * to an input stack and a transducer stack. When the {@link run()} method is called
 * the transductor will read input and invoke the effectors triggered by each input
 * transition until {@link status()} {@code !=} {@link Status#RUNNABLE}. Then one of
 * the following conditions is satisfied:
 * <br>
 * <ul>
 * <li>the input stack is empty or the {@code pause} effector is invoked ({@link Status#PAUSED})
 * <li>the transducer stack is empty ({@link Status#WAITING})
 * <li>input and transducer stack are both empty ({@link Status#STOPPED})
 * <li>an exception is thrown
 * </ul>
 * The {@code run()} method has no effect when the transducer or input stack is empty.
 * A paused transduction can be resumed when input is available ({@code push()}).
 * A waiting transduction can be resumed by starting a transducer ({@code start()}).
 * A stopped transducer can be reused to start a new transduction by pushing new
 * input and transducers. After the transduction has exhausted all input, the
 * transductor should {@code push(Signal.eos)} and {@code run()} once to ensure the
 * transduction is complete. If there is no final transition defined for {@code eos}
 * it will be ignored. Finally, {@code stop()} must be called again to clear the
 * transducer and input stacks before the transductor instance can be reused.
 * <br><br>
 * The {@code stop()} method should be called before starting a new transduction,
 * to ensure that the transducer and input stacks and all fields are cleared. The
 * {@code stop()} method will throw a {@code RiboseException} when it is called on
 * a proxy (instantiated for parameter compilation only) or otherwise nonfunctional
 * transductor. This condition can be checked at any time by testing {@link 
 * Status#isProxy()}.
 * <br><br>
 * The example below shows how {@code ITransductor} can be instantiated
 * and used directly to exert fine-grained control over transduction processes. For
 * more generic transductions, the more granular methods {@link
 * IModel#stream(Bytes, Signal, InputStream, OutputStream)} and {@link 
 * IModel#stream(Bytes, ITarget, Signal, InputStream, OutputStream)} are available. 
 * <br>
 * <pre>
 * ITarget proxyTarget = new Target();
 * ITarget liveTarget = new Target(args);
 * IModel runtime = Ribose.loadRuntimeModel(modelFile,proxyTarget);
 * ITransductor trex = runtime.transductor(liveTarget);
 * byte[] data = new byte[64 * 1024];
 * int limit = input.read(data,data.length);
 * if (trex.stop().push(data,limit).signal(Signal.nil).start(transducer).status().isRunnable()) {
 *   do {
 *     if (trex.run().status().isPaused()) {
 *       data = trex.recycle(data);
 *       limit = input.read(data,data.length);
 *       if (0 &lt; limit)
 *         trex.push(data,limit);
 *     }
 *   } while (trex.status().isRunnable());
 *   if (trex.status().isPaused())
 *     trex.signal(Signal.eos).run();
 *   trex.stop();
 * }</pre>
 * Domain errors (inputs with no transition defined) are handled by emitting a
 * {@code nul} signal, giving the transduction an opportunity to handle it with an
 * explicit transition on {@code nul}. Typically this involves searching without effect
 * for a synchronization pattern and resuming with effect after synchronizing. If
 * {@code nul} is not handled a {@code DomainErrorException} is thrown, with one exception
 * -- the {@code eos} signal sent after all other input is exhausted. Transducers can
 * safely ignore {@code eos} or use it explicitly if required. If {@code eos} is ignored
 * the {@code run()} method will return with {@code Status.WAITING} and {@code
 * DomainErrorException} will not be thrown.
 * <br><br>
 * The {@code signal[`!signal`]} effector injects a signal for immediate transduction on
 * the next transition. Effectors may inject a signal by returning from {@link IEffector#invoke()}
 * or {@link IParameterizedEffector#invoke(int)} a signal ordinal encoded with 
 * {@link IEffector#rtxSignal(int)}. This can be used to effect backflow of information 
 * from the target to the transductor; for example,
 * <br><pre>(nl, isThatSo[`!true` `false`]) ((true, yep) | (false, nope))</pre>
 * At most one encoded signal can be injected per transition (this is not checked in the
 * ribose runtime).
 * <br><br>
 * The runtime ITransductor implementation provides a core set of base effectors,
 * listed below, that are available to all ribose transducers.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>Built-in ribose effectors</b></caption>
 * <tr><th style="text-align:right"><i>syntax</i></th><th style="text-align:left">semantics</th></tr>
 * <tr><td style="text-align:right"><i>nul</i></td><td>Signal <b>nul</b> to indicate no transition defined for current input</td></tr>
 * <tr><td style="text-align:right"><i>nil</i></td><td>Does nothing</td></tr>
 * <tr><td style="text-align:right"><i>paste</i></td><td>Append current input to selected field</td></tr>
 * <tr><td style="text-align:right"><i>paste[(`~field`|`...`)+]</i></td><td>Append literal data and/or fields to selected field</td></tr>
 * <tr><td style="text-align:right"><i>select</i></td><td>Select the anonymous field</td></tr>
 * <tr><td style="text-align:right"><i>select[`~field`]</i></td><td>Select a field</td></tr>
 * <tr><td style="text-align:right"><i>copy</i></td><td>Copy the anonymous field into selected field</td></tr>
 * <tr><td style="text-align:right"><i>copy[`~field`]</i></td><td>Copy a field into selected field</td></tr>
 * <tr><td style="text-align:right"><i>cut</i></td><td>Cut the anonymous field into selected field</td></tr>
 * <tr><td style="text-align:right"><i>cut[`~field`]</i></td><td>Cut a field into selected field</td></tr>
 * <tr><td style="text-align:right"><i>clear</i></td><td>Clear the selected field</td></tr>
 * <tr><td style="text-align:right"><i>clear[`~*`]</i></td><td>Clear all fields </td></tr>
 * <tr><td style="text-align:right"><i>clear[`~field`]</i></td><td>Clear a specific field </td></tr>
 * <tr><td style="text-align:right"><i>count</i></td><td>Decrement the active counter and signal when counter drops to zero</td></tr>
 * <tr><td style="text-align:right"><i>count[(`~field`|`digit+`) `!signal`]</i></td><td>Set up a counter and signal from an initial numeric literal or field</td></tr>
 * <tr><td style="text-align:right"><i>signal</i></td><td>Equivalent to <i>signal[`!nil`]</i></td></tr>
 * <tr><td style="text-align:right"><i>signal[`!signal`]</i></td><td>Inject a signal into the input stream for immediate transduction</td></tr>
 * <tr><td style="text-align:right"><i>in</i></td><td>Push the currently selected field onto the input stack</td></tr>
 * <tr><td style="text-align:right"><i>in[(`~field`|`...`)+]</i></td><td>Push a concatenation of literal data and/or fields onto the input stack</td></tr>
 * <tr><td style="text-align:right"><i>out</i></td><td>Write the selected field onto the output stream</td></tr>
 * <tr><td style="text-align:right"><i>out[(`~field`|`...`)+]</i></td><td>Write literal data and/or fields onto the output stream</td></tr>
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
	/** Transduction status. */
	enum Status {
		/** Transduction stack not empty, input stack not empty. */
		RUNNABLE,
		/** Transduction stack empty, input stack not empty. */
		WAITING,
		/** Transduction stack not empty, input stack empty. */
		PAUSED,
		/** Transduction stack empty, input stack empty */
		STOPPED,
		/** Transductor is proxy for model effector parameter precompilation, not for runtime use */
		PROXY;

		/**
		 * Status == RUNNABLE
		 * @return true if Status == RUNNABLE
		 */
		public boolean isRunnable() {
			return this.equals(RUNNABLE);
		}

		/**
		 * Status == PAUSED
		 * @return true if Status == PAUSED
		 */
		public boolean isPaused() {
			return this.equals(PAUSED);
		}

		/**
		 * Status == WAITING
		 * @return true if Status == WAITING
		 */
		public boolean isWaiting() {
			return this.equals(WAITING);
		}

		/**
		 * Status == STOPPED
		 * @return true if Status == STOPPED
		 */
		public boolean isStopped() {
			return this.equals(STOPPED);
		}

		/**
		 * Status == PROXY
		 * @return true if Status == PROXY
		 */
		public boolean isProxy() {
			return this.equals(PROXY);
		}
	}

	/** Run metrics, per {@link #run()} call. */
	public class Metrics {
		/** Number of bytes of input consumed */
		public long bytes;

		/** Number of {@code nul} signals injected */
		public long errors;

		/** Number of bytes consumed in mproduct traps */
		public long product;

		/** Number of bytes consumed in msum traps */
		public long sum;

		/** Number of bytes consumed in mscan traps */
		public long scan;

		/** Number of bytes allocated while marking */
		public long allocated;

		/** Constructor */
		public Metrics() {
			this.reset();
		}

		/**
		 * Reset all metrics
		 * 
		 * @return a reference to the reset metrics
		 */
		public Metrics reset() {
			bytes = errors = product = sum = scan = 0;
			return this;
		}

		/**
		 * Add transient metrics to accumulator metrics
		 * 
		 * @param accumulator the metrics to be updated
		 */
		public void update(Metrics accumulator) {
			accumulator.bytes += this.bytes;
			accumulator.errors += this.errors;
			accumulator.product += this.product;
			accumulator.sum += this.sum;
			accumulator.scan += this.scan;
			accumulator.allocated += this.allocated;
		}
	}

	/**
	 * Test the status of the transduction's input and transducer stacks.
	 *
	 * A RUNNABLE transduction can be resumed immediately by calling run(). A PAUSED
	 * transduction can be resumed when new input is pushed. A WAITING transduction can be run
	 * again after starting a new transducer. Transducers may deliberately invoke the
	 * {@code pause} effector to break out of run() with transducer and input stacks not
	 * empty to allow the caller to take some action before calling run() to resume the
	 * transduction. A STOPPED transduction can be reused to start a new transduction
	 * with a different transducer stack and new input after calling stop() to reset
	 * the transduction stack to its original bound state.
	 *
	 * @return transduction status
	 */
	Status status();

	/**
	 * Set the output stream to be used for {@code out[]} effector. The only method
	 * called on this stream is {@code write(byte[] data, int offset, int length)}.
	 * Caller is responsible for conducting all other stream operations.
	 *
	 * The default output stream is set to {@code System.out}, which may filter output
	 * to convert line endings, flush frequently, etc. For raw binary output (including
	 * UTF-8 text) with buffering use a {@code BufferedOutputStream}.
	 *
	 * @param output the output stream to write to
	 * @return The previous output stream
	 */
	OutputStream output(OutputStream output);

	/**
	 * Push an initial segment {@code [0..limit)} of a data array onto the
	 * transduction input stack. The bottom input stack frame is the target
	 * for marking and resetting via the {@code mark} and {@code reset} effectors.
	 * These effectors project their effect onto the bottom frame if they are
	 * invoked while there are &gt;1 active frames on the input stack.
	 *
	 * @param input data to push onto input stack for immediate transduction
	 * @param limit truncate effective input range at {@code max(limit, data.length)}
	 * @return this ITransductor
	 */
	ITransductor push(byte[] input, int limit);

	/**
	 * Set up a signal as prologue for the next {@link #run()}. The signal will be
	 * consumed by the transducer at the top of the transduction stack on its first
	 * transition when the transductor is next {@code run()}. This can also be used to
	 * convey a hint to a paused transductor before resuming, In either case, the 
	 * {@code run()} then constinues with previously stacked input.
	 * <br>br>
	 * All ribose transducers <i>should</i> accept an initial {@code nil} signal, 
	 * ignoring it if not needed for initialization effects. Otherwise, any of the
	 * core ribose {@link Signal} enumerators can be used as a prologue to send to
	 * a resuming transducer.
	 *
	 * @param signal the signal to set as the next {@link #run()} prologue
	 * @return this ITransductor
	 */
	ITransductor signal(Signal signal);

	/**
	 * Push a transducer onto the transductor's transducer stack and set its state
	 * to the initial state. The topmost (last) pushed transducer will be activated
	 * when the {@code run()} method is called.
	 *
	 * @param transducer the name of the transducer to push
	 * @return this ITransductor
	 * @throws ModelException on error
	 */
	ITransductor start(Bytes transducer) throws ModelException;

	/**
	 * Run the transduction with current input until the input or transduction
	 * stack is empty, transduction pauses or stops, or an exception is thrown.
	 * This method should be called repeatedly until {@code status().isRunnable()}
	 * returns {@code false}. Normally, the transduction status when {@code run()}
	 * returns is {@link Status#PAUSED} (input stack empty, {@code push()} more
	 * input to resume) or {@link Status#WAITING} (transducer stack empty, 
	 * {@code start()} another transducer to resume), unless the {@code pause}
	 * effector forces return with {@link Status#RUNNABLE}. The latter case may
	 * arise if the transduction needs to synchronize with the process driving
	 * the transduction for some unimaginable reason. In any case, when the
	 * necessary action has been taken the transduction can be resumed by
	 * calling {@code run()} again.
	 * <br><br>
	 * If a mark is set, it applies to the primary input stream and marked input
	 * buffers held in the transduction mark set cannot be reused by the caller
	 * until a new mark is set or the input has been reset and all marked buffers
	 * have been transduced. Call {@link #recycle(byte[])} before reusing data buffers
	 * if the transduction involves backtracking with mark/reset.
	 *
	 * @return this ITransductor
	 * @throws RiboseException on error
	 * @throws DomainErrorException on error
	 * @see #recycle(byte[])
	 * @see #status()
	 */
	ITransductor run() throws RiboseException;

	/**
	 * Return a new or unmarked byte[] buffer if the previous buffer ({@code bytes})
	 * is marked in the input stack, else return the unmarked {@code bytes} buffer.
	 * <br><br>
	 * Byte arrays passed as input to the transductor when a mark is set are retained
	 * by the transductor and MUST NOT be subsequently reused as input containers until
	 * they have been recovered by a call to this method.
	 *
	 * @param bytes a recently used input buffer
	 * @return the given buffer ({@code bytes}), or a new buffer of equal size if {@code bytes} is marked
	 * @see #run()
	 */
	byte[] recycle(byte[] bytes);

	/**
	 * Update metrics from the most recent {@link #run()} call. Metrics are 
	 * preserved until the {@code run()} method is called again. This method
	 * sums the metrics from the most recent {@link #run()} call into an
	 * accumulating Metrics instance.
	 * 
	 * @param metrics the metrics to be updated
	 */
	void metrics(Metrics metrics);

	/**
	 * Clear input and transductor stacks and reset all fields to
	 * an empty state. This resets the transductor to original state
	 * ready for reuse, but preserves accumulated metrics.
	 *
	 * @return this ITransductor
	 * @throws RiboseException if transductor is proxy for parameter compilation
	 * @see #status()
	 */
	ITransductor stop() throws RiboseException;
}