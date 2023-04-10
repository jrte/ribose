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

import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Base class for parameterized named value effectors, which are invoked with
 * value name parameters. The setParamater(int, charset, byte[][]), invoke(), and
 * invoke(int) methods must be implemented by subclasses.
 * 
 * @author Kim Briggs
 */
abstract class BaseNamedValueEffector extends BaseParameterizedEffector<Transductor, Integer> {
	/**
	 * Constructor
	 * 
	 * @param transductor The transductor target that binds the effector  
	 * @param name the value name
	 */
	protected BaseNamedValueEffector(final Transductor transductor, final String name) {
		super(transductor, name);
	}

	@Override // IParameterizedEffector#invoke(int)
	public abstract int invoke(int parameterIndex) throws EffectorException;

	@Override // IParameterizedEffector#invoke()
	public abstract int invoke() throws EffectorException;

	@Override // IParameterizedEffector#newParameters(int)
	public void newParameters(int parameterCount) {
		super.parameters = new Integer[parameterCount];
	}

	@Override // IParameterizedEffector#getParameter(int)
	public Integer getParameter(int parameterOrdinal) {
		return this.parameters[parameterOrdinal];
	}

	@Override // IParameterizedEffector#setParameter(int, byte[][])
	public Integer compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
		if (parameterList.length != 1) {
			throw new TargetBindingException(String.format("%1$s.%2$s: effector accepts exactly one parameter", 
				super.target.getName(), super.getName()));
		}
		final Bytes valueName = new Bytes(parameterList[0], 1, parameterList[0].length - 1);
		final Integer valueOrdinal = super.target.getValueOrdinal(valueName);
		if (valueOrdinal < 0) {
			throw new TargetBindingException(String.format("%1$s.%2$s: value name '%3$s' not enumerated for parameter compilation", 
				super.target.getName(), super.getName().toString(), valueName.toString()));
		}
		super.setParameter(parameterIndex, valueOrdinal);
		return valueOrdinal;
	}

	@Override
	public String showParameter(int parameterIndex) {
		byte[] name = super.target.getModel().getValueName(super.parameters[parameterIndex]);
		byte[] value = new byte[name.length + 1];
		value[0] = Base.TYPE_REFERENCE_VALUE;
		System.arraycopy(name, 0, value, 1, name.length);
		return Bytes.decode(super.getDecoder(), value, value.length).toString();
	}
}
