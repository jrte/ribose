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
package com.characterforming.jrte.engine;

import java.nio.charset.CharsetDecoder;
import java.util.Arrays;

import com.characterforming.ribose.IToken;
import com.characterforming.ribose.base.Bytes;

/**
 * Wrapper for raw effector parameter tokens referenced in model effector parameters. 
 * 
 * @author Kim Briggs
 */
final class Token implements IToken {
	private static final String[] types = {
		"literal", "transducer", "field", "signal"
	};

	private final IToken.Type type;
	private final Bytes literal;
	private final Bytes symbol;
	private int ordinal;

	/**
	 * Assemble tokens from effector parameter bytes. For internal use
	 * by Model to support effector parameter precompilation during
	 * model assembly and runtime use. Here a parameter a series of
	 * raw byte arrays scraped from a paramterized effector reference
	 * in a ribose pattern, eg for {@code count[`99` `!nil`])} the
	 * raw tokens {@code `99`} (literal) and {@code `!nil`} (signal)
	 * are compiled to an instance of the {@code count} effector's
	 * effector parameter type (int[2]) as {@code new int[] {99, 257}}.
	 * 
	 * @param model the containing ribose model
	 * @param rawTokens a series of raw effector parameter tokens
	 */
	public static IToken[] getParameterTokens(Model model, byte[][] rawTokens) {
		IToken[] tokens = new Token[rawTokens.length];
		for (int i = 0; i < rawTokens.length; i++) {
			Token token = new Token(rawTokens[i]);
			if (token.type == Type.TRANSDUCER) {
				tokens[i] = new Token(rawTokens[i], model.getTransducerOrdinal(token.symbol));
			} else if (token.type == Type.FIELD) {
				tokens[i] = new Token(rawTokens[i], model.getFieldMap().getOrDefault(token.symbol, -1));
			} else if (token.type == Type.SIGNAL) {
				tokens[i] = new Token(rawTokens[i], model.getSignalOrdinal(token.symbol));
			} else {
				tokens[i] = token;
			}
		}
		return tokens;
	}

	/**
	 * Constructor for literal tokens
	 * 
	 * @param token a literal token
	 */
	public Token(byte[] token) {
		this(token, -1);
	}

	/**
	 * Constructor for literal or symbolic tokens. The ordinal value is
	 * ignored if the token does not represent a symbol.
	 * 
	 * @param token a literal or symbolic token
	 * @param ordinal the symbolic token ordinal
	 */
	public Token(byte[] token, int ordinal) {
		this.type = Token.type(token);
		if (this.type != IToken.Type.LITERAL) {
			this.literal = new Bytes(token);
			this.symbol = new Bytes(Arrays.copyOfRange(token, 1, token.length));
			this.ordinal = ordinal;
		} else {
			this.literal = new Bytes(this.literal(token));
			this.symbol = this.literal;
			this.ordinal = -1;
		}
	}

	private static Type type(byte[] token) {
		Type t = IToken.Type.LITERAL;
		if (token.length > 1 && token[0] != token[1]) {
			if (token[0] == IToken.TRANSDUCER_TYPE) {
				t = IToken.Type.TRANSDUCER;
			} else if (token[0] == IToken.FIELD_TYPE) {
				t = IToken.Type.FIELD;
			} else if (token[0] == IToken.SIGNAL_TYPE) {
				t = IToken.Type.SIGNAL;
			}
		} else if (token.length == 1 && token[0] == IToken.FIELD_TYPE) {
			t = IToken.Type.FIELD;
		}
		return t;
	}

	@Override // @see com.characterforming.ribose.IToken#getType()
	public Type getType() {
		return this.type;
	}
	
	@Override // @see com.characterforming.ribose.IToken#getLiteralValue()
	public Bytes getLiteral() {
		return this.literal;
	}

	@Override // @see com.characterforming.ribose.IToken#getSymbolName()
	public Bytes getSymbol() {
		return this.symbol;
	}

	@Override // @see com.characterforming.ribose.IToken#getOrdinal()
	public int getOrdinal() {
		return this.ordinal;
	}

	@Override // @see com.characterforming.ribose.IToken#setOrdinal(int)
	public void setOrdinal(int ordinal) {
		this.ordinal = ordinal;
	}

	@Override
	public String getTypeName() {
		return Token.types[this.type.ordinal()];
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Token o
		&& this.type == o.type && this.symbol.equals(o.symbol);
	}

	@Override
	public int hashCode() {
		return this.symbol.hashCode() * (this.type.ordinal() + 1);
	}

//	@Override
	public String toString(CharsetDecoder decoder) {
		return this.literal.getLength() != 1 || this.literal.bytes()[0] != 0xa
			? this.getLiteral().toString(decoder)
			: "#a";
	}

	private byte[] literal(byte[] token) {
		if ((token.length > 1) && (token[0] == token[1])
		&& (token[0] == '!' || token[0] == '~' || token[0] == '@')) {
			int trim = 2;
			while (token[0] == token[trim]) {
				trim++;
			}
			trim -= 1;
			return Arrays.copyOfRange(token, trim, token.length);
		}
		return token;
	}
}
