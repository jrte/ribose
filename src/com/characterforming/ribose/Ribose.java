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

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ModelException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.engine.Model;
import com.characterforming.jrte.engine.Model.Mode;

/**
 * 
 * Main ribose component factory provides static methods for compiling and loading 
 * ribose runtime models. A main() method to drive the ribose compiler is provided. 
 * 
 * Governance:
 * 
 * {@code main | compileRiboseRuntime | loadRiboseRuntime}
 * 
 * @author Kim Briggs
 *
 */
public final class Ribose {
	private final static Logger rtcLogger = Logger.getLogger(Base.RTC_LOGGER_NAME);
	
/**
 * Compile a collection of DFAs from an automata directory into a ribose model file
 * and bind them to a Target class. 
 * 
 * @param targetClass the ITarget implementation class will be instantiated as model target
 * @param ginrAutomataDirectory directory containing DFAs compiled by ginr
 * @param riboseRuntimeFile path indicating where to create the model file and file name
 * @returns false if compilation fails
 * @throws ModelException
 */
	public static boolean compileRiboseRuntime(Class<?> targetClass, File ginrAutomataDirectory, File riboseRuntimeFile) {
		for (Class<?> targetImplemenation : targetClass.getInterfaces()) {
			if (targetImplemenation.toString().equals(ITarget.class.toString())) {
				Model model = null;
				try {
					model = new Model(Mode.compile, riboseRuntimeFile, (ITarget)targetClass.getDeclaredConstructor().newInstance());
					return model.compile(ginrAutomataDirectory);
				} catch (Exception e) {
					String msg = String.format("Exception compiling model '%1$s' from '%2$s'",
						riboseRuntimeFile.getPath(), ginrAutomataDirectory.getPath());
					Ribose.rtcLogger.log(Level.SEVERE, msg, e);
				} finally {
					if (model != null) {
						model.close();
					}
				}
			}
			return false;
		}
		String msg = String.format("Can't compile ribose model, %1$s does not implement ITarget", 
			targetClass.getName());
		Ribose.rtcLogger.log(Level.SEVERE, msg);
		return false;
	}

	/**
	 * Load a ribose runtime model from persistent store and bind it to a model
	 * target instance, instantiate the model target effectors and precompile 
	 * model effector parameters. The runtime model can be used to instantiate
	 * runtime transductors.
	 * 
	 * @param riboseRuntimeFile path to the runtime model to load
	 * @param target the model target instance to bind to the runtime model
	 * @return a live ribose runtime model instance
	 */
	public static IRiboseRuntime loadRiboseRuntime(File riboseRuntimeFile, ITarget target) {
		try {
			return new RiboseRuntime(riboseRuntimeFile, target);
		} catch (Exception e) {
			String msg = String.format("Exception loading model '%1$s'", riboseRuntimeFile.getPath());
			RiboseRuntime.rteLogger.log(Level.SEVERE, msg, e);
		}
		return null;
	}
	
	/**
	 * Main method runs the ribose runtime compiler.
	 * 
	 * @param args [--nil] &lt;automata-directory-path&gt; &lt;runtime-model-path&gt; &lt;target-classname&gt;
	 */
	public static void main(final String[] args) {
		File ginrAutomataDirectory = null;
		File riboseRuntimeFile = null;
		String targetClassname = null;
		Class<?> targetClass = null;

		boolean argsOk = (args.length == 4) && args[0].equals("--target");
		if (argsOk) {
			targetClassname = args[1];
			ginrAutomataDirectory = new File(args[2]);
			riboseRuntimeFile = new File(args[3]);
			try {
				targetClass = Class.forName(targetClassname);
			} catch (Exception e) {
				Ribose.rtcLogger.log(Level.SEVERE, String.format("target-class '%1$s' could not be instantiated as model target", targetClassname), e);
				argsOk = false;
			}
			if (!ginrAutomataDirectory.isDirectory()) {
				Ribose.rtcLogger.log(Level.SEVERE, String.format("ginr-automata-dir '%1$s' is not a directory", ginrAutomataDirectory.getPath()));
				argsOk = false;
			}
			if (riboseRuntimeFile.isDirectory()) {
				Ribose.rtcLogger.log(Level.SEVERE, String.format("model-path '%1$s' is a directory", riboseRuntimeFile.getPath()));
				argsOk = false;
			}
		}

		if (!argsOk) {
			System.out.println();
			System.out.println("Usage: java [jvm-options] Ribose --target <target-class> <ginr-output-dir> <model-path>");
			System.out.println("   target-class     -- fully qualified name of class implementing ITarget");
			System.out.println("   ginr-output-dir  -- path to directory containing transducer automata compiled by ginr");
			System.out.println("   model-path       -- path for output model file");
			System.out.println("The <target-class> container must be included in the classpath.");
			System.out.println();
			System.exit(1);
		}
		System.out.println(String.format("Ribose runtime compiler version %1$s%2$sCopyright (C) 2011,2022 Kim Briggs%2$sDistributed under GPLv3 (http://www.gnu.org/licenses/gpl-3.0.txt)", Base.RTE_VERSION, System.getProperty("line.separator")));
		System.out.println(String.format("Compiling %1$s to runtime file %2$s", ginrAutomataDirectory.getPath(), riboseRuntimeFile.getPath()));
		
		int exitCode = 0;
		try {
			if (!Ribose.compileRiboseRuntime(targetClass, ginrAutomataDirectory, riboseRuntimeFile)) {
				exitCode = 1;
			}
		} catch (Exception e) {
			exitCode = 1;
		} finally {
			if (exitCode != 0) {
				System.out.println("Runtime compilation failed, see log for details.");
			}
		}
		System.exit(exitCode);
	}	
}
