package com.characterforming.jrte.engine;

final class Input {
	byte[] array;
	int position;
	int limit;
	int mark;
	int length;
	
	static final Input empty = new Input();

	Input() {
		this.clear();
	}
	
	Input(byte[] array) {
		this.array = array;
		this.length = array != null ? array.length : -1;
		this.stop();
	}
	
	Input(Input input) {
		this.copy(input);
	}

  static Input[] stack(final int initialSize) {
  	Input[] stack = new Input[initialSize];
  	for (int i = 0; i < stack.length; i++) {
  		stack[i] = new Input();
  	}
  	return stack;
  }
	
	Input copy(Input input) {
		this.array = input.array;
		this.length = input.array.length;
		this.position = input.position;
		this.limit = input.limit;
		this.mark = input.mark;
		return this;
	}
	
	void clear() {
		this.array = null;
		this.position = 0;
		this.limit = 0;
		this.mark = -1;
		this.length = 0;
	}

	void clear(byte[] input) {
		this.clear();
		this.array = input;
		this.limit = this.length = input.length;
	}

	boolean hasRemaining() {
		return this.position < this.limit;
	}
	
	boolean hasMark() {
		return this.mark >= 0;
	}
	
	Input getLimit(int limit) {
		assert limit >= 0;
		this.limit = Math.min(limit, this.length);
		return this;
	}
	
	void setMark() {
		assert hasRemaining();
		this.mark = this.position;
	}
	
	void resetToMark() {
		if (this.hasMark()) {
			this.position = this.mark;
			this.mark = -1;
		}
	}
	
	void rewind() {
		this.position = this.length >= 0 ? 0 : -1;
		this.mark = -1;
	}
	
	void stop() {
		this.rewind();
	}
	
	@Override
	public String toString() {
		return String.format("position:%d limit:%d mark:%d length:%d [array:%d]", 
			this.position, this.limit, this.mark, this.length, this.array != null ? this.array.length : -1);
	}
}
