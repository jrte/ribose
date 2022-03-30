/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import com.characterforming.jrte.GearboxException;

/**
 * @author Kim Briggs
 */
final class Transducer {
	private final String name;
	private final String targetName;
	private final int[] inputFilter;
	private final int[][] transitionMatrix;
	private final int[] effectorVector;

	Transducer(final String name, final String targetName, final int[] inputFilter, final int[][] transitionMatrix, final int[] effectorVector) throws GearboxException {
		this.name = name;
		this.targetName = targetName;
		this.inputFilter = inputFilter;
		this.transitionMatrix = transitionMatrix;
		this.effectorVector = effectorVector;
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
}
