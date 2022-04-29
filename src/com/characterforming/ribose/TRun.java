package com.characterforming.ribose;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.ITransductor.Status;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.BaseTarget;
import com.characterforming.ribose.base.Bytes;

/**
 * Loads a ribose runtime model file and runs a transduction on UTF-8 text input from a file.  
 * <p/>
 * Usage: <pre class="code">java -cp Jrte.jar com.characterforming.ribose.TRun [--nil] &lt;transducer-name&gt; &lt;input&gt; &lt;model&gt;</pre>
 * <table>
 * <tr><td align="right"><i>--nil</i></td><td>(Optional) Send an initial {@code nil} signal to transduction.</tr>
 * <tr><td align="right"><i>transducer</i></td><td>The name of the transducer to run.</tr>
 * <tr><td align="right"><i>input</i></td><td>The path to the input file.</tr>
 * <tr><td align="right"><i>model</i></td><td>The path to the model file.</tr>
 * </table>
 */
public final class TRun extends BaseTarget implements ITarget {

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
		
		final File input = new File(inputPath);
		if (!input.exists()) {
			System.out.println("No input file found at " + inputPath);
			System.exit(1);
		}
		final File model = new File(runtimePath);
		if (!model.exists()) {
			System.out.println("No ribose model file found at " + runtimePath);
			System.exit(1);
		}
		
		ITransductor t = null;
		ITarget modelTarget = new TRun();
		int exitCode = 0;
		try (
			IRuntime ribose = Ribose.loadRiboseRuntime(model, modelTarget);
			DataInputStream isr = new DataInputStream(new FileInputStream(input));
		) {
			int clen = (int)input.length();
			byte[] bytes = new byte[clen];
			clen = isr.read(bytes, 0, clen);
	
			ITarget runTarget = new TRun();
			ITransductor trun = t = ribose.newTransductor(runTarget);
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
		} catch (final Exception e) {
			rteLogger.log(Level.SEVERE, "Runtime instantiation failed", e);
			System.out.println("Runtime instantiation failed, see log for details.");
			exitCode = 1;
		}
		System.exit(exitCode);
	}
}
