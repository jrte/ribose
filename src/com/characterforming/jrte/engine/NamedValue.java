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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import com.characterforming.jrte.INamedValue;
import com.characterforming.jrte.base.Bytes;

/**
 * Wrapper for named value snapshots.
 * 
 * @author Kim Briggs
 */
class NamedValue implements INamedValue {
	private static final int INITIAL_NAMED_VALUE_BYTES = 256;
	
	private final Bytes name;
	private final int ordinal;

	private byte[] value;
	private int length;

	/**
	 * Constructor
	 */
	NamedValue(Bytes name, int ordinal, byte[] value, int length) {
		if (value == null) {
			value = new byte[Math.min(INITIAL_NAMED_VALUE_BYTES, length)];
		}
		assert value.length >= length;
		this.name = name;
		this.ordinal = ordinal;
		this.value = value;
		this.length = length;
	}

	NamedValue(NamedValue namedValue) {
		this.name = namedValue.name;
		this.ordinal = namedValue.ordinal;
		this.length = namedValue.length;
		this.value = new byte[this.length];
		System.arraycopy(namedValue.value, 0, this.value, 0, namedValue.length);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.INamedValue#getName()
	 */
	@Override
	public Bytes getName() {
		return this.name;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.INamedValue#getOrdinal()
	 */
	@Override
	public int getOrdinal() {
		return this.ordinal;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.INamedValue#getLength()
	 */
	@Override
	public int getLength() {
		return this.length;
	}
	
	@Override
	public byte[] copyValue() {
		return Arrays.copyOf(this.value, this.length);
	}

	@Override
	public char[] decodeValue() {
		CharBuffer buffer = Charset.defaultCharset().decode(ByteBuffer.wrap(this.value, 0, this.getLength()));
		char chars[] = new char[buffer.position()];
		buffer.get(chars);
		return chars;
	}

	void clear() {
		this.length = 0;
assert this.value.length <= 256 && this.length <= this.value.length;
	}

	byte[] getValue() {
		return this.value;
	}

	void growValue(int size) {
		if ((this.length + size) > this.value.length) {
			byte v[] = new byte[((this.length + size) * 5) >> 2];
			System.arraycopy(this.value, 0, v, 0, this.length);
			this.value = v;
		}
assert this.value.length <= 256 && this.length <= this.value.length;
	}

	void append(byte next) {
		assert this.value != null;
		growValue(1);
		this.value[this.length] = next;
		this.length += 1;
assert this.value.length <= 256 && this.length <= this.value.length;
	}

	void append(byte next[]) {
		assert this.value != null;
		growValue(next.length);
		System.arraycopy(next, 0, this.value, this.length, next.length);
		this.length += next.length;
assert this.value.length <= 256 && this.length <= this.value.length;
	}

	public void append(NamedValue next) {
		assert this.value != null;
		growValue(next.length);
		System.arraycopy(next.value, 0, this.value, this.length, next.length);
		this.length += next.length;
assert this.value.length <= 256 && this.length <= this.value.length;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		String value = this.value != null ? Charset.defaultCharset().decode(ByteBuffer.wrap(this.value, 0, this.length)).toString() : "null";
		return String.format("%s:%s", this.name.toString(), value);
	}
}
