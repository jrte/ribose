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

final class TransducerState {
	int countdown;
	int signal;
	int state;
	int[] inputFilter;
	long[] transitionMatrix;
	int[] effectorVector;
	int inputEquivalents;
	String name;

	TransducerState() {
		this.reset();
		this.name = null;
		this.inputFilter = null;
		this.transitionMatrix = null;
		this.effectorVector = null;
		this.inputEquivalents = 0;
	}

	TransducerState transducer(Transducer transducer) {
		this.reset();
		this.name = transducer.getName();
		this.inputFilter = transducer.getInputFilter();
		this.transitionMatrix = transducer.getTransitionMatrix();
		this.effectorVector = transducer.getEffectorVector();
		this.inputEquivalents = transducer.getInputEquivalentsCount();
		return this;
	}

	void reset() {
		this.state = 0;
		this.countdown = 0;
		this.signal = -1;
}

	@Override
	public String toString() {
		return this.name != null ? this.name : "empty";
	}
}