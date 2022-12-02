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

package com.characterforming.ribose;

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Provides a view of named values (data extracted by the transduction) to {@link IEffector}
 * instances bound to the transaction. Effectors receive their {@code IOutput} instance via
 * {@link IEffector#setOutput(IOutput)} when they are first bound to a transductor.
 * 
 * @author Kim Briggs
 */
public interface IOutput {
	/**
	 * Get a named value by name
	 * 
	 * @param valueName The name of the value (UTF-8 bytes)
	 * @return a named value instance or null
	 * @throws TargetBindingException on error
	 */
	INamedValue getNamedValue(Bytes valueName) throws TargetBindingException;
	
	/**
	 * Get the numeric index for a defined named value
	 * 
	 * @param valueName The name of the value (UTF-8 bytes)
	 * @return The numeric index of the value
	 * @throws TargetBindingException on error
	 */
	int getValueOrdinal(Bytes valueName) throws TargetBindingException;

	/**
	 * Get a copy of the current value for a named value
	 * 
	 * @param valueOrdinal The numeric index of the named value to get
	 * @return The named value wrapped in an {@link INamedValue} instance
	 */
	INamedValue getNamedValue(int valueOrdinal);

	/**
	 * Get the numeric index for the current selected named value
	 * 
	 * @return The numeric index of the selected value
	 */
	int getSelectedOrdinal();

	/**
	 * Get a copy of the current selected value
	 * 
	 * @return The selected value wrapped in an {@link INamedValue} instance
	 */
	INamedValue getSelectedValue();
}
