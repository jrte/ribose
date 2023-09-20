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
package com.characterforming.ribose.base;

import com.characterforming.ribose.ITransductor;

/**
 * General signals available in all ribose models.
 * Signal ordinal values are mapped to the end of the base {@code (0x0..0xff)} 
 * input range. {@code Signal} represents the predefined signals control signals
 * {@code nul nil eol eos}. Additional signal tokens (`!&lt;token&gt;`) declared
 * in model transduction patterns will be mapped to {@code [0x104, ..)}.
 * <br><br>
 * All transducers should recognize a {@code nil} signal as a prologue. This can be
 * ignored ({@code nil?}) if not required to trigger an initial action before 
 * receiving input from a data stream. Transductors send {@code nul} to the running
 * transducer when no transition is defined for the current symbol from the data 
 * input stream. This gives the transduction a first chance to synchronize with 
 * the input. If no transition is defined for the {@code nul} signal the {@link
 * ITransductor#run()} method will throw {@code DomainErrorException}. 
 * <br><br>
 * Transductors send {@code eos} to transductions when the input stack runs dry. 
 * If the receiving transduction has no transition defined for received {@code eos}
 * it is ignored. In any case {@code run()} will return normally with {@code Status.PAUSED}
 * after sending {@code eos}.
 * <br><br>
 * Specialized ribose models may introduce additional signals simply by using them
 * with the {@code signal[`!x`]} effector or any other effector. The signal 
 * symbol (stripped of the ! prefix) can then be recognized as an input. For example,
 * <br><pre>(nl, signal[`!x`]) (x, switch[`!door1` `!door2`]) ((door1, enter) | (door2, exit))</pre>
 * In this case, the {@code x} signal is explicitly raised via the {@code signal[]}
 * effector and the {@code switch} effector injects either the {@code door1} or
 * {@code door2} signal, as determined by the target's switch effector, by encoding the
 * signal in the integer code returned from {@code SwitchEffector.invoke(int})}. 
 * 
 * @author kb
 *
 */
public enum Signal {
	/** Signals first chance to handle missing transition on current input symbol */
	NUL(new byte[] { 'n', 'u', 'l' }, 256), 
	/** Signals anything, used as a generic out-of-band prompt to trigger actions */
	NIL(new byte[] { 'n', 'i', 'l' }, 257), 
	/** Signals end of feature, used as a generic feature delimiter */
	EOL(new byte[] { 'e', 'o', 'l' }, 258),
	/** Signals end of transduction input */
	EOS(new byte[] { 'e', 'o', 's' }, 259),
	/** Signals nothing, for optionality (use as null) */
	NONE(new byte[] {}, -1);
	
	private final Bytes sym;
	private final Bytes ref;
	private final int sig;
	
	private Signal(byte[] symbol, int signal) {
		byte[] signalReference = new byte[symbol.length + 1];
		System.arraycopy(symbol, 0, signalReference, 1, symbol.length);
		signalReference[0] = '!';
		this.sym = new Bytes(symbol);
		this.ref = new Bytes(signalReference);
		this.sig = signal;
	}
	
	/**
	 * Signal name, as input symbol (tape 0).
	 * 
	 * @return the signal name as lookup symbol
	 */
	public Bytes symbol() {
		return this.sym;
	}

	/**
	 * Signal name, as effector parameter token (tape 2).
	 * 
	 * @return the signal name as reference symbol
	 */
	public Bytes reference() {
		return this.ref;
	}

	/**
	 * Get the input ordinal value represented by this {@code Signal}
	 * 
	 * @return the ordinal value represented by {@code signal}
	 */
	public int signal() {
		return this.sig;
	}

	/**
	 * Check for {@code Signal.NONE}
	 * 
	 * @return true if this == {@code Signal.NONE}
	 */
	public boolean isNone() {
		return this == NONE;
	}
}