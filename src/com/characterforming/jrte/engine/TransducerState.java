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