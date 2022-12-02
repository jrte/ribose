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
 * with the {@code in[`!signal`]} effector or any other effector, The signal 
 * symbol (stripped of the ! prefix) can then be recogized as an input. For exanple,
 * <br><br>
 * <pre>
 * (nl, switch[`!door1` `!door2`]) ((door1, enter) | (door2, exit))
 * </pre>
 * 
 * @author kb
 *
 */
public enum Signal {
	/**
	 * Signals first chance to handle missing transition on current input symbol
	 */
	nul, 
	/**
	 * Signals anything, used as a generic out-of-band prompt to trigger actions 
	 */
	nil, 
	/**
	 * Signals end of feature, used as a generic feature delimiter
	 */
	eol,
	/**
	 * Signals end of transduction input
	 */
	eos;
	
	/**
	 * Signal name.
	 * 
	 * @return The signal name as lookup key
	 */
	public Bytes key() { return Base.RTE_SIGNAL_NAMES[this.ordinal()]; }
	
	/**
	 * Signal ordinal values are mapped to the end of the base {@code (0x0..0xff)} 
	 * input range. This range can be further extended with additional signal 
	 * ordinal values defined for domain-specific transduction models.
	 *  
	 * @return the signal ordinal value
	 */
	public int signal() {
		return Base.RTE_SIGNAL_BASE + ordinal();
	}
}