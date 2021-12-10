/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
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
	 * Return RTE_EFFECT_NONE from effector.invoke() methods that do not
	 * affect the ITransduction transducer stack or input stack.
	 */
	public static final int RTE_EFFECT_NONE = 0;
	/**
	 * Return RTE_EFFECT_START from effector.invoke() methods that push the
	 * ITransduction transducer stack.
	 */
	public static final int RTE_EFFECT_START = 1;
	/**
	 * Return RTE_EFFECT_STOP from effector.invoke() methods that pop the
	 * ITransduction transducer stack.
	 */
	public static final int RTE_EFFECT_STOP = 2;
	/**
	 * Return RTE_EFFECT_SHIFT from effector.invoke() methods that replace
	 * the top transducer on the ITransduction transducer stack.
	 */
	public static final int RTE_EFFECT_SHIFT = 4;
	/**
	 * Return RTE_EFFECT_PUSH from effector.invoke() methods that push the
	 * ITransduction input stack.
	 */
	public static final int RTE_EFFECT_PUSH = 8;
	/**
	 * Return RTE_EFFECT_POP from effector.invoke() methods that pop the
	 * ITransduction input stack.
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
	 * Returns the effector name. The name of the effector is the token used to reference it in transducer definitions.
	 * 
	 * @return The effector name.
	 */
	public String getName();

	/**
	 * This method is invoked at runtime when triggered by an input transition.
	 * 
	 * @return User-defined effectors should return 0
	 * @throws EffectorException On error
	 */
	public int invoke() throws EffectorException;

}
