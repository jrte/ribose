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

import java.util.HashMap;
import java.util.Map.Entry;

import com.characterforming.ribose.base.Bytes;

/**
 * @author Kim Briggs
 */
final class Transducer {
	private final int ordinal;
	private final String name;
	private final Bytes[] fieldNames;
	private final int[] inputFilter;
	private final long[] transitionMatrix;
	private final int[] effectorVector;
	private final int inputEquivalents;

	Transducer(
		String name,
		int ordinal,
		HashMap<Bytes, Integer> fieldMap,
		int[] inputFilter,
		long[] transitionMatrix,
		int[] effectorVector
	) {
		this.name = name;
		this.ordinal = ordinal;
		this.fieldNames = new Bytes[fieldMap.size()];
		for (Entry<Bytes, Integer> e : fieldMap.entrySet())
			this.fieldNames[e.getValue()] = e.getKey();
		this.inputFilter = inputFilter;
		this.transitionMatrix = transitionMatrix;
		this.effectorVector = effectorVector;
		int max = -1;
		for (int eq : this.inputFilter)
			if (eq > max)
				max = eq;
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
		return  ((parameter + 1) << 16) | effect;
	}

	// decode effector ordinal from action commponent of transition matrix cell
	static int effector(int action) {
		return action & 0xffff;
	}

	// decode parameter ordinal from action commponent of transition matrix cell
	static int parameter(int action) {
		return (action >>> 16) - 1;
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

	long[] transitionMatrix() {
		return this.transitionMatrix;
	}

	int[] inputFilter() {
		return this.inputFilter;
	}

	int[] effectorVector() {
		return this.effectorVector;
	}

	Bytes getFieldName(int fieldOrdinal) {
		if (fieldOrdinal >= 0 && fieldOrdinal < this.fieldNames.length)
			return this.fieldNames[fieldOrdinal];
		return null;
	}

	int getFieldCount() {
		return this.fieldNames.length;
	}

	int getInputEquivalentsCount() {
		return this.inputEquivalents;
	}
}
