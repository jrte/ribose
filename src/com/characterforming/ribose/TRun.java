package com.characterforming.ribose;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Transductor;
import com.characterforming.ribose.ITransductor.Status;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.BaseTarget;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Basic target class for building UTF-8 text transduction models containing 
 * transducers that use only the built-in ribose {@link Transductor} effectors,
 * which are implicit in all {@link ITarget} implementations and available to 
 * transducers in all ribose models.
 * <p/>
 * To build a text transduction model, compile with ginr a set of ribose-conformant
 * ginr patterns, saving automata (*.dfa) to be compiled into the model into a directory.
 * Run {@link TCompile#main(String[])} specifying {@link TRun} as target class,  
 * the path to the automata directory and the path and name of the file to 
 * contain the compiled model.
 * <p/>
 * Usage: <pre class="code">java -cp ribose.0.0.0.jar com.characterforming.ribose.Tcompile com.characterforming.ribose.TRun &lt;automata&gt; &lt;model&gt;</pre>
 * <p/>
 * Main method loads a text transduction model and instantiates a transductor to run
 * a specified transducer with input from a text file. The encoding is assumed to be
 * UTF-8.
 * <p/>
 * Usage: <pre class="code">java -cp ribose.0.0.0.jar com.characterforming.ribose.TRun [--nil] &lt;transducer-name&gt; &lt;input&gt; &lt;model&gt;</pre>
 * <p/>
 * Output from the {@code out[..]} effector will be written as UTF-8 byte stream to 
 * {@code System.out} unless {@code -Djrte.out.enabled=false} is indicated as a 
 * JVM argument to the java command. Text transduction models that construct 
 * domain objects and do not otherwise use {@code out[..]} elect to use it to
 * trace problematic transducers. This option is provided to allow benchmarking
 * to proceed without incurring delays and heap overhead relating to writing to
 * {@System.out}.
 * <table>
 * <tr><td align="right"><i>--nil</i></td><td>(Optional) Send an initial {@code nil} signal to transduction.</tr>
 * <tr><td align="right"><i>transducer</i></td><td>The name of the transducer to run.</tr>
 * <tr><td align="right"><i>input</i></td><td>The path to the input file.</tr>
 * <tr><td align="right"><i>model</i></td><td>The path to the model file.</tr>
 * </table>
 */
public final class TRun extends BaseTarget implements ITarget {
	/**
	 * Constructor (as model target for compilation of text transduction model)
	 */
	public TRun() {
		super();
	}
	
	/*
	 * (non-Javadoc)
	 * @see
	 * com.characterforming.ribose.ITarget#bindeEffectors()
	 */
	@Override
	public IEffector<?>[] bindEffectors() throws TargetBindingException {
		// This is just a proxy for Transductor.bindEffectors()
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
	 * Runs a transduction on an input file.
	 * @param args [--nil] &lt;transducer-name&gt; &lt;runtime-path&gt;
	 */
	public static void main(final String[] args) {
		final Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
		int argc = args.length;
		final boolean nil = (argc > 0) ? (args[0].compareTo("--nil") == 0) : false;
		if (nil) {
			--argc;
		}
		if (argc != 3) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=false] [--nil] <transducer-name> <input-path> <runtime-path>"));
			System.exit(1);
		}
		int arg = nil ? 1 : 0;
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String runtimePath = args[arg++];
		
		final File input = inputPath.charAt(0) == '-' ? null : new File(inputPath);
		if (input != null && !input.exists()) {
			System.out.println("No input file found at " + inputPath);
			System.exit(1);
		}
		final File model = new File(runtimePath);
		if (!model.exists()) {
			System.out.println("No ribose model file found at " + runtimePath);
			System.exit(1);
		}
		
		ITarget modelTarget = new TRun();
		int exitCode = 1;
		try (
			IRuntime ribose = Ribose.loadRiboseRuntime(model, modelTarget);
			DataInputStream isr = new DataInputStream(new FileInputStream(input));
		) {
			if (ribose != null) {
				int clen = (int)input.length();
				byte[] bytes = new byte[clen];
				clen = isr.read(bytes, 0, clen);
		
				ITarget runTarget = new TRun();
				ITransductor trun = ribose.newTransductor(runTarget);
				trun.input(bytes);
				if (nil) {
					trun.signal(Signal.nil.signal());
				}
				Status status = trun.start(Bytes.encode(transducerName));
				while (status == Status.RUNNABLE) {
					status = trun.run();
				}
				assert status != Status.NULL;
				trun.stop();
				exitCode = 0;
			}
		} catch (final Exception e) {
			rteLogger.log(Level.SEVERE, "Runtime instantiation failed", e);
			System.out.println("Runtime instantiation failed, see log for details.");
			exitCode = 1;
		} finally {
			System.exit(exitCode);
		}
	}
}
