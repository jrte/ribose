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
import com.characterforming.ribose.IToken;

/**
 * Base {@link IParameterizedEffector} implementation class extends {@link BaseEffector} to 
 * support specialized effectors. The {@link IParameterizedEffector#allocateParameters(int)},
 * {@link IParameterizedEffector#compileParameter(IToken[])}, {@link IParameterizedEffector#invoke()},
 * and {@link IParameterizedEffector#invoke(int)} methods must be implemented by subclasses. 
 * 
 * @param <T> The effector target type
 * @param <P> The effector parameter type, constructible from byte[][] (eg new P(byte[][]))
 * @author Kim Briggs
 * @see com.characterforming.ribose.IParameterizedEffector
 */
public abstract class BaseParameterizedEffector<T extends ITarget, P> extends BaseEffector<T> implements IParameterizedEffector<T, P> {

	/** Effector parameters indexed and selected by parameter ordinal.*/
	private IToken[][] tokens = null;
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

	@Override // com.characterforming.ribose.IParameterizedEffector#getParameterTokens(int)
	public IToken[]  getParameterTokens(int parameterIndex) {
		return this.tokens[parameterIndex];
	}

	@Override // com.characterforming.ribose.IParameterizedEffector#getParameters(int)
	public P[] getParameters() {
		return this.parameters;
	}

	@Override // com.characterforming.ribose.IParameterizedEffector#setParameters(Object))
	@SuppressWarnings("unchecked")
	public void setParameters(Object proxy) {
		IParameterizedEffector<T, P> proxyEffector = (IParameterizedEffector<T, P>)proxy;
		this.parameters = proxyEffector.getParameters();
	}

	@Override
	public String showParameterTokens(int parameterIndex) {
		StringBuilder sb = new StringBuilder(256);
		for (IToken token : getParameterTokens(parameterIndex)) {
			byte[] name = token.getLiteralValue();
			sb.append(sb.length() > 0 ? " `" : "`");
			sb.append(Bytes.decode(super.getDecoder(), name, name.length));
			sb.append("`");
		}
		return sb.toString();
	}

	/**
	 * Get the number of parameters for this effector (after parameter compilation
	 * is complete)
	 * 
	 * @return the parameter count
	 * @see #compileParameters(IToken[][])
	 */
	public int getParameterCount() {
		return this.parameters != null ? this.parameters.length : 0;
	}

	/**
	 * Allocate and populate the {@code parameters} array with precompiled
	 * parameter ({@code P}) instances, compiled from a list of parameter
	 * token arrays. This method is for internal use only.
	 * 
	 * @param parameterTokens an array of byte[][] (raw effector parameter tokens)
	 * @throws TargetBindingException if a parameter fails to compile
	 */
	public void compileParameters(IToken[][] parameterTokens) throws TargetBindingException {
		this.tokens = parameterTokens;
		this.parameters = this.allocateParameters(parameterTokens.length);
		for (int i = 0; i < parameterTokens.length; i++) {
			this.parameters[i] = this.compileParameter(parameterTokens[i]);
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
