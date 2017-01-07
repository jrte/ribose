/**
 * 
 */
package com.characterforming.jrte;

/**
 * Snapshot wrapper for named values. These contain a direct reference
 * to the value buffer as of the time of the call, and should be treated
 * as transient objects (assign to stack variables, use in stack based
 * collections, etc, only).
 * 
 * @author kb
 */
public interface INamedValue {
	/**
	 * Get the value name
	 * 
	 * @return The value name
	 */
	public String getName();

	/**
	 * Get the value index.
	 * 
	 * @return The value index
	 */
	public int getIndex();

	/**
	 * Get a reference to the value. Use {@link #getLength()} to determine the
	 * actual number of <code>char</code> in the returned array reference.
	 * 
	 * @return A direct reference to the value array as of the time of the call
	 */
	public char[] getValue();

	/**
	 * Get the actual number of <code>char</code> in the value array as of the
	 * time of the call
	 * 
	 * @return The actual number of <code>char</code> in the value array as of
	 *         the time of the call
	 */
	public int getLength();
}
