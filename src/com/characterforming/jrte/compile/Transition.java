/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

import com.characterforming.jrte.CompilationException;

final class Transition {
	private final ArrayList<String> errors;
	private final Charset charset;
	private int inS, outS;
	private int tape;
	private byte[] bytes;
	private String string;
	private boolean isFinal;
	private boolean isValid;

	public Transition(final Charset charset, final ArrayList<String> errors) {
		this.errors = errors;
		this.charset = charset;
		this.inS = this.outS = this.tape = -1;
		this.bytes = null;
		this.string = null;
		this.isFinal = false;
		this.isValid = false;
	}

	public boolean load(final DataInputStream f) throws IOException, CompilationException {
		try {
			this.inS = f.readShort();
		} catch (final EOFException e) {
			return false;
		}
		this.isValid = true;
		this.outS = f.readShort();
		final byte[] buf = new byte[64 << 10];
		if (f.read(buf, 0, 2) != 2) {
			this.errors.add("Unexpected end of file in INR automaton");
			this.isValid = false;
		}
		final String tape = this.charset.decode(ByteBuffer.wrap(buf, 0, 1)).toString();
		final String dot = this.charset.decode(ByteBuffer.wrap(buf, 1, 1)).toString();
		if (dot.equals(".")) {
			try {
				this.tape = Integer.parseInt(tape);
			} catch (final NumberFormatException e) {
				this.errors.add(String.format("Malformed tape number '%1$s' in transition in INR automaton", tape));
				this.isValid = false;
			}
			boolean escape = false;
			boolean escapenl = false;
			int i = 0;
			do {
				escapenl = false;
				if (f.read(buf, i, 1) != 1) {
					throw new CompilationException("Unexpected end of file in INR automaton");
				}
				if (escape) {
					escape = false;
					switch (buf[i]) {
						case '_':
							buf[i] = ' ';
							break;
						case 'n':
							buf[i] = '\n';
							escapenl = true;
							break;
						case 't':
							buf[i] = '\t';
							break;
						case '\\':
							buf[i] = '\\';
							break;
						default:
							this.errors.add(String.format("Invalid escape sequence '\\%1$c' in transition symbol in INR automaton", buf[i]));
							break;
					}
				} else if (buf[i] == '\\') {
					escape = true;
					i--;
				}
			} while ((i == -1 || buf[i] != 10 || escapenl) && ++i < buf.length);
			if (i < buf.length && !escape) {
				this.bytes = i > 0 ? Arrays.copyOf(buf, i) : new byte[] { 0 };
			} else if (escape) {
				this.errors.add("End of transition with incomplete escape sequence in INR automaton");
				this.isValid = false;
			} else {
				this.errors.add("Symbol length overflow transition in INR automaton");
				this.isValid = false;
			}
			this.string = this.charset.decode(ByteBuffer.wrap(this.bytes, 0, this.bytes.length)).toString();
		} else if (tape.equals("-") && dot.equals("|")) {
			this.tape = -1;
			this.outS = -1;
			this.string = null;
			this.isFinal = true;
			if (f.read(buf, 0, 1) != 1) {
				throw new CompilationException("Unexpected end of file in INR automaton");
			} else if (buf[0] != 10) {
				throw new CompilationException("Missing end of line in final in INR automaton");
			}
		} else {
			this.errors.add(String.format("Missing or malformed tape delimiter '%1$s' in transition in INR automaton", dot));
			this.isValid = false;
		}
		return true;
	}

	public int getInS() {
		return this.inS;
	}

	public int getOutS() {
		return this.outS;
	}

	public byte[] getBytes() {
		return this.bytes;
	}

	public String getString() {
		return this.string;
	}

	public int getTape() {
		return this.tape;
	}

	public boolean isFinal() {
		return this.isFinal;
	}

	public boolean isValid() {
		return this.isValid;
	}

	@Override
	public String toString() {
		return String.format("%1$d>%2$d:%3$d.%4$s", this.inS, this.outS, this.tape, this.string);
	}
}
