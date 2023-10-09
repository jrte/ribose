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

package com.characterforming.ribose.base;

import java.nio.charset.CharacterCodingException;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.ITarget;

/**
 * Base {@link IEffector} implementation class provides subclasses with access
 * to the transduction target and an output view to enable field extraction. 
 * The {@link invoke()} method must be implemented by subclasses.
 * 
 * @param <T> the effector target type
 * @author Kim Briggs
 */
public abstract class BaseEffector<T extends ITarget> implements IEffector<T> {

	/** Effector access to the target that it is bound to */
	protected T target;
	/** Effector view of Transductor loggers, UTF-8 codecs and fields. */
	protected IOutput output;

	private final Bytes name;
	
	/**
	 * Constructor receives target and a name.
	 * 
	 * @param target the target that binds the effector
	 * @param name the effector name
	 * @throws CharacterCodingException if encoder fails
	 */
	protected BaseEffector(final T target, final String name)
	throws CharacterCodingException {
		this.name = Codec.encode(name);
		this.target = target;
		this.output = null;
	}

	@Override // @see com.characterforming.ribose.base.IEffector#nvoke()
	public abstract int invoke() throws EffectorException;
	
	@Override // @see com.characterforming.ribose.base.IEffector#setOutput(IOutput)
	public void setOutput(IOutput output)
	throws EffectorException {
		this.output = output;
	}

	@Override // @see com.characterforming.ribose.base.IEffector#getName()
	public final Bytes getName() {
		return this.name;
	}

	@Override // @see com.characterforming.ribose.IEffector#getTarget()
	public final T getTarget() {
		return this.target;
	}

	@Override // @see java.lang.Object#toString()
	public String toString() {
		return this.name.asString();
	}

	@Override // com.characterforming.ribose.IEffector#passivate()
	public void passivate() {
		this.target = null;
		this.output = null;
	}
}
