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

import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.EffectorException;

/**
 * Interface for simple effectors that present only a niladic {@link #invoke()}
 * method which is called on live effector instances in response to state transitions
 * in a running transduction. Implementations must extend the {@link BaseEffector}
 * superclass, which provides implementations for all other {@code IEffector} methods
 * and protected access to {@code BaseEffector.target} and {@code BaseEffector.output}.
 * Subclassess must implement {@link #invoke()} and may override other base effector
 * methods. They are typically implemented as inner classes within a specialized model
 * {@link ITarget} implementation class of type <b>T</b>, with a constructor that
 * receives a reference to a model target instance.
 * <br><br>
 * <i>Proxy</i> simple effectors are instantiated but not involved model compilation
 * contexts. They receive {@link #setOutput(IOutput)} when they are bound to a proxy
 * transductor but their references to output and target are erased in {@link #passivate()}
 * when parameter compilation completes. <i>Live</i> simple effectors instantiated in
 * runtime contexts receive {@link #setOutput(IOutput)} followed by 0 or more {@link
 * #invoke()}. Live effectors are never passivated and may access {@code super.target}
 * and {@code super.output} in their {@link #invoke()} methods. Effectors access transducer
 * fields by overriding {@link #setOutput(IOutput)} and using {@link
 * IOutput#getLocalizedFieldIndex(String, String)} to obtain stable field indexes for
 * subsequent use in {@link #invoke()} with the {@link IOutput} data transfer methods.
 * See {@link IOutput} for more information regarding field binding in effectors.
 * <br><br>
 * All {@link #invoke()} implementations return an integer <a href="#field.summary">RTX
 * </a> code, which is a bit map of special conditions. RTX bits are additive and
 * accumulate as an effect vector is executed for a transition. Specialized effectors
 * should return only {@code RTX_NONE} (to continue transduction normally) or a signal
 * encoded using {@link signal(int)}. Care should be taken to ensure that at
 * most one effector in any effect vector may return an encoded signal. The built-in
 * {@code count} and {@code signal} effectors and any specialized target effectors
 * that return encoded signals should never appear in combination within a single
 * effector vector, and this condition can be checked in the pattern domain. An
 * {@code EffectorException} will be thrown from {@link ITransductor#run()} if the
 * decoded signal is out of range, but mixed signals that remain in range will
 * go undetected (or force a domain error and transition on {@code nul}).
 * <br><br>
 * To verify that transducer <b>T</b> from a model using signalling effectors in
 * <b>S</b>={{@code count}, {@code signal}, {@code ...}} satisfies this signalling
 * constraint:
 * <br><pre> (T$(0 1)) &amp; (T$(0 1):alph)*S(T$1:alph)*S(T$(0 1):alph)*</pre>
 * must be empty. In general, range constraints on the effector set can be
 * expressed as patterns to be tested against transducer patterns in the design
 * stage, before they are saved to ginr FSTs for inclusion in ribose models.
 * <br><br>
 * Conversion between Java/Unicode char and UTF-8 bytes are supported by static {@link
 * Codec} methods backed by thread local encoder and decoder instances. Ribose works
 * exclusively in the byte domain internally and the codecs are required only in
 * peripheral contexts, for example, to decode extracted bytes to String, or encoding
 * String transducer names to obtain a key for looking up and loading a transducer.
 * Ribose and ginr currently support only UTF-8 character encodings.
 *
 * @param <T> The effector target type
 * @see IParameterizedEffector
 * @see BaseEffector
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
	 * Returns the effector name. The name of the effector is the token
	 * used to reference it in transducer patterns.
	 *
	 * @return the effector name.
	 */
	Bytes getName();

	/**
	 * Returns a reference to the target instance that expresses the effector.
	 *
	 * @return the target instance
	 */
	T getTarget();

	/**
	 * Receive an {@link IOutput} view of the transductor that the effector target
	 * is bound to. Effectors retain their IOutput view to look up field ordinals
	 * for parameter compilation and to transfer data out of runnimg transductors.
	 * Effector implementations may access {@link BaseEffector#output} directly.
	 *
	 * @param output an object that provides a view of transduction runtime fields
	 * @throws EffectorException if field names can't be resolved
	 * @see IOutput#getLocalizedFieldIndex(String, String)
	 */
	void setOutput(IOutput output)
	throws EffectorException;

	/**
	 * This method is invoked at runtime when triggered by an input transition. Normally
	 * implementation will return {@code IEffector.RTX_NONE}, which has no effect. In some
	 * cases a signal may be encoded in the return value using {@code IEffector.signal(sig)},
	 * where {@code sig} is the ordinal value (&gt;255) of the signal. In that case the
	 * decoded signal will be used to trigger the next transition.
	 *
	 * @return {@code IEffector.RTX_NONE} or {@code IEffector.signal(signalOrdinal)}
	 * @throws EffectorException if things don't work out
	 */
	int invoke()
	throws EffectorException;

	/**
	 * Construct an effector invokation return code indicating that a signal
	 * is to be injected into the input stream to trigger next transition. The
	 * {@code signal} must be &gt;255 or 0
	 *
	 * @param signalOrdinal the signal ordinal (>255)
	 * @return the encoded signal
	 */
	static int signal(int signalOrdinal) {
		assert signalOrdinal > 255;
		return (signalOrdinal << 16) | IEffector.RTX_SIGNAL;
	}

	/**
	 * Called for proxy effectors after parameter compilation is
	 * complete. This will null out the target and output fields
	 * in the {@code BaseEffector} superclass. This allows the
	 * proxy model and transducer to be garbage collected after
	 * all effector parameters have been compiled. Subclasses
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
