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
import java.util.List;

import com.characterforming.ribose.IParametricEffector;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.IToken;

/**
 * Base {@link IParametricEffector} implementation class extends {@link BaseEffector}
 * to support specialized effectors. All {@link IParametricEffector} implementations
 * <i>must</i> extend {@link BaseParametricEffector} and <i>must</i> implement:
 * <ul>
 * <li>{@link IParametricEffector#invoke()}</li>
 * <li>{@link IParametricEffector#invoke(int)}</li>
 * <li>{@link IParametricEffector#allocateParameters(int)}</li>
 * <li>{@link IParametricEffector#compileParameter(IToken[])}</li>
 * </ul>
 * Subclasses may access indexed compiled parameter objects in their {@link #invoke(int)}
 * implementations directly as {@code super.parameters[int]}. Default {@link showParameterType()}
 * and {@link showParameterTokens(int)} methods are implemented here but subclasses
 * <i>may</i> override these if desired. Otherwise, public methods not exposed in the
 * {@link IParametricEffector} interface are for internal use only. These methods
 * implement the parameter compilation and binding protocols for all parametric effector
 * implementations.
 *
 * @param <T> the effector target type
 * @param <P> the effector parameter type, constructible from IToken[] (eg new P(IToken[]))
 * @author Kim Briggs
 * @see IParametricEffector
 */
public abstract class BaseParametricEffector<T extends ITarget, P> extends BaseEffector<T> implements IParametricEffector<T, P> {

	/** Raw and compiled effector parameters indexed and selected by parameter ordinal.*/
	protected P[] parameters = null;
	private IToken[][] tokens = null;

	/**
	 * Constructor
	 *
	 * @param target the target for the effector
	 * @param name the effector name as referenced from ginr transducers
	 * @throws CharacterCodingException if encoder fails
	 */
	protected BaseParametricEffector(final T target, final String name)
	throws CharacterCodingException {
		super(target, name);
	}

	@Override
	public String showParameterType() {
		return this.parameters != null && this.parameters.length > 0
				? this.parameters[0].getClass().getSimpleName()
				: "void";
	}

	@Override
	public String showParameterTokens(int parameterIndex) {
		StringBuilder sb = new StringBuilder(256);
		for (IToken token : this.tokens[parameterIndex]) {
			sb.append(sb.length() == 0 ? "`" : " `")
				.append(token.getSymbol().asString())
				.append("`");
		}
		return sb.toString();
	}

	/**
	 * Set the parameters array from proxy effector
	 *
	 * @param proxyEffector the proxy effector holding the compiled parameters
	 */
	@SuppressWarnings("unchecked")
	public final void setParameters(IParametricEffector<?,?> proxyEffector) {
		assert proxyEffector instanceof BaseParametricEffector<?,?>;
		if (proxyEffector instanceof BaseParametricEffector<?,?> proxy) {
			this.parameters = (P[])proxy.parameters;
		}
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
				parameterErrors.add(String.format("%1$s.%2$s[]: %3$s",
					super.target.getName(), super.getName(), e.getMessage()));
				fail = true;
			}
		}
		return fail;
	}
}
