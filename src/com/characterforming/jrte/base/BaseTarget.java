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

package com.characterforming.jrte.base;

import com.characterforming.jrte.IEffector;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.engine.Transduction;

/**
 * Base {@link ITarget} implementation class exports no effectors. It serves only 
 * as a public proxy for the inline transduction effectors paste/cut/copy etc. 
 * 
 * This class is used as target in {@link com.characterforming.ribose.Ribose#main(String[])}
 * and can be used as target for collections of utf8- or byte-oriented transducers
 * that use only the inline transduction effectors.  
 * 
 * @author Kim Briggs
 * @see {@link Transduction#bindEffectors()}
 */
public final class BaseTarget implements ITarget {
	public BaseTarget() {
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#bindEffectors(int)
	 */
	@Override
	public IEffector<?>[] bindEffectors() throws TargetBindingException {
		return new IEffector<?>[] { };
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#getName()
	 */
	@Override
	public final String getName() {
		return this.getClass().getSimpleName();
	}
}
