/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

import com.characterforming.jrte.base.BaseParameterizedEffector;

/**
 * Interface for parameterised effectors. Parameters are compiled from arrays of
 * bytes into an array of some parameter type P that is constructible from one
 * or more arrays of bytes. Compiled parameter values are referenced by their
 * index in the resulting array. At runtime, the invoke(int) method is called
 * with the parameter index to indicate which P[] to apply to the invocation.
 * 
 * @author kb
 * @param <T> The effector target type
 * @param <P> The effector parameter type, constructible from byte[][] (eg new
 *           P(byte[][]))
 */
public interface IParameterizedEffector<T extends ITarget, P> extends IEffector<T> {
	/**
	 * Create a parameters array with capacity for a specified number of compiled
	 * parameter values. The implementation class must call
	 * {@link BaseParameterizedEffector#setParameters(Object[])} with the result.
	 * 
	 * @param parameterCount The size of the array to create
	 */
	public void newParameters(int parameterCount);

	/**
	 * Compile and set a parameter value from effector arguments specified in
	 * gearbox transducers. The parameter value, which may be a scalar or an
	 * array, is to be compiled from an array of byte arrays.
	 * The implementation class must call
	 * {@link BaseParameterizedEffector#setParameter(int, Object)} with the
	 * result.
	 * 
	 * @param parameterIndex The array index in the parameters array P[] to set
	 *           with the parameter value
	 * @param parameterList An array of parameters, where each parameter is an
	 *           array of bytes.
	 * @throws TargetBindingException
	 */
	public void setParameter(int parameterIndex, byte[][] parameterList) throws TargetBindingException;

	/**
	 * Get a compiled parameter value
	 * 
	 * @param parameterIndex The parameter index
	 * @return The parameter value at the index
	 */
	public P getParameter(int parameterIndex);

	/**
	 * This method is invoked at runtime when triggered by an input transition,
	 * passing a parameter index to indicate which parameter object is to be
	 * used in the invocation.
	 * 
	 * @param parameterIndex This index of the parameter object to be used in the
	 *           invocation
	 * @return User-defined effectors should return 0
	 * @throws EffectorException
	 */
	public int invoke(int parameterIndex) throws EffectorException;
}
