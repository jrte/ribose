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

import com.characterforming.jrte.engine.Transductor;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.BaseTarget;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Basic target class for building and running UTF-8 text transduction models involving 
 * transducers that use only the built-in ribose {@link Transductor} effectors, below. 
 * These are implicit in all {@link ITarget} implementations and available to 
 * transducers in all ribose models.
 * <br><br>
 * <table>
 * <caption style="text-align:left"><b>Built-in ribose effectors</b></caption>
 * <tr><td><i>nul</i></td><td>Signal <b>nul</b> to indicate no transition defined for current input</td></tr>
 * <tr><td><i>nil</i></td><td>Does nothing</td></tr>
 * <tr><td><i>paste</i></td><td>Append current input to selected named value</td></tr>
 * <tr><td><i>paste[...]</i></td><td>Paste literal data and/or named values into selected named value</td></tr>
 * <tr><td><i>select</i></td><td>Select the anonymous named value</td></tr>
 * <tr><td><i>select[`~name`]</i></td><td>Select a named value</td></tr>
 * <tr><td><i>copy</i></td><td>Copy the anonymous named value into selected named value</td></tr>
 * <tr><td><i>copy[`~name`]</i></td><td>Copy a named value into selected named value</td></tr>
 * <tr><td><i>cut</i></td><td>Cut the anonymous named value into selected named value</td></tr>
 * <tr><td><i>cut[`~name`]</i></td><td>Cut a named value into selected named value</td></tr>
 * <tr><td><i>clear</i></td><td>Clear the anonymous named value</td></tr>
 * <tr><td><i>clear[`~name`]</i></td><td>Clear a named value </td></tr>
 * <tr><td><i>count</i></td><td>Decrement the active counter and signal when counter drops to 0</td></tr>
 * <tr><td><i>count[`~name` `!signal`]</i></td><td>Set up a counter and signal from numeric named value</td></tr>
 * <tr><td><i>in</i></td><td>Push the anonymous value onto the input stack</td></tr>
 * <tr><td><i>in[...]</i></td><td>Push literal data and/or named values onto the input stack</td></tr>
 * <tr><td><i>out</i></td><td>Write the anonymous value onto System.out</td></tr>
 * <tr><td><i>out[...]</i></td><td>Write literal data and/or named values onto System.out</td></tr>
 * <tr><td><i>mark</i></td><td>Mark a position in the input stream</td></tr>
 * <tr><td><i>reset</i></td><td>Reset position to most recent mark (if any)</td></tr>
 * <tr><td><i>start[`@transducer`]</i></td><td>Push a transducer onto the transducer stack</td></tr>
 * <tr><td><i>pause</i></td><td>Force immediate return from {@code ITransductor.run()}</td></tr>
 * <tr><td><i>stop</i></td><td>Pop the transducer stack</td></tr>
 * </table>
 * <br><br>
 * To build a text transduction model, compile with ginr a set of ribose-conformant
 * ginr patterns, saving automata (*.dfa) to be compiled into the model into a directory.
 * Run {@link TCompile#main(String[])} specifying {@link TRun} as target class,  
 * the path to the automata directory and the path and name of the file to 
 * contain the compiled model.
 * <br><br>
 * <b>TCompile Usage</b> (with TRun as model target): <pre class="code">java -cp ribose.0.0.0.jar com.characterforming.ribose.Tcompile com.characterforming.ribose.TRun &lt;automata&gt; &lt;model&gt;</pre>
 * Use {@link TRun#main(String[])} to load a text transduction model and instantiate
 * transductors to run transductions on UTF-8 byte streams.
 * <br><br>
 * <b>TRun Usage:</b> <pre class="code">java -cp ribose.0.0.0.jar com.characterforming.ribose.TRun [--nil] &lt;transducer-name&gt; &lt;input&gt; &lt;model&gt;</pre>
 * Output from the {@code out[..]} effector will be written as UTF-8 byte stream to 
 * {@code System.out} unless {@code -Djrte.out.enabled=false} is indicated as a 
 * JVM argument to the java command. Text transduction models that construct 
 * domain objects and do not otherwise use {@code out[..]} elect to use it to
 * trace problematic transducers. This option is provided to allow benchmarking
 * to proceed without incurring delays and heap overhead relating to writing to
 * {@code System.out}.
 * <br><br>
 * <table>
 * <caption style="text-align:left"><b>TRun command-line arguments</b></caption>
 * <tr><td><i>--nil</i></td><td>(Optional) Send an initial {@code nil} signal to transduction.</tr>
 * <tr><td><i>transducer</i></td><td>The name of the transducer to run.</tr>
 * <tr><td><i>input</i></td><td>The path to the input file.</tr>
 * <tr><td><i>model</i></td><td>The path to the model file.</tr>
 * </table>
 */
public final class TRun extends BaseTarget {
	/**
	 * Constructor
	 */
	public TRun() {
		super();
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITarget#getEffectors()
	 */
	@Override
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		// This is just a proxy for Transductor.getEffectors()
		return super.getEffectors();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITarget#getName()
	 */
	@Override
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
		final CharsetEncoder encoder = Base.getRuntimeCharset().newEncoder();
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
				int outsize = Integer.parseInt(System.getProperty("ribose.outbuffer.size", "8196"));
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
