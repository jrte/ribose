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

import java.util.logging.Logger;

import com.characterforming.ribose.base.Bytes;
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
	 * Get the ribose compiler Logger instance (shared by transductor and all bound effectors)
	 *
	 * @return compiler Logger instance
	 */
	Logger getRtcLogger();

	/**
	 * Get the ribose runtime Logger instance (shared by transductor and all bound effectors)
	 *
	 * @return runtime Logger instance
	 */
	Logger getRteLogger();

	/**
	 * Get a copy of the current value for a field from an effector parameter token
	 *
	 * @param fieldName the name of the field (UTF-8 bytes, withouy `~` prefix in lead byte)
	 * @return a field instance or null
	 * @throws TargetBindingException on error
	 */
	IField getField(Bytes fieldName) throws TargetBindingException;

	/**
	 * Get a field ordinal from an effector parameter token
	 *
	 * @param fieldName the name of the field (UTF-8 bytes, without `~` prefix in lead byte)
	 * @return the numeric index of the field
	 * @throws TargetBindingException on error
	 */
	int getFieldOrdinal(Bytes fieldName) throws TargetBindingException;

	/**
	 * Get a copy of the current value for a field
	 *
	 * @param fieldOrdinal the numeric index of the field to get
	 * @return the specified field
	 */
	IField getField(int fieldOrdinal);

	/**
	 * Get the numeric index for the current selected field
	 *
	 * @return the numeric index of the selected field
	 */
	int getSelectedOrdinal();

	/**
	 * Get a copy of the current selected field
	 *
	 * @return the selected field
	 */
	IField getSelectedField();
}
