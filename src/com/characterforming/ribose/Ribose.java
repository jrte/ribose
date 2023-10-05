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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Base;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.SimpleTarget;

/**
 * Provides a {@link Ribose#main(String[])} method to support compiling, running
 * and decompiling ribose models and transducers. This can be accessed by
 * running java directly from the shell or using the <i>ribose</i> shell
 * script included in the ribose root directory. Typing {@code 
 * ./ribose help <command>}, where {@code command} is one of {@code 
 * compile|run|decompile}, will display the parameter options for 
 * java and the ribose script.
 * <br>
 * <br>
 * Ribose recognizes four commands:
 * <br>
 * <ul>
 * <li><i>help:</i> help for ribose commands
 * <li><i>compile:</i> compile a ribose model from a collection of ginr automata
 * <li><i>run:</i> execute a streaming transduction)
 * <li><i>decompile:</i> decompile a transducer
 * </ul>
 * The {@code --target-path} argument need not be specified if the model target
 * is {@link SimpleTarget}. Default input and output are {@code System.in} and
 * {@code System.out}.
 * <br><br>
 * -: <b>./ribose help compile</b>
 * <pre>
 * Use: java [&lt;jvm-args&gt;] com.characterforming.ribose.Ribose compile [--target &lt;classname&gt;] &lt;automata-path&gt; &lt;model-path&gt;
 *  or: ribose [&lt;jvm-args&gt;] compile [--target-path &lt;path&gt;] [--target &lt;classname&gt;] &lt;automata-path&gt; &lt;model-path&gt;
 *   --target-path &lt;path&gt; -- model target classpath (or -cp &lt;path&gt; in &lt;jvm-args&gt;)
 *   --target &lt;classname&gt; -- fully qualified &lt;classname&gt; of the target class (implements ITarget)
 *          automata-path -- path to directory containing transducer automata compiled by ginr
 *             model-path -- path for output model file
 * The target classpath must be specified (excepting SimpleTarget).
 * The target class must have a default constructor.</pre>
 * -: <b>./ribose help run</b>
 * <pre>
 * Use: java [&lt;jvm-args&gt;] com.characterforming.ribose.Ribose run [--nil] model transducer input [output]
 *  or: ribose [&lt;jvm-args&gt;] run [--target-path &lt;path&gt;] [--nil] model transducer input [output]
 *   --target-path &lt;path&gt; -- model target classpath (or -cp &lt;path&gt; in &lt;jvm-args&gt;)
 *                  --nil -- push a &lt;i&gt;nil&lt;/i&gt; signal to start the transduction (recommended)
 *                  model -- path to model file
 *             transducer -- name of transducer to run
 *                  input -- path to input file (or - to read System.in)
 *                 output -- path to UTF-8 output file (optional, default is System.out)
 * The target classpath must be specified (excepting SimpleTarget).
 * 
 * Default buffer sizes are 8196 bytes for output and 65536 for input. These
 * can be overridden in the jvm options. Specify '-Dribose.outbuffer.size=N'
 * and/or '-Dribose.inbuffer.size=N' for a buffer size of N bytes.</pre>
 * -: <b>./ribose help decompile</b>
 * <pre>
 * Use: java [&lt;jvm-args&gt;] com.characterforming.ribose.Ribose decompile &lt;model&gt; &lt;transducer&gt;
 *  or: ribose [&lt;jvm-args&gt;] decompile [--target-path &lt;path&gt;] &lt;model&gt; &lt;transducer&gt;
 *   --target-path &lt;path&gt; -- model target classpath (or -cp &lt;path&gt; in &lt;jvm-args&gt;)
 *                  model -- path to model file
 *             transducer -- name of transducer to decompile
 * The target classpath must be specified (excepting SimpleTarget).</pre>
 */
public final class Ribose {
	private static final String[] EMPTY = new String[] {};
	private static final String VERSION = "Ribose runtime compiler version %1$s%nCopyright (C) 2011,2022 Kim Briggs%nDistributed under GPLv3 (http://www.gnu.org/licenses/gpl-3.0.txt)";

	private enum Command {
		NULL(""),
		HELP("help"),
		COMPILE("compile"),
		RUN("run"),
		DECOMPILE("decompile"),
		VERSION("version"),
		MAP("map");

		private String name;

		Command(String name) {
			this.name = name;
		}

		boolean is(String token) {
			return this.name.equals(token);
		}
	}

	private static Command which(String token) {
		for (Command c : Command.values())
			if (c.is(token))
				return c;
		return Command.NULL;
	}

	private static boolean help(Command command) {
		switch(command) {
		case NULL:
		case HELP:
		case VERSION:
			System.out.println(
				"Use: java [<jvm-args>] com.characterforming.ribose.Ribose version | help [compile | run | decompile]");
			break;
		case COMPILE:
			Ribose.execCompile(EMPTY);
			break;
		case RUN:
			Ribose.execRun(EMPTY);
			break;
		case DECOMPILE:
			Ribose.execDecompile(EMPTY);
			break;
		case MAP:
			Ribose.execMap(EMPTY);
			break;
		}
		return true;
	}

	/**
	 * Main method for invoking ribose compiler, runtime and decompiler. 
	 * 
	 * @param args arguments from the shell
	 * @throws SecurityException if unable to start the logging susystem
	 */
	public static void main(final String[] args) {
		boolean fail = true;
		if (args.length > 0) {
			Command command = Ribose.which(args[0]);
			if (command == Command.NULL || command == Command.HELP) {
				fail = !Ribose.help(args.length == 1 ? Command.HELP : Ribose.which(args[1]));
			} else if (command == Command.VERSION) {
				System.out.println(String.format(VERSION, Base.RTE_VERSION));
				fail = false;
			} else {
				String[] cargs = Arrays.copyOfRange(args, 1, args.length);
				if (command == Command.COMPILE) {
					fail = !Ribose.execCompile(cargs);
				} else if (command == Command.RUN) {
					fail = !Ribose.execRun(cargs);
				} else if (command == Command.DECOMPILE) {
					fail = !Ribose.execDecompile(cargs);
				} else if (command == Command.MAP) {
					fail = !Ribose.execMap(cargs);
				}
			}
		} else {
			fail = !Ribose.help(Command.HELP);
		}
		System.exit(fail ? 1 : 0);
	}

	private static boolean execCompile(final String[] args) throws SecurityException {
		Base.startLogging();
		File ginrAutomataDirectory = null;
		File riboseModelFile = null;
		String riboseModelPath = null;
		String targetClassname = null;
		Logger rtcLogger = Base.getCompileLogger();

		int arg = 0;
		if ((args.length == 4) && args[0].equals("--target")) {
			targetClassname = args[1];
			arg = 2;
		}
		boolean argsOk = (args.length - arg) == 2;
		if (argsOk) {
			ginrAutomataDirectory = new File(args[arg++]);
			if (!ginrAutomataDirectory.isDirectory()) {
				final String directoryPath = ginrAutomataDirectory.getPath();
				rtcLogger.log(Level.SEVERE, () -> String.format("Automata path '%1$s' is not a directory",
					directoryPath));
				argsOk = false;
			}
			riboseModelFile = new File(args[arg]);
			riboseModelPath = riboseModelFile.getPath();
			if (riboseModelFile.isDirectory()) {
				final String modelPath = riboseModelPath;
				rtcLogger.log(Level.SEVERE, () -> String.format("Model path '%1$s' is a directory, a file path is required",
					modelPath));
				argsOk = false;
			} else if (riboseModelFile.exists()) {
				argsOk &= riboseModelFile.delete();
			}
		}
		
		if (!argsOk) {
			System.out.println();
			System.out.println(
				"Use: java [<jvm-args>] com.characterforming.ribose.Ribose compile [--target-path <classpath> --target <classname>] <automata-path> <model-path>");
			System.out.println(
				" or: ribose [<jvm-args>] compile [--target-path <classpath> --target <classname>] <automata-path> <model-path>");
			System.out.println("  --target-path <classpath> -- model target classpath");
			System.out.println("       --target <classname> -- fully qualified <classname> of the target class (implements ITarget)");
			System.out.println("              automata-path -- path to directory containing transducer automata compiled by ginr");
			System.out.println("                 model-path -- path for output model file");
			System.out.println("The target class path and name must be specified unless target class is SimpleTarget.");
			System.out.println("The target class must have a default constructor for effector parameter compilation.");
			System.out.println("The model map will be written to System.out; redirect to persistent file if required.");
			System.out.println();
			return args.length == 0;
		}
				
		boolean compiled = false;
		if (argsOk) try {
			compiled = IModel.compileRiboseModel(targetClassname, ginrAutomataDirectory, riboseModelFile);
		} catch (Exception e) {
			final String modelPath = riboseModelPath;
			rtcLogger.log(Level.SEVERE, e, () -> String.format("Model compilation failed for '%1$s'",
				modelPath));
		}
		Base.endLogging();
		if (argsOk && !compiled) {
			System.out.println("Runtime compilation failed, see log for details.");
		}
		return compiled;
	}

	private static boolean execRun(final String[] args) throws SecurityException {
		int argc = args.length;
		boolean nil = argc > 0 && args[0].compareTo("--nil") == 0;
		int arg = nil ? 1 : 0;
		arg += (arg > (nil ? 1 : 0)) ? 1 : 0;
		boolean argsOk = ((argc - arg) == 3) || ((argc - arg) == 4);
		if (!argsOk) {
			System.out.println();
			System.out.println(
				"Use: java [<jvm-args>] com.characterforming.ribose.Ribose run [--nil] model transducer input [output]");
			System.out.println(
				" or: ribose [<jvm-args>] run [--target-path <classpath>] [--nil] model transducer input [output]");
			System.out.println("  --target-path <classpath> -- model target classpath");
			System.out.println("                      --nil -- push a <i>nil</i> signal to start the transduction (recommended)");
			System.out.println("                      model -- path to model file");
			System.out.println("                 transducer -- name of transducer to run");
			System.out.println("                      input -- path to input file (or - to read System.in)");
			System.out.println("                     output -- path to UTF-8 output file (optional, default is System.out)");
			System.out.println("The target class path can be omitted if the target class is SimpleTarget.");
			System.out.printf("Default buffer sizes are %1$d bytes for output and %2$d for input. These%n",
				Base.getOutBufferSize(), Base.getInBufferSize());
			System.out.println("can be overridden in the jvm options. Specify '-Dribose.outbuffer.size=N'");
			System.out.println("and/or '-Dribose.inbuffer.size=N' for a buffer size of N bytes.");
			System.out.println();
			return args.length == 0;
		}

		Base.startLogging();
		final String modelPath = args[arg++];
		final String transducerName = args[arg++];
		final String inputPath = arg < argc ? args[arg++] : "-";
		final String output = arg < argc ? args[arg++] : null;
		final Logger rteLogger = Base.getRuntimeLogger();
		final CharsetEncoder encoder = Base.newCharsetEncoder();
		final File input = inputPath.charAt(0) == '-' ? null : new File(inputPath);
		boolean run = true;
		if (input != null && !input.exists()) {
			rteLogger.log(Level.SEVERE, "No input file found at {0}", inputPath);
			run = false;
		}
		final File model = new File(modelPath);
		if (!model.exists()) {
			rteLogger.log(Level.SEVERE, "No ribose model file found at {0}", modelPath);
			run = false;
		}

		if (run) try (
			IModel ribose = IModel.loadRiboseModel(model);
			InputStream streamIn = input == null ? System.in : new FileInputStream(input);
			OutputStream streamOut = new BufferedOutputStream(
				output == null ? System.out : new FileOutputStream(new File(output)), Base.getOutBufferSize()
			);
		) {
			run = ribose.stream(
				Bytes.encode(encoder, transducerName),
				nil ? Signal.NIL : Signal.NONE, streamIn, streamOut
			);
			streamOut.flush();
		} catch (final IOException | ModelException | RiboseException e) {
			rteLogger.log(Level.SEVERE, "Runtime failed", e);
		}
		Base.endLogging();
		if (!run) {
			System.out.println("Runtime failed, see log for details.");
		}
		return run;
	}

	private static boolean execDecompile(final String[] args) {
		if (args.length != 2) {
			System.out.println();
			System.out.println(
				"Use: java [<jvm-args>] com.characterforming.ribose.Ribose decompile <model> <transducer>");
			System.out.println(
				" or: ribose [<jvm-args>] decompile [--target-path <classpath>] <model> <transducer>");
			System.out.println("  --target-path <classpath> -- model target classpath");
			System.out.println("                      model -- path to model file");
			System.out.println("                 transducer -- name of transducer to decompile");
			System.out.println("The target class path can be omitted if the target class is SimpleTarget.");
			System.out.println("Ouput is written to System.out and may be redirected in the shell.");
			System.out.println();
			return args.length == 0;
		}
		final String transducerName = args[1];
		final File modelFile = new File(args[0]);

		Base.startLogging();
		Logger rteLogger = Base.getRuntimeLogger();
		boolean decompiled = false;
		try (IModel model = IModel.loadRiboseModel(modelFile)) {
			model.decompile(transducerName);
			decompiled = true;
		} catch (ModelException e) {
			final String format = "Failed to decompile %1$s";
			rteLogger.log(Level.SEVERE, e, () -> String.format(format,
				transducerName));
		} finally {
			Base.endLogging();
		}
		if (!decompiled) {
			System.out.println("Decompilation failed, see log for details.");
		}
		return decompiled;
	}

	private static boolean execMap(final String[] args) {
		if (args.length != 1) {
			System.out.println();
			System.out.println(
					"Use: java [<jvm-args>] com.characterforming.ribose.Ribose map <model>");
			System.out.println(
					" or: ribose [<jvm-args>] map [--target-path <classpath>] <model>");
			System.out.println("  --target-path <classpath> -- model target classpath");
			System.out.println("                      model -- path to model file");
			System.out.println("The target class path can be omitted if the target class is SimpleTarget.");
			System.out.println("Map is written to System.out and may be redirected in the shell.");
			System.out.println();
			return args.length == 0;
		}

		final File modelFile = new File(args[0]);

		Base.startLogging();
		Logger rteLogger = Base.getRuntimeLogger();
		boolean mapped = false;
		try (IModel model = IModel.loadRiboseModel(modelFile)) {
			mapped = model.map(System.out);
		} catch (ModelException e) {
			final String format = "Failed to decompile %1$s";
			rteLogger.log(Level.SEVERE, e, () -> String.format(
				format,	modelFile.getAbsolutePath()));
		} finally {
			Base.endLogging();
		}
		if (!mapped) {
			System.out.println("Map writer failed, see log for details.");
		}
		return mapped;
	}
}
