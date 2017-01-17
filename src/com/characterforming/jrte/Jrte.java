/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

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
	 * Constructor sets up the gearbox as an ITransduction factory. This will instantiate a proxy instance of the
	 * target class for effector binding as follows:
	 * <p>
	 * <code>Target.Target(); Target.getName(); bind(null); (Efffector.getName())*</code>
	 * <p>
	 * The proxy target instance is discarded after enumerating the target and effector namespace.
	 * 
	 * @param gearboxPath The path to the gearbox
	 * @param targetClassName The fully qualified Java class name of the target class to bind to the transduction stack
	 * @throws GearboxException If the gearbox is invalid or corrupt or cannot be instantiated
	 * @throws TargetBindingException If the target or target effectors cannot be bound
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
	 * @throws TargetBindingException If the target effectors cannot be bound
	 * @throws TargetNotFoundException If the target class is not known to the gearbox
	 * @throws GearboxException If the gearbox is invalid or corrupt
	 * @throws TargetNotFoundException If the specified target class does not match gearbox target class
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
	 * @throws GearboxException
	 * @throws InputException
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
	 * @throws GearboxException
	 * @throws InputException
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
	 * @throws GearboxException
	 * @throws InputException
	 */
	public IInput input(final InputStream infile, final Charset charset) throws GearboxException, InputException {
		return new StreamInput(infile, charset);
	}
}
