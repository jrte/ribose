package com.characterforming.ribose.base;

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.ITarget;

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