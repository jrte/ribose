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
 * encoded text and/or binary data. The {@link Bytes} wrapper class and {@link Base}
 * utility class are used extensively throughout the ribose compiler and runtime.
 * The base classes {@link BaseEffector} and {@link  BaseParameterizedEffector}
 * provide cross-cutting support to all effector subclasses. The {@link SimpleTarget}
 * provides a target that exposes the built-in {@link ITransductor} effectors and
 * can be used to construct simple models that do not require specialized effectors.
 * <br><br>
 * Signals are out-of-band (&gt;256) inputs that are injected into transductor
 * input for immediate transduction. {@link Signal} enumerates the core signals used
 * to control transduction processes, additional signals can be defined by referencing
 * them as effector parameters (eg, <i>(nl, signal[`!name`]</i>). It is considered an
 * error if a signal so defined is not referenced as an input (eg, <i>(name, stop)</i>)
 * in at least one transducer.
 * @author Kim Briggs
 */
package com.characterforming.ribose.base;

import com.characterforming.ribose.ITransductor;
import com.characterforming.jrte.engine.Base;
