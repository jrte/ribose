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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Model.Mode;
import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.INamedValue;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Runtime transductor instances are instantiated using {@link IRuntime#newTransductor(ITarget)}.
 * Client applications drive transduction using the Transductor.run() method, 
 * which processes the bound IInput stack until one of the following conditions is 
 * satisfied:
 * <br><br>
 * <ol>
 * <li>the input stack is empty
 * <li>the transducer stack is empty
 * <li>an effector returns RTX_PAUSE
 * <li>the transduction throws an exception.
 * </ol>
 * 
 * @author Kim Briggs
 */
public final class Transductor implements ITransductor, IOutput {
	static final int INITIAL_STACK_SIZE = 8;
	static final int INITIAL_NAMED_VALUE_BYTES = 256;
	static final int INITIAL_NAMED_VALUE_BUFFERS = 256;
	
	private static final Logger logger = Logger.getLogger(Base.RTE_LOGGER_NAME);
	
	/* enumeration of built-in effectors, all below inlined in run(), except (*) */
	private static final int NUL = 0;
	private static final int NIL = 1;
	private static final int PASTE = 2;
	private static final int SELECT = 3;
	private static final int COPY = 4;
	private static final int CUT = 5;
	private static final int CLEAR = 6;
	private static final int COUNT = 7;
	private static final int IN = 8;
	private static final int OUT = 9;
	private static final int MARK = 10;
	private static final int RESET = 11;
	@SuppressWarnings("unused")
	private static final int START = 12;
	private static final int PAUSE = 13;
	private static final int STOP = 14;
	
	private final Model model;
	private IEffector<?>[] effectors;
	private NamedValue selected;
	private NamedValue[] namedValueHandles;
	private Map<Bytes, Integer> namedValueOrdinalMap;
	private final TransducerStack transducerStack;
	private final InputStack inputStack;
	private final CharsetDecoder decoder;
	private final CharsetEncoder encoder;
	private OutputStream output;
	private final boolean isOutEnabled;
	private int errorCount;

	/**
	 *  Constructor
	 *
	 * @param model The runtime model 
	 * @throws ModelException on error
	 */
	Transductor(final Model model, Mode mode) {
		super();
		this.model = model;
		this.effectors = null;
		this.namedValueHandles = null;
		this.namedValueOrdinalMap = null;
		this.output = System.out;
		this.selected = null;
		this.errorCount = 0;
		this.isOutEnabled = System.getProperty("jrte.out.enabled", "true").equals("true");
		this.decoder = Base.newCharsetDecoder();
		this.encoder = Base.newCharsetEncoder();

		if (mode == Mode.run) {
			this.namedValueOrdinalMap = this.model.getNamedValueMap();
			this.namedValueHandles = new NamedValue[this.namedValueOrdinalMap.size()];
			for (final Entry<Bytes, Integer> entry : this.namedValueOrdinalMap.entrySet()) {
				final int valueIndex = entry.getValue();
				byte[] valueBuffer = new byte[INITIAL_NAMED_VALUE_BYTES];
				this.namedValueHandles[valueIndex] = new NamedValue(entry.getKey(), valueIndex, valueBuffer, 0);
			}
			this.selected = this.namedValueHandles[Model.ANONYMOUS_VALUE_ORDINAL];
			this.inputStack = new InputStack(INITIAL_STACK_SIZE, this.model.getSignalCount(), this.namedValueHandles.length);
			this.transducerStack = new TransducerStack(INITIAL_STACK_SIZE);
		} else {
			this.inputStack = null;
			this.transducerStack = null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITarget#getEffectors()
	 */
	@Override
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		return new IEffector<?>[] {
	/* 0*/	new InlineEffector(this, "0"),
	/* 1*/	new InlineEffector(this, "1"),
	/* 2*/	new PasteEffector(this),
	/* 3*/	new SelectEffector(this),
	/* 4*/	new CopyEffector(this),
	/* 5*/	new CutEffector(this),
	/* 6*/	new ClearEffector(this),
	/* 7*/	new CountEffector(this),
	/* 8*/	new InEffector(this),
	/* 9*/	new OutEffector(this),
	/*10*/	new InlineEffector(this, "mark"),
	/*11*/	new InlineEffector(this, "reset"),
	/*12*/	new StartEffector(this),
	/*13*/	new PauseEffector(this),
	/*14*/	new StopEffector(this)
		};
	}

	@Override
	public CharsetDecoder getCharsetDecoder() {
		return this.decoder;
	}

	@Override
	public CharsetEncoder getCharsetEncoder() {
		return this.encoder;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITarget#getName()
	 */
	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#status()
	 */
	@Override
	public Status status() {
		if (this.effectors != null) {
			boolean hasInput = this.inputStack != null && !this.inputStack.isEmpty();
			boolean hasTransducer = this.transducerStack != null && !this.transducerStack.isEmpty();
			if (hasTransducer) {
				return hasInput ? Status.RUNNABLE : Status.PAUSED;
			} else {
				return hasInput ? Status.WAITING : Status.STOPPED;
			}
		} else {
			return Status.NULL;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#output(OutputStream)
	 */
	@Override
	public OutputStream output(OutputStream output) {
		OutputStream out = this.output;
		this.output = output;
		return out;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#push(byte[], int)
	 */
	@Override
	public ITransductor push(final byte[] input, int limit) {
		if (this.status() != Status.NULL) {
			this.inputStack.push(input).limit(limit);
		}
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#push(Signal)
	 */
	@Override
	public ITransductor push(Signal signal) {
		if (this.status() != Status.NULL) {
			this.inputStack.signal(signal.signal());
		}
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#start(Bytes)
	 */
	@Override
	public ITransductor start(final Bytes transducerName) throws ModelException {
		if (this.status() != Status.NULL) {
			this.transducerStack.push(this.model.loadTransducer(this.model.getTransducerOrdinal(transducerName)));
			this.select(Model.ANONYMOUS_VALUE_ORDINAL);
			this.clear();
		}
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#stop()
	 */
	@Override
	public ITransductor stop() throws RiboseException {
		if (this.inputStack != null) {
			this.inputStack.unmark();
			while (!this.inputStack.isEmpty()) {
				if (this.inputStack.peek().hasRemaining()) {
					this.inputStack.peek().clear();
				}
				this.inputStack.pop();
			}
		}
		if (this.transducerStack != null) {
			while (!this.transducerStack.isEmpty()) {
				this.transducerStack.pop();
			}
		}
		if (this.namedValueHandles != null) {
			this.selected = this.namedValueHandles[Model.ANONYMOUS_VALUE_ORDINAL];
			for (NamedValue value : this.namedValueHandles) {
				if (value != null) {
					value.clear();
				}
			}
		}
		if (this.status() == Status.NULL) {
			RiboseException rtx = new RiboseException("run: Transduction is MODEL and inoperable");
			logger.log(Level.SEVERE, rtx.getMessage(), rtx);
			throw rtx;
		}
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#run()
	 */
	@Override
	public ITransductor run() throws RiboseException, DomainErrorException {
		if (this.status() == Status.NULL) {
			return this.stop();
		}
		final int nulSignal = Signal.nul.signal();
		final int eosSignal = Signal.eos.signal();
		TransducerState transducer = null;
		int state = 0, last = -1, token = -1;
		int errorInput = -1, signalInput = -1;
		int[] aftereffects = new int[32];
		Input input = Input.empty;
		this.errorCount = 0;
		try {
T:		do {
				// start a pushed transducer
				transducer = this.transducerStack.peek();
				final int[] inputFilter = transducer.inputFilter;
				final int[][] transitionMatrix = transducer.transitionMatrix;
				final int[] effectorVector = transducer.effectorVector;
				state = transducer.state;
				do {
					// get next input token
					if (signalInput == 0) {
						token = Byte.toUnsignedInt(input.array[input.position++]);
					} else if (signalInput > 0) {
						token = signalInput;
						signalInput = 0;
					} else {
						signalInput = 0;
						input = this.inputStack.peek();
						while (input != Input.empty && !input.hasRemaining()) {
							input = this.inputStack.pop();
						}
						if (input != Input.empty) {
							switch (Base.getReferenceType(input.array)) {
							case Base.TYPE_REFERENCE_SIGNAL:
								token = Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, input.array);
								input.position = input.length;
								break;
							case Base.TYPE_REFERENCE_VALUE:
								this.inputStack.pop();
								NamedValue value = this.namedValueHandles[Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_VALUE, input.array)];
								input = this.inputStack.push(value.getValue()).limit(value.getLength());
								token = Byte.toUnsignedInt(input.array[input.position++]);
								break;
							case Base.TYPE_REFERENCE_NONE:
								token = Byte.toUnsignedInt(input.array[input.position++]);
								break;
							default:
								token = Byte.toUnsignedInt(input.array[input.position++]);
								assert false;
								break;
							}
						} else {
							if (token != eosSignal && token != nulSignal) {
								token = eosSignal;
							} else {
								break T;
							} 
						} 
					}
					// flag input stack condition if at end of frame
					if (input.position >= input.limit) {
						signalInput = -1;
					}
					
					int action = NUL;
					int index = 0;
I:				do {
						last = state;
						// filter token to equivalence ordinal and map ordinal and state to next state and action
						final int transition[] = transitionMatrix[state + inputFilter[token]];
						state = transition[0];
						action = transition[1];
						switch (action) {
						case NIL:
							break;
						case PASTE:
							this.selected.append((byte)token);
							action = NIL;
							break;
						default:
							if (action < NUL) {
								index = -action;
								action = effectorVector[index++];
							} 
							break I;
						}
						if (signalInput == 0) {
							token = input.array[input.position++];
							if (input.position >= input.limit) {
								signalInput = -1;
							}
						} else {
							if (token >= Base.RTE_SIGNAL_BASE) {
								signalInput = -1;
							}
							break I;
						} 
					} while (true);
					
					// continue at top of input loop after a run (nil* paste*)* nil singleton effects
					if (action == NIL) {
						continue;
					}
										
					// invoke a vector of 1 or more effectors and record side effects on transducer and input stacks 
					aftereffects[0] = 0;
					do {
						int effect = IEffector.RTX_NONE;
						switch (action) {
						default:
							if (action > 0) {
								effect = this.effectors[action].invoke();
							} else {
								effect = ((IParameterizedEffector<?,?>)this.effectors[(-1)*action]).invoke(effectorVector[index++]);
							}
							break;
						case NUL:
							if (token == nulSignal) {
								throw new DomainErrorException(this.getErrorInput(last, state, errorInput));
							} else if (token != eosSignal) {
								errorInput = token;
								signalInput = nulSignal;
								++this.errorCount;
							}
							break;
						case NIL:
							break;
						case PASTE:
							this.selected.append((byte)token);
							break;
						case SELECT:
							this.selected = this.namedValueHandles[Model.ANONYMOUS_VALUE_ORDINAL];
							break;
						case COPY:
							this.selected.append(this.namedValueHandles[Model.ANONYMOUS_VALUE_ORDINAL]);
							break;
						case CUT:
							this.selected.append(this.namedValueHandles[Model.ANONYMOUS_VALUE_ORDINAL]);
							this.namedValueHandles[Model.ANONYMOUS_VALUE_ORDINAL].clear();
							break;
						case CLEAR:
							this.selected.clear();
							break;
						case COUNT:
							if (--transducer.countdown[0] <= 0) {
								signalInput = transducer.countdown[1];
								transducer.countdown[0] = 0;
							}
							break;
						case IN:
							this.inputStack.value(this.selected.getValue(), this.selected.getLength());
							effect = IEffector.RTX_PUSH;
							break;
						case OUT: 
							if (this.isOutEnabled && this.selected.getLength() > 0) {
								try {
									this.output.write(this.selected.getValue(), 0, this.selected.getLength());
								} catch (IOException e) {
									throw new EffectorException("Unable to write() to output", e);
								}
							}
							break;
						case MARK:
							this.inputStack.mark();
							break;
						case RESET:
							if (this.inputStack.reset()) {
								effect = IEffector.RTX_PUSH;
							}
							break;
						case PAUSE:
							effect = IEffector.RTX_PAUSE;
							break;
						case STOP:
							effect = popTransducer();
							break;
						}
						if (effect != 0) {
							aftereffects[++aftereffects[0]] = effect;
						}
						// invariant: effector vector at index 0 holds NUL, so singletons (index == 0) always break out of loop here
						action = effectorVector[index++];
					} while (action != NUL);		
					
					// check for transducer or input stack adjustmnent
					if (aftereffects[0] != 0) {
						int breakout = 0;
						for (int i = 1; i <= aftereffects[0]; i++) {
							switch (aftereffects[i]) {
							case IEffector.RTX_PUSH:
							case IEffector.RTX_POP:
								signalInput = -1;
								break;
							case IEffector.RTX_START:
								assert transducer == this.transducerStack.get(this.transducerStack.tos()-1);
								transducer.state = state;
								if (breakout == 0) {
									breakout = -1;
								}
								break;
							case IEffector.RTX_STOP:
								if (breakout == 0) {
									breakout = -1;
								}
								break;
							case IEffector.RTX_COUNT:
								assert (transducer == this.transducerStack.get(this.transducerStack.tos()))
								|| (transducer == this.transducerStack.get(this.transducerStack.tos()-1));
								if (transducer.countdown[0] < 0) {
									transducer.countdown[0] = (int)this.getNamedValue((-1 * transducer.countdown[0]) - 1).asInteger();
								}
								if (transducer.countdown[0] <= 0) {
									signalInput = transducer.countdown[1];
									transducer.countdown[0] = 0;
								}
								break;
							case IEffector.RTX_PAUSE:
							case IEffector.RTX_STOPPED:
								breakout = 1;
								break;
							default:
								assert false;
								break;
							}
						}
						if (breakout == 1) {
							break T;
						} else if (breakout == -1) {
							break;
						}
					}
				} while (this.status().isRunnable());
			} while (this.status().isRunnable());
		} catch (AssertionError e) {
			throw e;
		} finally {
			// Prepare to pause (or stop) transduction
			if (!this.transducerStack.isEmpty()) {
				assert (transducer == this.transducerStack.peek()) || (transducer == this.transducerStack.get(-1));
				transducer.state = state;
			}
		}
		
		// Transduction is paused or stopped; if paused it will resume on next call to run()
		return this;
	}

		/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#hasMark()
	 */
	@Override
	public boolean hasMark() {
		return this.inputStack.hasMark();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#recycle()
	 */
	@Override
	public byte[] recycle(byte[] bytes) {
		return this.inputStack.recycle(bytes);
	}

/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IOutput#getErrorCount()
	 */
	@Override
	public int getErrorCount() {
		return this.errorCount;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IOutput#getValueOrdinal(Bytes)
	 */
	@Override
	public int getValueOrdinal(final Bytes valueName) {
		if (this.namedValueOrdinalMap.containsKey(valueName)) {
			return this.namedValueOrdinalMap.get(valueName);
		}
		return -1;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IOutput#getSelectedOrdinal()
	 */
	@Override
	public int getSelectedOrdinal() {
		assert this.selected != null;
		return (this.selected != null) ? this.selected.getOrdinal() : -1;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IOutput#getNamedValue(int)
	 */
	@Override
	public INamedValue getNamedValue(final int nameOrdinal) {
		assert this.namedValueHandles != null;
		if (this.namedValueHandles != null && nameOrdinal < this.namedValueHandles.length) {
			return this.namedValueHandles[nameOrdinal];
		} else {
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IOutput#getNamedValue(String)
	 */
	@Override
	public INamedValue getNamedValue(final Bytes valueName) {
		return this.getNamedValue(this.getValueOrdinal(valueName));
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.IOutput#getSelectedValue()
	 */
	@Override
	public INamedValue getSelectedValue() {
		assert this.selected != null;
		return this.selected;
	}

	byte[] copyNamedValue(final int nameIndex) {
		NamedValue value = this.namedValueHandles[nameIndex];
		assert value != null;
		if (value != null && value.getValue() != null) {
			return Arrays.copyOf(value.getValue(), this.namedValueHandles[nameIndex].getLength());
		} else {
			return Model.EMPTY;
		}
	}

	Model getModel() {
		return this.model;
	}

	void setEffectors(IEffector<?>[] effectors) {
		this.effectors = effectors;
	}

	void setNamedValueOrdinalMap(Map<Bytes, Integer> namedValueOrdinalMap) {
		this.namedValueOrdinalMap = namedValueOrdinalMap;
		if (this.namedValueOrdinalMap.size() > 0) {
			this.namedValueHandles = new NamedValue[this.namedValueOrdinalMap.size()];
			for (final Entry<Bytes, Integer> entry : this.namedValueOrdinalMap.entrySet()) {
				final int valueIndex = entry.getValue();
				byte[] valueBuffer = new byte[INITIAL_NAMED_VALUE_BYTES];
				this.namedValueHandles[valueIndex] = new NamedValue(entry.getKey(), valueIndex, valueBuffer, 0);
			}
			this.selected = this.namedValueHandles[Model.ANONYMOUS_VALUE_ORDINAL];
		}
	}

	private int select(final int selectionIndex) {
		this.selected = this.namedValueHandles[selectionIndex];
		return IEffector.RTX_NONE;
	}

	private int paste(final byte[] bytes, int start, int length) {
		this.selected.append(bytes);
		return IEffector.RTX_NONE;
	}

	private int copy(final int nameIndex) {
		this.selected.append(this.namedValueHandles[nameIndex]);
		return IEffector.RTX_NONE;
	}

	private int cut(final int nameIndex) {
		this.copy(nameIndex);
		this.namedValueHandles[nameIndex].clear();
		return IEffector.RTX_NONE;
	}

	private int clear(final int nameIndex) {
		assert (nameIndex >= 0) || (nameIndex == -1);
		int index = (nameIndex >= 0) ? nameIndex : this.selected.getOrdinal();
		if (index != Model.CLEAR_ANONYMOUS_VALUE) {
			this.namedValueHandles[index].clear();
		} else {
			clear();
		}
		return IEffector.RTX_NONE;
	}

	private int clear() {
		for (NamedValue nv : this.namedValueHandles) {
			nv.clear();
		}
		return IEffector.RTX_NONE;
	}

	private int count(final int[] countdown) {
		assert countdown.length == 2;
		TransducerState tos = this.transducerStack.peek();
		tos.countdown[0] = countdown[0];
		tos.countdown[1] = countdown[1];
		return IEffector.RTX_COUNT;
	}

	private int in(final byte[][] input) {
		this.inputStack.put(input);
		return IEffector.RTX_PUSH;
	}

	private int pushTransducer(final Integer transducerOrdinal) throws EffectorException {
		try {
			this.transducerStack.push(this.model.loadTransducer(transducerOrdinal));
			return IEffector.RTX_START;
		} catch (final ModelException e) {
			throw new EffectorException(String.format("The start effector failed to load %1$s", this.model.getTransducerName(transducerOrdinal)), e);
		}
	}

	private int popTransducer() {
		this.transducerStack.pop();
		if (this.transducerStack.isEmpty()) {
			return IEffector.RTX_STOPPED;
		} else {
			return IEffector.RTX_STOP;
		}
	}

	private String getErrorInput(int last, int state, int errorInput) {
		TransducerState top = this.transducerStack.peek();
		top.state = state;
		last /= top.inputEquivalents;
		state /= top.inputEquivalents;
		StringBuilder output = new StringBuilder(256);
		output.append(String.format("Domain error on (%1$d~%2$d) in %3$s [%4$d]->[%5$d]\n\tTransducer stack:\n", 
			errorInput, top.inputFilter[errorInput], top.name, last, state));
		for (int i = this.transducerStack.tos(); i >= 0; i--) {
			TransducerState t = this.transducerStack.get(i);
			int s = t.state / t.inputEquivalents;
			output.append(String.format("\t\t%1$20s state:%2$3d; accepting", t.name, s));
			for (int j = 0; j < top.inputEquivalents; j++) {
				if (t.transitionMatrix[t.state + j][1] != Transductor.NUL) {
					output.append(String.format(" (%1$d)->[%2$d]", j, 
						t.transitionMatrix[t.state + j][0] / t.inputEquivalents));
				}
			}
			output.append('\n');
		}
		output.append("\n\tInput stack:\n");
		for (int i = this.inputStack.tos(); i >= 0; i--) {
			final Input input = this.inputStack.get(i);
			if (input.array == null) {
				output.append("\t\t(null)\n");
			} else if (!input.hasRemaining()) {
				output.append("[ ]");
			} else if (Base.isReferenceOrdinal(input.array)) {
				output.append(String.format("\t\t[ !%1$d ]\n", 
					Base.decodeReferenceOrdinal(Base.getReferenceType(input.array), input.array)));
			} else if (input.position < input.length) {
				assert input.position < input.length && input.length <= input.array.length ;
				int position = Math.max(0, input.position - 1);
				int start = Math.max(0, position - 8);
				int end = Math.min(start + 16, input.length);
				String inchar = "";
				int inbyte = -1;
				if (input.array[position] >= 0x20 && input.array[position] < 0x7f) {
					inchar = String.format("%1$2c", (char)input.array[position]);
				} else {
					inchar = String.format("%1$2x", Byte.toUnsignedInt(input.array[position]));
				}
				inbyte = Byte.toUnsignedInt(input.array[position]);
				output.append(String.format("\t\t[ char='%1$s' (0x%2$02X); pos=%3$d; length=%4$d < ", 
					inchar, inbyte, position, input.array.length));
				while (start < end) {
					int ubyte = Byte.toUnsignedInt(input.array[start]);
					int equiv = top.inputFilter[ubyte];
					if ((ubyte < 0x20) || (ubyte > 0x7e)) {
						output.append(String.format((start != position) ? "%1$02X~%2$d " : "[%1$02X~%2$d] ", ubyte, equiv));
					} else {
						output.append(String.format((start != position) ? "%1$c~%2$d " : "[%1$c~%2$d] ", (char)input.array[start], equiv));
					}
					start += 1;
				}
				output.append("> ]\n");
			} else {
				output.append("\t\t[ < end-of-input > ]\n");
			} 
		}
		return output.toString();
	}

	private final class InlineEffector extends BaseEffector<Transductor> {
		private InlineEffector(final Transductor transductor, final String name) {
			super(transductor, name);
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException(String.format("Cannot invoke inline effector '%1$s'", super.getName()));
		}
	}

	private final class PasteEffector extends BaseInputOutputEffector {
		private PasteEffector(final Transductor transductor) {
			super(transductor, "paste");
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException("Cannot invoke inline effector 'paste'");
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			for (byte[] bytes : super.getParameter(parameterIndex)) {
				if (Base.getReferenceType(bytes) == Base.TYPE_REFERENCE_VALUE) {
					int valueOrdinal = Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_VALUE, bytes);
					NamedValue value = (NamedValue)super.getTarget().getNamedValue(valueOrdinal);
					assert value != null;
					if (value != null) {
						super.getTarget().paste(value.getValue(), 0, value.getLength());
					}
				} else {
					super.getTarget().paste(bytes, 0, bytes.length);
				}
			}
			return IEffector.RTX_NONE;
		}
	}

	private final class SelectEffector extends BaseNamedValueEffector {
		private SelectEffector(final Transductor transductor) {
			super(transductor, "select");
		}

		@Override
		public int invoke() throws EffectorException {
			return super.getTarget().select(0);
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int valueOrdinal = super.getParameter(parameterIndex);
			return (valueOrdinal != 1) ? super.getTarget().select(valueOrdinal) : IEffector.RTX_NONE;
		}
	}

	private final class CopyEffector extends BaseNamedValueEffector {
		private CopyEffector(final Transductor transductor) {
			super(transductor, "copy");
		}

		@Override
		public int invoke() throws EffectorException {
			assert false;
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int valueOrdinal = super.getParameter(parameterIndex);
			return (valueOrdinal != 1) ? super.getTarget().copy(valueOrdinal) : IEffector.RTX_NONE;
		}
	}

	private final class CutEffector extends BaseNamedValueEffector {
		private CutEffector(final Transductor transductor) {
			super(transductor, "cut");
		}

		@Override
		public int invoke() throws EffectorException {
			return super.getTarget().cut(0);
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int valueOrdinal = super.getParameter(parameterIndex);
			return (valueOrdinal != 1) ? super.getTarget().cut(valueOrdinal) : IEffector.RTX_NONE;
		}
	}

	private final class ClearEffector extends BaseNamedValueEffector {
		private ClearEffector(final Transductor transductor) {
			super(transductor, "clear");
		}

		@Override
		public int invoke() throws EffectorException {
			return super.getTarget().clear(-1);
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			final int nameIndex = super.getParameter(parameterIndex);
			return super.getTarget().clear(nameIndex);
		} 
	}

	private final class InEffector extends BaseInputOutputEffector {
		private InEffector(final Transductor transductor) {
			super(transductor, "in");
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException("The default in[] effector is inlined");
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().in(super.getParameter(parameterIndex));
		}
	}

	private final class OutEffector extends BaseInputOutputEffector {
		private final boolean isOutEnabled;

		private OutEffector(final Transductor transductor) {
			super(transductor, "out");
			this.isOutEnabled = super.target.isOutEnabled;
		}
		
		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_NONE;
		}
		
		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (this.isOutEnabled) {
				for (final byte[] bytes : super.getParameter(parameterIndex)) {
					try {
						if (Base.isReferenceOrdinal(bytes)) {
							assert Base.getReferenceType(bytes) == Base.TYPE_REFERENCE_VALUE;
							int ordinal = Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_VALUE, bytes);
							NamedValue handle = (NamedValue)super.getTarget().getNamedValue(ordinal);
							super.target.output.write(handle.getValue(), 0, handle.getLength());
						} else {
							super.target.output.write(bytes, 0, bytes.length);
						}
					} catch (IOException e) {
						throw new EffectorException("Unable to write() to output", e);
					}
				}
			}
			return IEffector.RTX_NONE;
		}
	}
	
	private final class CountEffector extends BaseParameterizedEffector<Transductor, int[]> {
		
		private CountEffector(final Transductor transductor) {
			super(transductor, "count");
		}
		
		@Override
		public void newParameters(final int parameterCount) {
			super.parameters = new int[parameterCount][];
		}
		
		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException(String.format("Cannot invoke inline effector '%1$s'", super.getName()));
		}

		@Override
		public int[] compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 2) {
				throw new TargetBindingException(String.format("%1$S.%2$S: effector requires two parameters",
					super.getTarget().getName(), super.getName()));
			}
			int count = -1;
			assert !Base.isReferenceOrdinal(parameterList[0]) : "Reference ordinal presented for <count> to CountEffector[<count> <signal>]";
			byte type = Base.getReferentType(parameterList[0]);
			if (type == Base.TYPE_REFERENCE_VALUE) {
				Bytes valueName = new Bytes(Base.getReferenceName(parameterList[0]));
				int valueOrdinal = super.target.getValueOrdinal(valueName);
				if (valueOrdinal >= 0) {
					count = -1 * (1 + valueOrdinal);
				} else {
					throw new TargetBindingException(String.format("%1$s.%2$s: Named value %3$s is not found",
						super.getTarget().getName(), super.getName(), valueName.toString()));
				}
			} else if (type == Base.TYPE_REFERENCE_NONE) {
				count = Base.decodeInt(parameterList[0], parameterList[0].length);
			}
			assert !Base.isReferenceOrdinal(parameterList[1]) : "Reference ordinal presented for <signal> to CountEffector[<count> <signal>]";
			if (Base.getReferentType(parameterList[1]) == Base.TYPE_REFERENCE_SIGNAL) {
				Bytes signalName = new Bytes(Base.getReferenceName(parameterList[1]));
				int signalOrdinal = super.getTarget().getModel().getSignalOrdinal(signalName);
				assert signalOrdinal >= Signal.nul.signal();
				super.setParameter(parameterIndex, new int[] { count, signalOrdinal });
				return super.getParameter(parameterIndex);
			} else {
				throw new TargetBindingException(String.format("%1$s.%2$s: invalid signal '%3$%s' for count effector",
					super.getTarget().getName(), super.getName(), Bytes.decode(super.decoder, parameterList[1], parameterList[1].length)));
			}		
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().count(super.getParameter(parameterIndex));
		}
	}

	private final class StartEffector extends BaseParameterizedEffector<Transductor, Integer> {
		private StartEffector(final Transductor transductor) {
			super(transductor, "start");
		}

		@Override
		public void newParameters(final int parameterCount) {
			super.parameters = new Integer[parameterCount];
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException("The start effector requires a parameter");
		}

		@Override
		public Integer compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The start effector accepts at most one parameter");
			}
			assert !Base.isReferenceOrdinal(parameterList[0]);
			if (Base.getReferentType(parameterList[0]) == Base.TYPE_REFERENCE_TRANSDUCER) {
				final Bytes name = new Bytes(Base.getReferenceName(parameterList[0]));
				final int ordinal = super.getTarget().getModel().getTransducerOrdinal(name);
				if (ordinal >= 0) {
					super.setParameter(parameterIndex, ordinal);
				} else {
					throw new TargetBindingException(String.format("Null transducer reference for start effector: %s", name.toString()));
				}
				return ordinal;
			} else {
				throw new TargetBindingException(String.format("Invalid transducer reference `$s` for start effector, requires type indicator ('$c') before the transducer name", 
					new Bytes(parameterList[0]).toString(), Base.TYPE_REFERENCE_TRANSDUCER));
			}
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().pushTransducer(super.getParameter(parameterIndex));
		}
	}

	private final class PauseEffector extends BaseEffector<Transductor> {
		private PauseEffector(final Transductor transductor) {
			super(transductor, "pause");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_PAUSE;
		}
	}

	private final class StopEffector extends BaseEffector<Transductor> {
		private StopEffector(final Transductor transductor) {
			super(transductor, "stop");
		}

		@Override
		public int invoke() throws EffectorException {
			return this.getTarget().popTransducer();
		}
	}
}
