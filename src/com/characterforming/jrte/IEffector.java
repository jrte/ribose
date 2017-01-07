/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Interface for simple effectors. Effectors are invoked at runtime in response to state transitions in a running
 * transduction instance. They are typically anonymous inner classes defined in ITarget.bind().
 * 
 * @param <T> The effector target type
 * @author kb
 */
public interface IEffector<T extends ITarget> {
	/**
	 * Returns the target that expresses the effector
	 * 
	 * @return The target that expresses the effector
	 */
	public T getTarget();

	/**
	 * Returns the effector name. The name of the effector is the token used to reference it in transducer definitions.
	 * 
	 * @return The effector name.
	 */
	public String getName();

	/**
	 * This method is invoked at runtime when triggered by an input transition.
	 * 
	 * @return User-defined effectors should return 0
	 * @throws EffectorException
	 */
	public int invoke() throws EffectorException;

}
