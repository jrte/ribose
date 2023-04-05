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
 * Interface for simple effectors that present only a nullary {@link #invoke()}
 * method to the transductor. Effectors are invoked at runtime in response to
 * state transitions in a running transduction. They are typically implemented
 * as anonymous inner classes within a specialized {@link ITarget} implementation
 * class.
 * <br><br>
 * Simple effectors present an {@link invoke()} method that is called from running
 * transductions. Parameterized effectors implementing {@link IParameterizedEffector} may
 * also be included in ribose targets. The ribose {@link ITransductor} implmentation
 * also presents a core suite of base effectors that are accessible to all targets.
 * <br><br>
 * All effectors return an integer {@code RTX} code (see RTX_*, below), which is a bit
 * map of special conditions. RTX bits are additive and accumulate as the effect vector is
 * executed for a transition. Most RTX bits reflect the action of base effectors. Effectors
 * that do not affect the {@link ITransductor} transducer stack or input stack should
 * return only {@code RTX_NONE} (to continue transduction normally) or a signal encoded
 * with {@code RTX_SIGNAL} using {@link IEffector#rtx(int)}.
 * <br><br>
 * Care should be taken to ensure that at most one effector in any effect vector
 * may return an encoded signal. The built-in {@code count} and {@code signal}
 * effectors and any domain-specific target effectors that return encoded signals
 * should never appear in combination within a single effector vector, and this
 * condition can be checked in the pattern domain. An {@code EffectorException}
 * will be thrown from {@link ITransductor#run()} if the decoded signal is out
 * of range, but mixed signals that remain in range will go undetected (or force
 * a domain error and transition on {@code nul}).
 * @param <T> The effector target type
 * @author Kim Briggs
 */
public interface IEffector<T extends ITarget> {

	/**
	 */
	static final int RTX_NONE = 0;
	/** Transducer pushed onto the transducer stack. */
	static final int RTX_START = 1;
	/** Transducer stack popped. */
	static final int RTX_STOP = 2;
	/** Input (or signal) pushed onto the input stack. */
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
	 * @return User-defined effectors should return {@code IEffector.RTX_NONE}
	 * @throws EffectorException on error
	 */
	int invoke() throws EffectorException;

	/**
	 * Receive an IOutput view of transduction loggers and named values. Named
	 * values are arrays of bytes extracted from transduction input. Effectors will
	 * typically select and hold a {@link java.util.logging.Logger} and a subset
	 * of {@link INamedValue} fields of interest here and extract value data in
	 * {@link #invoke()} for assimilation into the target.
	 *
	 * @param output A object that provides a view or transduction runtime values
	 * @throws TargetBindingException if value names can't be resolved
	 */
	void setOutput(IOutput output) throws TargetBindingException;

	/**
	 * Returns the target that expresses the effector.
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
	 * Encode a signal with an {@link IEffector#RTX_SIGNAL} value to return from 
	 * an effctor {@link IEffector#invoke()} or {@link IParameterizedEffector#invoke(int)}
	 * method. The transductor will decode and inject {@code signal} into the 
	 * input stream for immediate transduction. Since the return values from successive
	 * invocations of effectors in a vector are OR'd together the decoded final value
	 * may be out of range of the models defined signals.
	 * 
	 * @param signal the signal value (&gt;255, &lt;256+#signals) to encode
	 * @return signal &lt;&lt; 16 | {@code IEffector.RTX_SIGNAL}
	 */
	static int rtx(int signal) {
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
}
