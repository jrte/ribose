/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte;

/**
 * Thrown when there is a problem relating to the model
 * 
 * @author Kim Briggs
 */
public class ModelException extends RteException {
	private static final long serialVersionUID = 1L;

	public ModelException() {
	}

	public ModelException(final String message) {
		super(message);
	}

	public ModelException(final Throwable cause) {
		super(cause);
	}

	public ModelException(final String message, final Throwable cause) {
		super(message, cause);
	}

}