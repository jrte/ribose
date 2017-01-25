/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import com.characterforming.jrte.GearboxException;

/**
 * @author kb
 */
public final class Transducer {
	private final String name;
	private final String targetName;
	private final int[] inputFilter;
	private final int[][] transitionMatrix;
	private final int[] effectorVector;

	public Transducer(final String name, final String targetName, final int[] inputFilter, final int[][] transitionMatrix, final int[] effectorVector) throws GearboxException {
		this.name = name;
		this.targetName = targetName;
		this.inputFilter = inputFilter;
		this.transitionMatrix = transitionMatrix;
		this.effectorVector = effectorVector;
	}

	public String getName() {
		return this.name;
	}

	public String getTargetName() {
		return this.targetName;
	}

	public int[][] getTransitionMatrix() {
		return this.transitionMatrix;
	}

	public int[] getInputFilter() {
		return this.inputFilter;
	}

	public int[] getEffectorVector() {
		return this.effectorVector;
	}
}
