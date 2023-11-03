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

import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.HashMap;
import com.characterforming.jrte.engine.Model.Argument;
import com.characterforming.ribose.IToken;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;

/**
 * Wrapper for raw effector parameter tokens referenced in model effector parameters.
 *
 * @author Kim Briggs
 */
public final class Token implements IToken {
	/** type decoration ('@') for ginr tokens representing transducers in ribose patterns */
	private static byte transducerType = '@';
	/** type decoration ('!') for ginr tokens representing signals in ribose patterns */
	private static byte signalType = '!';
	/** type decoration ('~') for ginr tokens representing fields in ribose patterns */
	private static byte fieldType = '~';
	/** type decoration (0xf8) for ginr escaped literal tokens */
	private static byte literalType = Token.escape();
	/** type decoration (0xf8) for ginr escaped literal tokens */
	private static byte nullType = (byte) 0x0;

	/** Enumeration of token types. */
	public enum Type {
		/** A literal token, eg {@code `abc`} or {@code `0xf8!nil`} (escaped signal reference) */
		LITERAL(literalType, "literal"),
		/** A transducer token, eg {@code `@abc`} */
		TRANSDUCER(transducerType, "transducer"),
		/** A field token, eg {@code `~abc`} */
		FIELD(fieldType, "field"),
		/** A signal token, eg {@code `!abc`} */
		SIGNAL(signalType, "signal"),
		/** A null or empty token */
		NULL(nullType, "null");


		private final byte indicator;
		private final String name;

		/**
		 * Constructor
		 *
		 * @param indicator the type indicator (reference prefix)
		 * @param name the display name for the Type
		 */
		Type(byte indicator, String name) {
			assert Type.isIndicator(indicator);
			this.indicator = indicator;
			this.name = name;
		}

		/**
		 *  Get the type indicator (reference prefix)
		 *
		 * @return the type indicator
		 */
		public byte getIndicator() {
			return this.indicator;
		}

		/**
		 * Get the type name
		 *
		 * @return the type name
		 */
		public String getName() {
			return this.name;
		}

		public static boolean isIndicator(byte b) {
			return (b == Token.literalType) || (b == Token.transducerType)
			|| (b == Token.fieldType) || (b == Token.signalType)
			|| (b == Token.nullType);
		}
	}

	private static final String[] types = {
		"literal", "transducer", "field", "signal"
	};

	private final Token.Type type;
	private final Bytes literal;
	private final Bytes symbol;
	private int transducerOrdinal;
	private int tokenOrdinal;

	/**
	 * Constructor for literal tokens
	 *
	 * @param token a literal token
	 */
	public Token(byte[] token) {
		this(token, -1, -1);
	}

	/**
	 * Constructor for literal or symbolic tokens. The ordinal value is
	 * ignored if the token does not represent a symbol.
	 *
	 * @param token a literal or symbolic token
	 * @param ordinal the symbolic token ordinal
	 */
	public Token(byte[] token, int ordinal) {
		this(token, ordinal, -1);
	}

	/**
	 * Constructor for literal or symbolic tokens. The ordinal value is
	 * ignored if the token does not represent a symbol.
	 *
	 * @param token a literal or symbolic token
	 * @param ordinal the symbolic token ordinal
	 * @param transducer the ordinal of the transducer the token is bound to
	 */
	public Token(byte[] token, int ordinal, int transducer) {
		this.type = Token.type(token);
		if (this.type != Token.Type.LITERAL) {
			this.literal = new Bytes(Token.descape(token));
			this.symbol = new Bytes(Arrays.copyOfRange(this.literal.bytes(), 1, this.literal.getLength()));
		} else {
			this.literal = new Bytes(Token.descape(token));
			this.symbol = this.literal;
		}
		this.transducerOrdinal = transducer;
		this.tokenOrdinal = ordinal;
	}

	@Override
	public boolean isLiteral() {
		return this.type == Type.LITERAL;
	}

	@Override
	public boolean isField() {
		return this.type == Type.FIELD;
	}

	@Override
	public boolean isSignal() {
		return this.type == Type.SIGNAL;
	}

	@Override
	public boolean isTransducer() {
		return this.type == Type.TRANSDUCER;
	}

	@Override // @see com.characterforming.ribose.IToken#asString()
	public String asString() {
		try {
			return Codec.decode(this.symbol.bytes());
		} catch (CharacterCodingException e) {
			return this.symbol.toHexString();
		}
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
		return this.tokenOrdinal;
	}

	@Override // @see com.characterforming.ribose.IToken#getTypeName()
	public String getTypeName() {
		return Token.types[this.type.ordinal()];
	}

	@Override // @see com.characterforming.ribose.IToken#getTransducerOrdinal()
	public int getTransducerOrdinal() {
		return this.transducerOrdinal;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Token o && this.type == o.type && this.symbol.equals(o.symbol);
	}

	@Override
	public int hashCode() {
		return this.symbol.hashCode() * (this.type.ordinal() + 1);
	}

	/**
	 * Make a reference (eg {@code `!name`}) for a symbol (eg {@code `name`})
	 *
	 * @param type the type of reference to make
	 * @param symbol the symbal to reference
	 * @return the symbol reference
	 */
	static byte[] reference(Token.Type type, byte[] symbol) {
		byte[] reference = new byte[symbol.length + 1];
		System.arraycopy(symbol, 0, reference, 1, symbol.length);
		reference[0] = type.getIndicator();
		return reference;
	}

	/**
	 * The disinquished byte used to escape symbolic references in backquoted effector
	 * parameter tokens. For example, using {@code `\xf8@whatever`} to interpret token
	 * literally, not as a transducer reference, or prepending {@code 0xf8} to a series
	 * of binary bytes that might lead with a reference indicator byte.
	 *
	 * @return the escape byte (0xf8)
	 */
	static byte escape() {
		return (byte) 0xf8;
	}

	/**
	 * A literal token must be escaped if its length is >1 and lead byte is a reference byte
	 * (!, @, ~ or 0xf8).
	 *
	 * @param token the token to test
	 * @return true if the token must be escaped
	 */
	static boolean mustEscape(byte[] token) {
		return token.length > 1
		&& (token[0] == Token.escape() || token[0] == '!' || token[0] == '@' || token[0] == '~');
	}

	/**
	 * A literal token is escaped if its length is >1 and lead byte the escape byte 0xf8
	 *
	 * @param token the token to test
	 * @return true if the token escaped
	 */
	static boolean isEscaped(byte[] token) {
		return token.length > 1 && token[0] == Token.escape();
	}

	/**
	 * A literal token is escaped by inserting, if necessary, the escape byte 0xf8 as
	 * lead byte to distinguish it unambiguously from symbolic reference tokens
	 * (fields, transducers, signals). The input token is returned unchanged if
	 * not ambiguous.
	 *
	 * @param token the token to escape
	 * @return a token that can be safely distinguished from symbolic refernce tokens
	 */
	static byte[] escape(byte[] token, int length) {
		if (mustEscape(token)) {
			byte[] t = new byte[length + 1];
			System.arraycopy(token, 0, t, 1, length);
			t[0] = Token.escape();
			token = t;
		}
		return token;
	}

	/**
	 * A literal token is descaped by removing the lead escape byte 0xf8, or returned
	 * unchanged if not escaped.
	 *
	 * @param token the token to remove from escapement
	 * @return the literal token with lescape byte removed
	 */
	static byte[] descape(byte[] token) {
		if (isEscaped(token)) {
			byte[] t = new byte[token.length - 1];
			System.arraycopy(token, 1, t, 0, token.length - 1);
			token = t;
		}
		return token;
	}

	static IToken[] getParameterTokens(Model model, Argument argument) {
		byte[][] bytes = argument.tokens().getBytes();
		IToken[] tokens = new Token[bytes.length];
		for (int i = 0; i < tokens.length; i++) {
			Token token = new Token(argument.tokens().getBytes(i));
			if (token.type == Token.Type.FIELD) {
				int transducerOrdinal = argument.transducerOrdinal();
				int fieldOrdinal = model.getFieldOrdinal(token.symbol);
				HashMap<Integer, Integer> localFieldMap = model.transducerFieldMaps.get(transducerOrdinal);
				int localFieldIndex = localFieldMap.computeIfAbsent(fieldOrdinal, absent -> localFieldMap.size());
				tokens[i] = new Token(bytes[i], localFieldIndex);
				assert transducerOrdinal >= 0 && fieldOrdinal >= 0
				&& (localFieldMap.isEmpty() || localFieldIndex >= 0);
			} else if (token.type == Token.Type.TRANSDUCER)
				tokens[i] = new Token(bytes[i], model.getTransducerOrdinal(token.symbol));
			else if (token.type == Token.Type.SIGNAL)
				tokens[i] = new Token(bytes[i], model.getSignalOrdinal(token.symbol));
			else
				tokens[i] = token;
		}
		return tokens;
	}

	private static Token.Type type(byte[] token) {
		Token.Type type = Token.Type.LITERAL;
		if (!Token.isEscaped(token)) {
			if (token.length > 0) {
				if (token[0] == Token.transducerType)
					type = Token.Type.TRANSDUCER;
				else if (token[0] == Token.fieldType)
					type = Token.Type.FIELD;
				else if (token[0] == Token.signalType)
					type = Token.Type.SIGNAL;
			}
		} else if (token.length == 1 && token[0] == Token.fieldType)
			type = Token.Type.FIELD;
		return type;
	}

	void setOrdinal(int ordinal) {
		this.tokenOrdinal = ordinal;
	}

	void setTransducerOrdinal(int transducerOrdinal) {
		this.transducerOrdinal = transducerOrdinal;
	}
}
