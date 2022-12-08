package com.characterforming.ribose;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.CharsetEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.BaseTarget;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Provides a {@link TRun#main(String[])} method to run a transduction using a ribose
 * model. {@code TRun} also serves as a target class for building basic UTF-8 text or 
 * other byte stream transduction models that use only built-in ribose effectors. 
 * To build a basic transduction model, compile with ginr a set of ribose-conformant
 * ginr patterns, saving automata (*.dfa) to be compiled into the model into a
 * directory. Then run {@link TCompile} with {@link TRun} as target class to package
 * the automata in the directory into a ribose model.
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TRun usage</b></caption>
 * <tr><td style="text-align:right"><b>java</b></td><td>-cp ribose.0.0.0.jar com.characterforming.ribose.TRun [--nil] [--target <i>classname</i>] <i>model transducer input [output]</i></td></tr>
 * <tr><td style="text-align:right">--nil</td><td>Push {@link com.characterforming.ribose.base.Signal#nil} to start the transduction (recommended).</td></tr>
 * <tr><td style="text-align:right">--target <i>classname</i></td><td>Fully qualified name of the target class (must have nullary constructor), else use {@code TRun}.</td></tr>
 * <tr><td style="text-align:right"><i>model</i></td><td>The path to the model file containing the transducer.</td></tr>
 * <tr><td style="text-align:right"><i>transducer</i></td><td>The name of the transducer to start the transduction.</td></tr>
 * <tr><td style="text-align:right"><i>input</i></td><td>The path to the input file.</td></tr>
 * <tr><td style="text-align:right"><i>output</i></td><td>The path to the output file.</td></tr>
 * </table>
 * <br>
 * Default target is {@link TRun} but any target class with a nullary constructor can use used. Default output is System.out.
 * 
 */
public class TRun extends BaseTarget {
	/**
	 * Constructor
	 */
	public TRun() {
		super();
	}
	
	@Override // ITarget#getEffectors()
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		// This is just a proxy for Transductor.getEffectors()
		return new IEffector<?>[] { };
	}

	@Override // ITarget#getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

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
		final String targetName = (argc > (arg + 1) && args[arg].compareTo("--target") == 0)
		? args[++arg] : TRun.class.getName();
		arg += (arg > (nil ? 1 : 0)) ? 1 : 0;
		if ((argc - arg) != 3 && (argc - arg) != 4) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=false] [--nil] [--target <target-classname>] <model-path> <transducer-name> <input-path> [<output-path>]"));
			System.out.println("Default target is com.characterforming.ribose.TRun, default output System.out is used unless <output-path> specified.");
			System.exit(1);
		}
		final String modelPath = args[arg++];
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String outputPath = arg < argc ? args[arg++] : null;
		
		Base.startLogging();
		final CharsetEncoder encoder = Base.newCharsetEncoder();
		final Logger rteLogger = Base.getRuntimeLogger();
		final File input = inputPath.charAt(0) == '-' ? null : new File(inputPath);
		if (input != null && !input.exists()) {
			System.out.println("No input file found at " + inputPath);
			Base.endLogging();
			System.exit(1);
		}
		final File model = new File(modelPath);
		if (!model.exists()) {
			System.out.println("No ribose model file found at " + modelPath);
			Base.endLogging();
			System.exit(1);
		}
		final File outputFile = outputPath != null ? new File(outputPath) : null;
		OutputStream os = System.out;
		if (outputFile != null) {
			if (outputFile.exists()) {
				outputFile.delete();
			}
			try {
				os = new FileOutputStream(outputFile);
			} catch (FileNotFoundException e) {
				System.out.println("No path to output file at " + outputPath);
				Base.endLogging();
				System.exit(1);
				}
		}

		int exitCode = 1;
		try (BufferedOutputStream osw = new BufferedOutputStream(os, Base.getOutBufferSize())) {
			try (
				IRuntime ribose = Ribose.loadRiboseModel(model);
				FileInputStream isr = new FileInputStream(input);
			) {
				if (ribose != null) {
					Bytes transducer = Bytes.encode(encoder, transducerName);
					ITarget runTarget = (ITarget) Class.forName(targetName).getDeclaredConstructor().newInstance();
					if (ribose.transduce(runTarget, transducer, nil ? Signal.nil : null, isr, osw)) {
						exitCode = 0;
					}
				}
			} catch (final Exception e) {
				rteLogger.log(Level.SEVERE, "Runtime failed", e);
				System.out.println("Runtime failed, see log for details.");
			} catch (final AssertionError e) {
				rteLogger.log(Level.SEVERE, "Runtime assertion failed", e);
				System.out.println("Runtime assertion failed, see log for details.");
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
