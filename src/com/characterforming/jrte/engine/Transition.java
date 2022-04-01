/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
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

	Transition(final ArrayList<String> errors) {
		this.errors = errors;
		this.inS = this.outS = this.tape = -1;
		this.bytes = null;
		this.isFinal = false;
		this.isValid = false;
	}

	boolean load(final String fname, final DataInputStream f, final int line) throws CompilationException {
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

	int getInS() {
		return this.inS;
	}

	int getOutS() {
		return this.outS;
	}

	byte[] getBytes() {
		return this.bytes;
	}

	int getTape() {
		return this.tape;
	}

	boolean isFinal() {
		return this.isFinal;
	}

	boolean isValid() {
		return this.isValid;
	}

	@Override
	public String toString() {
		String label = Charset.defaultCharset().decode(ByteBuffer.wrap(this.bytes)).toString();
		return String.format("%1$d>%2$d:%3$d.%4$s", this.inS, this.outS, this.tape, label);
	}
}
