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
 * support specialized effectors. The {@link IParameterizedEffector#allocateParameters(int)},
 * {@link IParameterizedEffector#compileParameter(byte[][])}, {@link IParameterizedEffector#invoke()},
 * and {@link IParameterizedEffector#invoke(int)} methods must be implemented by subclasses. 
 * 
 * @param <T> The effector target type
 * @param <P> The effector parameter type, constructible from byte[][] (eg new P(byte[][]))
 * @author Kim Briggs
 * @see com.characterforming.ribose.IParameterizedEffector
 */
public abstract class BaseParameterizedEffector<T extends ITarget, P> extends BaseEffector<T> implements IParameterizedEffector<T, P> {

	/** Effector parameters are indexed and selected by parameter ordinal.  */
	private P[] parameters = null;

	/**
	 * Constructor
	 * 
	 * @param target The target for the effector
	 * @param name The effector name as referenced from ginr transducers
	 */
	protected BaseParameterizedEffector(final T target, final String name) {
		super(target, name);
	}
	
	@Override // com.characterforming.ribose.IParameterizedEffector#getParameter(int)
	public P[] getParameters() {
		return this.parameters;
	}

	@Override // com.characterforming.ribose.IParameterizedEffector#setParameters(Object))
	@SuppressWarnings("unchecked")
	public void setParameters(Object proxy) {
		IParameterizedEffector<T, P> proxyEffector = (IParameterizedEffector<T, P>)proxy;
		this.parameters = proxyEffector.getParameters();
	}

	/**
	 * Allocate and populate the {@code parameters} array with precompiled
	 * parameter ({@code P}) instances, compiled from a list of parameter
	 * token arrays. This method is for internal use only. 
	 * 
	 * @param parameterTokensArray an array of byte[][] (raw effector parameter tokens)
	 * @throws TargetBindingException if a parameter fails to compile
	 */
	public void compileParameters(byte[][][] parameterTokensArray) throws TargetBindingException {
		this.parameters = this.allocateParameters(parameterTokensArray.length);
		for (int i = 0; i < parameterTokensArray.length; i++) {
			this.parameters[i] = this.compileParameter(parameterTokensArray[i]);
		}
	}

	/**
	 * Set parameter in parameters array at index
	 * 
	 * @param parameterIndex the parameter index in the array
	 * @param parameter the parameter instance
	 * @return the parameter instance
	 */
	protected P setParameter(int parameterIndex, P parameter) {
		this.parameters[parameterIndex] = parameter;
		return parameter;
	}

	/**
	 * Get parameter at index in parameters array
	 * 
	 * @param parameterIndex the parameter index in the array
	 * @return the parameter instance
	 */
	protected P getParameter(int parameterIndex) {
		return this.parameters[parameterIndex];
	}
}
