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

import com.characterforming.ribose.IField;
import com.characterforming.ribose.base.Bytes;

/**
 * Wrapper for field snapshots.
 * 
 * @author Kim Briggs
 */
final class Field implements IField {
	private static final int INITIAL_FIELD_VALUE_BYTES = 256;
	
	private final Bytes name;
	private final int ordinal;

	private byte[] data;
	private int length;

	private final CharsetDecoder decoder;

	/**
	 * Constructor
	 * @param charset
	 */
	Field(Bytes name, int ordinal, byte[] value, int length) {
		if (value == null) {
			value = new byte[Math.min(INITIAL_FIELD_VALUE_BYTES, length)];
		}
		assert value.length >= length;
		this.decoder = Base.newCharsetDecoder();
		this.name = name;
		this.ordinal = ordinal;
		this.data = value;
		this.length = length;
	}

	Field(Field field) {
		this.name = field.name;
		this.ordinal = field.ordinal;
		this.length = field.length;
		this.data = new byte[this.length];
		this.decoder = Base.newCharsetDecoder();
		System.arraycopy(field.data, 0, this.data, 0, field.length);
	}

	@Override // @see com.characterforming.ribose.IField#getName()
	public Bytes getName() {
		return this.name;
	}

	@Override // @see com.characterforming.ribose.IField#getOrdinal()
	public int getOrdinal() {
		return this.ordinal;
	}

	@Override // @see com.characterforming.ribose.IField#getLength()
	public int getLength() {
		return this.length;
	}
	
	@Override // @see com.characterforming.ribose.IField#copyValue()
	public byte[] copyValue() {
		return Arrays.copyOf(this.data, this.length);
	}

	@Override // @see com.characterforming.ribose.IField#decodeValue()
	public char[] decodeValue() {
		char[] chars = null;
		ByteBuffer in = ByteBuffer.wrap(this.data, 0, this.getLength());
		CharBuffer out = CharBuffer.allocate(this.getLength());
		CoderResult result = this.decoder.reset().decode(in, out, true);
		assert !result.isOverflow() && !result.isError();
		if (!result.isOverflow() && !result.isError()) {
			chars = new char[out.flip().length()];
			out.get(chars);
		}
		return chars;
	}

	@Override // @see com.characterforming.ribose.IField#asString()
	public String asString() {
		return new String(this.decodeValue());
	}
	
	@Override // @see com.characterforming.ribose.IField#asInteger()
	public long asInteger() {
		long value = 0;
		long sign = (this.data[0] == '-') ? -1 : 1;
		for (int i = sign > 0 ? 0 : 1; i < this.length; i++) {
			if (Character.getType(this.data[i]) == Character.DECIMAL_DIGIT_NUMBER) {
				value = (10 * value) + (this.data[i] - 48);
			} else {
				throw new NumberFormatException(String.format(
					"Not a numeric value '%1$s'", this.toString())); 
			}
		}
		return sign * value;
	}
	
	@Override // @see com.characterforming.ribose.IField#asReal()
	public double asReal() {
		long value = 0;
		boolean mark = false;
		double fraction = (this.data[0] == '-') ? -1.0 : 1.0;
		for (int i = fraction < 0.0 ? 1 : 0; i < this.length; i++) {
			byte digit = this.data[i];
			if (Character.getType(digit) == Character.DECIMAL_DIGIT_NUMBER) {
				value = (10 * value) + (digit - 48);
				if (mark) {
					fraction /= 10.0;
				}
			} else if (digit == '.') {
				mark = true;
			} else {
				throw new NumberFormatException(String.format(
					"Not a floating point value '%1$s'", this.toString())); 
			}
		}
		return fraction * value;
	}

	void clear() {
		this.length = 0;
	}

	byte[] getData() {
		return this.data;
	}

	void append(byte next) {
		assert this.data != null;
		growValue(1);
		this.data[this.length] = next;
		this.length += 1;
	}

	void append(byte[] next) {
		assert this.data != null;
		growValue(next.length);
		System.arraycopy(next, 0, this.data, this.length, next.length);
		this.length += next.length;
	}

	void append(IField next) {
		Field field = (Field)next;
		growValue(field.length);
		System.arraycopy(field.data, 0, this.data, this.length, field.length);
		this.length += field.length;
	}

	private void growValue(int size) {
		if ((this.length + size) > this.data.length) {
			byte[] v = new byte[((this.length + size) * 5) >> 2];
			System.arraycopy(this.data, 0, v, 0, this.length);
			this.data = v;
		}
	}

	@Override // @see java.lang.Object#toString()
	public String toString() {
		return Bytes.decode(this.decoder, data, length).toString();
	}
}
