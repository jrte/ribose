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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.CompilationException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.Bytes;

class Automaton {
	protected final RuntimeModel model;
	private final Bytes name;
	private final HashMap<Integer, Integer>[] stateMaps;
	private final HashSet<Bytes>[] symbolMaps;
	private final HashMap<Integer, ArrayList<Transition>> transitions;
	private final HashSet<Integer> finalStates;
	private final ArrayList<String> errors;

	private final static Logger logger = Logger.getLogger(Base.RTC_LOGGER_NAME);

	@SuppressWarnings("unchecked")
	Automaton(final Bytes name, final RuntimeModel model) {
		this.name = name;
		this.model = model;
		this.stateMaps = (HashMap<Integer, Integer>[])new HashMap<?,?>[10];
		this.symbolMaps = (HashSet<Bytes>[])new HashSet<?>[10];
		this.transitions = new HashMap<Integer, ArrayList<Transition>>(10 << 10);
		this.finalStates = new HashSet<Integer>();
		this.errors = new ArrayList<String>();
	}

	Bytes load(final File inrfile) throws IOException, CompilationException {
		DataInputStream f = null;
		byte input = 0;
		try {
			f = new DataInputStream(new FileInputStream(inrfile));
			byte bytes[] = new byte[16];
			int index = 0;
			input = f.readByte();
			while (input != '\t') {
				bytes[index++] = input;
				input = f.readByte();
			}
			Bytes version = Bytes.getBytes(bytes, 0, index);
			int header[] = new int[4];
			for (index = 0; index < header.length; index++) {
				int i = 0;
				input = f.readByte();
				do {
					if (input >= '0' && input <= '9') {
						i = (i * 10) + (input - '0');
					} else if (input == '\t' || input == '\n') {
						header[index++] = i;
						i = 0;
					} else {
						throw new CompilationException(String.format(
							"Digit expected, received '%1$c' (0x%1$x) in header line of %2$s",
							(char) input, inrfile.getPath()));
					}
					if (input != '\n') {
						input = f.readByte();
					}
				} while (input != '\n');
				header[index] = i;
			}
			if (header[0] > 3) {
				throw new CompilationException(String.format(
					"Too many tapes (%1$d, at most 3 tapes expected) in header line of %2$s", header[0], inrfile.getPath()));
			}	
			if (header[3] == 0) {
				throw new CompilationException(String.format(
					"Unexpected end of header line of %1$s", inrfile.getPath()));
			}
			int line = 2;
			for (Transition t = new Transition(this.errors); t.load(inrfile.getPath(), f, line++); t = new Transition(this.errors)) {
				if (t.isValid()) {
					if (!t.isFinal()) {
						HashMap<Integer, Integer> rteStates = this.stateMaps[t.getTape()];
						if (rteStates == null) {
							rteStates = this.stateMaps[t.getTape()] = new HashMap<Integer, Integer>(256);
						}
						if (rteStates.get(t.getInS()) == null) {
							rteStates.put(t.getInS(), rteStates.size());
						}
						HashSet<Bytes> rteSymbols = this.symbolMaps[t.getTape()];
						if (rteSymbols == null) {
							rteSymbols = this.symbolMaps[t.getTape()] = new HashSet<Bytes>(256);
						}
						rteSymbols.add(new Bytes(t.getBytes()));
						switch (t.getTape()) {
						case 0:
							this.compileInputToken(t.getBytes());
							break;
						case 1:
							break;
						case 2:
							this.compileParameterToken(t.getBytes());
							break;
						}
						if (t.getTape() == 2) {
						}
					} else {
						this.finalStates.add(t.getInS());
					}
					ArrayList<Transition> inrST = this.transitions.get(t.getInS());
					if (inrST == null) {
						inrST = new ArrayList<Transition>(16);
						this.transitions.put(t.getInS(), inrST);
					}
					inrST.add(t);
				}
				Automaton.logger.log(Level.FINEST, t.toString());
			}
			for (final ArrayList<Transition> a : this.transitions.values()) {
				a.trimToSize();
			}
			return version;
		} catch (FileNotFoundException e) {
			throw new CompilationException(String.format("FileNotFoundException loading '%1$s'", inrfile.getPath()), e);
		} catch (IOException e) {
			throw new CompilationException(String.format("IOException loading '%1$s'", inrfile.getPath()), e);
		} finally {
			if (f != null) {
				f.close();
			}
		}
	}

	private byte[] compileInputToken(byte[] bytes) throws CompilationException {
		if (bytes.length > 1) {
			if (Base.TYPE_REFERENCE_NONE == Base.getReferenceType(bytes)) {
				this.model.addSignal(new Bytes(bytes));
			} else {
				String type = null;
				switch(bytes[0]) {
				case Base.TYPE_REFERENCE_TRANSDUCER:
					type = "transducer";
					break;
				case Base.TYPE_REFERENCE_VALUE:
					type = "value name";
					break;
				case Base.TYPE_REFERENCE_SIGNAL:
					type = "signal";
					break;
				default:
					type = "invalid";
					break;
				}
				throw new CompilationException(String.format(
					"Invalid input token '%1$s' on tape 0 of type %2$s",
					Bytes.decode(bytes, bytes.length), type));
			}
		}
		return bytes;
	}

	private byte[] compileParameterToken(byte[] bytes) {
		if (bytes.length > 1) {
			switch(bytes[0]) {
			case Base.TYPE_REFERENCE_TRANSDUCER:
				this.model.addTransducer(Bytes.getBytes(bytes, 1, bytes.length));
				break;
			case Base.TYPE_REFERENCE_VALUE:
				this.model.addNamedValue(Bytes.getBytes(bytes, 1, bytes.length));
				break;
			case Base.TYPE_REFERENCE_SIGNAL:
				this.model.addSignal(Bytes.getBytes(bytes, 1, bytes.length));
				break;
			default:
				break;
			}
		}
		return bytes;
	}

	int getRteState(final int tape, final int inrState) throws CompilationException {
		final Integer rteInS = tape < this.stateMaps.length && this.stateMaps[tape] != null ? this.stateMaps[tape].get(inrState) : null;
		if (rteInS == null) {
			throw new CompilationException(String.format("No RTE state for INR tape %1$d state %2$d", tape, inrState));
		}
		return rteInS;
	}

	Integer[] getInrStates(final int tape) {
		final HashMap<Integer, Integer> inrRteStateMap = tape < this.stateMaps.length && this.stateMaps[tape] != null ? this.stateMaps[tape] : null;
		if (inrRteStateMap != null) {
			Integer[] inrStates = new Integer[inrRteStateMap.size()];
			inrStates = inrRteStateMap.keySet().toArray(inrStates);
			Arrays.sort(inrStates);
			return inrStates;
		}
		return null;
	}

	Bytes[] getSymbolBytes(final int tape) {
		final HashSet<Bytes> symbolBytes = tape < this.stateMaps.length && this.symbolMaps[tape] != null ? this.symbolMaps[tape] : null;
		if (symbolBytes != null) {
			return symbolBytes.toArray(new Bytes[symbolBytes.size()]);
		}
		return null;
	}

	boolean isInrStateFinal(final int inrState) {
		return this.finalStates.contains(inrState);
	}

	ArrayList<Transition> getInrTransitions(final int inrState) {
		return this.transitions.get(inrState);
	}

	String getName() {
		return this.name.toString();
	}

	void error(final String message) {
		if (!this.errors.contains(message)) {
			this.errors.add(message);
		}
	}

	ArrayList<String> getErrors() {
		return this.errors;
	}
}
