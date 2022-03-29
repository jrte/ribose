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

import com.characterforming.jrte.base.Bytes;

/**
 * Implementation provides a view of named values (data extracted by the transduction) to 
 * IEffector instances bound to the transaction.
 * 
 * @author kb
 *
 */
public interface IOutput {
	/**
	 * Get the numeric index for a defined named value
	 * 
	 * @param valueName The name of the value (UTF-8 bytes)
	 * @return The numeric index of the value
	 * @throws TargetBindingException On error
	 */
	public int getValueOrdinal(Bytes valueName) throws TargetBindingException;

	/**
	 * Get a copy of the current value for a named value
	 * 
	 * @param valueOrdinal The numeric index of the named value to get
	 * @return The named value wrapped in an {@link INamedValue} instance
	 */
	public INamedValue getNamedValue(int valueOrdinal);

	/**
	 * Get the numeric index for the current selected named value
	 * 
	 * @return The numeric index of the selected value
	 * @throws TargetBindingException On error
	 */
	public int getSelectedOrdinal();

	/**
	 * Get a copy of the current selected value
	 * 
	 * @return The selected value wrapped in an {@link INamedValue} instance
	 */
	public INamedValue getSelectedValue();
}