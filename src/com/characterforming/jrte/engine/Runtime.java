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
import com.characterforming.ribose.base.BaseTarget;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.ModelException;

/**
 * Ribose runtime loads a target model and instantiates runtime transductors. A 
 * transductor is a capbility to run transductions. A main() method is provided
 * to enable {@link BaseTarget} model collections of stdin-&gt;stdout text transducers
 * to be run from host shells. Models based on other target classes are loaded and used
 * similarly in other service and application contexts.
 * 
 * @author Kim Briggs
 */
public final class Runtime implements IRuntime, AutoCloseable {
	public final static Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
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
	public Runtime(final File runtimePath, final ITarget target) throws ModelException {
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
		return this.model.bindTransductor(target, false);
	}

	@Override
	public void close() throws ModelException {
		this.model.close();
	}
}
