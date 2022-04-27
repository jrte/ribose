package com.characterforming.ribose;

import java.io.File;
import java.util.logging.Level;

import com.characterforming.ribose.base.Base;

/**
 * Runs the ribose runtime compiler to build a runtime model for a target 
 * class from a collection of ginr automata generated from ribose patterns.
 * <p/>
 * Usage: <pre class="code">java -cp Jrte.jar com.characterforming.ribose.Tcompile &lt;target&gt; &lt;automata&gt; &lt;model&gt;</pre>
 * <table>
 * <tr><td align="right"><i>target</i></td><td>Fully qualified name of the model target class.</tr>
 * <tr><td align="right"><i>automata</i></td><td>The path to the directory containing automata to include in the model.</tr>
 * <tr><td align="right"><i>model</i></td><td>The path to the file to contain the model.</tr>
 * </table>
 */
public final class TCompile {
	/**
	 * Runs the ribose runtime compiler to build a runtime model for a target 
	 * class from a collection of ginr automata generated from ribose patterns.
	 * @param args &lt;target-classname&gt; &lt;automata-directory-path&gt; &lt;runtime-model-path&gt;
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