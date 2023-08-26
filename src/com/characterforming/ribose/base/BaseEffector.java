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

package com.characterforming.ribose.base;

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import com.characterforming.jrte.engine.Base;
import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IField;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.ITarget;

/**
 * Base {@link IEffector} implementation class provides subclasses with access
 * to the transduction target and an output view to enable field extraction. 
 * The {@link invoke()} method must be implemented by subclasses.
 * 
 * @param <T> the effector target type
 * @author Kim Briggs
 */
public abstract class BaseEffector<T extends ITarget> implements IEffector<T> {

	/** Effector access to the target that it is bound to */
	protected T target;
	/** Effector view of Transductor loggers, UTF-8 codecs and fields. */
	protected IOutput output;

	private CharsetDecoder decoder;
	private CharsetEncoder encoder;
	private final Bytes name;
	
	/**
	 * Constructor receives target and a name.
	 * 
	 * @param target the target that binds the effector
	 * @param name the effector name
	 */
	protected BaseEffector(final T target, final String name) {
		this.name = Bytes.encode(Base.newCharsetEncoder(), name);
		this.target = target;
		this.output = null;
		this.decoder = null;
		this.encoder = null;
	}

	@Override // @see com.characterforming.ribose.base.IEffector#nvoke()
	public abstract int invoke() throws EffectorException;
	
	@Override // @see com.characterforming.ribose.base.IEffector#setOutput(IOutput)
	public void setOutput(IOutput output) throws TargetBindingException {
		this.output = output;
	}

	@Override // @see com.characterforming.ribose.base.IEffector#getName()
	public final Bytes getName() {
		return this.name;
	}

	@Override // @see com.characterforming.ribose.IEffector#getTarget()
	public final T getTarget() {
		return this.target;
	}

	@Override // com.characterforming.ribose.IEffector#getField(String)
	public IField getField(String fieldName) {
		try {
			return this.output.getField(Bytes.encode(this.getEncoder(), fieldName));
		} catch (TargetBindingException e) {
			assert false;
			return null;
		}
	}

	@Override // @see java.lang.Object#toString()
	public String toString() {
		return this.name.toString();
	}

	@Override // com.characterforming.ribose.IEffector#passivate()
	public void passivate() {
		this.target = null;
		this.output = null;
		this.encoder = null;
		this.decoder = null;
	}

	/**
	 * Lazy instantiation for charset decoder
	 * 
	 * @return a decoder instance
	*/
	protected CharsetDecoder getDecoder() {
		if (this.decoder == null) {
			this.decoder = Base.newCharsetDecoder();
		}
		return this.decoder;
	}

	/**
	 * Lazy instantiation for charset encoder
	 * 
	 * @return a encoder instance
	*/
	protected CharsetEncoder getEncoder() {
		if (this.encoder == null) {
			this.encoder = Base.newCharsetEncoder();
		}
		return this.encoder;
	}
}
