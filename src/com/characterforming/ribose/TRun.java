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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharsetEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Base;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Signal;

/**
 * Provides a {@link TRun#main(String[])} method to run a transduction using a ribose
 * model. The model target class must provide a default constructor capable of instantiating
 * both proxy and live target instances. A proxy instance of the model target class is 
 * instantiated to precompile effector parameters. A live target instance is instantiated
 * and bound to the transduction. See the {@link ITarget} documentation for details
 * regarding proxy and live targets in the ribose runtime. The {@code --target-path}
 * argument need not be specified if the model target is {@link com.characterforming.ribose.base.BaseTarget}.
 * Default output is System.out.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TRun usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose-&lt;version&gt;.jar com.characterforming.ribose.TRun [--nil] [--target <i>classname</i>] <i>model transducer input [output]</i></td></tr>
 * <tr><td style="text-align:right">--target-path <i>paths:to:jars</i></td><td>Classpath containing jars for target class and dependencies.</td></tr>
 * <tr><td style="text-align:right">--nil</td><td>Push {@link com.characterforming.ribose.base.Signal#nil} to start the transduction (recommended).</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the model file containing the transducer.</td></tr>
 * <tr><td style="text-align:right"><i>transducer</i></td><td>The name of the transducer to start the transduction.</td></tr>
 * <tr><td style="text-align:right"><i>input</i></td><td>The path to the input file.</td></tr>
 * <tr><td style="text-align:right"><i>output</i></td><td>The path to the output file.</td></tr>
 * </table>
 * <br>
 */
public final class TRun {
	/**
	 * Opens a text transduction model built with {@link TRun} as model target class
	 * and runs a transduction on an input file, optionally pushing a {@code nil}
	 * signal as prologue. The named transducer, to be pushed to begin the transduction,
	 * must be contained in the specified model file.
	 * <br><br>
	 * If no output file is soecified the {@code out[]} effector will write to System.out.
	 *
	 * @param args [--nil] <i>trun-model-path transducer-name input-file-path [output-file-path]</i>
	 * @throws IOException if unable to start the logging susystem
	 * @throws SecurityException if unable to start the logging susystem
	 */
	public static void main(final String[] args) throws SecurityException, IOException {
		int argc = args.length;
		final boolean nil = (argc > 0) ? (args[0].compareTo("--nil") == 0) : false;
		int arg = nil ? 1 : 0;
		arg += (arg > (nil ? 1 : 0)) ? 1 : 0;
		if ((argc - arg) != 3 && (argc - arg) != 4) {
			System.out.println();
			System.out.println("Usage: java [jvm-options] com.characterforming.ribose.TRun [--target-path <classpath>] [--nil] model transducer input [output]");
			System.out.println("   --target-path    -- <classpath> for jars containing model target class and dependencies");
			System.out.println("   --nil            -- push a <i>nil</i> signal to start the transduction (recommended)");
			System.out.println("   model            -- path to model file");
			System.out.println("   transducer       -- name of transducer to run");
			System.out.println("   input            -- path to input file (or - to read System.in)");
			System.out.println("   output           -- path to UTF-8 output file (optional, default is System.out)");
			System.out.println("The model target class must have a default constructor and be included in the classpath.");
			System.out.println();
			System.exit(1);
		}
		final String modelPath = args[arg++];
		final String transducerName = args[arg++];
		final String inputPath = arg < argc ? args[arg++] : "-";
		final String outputPath = arg < argc ? args[arg++] : null;
		
		Base.startLogging();
		final Logger rteLogger = Base.getRuntimeLogger();
		final Logger rtmLogger = Base.getMetricsLogger();
		final CharsetEncoder encoder = Base.newCharsetEncoder();
		final boolean outputEnabled = System.getProperty("jrte.out.enabled", "true").equals("true");
		final File input = inputPath.charAt(0) == '-' ? null : new File(inputPath);
		if (input != null && !input.exists()) {
			rteLogger.log(Level.SEVERE, "No input file found at {0}", inputPath);
			System.exit(1);
		}
		final File model = new File(modelPath);
		if (!model.exists()) {
			rteLogger.log(Level.SEVERE, "No ribose model file found at {0}", modelPath);
			System.exit(1);
		}
		OutputStream os = System.out;
		if (outputEnabled && outputPath != null) {
			File outputFile = new File(outputPath);
			if (outputFile.exists() && !outputFile.delete()) {
				rteLogger.log(Level.WARNING, "Unable to delete output file {0}", outputPath);
			}
			try {
				os = new FileOutputStream(outputFile);
			} catch (FileNotFoundException e) {
				rteLogger.log(Level.SEVERE, "No path to output file at {0}", outputPath);
				System.exit(1);
			}
		}

		int exitCode = 1;
		try (BufferedOutputStream osw = new BufferedOutputStream(os, Base.getOutBufferSize())) {
			try (
				IRuntime ribose = Ribose.loadRiboseModel(model);
				InputStream isr = input != null ? new FileInputStream(input) : System.in;
			) {
				if (ribose != null) {
					Bytes transducer = Bytes.encode(encoder, transducerName);
					ITarget runTarget = (ITarget) Class.forName(ribose.getTargetClassname()).getDeclaredConstructor().newInstance();
					long t0 = System.nanoTime();
					if (ribose.transduce(runTarget, transducer, nil ? Signal.nil : null, isr, outputEnabled ? osw : null)) {
						if (input != null) {
							long clen = input.length();
							double t1 = System.nanoTime() - t0;
							double mbps = (t1 > 0) ? (double)(clen*1000) / t1 : -1;
							rtmLogger.log(Level.FINE, () -> String.format("%s\t%7.3f\t%d\t%s",
								inputPath, mbps, clen, transducerName));
						}
						exitCode = 0;
					}
				}
			} catch (final Exception e) {
				rteLogger.log(Level.SEVERE, "Runtime failed", e);
				System.out.println("Runtime failed, see log for details.");
			} finally {
				try {
					osw.flush();
				} catch (IOException e) {
					String message = String.format("Unable to flush final output to %s", outputPath);
					rteLogger.log(Level.SEVERE, message, e);
					exitCode = 1;
				}
			}
		} finally {
			Base.endLogging();
			System.exit(exitCode);
		}
	}
}
