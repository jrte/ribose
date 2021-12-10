/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile;

final class Chain {
	private final int[] effectVector;
	private final int outS;

	public Chain(final int[] effectVector, final int outS) {
		this.effectVector = effectVector;
		this.outS = outS;
	}

	public int[] getEffectVector() {
		return this.effectVector;
	}

	public int getOutS() {
		return this.outS;
	}

	public boolean isScalar() {
		return this.effectVector.length == 1;
	}

	public boolean isEmpty() {
		return this.effectVector.length == 0;
	}
}
