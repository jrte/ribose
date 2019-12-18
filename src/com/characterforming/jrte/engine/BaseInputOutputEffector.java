/**
 * 
 */
package com.characterforming.jrte.engine;

import java.util.Arrays;

import com.characterforming.jrte.EffectorException;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.base.BaseParameterizedEffector;

/**
 * @author kb
 */
public abstract class BaseInputOutputEffector extends BaseParameterizedEffector<Transduction, char[][]> {
	private boolean hasBufferReferences;

	protected BaseInputOutputEffector(final Transduction target, final String name) {
		super(target, name);
		this.hasBufferReferences = false;
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
		super.setParameters(new char[parameterCount][][]);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.IParameterizedEffector#setParameter(int, byte[][])
	 */
	@Override
	public void setParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
		if (parameterList.length < 1) {
			throw new TargetBindingException(String.format("The %1$s effector requires at leat one parameter", super.getName()));
		}
		final char[][] parameter = new char[parameterList.length][];
		for (int i = 0; i < parameterList.length; i++) {
			parameter[i] = super.decodeParameter(parameterList[i]);
			if (parameter[i][0] == Transduction.TYPE_REFERENCE_VALUE) {
				final char[] referenceName = Arrays.copyOfRange(parameter[i], 1, parameter[i].length);
				final Integer nameIndex = super.getTarget().getNamedValueReference(referenceName, true);
				if (nameIndex != null) {
					super.getTarget().ensureNamedValueCapacity(nameIndex);
					parameter[i] = new char[] { Character.MAX_VALUE, (char) nameIndex.intValue() };
					this.hasBufferReferences = true;
				} else {
					throw new TargetBindingException(String.format("Unrecognized value reference `%1$s` for %2$s effector", new String(parameter[i]), this.getName()));
				}
			} else if (parameter[i][0] == Transduction.TYPE_REFERENCE_SIGNAL) {
				parameter[i] = super.getTarget().getGearbox().getSignalReference(parameter[i]);
				if (parameter[i] == null) {
					throw new TargetBindingException(String.format("Unrecognized signal reference `%1$s` for %2$s effector", new String(parameter[i]), this.getName()));
				}
			}
		}
		super.setParameter(parameterIndex, parameter);
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#getParameter(int,
	 * byte[][])
	 */
	@Override
	public char[][] getParameter(final int parameterIndex) {
		char[][] parameter = super.getParameter(parameterIndex);
		if (this.hasBufferReferences) {
			parameter = Arrays.copyOf(parameter, parameter.length);
			for (int i = 0; i < parameter.length; i++) {
				if (parameter[i][0] == Character.MAX_VALUE) {
					int namedValueIndex = (int)parameter[i][1];
					parameter[i] = super.getTarget().copyNamedValue(namedValueIndex);
				}
			}
		}
		return parameter;
	}
}
