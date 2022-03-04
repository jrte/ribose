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
 * @author kb
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
	NamedValue(final Bytes name, final int ordinal, final byte[] value, final int length) {
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
		System.arraycopy(namedValue.value, 0, this.value, 0, this.length);
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

	void setLength(int length) {
		assert length <= this.value.length;
		this.length = length;
	}

	byte[] getValue() {
		return this.value;
	}

	byte[] growValue(int minIncrement) {
		int increment = ((this.length * 5) >> 2) - this.length;
		this.value = Arrays.copyOf(this.value, Math.max(minIncrement, increment));
		return this.value;
	}

	void append(byte next) {
		assert this.value != null;
		if (this.length >= this.value.length) {
			this.value = Arrays.copyOf(this.value, (this.length * 5) >> 2);
		}
		this.value[this.length] = next;
		++this.length;
	}

	void append(byte next[]) {
		if (this.value == null) {
			this.value = new byte[Math.max(next.length, INITIAL_NAMED_VALUE_BYTES)];
			this.length = 0;
		} else if ((this.length + next.length) > this.value.length) {
			this.value = Arrays.copyOf(this.value, ((this.length + next.length) * 5) >> 2);
		}
		System.arraycopy(next, 0, this.value, this.length, next.length);
		this.length += next.length;
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
