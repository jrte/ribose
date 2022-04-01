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
 * @author Kim Briggs
 */
public abstract class BaseInputOutputEffector extends BaseParameterizedEffector<Transduction, byte[][]> {
	protected BaseInputOutputEffector(Transduction transduction, Bytes name) {
		super(transduction, name);
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
	public int invoke() throws EffectorException {
		throw new EffectorException(String.format("The %1$s effector requires at least one parameter", super.getName()));
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#newParameters()
	 */
	@Override
	public final void newParameters(final int parameterCount) {
		super.parameters = new byte[parameterCount][][];
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.IParameterizedEffector#setParameter(int, byte[][])
	 */
	@Override
	public byte[][] compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
		if (parameterList.length < 1) {
			throw new TargetBindingException(String.format("The %1$s effector requires at least one parameter", super.getName()));
		}
		final byte[][] parameter = new byte[parameterList.length][];
		for (int i = 0; i < parameterList.length; i++) {
			assert !Base.isReferenceOrdinal(parameterList[i]);
			byte type = Base.getReferenceType(parameterList[i]);
			if (type == Base.TYPE_REFERENCE_VALUE) {
				final Bytes valueName = new Bytes(Base.getReferenceName(parameterList[i]));
				final Integer nameIndex = super.getTarget().getValueOrdinal(valueName);
				parameter[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_VALUE, nameIndex);
			} else if (type == Base.TYPE_REFERENCE_SIGNAL) {
				final Bytes valueName = new Bytes(Base.getReferenceName(parameterList[i]));
				final Integer signalOrdinal = super.getTarget().getModel().getSignalOrdinal(valueName);
				parameter[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, signalOrdinal);
			} else {
				parameter[i] = parameterList[i];
			}
		}
		this.parameters[parameterIndex] = parameter;
		return parameter;
	}
}
