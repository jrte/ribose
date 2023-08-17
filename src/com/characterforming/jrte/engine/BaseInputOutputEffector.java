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
 * @author Kim Briggs
 */
abstract class BaseInputOutputEffector extends BaseParameterizedEffector<Transductor, byte[][]> {
	/**
	 * Constructor
	 *
	 * @param transductor The transductor target that binds the effector
	 * @param name the field name
	 */
	protected BaseInputOutputEffector(Transductor transductor, String name) {
		super(transductor, name);
	}

	@Override // @see com.characterforming.ribose.IParameterizedEffector#invoke()
	public int invoke() throws EffectorException {
		throw new EffectorException(String.format("The %1$s effector requires at least one parameter", super.getName()));
	}

	@Override // @see com.characterforming.ribose.IParameterizedEffector#iallocateParameters(int)
	public void allocateParameters(int parameterCount) {
		this.parameters = new byte[parameterCount][][];
	}

	@Override // @see com.characterforming.ribose.IParameterizedEffector#setParameter(int, byte[][])
	public byte[][] compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
		if (parameterList.length < 1) {
			throw new TargetBindingException(String.format("%1$s.%2$s: effector requires at least one parameter",
				super.target.getName(), super.getName()));
		}
		final byte[][] parameter = new byte[parameterList.length][];
		for (int i = 0; i < parameterList.length; i++) {
			assert !Base.isReferenceOrdinal(parameterList[i]);
			byte type = Base.getReferentType(parameterList[i]);
			if (type == Base.TYPE_REFERENCE_FIELD) {
				final Bytes fieldName = new Bytes(Base.getReferenceName(parameterList[i]));
				final Integer fieldOrdinal = super.target.getFieldOrdinal(fieldName);
				if (fieldOrdinal < 0) {
					throw new TargetBindingException(String.format("%1$s.%2$s: field name '%3$s' not enumerated for parameter compilation",
						super.target.getName(), super.getName().toString(), fieldName.toString()));
				}
				parameter[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_FIELD, fieldOrdinal);
			} else if (type == Base.TYPE_REFERENCE_SIGNAL) {
				throw new TargetBindingException(String.format("%1$s.%2$s: signal is not an acceptable parameter",
					super.target.getName(), super.getName().toString()));
			} else if (type == Base.TYPE_REFERENCE_TRANSDUCER) {
				throw new TargetBindingException(String.format("%1$s.%2$s: transducer is not an acceptable parameter",
					super.target.getName(), super.getName().toString()));
			} else {
				parameter[i] = parameterList[i];
			}
		}
		this.parameters[parameterIndex] = parameter;
		return parameter;
	}

	@Override
	public String showParameter(int parameterIndex) {
		StringBuilder sb = new StringBuilder(256);
		for (byte[] bytes : super.parameters[parameterIndex]) {
			final int ordinal = Base.isReferenceOrdinal(bytes) ? Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_FIELD, bytes) : -1;
			if (ordinal >= 0) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				bytes = super.target.getField(ordinal).getName().getData();
				byte[] name = new byte[bytes.length + 1];
				System.arraycopy(bytes, 0, name, 1, bytes.length);
				name[0] = Base.TYPE_REFERENCE_FIELD;
				sb.append(Bytes.decode(super.getDecoder(), name, name.length));
			} else {
				for (int i = 0; i < bytes.length; i++) {
					if (sb.length() > 0) {
						sb.append(' ');
					}
					if (bytes[i] > 32 && bytes[i] < 127) {
						sb.append((char)bytes[i]);
					} else {
						sb.append(String.format("#%02X", bytes[i]));
					}
				}
			}
		}
		return sb.toString();
	}
}
