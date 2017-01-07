/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.base;

import com.characterforming.jrte.EffectorException;
import com.characterforming.jrte.IEffector;
import com.characterforming.jrte.ITarget;

/**
 * Base {@link IEffector} implementation class. The invoke() method must be
 * implemented by subclasses.
 * 
 * @param <T> The effector target type
 * @author kb
 */
public abstract class BaseEffector<T extends ITarget> implements IEffector<T> {
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
	 * immediate exit from run().
	 */
	public static final int RTE_TRANSDUCTION_PAUSE = 32;

	private final T target;
	private final String name;

	public BaseEffector(final T target, final String name) {
		this.target = target;
		this.name = name;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.IEffector#getName()
	 */
	@Override
	public final String getName() {
		return this.name;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.IEffector#getTarget()
	 */
	@Override
	public final T getTarget() {
		return this.target;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.base.IEffector#invoke()
	 */
	@Override
	public abstract int invoke() throws EffectorException;

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.name;
	}
}
