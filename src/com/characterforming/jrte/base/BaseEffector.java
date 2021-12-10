/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
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
