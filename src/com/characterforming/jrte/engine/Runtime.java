/***
 * Ribose is a recursive transduction engine for Java
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
 * You should have received a copy of the GNU General Public License
 * along with this program (LICENSE-gpl-3.0). If not, see
 * <http://www.gnu.org/licenses/#GPL>.
 */

package com.characterforming.jrte.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Model.Mode;
import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;

/**
 * Ribose runtime loads a target model and instantiates runtime transductors. A model 
 * is a binding of a target class and a collection of ribose transducers and supporting 
 * signals and named values. A transductor is a capability to run transductions. A 
 * transduction is a process mapping serial input through a nesting of transducers
 * on a transductor's input and transducer stacks. 
 * 
 * @author Kim Briggs
 */
public final class Runtime implements IRuntime {
	public final static Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
	private final Model model;
	
	/**
	 * Constructor sets up the runtime as an ITransductor factory. This will instantiate a 
	 * default instance of the target class for effector binding. The target instance will
	 * only receive two method calls ({@code getName()} then {@code bindEffector()} and each
	 * bound {@code P} parameterized effector will receive a call to {@code newParameters(N)}
	 * followed by {@code N compileParameters(byte[][])} calls, one for each enumerated 
	 * parameter. The effector is expected to instantiate an array {@code P[N]} and compile 
	 * each received {@code byte[][]} into a {@code P} instance into the array. 
	 * 
	 * The model target instance is not used after after instantiating model effectors and 
	 * compiling model effector paramters. The model effectors remain bound to the model 
	 * to provide live effectors with precompiled parameters when transductors are 
	 * instantiated for new target instances. 
	 * 
	 * @param modelPath The path to a runtime model for the target class
	 * @param target The targetinstance to bind to transductors
	 * @throws ModelException on error
	 */
	public Runtime(final File modelPath, final ITarget target) throws ModelException {
		this.model = new Model(Mode.run, modelPath, target);
		this.model.initialize();
		this.model.load();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IRuntime#newTransductor(ITarget)
	 */
	@Override
	public ITransductor newTransductor(ITarget target) throws ModelException {
		return this.model.bindTransductor(target);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IRuntime#transduce(Bytes, Signal, InputStream)
	 */
	@Override
	public boolean transduce(ITarget target, Bytes transducer, Signal prologue, InputStream in, OutputStream out) throws RiboseException {
		try {
			byte[] bytes = new byte[InputStack.BLOCK_SIZE];
			int read = in.read(bytes);
			@SuppressWarnings("unused")
			int position = read;
			if (read > 0) {
				ITransductor trex = newTransductor(target);
				if (trex.push(bytes, read).status().isWaiting()
				&& ((prologue == null) || (trex.push(prologue).status().isWaiting()))
				&& (trex.start(transducer).status().isRunnable())) {
					trex.output(out);
					do {
						if (trex.run().status().isPaused()) {
							bytes = trex.recycle(bytes);
							assert bytes != null;
							read = in.read(bytes);
							if (read > 0) {
								trex.push(bytes, read).status();
								position += read;
							} else {
								break;
							}
						}
					} while (trex.status().isRunnable());
					trex.stop();
				}
			}
	} catch (ModelException e) {
			log(target, transducer, e);
			return false;
		} catch (DomainErrorException e) {
			log(target, transducer, e);
			return false;
		} catch (IOException e) {
			log(target, transducer, e);
			return false;
		} catch (AssertionError e) {
			log(target, transducer, e);
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IRuntime#transduce(Bytes, InputStream)
	 */
	@Override
	public boolean transduce(ITarget target, Bytes transducer, InputStream in, OutputStream out) throws RiboseException {
		return this.transduce(target, transducer, null, in, out);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IRuntime#close()
	 */
	@Override
	public void close() {
		this.model.close();
	}
	
	private void log(ITarget target, Bytes transducer, Throwable e) {
		Runtime.rteLogger.log(Level.SEVERE, String.format("Exception in Runtime.transduce(%1$s, %2$s, ...)",
			target.getClass().getSimpleName(), transducer.toString()), e);
	}
}
