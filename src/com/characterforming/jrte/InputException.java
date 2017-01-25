/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Thrown when there is a problem with the input
 * 
 * @author kb
 */
public class InputException extends RteException {
	private static final long serialVersionUID = 1L;

	public InputException() {
	}

	public InputException(final String message) {
		super(message);
	}

	public InputException(final Throwable cause) {
		super(cause);
	}

	public InputException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
