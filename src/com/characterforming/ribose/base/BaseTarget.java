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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See
 * LICENSE-gpl-3.0. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.ribose.base;

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.ITarget;

/**
 * Base {@link ITarget} implementation provides subclasses with charset
 * codecs and access to the built-in transduction effectors. It can be extended
 * to realize specialzed targets with additional effectors and behaviours.
 * 
 * @author Kim Briggs
 */
public class BaseTarget implements ITarget {
	private final CharsetDecoder decoder;
	private final CharsetEncoder encoder;

	/**
	 * Constructor
	 */
	public BaseTarget() {
		super();
		this.decoder = Base.newCharsetDecoder();
		this.encoder = Base.newCharsetEncoder();
	}
	
	@Override // ITarget#getEffectors()
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		// This is just a proxy for Transductor.getEffectors()
		return new IEffector<?>[] { };
	}

	@Override	// ITarget#getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override	// ITarget#getCharsetDecoder()
	public CharsetDecoder getCharsetDecoder() {
		return this.decoder;
	}

	@Override	// ITarget#getCharsetDecoder()
	public CharsetEncoder getCharsetEncoder() {
		return this.encoder;
	}
}