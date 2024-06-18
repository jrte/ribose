package com.characterforming.jrte.engine;

import java.nio.charset.CharacterCodingException;
import java.util.Arrays;

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;

class Value {
	private byte[] data;
	private int len;

	Value(int size) {
		this.data = new byte[Math.max(size, 256)];
		this.len = 0;
	}

	int length() { return this.len; }

	byte[] data() { return this.data; }

	void clear() { this.len = 0; }

	void paste(byte input) {
		if (this.len >= this.data.length) {
			this.data = Arrays.copyOf(this.data, this.len + (this.len >> 1));
		}
		this.data[this.len++] = input;
	}

	void paste(byte[] input, int length) {
		assert length <= input.length;
		int newLength = this.len + length;
		if (newLength > this.data.length) {
			this.data = Arrays.copyOf(this.data, newLength + (this.len >> 1));
		}
		System.arraycopy(input, 0, this.data, this.len, length);
		this.len = newLength;
	}

	void paste(Value value) {
		this.paste(value.data, value.length());
	}

	@Override
	public String toString() {
		try {
			return Codec.decode(this.data, this.len);
		} catch (CharacterCodingException e) {
			Bytes bytes = new Bytes(this.data, 0, this.len);
			return bytes.toHexString();
		}
	}
}