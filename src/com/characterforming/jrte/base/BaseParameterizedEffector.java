/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.base;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.characterforming.jrte.EffectorException;
import com.characterforming.jrte.IParameterizedEffector;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.engine.Transduction;

/**
 * Base {@link IParameterizedEffector} implementation class. The
 * {@link #newParameters(int)} {@link #setParameter(int, byte[][])},
 * {@link #invoke()}, and {@link #invoke(int)} methods must be implemented by
 * subclasses. Subclasses should use {@link #decodeParameter(byte[])} to decode
 * strings from parameter byte arrays using the gearbox Charset.
 * 
 * @param <T> The effector target type
 * @param <P> The effector parameter type, constructible from byte[][] (eg new
 *           P(byte[][]))
 * @author kb
 */
public abstract class BaseParameterizedEffector<T extends ITarget, P> extends BaseEffector<T> implements IParameterizedEffector<T, P> {
	private P[] parameters = null;

	/**
	 * Constructor
	 * 
	 * @param target The target for the effector
	 * @param name The effector name as referenced from ginr transducers
	 */
	protected BaseParameterizedEffector(final T target, final String name) {
		super(target, name);
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#newParameters()
	 */
	public abstract void newParameters(int parameterLength);

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#setParameter(int,
	 * Charset, byte[][])
	 */
	public abstract void setParameter(int parameterIndex, byte[][] parameterList) throws TargetBindingException;
	// TODO add P[] getParameters(), void setParameters(P[]) and maintain array[effector] of ?[] in gearbox

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.jrte.engine.IParameterizedEffector#getParameter()
	 */
	public P getParameter(final int parameterIndex) {
		return this.parameters[parameterIndex];
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.IParameterizedEffector#invoke(int)
	 */
	public abstract int invoke(int parameterIndex) throws EffectorException;

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.IParameterizedEffector#invoke()
	 */
	public abstract int invoke() throws EffectorException;

	/**
	 * Decode a byte array using the gearbox Charset
	 * 
	 * @param bytes The byte array to decode
	 * @return The decoded chars
	 */
	protected char[] decodeParameter(final byte[] bytes) {
		final Charset charset = ((Transduction) super.getTarget().getTransduction()).getGearbox().getCharset();
		return charset.decode(ByteBuffer.wrap(bytes)).array();
	}

	/**
	 * Set the parameters P[] from a previously compiled parameters array
	 * 
	 * @param parameters An array of compiled parameter values of type P
	 */
	@SuppressWarnings("unchecked")
	public final void setParameters(final Object[] parameters) {
		this.parameters = (P[]) parameters;
	}

	/**
	 * Subclasses must call this method from {@link #setParameter(int, byte[][])}
	 * to set the compiled parameter value in the the base class parameters array
	 * P[].
	 * 
	 * @param parameterIndex The array index in the parameters array P[] to set
	 *           with the parameter value
	 * @param parameter The parameter value to set
	 */
	protected final void setParameter(final int parameterIndex, final P parameter) {
		this.parameters[parameterIndex] = parameter;
	}
}
