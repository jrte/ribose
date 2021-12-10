/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile.array;

import java.nio.CharBuffer;
import java.util.Arrays;

/**
 * Wraps an array of char
 * 
 * @author kb
 */
public final class Chars {
	private final char[] chars;
	private int hash;

	public Chars(final char[] chars) {
		this.chars = chars;
		this.hash = 0;
	}

	public Chars(final CharBuffer chars) {
		this.chars = new char[chars.length()];
		chars.get(this.chars);
		this.hash = this.hash();
	}

	private int hash() {
		int h = this.chars.length;
		for (final char c : this.chars) {
			h = h * 31 + c;
		}
		return h != 0 ? h : -1;
	}

	public char[] getchars() {
		return this.chars;
	}

	@Override
	public int hashCode() {
		if (this.hash == 0) {
			this.hash = this.hash();
		}
		return this.hash;
	}

	@Override
	public boolean equals(final Object other) {
		return other == this || other != null && other instanceof Chars
				&& ((Chars) other).hashCode() == this.hashCode()
				&& Arrays.equals(((Chars) other).chars, this.chars);
	}

	@Override
	public String toString() {
		return new String(this.chars);
	}
}
