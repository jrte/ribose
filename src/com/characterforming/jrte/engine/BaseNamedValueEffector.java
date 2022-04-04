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

package com.characterforming.jrte.engine;

import com.characterforming.jrte.EffectorException;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.BaseParameterizedEffector;
import com.characterforming.jrte.base.Bytes;

/**
 * Base class for parameterised named value effectors, which are invoked with
 * value name parameters. The setParamater(int, charset, byte[][]), invoke(), and
 * invoke(int) methods must be implemented by subclasses.
 * 
 * @author Kim Briggs
 */
public abstract class BaseNamedValueEffector extends BaseParameterizedEffector<Transduction, Integer> {
	protected BaseNamedValueEffector(final Transduction target, final Bytes name) {
		super(target, name);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.IParameterizedEffector#invoke(int)
	 */
	@Override
	public abstract int invoke(int parameterIndex) throws EffectorException;

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.IParameterizedEffector#invoke()
	 */
	@Override
	public abstract int invoke() throws EffectorException;

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#newParameters()
	 */
	@Override
	public void newParameters(int parameterCount) {
		super.parameters = new Integer[parameterCount];
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#newParameters()
	 */
	@Override
	public Integer getParameter(int parameterOrdinal) {
		return this.parameters[parameterOrdinal];
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#setParameter(int,
	 * byte[][])
	 */
	@Override
	public Integer compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
		if (parameterList.length != 1) {
			throw new TargetBindingException(String.format("The %1$s effector accepts exactly one parameter", super.getName()));
		} else if (parameterList[0].length > 0 && parameterList[0][0] != Base.TYPE_REFERENCE_VALUE) {
			throw new TargetBindingException(String.format("The %1$s effector accepts only `~<value-name>` parameters, %2$s is not valid", 
				super.getName(), new Bytes(parameterList[0]).toString()));
		}
		final Bytes valueName = Bytes.getBytes(parameterList[0], 1, parameterList[0].length - 1);
		final Integer valueOrdinal = super.getTarget().getValueOrdinal(valueName);
		super.setParameter(parameterIndex, valueOrdinal);
		return valueOrdinal;
	}
}
