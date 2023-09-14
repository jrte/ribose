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

import com.characterforming.jrte.engine.Base;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Interface for simple effectors that present only a niladic {@link #invoke()}
 * method which is called on live effector instances in response to state transitions
 * in a running transduction. They are typically implemented as anonymous inner classes
 * within a specialized {@link ITarget} implementation classes. In compile contexts
 * simple effectors are instantiated as proxies but receive only {@link #passivate()}, 
 * which clears its internal fields. In runtime contexts they instantiated as live
 * effectors. Live simple effectors receive a {@link #setOutput(IOutput)} call followed
 * by 0 or more calls to {@link #invoke()}.
 * <br><br>
 * All effectors return an integer <a href="#field.summary">RTX</a> code, which is a bit
 * map of special conditions. RTX bits are additive and accumulate as the effect vector is
 * executed for a transition. Specialized effectors should return only {@code RTX_NONE}
 * (to continue transduction normally) or a signal encoded using {@link IEffector#rtxSignal(int)}.
 * Care should be taken to ensure that at most one effector in any effect vector may
 * return an encoded signal. The built-in {@code count} and {@code signal} effectors and
 * any specialized target effectors that return encoded signals should never appear in
 * combination within a single effector vector, and this condition can be checked in
 * the pattern domain. An {@code AssertionError} will be thrown from {@link ITransductor#run()}
 * if the decoded signal is out of range and assertions are enabled in the JVM, but
 * mixed signals that remain in range will go undetected (or force a domain error and
 * transition on {@code nul}).
 * <br><br>
 * To verify that transducer <b>T</b> from a model using signalling effectors in 
 * <b>S</b>={{@code count}, {@code signal}, {@code ...}} satisfies this signalling
 * constraint:
 * <br><pre> (T$(0 1)) &amp; (T$(0 1):alph)*S(T$1:alph)*S(T$(0 1):alph)*</pre>
 * must be empty. In general, range constraints on the effector set can be 
 * expressed as patterns to be tested against transducer patterns in the design
 * stage, before they are saved to ginr FSTs for inclusion in ribose models.
 * @param <T> The effector target type
 * @see IParameterizedEffector
 * @author Kim Briggs
 */
public interface IEffector<T extends ITarget> {

	/** No aftereffect */
	static final int RTX_NONE = 0;
	/** Transducer pushed onto the transducer stack. */
	static final int RTX_START = 1;
	/** Transducer stack popped. */
	static final int RTX_STOP = 2;
	/** Input pushed onto the input stack. */
	static final int RTX_INPUT = 4;
	/** Force immediate and resumable exit from ITransductor.run(). */
	static final int RTX_PAUSE = 8;
	/** Force immediate and final exit from ITransductor.run(). */
	static final int RTX_STOPPED = 16;
	/** Inject a signal for immediate transduction in ITransductor.run(). */
	static final int RTX_SIGNAL = 32;

	/**
	 * This method is invoked at runtime when triggered by an input transition.
	 *
	 * @return user-defined effectors should return {@code IEffector.RTX_NONE}
	 * @throws EffectorException on error
	 */
	int invoke() throws EffectorException;

	/**
	 * Receive an IOutput view of transduction loggers and fields. Fields are
	 * arrays of bytes extracted from transduction input. Effectors will
	 * typically select and hold a {@link java.util.logging.Logger} and a subset
	 * of {@link IField} fields of interest here and extract field data in
	 * {@link #invoke()} for assimilation into the target.
	 *
	 * @param output an object that provides a view of transduction runtime fields
	 * @throws TargetBindingException if field names can't be resolved
	 */
	void setOutput(IOutput output) throws TargetBindingException;

	/**
	 * Returns the target that expresses the effector.
	 *
	 * @return the target that expresses the effector
	 */
	T getTarget();

	/**
	 * Returns the effector name. The name of the effector is the token
	 * used to reference it in transducer patterns.
	 *
	 * @return the effector name.
	 */
	Bytes getName();

	/**
	 * Helper method simplifies {@link IField} access
	 * 
	 * @param fieldName the name of the field (Unicode)
	 * @return the corresponding field
	 * 
	 */
	IField getField(String fieldName);

	/**
	 * Encode a signal with an {@link IEffector#RTX_SIGNAL} value to return from 
	 * an effctor {@link IEffector#invoke()} or {@link IParameterizedEffector#invoke(int)}
	 * method. The transductor will decode and inject {@code signal} into the 
	 * input stream for immediate transduction. Since the return values from successive
	 * invocations of effectors in a vector triggered by a state transition are OR'd
	 * together the decoded final value may be out of range of the model's defined signals
	 * if &gt;1 effectors return values with encoded signals.
	 * 
	 * @param signal the signal value (&gt;255, &lt;256+#signals) to encode
	 * @return signal &lt;&lt; 16 | {@code IEffector.RTX_SIGNAL}
	 */
	static int rtxSignal(int signal) {
		assert signal >= Base.RTE_SIGNAL_BASE;
		return (signal << 16) | RTX_SIGNAL;
	}

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

	/**
	 * Called for proxy effectors after parameter compilation is
	 * complete. This will null out the target, output, decoder and
	 * encoder fields in the {@code BaseEffector} superclass. This
	 * allows the proxy model and transducer to be garbage collected
	 * after all effector parameters have been compiled. Subclasses
	 * may override this method to dispose of additional resources
	 * as well, but must also call {@code super.passivate()} in 
	 * the overriding method.
	 * <br><br>
	 * This method is never called for effector instances that are 
	 * bound to a live transduction. It is only called for proxy
	 * effectors, which are retained only to transfer compiled
	 * parameters to live effectors. Otherwise, proxy effectors are
	 * effectively zombies after parameter compilation is complete.
	 */
	public void passivate();
}
