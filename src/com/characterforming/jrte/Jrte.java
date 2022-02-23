/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.engine.Gearbox;
import com.characterforming.jrte.engine.Transduction;
import com.characterforming.jrte.engine.input.ReaderInput;
import com.characterforming.jrte.engine.input.SignalInput;
import com.characterforming.jrte.engine.input.StreamInput;

/**
 * This is the Jrte main runtime transduction factory class. It opens a transducer gearbox and
 * enumerates the target and effector namespaces on instantiation. The target and effector
 * namespaces must be identical to the target and effector namespaces used to build the gearbox.
 * <p>
 * Use {@link #transduction(ITarget)} to instantiate a transduction for runtime application. To transduce the input starting with a specific transducer, call the {@link ITransduction#start(String)} and {@link ITransduction#input(IInput[])}
 * methods.
 * <p>
 * This class is threadsafe, but ITransduction instances obtained from runtime instances of this class are not. Each ITransduction instance is expected to run on a single thread; otherwise, references to the instance must be synchronized by
 * the client threads. If multiple ITransduction instances are run on concurrent threads using the same ITarget instance then references to the ITarget instance must be synchronized.
 * 
 * @author kb
 */
public final class Jrte {
	private final Gearbox gearbox;
	
	/**
	 * Default Jrte runtime transduces System.in to System.out, with an optional nil signal as input prefix. 
	 * <pre class="code">Usage: java -cp Jrte.jar com.characterforming.jrte.Jrte [--nil] &lt;transducer-name&gt; &lt;gearbox-path&gt;</pre>
	 * @param args [--nil] &lt;transducer-name&gt; &lt;gearbox-path&gt;
	 * @throws SecurityException On error
	 * @throws IOException On error
	 * @throws RteException On error
	 * @throws GearboxException On error
	 * @throws TargetBindingException On error
	 * @throws InputException On error
	 */
	public static void main(final String[] args) 
			throws SecurityException, IOException, RteException, GearboxException, TargetBindingException, InputException {
		if ((args.length < 3) || (args.length > 4)) {
			for (int i = 0; i < args.length; i++) {System.out.printf("%d: %s\n", i, args[i]); } 
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=false] [--nil] <transducer-name> <input-path> <gearbox-path>"));
			System.exit(1);
		}
		final boolean nil = args[0].compareTo("--nil") == 0;
		int arg = nil ? 1 : 0;
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String gearboxPath = args[arg++];

		try {
			Jrte jrte = new Jrte(new File(gearboxPath), "com.characterforming.jrte.base.BaseTarget");
//			System.out.format("Opened %s\n", gearboxPath);
			final File f = new File(inputPath);
			final InputStreamReader isr = new InputStreamReader(new FileInputStream(f));
			int clen = (int)f.length();
			char[] chars = new char[clen];
			clen = isr.read(chars, 0, clen);
			isr.close();

			IInput[] inputs = nil
					? new IInput[] { (SignalInput) jrte.input(new char[][] {new String("!nil").toCharArray(), chars}) }
					: new IInput[] { (SignalInput) jrte.input(new char[][] {chars}) };

//			System.out.format("Inputs.length %d\n", inputs.length);
			ITarget baseTarget = new BaseTarget();
//			System.out.format("Target %s\n", baseTarget.getClass().getName());
			ITransduction transduction = jrte.transduction(baseTarget);
//			System.out.format("Transduction %s\n", transduction.getClass().getName());
			transduction.start(transducerName);
//			System.out.format("Transducer %s\n", transducerName);
			transduction.input(inputs);
//			for (int i = 0; i < inputs.length; i++) {
//				System.out.format("Inputs[$d] %s\n", i, inputs[i].getClass().getName());
//			}
			while (transduction.status() == ITransduction.RUNNABLE) {
//				System.out.format("Running...\n");
				transduction.run();
			} 
		} catch (Exception e) {
			System.out.print(e);
		}
	}

	/**
	 * Constructor sets up the gearbox as an ITransduction factory. This will instantiate a proxy instance of the
	 * target class for effector binding as follows:
	 * <p>
	 * <code>Target.Target(); Target.getName(); bind(null); (Efffector.getName())*</code>
	 * <p>
	 * The proxy target instance is discarded after enumerating the target and effector namespace.
	 * 
	 * @param gearboxPath The path to the gearbox
	 * @param targetClassName The fully qualified Java class name of the target class to bind to the transduction stack
	 * @throws GearboxException On error
	 * @throws TargetBindingException On error
	 */
	public Jrte(final File gearboxPath, final String targetClassName) throws GearboxException, TargetBindingException {
		try {
			this.gearbox = new Gearbox(gearboxPath, (ITarget) Class.forName(targetClassName).newInstance());
		} catch (final InstantiationException e) {
			throw new TargetBindingException(String.format("Unable to instantiate class '%1$s'", targetClassName), e);
		} catch (final IllegalAccessException e) {
			throw new TargetBindingException(String.format("Unable to access class '%1$s'", targetClassName), e);
		} catch (final ClassNotFoundException e) {
			throw new TargetBindingException(String.format("Unable to find class '%1$s'", targetClassName), e);
		} catch (final Exception e) {
			throw new GearboxException("Unable to instantiate Jrte", e);
		}
	}

	/**
	 * Bind an unbound target instance to a new transduction. Use the {@link ITransduction#start(String)}
	 * and {@link ITransduction#run()} methods to set up and run the transduction.
	 * 
	 * @param target The ITarget instance to bind to the transduction
	 * @return The bound Transduction instance
	 * @throws TargetBindingException On error
	 * @throws TargetNotFoundException On error
	 * @throws GearboxException On error
	 * @throws TargetNotFoundException On error
	 */
	public ITransduction transduction(final ITarget target) throws TargetBindingException, TargetNotFoundException, GearboxException, RteException {
		if (target.getClass().equals(this.gearbox.getTarget().getClass())) {
			return new Transduction(this.gearbox, target, false);
		} else {
			throw new TargetNotFoundException(String.format("Cannot bind instance of target class '%1$s', can only bind to gearbox target class '%2$s'", target.getClass().getName(), this.gearbox.getTarget().getName()));
		}
	}

	/**
	 * Set up a transduction input source with a sequence of Unicode and signal ordinals.
	 * Each signal reference in the input array is mapped to the respective signal
	 * ordinal. Other tokens in the input array are treated as text Unicode ordinals.
	 * 
	 * @param input The symbolic names for the signals and text segments to include in LIFO order (input[0] is last out)
	 * @return An IInput containing the signal sequence
	 * @throws GearboxException On error
	 * @throws InputException On error
	 */
	public IInput input(final char[][] input) throws GearboxException, InputException {
		int n = 0;
		final char[][] array = new char[input.length][];
		for (final char[] chars : input) {
			final char[] signal = this.gearbox.getSignalReference(chars);
			array[n++] = signal != null ? signal : chars;
		}
		return new SignalInput(array);
	}

	/**
	 * Set up a transduction input source with a text file as input source.
	 * 
	 * @param infile The text input source
	 * @return An IInput wrapping the input source
	 * @throws GearboxException On error
	 * @throws InputException On error
	 */
	public IInput input(final Reader infile) throws GearboxException, InputException {
		return new ReaderInput(infile);
	}

	/**
	 * Set up a transduction input source with a raw file and charset decoder as input source.
	 * 
	 * @param infile The raw input source
	 * @param charset The Charset to decode the raw input with
	 * @return An IInput wrapping the input source
	 * @throws GearboxException On error
	 * @throws InputException On error
	 */
	public IInput input(final InputStream infile, final Charset charset) throws GearboxException, InputException {
		return new StreamInput(infile, charset);
	}
}
