/**
 * 
 */
package com.characterforming.ribose;

import java.io.File;

import com.characterforming.jrte.ITarget;

/**
 * @author rex ex ossibus meis
 *
 */
public interface IRiboseCompiler {
	
	void compileModelInstance(ITarget target, File dfaDirectory, File gearboxFile);

}
