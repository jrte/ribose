/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Thrown by the runtime engine when an effector throws an exception
 * 
 * @author kb
 */
public class EffectorException extends RteException {
	private static final long serialVersionUID = 1L;

	public EffectorException() {
	}

	public EffectorException(final String message) {
		super(message);
	}

	public EffectorException(final Throwable cause) {
		super(cause);
	}

	public EffectorException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
