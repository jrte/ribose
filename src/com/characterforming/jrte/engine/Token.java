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

import java.nio.ByteBuffer;
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
	private final IToken.Type type;
	private final byte[] literal;
	private final byte[] symbol;
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
			byte[] token = rawTokens[i];
			IToken.Type type;
			if (token[0] == IToken.TRANSDUCER_TYPE) {
				type = IToken.Type.TRANSDUCER;
			} else if (token[0] == IToken.FIELD_TYPE) {
				type = IToken.Type.FIELD;
			} else if (token[0] == IToken.SIGNAL_TYPE) {
				type = IToken.Type.SIGNAL;
			} else {
				tokens[i] = new Token(token);
				continue;
			}
			int ordinal = -1;
			Bytes symbol = new Bytes(Arrays.copyOfRange(token, 1, token.length));
			if (type == Type.TRANSDUCER) {
				ordinal = model.getTransducerOrdinal(symbol);
			} else if (type == Type.FIELD) {
				ordinal = model.getFieldMap().getOrDefault(symbol, -1);
			} else if (type == Type.SIGNAL) {
				ordinal = model.getSignalOrdinal(symbol);
			}
			tokens[i] = new Token(token, ordinal);
		}
		return tokens;
	}

	/**
	 * Constructor for literal tokens
	 * 
	 * @param token a literal token
	 */
	public Token(byte[] token) {
		this.literal = this.symbol = token;
		this.type = IToken.Type.LITERAL;
		this.ordinal = -1;
	}

	/**
	 * Constructor for literal or symbolic tokens. The ordinal value is
	 * ignored if the token does not represent a symbol.
	 * 
	 * @param token a literal or symbolic token
	 * @param ordinal the symbolic token ordinal
	 */
	public Token(byte[] token, int ordinal) {
		this.literal = token;
		if (token[0] == IToken.TRANSDUCER_TYPE) {
			this.type = IToken.Type.TRANSDUCER;
		} else if (token[0] == IToken.FIELD_TYPE) {
			this.type = IToken.Type.FIELD;
		} else if (token[0] == IToken.SIGNAL_TYPE) {
			this.type = IToken.Type.SIGNAL;
		} else {
			this.type = IToken.Type.LITERAL;
		}
		if (this.type != IToken.Type.LITERAL) {
			this.symbol = Arrays.copyOfRange(this.literal, 1, this.literal.length);
			this.ordinal = ordinal;
		} else {
			this.symbol = this.literal;
			this.ordinal = -1;
		}
	}

	@Override // @see com.characterforming.ribose.IToken#getType()
	public Type getType() {
		return this.type;
	}
	
	@Override // @see com.characterforming.ribose.IToken#getLiteralValue()
	public byte[] getLiteralValue() {
		return this.literal;
	}

	@Override // @see com.characterforming.ribose.IToken#getSymbolName()
	public byte[] getSymbolName() {
		return this.symbol;
	}

	@Override // @see com.characterforming.ribose.IToken#getSymbolOrdinal()
	public int getSymbolOrdinal() {
		return this.ordinal;
	}

	@Override
	public String toString() {
		try {
			CharsetDecoder decoder = Base.newCharsetDecoder();
			ByteBuffer buffer = ByteBuffer.wrap(getLiteralValue());
			return decoder.decode(buffer).toString();
		} catch (Exception e) {
			Bytes data = new Bytes(getLiteralValue());
			return data.toHexString();
		}
	}
}
