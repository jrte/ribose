/**
 * 
 */
package com.characterforming.jrte.engine;

import com.characterforming.jrte.INamedValue;

/**
 * Wrapper for named value snapshots.
 * 
 * @author kb
 */
public class NamedValue implements INamedValue {
	private final String name;
	private final int index;

	private char[] value;
	private int length;

	/**
	 * Constructor
	 */
	public NamedValue(final String name, final int index, final char[] value, final int length) {
		this.name = name;
		this.index = index;
		this.value = value;
		this.length = length;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.INamedValue#getName()
	 */
	public String getName() {
		return this.name;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.INamedValue#getIndex()
	 */
	public int getIndex() {
		return this.index;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.INamedValue#getValue()
	 */
	public char[] getValue() {
		return this.value;
	}

	void setValue(final char[] value) {
		this.value = value;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.INamedValue#getLength()
	 */
	public int getLength() {
		return this.length;
	}

	void setLength(final int length) {
		this.length = length;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return String.format("%s:%s", this.name, this.value != null ? this.value : "null");
	}
}
