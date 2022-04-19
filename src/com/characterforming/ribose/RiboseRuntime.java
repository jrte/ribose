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
import java.util.logging.Logger;

import com.characterforming.jrte.ByteInput;
import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ITransductor;
import com.characterforming.jrte.ModelException;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.base.Bytes;
import com.characterforming.jrte.engine.Model;
import com.characterforming.jrte.engine.Model.Mode;

/**
 * Ribose runtime loads a target model and instantiates runtime transductors. A 
 * transductor is a capbility to run transductions. A main() method is provided
 * to enable {@link BaseTarget} model collections of stdin-&gt;stdout text transducers
 * to be run from host shells. Models based on other target classes are loaded and used
 * similarly in other service and application contexts.
 * 
 * @author Kim Briggs
 */
public final class RiboseRuntime implements IRiboseRuntime, AutoCloseable {
	final static Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
	private final Model model;
	
	/**
	 * Constructor sets up the runtime as an ITransductor factory. This will instantiate a 
	 * model instance of the target class for effector binding as follows:
	 * <p>
	 * <code>
	 * 	t = new Target(); 
	 * 	t.getName(); 
	 * 	t.bindEffectors();
	 *  (
	 *  	Effector.getName()
	 *  	(
	 *  	 Effector.newParamameters(count)
	 *  	 Effector.compileParameter(byte[][])*
	 *  	)?
	 *  )*
	 *  </code>
	 * <p>
	 * The model target instance is not used after after instantiating model effectors and 
	 * compiling model effector paramters. The model effectors remain bound to the model 
	 * to provide live effectors with precompiled parameters when transductors are 
	 * instantiated for new target instances. 
	 * 
	 * @param runtimePath The path to the runtime model file
	 * @param target The target instance to bind to transductors
	 * @throws ModelException
	 */
	RiboseRuntime(final File runtimePath, final ITarget target) throws ModelException {
		this.model = new Model(Mode.run, runtimePath, target);
		this.model.load();
	}

	/**
	 * Bind an unbound target instance to a new transductor. Use the {@link ITransductor#start(Bytes)}
	 * and {@link ITransductor#run()} methods to set up and run transductions.
	 * 
	 * @param target The ITarget instance to bind to the transductor
	 * @return The bound ITransductor instance
	 * @throws ModelException
	 */
	@Override
	public ITransductor newTransductor(final ITarget target) throws ModelException {
		return this.model.bindTransductor(target);
	}

	/**
	 * Set up a transduction input source with a sequence of UTF-8 and signal ordinals.
	 * Each signal reference in the input array is mapped to the respective signal ordinal
	 * encoded separately in input[][] as {0xff,'!',hi,lo} encoding the 16-bit signal 
	 * ordinal as{@code  (hi << 8) | lo}. Other tokens in the input array are treated
	 * as sequences of 8-bit byte (binary {@code 0x0..0xff}). 
	 * <p/>
	 * Note that this limits the range of unreserved tokens available to ginr patterns 
	 * for jrte to {@code (0x00..0xff)* - 0xff ('!'|'~'|'@')(0x00..0xff)(0x00..0xff)}. UTF-8
	 * tokens are not constrained because {@code (0xf8..0xf8)} are not legal in UTF-8 streams.
	 * Binary tokens are not impacted unless they are contained in a 4-byte buffer and have a
	 * {@code 0xff ('!'|'~'|'@')}.
	 * 
	 * @param input The symbolic names for the signals and text segments to include in LIFO order (input[0] is last out)
	 * @return An IInput containing the signal sequence
	 */
	@Override
	public IInput input(final byte[][] input) {
		return new ByteInput(input);
	}

	@Override
	public void close() throws ModelException {
		this.model.close();
	}
	
	/**
	 * Default ribose runtime transduces System.in to System.out, with an optional nil signal as input prefix. 
	 * <pre class="code">Usage: java -cp Jrte.jar com.characterforming.ribose.RiboseRuntime [--nil] &lt;transducer-name&gt; &lt;runtime-path&gt;</pre>
	 * @param args [--nil] &lt;transducer-name&gt; &lt;runtime-path&gt;
	 * 
	 * @throws ModelException
	 */
	public static void main(final String[] args) 
			throws SecurityException, IOException, RteException, ModelException {
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
		
		ITarget baseTarget = new BaseTarget();
		IRiboseRuntime ribose = null;
		DataInputStream isr = null;
		int exitCode = 0;
		try {
			int clen = (int)input.length();
			byte[] bytes = new byte[clen];
			ribose = Ribose.loadRiboseRuntime(model, baseTarget);
			isr = new DataInputStream(new FileInputStream(input));
			clen = isr.read(bytes, 0, clen);

			ITransductor transductor = ribose.newTransductor(baseTarget);
			transductor.start(Bytes.encode(transducerName));
			transductor.input(nil
				? new IInput[] { (ByteInput) ribose.input(new byte[][] { Base.Signal.nil.reference(), bytes }) }
				: new IInput[] { (ByteInput) ribose.input(new byte[][] { bytes }) }
			);
			do {
				switch (transductor.run()) {
				case RUNNABLE:
					break;
				case PAUSED:
				case STOPPED:
					transductor.stop();
					break;
				case NULL:
				default:
					assert false;
					break;
				}
			}
			while (transductor.status() == ITransductor.Status.PAUSED);
		} catch (final Exception e) {
			System.out.println("Runtime instantiation failed, see log for details.");
			exitCode = 1;
		} finally {
			if (ribose != null) {
				ribose.close();
			}
			if (isr != null) {
				isr.close();
			}
		}
		System.exit(exitCode);
	}
}
