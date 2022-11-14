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

import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * @author Kim Briggs
 */
abstract class BaseInputOutputEffector extends BaseParameterizedEffector<Transductor, byte[][]> {
	/**
	 * Constructor
	 * 
	 * @param transductor The transductor target that binds the effector  
	 * @param name the value name
	 */
	protected BaseInputOutputEffector(Transductor transductor, Bytes name) {
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
	public int invoke() throws EffectorException {
		throw new EffectorException(String.format("The %1$s effector requires at least one parameter", super.getName()));
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.ribose.IParameterizedEffector#newParameters(int)
	 */
	@Override
	public final void newParameters(final int parameterCount) {
		super.parameters = new byte[parameterCount][][];
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IParameterizedEffector#setParameter(int, byte[][])
	 */
	@Override
	public byte[][] compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
		if (parameterList.length < 1) {
			throw new TargetBindingException(String.format("%1$s.%2$s: effector requires at least one parameter", 
				super.getTarget().getName(), super.getName()));
		}
		final byte[][] parameter = new byte[parameterList.length][];
		for (int i = 0; i < parameterList.length; i++) {
			assert !Base.isReferenceOrdinal(parameterList[i]);
			byte type = Base.getReferentType(parameterList[i]);
			if (type == Base.TYPE_REFERENCE_VALUE) {
				final Bytes valueName = new Bytes(Base.getReferenceName(parameterList[i]));
				final Integer valueOrdinal = super.getTarget().getValueOrdinal(valueName);
				if (valueOrdinal < 0) {
					throw new TargetBindingException(String.format("%1$s.%2$s: value name '%3$s' not enumerated for parameter compilation", 
						super.getTarget().getName(), super.getName().toString(), valueName.toString()));
				}
				parameter[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_VALUE, valueOrdinal);
			} else if (type == Base.TYPE_REFERENCE_SIGNAL) {
				final Bytes signalName = new Bytes(Base.getReferenceName(parameterList[i]));
				final Integer signalOrdinal = super.getTarget().getModel().getSignalOrdinal(signalName);
				if (signalOrdinal < 0) {
					throw new TargetBindingException(String.format("%1$s.%2$s: signal name '%3$s' not enumerated for parameter compilation", 
						super.getTarget().getName(), super.getName().toString(), signalName.toString()));
				}
				parameter[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, signalOrdinal);
			} else {
				parameter[i] = parameterList[i];
			}
		}
		this.parameters[parameterIndex] = parameter;
		return parameter;
	}
}
