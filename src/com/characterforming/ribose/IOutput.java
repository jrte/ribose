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

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.logging.Logger;

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Provides loggers, UTF-8 codecs and a view of fields (data extracted by the transduction)
 * to {@link IEffector} implementations. Effectors receive their {@code IOutput} instance via
 * {@link IEffector#setOutput(IOutput)} when they are first bound to a transductor. Fields can be
 * accessed by name or ordinal number as {@link IField} instances. There is an anonymous field
 * with ordinal 0 that can be referenced by the anonymous name token (`~`) in patterns or by the
 * empty string in Java. Fields are referenced with a field type prefix <b>~</b> in ribose 
 * transducer patterns (eg, {@code out[`~data`]}) and without the type prefix in Java (eg,
 * {@code getFieldOrdinal("data")}.
 * <br><br>
 * All effectors receive with {@code IOutput} identical instances of loggers and codecs,
 * which are shared by the transductor and its effectors. The compiler logger is exposed
 * here for use by proxy effectors in compilation contexts and should not be
 * used otherwise. The runtime logger should be used to log events in live transduction
 * contexts.
 *
 * @author Kim Briggs
 */
public interface IOutput {
	/**
	 * Get a copy of the current value for a field
	 *
	 * @param fieldOrdinal the ordinal number of the field to get
	 * @return the specified field
	 */
	IField getField(int fieldOrdinal);

	/**
	 * Get the ordinal number for the current selected field
	 *
	 * @return the ordinal number of the selected field
	 */
	int getFieldOrdinal();

	/**
	 * Get a copy of the current selected field
	 *
	 * @return the selected field
	 */
	IField getField();

	/**
	 * Get a copy of the current value for a field from an effector parameter token
	 *
	 * @param fieldName the name of the field (UTF-8 bytes, withouy `~` prefix in lead byte)
	 * @return a field instance or null
	 * @throws TargetBindingException if things don't work out
	 */
	IField getField(Bytes fieldName) throws TargetBindingException;

	/**
	 * Get a field ordinal from an effector parameter token
	 *
	 * @param fieldName the name of the field (UTF-8 bytes, without `~` prefix in lead byte)
	 * @return the ordinal number of the field
	 * @throws TargetBindingException if things don't work out
	 */
	int getFieldOrdinal(Bytes fieldName) throws TargetBindingException;

	/**
	 * Encode a signal in an {@code effector.invoke()} return value. The signal will be
	 * consumed in the next transition of the running transductor. At most one effector
	 * can return an encoded signal in any effect vector triggered by a state transition.
	 * If &gt;1 effectors return encoded signals the signal decoded after the transition
	 * will be the bitwise OR of the signal ordinals. In that case the decoded signal
	 * ordinal will either be out of range (an exception will result) or it will be in
	 * range and incorrect but applied as is (producing an unpredictable outcome).
	 * <br><br>
	 * Signal ordinals in a ribose model are mapped to the end of the byte ordinal range
	 * {@code [0..255]}. The range of signal ordinals is {@code [256..256+N]} for a model
	 * with <b>N</b> additional signals. Signal names and ordinals are listed in the
	 * model map produced with every compiled ribose model. For built-in {@link Signal}
	 * the {@code Signal.ordinal()} method reflects the Java enumerator ordinal; use 
	 * {@link Signal#signal()} to obtain the model signal ordinal.
	 * 
	 * @param signalOrdinal the signal ordinal to encode ( {@code 256 ≤ value < 256+#signals} )
	 * @return an encoding of the signal, to return from effector {@code invoke()} 
	 * @throws EffectorException if {@code signalOrdinal} is out of range 
	 * @see Signal#signal() 
	 */
	int signal(int signalOrdinal) throws EffectorException;

	/**
	 * Get the decoder bound to the transductor
	 * 
	 * @return the transductor's decoder
	 */
	CharsetDecoder decoder();

	/**
	 * Get the encoder bound to the transductor
	 * 
	 * @return the transductor's encoder
	 */
	CharsetEncoder encoder();

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
