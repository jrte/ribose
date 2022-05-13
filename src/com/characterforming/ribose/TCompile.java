package com.characterforming.ribose;

import java.io.File;
import java.util.logging.Level;

import com.characterforming.jrte.engine.Model;
import com.characterforming.jrte.engine.ModelCompiler;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * The ribose model compiler is a transducer of ginr DFAs as represented in {@code dfamin:save}
 * format in {@code *.dfa} files. As such it is contained in a compiler model, for which this
 * class is the model target class.
 * <br>
 * The default constructor {@link TCompile()} is used only when building a new compiler model. In 
 * that context is serves as model target only to present its effectors and compile effector
 * paramters. The {@link TCompile(Model)} constructor instantiates a runtime instance of the
 * model compiler to compile a ribose model for domain-specifi0 target and related transducers.
 * <br>
 * Main method runs the ribose runtime compiler to build a runtime model for a target 
 * class from a collection of ginr automata generated from ribose patterns.
 * <br><br>
 * <b>TCompile Usage:</b> <pre class="code">java -cp ribose.0.0.0.jar com.characterforming.ribose.Tcompile &lt;target&gt; &lt;automata&gt; &lt;model&gt;</pre>
 * <br>
 * <table>
 * <caption style="text-align:left"><b>TCompile command-line arguments</b></caption>
 * <tr><td><i>target</i></td><td>Fully qualified name of the model target class.</tr>
 * <tr><td><i>automata</i></td><td>The path to the directory containing automata to include in the model.</tr>
 * <tr><td><i>model</i></td><td>The path to the file to contain the model.</tr>
 * </table>
 */
public final class TCompile extends ModelCompiler implements ITarget {
	/**
	 * Constructor (as model target for compilation of TCompile.model)
	 */
	public TCompile() {
		super();
	}
	
	/**
	 * Constructor (as runtime target to build from Automaton.dfa and persist to TCompile.model)
	 * 
	 * @param targetModel the TCompile model instance to be constructed and persisted
	 */
	public TCompile(Model targetModel) {
		super(targetModel);
	}
	
	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.ribose.ITarget#bindeEffectors()
	 */
	@Override
	public IEffector<?>[] bindEffectors() throws TargetBindingException {
		return super.bindEffectors();
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.ribose.ITarget#getName()
	 */
	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * Runs the ribose runtime compiler to build a runtime model for a target 
	 * class from a collection of ginr automata generated from ribose patterns.
	 * @param args &lt;target-classname&gt; &lt;automata-directory-path&gt; &lt;runtime-model-path&gt;
	 */
	public static void main(final String[] args) {
		File ginrAutomataDirectory = null;
		File riboseModelFile = null;
		String targetClassname = null;
		Class<?> targetClass = null;
	
		boolean argsOk = (args.length == 4) && args[0].equals("--target");
		if (argsOk) {
			targetClassname = args[1];
			ginrAutomataDirectory = new File(args[2]);
			riboseModelFile = new File(args[3]);
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
			if (riboseModelFile.isDirectory()) {
				Ribose.rtcLogger.log(Level.SEVERE, String.format("model-path '%1$s' is a directory", riboseModelFile.getPath()));
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
		System.out.println(String.format("Compiling %1$s to runtime file %2$s", ginrAutomataDirectory.getPath(), riboseModelFile.getPath()));
		
		int exitCode = 0;
		try {
			if (!Ribose.compileRiboseModel(targetClass, ginrAutomataDirectory, riboseModelFile)) {
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
