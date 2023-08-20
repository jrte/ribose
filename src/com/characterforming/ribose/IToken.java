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

/**
 * Wrapper for raw effector parameter tokens referenced in model effector parameters. A
 * token is a byte array containing UTF-8 text and/or binary bytes. It may represent a 
 * literal or a symbol (transducer, field or signal name) prefixed with a special 
 * byte designating its type (@, ~ or !, respectively).
 *
 * @author Kim Briggs
 *
 */
public interface IToken {
	/** type decoration ('@') for ginr tokens representing transducers in ribose patterns */
	byte TRANSDUCER_TYPE = '@';
	/** type decoration ('!') for ginr tokens representing signals in ribose patterns */
	byte SIGNAL_TYPE = '!';
	/** type decoration ('~') for ginr tokens representing fields in ribose patterns */
	byte FIELD_TYPE = '~';
	/** null value for type decoration */
	byte LITERAL_TYPE = 0;

	/** Enumeration of token types. */
	enum Type {
		/** A literal token, eg `abc` */
		LITERAL,
		/** A transducer token, eg `@abc` */
		TRANSDUCER,
		/** A field token, eg `~abc` */
		FIELD,
		/** A signal token, eg `!abc` */
		SIGNAL
	}

	/**
	 * Get the type of the token.
	 * 
	 * @return the type of the token
	 */
	Type getType();

	/**
	 * Get the raw symbol bytes, including type prefix if not a literal symbol.
	 *
	 * @return the raw symbol bytes, including type prefix if not a literal symbol
	 */
	byte[] getLiteralValue();

	/**
	 * Get the symbol bytes, excluding type prefix if not a literal symbol. For 
	 * literal symbols this is identical to {@code getLiteral()}.
	 *
	 * @return the symbol bytes, excluding type prefix if not a literal symbol
	 */
	byte[] getSymbolName();

	/**
	 * Get the symbol ordinal, or return -1 for literal symbols.
	 * 
	 * @return symbol ordinal, or -1 for literal symbols
	 */
	int getSymbolOrdinal();
}
