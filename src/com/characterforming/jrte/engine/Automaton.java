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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 *
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.	See
 * LICENSE-gpl-3.0. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.ModelException;

/** Assembles bootstrap transducer used by ModelCompiler to assemble transducers for ribose models from ginr FSTs */
final class Automaton {
	private final ModelCompiler compiler;
	private final Logger rtcLogger;
	private final Input dfain;
	private int line;

	/** Constructor */
	 Automaton(ModelCompiler compiler, Logger rtcLogger) {
		this.compiler = compiler;
		this.rtcLogger = rtcLogger;
		this.dfain = new Input();
		this.line = 0;
	}

	boolean assemble(File inrAutomataDirectory) {
		boolean commit = true;
		for (final String filename : inrAutomataDirectory.list()) {
			if (filename.endsWith(Base.AUTOMATON_FILE_SUFFIX)) {
				String name = filename.substring(0, filename.length() - Base.AUTOMATON_FILE_SUFFIX.length());
				File inrFile = new File(inrAutomataDirectory, filename);
				int size = (int)inrFile.length();
				byte[] bytes = null;
				try (DataInputStream f = new DataInputStream(new FileInputStream(inrFile))) {
					int position = 0, length = size;
					bytes = new byte[length];
					while (length > 0) {
						int read = f.read(bytes, position, length);
						position += read;
						length -= read;
					}
					assert position == size;
				} catch (FileNotFoundException e) {
					this.compiler.addError(String.format("%1$s: File not found '%2$s'",
						name, inrFile.getPath()));
				} catch (IOException e) {
					this.compiler.addError(String.format("%1$s: IOException compiling '%2$s'; %3$s",
						name, inrFile.getPath(), e.getMessage()));
				}
				try {
					this.compiler.reset(inrFile);
					commit = this.parse(bytes);
					if (commit)
						this.compiler.saveTransducer();
				} catch (ModelException | ParseException | CharacterCodingException e) {
					String msg = String.format("%1$s: Exception caught assembling compiler model file; %2$s",
						filename, e.getMessage());
					rtcLogger.log(Level.SEVERE, msg, e);
					this.compiler.addError(msg);
					commit = false;
				}
			}
		}
		return commit;
	}

	private boolean parse(byte[] dfa) throws ParseException, CharacterCodingException {
		this.dfain.clear(dfa);
		this.line = 0;

		if (this.dfain.array[this.dfain.position++] == 'I'
		&& this.dfain.array[this.dfain.position++] == 'N'
		&& this.dfain.array[this.dfain.position++] == 'R') {
			ModelCompiler.Header header = new ModelCompiler.Header(
				this.number(), this.number(), this.number(), this.number(), this.number());
			compiler.putHeader(header);
			for (int last = ++this.line + header.transitions(); this.line < last; this.line++) {
				int from = this.number(), to = this.number(), tape = this.number(), count = this.number();
				boolean isFinal = to == 1 && tape == 0 && count == 0;
				compiler.putTransition(new ModelCompiler.Transition(
					from, to, tape, this.symbol(count), isFinal));
			}
			assert this.dfain.position == this.dfain.limit;
			compiler.putAutomaton();
			return true;
		}
		return false;
	}

	private Bytes symbol(int length) throws ParseException {
		Bytes symbol = new Bytes(Arrays.copyOfRange(
			this.dfain.array, this.dfain.position, this.dfain.position + length));
		this.dfain.position += length;
		byte d = this.dfain.array[this.dfain.position++];
		if (d != '\n')
			throw new ParseException(String.format(
				"Malformed transition symbol; expected newline but received '%1$c' on line %2$d",
				d, this.line), this.dfain.position - 1);
		return symbol;
	}

	private int number() throws ParseException {
		int  d = 0, n = 0;
		int sign = this.dfain.array[this.dfain.position] == '-' ? -1 : 1;
		if (sign < 0) ++this.dfain.position;
		while (this.dfain.position < this.dfain.length) {
			d = this.dfain.array[this.dfain.position++];
			if (d >= '0' && d <= '9')
				n = (10 * n) + (d - '0');
			else
				break;
		}
		if (d == '\t' || d == '\n')
			return sign * n;
		throw new ParseException(String.format(
			"Malformed transition; expected tab or newline but received'%1$c' on line %2$d",
				d, this.line), this.dfain.position);
	}
}
