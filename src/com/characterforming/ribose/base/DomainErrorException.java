/***
 * Ribose is a recursive transduction engine for Java
 * 
 * Copyright (C) 2011,2022 Kim Briggs
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program (LICENSE-gpl-3.0). If not, see
 * <http://www.gnu.org/licenses/#GPL>.
 */

package com.characterforming.ribose.base;

/**
 * Thrown by the runtime engine when no transition is defined for nul signal injected 
 * after receiving an input ordinal not recognized for current state. 
 * 
 * @author Kim Briggs
 */
public class DomainErrorException extends Exception {
	private static final long serialVersionUID = 1L;

	/** Constructor */
	public DomainErrorException() {
	}

	/**
	 * Constructor
	 * 
	 * @param message exception message
	 */
	public DomainErrorException(final String message) {
		super(message);
	}

	/**
	 * Constructor
	 * 
	 * @param cause the causal exception
	 */
	public DomainErrorException(final Throwable cause) {
		super(cause);
	}

	/**
	 * Constructor
	 * 
	 * @param message exception message
	 * @param cause the causal exception
	 */
	public DomainErrorException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
