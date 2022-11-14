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
 * LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.ribose.base;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.ITarget;

/**
 * This class is used as target for collections of utf8- or byte-oriented
 * transducers that use only the inline transductor effectors. These effectors
 * are available to all transductions and are enumerated by the transductor
 * itself. Subclasses that express specialized effectors must override 
 * {@code getEffectors()} to present them to transductors.
 * 
 * @author Kim Briggs
 */
public class BaseTarget implements ITarget {
	public BaseTarget() {
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITarget#getEffectors()
	 */
	@Override
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		return new IEffector<?>[] { };
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITarget#getName()
	 */
	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}
}
