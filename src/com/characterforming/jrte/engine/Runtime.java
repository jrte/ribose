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

package com.characterforming.jrte.engine;

import java.io.File;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Model.Mode;
import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.ModelException;

/**
 * Ribose runtime loads a target model and instantiates runtime transductors. A model 
 * is a binding of a target class and a collection of ribose transducers and supporting 
 * signals and named values. A transductor is a capability to run transductions. A 
 * transduction is a process mapping serial input through a nesting of transducers
 * on a transductor's input and transducer stacks. 
 * 
 * @author Kim Briggs
 */
public final class Runtime implements IRuntime, AutoCloseable {
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
	 * @throws ModelException
	 */
	public Runtime(final File modelPath, final ITarget target) throws ModelException {
		this.model = new Model(Mode.run, modelPath, target);
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
		return this.model.bindTransductor(target, false);
	}

	@Override
	public void close() {
		this.model.close();
	}
}
