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
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.ribose.base;

import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.ITarget;

/**
 * Base {@link IParameterizedEffector} implementation class. The
 * {@link #newParameters(int)} {@link #compileParameter(int, byte[][])},
 * {@link #invoke()}, and {@link #invoke(int)} methods must be implemented by
 * subclasses. Subclasses can use {@link Bytes#decode(byte[], int)} to decode UTF-8
 * byte[] to char[] or String from parameter byte arrays using the default Charset.
 * 
 * @param <T> The effector target type
 * @param <P> The effector parameter type, constructible from byte[][] (eg new
 *           P(byte[][]))
 * @author Kim Briggs
 */
public abstract class BaseParameterizedEffector<T extends ITarget, P> extends BaseEffector<T> implements IParameterizedEffector<T, P> {
	/**
	 * Effector parameters are indexed and selected by parameter ordinal.
	 */
	protected P[] parameters = null;

	/**
	 * Constructor
	 * 
	 * @param target The target for the effector
	 * @param name The effector name as referenced from ginr transducers
	 */
	protected BaseParameterizedEffector(final T target, final Bytes name) {
		super(target, name);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#newParameters(int)
	 */
	@Override
	public abstract void newParameters(int parameterCount);

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#getParameterCount()
	 */
	@Override
	public int getParameterCount() {
		assert this.parameters != null;
		return (this.parameters != null) ? this.parameters.length : 0;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#compileParameter(int, byte[][])
	 */
	@Override
	public abstract P compileParameter(int parameterIndex, byte[][] parameterList) throws TargetBindingException;

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#setParameter(int, Object)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void setParameter(int parameterIndex, Object parameter) {
		this.parameters[parameterIndex] = (P)parameter;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.ribose.IParameterizedEffector#getParameter(int)
	 */
	@Override
	public P getParameter(final int parameterIndex) {
		return this.parameters[parameterIndex];
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#invoke(int)
	 */
	@Override
	public abstract int invoke(int parameterIndex) throws EffectorException;
}
