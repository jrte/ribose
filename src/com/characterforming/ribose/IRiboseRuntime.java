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
public interface IRiboseRuntime {
	
	public IRiboseRuntime load(File jrteGearboxFile, String targetClassname);
	
	public IRiboseRuntime bind(ITarget target);
	
	public void close();
}
