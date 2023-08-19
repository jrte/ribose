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
public class Token implements IToken {
	private final IToken.Type type;
	private final byte[] literal;
	private final byte[] symbol;
	private int ordinal;

	/**
	 * Determine token type
	 * 
	 * @param token the token to interpret
	 * @return the token type
	 */
	public static Type getSymbolType(byte[] token) {
		if (token[0] == IToken.TYPE_REFERENCE_TRANSDUCER) {
			return IToken.Type.TRANSDUCER;
		} else if (token[0] == IToken.TYPE_REFERENCE_FIELD) {
			return IToken.Type.FIELD;
		} else if (token[0] == IToken.TYPE_REFERENCE_SIGNAL) {
			return IToken.Type.SIGNAL;
		} else {
			return IToken.Type.LITERAL;
		}
	}

	/**
	 * Extract symbol name from symbolic token
	 * 
	 * @param token the token to interpret
	 * @return the symbol name if token is a symbol, otherwise the literal value
	 */
	public static byte[] getSymbolName(byte[] token) {
		if (getSymbolType(token) != Type.LITERAL) {
			return Arrays.copyOfRange(token, 1, token.length);
		}
		return token;
	}

	/**
	 * Assemble tokens from effector partameter bytes.
	 * 
	 * @param token
	 */
	public static IToken[] getParameterTokens(Model model, byte[][] parameters) {
		IToken[] tokens = new Token[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			byte[] parameter = parameters[i];
			switch (Token.getSymbolType(parameter)) {
				case LITERAL:
					tokens[i] = new Token(parameter);
					break;
				case TRANSDUCER:
					Bytes symbol = new Bytes(Token.getSymbolName(parameter));
					int ordinal = model.getTransducerOrdinal(symbol);
					tokens[i] = new Token(parameter, ordinal);
					break;
				case FIELD:
					symbol = new Bytes(Token.getSymbolName(parameter));
					ordinal = model.getFieldMap().getOrDefault(symbol, -1);
					tokens[i] = new Token(parameter, ordinal);
					break;
				case SIGNAL:
					symbol = new Bytes(Token.getSymbolName(parameter));
					ordinal = model.getSignalOrdinal(symbol);
					tokens[i] = new Token(parameter, ordinal);
					break;
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
		if (token[0] == IToken.TYPE_REFERENCE_TRANSDUCER) {
			this.type = IToken.Type.TRANSDUCER;
		} else if (token[0] == IToken.TYPE_REFERENCE_FIELD) {
			this.type = IToken.Type.FIELD;
		} else if (token[0] == IToken.TYPE_REFERENCE_SIGNAL) {
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
