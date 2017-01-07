/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Thrown by the runtime engine when not transition defined for current input ordinal
 * 
 * @author kb
 */
public class DomainErrorException extends RteException {
	private static final long serialVersionUID = 1L;

	public DomainErrorException() {
	}

	public DomainErrorException(final String message) {
		super(message);
	}

	public DomainErrorException(final Throwable cause) {
		super(cause);
	}

	public DomainErrorException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
