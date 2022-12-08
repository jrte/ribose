/*
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

/**
 * Ribose base classes for integration and extension into other domains.
 * <br><br>
 * Ribose operates on byte[] and {@link Signal} inputs. Input may include UTF-8
 * encoded text and/or binary data. The {@link Byte} wrapper class and {@link Base}
 * utility class are used extensively throughout the ribose compiler and runtime. 
 * The base classes {@link BaseEffector} and {@link  BaseParameterizedEffector} 
 * provide cross-cutting support to built-in and specialized effector subclasses.
 * <br><br>
 * {@link Signal} enumerates the core signals used to control transduction processes. 
 * These resolve to out-of-band (&gt;256) inputs that can be pushed onto tranductor
 * input stacks. Additional signals can be declared in transducer patterns simply by including
 * them as <i>`!name`</i> parameters with unique names for the {@code in[`!name`]} 
 * effector in transducer patterns. Then <i>name</i> (without <i>`!...`</i> signal 
 * type indicator and backquotes) can be used on the transducer input tape to trigger
 * a transition when the signal pops to the top of the input stack.
 * @author Kim Briggs
 */
package com.characterforming.ribose.base;
