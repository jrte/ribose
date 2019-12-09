/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.base;

import com.characterforming.jrte.IEffector;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ITransduction;

/**
 * Base {@link ITarget} implementation class exports no effectors. It serves only 
 * as a public proxy for the core transduction target that provides the built-in 
 * effectors. It can be subclassed by specialized targets that implement 
 * {@link ITarget} to define additional effectors.
 * 
 * Specialized targets present their effectors by overriding the 
 * {@link #bind(ITransduction)} method, and these may serve as subclasses for 
 * additional extensions. Each subclass override must call super.bind() and
 * include the superclass effectors as predecessors of its own in the returned
 * list. In that context you should consider using a List&lt;IEffector&lt;?&gt;&gt; to 
 * accumulate subclass effectors and call List&lt;..&gt;.toArray() to obtain 
 * top-level bindings to return.
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
