/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

import com.characterforming.jrte.base.BaseTarget;

/**
 * Interface for transduction target classes, which express IEffector instances
 * that are invoked from runtime transductions. Target implementations must
 * subclass and extend BaseTarget with specialized effectors.
 * <p>
 * Target classes must present a public default constructor with no arguments. A
 * proxy instance of the target implementation class is instantiated during
 * gearbox compilation to determine the names and types of the target effectors.
 * <p>
 * The {@link #bind(ITransduction)} method will be called on the proxy target at
 * the end of the gearbox compilation process to verify effector binding and
 * effector parameter compilation. At that time the
 * {@link IParameterizedEffector#newParameters(int)} and
 * {@link IParameterizedEffector#setParameter(int, byte[][])} methods will be
 * called for each IParameterizedEffector.
 * <p>
 * At runtime, targets are bound only once to a transduction and the binding
 * persists for the lifetime of the target object. Applications use the
 * transduction to start and restart transducers and run them against available
 * inputs. The target effectors update the target state when the transduction is
 * run.
 * 
 * @author kb
 */
public interface ITarget {
	/**
	 * Get the name of the target as referenced in transducers that use the
	 * target.
	 * 
	 * @return The name of of this target
	 */
	public String getName();

	/**
	 * Bind a transduction and list all effectors presented by this target. This
	 * method is called to list the names and types of the effectors expressed
	 * by the target class and to bind the target effectors and effector
	 * parameters to the transduction.
	 * <p>
	 * Subclasses must include their effectors in the IEffector array returned.
	 * After runtime binding, effectors are invoked when triggered by input
	 * transitions.
	 * 
	 * @param transduction The Transduction that the target is being bound to
	 * @return An array of IEffector instances implemented by the target.
	 * @throws TargetBindingException On error
	 * @see BaseTarget#bind(ITransduction)
	 */
	public IEffector<?>[] bind(ITransduction transduction) throws TargetBindingException;

	/**
	 * Get the ITransduction instance that is bound to the target
	 * 
	 * @return The Transduction that is bound to the target, or null if unbound
	 */
	public ITransduction getTransduction();
}
