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

/**
 * Base {@link ITarget} implementation class exports no effectors. It serves only 
 * as a public proxy for the core transduction target that provides the built-in 
 * effectors. It can be subclassed by specialized targets that implement 
 * {@link ITarget} to define additional effectors.
 * 
 * Specialized targets present their effectors by overriding the 
 * {@link #bindEffectors()} method, and these may serve as subclasses for 
 * additional extensions. Each subclass override must call super.bind() and
 * include the superclass effectors as predecessors of its own in the returned
 * list. In that context you should consider using a List&lt;IEffector&lt;?&gt;&gt; to 
 * accumulate subclass effectors and call List&lt;..&gt;.toArray() to obtain 
 * top-level bindings to return.
 * 
 * NOTE: BaseTarget presents no effectors because the core transduction effectors 
 * are implmented inline in Transduction class, which binds them to itself before
 * calling domain target bind() to bind itself to target effectors.
 * 
 * @author kb
 */
public class BaseTarget implements ITarget {
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
