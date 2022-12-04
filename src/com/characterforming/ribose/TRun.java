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
 * model. 
 * <br><br>
 * <table style="font-size:12px">
 * <caption style="text-align:left"><b>TRun usage</b></caption>
 * <tr><td style="text-align:right">java</td><td>-cp ribose.0.0.0.jar com.characterforming.ribose.TRun &lt;target&gt; &lt;input&gt; &lt;model&gt;</td></tr>
 * <tr><td style="text-align:right">target</td><td>Fully qualified name of the model target class.</td></tr>
 * <tr><td style="text-align:right">transducer</td><td>The path to the directory containing automata (*.dfa) to include in the model.</td></tr>
 * <tr><td style="text-align:right">input</td><td>The path to the input file.</td></tr>
 * <tr><td style="text-align:right">model</td><td>The path to the model file containing the transducer.</tr>
 * <tr><td style="text-align:right">output</td><td>The path to the output file.</tr>
 * </table>
 * <br><br>
 * {@code TRun} also serves as a target class for building basic UTF-8 text or 
 * other byte stream transduction models that use only built-in ribose effectors. 
 * To build a basic transduction model, compile with ginr a set of ribose-conformant
 * ginr patterns, saving automata (*.dfa) to be compiled into the model into a
 * directory. Then run {@link TCompile} to package the automata in the directory
 * into a ribose model.
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
	 *    
	 * @param args [--nil] &lt;target-class&gt; &lt;transducer-name&gt; &lt;input-file-path&gt; &lt;trun-model-path&gt;
	 */
	public static void main(final String[] args) {
		final CharsetEncoder encoder = Base.newCharsetEncoder();
		final Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
		rteLogger.setLevel(Level.WARNING);
		int argc = args.length;
		final boolean nil = (argc > 0) ? (args[0].compareTo("--nil") == 0) : false;
		int arg = nil ? 1 : 0;
		if ((argc - arg) != 4 && (argc - arg) != 5) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=false] [--nil] <target-class> <transducer-name> <input-path> <model-path> [<output-path>]"));
			System.out.println("Default output is System.out, unless <output-path> specified");
			System.exit(1);
		}
		final String targetClassname = args[arg++];
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String modelPath = args[arg++];
		final String outputPath = arg < argc ? args[arg++] : null;
		final File outputFile = outputPath != null ? new File(outputPath) : null;
		if (outputFile != null) {
			if (outputFile.exists()) {
				outputFile.delete();
			}
		}
		OutputStream osw = System.out;
		if (outputFile != null) {
			try {
				int outsize = Base.getOutBufferSize();
				osw = new BufferedOutputStream(new FileOutputStream(outputFile), outsize);
			} catch (FileNotFoundException e) {
				Ribose.rtcLogger.log(Level.SEVERE, String.format("Unable to open output file %s", outputPath), e);
				System.exit(1);
			}
		}
	
		final File input = inputPath.charAt(0) == '-' ? null : new File(inputPath);
		if (input != null && !input.exists()) {
			System.out.println("No input file found at " + inputPath);
			System.exit(1);
		}
		final File model = new File(modelPath);
		if (!model.exists()) {
			System.out.println("No ribose model file found at " + modelPath);
			System.exit(1);
		}

		int exitCode = 1;
		ITarget modelTarget = null;
		try {
			Class<?> targetClass = Class.forName(targetClassname);
			modelTarget = (ITarget) targetClass.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			Ribose.rtcLogger.log(Level.SEVERE, String.format("target-class '%1$s' could not be instantiated as model target", targetClassname), e);
			System.exit(exitCode);
		}
		try (
			IRuntime ribose = Ribose.loadRiboseModel(model, modelTarget);
			FileInputStream isr = new FileInputStream(input);
		) {
			if (ribose != null) {
				ITarget runTarget = new TRun();
				Bytes transducer = Bytes.encode(encoder, transducerName);
				if (ribose.transduce(runTarget, transducer, nil ? Signal.nil : null, isr, osw)) {
					exitCode = 0;
				}
			}
		} catch (final Exception e) {
			rteLogger.log(Level.SEVERE, "Runtime failed", e);
			System.out.println("Runtime failed, see log for details.");
			exitCode = 1;
		} catch (final AssertionError e) {
			rteLogger.log(Level.SEVERE, "Runtime assertion failed", e);
			System.out.println("Runtime assertion failed, see log for details.");
			exitCode = 1;
		} finally {
			if (outputFile != null) {
				try {
					osw.close();
				} catch (IOException e) {
					String message = String.format("Unable to close output file %s", outputPath);
					rteLogger.log(Level.SEVERE, message, e);
					System.out.println(message);
					exitCode = 1;
				}
			}
			System.exit(exitCode);
		}
	}
}
