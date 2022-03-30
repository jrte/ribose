/**
 * 
 */
package com.characterforming.ribose;

import java.io.File;

import com.characterforming.jrte.ITarget;

/**
 * @author Kim Briggs
 *
 */
public interface IRiboseCompiler {
	
	void compileModelInstance(Class<ITarget> targetClass, File dfaDirectory, File gearboxFile);
}
