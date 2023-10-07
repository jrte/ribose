package com.characterforming.jrte.engine;

import java.util.Arrays;

class Value {
	private byte[] val;
	private int len;

	Value(int size) {
		this.val = new byte[Math.max(size, 16)];
		this.len = 0;
	}

	int length() { return this.len; }

	byte[] value() { return this.val; }

	void clear() { this.len = 0; }

	void paste(byte input) {
		if (this.len >= this.val.length) {
			this.val = Arrays.copyOf(this.val, this.len + (this.len >> 1));
		}
		this.val[this.len++] = input;
	}

	void paste(byte[] input, int length) {
		assert length <= input.length;
		int newLength = this.len + length;
		if (newLength > this.val.length) {
			this.val = Arrays.copyOf(this.val, newLength + (this.len >> 1));
		}
		System.arraycopy(input, 0, this.val, this.len, length);
		this.len = newLength;
	}

	void paste(Value value) {
		this.paste(value.val, value.length());
	}
}