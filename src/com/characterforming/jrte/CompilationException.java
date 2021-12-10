/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Thrown by the gearbox compiler when an error is found during gearbox compilation
 * 
 * @author kb
 */
public class CompilationException extends Exception {
	private static final long serialVersionUID = 1L;

	public CompilationException() {
		super();
	}

	public CompilationException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public CompilationException(final String message) {
		super(message);
	}

	public CompilationException(final Throwable cause) {
		super(cause);
	}
}
