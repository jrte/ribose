/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Thrown when the target class cannot be instantitated using a default constructor
 * 
 * @author kb
 */
public class TargetNotFoundException extends GearboxException {
	private static final long serialVersionUID = 1L;

	public TargetNotFoundException() {
	}

	public TargetNotFoundException(final String message) {
		super(message);
	}

	public TargetNotFoundException(final Throwable cause) {
		super(cause);
	}

	public TargetNotFoundException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
