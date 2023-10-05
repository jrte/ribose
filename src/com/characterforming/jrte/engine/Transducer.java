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
	private final int ordinal;
	private final String name;
	private final int[] fields;
	private final int[] inputFilter;
	private final long[] transitionMatrix;
	private final int[] effectorVector;
	private final int inputEquivalents;

	Transducer(
		String name,
		int ordinal,
		int[] fields,
		int[] inputFilter,
		long[] transitionMatrix,
		int[] effectorVector
	) {
		this.name = name;
		this.ordinal = ordinal;
		this.fields = fields;
		this.inputFilter = inputFilter;
		this.transitionMatrix = transitionMatrix;
		this.effectorVector = effectorVector;
		int max = -1;
		for (int eq : this.inputFilter) {
			if (eq > max) {
				max = eq;
			}
		}
		this.inputEquivalents = max + 1;
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

	// decode signal from RTX_SIGNAL code from an effector 
	static int signal(int rtxSignal) {
		return rtxSignal >>> 16;
	}

	int getOrdinal() {
		return this.ordinal;
	}

	String getName() {
		return this.name;
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

	int[] getFields() {
		return this.fields;
	}

	int getFieldCount() {
		return this.fields.length;
	}
	
	int getInputEquivalentsCount() {
		return this.inputEquivalents;
	}
}
