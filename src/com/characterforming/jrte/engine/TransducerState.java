/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

class TransducerState {
	Transducer transducer;
	int[] countdown;
	int state;

	TransducerState(final int state, final Transducer transducer) {
		this.state = state;
		this.transducer = transducer;
		this.countdown = new int[2];
	}

	void reset(final int state, final Transducer transducer) {
		this.state = state;
		this.transducer = transducer;
		this.countdown[0] = 0;
		this.countdown[1] = 0;
	}

	@Override
	public String toString() {
		return this.transducer != null ? this.transducer.getName() : "empty";
	}
}