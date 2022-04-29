package com.characterforming.jrte.engine;

final class Input {
	byte[] array;
	int position;
	int limit;
	int mark;
	int length;
	
	Input() {
		this.clear();
	}
	
	Input(byte[] array) {
		this.array = array;
		this.length = array != null ? array.length : -1;
		this.stop();
	}
	
	Input(Input input) {
		this.array = input.array;
		this.length = input.array.length;
		this.position = input.position;
		this.limit = input.limit;
		this.mark = input.mark;
	}
	
	void clear() {
		this.array = null;
		this.position = -1;
		this.limit = -1;
		this.mark = -1;
		this.length = 0;
	}
	
	boolean hasRemaining() {
		return this.position < this.length;
	}
	
	boolean hasMark() {
		return this.mark >= 0;
	}
	
	void limit(int limit) {
		this.limit = Math.min(limit, this.length);
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
		this.limit = this.length;
		this.rewind();
	}
	
	@Override
	public String toString() {
		return String.format("position:%d limit:%d mark:%d length:%d", 
			this.position, this.limit, this.mark, this.array != null ? this.array.length : -1);
	}
}
