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

package com.characterforming.ribose;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.ByteInput;
import com.characterforming.jrte.GearboxException;
import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.InputException;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.TargetNotFoundException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.base.Bytes;
import com.characterforming.jrte.engine.Gearbox;
import com.characterforming.jrte.engine.Gearbox.Gear;
import com.characterforming.jrte.engine.Transduction;

/**
 * Ribose runtime transduction factory. Use {@link newTransduction(ITarget)} to instantiate a 
 * transduction and call {@link com.characterforming.jrte.ITransduction#start(Bytes)} and 
 * {@link com.characterforming.jrte.ITransduction#input(IInput[])} to set up the initial 
 * input and transducer stacks, the drive the tranduction to an endpoint by calling 
 * {@link com.characterforming.jrte.ITransduction#run()} until 
 * {@link com.characterforming.jrte.ITransduction#status()} returns
 * {@link com.characterforming.jrte.ITransduction.Status#STOPPED}.
 * 
 * @author Kim Briggs
 */
public final class RiboseRuntime implements IRiboseRuntime, AutoCloseable {
	private final static Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
	private final Gearbox gearbox;
	
	/**
	 * Constructor sets up the runtime as an ITransduction factory. This will instantiate a proxy instance of the
	 * target class for effector binding as follows:
	 * <p>
	 * <code>Target.Target(); Target.getName(); bind(null); (Effector.getName())*</code>
	 * <p>
	 * The proxy target instance is discarded after enumerating the target and effector namespace.
	 * 
	 * @param runtimePath The path to the runtime gearbox file
	 * @param target The target instance to bind to the transduction 
	 * @throws GearboxException On error
	 * @throws TargetBindingException On error
	 */
	public RiboseRuntime(final File runtimePath, final ITarget target) throws GearboxException {
		try {
			this.gearbox = new Gearbox(Gear.run, runtimePath, target);
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
	@Override
	public ITransduction newTransduction(final ITarget target) throws GearboxException, RteException {
		Class<? extends ITarget> targetClass = target.getClass();
		Class<? extends ITarget> gearboxClass = this.gearbox.getTarget().getClass();
		if (!gearboxClass.isAssignableFrom(targetClass)) {
			throw new TargetNotFoundException(String.format("Cannot bind instance of target class '%1$s', can only bind to gearbox target class '%2$s'", target.getClass().getName(), this.gearbox.getTarget().getName()));
		}
		Transduction trex = this.gearbox.bindTransduction(target);
		assert trex != null;
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
	 */
	@Override
	public IInput input(final byte[][] input) {
		return new ByteInput(input);
	}

	@Override
	public void close() throws GearboxException {
		this.gearbox.close();
	}
	
	/**
	 * Default ribose runtime transduces System.in to System.out, with an optional nil signal as input prefix. 
	 * <pre class="code">Usage: java -cp Jrte.jar com.characterforming.ribose.RiboseRuntime [--nil] &lt;transducer-name&gt; &lt;runtime-path&gt;</pre>
	 * @param args [--nil] &lt;transducer-name&gt; &lt;runtime-path&gt;
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
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=false] [--nil] <transducer-name> <input-path> <runtime-path>"));
			System.exit(1);
		}
		int arg = nil ? 1 : 0;
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String runtimePath = args[arg++];

		final File f = new File(inputPath);
		ITarget baseTarget = new BaseTarget();
		IRiboseRuntime ribose = null;
		DataInputStream isr = null;
		try {
			int clen = (int)f.length();
			byte[] bytes = new byte[clen];
			ribose = Ribose.loadRiboseRuntime(new File(runtimePath), baseTarget);
			isr = new DataInputStream(new FileInputStream(f));
			clen = isr.read(bytes, 0, clen);

			ITransduction transduction = ribose.newTransduction(baseTarget);
			transduction.start(Bytes.encode(transducerName));
			transduction.input(nil
				? new IInput[] { (ByteInput) ribose.input(new byte[][] { Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, Base.Signal.nil.signal()), bytes }) }
				: new IInput[] { (ByteInput) ribose.input(new byte[][] { bytes }) }
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
				transducerName, runtimePath), e);
			System.exit(1);
		} finally {
			ribose.close();
			isr.close();
		}
		System.exit(0);
	}
}
