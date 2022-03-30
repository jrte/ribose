/**
 * 
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
				final Integer signalOrdinal = super.getTarget().getGearbox().getSignalOrdinal(valueName);
				parameter[i] = Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, signalOrdinal);
			} else {
				parameter[i] = parameterList[i];
			}
		}
		this.parameters[parameterIndex] = parameter;
		return parameter;
	}
}
