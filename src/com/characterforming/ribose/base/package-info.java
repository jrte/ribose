/*
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
 * LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

/**
 * Ribose integration base classes for extension into other domains. 
 * The ribose compiler binds domain-specific {@link com.characterforming.ribose.ITarget}
 * classes and associated {@link com.characterforming.ribose.IEffector} implementations
 * to collections of transducers in a ribose runtime file. In the ribose runtime applications 
 * instantiate target instances and bind them to runtime transductors to drive data through
 a stacked composition of transducers and assimilate information into the target.
 *
 * @author Kim Briggs
 */
package com.characterforming.ribose.base;
