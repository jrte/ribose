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
