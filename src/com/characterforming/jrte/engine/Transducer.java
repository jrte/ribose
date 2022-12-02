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

import com.characterforming.ribose.base.ModelException;

/**
 * @author Kim Briggs
 */
final class Transducer {
	private final String name;
	private final String targetName;
	private final int[] inputFilter;
	private final int[][] transitionMatrix;
	private final int[] effectorVector;
	int inputEquivalents;

	Transducer(final String name, final String targetName, final int[] inputFilter, final int[][] transitionMatrix, final int[] effectorVector) throws ModelException {
		this.name = name;
		this.targetName = targetName;
		this.inputFilter = inputFilter;
		this.transitionMatrix = transitionMatrix;
		this.effectorVector = effectorVector;
		this.inputEquivalents = 0;
	}

	String getName() {
		return this.name;
	}

	String getTargetName() {
		return this.targetName;
	}

	int[][] getTransitionMatrix() {
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
