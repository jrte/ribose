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

/**
 * @author Kim Briggs
 */
final class Transducer {
	private final String name;
	private final String targetName;
	private final int[] inputFilter;
	private final long[] transitionMatrix;
	private final int[] effectorVector;
	int inputEquivalents;

	Transducer() {
		this.name = null;
		this.targetName = null;
		this.inputFilter = null;
		this.transitionMatrix = null;
		this.effectorVector = null;
		this.inputEquivalents = 0;
	}

	Transducer(String name, String targetName, int[] inputFilter, long[] transitionMatrix, int[] effectorVector) {
		this.name = name;
		this.targetName = targetName;
		this.inputFilter = inputFilter;
		this.transitionMatrix = transitionMatrix;
		this.effectorVector = effectorVector;
		this.inputEquivalents = 0;
	}

	// encode state and action in transition matrix cell
	static long transition(int state, int action) {
		return ((long)action << 32) | state;
	}

	// decode state from transition matrix cell
	static int state(long transition) {
		return (int)(transition & 0xffffffff);
	}

	// decode action from transition matrix cell
	static int action(long transition) {
		return (int)(transition >>> 32);
	}

	// encode effector and paramter ordinals in action commponent of transition matrix cell
	static int action(int effect, int parameter) {
		return  (effect << 16) | parameter;
	}

	// decode effector ordinal from action commponent of transition matrix cell
	static int effector(int action) {
		return action >>> 16;
	}

	// decode parameter ordinal from action commponent of transition matrix cell
	static int parameter(int action) {
		return action & 0xffff;
	}

	boolean isLoaded() {
		return null != this.name;
	}

	String getName() {
		return this.name;
	}

	String getTargetName() {
		return this.targetName;
	}

	long[] getTransitionMatrix() {
		return this.transitionMatrix;
	}

	int[] getInputFilter() {
		return this.inputFilter;
	}

	int[] getEffectorVector() {
		return this.effectorVector;
	}
	
	int getInputEquivalentsCount() {
		if (this.inputEquivalents == 0) {
			int max = 0;
			for (int equiv : this.inputFilter) {
				if (equiv > max) {
					max = equiv;
				}
			}
			this.inputEquivalents = max + 1;
		}
		return this.inputEquivalents;
	}
}
