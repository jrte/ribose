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

package com.characterforming.jrte.engine;

import java.nio.charset.CharacterCodingException;

import com.characterforming.ribose.IToken;
import com.characterforming.ribose.base.BaseParametricEffector;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * @author Kim Briggs
 */
abstract class BaseInputOutputEffector extends BaseParametricEffector<Transductor, IToken[]> {
	/**
	 * Constructor
	 *
	 * @param transductor The transductor target that binds the effector
	 * @param name the field name
	 * @throws CharacterCodingException
	 */
	protected BaseInputOutputEffector(Transductor transductor, String name) throws CharacterCodingException {
		super(transductor, name);
	}

	@Override // @see com.characterforming.ribose.IParametricEffector#invoke()
	public int invoke() throws EffectorException {
		throw new EffectorException(String.format("The %1$s effector requires at least one parameter", super.getName()));
	}

	@Override // @see com.characterforming.ribose.IParametricEffector#allocateParameters(int)
	public IToken[][] allocateParameters(int parameterCount) {
		return new IToken[parameterCount][];
	}

	@Override // @see com.characterforming.ribose.IParametricEffector#compileParameter(IToken[])
	public IToken[] compileParameter(final IToken[] parameterList) throws TargetBindingException {
		if (parameterList.length < 1) {
			throw new TargetBindingException(String.format(
				"%1$s.%2$s[]: effector requires at least one parameter",
				super.target.getName(), super.getName()));
		}
		for (IToken token : parameterList) {
			if (!token.isLiteral() && !token.isField()) {
				throw new TargetBindingException(String.format(
					"%1$s.%2$s[]: literal or field name expected, found '%3$s'",
					super.target.getName(), super.getName().asString(),
					token.asString()));
			}
		}
		return parameterList;
	}
}
