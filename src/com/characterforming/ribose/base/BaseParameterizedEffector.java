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

import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.ITarget;

/**
 * Base {@link IParameterizedEffector} implementation class extends {@link BaseEffector} to 
 * support specialized effectors. The {@link #newParameters(int)}, {@link #compileParameter(int, byte[][])},
 * {@link #invoke()}, and {@link #invoke(int)} methods must be implemented by subclasses. 
 * 
 * @param <T> The effector target type
 * @param <P> The effector parameter type, constructible from byte[][] (eg new P(byte[][]))
 * @author Kim Briggs
 * @see com.characterforming.ribose.IParameterizedEffector
 */
public abstract class BaseParameterizedEffector<T extends ITarget, P> extends BaseEffector<T> implements IParameterizedEffector<T, P> {

	/** Effector parameters are indexed and selected by parameter ordinal.  */
	protected P[] parameters = null;

	/**
	 * Constructor
	 * 
	 * @param target The target for the effector
	 * @param name The effector name as referenced from ginr transducers
	 */
	protected BaseParameterizedEffector(final T target, final String name) {
		super(target, name);
	}

	@Override // @see com.characterforming.ribose.IParameterizedEffector#newParameters(int)
	public abstract void newParameters(int parameterCount);

	@Override // @see com.characterforming.ribose.IParameterizedEffector#getParameterCount()
	public int getParameterCount() {
		assert this.parameters != null;
		return (this.parameters != null) ? this.parameters.length : 0;
	}

	@Override // @see com.characterforming.ribose.IParameterizedEffector#compileParameter(int, byte[][])
	public abstract P compileParameter(int parameterIndex, byte[][] parameterList) throws TargetBindingException;

	@Override // @see com.characterforming.ribose.IParameterizedEffector#setParameter(int, Object)
	@SuppressWarnings("unchecked")
	public void setParameter(int parameterIndex, Object parameter) {
		this.parameters[parameterIndex] = (P)parameter;
	}

	@Override // com.characterforming.ribose.IParameterizedEffector#getParameter(int)
	public P getParameter(final int parameterIndex) {
		return this.parameters[parameterIndex];
	}
	
	@Override // com.characterforming.ribose.IParameterizedEffector#getParameter(int)
	public P[] getParameters() {
		return this.parameters;
	}
	
	@Override // @see com.characterforming.ribose.IParameterizedEffector#invoke(int)
	public abstract int invoke(int parameterIndex) throws EffectorException;
}
