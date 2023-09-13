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

import java.util.List;

import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.IToken;

/**
 * Base {@link IParameterizedEffector} implementation class extends {@link BaseEffector} 
 * to support specialized effectors. All {@code IParameterizedEffector} implementation classes
 * <i>must</i> extend {@code BaseParameterizedEffector}. Subclasses <i>must</i> implement
 * {@link IParameterizedEffector#invoke()}, {@link IParameterizedEffector#invoke(int)},
 * {@link IParameterizedEffector#allocateParameters(int)}, {@link IParameterizedEffector#compileParameter(IToken[])}
 * and {@link IParameterizedEffector#showParameterType()} methods and <i>may</i> override
 * {@link IParameterizedEffector#showParameterTokens(int)} if required.
 * 
 * @param <T> the effector target type
 * @param <P> the effector parameter type, constructible from IToken[] (eg new P(IToken[]))
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
	 * @param target the target for the effector
	 * @param name the effector name as referenced from ginr transducers
	 */
	protected BaseParameterizedEffector(final T target, final String name) {
		super(target, name);
	}

	@Override // @see com.characterforming.ribose.base.IParameterizedEffector#nvoke(int)
	public abstract int invoke(int parameterIndex) throws EffectorException;

	@Override // @see com.characterforming.ribose.base.IParameterizedEffector#allocateParameters(int)
	public abstract P[] allocateParameters(int parameterCount);

	@Override // @see com.characterforming.ribose.base.IParameterizedEffector#compileParameter(IToken[])
	public abstract P compileParameter(IToken[] parameterTokens) throws TargetBindingException;

	@Override // @see com.characterforming.ribose.base.IParameterizedEffector#showParameterType(int)
	public abstract String showParameterType();

	@Override // com.characterforming.ribose.IParameterizedEffector#getParameterTokens(int)
	public final IToken[] getParameterTokens(int parameterIndex) {
		return this.tokens[parameterIndex];
	}

	@Override // com.characterforming.ribose.IParameterizedEffector#getParameters(int)
	public final P[] getParameters() {
		return this.parameters;
	}

	@Override // com.characterforming.ribose.IParameterizedEffector#setParameters(Object)
	@SuppressWarnings("unchecked")
	public final void setParameters(Object proxyEffector) {
		IParameterizedEffector<T,P> proxy = (IParameterizedEffector<T,P>)proxyEffector;
		this.parameters = proxy.getParameters();
	}

	/**
	 * Get the number of parameters for this effector (after parameter compilation
	 * is complete)
	 * 
	 * @return the parameter count
	 * @see compileParameters(IToken[][], List<String>)
	 */
	public final int getParameterCount() {
		return this.parameters != null ? this.parameters.length : 0;
	}

	/**
	 * Allocate and populate the {@code parameters} array with precompiled
	 * parameter ({@code P}) instances, compiled from a list of parameter
	 * token arrays. This method is for internal use only.
	 * 
	 * @param parameterTokens an array of IToken[] (raw effector parameter tokens)
	 * @param parameterErrors a list of error messages if one ore more parameters fail to compile
	 * @return false if any errors were reported
	 */
	public final boolean compileParameters(IToken[][] parameterTokens, List<String> parameterErrors) {
		boolean fail = false;
		this.tokens = parameterTokens;
		this.parameters = this.allocateParameters(parameterTokens.length);
		for (int i = 0; i < parameterTokens.length; i++) {
			try {
				this.parameters[i] = this.compileParameter(parameterTokens[i]);
			} catch (TargetBindingException e) {
				parameterErrors.add(e.getMessage());
				fail = true;
			} catch (Exception e) {
				parameterErrors.add(String.format("%1$s.%2$s[]: %3$%s",
					super.getTarget().getName(), super.getName(), e.getMessage()));
				fail = true;
			}
		}
		return fail;
	}

	/**
	 * Get parameter at index in parameters array
	 * 
	 * @param parameterIndex the parameter index in the array
	 * @return the parameter instance
	 */
	protected final P getParameter(int parameterIndex) {
		return this.parameters[parameterIndex];
	}

	@Override
	public String showParameterTokens(int parameterIndex) {
		StringBuilder sb = new StringBuilder(256);
		for (IToken token : getParameterTokens(parameterIndex)) {
			sb.append(sb.length() == 0 ? "`" : " `").append(token.toString()).append("`");
		}
		return sb.toString();
	}
}
