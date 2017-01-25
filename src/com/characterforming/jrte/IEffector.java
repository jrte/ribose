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
	 * Return RTE_TRANSDUCTION_RUN from effector.invoke() methods that do not
	 * affect the ITransduction transducer stack or input stack.
	 */
	public static final int RTE_TRANSDUCTION_RUN = 0;
	/**
	 * Return RTE_TRANSDUCTION_START from effector.invoke() methods that push the
	 * ITransduction transducer stack.
	 */
	public static final int RTE_TRANSDUCTION_START = 1;
	/**
	 * Return RTE_TRANSDUCTION_STOP from effector.invoke() methods that pop the
	 * ITransduction transducer stack.
	 */
	public static final int RTE_TRANSDUCTION_STOP = 2;
	/**
	 * Return RTE_TRANSDUCTION_SHIFT from effector.invoke() methods that replace
	 * the top transducer on the ITransduction transducer stack.
	 */
	public static final int RTE_TRANSDUCTION_SHIFT = 4;
	/**
	 * Return RTE_TRANSDUCTION_PUSH from effector.invoke() methods that push the
	 * ITransduction input stack.
	 */
	public static final int RTE_TRANSDUCTION_PUSH = 8;
	/**
	 * Return RTE_TRANSDUCTION_PUSH from effector.invoke() methods that push the
	 * ITransduction input stack.
	 */
	public static final int RTE_TRANSDUCTION_POP = 16;
	/**
	 * Return RTE_TRANSDUCTION_PAUSE from an effector.invoke() method to force
	 * immediate exit from run() on end of input if caller should continue when
	 * input is available.
	 */
	public static final int RTE_TRANSDUCTION_PAUSE = 32;
	/**
	 * Return RTE_TRANSDUCTION_END from an effector.invoke() method to force
	 * immediate and final exit from run() .
	 */
	public static final int RTE_TRANSDUCTION_END = 64;

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
