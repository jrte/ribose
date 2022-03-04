/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.base.Bytes;
import com.characterforming.jrte.engine.Gearbox;
import com.characterforming.jrte.engine.Gearbox.Gear;
import com.characterforming.jrte.engine.Transduction;

/**
 * This is the Jrte main runtime transduction factory class. It opens a transducer gearbox and
 * enumerates the target and effector namespaces on instantiation. The target and effector
 * namespaces must be identical to the target and effector namespaces used to build the gearbox.
 * <p>
 * Use {@link #transduction(ITarget)} to instantiate a transduction for runtime application. To transduce the input starting with a specific transducer, call the {@link ITransduction#start(Bytes)} and {@link ITransduction#input(IInput[])}
 * methods.
 * <p>
 * This class is threadsafe, but ITransduction instances obtained from runtime instances of this class are not. Each ITransduction instance is expected to run on a single thread; otherwise, references to the instance must be synchronized by
 * the client threads. If multiple ITransduction instances are run on concurrent threads using the same ITarget instance then references to the ITarget instance must be synchronized.
 * 
 * @author kb
 */
public final class Jrte {
	private final static Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
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
			throws SecurityException, IOException, RteException, GearboxException {
		int argc = args.length;
		final boolean nil = (argc > 0) ? (args[0].compareTo("--nil") == 0) : false;
		if (nil) {
			--argc;
		}
		if (argc != 3) {
			for (int i = 0; i < args.length; i++) {System.out.printf("%d: %s\n", i, args[i]); } 
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=false] [--nil] <transducer-name> <input-path> <gearbox-path>"));
			System.exit(1);
		}
		int arg = nil ? 1 : 0;
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String gearboxPath = args[arg++];

		try {
			final File f = new File(inputPath);
			final DataInputStream isr = new DataInputStream(new FileInputStream(f));
			int clen = (int)f.length();
			byte[] bytes = new byte[clen];
			clen = isr.read(bytes, 0, clen);
			isr.close();

			ITarget baseTarget = new BaseTarget();
			Jrte jrte = new Jrte(new File(gearboxPath), baseTarget);	
			ITransduction transduction = jrte.transduction(baseTarget);
			transduction.start(Bytes.encode(transducerName));
			transduction.input(nil
				? new IInput[] { (ByteInput) jrte.input(new byte[][] { Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, Base.Signal.nil.signal()), bytes }) }
				: new IInput[] { (ByteInput) jrte.input(new byte[][] { bytes }) }
			);
			do {
				switch (transduction.run()) {
				case RUNNABLE:
					break;
				case PAUSED:
				case STOPPED:
					transduction.stop();
					break;
				case NULL:
				default:
					assert false;
					break;
				}
			}
			while (transduction.status() == ITransduction.Status.PAUSED);
		} catch (final Exception e) {
			rteLogger.log(Level.SEVERE, String.format("Caught Exception running transducer '%1$s' from gearbox '%2$s'",
					transducerName, gearboxPath), e);
			System.exit(1);
		}
		System.exit(0);
	}

	/**
	 * Constructor sets up the gearbox as an ITransduction factory. This will instantiate a proxy instance of the
	 * target class for effector binding as follows:
	 * <p>
	 * <code>Target.Target(); Target.getName(); bind(null); (Effector.getName())*</code>
	 * <p>
	 * The proxy target instance is discarded after enumerating the target and effector namespace.
	 * 
	 * @param gearboxPath The path to the gearbox
	 * @param target The target instance to bind to the transduction 
	 * @throws GearboxException On error
	 * @throws TargetBindingException On error
	 */
	public Jrte(final File gearboxPath, final ITarget target) throws GearboxException {
		try {
			this.gearbox = new Gearbox(Gear.run, gearboxPath, target);
		} catch (final Exception e) {
			throw new GearboxException("Unable to instantiate Jrte", e);
		}
	}

	/**
	 * Bind an unbound target instance to a new transduction. Use the {@link ITransduction#start(Bytes)}
	 * and {@link ITransduction#run()} methods to set up and run the transduction.
	 * 
	 * @param target The ITarget instance to bind to the transduction
	 * @return The bound Transduction instance
	 * @throws TargetBindingException On error
	 * @throws TargetNotFoundException On error
	 * @throws GearboxException On error
	 * @throws TargetNotFoundException On error
	 */
	public ITransduction transduction(final ITarget target) throws GearboxException, RteException {
		Class<? extends ITarget> targetClass = target.getClass();
		Class<? extends ITarget> gearboxClass = this.gearbox.getTarget().getClass();
		if (!gearboxClass.isAssignableFrom(targetClass)) {
			throw new TargetNotFoundException(String.format("Cannot bind instance of target class '%1$s', can only bind to gearbox target class '%2$s'", target.getClass().getName(), this.gearbox.getTarget().getName()));
		}
		Transduction trex = this.gearbox.bindTransduction(target);
		assert trex != null && trex.status() != ITransduction.Status.NULL;
		return trex;
	}

	/**
	 * Set up a transduction input source with a sequence of UTF-8 and signal ordinals.
	 * Each signal reference in the input array is mapped to the respective signal ordinal
	 * encoded separately in input[][] as {0xFF,'!',hi,lo} encoding the 16-bit signal 
	 * ordinal as (hi &lt;&lt; 8) | lo. Other tokens in the input array are treated
	 * as sequences of 8-bit byte (binary 0x0..0xff).
	 * 
	 * Note that this limits the range of unreserved tokens available to ginr patterns 
	 * for jrte to {@code (0x00..0xff)* - 0xff('!'|'~'|'@')(0x00..0xff)(0x00..0xff)}. 
	 * 
	 * @param input The symbolic names for the signals and text segments to include in LIFO order (input[0] is last out)
	 * @return An IInput containing the signal sequence
	 * @throws GearboxException On error
	 * @throws InputException On error
	 */
	public IInput input(final byte[][] input) throws GearboxException, InputException {
		return new ByteInput(input);
	}
}
