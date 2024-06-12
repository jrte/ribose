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

/**
 * Provides loggers and a view of fields (data extracted by the transduction) to {@link
 * IEffector} implementations. Effectors receive their {@code IOutput} instance via
 * {@link IEffector#setOutput(IOutput)} when they are first bound to a transductor. Transducer
 * fields are local and bound to the defining transducer and can be accessed by stable
 * field ordinal numbers <i>when the transducer is running on the transductor stack</i>.
 * Every transducer has an anonymous field with ordinal number 0 (the anonymous name
 * token (`~`) in transducer patterns). Ordinal numbers for named fields (referenced
 * with a <b>~</b> field type prefix in patterns, eg, {@code out[`~data`]}) are obtained
 * by providing defining transducer and field names to {@link #getLocalizedFieldIndex(String, String)}.
 * Effectors should obtain field ordinals in {@code setOutput()} and retain them for
 * use with the data transfer methods {@link #asInteger(int)}, {@link #asReal(int)},
 * {@link #asBytes(int)} and {@link #asString(int)} in {@link IEffector#invoke()}
 * and {@link IParameterizedEffector#invoke(int)} as shown in the example below.
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
 *       this.fields[i] = super.output.getLocalizedFieldOrdinal(transducerName, fieldNames[i]);
 *     }
 *   }

 *   &#64;Override // called from transducer running on top of live transductor's stack
 *   public int invoke() throws EffectorException {
 *     ModelCompiler.this.putHeader(new Header(
 *       (int)super.output.asInteger(fields[0]),
 *       (int)super.output.asInteger(fields[1]),
 *       (int)super.output.asInteger(fields[2]),
 *       (int)super.output.asInteger(fields[3]),
 *       (int)super.output.asInteger(fields[4])));
 *     return IEffector.RTX_NONE;
 *   }
 * }</pre>
 * All effectors receive with {@code IOutput} identical instances of thread-safe loggers,
 * which are shared by the transductor and its effectors. The compiler logger is exposed
 * here for use by proxy effectors in compilation contexts and should not be used otherwise.
 * The runtime logger should be used to log events in live transduction contexts.
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
	int getLocalizedFieldIndex(String transducerName, String fieldName)
	throws EffectorException, CharacterCodingException;

	/**
	 * Get the localized ordinal number for the current selected field. Not valid for proxy effectors.
	 * The localized index is the offset to the field in the transducer stack frame.
	 *
	 * @return the localized index of the selected field in the current transducer stack frame
	 * @throws EffectorException if called on a proxy transductor
	 */
	int getLocalizedFieldndex()
	throws EffectorException;

	/**
	 * Get current field value as integer value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @return the String value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor
	 * @throws CharacterCodingException if decoding fails
	 */
	String asString(int fieldOrdinal)
	throws EffectorException, CharacterCodingException;

	/**
	 * Get current field value as integer value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @return the integer value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor
	 */
	long asInteger(int fieldOrdinal)
	throws EffectorException;

	/**
	 * Get current field value as real value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @return the real value decoded from the field contents
	 * @throws EffectorException if called on a proxy transductor
	 */
	double asReal(int fieldOrdinal)
	throws EffectorException;

	/**
	 * Get current field value as integer value.
	 *
	 * @param fieldOrdinal the field ordinal
	 * @return the field contents
	 * @throws EffectorException if called on a proxy transductor
	 */
	byte[] asBytes(int fieldOrdinal)
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
