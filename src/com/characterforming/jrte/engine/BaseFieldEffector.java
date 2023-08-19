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

import com.characterforming.ribose.IToken;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Base class for parameterized field effectors, which are invoked with
 * field name parameters. The setParamater(int, charset, byte[][]), invoke(), and
 * invoke(int) methods must be implemented by subclasses.
 * 
 * @author Kim Briggs
 */
abstract class BaseFieldEffector extends BaseParameterizedEffector<Transductor, Integer> {
	/**
	 * Constructor
	 * 
	 * @param transductor The transductor target that binds the effector  
	 * @param name the field name
	 */
	protected BaseFieldEffector(final Transductor transductor, final String name) {
		super(transductor, name);
	}

	@Override // @see com.characterforming.ribose.IParameterizedEffector#iallocateParameters(int)
	public Integer[] allocateParameters(int parameterCount) {
		return new Integer[parameterCount];
	}

	@Override // IParameterizedEffector#setParameter(int, byte[][])
	public Integer compileParameter(final IToken[] parameterList) throws TargetBindingException {
		if (parameterList.length != 1) {
			throw new TargetBindingException(String.format("%1$s.%2$s: effector accepts exactly one parameter", 
				super.target.getName(), super.getName()));
		}
		if (parameterList[0].getType() != IToken.Type.FIELD) {
			throw new TargetBindingException(String.format("%1$s.%2$s: effector accepts only a FIELD parameter",
				super.target.getName(), super.getName()));
		}
		return parameterList[0].getSymbolOrdinal();
	}
}
