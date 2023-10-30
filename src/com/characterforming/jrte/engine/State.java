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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 *
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.	See
 * LICENSE-gpl-3.0. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.util.HashSet;

import com.characterforming.ribose.base.Signal;

class State {
	final int ordinal;
	final int signalLimit;
	final int[][] transitions;
	final long[] idempotentBytes;
	final HashSet<Integer>[] inputEquivalents;
	int idempotentCount;
	int idempotentNulBytes;
	int idempotentByteCount;
	int outboundCount;
	int outboundByte;
	int outboundEq;

	State(int state, int[][] transitions, HashSet<Integer>[] inputEquivalents, int signalLimit) {
		this.ordinal = state;
		this.signalLimit = signalLimit;
		this.transitions = transitions;
		this.inputEquivalents = inputEquivalents;
		this.idempotentNulBytes = 0;
		this.idempotentBytes = new long[] { 0, 0, 0, 0 };
		this.idempotentByteCount = 0;
		this.idempotentCount = 0;
		this.outboundEq = -1;
		this.outboundByte = -1;
		this.outboundCount = 0;
		this.setup();
	}

	private void setup() {
		for (int eq = 0; eq < this.transitions.length; eq++)
			if (this.transitions[eq][0] == this.ordinal) {
				this.idempotentCount += this.inputEquivalents[eq].size();
				for (int token : this.inputEquivalents[eq])
					if (token < Signal.NUL.signal()) {
						if (this.transitions[eq][1] == Assembler.NIL) {
							this.idempotentBytes[token >> 6] |= (1L << (token & 0x3f));
							this.idempotentByteCount += 1;
						} else if (this.transitions[eq][1] == Assembler.NUL)
							this.idempotentNulBytes += 1;
					}
			} else {
				final int size = this.inputEquivalents[eq].size();
				int token = this.inputEquivalents[eq].iterator().next().intValue();
				if (size == 1 && this.outboundCount == 0
				&& this.transitions[eq][1] == Assembler.NIL
				&& token < Signal.NUL.signal()) {
						this.outboundByte = token;
						this.outboundCount = 1;
						this.outboundEq = eq;
				} else
					for (int t : this.inputEquivalents[eq])
						if (t < Signal.NUL.signal())
							++this.outboundCount;
			}
		if (this.isScanState())
			for (int word = 0; word < this.idempotentBytes.length; word++)
				if (this.idempotentBytes[word] != 0xffffffffffffffffL)
					for (int bit = 0; bit < 64; bit++)
						if (0 == ((1L<<bit) & this.idempotentBytes[word])) {
							this.outboundByte = (word << 6) | bit;
							break;
						}
		assert !this.isScanState() || !this.isProduct() && this.outboundByte >= 0;
	}

	private boolean isProduct() {
		return this.outboundCount == 1 && this.idempotentNulBytes == 255
		&& this.outboundEq >= 0 && this.transitions[this.outboundEq][1] == Assembler.NIL
		&& this.outboundByte >= 0 && this.outboundByte < Signal.NUL.signal();
	}

	boolean isScanState() {
		assert this.idempotentByteCount != 255
		|| (!this.isSumState() && !this.isProduct());
		return this.idempotentByteCount == 255;
	}

	boolean isSumState() {
		assert this.idempotentByteCount < ModelCompiler.MIN_SUM_SIZE || this.idempotentByteCount >= 255
		|| (!this.isScanState() && !this.isProduct());
		return this.idempotentByteCount >= ModelCompiler.MIN_SUM_SIZE
		&& this.idempotentByteCount < 255;
	}

	boolean isProductState() {
		assert !isProduct() || (!this.isScanState() && !this.isSumState());
		return isProduct();
	}

	boolean isNotInstrumented() {
		return !this.isScanState() && !this.isSumState() && !this.isProduct();
	}
}