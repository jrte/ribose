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

import com.characterforming.ribose.base.Bytes;

/**
 * Wrapper for raw effector parameter tokens referenced in model effector parameters.
 * A token is backed by a byte array containing UTF-8 text and/or binary bytes
 * corresponding to backquoted Unicode text in effector parameters in ribose patterns.
 * To ribose a token may represent a literal or a symbolic reference to a transducer,
 * field or signal prefixed with a special byte designating the type of the referent
 * (@, ~ or !, respectively). Literal tokens that require a type prefix, (eg {@code
 * out[`!Aliteral`]}) can escape the prefix by doubling it ({@code out[`!!Aliteral`]}).
 * <br><br>
 * Arrays of {@code IToken} objects, corresponding to effector parameters, are conveyed
 * to proxy parameterized instances during effector parameter precompilation. See the
 * {@link IParameterizedEffector} documentation for more information regarding parameter
 * tokens.
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
	/** type decoration (0) for ginr literal tokens */
	byte LITERAL_TYPE = 0;

	/** Enumeration of token types. */
	enum Type {
		/** A literal token, eg `abc` */
		LITERAL(LITERAL_TYPE, "literal"),
		/** A transducer token, eg `@abc` */
		TRANSDUCER(TRANSDUCER_TYPE, "transducer"),
		/** A field token, eg `~abc` */
		FIELD(FIELD_TYPE, "field"),
		/** A signal token, eg `!abc` */
		SIGNAL(SIGNAL_TYPE, "signal");

		private final byte indicator;
		private final String name;

		/**
		 * Constructor
		 * 
		 * @param indicator the type indicator (reference prefix)
		 * @param name the display name for the Type
		 */
		Type(byte indicator, String name) {
			this.indicator = indicator;
			this.name = name;
		}

		/**
		 *  Get the type indicator (reference prefix)
		 * 
		 * @return the type indicator
		 */
		byte getIndicator() {
			return this.indicator;
		}

		/**
		 * Get the type indicator (reference prefix)
		 * 
		 * @return the type indicator
		 */
		String getName() {
			return this.name;
		}
	}

	/**
	 * Get the type of the token.
	 * 
	 * @return the type of the token
	 */
	Type getType();

	/**
	 * Get the name of the token.
	 * 
	 * @param decoder the decoder to use for the name
	 * @return the name of this token
	 */
	String getName(CharsetDecoder decoder);

	/**
	 * Get the name of the type, eg "signal", "field" or "transducer".
	 * 
	 * @return the name of the type of this token
	 */
	String getTypeName();

	/**
	 * Get the raw symbol bytes, including type prefix if not a literal symbol.
	 *
	 * @return the raw symbol bytes, including type prefix if not a literal symbol
	 */
	Bytes getLiteral();

	/**
	 * Get the symbol bytes, excluding type prefix if not a literal symbol. For 
	 * literal tokens this is identical to {@code getLiteral()}.
	 *
	 * @return the symbol bytes, excluding type prefix if not a literal symbol
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

	/**
	 * Make a reference (eg `!name`) for a symbol (eg `name`)
	 * 
	 * @param type the type of reference to make
	 * @param symbol the symbal to reference
	 * @return the symbol reference
	 */
	default byte[] getReference(Type type, byte[] symbol) {
		byte[] reference = new byte[symbol.length + 1];
		System.arraycopy(symbol, 0, reference, 1, symbol.length);
		reference[0] = type.getIndicator();
		return reference;
	}
}
