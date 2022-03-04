/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;

import com.characterforming.jrte.CompilationException;

final class Transition {
	private final ArrayList<String> errors;
	private int inS, outS;
	private int tape;
	private byte[] bytes;
	private boolean isFinal;
	private boolean isValid;

	public Transition(final ArrayList<String> errors) {
		this.errors = errors;
		this.inS = this.outS = this.tape = -1;
		this.bytes = null;
		this.isFinal = false;
		this.isValid = false;
	}

	public boolean load(final String fname, final DataInputStream f, final int line) throws CompilationException {
		assert line >= 2;
		byte input = 0;
		int ints[] = new int[4];
		int index = 0;
		try {
			do {
				int i = 0;
				int sign = 1;
				try {
					input = f.readByte();
				} catch (EOFException e) {
					return false;
				}
				do {
					if (input >= '0' && input <= '9') {
						i = (i * 10) + (input - '0');
					} else if (i == 0 && input == '-') {
						sign = -1;
					} else if (input == '\t') {
						ints[index++] = sign * i;
						sign = 1;
						i = 0;
					} else {
						throw new CompilationException(String.format(
							"Digit expected, received '%1$c' (0x%1$x) in header line of %2$s",
							(char) input, fname));
					}
					if (index < ints.length) {
						input = f.readByte();
					}
				} while (index < ints.length && input != '\n');
			} while (index < ints.length);
			if (input == '\n') {
				throw new CompilationException(String.format(
					"Tab expected, received newline in line %1$d of %2$s",
					line, fname));
			} else if (input != '\t') {
				throw new CompilationException(String.format(
					"Digit expected, received '%1$c' (0x%1$x) in line %2$d of %3$s",
					(char) input, line, fname));
			}
			if (index == ints.length) {
				byte label[] = new byte[ints[3]];
				int bytesRead = f.read(label);
				if (bytesRead < ints[3]) {
					throw new CompilationException(String.format(
						"Digit expected, received '%1$c' (0x%1$x) in header line of %2$s",
						(char) input, fname));
				}
				input = f.readByte();
				if (input != '\n') {
					throw new CompilationException(String.format(
						"Unexpected bytes in label of line '%1$d' of %2$s",
						line, fname));
				}
				this.inS = ints[0];
				this.outS = ints[1];
				this.tape = ints[2];
				this.bytes = label;
				this.isValid = this.inS >= 0 && this.outS >= 0 && this.tape >= 0;
				this.isFinal = this.outS == 1 && this.tape == 0 && this.bytes.length == 0;
			}
		} catch (IOException e) {
			String error = String.format("Unexpected IOException reading line %1$d in %2$s", line, fname);
			this.errors.add(error);
			throw new CompilationException(error, e);
		} catch (CompilationException c) {
			this.errors.add(c.getMessage());
			throw c;
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
		String label = Charset.defaultCharset().decode(ByteBuffer.wrap(this.bytes)).toString();
		return String.format("%1$d>%2$d:%3$d.%4$s", this.inS, this.outS, this.tape, label);
	}
}
