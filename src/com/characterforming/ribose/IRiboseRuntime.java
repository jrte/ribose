/**
 * 
 */
package com.characterforming.ribose;

import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ITransduction;

/**
 * @author Kim Briggs
 *
 */
public interface IRiboseRuntime {
	
	public ITransduction newTransduction(ITarget target);
}
