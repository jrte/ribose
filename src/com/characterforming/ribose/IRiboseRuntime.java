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

package com.characterforming.ribose;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.ModelException;
import com.characterforming.jrte.RteException;

/**
 * Runtime transduction factory.
 * 
 * @author Kim Briggs
 *
 */
public interface IRiboseRuntime {
	
	/** 
	 * Construct an IInput object to feed into a transduciton.
	 * 
	 * @param bytesInput An array of arrays of bytes to be transduced sequentially.
	 * @return An IInput object
	 */
	public IInput input(byte[][] bytesInput);
	
	/**
	 * Instantiate a new transduction stack. 
	 * 
	 * @param target The target instance to bind to the transduciton.
	 * @return A new transduction
	 * @throws ModelException
	 * @throws RteException
	 */
	public ITransduction newTransduction(ITarget target) throws ModelException, RteException;
	
	/**
	 * Close the runtime model and file.
	 * 
	 * @throws ModelException
	 */
	public void close() throws ModelException;
}
