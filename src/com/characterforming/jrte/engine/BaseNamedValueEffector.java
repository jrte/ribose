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
 * LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Base class for parameterised named value effectors, which are invoked with
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
	protected BaseNamedValueEffector(final Transductor transductor, final Bytes name) {
		super(transductor, name);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#invoke(int)
	 */
	@Override
	public abstract int invoke(int parameterIndex) throws EffectorException;

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#invoke()
	 */
	@Override
	public abstract int invoke() throws EffectorException;

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#newParameters(int)
	 */
	@Override
	public void newParameters(int parameterCount) {
		super.parameters = new Integer[parameterCount];
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#newParameters(int)
	 */
	@Override
	public Integer getParameter(int parameterOrdinal) {
		return this.parameters[parameterOrdinal];
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#setParameter(int, byte[][])
	 */
	@Override
	public Integer compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
		if (parameterList.length != 1) {
			throw new TargetBindingException(String.format("%1$s.%2$s: effector accepts exactly one parameter", 
				super.getTarget().getName(), super.getName()));
		}
		final Bytes valueName = Bytes.getBytes(parameterList[0], 1, parameterList[0].length - 1);
		final Integer valueOrdinal = super.getTarget().getValueOrdinal(valueName);
		if (valueOrdinal < 0) {
			throw new TargetBindingException(String.format("%1$s.%2$s: value name '%3$s' not enumerated for parameter compilation", 
				super.getTarget().getName(), super.getName().toString(), valueName.toString()));
		}
		super.setParameter(parameterIndex, valueOrdinal);
		return valueOrdinal;
	}
}
