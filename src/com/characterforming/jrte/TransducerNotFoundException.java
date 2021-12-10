/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Thrown when a named transducer cannot be found in the gearbox
 * 
 * @author kb
 */
public class TransducerNotFoundException extends RteException {
	private static final long serialVersionUID = 1L;

	public TransducerNotFoundException() {
	}

	public TransducerNotFoundException(final String message) {
		super(message);
	}

	public TransducerNotFoundException(final Throwable cause) {
		super(cause);
	}

	public TransducerNotFoundException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
