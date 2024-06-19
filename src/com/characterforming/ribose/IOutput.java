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

package com.characterforming.ribose;

import java.nio.charset.CharacterCodingException;
import java.util.logging.Logger;

import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.BaseReceptorEffector;

/**
 * Provides {@link IEffector} implementations with loggers and data transfer methods
 * for extracting transducer fields to java primitives. Effectors receive their {@code
 * IOutput} instance via {@link IEffector#setOutput(IOutput)} when they are first bound
 * to a transductor. Transducer fields are local and bound to the defining transducer
 * and can be accessed by stable field ordinal numbers <i>when the transducer is topmost
 * on the transductor stack</i>. Every transducer has an anonymous field with ordinal
 * number 0 (the anonymous name token (`~`) in transducer patterns). Ordinal numbers
 * for named fields (referenced with a <b>~</b> field type prefix in patterns, eg,
 * {@code out[`~data`]}) are obtained by providing defining transducer and field names
 * to {@link #getTransducerFieldndex(String, String)}. Effectors should obtain field
 * ordinals in {@code setOutput()} and retain them for use with the data transfer methods
 * ({@link #asInteger(int, int)}, etc) in {@link IEffector#invoke()} and {@link
 * IParametricEffector#invoke(int)} as shown in the example below (this example is
 * implemented more succinctly as a receptor effector -- see below).
 * <br><pre>
 * record Header (int version, int tapes, int transitions, int states, int symbols) {}
 *
 * final class HeaderEffector extends BaseEffector&lt;ModelCompiler&gt; {
 *   private static final String transducerName = "Automaton");
 *   private static final String[] fieldNames = new String[] {
 *     "version", "tapes", "transitions", "states", "symbols"
 *   };
 *   private final int[] fields;
 *
 *   HeaderEffector(ModelCompiler compiler) {
 *     super(compiler, "header");
 *     this.fields = new int[HeaderEffector.fieldNames.length];
 *   }
 *
 *   &#64;Override // called once per proxy (compile) or live (run) transductor
 *   public void setOutput(IOutput output) throws EffectorException {
 *     super.setOutput(output);
 *     for (int i = 0; i &lt; this.fields.length; i++) {
 *       this.fields[i] = super.output.getTransducerFieldOrdinal(transducerName, fieldNames[i]);
 *     }
 *   }

 *   &#64;Override // called from transducer running on top of live transductor's stack
 *   public int invoke() throws EffectorException {
 *     ModelCompiler.this.putHeader(new Header(
 *       (int)super.output.asInteger(fields[0], -1),
 *       (int)super.output.asInteger(fields[1], -1),
 *       (int)super.output.asInteger(fields[2], -1),
 *       (int)super.output.asInteger(fields[3], -1),
 *       (int)super.output.asInteger(fields[4], -1)));
 *     return IEffector.RTX_NONE;
 *   }
 * }</pre>
 * All effectors receive with {@code IOutput} identical instances of thread-safe loggers,
 * which are shared by the transductor and its effectors. The compiler logger is exposed
 * here for use by proxy effectors in compilation contexts and should not be used otherwise.
 * The runtime logger should be used to log events in live transduction contexts.
 * <br><br>
 * Effectors that subclass {@link BaseReceptorEffector} do not need to call {@code IOutput}
 * data transfer methods directly as the base class converts and injects transducer field data
 * directly into receiver fields declared in the subclass. These receptor effectors receive
 * as effector parameters a list of transducer fields to transfer into effector fields with
 * a matching name. See the {@link BaseReceptorEffector} documentation for an example.
 *
 * @author Kim Briggs
 */
public interface IOutput {
	/**
	 * Determine whether the output instance is a bound to proxy transductor. In that case
	 * attempts to access transduction runtime field data or the currently selected field
	 * will throw {@link EffectorException}.
	 *
	 * @return true if output bound to a proxy transductor
	 */
	boolean isProxy();

	/**
	 * Get a localized field ordinal from an effector parameter token. This method may
	 * be called by proxy effectors during paramter compilation. The localized ordinal
	 * is the offset to the field in the transducer stack frame.
	 *
	 * @param transducerName the name of the transducer that defines the field (without `@` prefix in lead byte)
	 * @param fieldName the name of the field (`~` prefix in lead byte)
	 * @return the localized index of the field in the current transducer stack frame
	 * @throws EffectorException if things don't work out
	 * @throws CharacterCodingException if encoder fails
	 */
	int getTransducerFieldndex(String transducerName, String fieldName)
	throws EffectorException, CharacterCodingException;

	/**
	 * Get the localized ordinal number for the current selected field. Not valid for proxy effectors.
	 * The localized index is the offset to the field in the transducer stack frame.
	 *
	 * @return the localized index of the selected field in the current transducer stack frame
	 * @throws EffectorException if called on a proxy transductor
	 */
	int getTransducerFieldndex()
	throws EffectorException;

	/**
	 * Get current field value as string value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the String value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor
	 */
	String asString(int fieldOrdinal, String defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as char[] array value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the char[] array value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor
	 */
	char[] asChars(int fieldOrdinal, char[] defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as byte[] array value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the byte[] array value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor
	 */
	byte[] asBytes(int fieldOrdinal, byte[] defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as char value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the char value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor or decoded length != 1
	 */
	char asChar(int fieldOrdinal, char defaultValue)
	throws EffectorException;

	/**
	* Get current field value as boolean value.
	*
	* @param fieldOrdinal the field ordinal
	* @param defaultValue the default value to return if the transducer field is empty
	* @return the boolean value decoded from the field contents
	* @throws EffectorException if called on a proxy transductor or field contents not in {yes, no, true, false}
	*/
	boolean asBoolean(int fieldOrdinal, boolean defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as byte value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the byte value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor or value out of range
	 */
	byte asByte(int fieldOrdinal, byte defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as short value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the short value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor or value out of range
	 */
	short asShort(int fieldOrdinal, short defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as integer value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the integer value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor or value out of range
	 */
	int asInteger(int fieldOrdinal, int defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as long value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the long value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor or value out of range
	 */
	long asLong(int fieldOrdinal, long defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as double value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the double value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor or value out of range
	 */
	double asDouble(int fieldOrdinal, double defaultValue)
	throws EffectorException;

	/**
	 * Get current field value as float value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @param defaultValue the default value to return if the transducer field is empty
	 * @return the float value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor or value out of range
	 */
	float asFloat(int fieldOrdinal, float defaultValue)
	throws EffectorException;

	/**
	 * Get the ribose compiler Logger instance
	 *
	 * @return compiler Logger instance
	 */
	Logger getRtcLogger();

	/**
	 * Get the ribose runtime Logger instance
	 *
	 * @return runtime Logger instance
	 */
	Logger getRteLogger();
}
