/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Thrown when the amount of marked data in an marked input exceeds the maximum
 * 
 * @author kb
 */
public class MarkLimitExceededException extends InputException {
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public MarkLimitExceededException() {
	}

	/**
	 * @param message
	 */
	public MarkLimitExceededException(final String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public MarkLimitExceededException(final Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public MarkLimitExceededException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
