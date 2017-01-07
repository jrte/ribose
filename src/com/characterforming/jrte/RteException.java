/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Base class for runtime exceptions
 * 
 * @author kb
 */
public class RteException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public RteException() {
	}

	public RteException(final String message) {
		super(message);
	}

	public RteException(final Throwable cause) {
		super(cause);
	}

	public RteException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
