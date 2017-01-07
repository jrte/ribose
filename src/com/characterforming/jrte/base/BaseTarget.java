/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.base;

import com.characterforming.jrte.IEffector;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ITransduction;

/**
 * Base {@link ITarget} implementation class can be subclassed to define other
 * effectors to suit application-specific needs. BaseTarget subclasses express
 * these effectors by overriding the {@link #bind(ITransduction)} method.
 * 
 * @author kb
 */
public class BaseTarget implements ITarget {
	private ITransduction transduction;

	public BaseTarget() {
		this.transduction = null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#bind(ITransduction)
	 */
	@Override
	public IEffector<?>[] bind(final ITransduction transduction) {
		this.transduction = transduction;
		return new IEffector[] {};
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#getTransduction()
	 */
	@Override
	public final ITransduction getTransduction() {
		return this.transduction;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#getName()
	 */
	@Override
	public final String getName() {
		return this.getClass().getSimpleName();
	}
}
