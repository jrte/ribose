/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.ribose.base;

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.ITarget;

/**
 * Base {@link IEffector} implementation class provides subclasses with access
 * to the transduction target and an output view to enable named value extraction. 
 * The {@link invoke()} method must be implemented by subclasses.
 * 
 * @param <T> The effector target type
 * @author Kim Briggs
 */
public abstract class BaseEffector<T extends ITarget> implements IEffector<T> {

	/** Effector access to the target that it is bound to */
	protected T target;
	/** Effector view of Transductor named values. */
	protected IOutput output;
	/** Charset decoder */
	protected final CharsetDecoder decoder;
	/** Charset encoder */
	protected final CharsetEncoder encoder;

	private final Bytes name;
	
	/**
	 * Constructor receivs target and a name.
	 * 
	 * @param target The target that binds the effector
	 * @param name The effector name
	 */
	public BaseEffector(final T target, final String name) {
		this.decoder = target.getCharsetDecoder();
		this.encoder = target.getCharsetEncoder();
		this.name = Bytes.encode(this.encoder, name);
		this.target = target;
		this.output = null;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.base.IEffector#setOutput(IOutput)
	 */
	@Override
	public void setOutput(IOutput output) throws TargetBindingException {
		this.output = output;
	}


	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.base.IEffector#getName()
	 */
	@Override
	public final Bytes getName() {
		return this.name;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.base.IEffector#getTarget()
	 */
	@Override
	public final T getTarget() {
		return this.target;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.base.IEffector#invoke()
	 */
	@Override
	public abstract int invoke() throws EffectorException;

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.name.toString();
	}
}
