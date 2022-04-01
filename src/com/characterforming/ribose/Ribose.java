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
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ModelException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.engine.RuntimeModel;
import com.characterforming.jrte.engine.RuntimeModel.Mode;

/**
 * 
 * Main ribose component factory provides static methods for compiling, loading 
 * and running ribose transductions.
 * 
 * @author Kim Briggs
 *
 */
public final class Ribose {
	private final static Logger rtcLogger = Logger.getLogger(Base.RTC_LOGGER_NAME);

	public static void compileRiboseRuntime(Class<?> targetClass, File ginrAutomataDirectory, File riboseRuntimeFile) throws ModelException {
		RuntimeModel model = null;
		try {
			model = new RuntimeModel(Mode.compile, riboseRuntimeFile, (ITarget)targetClass.getDeclaredConstructor().newInstance());
			model.compile(ginrAutomataDirectory);
		} catch (ModelException e) {
			String msg = String.format("ModelException compiling model '%1$s' from source directory '%2$s'",
				riboseRuntimeFile.getPath(), ginrAutomataDirectory.getPath());
			Ribose.rtcLogger.log(Level.SEVERE, msg, e);
			throw e;
		} catch (Exception e) {
			String msg = String.format("Exception compiling model '%1$s' from source directory '%2$s'",
					riboseRuntimeFile.getPath(), ginrAutomataDirectory.getPath());
				Ribose.rtcLogger.log(Level.SEVERE, msg, e);
				ModelException m = new ModelException(msg, e);
				throw m;
		} finally {
			if (model != null) {
				model.close();
			}
		}
	}

	public static IRiboseRuntime loadRiboseRuntime(File riboseRuntimeFile, ITarget target) throws ModelException {
		try {
			return new RiboseRuntime(riboseRuntimeFile, target);
		} catch (ModelException e) {
			String msg = String.format("ModelException loading model '%1$s'", riboseRuntimeFile.getPath());
			RiboseRuntime.rteLogger.log(Level.SEVERE, msg, e);
			throw e;
		}
	}
	
	public static void main(final String[] args) throws SecurityException, IOException {
		File ginrAutomataDirectory = null;
		File riboseRuntimeFile = null;
		String targetClassname = null;
		Class<?> targetClass = null;

		boolean argsOk = (args.length == 4) && args[0].equals("--target");
		if (argsOk) {
			targetClassname = args[1];
			ginrAutomataDirectory = new File(args[2]);
			riboseRuntimeFile = new File(args[3]);
		}
		
		if (argsOk) {
			try {
				targetClass = Class.forName(targetClassname);
			} catch (Exception e) {
				Ribose.rtcLogger.log(Level.SEVERE, String.format("target-class '%1$s' could not be instantiated as model target", targetClassname), e);
				argsOk = false;
			}
			if (!ginrAutomataDirectory.isDirectory()) {
				Ribose.rtcLogger.log(Level.SEVERE, String.format("ginr-output-dir '%1$s' is not a directory", ginrAutomataDirectory.getPath()));
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

		int exitCode = 0;
		System.out.println(String.format("Ribose runtime compiler version %1$s%2$sCopyright (C) 2011,2022 Kim Briggs%2$sDistributed under GPLv3 (http://www.gnu.org/licenses/gpl-3.0.txt)", Base.RTE_VERSION, System.getProperty("line.separator")));
		System.out.println(String.format("Compiling %1$s to runtime file %2$s", ginrAutomataDirectory.getPath(), riboseRuntimeFile.getPath()));
		try {
			Ribose.compileRiboseRuntime(targetClass, ginrAutomataDirectory, riboseRuntimeFile);
		} catch (Exception e) {
			System.out.println("Runtime compilation failed, see log for details.");
			exitCode = 1;
		}
		System.exit(exitCode);
	}	
}
