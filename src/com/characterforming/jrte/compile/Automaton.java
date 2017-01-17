/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.CompilationException;
import com.characterforming.jrte.compile.array.Bytes;

class Automaton {
	private final static Logger logger = Logger.getLogger(Automaton.class.getName());
	private final String name;
	private final HashMap<Integer, Integer>[] stateMaps;
	private final HashSet<Bytes>[] symbolMaps;
	private final HashMap<Integer, ArrayList<Transition>> transitions;
	private final HashSet<Integer> finalStates;
	private final Charset charset;
	private final ArrayList<String> errors;

	@SuppressWarnings("unchecked")
	public Automaton(final String name, final Charset charset) {
		this.name = name;
		this.charset = charset;
		this.stateMaps = new HashMap[10];
		this.symbolMaps = new HashSet[10];
		this.transitions = new HashMap<Integer, ArrayList<Transition>>(10 << 10);
		this.finalStates = new HashSet<Integer>();
		this.errors = new ArrayList<String>();
	}

	public void load(final File inrfile) throws IOException, CompilationException {
		final DataInputStream f = new DataInputStream(new FileInputStream(inrfile));
		final byte[] tapeHeader = new byte[2];
		if (f.read(tapeHeader) != 2 || tapeHeader[0] > 2 || tapeHeader[1] != 10) {
			String error = String.format("Malformed tape header for INR automaton %1$s (%2$d tapes)", inrfile.getPath(), tapeHeader[0]);
			this.error(error);
			throw new CompilationException(error);
		}
		for (Transition t = new Transition(this.charset, this.errors); t.load(f); t = new Transition(this.charset, this.errors)) {
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
	}

	public Charset getDecoder() {
		return this.charset;
	}

	public int getRteState(final int tape, final int inrState) throws CompilationException {
		final Integer rteInS = tape < this.stateMaps.length && this.stateMaps[tape] != null ? this.stateMaps[tape].get(inrState) : null;
		if (rteInS == null) {
			throw new CompilationException(String.format("No RTE state for INR tape %1$d state %2$d", tape, inrState));
		}
		return rteInS;
	}

	public Integer[] getInrStates(final int tape) {
		final HashMap<Integer, Integer> inrRteStateMap = tape < this.stateMaps.length && this.stateMaps[tape] != null ? this.stateMaps[tape] : null;
		if (inrRteStateMap != null) {
			Integer[] inrStates = new Integer[inrRteStateMap.size()];
			inrStates = inrRteStateMap.keySet().toArray(inrStates);
			Arrays.sort(inrStates);
			return inrStates;
		}
		return null;
	}

	public byte[][] getSymbolBytes(final int tape) {
		final HashSet<Bytes> symbolBytes = tape < this.stateMaps.length && this.symbolMaps[tape] != null ? this.symbolMaps[tape] : null;
		if (symbolBytes != null) {
			int i = 0;
			final byte[][] bytesArray = new byte[symbolBytes.size()][];
			for (final Bytes bytes : symbolBytes) {
				bytesArray[i++] = bytes.getBytes();
			}
			return bytesArray;
		}
		return null;
	}

	public String[] getSymbols(final byte[][] bytesArray) {
		final String[] symbols = new String[bytesArray.length];
		for (int i = 0; i < symbols.length; i++) {
			symbols[i] = this.charset.decode(ByteBuffer.wrap(bytesArray[i])).toString();
		}
		return symbols;
	}

	public char[] getChars(final byte[] bytes) {
		final CharBuffer charbuf = this.charset.decode(ByteBuffer.wrap(bytes));
		final char[] chars = new char[charbuf.length()];
		charbuf.get(chars);
		return chars;
	}

	public boolean isInrStateFinal(final int inrState) {
		return this.finalStates.contains(inrState);
	}

	public ArrayList<Transition> getInrTransitions(final int inrState) {
		return this.transitions.get(inrState);
	}

	public String getName() {
		return this.name;
	}

	public void error(final String message) {
		if (!this.errors.contains(message)) {
			this.errors.add(message);
		}
	}

	public ArrayList<String> getErrors() {
		return this.errors;
	}
}
