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
		this.length = array != null ? array.length : 0;
		this.position = this.limit = 0;
		this.mark = -1;
}
	
	Input(Input input) {
		this.copy(input);
	}

  static Input[] stack(final int initialSize) {
  	Input stack[] = new Input[initialSize];
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
	
	boolean hasRemaining() {
		return this.position < this.limit;
	}
	
	boolean hasMark() {
		return this.mark >= 0;
	}
	
	Input limit(int limit) {
		assert limit >= 0;
		this.limit = Math.min(limit, this.length);
		return this;
	}
	
	void mark() {
		assert hasRemaining();
		this.mark = this.position;
	}
	
	void reset() {
		if (this.hasMark()) {
			this.position = this.mark;
			this.mark = -1;
		}
	}
	
	void rewind() {
		this.position = 0;
		this.mark = -1;
	}
	
	void stop() {
		this.rewind();
	}
	
	@Override
	public String toString() {
		return String.format("[array:%s] position:%d limit:%d mark:%d length:%d limit:%d", 
			this.array.toString(), this.position, this.limit, this.mark, this.length, this.limit);
	}

	@Override
	public boolean equals(Object input) {
		if (input != null && input instanceof Input) {
			Input other = (Input)input;
			return this.array == other.array
			&& this.position == other.position
			&& this.limit == other.limit;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return this.array != null ? this.array.hashCode() : 0;
	}
}
