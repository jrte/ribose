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

import com.characterforming.ribose.base.Bytes;

/**
 * Wrapper for raw effector parameter tokens referenced in model effector parameters. In
 * ribose patterns effector parameters are presented as a series of 1 or more backquoted
 * tokens (`token`), each of which is either a literal sequence of bytes or a symbolic
 * reference to a field, signal or transducer. A single lead byte distinquishes between
 * different token type: `~` for a field reference ({@code `~field`}), `!` for a signal
 * ({@code `!signal`}) and `@` for a transducer ({@code `@transducer`}). If a literal
 * token must begin with one of these bytes it can be escaped by prepending {@code `\xf8`}
 * (eg, {@code `\xf8@...`}). Similarly, if a token may contain binary data that may lead
 * with a symbolic reference type byte it must be prepended with {@code `\xf8`} to prevent
 * it from being misinterpreted.
 * <br><br>
 * Briefly, any backquoted effefctor parameter token that has `~`, `!`, `@` or `\xf8` as
 * lead byte must be escaped if it must be interpreted as a literal token. For example,
 * <br><ul>
 * <li>{@code out[`\xf8\xatext`]} -> {@code out[`\xf8\xf8\xatext`]}
 * <li>{@code out[`@literal`]} -> {@code out[`\xf8@literal`]}
 * <li>{@code out[`~literal`]} -> {@code out[`\xf8~literal`]}
 * <li>{@code out[`!literal`]} -> {@code out[`\xf8!literal`]}
 * </ul>
 * The original literal bytes can be recovered by calling {@link #getSymbol()}, which
 * can also be used to recover the undecorated field, signal or transducer name from
 * non-literal tokens types.
 * <br><br>
 * Arrays of {@code IToken} objects, corresponding to effector parameters, are conveyed
 * to proxy parameterized effectors for parameter precompilation. The proxy effector
 * constructs from each {@code IToken[]} array an indexed instance of its generic parameter
 * type <b>P</b>. See the {@link IParameterizedEffector} documentation for more information
 * regarding effector parameter compilation.
 *
 * @author Kim Briggs
 *
 */
public interface IToken {
	/**
	 * Test for literal token
	 *
	 * @return true if this is a literal token
	 */
	boolean isLiteral();

	/**
	 * Test for field token
	 *
	 * @return true if this is a field token
	 */
	boolean isField();

	/**
	 * Test for signal token
	 *
	 * @return true if this is a signal token
	 */
	boolean isSignal();

	/**
	 * Test for transducer token
	 *
	 * @return true if this is a transducer token
	 */
	boolean isTransducer();

	/**
	 * Get the a string representing the token. This will obtained by decoding the
	 * token literal value as a UTF-8 byte sequence, or converting all bytes to
	 * /xHH representation if decoding fails.
	 *
	 * @return a string representation of this token
	 */
	String asString();

	/**
	 * Get the name of the type, eg "signal", "field" or "transducer".
	 *
	 * @return the name of the type of this token
	 */
	String getTypeName();

	/**
	 * Get the raw token bytes, including type prefix if not a literal symbol.
	 *
	 * @return the raw token bytes, including type prefix if not a literal symbol
	 */
	Bytes getLiteral();

	/**
	 * Get the symbol bytes, excluding type prefix.
	 *
	 * @return the symbol bytes, excluding type prefix
	 */
	Bytes getSymbol();

	/**
	 * Get the symbol ordinal, or return -1 for literal tokens.
	 *
	 * @return symbol ordinal, or -1 for literal tokens
	 */
	int getOrdinal();

	/**
	 * Get the ordinal of the transducer of this field token (-1 for other token types)
	 *
	 * @return the transducer ordinal
	 */
	int getTransducerOrdinal();
}
