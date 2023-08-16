/***
 * Ribose is a recursive transduction engine for Java
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
 * You should have received a copy of the GNU General Public License
 * along with this program (LICENSE-gpl-3.0). If not, see
 * <http://www.gnu.org/licenses/#GPL>.
 */

 package com.characterforming.ribose;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Base;
import com.characterforming.jrte.engine.Model;
import com.characterforming.jrte.engine.ModelCompiler;

/**
 * Provides a {@link TCompile#main(String[])} method to transduce ginr DFAs to ribose transducers
 * and package them in a ribose runtime model along with a target class and associated effectors.
 * The main method runs the ribose runtime compiler to build a runtime model and model map
 * for a target class from a collection of ginr automata generated from ribose patterns. The
 * default {@link com.characterforming.ribose.base.BaseTarget} class will be used as target if
 * {@code --target} and {@code --target-path} are not specified. In any case, a proxy 
 * instance of the model target class will be instantiated, using its default constructor,
 * to precompile effector parameters. See the {@link ITarget} documentation for details
 * regarding this process. The compiled model contains compiled transducers, signals, 
 * fields, effectors and effector parameters. The model map is a text file listing the 
 * names and ordinal enumerators for these artifacts. {@link TDecompile} can be used in
 * conjunction with the model map to assist with debugging transducers in the ribose runtime.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TCompile usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TCompile --target <i>classname automata model</i></td></tr>
 * <tr><td style="text-align:right">--target <i>classname</i></td><td>Fully qualified name of the model target class.</td></tr>
 * <tr><td style="text-align:right">--target-path <i>paths:to:jars</i></td><td>Classpath containing jars for target class and dependencies.</td></tr>
 * <tr><td style="text-align:right"><i>automata</i></td><td>The path to the directory containing automata (*.dfa) to include in the model.</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the file to contain the compiled model.</td></tr>
 * </table>
 * <br><br>
 * The compiler model and map files ({@code TCompile.model, TCompile.map}) must be rebuilt when
 * new effectors are added to the {@code Transductor} class. Otherwise, the compiler model is not
 * affected by changes to the effector sets of other, domain-specific target classes. New
 * {@code Transductor} effectors must be appended to the existing enumeration so that models not
 * yet updated can be supported in the updated runtime. To do this increment the ribose version
 * string {@code Base.RTE_VERSION} and set {@code Base.RTE_PREVIOUS} to the previous 
 * {@code Base.RTE_VERSION} string. Then modify {@code Transductor.getEffectors()} to include 
 * the new effectors for {@code Base.RTE_VERSION} and the previous version effectors for 
 * {@code Base.RTE_PREVIOUS}. Run the compiler compiler to update {@code TCompile.model, TCompile.map}
 * and you're done. Other existing ribose models built by the previous version will still run with
 * the new transductor effectors included but must be recompiled to update to the new model version.
 */
public final class TCompile extends ModelCompiler {
	/**
	 * Constructor (as proxy target for compilation of TCompile.model)
	 */
	public TCompile() {
		super();
	}
	
	/**
	 * Constructor (as runtime target for compilation of ribose models)
	 * 
	 * @param targetModel the TCompile model instance to be constructed and persisted
	 */
	public TCompile(Model targetModel) {
		super(targetModel);
	}

	/**
	 * Runs the ribose model compiler to refresh the model compiler model.
	 * 
	 * @param args <i>target-classname automata-directory-path runtime-model-path</i>
	 * @throws SecurityException If logging subsystem can't initialize
	 */
	public static void main(final String[] args) throws SecurityException {
		File ginrAutomataDirectory = null;
		File riboseModelFile = null;
		Class<?> targetClass = null;
		
		Base.startLogging();
		final Logger rtcLogger = Base.getCompileLogger();
		boolean argsOk = (args.length == 4) && args[0].equals("--target");
		if (argsOk) {
			final String targetClassname = args[1];
			ginrAutomataDirectory = new File(args[2]);
			riboseModelFile = new File(args[3]);
			try {
				targetClass = Class.forName(targetClassname);
			} catch (Exception e) {
				rtcLogger.log(Level.SEVERE, e, () -> String.format("target '%1$s' could not be instantiated as model target", targetClassname));
				argsOk = false;
			}
			if (!ginrAutomataDirectory.isDirectory()) {
				final String directoryPath = ginrAutomataDirectory.getPath();
				rtcLogger.log(Level.SEVERE, () -> String.format("ginr-automata-dir '%1$s' is not a directory", directoryPath));
				argsOk = false;
			}
			if (riboseModelFile.isDirectory()) {
				final String riboseModelPath = riboseModelFile.getPath();
				rtcLogger.log(Level.SEVERE, () -> String.format("model-path '%1$s' is a directory", riboseModelPath));
				argsOk = false;
			}
		}
	
		if (!argsOk) {
			System.err.println();
			System.err.println("Usage: java [jvm-options] com.characterforming.ribose.TCompile --target <classname> --target-path <classpath> <automata-path> <model-path>");
			System.err.println("   --target         -- fully qualified <classname> of the target class (implements ITarget)");
			System.err.println("   --target-path    -- <classpath> for jars containing target class and dependencies");
			System.err.println("   automata-path    -- path to directory containing transducer automata compiled by ginr");
			System.err.println("   model-path       -- path for output model file");
			System.err.println("The target class must have a default constructor and be included in the classpath.");
			System.err.println();
			System.exit(1);
		}
		System.err.println(String.format("Ribose runtime compiler version %1$s%nCopyright (C) 2011,2022 Kim Briggs%nDistributed under GPLv3 (http://www.gnu.org/licenses/gpl-3.0.txt)", 
			Base.RTE_VERSION));
		System.err.println(String.format("Compiling %1$s to runtime model %2$s", 
			ginrAutomataDirectory.getPath(), riboseModelFile.getPath()));
		
		int exitCode = 1;
		try {
			if (Ribose.compileRiboseModel(targetClass, ginrAutomataDirectory, riboseModelFile)) {
				exitCode = 0;
			}
		} catch (Exception e) {
			rtcLogger.log(Level.SEVERE, "Compiler failed", e);
			System.err.println("Compiler failed, see log for details.");
		} finally {
			if (exitCode != 0) {
				System.err.println("Runtime compilation failed, see log for details.");
			}
			Base.endLogging();
		}
		System.exit(exitCode);
	}
}
