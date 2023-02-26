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
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Model;
import com.characterforming.jrte.engine.ModelCompiler;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Provides a {@link TCompile#main(String[])} method to transduce ginr DFAs to ribose transducers
 * and package them in a ribose runtime model along with a target class and associated effectors.
 * {@code TCompile} also serves as target class for the ribose compiler model {@code TCompile.model}
 * containing transducers used by the compiler. It inherits three specialized effectors from its
 * base {@code com.characterforming.jrte.engine.ModelCompiler} class.
 * <br><br>
 * The default constructor {@link TCompile()} is used only when building a new compiler model
 * and when loading the compiler model for runtime use by the compilers. In those contexts it
 * serves only as a proxy target to present its effectors and compile effector parameters. The
 * {@link TCompile(Model)} constructor instantiates a runtime instance of the model compiler
 * to serve as a live target during compilation of other ribose models, pulling precompiled 
 * effector parameters from the praxy target in the model.
 * <br><br>
 * The main method runs the ribose runtime compiler to build a runtime model for a target 
 * class from a collection of ginr automata generated from ribose patterns.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TCompile usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TCompile --target <i>classname automata model</i></td></tr>
 * <tr><td style="text-align:right">--target <i>classname</i></td><td>Fully qualified name of the model target class.</td></tr>
 * <tr><td style="text-align:right"><i>automata</i></td><td>The path to the directory containing automata (*.dfa) to include in the model.</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the file to contain the compiled model.</td></tr>
 * </table>
 * <br><br>
 * The compiler model and map files ({@code TCompile.model, TCompile.map}) must be rebuilt when
 * new effectors are added to the {@code Transductor} class. New effectors must be appended to 
 * the existing enumeration so that models not yet updated can be supported in the updated runtime.
 * To do this increment the ribose version string {@code Base.RTE_VERSION} and set {@code Base.RTE_PREVIOUS}
 * to the previous {@code Base.RTE_VERSION} string. Then modify {@code Transductor.getEffectors()}
 * to include the new effectors for {@code Base.RTE_VERSION} and the previous version effectors for 
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
	 * Constructor (as runtime target for compilation of other ribose models)
	 * 
	 * @param targetModel the TCompile model instance to be constructed and persisted
	 */
	public TCompile(Model targetModel) {
		super(targetModel);
	}
	
	@Override // @see com.characterforming.ribose.ITarget#bindeEffectors()
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		return super.getEffectors();
	}

	@Override // com.characterforming.ribose.ITarget#getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * Runs the ribose runtime compiler to build a runtime model for a target 
	 * class from a collection of ginr automata generated from ribose patterns.
	 * 
	 * @param args <i>target-classname automata-directory-path runtime-model-path</i>
	 * @throws IOException If logging subsystem can't initialize
	 * @throws SecurityException If logging subsystem can't initialize
	 */
	public static void main(final String[] args) throws SecurityException, IOException {
		File ginrAutomataDirectory = null;
		File riboseModelFile = null;
		String targetClassname = null;
		Class<?> targetClass = null;
		
		final Logger rtcLogger = Base.getCompileLogger();
		boolean argsOk = (args.length == 4) && args[0].equals("--target");
		if (argsOk) {
			targetClassname = args[1];
			ginrAutomataDirectory = new File(args[2]);
			riboseModelFile = new File(args[3]);
			try {
				targetClass = Class.forName(targetClassname);
			} catch (Exception e) {
				rtcLogger.log(Level.SEVERE, String.format("target-class '%1$s' could not be instantiated as model target", targetClassname), e);
				argsOk = false;
			}
			if (!ginrAutomataDirectory.isDirectory()) {
				rtcLogger.log(Level.SEVERE, String.format("ginr-automata-dir '%1$s' is not a directory", ginrAutomataDirectory.getPath()));
				argsOk = false;
			}
			if (riboseModelFile.isDirectory()) {
				rtcLogger.log(Level.SEVERE, String.format("model-path '%1$s' is a directory", riboseModelFile.getPath()));
				argsOk = false;
			}
		}
	
		if (!argsOk) {
			System.out.println();
			System.out.println("Usage: java [jvm-options] com.characterforming.ribose.TCompile --target <target-class> <ginr-output-dir> <model-path>");
			System.out.println("   target-class     -- fully qualified name of class implementing ITarget");
			System.out.println("   ginr-output-dir  -- path to directory containing transducer automata compiled by ginr");
			System.out.println("   model-path       -- path for output model file");
			System.out.println("The <target-class> container must be included in the classpath.");
			System.out.println();
			System.exit(1);
		}
		System.out.println(String.format("Ribose runtime compiler version %1$s%2$sCopyright (C) 2011,2022 Kim Briggs%2$sDistributed under GPLv3 (http://www.gnu.org/licenses/gpl-3.0.txt)", 
			Base.RTE_VERSION, Base.lineEnd));
		System.out.println(String.format("Compiling %1$s to runtime model %2$s", 
			ginrAutomataDirectory.getPath(), riboseModelFile.getPath()));
		
		int exitCode = 0;
		try {
			if (!Ribose.compileRiboseModel(targetClass, ginrAutomataDirectory, riboseModelFile)) {
				exitCode = 1;
			}
		} catch (Exception e) {
			rtcLogger.log(Level.SEVERE, "Compiler failed", e);
			System.out.println("Compiler failed, see log for details.");
			exitCode = 1;
		} finally {
			if (exitCode != 0) {
				System.out.println("Runtime compilation failed, see log for details.");
			}
		}
		System.exit(exitCode);
	}
}
