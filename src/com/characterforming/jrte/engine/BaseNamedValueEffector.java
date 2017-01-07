/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import com.characterforming.jrte.EffectorException;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.base.BaseParameterizedEffector;

/**
 * Base class for parameterised named value effectors, which can be invoked with
 * value name
 * parameters. The setParamater(int, charset, byte[][]), invoke(), and
 * invoke(int) methods
 * must be implemented by subclasses.
 * 
 * @author kb
 */
public abstract class BaseNamedValueEffector extends BaseParameterizedEffector<Transduction, Integer> {
	protected BaseNamedValueEffector(final Transduction target, final String name) {
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
	public final void newParameters(final int parameterCount) {
		super.setParameters(new Integer[parameterCount]);
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#setParameter(int,
	 * byte[][])
	 */
	@Override
	public void setParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
		if (parameterList.length != 1) {
			throw new TargetBindingException(String.format("The %1$s effector accepts exactly one parameter", super.getName()));
		}
		final char[] valueName = super.decodeParameter(parameterList[0]);
		if (valueName[0] == Transduction.TYPE_REFERENCE_VALUE) {
			final Integer nameIndex = super.getTarget().getNamedValueReference(valueName, true);
			if (nameIndex != null) {
				super.getTarget().ensureNamedValueCapacity(nameIndex);
				super.setParameter(parameterIndex, nameIndex);
				return;
			}
			throw new TargetBindingException(String.format("Unrecognized value reference `%1$s` for %2$s effector", new String(valueName), this.getName()));
		}
		throw new TargetBindingException(String.format("Invalid value reference `%1$s` for %2$s effector, requires type indicator ('%3$c') before the value name", new String(valueName), this.getName(), Transduction.TYPE_REFERENCE_VALUE));
	}
}
