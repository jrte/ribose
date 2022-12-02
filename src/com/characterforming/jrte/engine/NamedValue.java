/***
 * Ribose is a recursive transduction engine for Java
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
 * You should have received a copy of the GNU General Public License
 * along with this program (LICENSE-gpl-3.0). If not, see
 * <http://www.gnu.org/licenses/#GPL>.
 */

package com.characterforming.jrte.engine;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;

import com.characterforming.ribose.INamedValue;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Bytes;

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

	private final CharsetDecoder decoder;

	/**
	 * Constructor
	 * @param charset
	 */
	NamedValue(Bytes name, int ordinal, byte[] value, int length) {
		if (value == null) {
			value = new byte[Math.min(INITIAL_NAMED_VALUE_BYTES, length)];
		}
		assert value.length >= length;
		this.decoder = Base.newCharsetDecoder();
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
		this.decoder = Base.newCharsetDecoder();
		System.arraycopy(namedValue.value, 0, this.value, 0, namedValue.length);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.INamedValue#getName()
	 */
	@Override
	public Bytes getName() {
		return this.name;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.INamedValue#getOrdinal()
	 */
	@Override
	public int getOrdinal() {
		return this.ordinal;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.INamedValue#getLength()
	 */
	@Override
	public int getLength() {
		return this.length;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.INamedValue#copyValue()
	 */
	@Override
	public byte[] copyValue() {
		return Arrays.copyOf(this.value, this.length);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.INamedValue#decodeValue()
	 */
	@Override
	public char[] decodeValue() {
		char chars[] = null;
		ByteBuffer in = ByteBuffer.wrap(this.value, 0, this.getLength());
		CharBuffer out = CharBuffer.allocate(this.getLength());
		CoderResult result = this.decoder.decode(in, out, true);
		assert !result.isOverflow() && !result.isError();
		if (!result.isOverflow() && !result.isError()) {
			chars = new char[out.length()];
			out.get(chars);
		}
		return chars;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.INamedValue#asString()
	 */
	@Override
	public String asString() {
		return new String(this.decodeValue());
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.INamedValue#asInteger()
	 */
	@Override
	public long asInteger() {
		long integer = 0;
		long sign = (this.value[0] == '-') ? -1 : 1;
		for (int i = ((sign > 0) ? 0 : 1); i < this.length; i++) {
			if (Character.getType(this.value[i]) == Character.DECIMAL_DIGIT_NUMBER) {
				integer *= 10;
				integer += (this.value[i] - 48);
			} else {
				throw new NumberFormatException(String.format(
					"Not a numeric value '%1$s'", this.toString())); 
			}
		}
		return sign * integer;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.INamedValue#asReal()
	 */
	@Override
	public double asReal() {
		int mark = 0;
		long sign = (this.value[0] == '-') ? -1 : 1;
		int index[] = new int[] {(sign > 0) ? 0 : 1, 0};
		int parts[] = new int[] {0, 0};
		while (index[0] < this.length) {
			if (Character.getType(this.value[index[0]]) == Character.DECIMAL_DIGIT_NUMBER) {
				parts[index[1]] *= 10;
				parts[index[1]] += (this.value[index[0]] - 48);
			} else if (this.value[index[0]] == '.') {
				mark = index[0];
				index[1] = 1;
			} else {
				throw new NumberFormatException(String.format(
					"Not a floating point value '%1$s'", this.toString())); 
			}
			index[0] += 1;
		}
		double real = parts[0];
		if (this.length > mark) {
			double fraction = 1.0 / Math.pow(10, this.length - mark);
			real += parts[1] * fraction;
		}
		return sign * real;
	}

	void clear() {
		this.length = 0;
	}

	byte[] getValue() {
		return this.value;
	}

	void append(byte next) {
		assert this.value != null;
		growValue(1);
		this.value[this.length] = next;
		this.length += 1;
	}

	void append(byte next[]) {
		assert this.value != null;
		growValue(next.length);
		System.arraycopy(next, 0, this.value, this.length, next.length);
		this.length += next.length;
	}

	void append(NamedValue next) {
		assert this.value != null;
		growValue(next.length);
		System.arraycopy(next.value, 0, this.value, this.length, next.length);
		this.length += next.length;
	}

	private void growValue(int size) {
		if ((this.length + size) > this.value.length) {
			byte v[] = new byte[((this.length + size) * 5) >> 2];
			System.arraycopy(this.value, 0, v, 0, this.length);
			this.value = v;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return String.format("%s:%s", this.name.toString(), this.asString());
	}
}
