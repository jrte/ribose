/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Thrown when there is a problem relating to the gearbox
 * 
 * @author kb
 */
public class GearboxException extends RteException {
	private static final long serialVersionUID = 1L;

	public GearboxException() {
	}

	public GearboxException(final String message) {
		super(message);
	}

	public GearboxException(final Throwable cause) {
		super(cause);
	}

	public GearboxException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
