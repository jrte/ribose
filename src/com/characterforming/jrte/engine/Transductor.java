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
import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Runtime transductor instances are instantiated using {@link IRuntime#newTransductor(ITarget)}.
 * Client applications drive transduction using the Transductor.run() method, 
 * which processes the bound IInput stack until one of the following conditions is 
 * satisfied: <br>
 * <ol>
 * <li>the input stack is empty
 * <li>the transducer stack is empty
 * <li>an effector returns RTE_EFFECT_PAUSE
 * <li>the transduction throws an exception.
 * </ol>
 * 
 * @author Kim Briggs
 */
public final class Transductor implements ITransductor, ITarget, IOutput {
	private static final boolean isOutEnabled = System.getProperty("jrte.out.enabled", "true").equals("true");
	private static final Logger logger = Logger.getLogger(Base.RTE_LOGGER_NAME);
	static int INITIAL_NAMED_VALUE_BUFFERS = 256;
	static int INITIAL_NAMED_VALUE_BYTES = 256;

	public static final int RTE_EFFECTOR_NUL = 0;
	public static final int RTE_EFFECTOR_NIL = 1;
	public static final int RTE_EFFECTOR_PASTE = 2;
	public static final int RTE_EFFECTOR_SELECT = 3;
	public static final int RTE_EFFECTOR_COPY = 4;
	public static final int RTE_EFFECTOR_CUT = 5;
	public static final int RTE_EFFECTOR_CLEAR = 6;
	public static final int RTE_EFFECTOR_COUNT = 7;
	public static final int RTE_EFFECTOR_IN = 8;
	public static final int RTE_EFFECTOR_OUT = 9;
	public static final int RTE_EFFECTOR_MARK = 10;
	public static final int RTE_EFFECTOR_RESET = 11;
	public static final int RTE_EFFECTOR_START = 12;
	public static final int RTE_EFFECTOR_PAUSE = 13;
	public static final int RTE_EFFECTOR_STOP = 14;

	private Model model;
	private IEffector<?>[] effectors;
	private NamedValue selected;
	private NamedValue[] namedValueHandles;
	private Map<Bytes, Integer> namedValueOrdinalMap;
	private final TransducerStack transducerStack;
	private final InputStack inputStack;
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
		this.errorCount = 0;
		if (mode == Mode.run) {
			this.namedValueOrdinalMap = this.model.getNamedValueMap();
			this.namedValueHandles = new NamedValue[this.namedValueOrdinalMap.size()];
			for (final Entry<Bytes, Integer> entry : this.namedValueOrdinalMap.entrySet()) {
				final int valueIndex = entry.getValue();
				byte[] valueBuffer = new byte[INITIAL_NAMED_VALUE_BYTES];
				this.namedValueHandles[valueIndex] = new NamedValue(entry.getKey(), valueIndex, valueBuffer, 0);
			}
			this.selected = this.namedValueHandles[Base.ANONYMOUS_VALUE_ORDINAL];
			this.inputStack = new InputStack(8, this.model.getSignalCount(), this.namedValueHandles.length);
			this.transducerStack = new TransducerStack(8);
		} else {
			this.namedValueOrdinalMap = null;
			this.namedValueHandles = null;
			this.inputStack = null;
			this.transducerStack = null;
			this.selected = null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITarget#bindEffectors()
	 */
	@Override
	public IEffector<?>[] bindEffectors() throws TargetBindingException {
		return new IEffector<?>[] {
	/* 0*/	new InlineEffector(this, Bytes.encode("0")),
	/* 1*/	new InlineEffector(this, Bytes.encode("1")),
	/* 2*/	new PasteEffector(this),
	/* 3*/	new SelectEffector(this),
	/* 4*/	new CopyEffector(this),
	/* 5*/	new CutEffector(this),
	/* 6*/	new ClearEffector(this),
	/* 7*/	new CountEffector(this),
	/* 8*/	new InEffector(this),
	/* 9*/	new OutEffector(this),
	/*10*/	new InlineEffector(this, Bytes.encode("mark")),
	/*11*/	new InlineEffector(this, Bytes.encode("reset")),
	/*12*/	new StartEffector(this),
	/*13*/	new PauseEffector(this),
	/*14*/	new StopEffector(this)
		};
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
				return hasInput ? Status.RUNNABLE : Status.WAITING;
			} else {
				return hasInput ? Status.PAUSED : Status.STOPPED;
			}
		} else {
			return Status.NULL;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#input(byte[], int)
	 */
	@Override
	public Status input(final byte[] input, int limit) {
		this.inputStack.push(input).limit(limit);
		return this.status();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#signal(int)
	 */
	@Override
	public Status signal(Signal signal) {
		this.inputStack.signal(signal.signal());
		return this.status();
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
	 * @see com.characterforming.ribose.ITransductor#start(Bytes)
	 */
	@Override
	public Status start(final Bytes transducerName) throws ModelException {
		if (this.status() != Status.NULL) {
			int transducerOrdinal = this.model.getTransducerOrdinal(transducerName);
			Transducer transducer = this.model.loadTransducer(transducerOrdinal);
			if (transducer != null) {
				this.transducerStack.push(transducer);
				this.select(Base.ANONYMOUS_VALUE_ORDINAL);
				this.clear();
			} else {
				logger.log(Level.SEVERE, String.format("No transducer named %1$s", 
					transducerName.toString()));
			}
		} else {
			logger.log(Level.SEVERE, "Transduction not bound to target");
		}
		return this.status();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#stop()
	 */
	@Override
	public Status stop() {
		if (this.inputStack != null) {
			this.inputStack.unmark();
			while (!this.inputStack.isEmpty()) {
				this.inputStack.pop();
			}
		}
		if (this.transducerStack != null) {
			while (!this.transducerStack.isEmpty()) {
				this.transducerStack.pop();
			}
		}
		for (NamedValue value : this.namedValueHandles) {
			value.clear();
		}
		this.selected = this.namedValueHandles[Base.ANONYMOUS_VALUE_ORDINAL];
		return this.status();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.ribose.ITransductor#run()
	 */
	@Override
	public Status run() throws RiboseException, DomainErrorException {
		if (this.status() == Status.NULL) {
			RiboseException rtx = new RiboseException("run: Transduction is MODEL and inoperable");
			logger.log(Level.SEVERE, rtx.getMessage(), rtx);
			throw rtx;
		}
		final int nulSignal = Base.Signal.nul.signal();
		final int eosSignal = Base.Signal.eos.signal();
		int errorCount = this.errorCount = 0;
		TransducerState transducer = null;
		int state = 0, last = -1, token = -1;
		int errorInput = -1, signalInput = -1;
		Status status = this.status();
		Input input = Input.empty;
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
					
					int action = RTE_EFFECTOR_NUL;
					int index = 0;
I:				do {
						// flag input stack condition if at end of frame
						if (input.position >= input.limit) {
							signalInput = -1;
						}
						last = state;
						// filter token to equivalence ordinal and map ordinal and state to next state and action
						final int transition[] = transitionMatrix[state + inputFilter[token]];
						state = transition[0];
						action = transition[1];
						switch (action) {
						case RTE_EFFECTOR_NIL:
							break;
						case RTE_EFFECTOR_PASTE:
							this.selected.append((byte)token);
							action = RTE_EFFECTOR_NIL;
							break;
						default:
							if (action < RTE_EFFECTOR_NUL) {
								index = -action;
								action = effectorVector[index++];
							} 
							break I;
						}
						if (signalInput == 0) {
							token = input.array[input.position++];
						} else if (token < Base.RTE_SIGNAL_BASE) {
							assert !this.inputStack.peek().hasRemaining();
							this.inputStack.pop();
							break;
						} else {
							signalInput = -1;
							break I;
						} 
					} while (true);
					
					// invoke a vector of 1 or more effectors and record side effects on transducer and input stacks 
					int effect = IEffector.RTE_EFFECT_NONE;
					do {
						switch (action) {
						default:
							if (action > 0) {
								effect |= this.effectors[action].invoke();
							} else {
								effect |= ((IParameterizedEffector<?,?>)this.effectors[(-1)*action]).invoke(effectorVector[index++]);
							}
							break;
						case RTE_EFFECTOR_NUL:
							if (token == nulSignal) {
								throw new DomainErrorException(this.getErrorInput(last, state, errorInput));
							} else if (token != eosSignal) {
								errorInput = token;
								signalInput = nulSignal;
								++errorCount;
							}
							break;
						case RTE_EFFECTOR_NIL:
							break;
						case RTE_EFFECTOR_PASTE:
							this.selected.append((byte)token);
							break;
						case RTE_EFFECTOR_SELECT:
							this.selected = this.namedValueHandles[Base.ANONYMOUS_VALUE_ORDINAL];
							break;
						case RTE_EFFECTOR_COPY:
							this.selected.append(this.namedValueHandles[Base.ANONYMOUS_VALUE_ORDINAL]);
							break;
						case RTE_EFFECTOR_CUT:
							this.selected.append(this.namedValueHandles[Base.ANONYMOUS_VALUE_ORDINAL]);
							this.namedValueHandles[Base.ANONYMOUS_VALUE_ORDINAL].clear();
							break;
						case RTE_EFFECTOR_CLEAR:
							this.selected.clear();
							break;
						case RTE_EFFECTOR_COUNT:
							if (--transducer.countdown[0] <= 0) {
								effect |= this.in(transducer.countdown[1]);
								transducer.countdown[0] = 0;
							}
							break;
						case RTE_EFFECTOR_IN:
							this.inputStack.value(this.selected.getValue(), this.selected.getLength());
							effect |= IEffector.RTE_EFFECT_PUSH;
							break;
						case RTE_EFFECTOR_OUT: {
							if (Transductor.isOutEnabled && this.selected.getLength() > 0) {
								System.out.write(this.selected.getValue(), 0, this.selected.getLength());
							}
							break;
						}
						case RTE_EFFECTOR_MARK:
							this.inputStack.mark();
							break;
						case RTE_EFFECTOR_RESET:
							if (this.inputStack.reset()) {
								effect |= IEffector.RTE_EFFECT_PUSH;
							}
							break;
						case RTE_EFFECTOR_PAUSE:
							effect |= IEffector.RTE_EFFECT_PAUSE;
							break;
						case RTE_EFFECTOR_STOP:
							effect |= popTransducer();
							break;
						}
						action = effectorVector[index++];
					} while (action != RTE_EFFECTOR_NUL);		
					
					// check for transducer or input stack adjustmnent
					if ((effect != 0) || (token == eosSignal)) {
						status = this.status();
					}
					if (effect != 0) {
						if (0 != (effect & (IEffector.RTE_EFFECT_PUSH | IEffector.RTE_EFFECT_POP))) {
							signalInput = -1;
						}
						if (0 != (effect & IEffector.RTE_EFFECT_START)) {
							assert transducer == this.transducerStack.get(this.transducerStack.tos()-1);
							transducer.state = state;
						}
						if (effect >= IEffector.RTE_EFFECT_PAUSE) {
							break T;
						} else if (0 != (effect & (IEffector.RTE_EFFECT_START | IEffector.RTE_EFFECT_STOP))) {
							break;
						}
					}					
				} while (status == Status.RUNNABLE);
			} while (status == Status.RUNNABLE);
		} finally {
			// Prepare to pause (or stop) transduction
			if (!this.transducerStack.isEmpty()) {
				assert (transducer == this.transducerStack.peek()) || (transducer == this.transducerStack.get(-1));
				transducer.state = state;
			}
		}
		
		// Transduction is paused or stopped; if paused it will resume on next call to run()
		this.errorCount = errorCount;
		return this.status();
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
		assert this.namedValueHandles != null;
		if (this.namedValueHandles != null
		&& this.namedValueOrdinalMap.containsKey(valueName)) {
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
			return Base.EMPTY;
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
			this.selected = this.namedValueHandles[Base.ANONYMOUS_VALUE_ORDINAL];
		}
	}

	private int select(final int selectionIndex) {
		this.selected = this.namedValueHandles[selectionIndex];
		return IEffector.RTE_EFFECT_NONE;
	}

	private int paste(final byte[] bytes, int start, int length) {
		this.selected.append(bytes);
		return IEffector.RTE_EFFECT_NONE;
	}

	private int copy(final int nameIndex) {
		this.selected.append(this.namedValueHandles[nameIndex]);
		return IEffector.RTE_EFFECT_NONE;
	}

	private int cut(final int nameIndex) {
		this.copy(nameIndex);
		this.namedValueHandles[nameIndex].clear();
		return IEffector.RTE_EFFECT_NONE;
	}

	private int clear(final int nameIndex) {
		assert (nameIndex >= 0) || (nameIndex == -1);
		int index = (nameIndex >= 0) ? nameIndex : this.selected.getOrdinal();
		if (index != Base.CLEAR_ANONYMOUS_VALUE) {
			this.namedValueHandles[index].clear();
		} else {
			clear();
		}
		return IEffector.RTE_EFFECT_NONE;
	}

	private int clear() {
		for (NamedValue nv : this.namedValueHandles) {
			nv.clear();
		}
		return IEffector.RTE_EFFECT_NONE;
	}

	private int count(final int[] countdown) {
		assert countdown.length == 2;
		TransducerState tos = this.transducerStack.peek();
		System.arraycopy(countdown, 0, tos.countdown, 0, countdown.length);
		if (tos.countdown[0] < 0) {
			tos.countdown[0] = (int)this.getNamedValue((-1 * tos.countdown[0]) - 1).asInteger();
		}
		if (tos.countdown[0] == 0) {
			return this.in(tos.countdown[1]);
		}
		return IEffector.RTE_EFFECT_NONE;
	}

	private int in(final int signal) {
		assert (signal >= Base.RTE_SIGNAL_BASE && signal < this.model.getSignalLimit());
		this.inputStack.signal(signal);
		return IEffector.RTE_EFFECT_PUSH;
	}

	private int in(final byte[][] input) {
		for (int i = input.length - 1; i >= 0; i--) {
			this.inputStack.push(input[i]);
		}
		return IEffector.RTE_EFFECT_PUSH;
	}

	private int pushTransducer(final Integer transducerOrdinal) throws EffectorException {
		try {
			this.transducerStack.push(this.model.loadTransducer(transducerOrdinal));
			return IEffector.RTE_EFFECT_START;
		} catch (final ModelException e) {
			throw new EffectorException(String.format("The start effector failed to load %1$s", this.model.getTransducerName(transducerOrdinal)), e);
		}
	}

	private int popTransducer() {
		this.transducerStack.pop();
		if (this.transducerStack.isEmpty()) {
			return IEffector.RTE_EFFECT_STOPPED;
		} else {
			return IEffector.RTE_EFFECT_STOP;
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
				if (t.transitionMatrix[t.state + j][1] != Transductor.RTE_EFFECTOR_NUL) {
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
		private InlineEffector(final Transductor transductor, final Bytes name) {
			super(transductor, name);
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException(String.format("Cannot invoke inline effector '%1$s'", super.getName()));
		}
	}

	private final class PasteEffector extends BaseInputOutputEffector {
		private PasteEffector(final Transductor transductor) {
			super(transductor, Bytes.encode("paste"));
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
			return IEffector.RTE_EFFECT_NONE;
		}
	}

	private final class SelectEffector extends BaseNamedValueEffector {
		private SelectEffector(final Transductor transductor) {
			super(transductor, Bytes.encode("select"));
		}

		@Override
		public int invoke() throws EffectorException {
			return super.getTarget().select(0);
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int valueOrdinal = super.getParameter(parameterIndex);
			return (valueOrdinal != 1) ? super.getTarget().select(valueOrdinal) : IEffector.RTE_EFFECT_NONE;
		}
	}

	private final class CopyEffector extends BaseNamedValueEffector {
		private CopyEffector(final Transductor transductor) {
			super(transductor, Bytes.encode("copy"));
		}

		@Override
		public int invoke() throws EffectorException {
			assert false;
			return IEffector.RTE_EFFECT_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int valueOrdinal = super.getParameter(parameterIndex);
			return (valueOrdinal != 1) ? super.getTarget().copy(valueOrdinal) : IEffector.RTE_EFFECT_NONE;
		}
	}

	private final class CutEffector extends BaseNamedValueEffector {
		private CutEffector(final Transductor transductor) {
			super(transductor, Bytes.encode("cut"));
		}

		@Override
		public int invoke() throws EffectorException {
			return super.getTarget().cut(0);
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int valueOrdinal = super.getParameter(parameterIndex);
			return (valueOrdinal != 1) ? super.getTarget().cut(valueOrdinal) : IEffector.RTE_EFFECT_NONE;
		}
	}

	private final class ClearEffector extends BaseNamedValueEffector {
		private ClearEffector(final Transductor transductor) {
			super(transductor, Bytes.encode("clear"));
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
			super(transductor, Bytes.encode("in"));
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
			super(transductor, Bytes.encode("out"));
			this.isOutEnabled = System.getProperty("jrte.out.enabled", "true").equals("true");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTE_EFFECT_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (this.isOutEnabled) {
				for (final byte[] bytes : super.getParameter(parameterIndex)) {
					if (Base.isReferenceOrdinal(bytes)) {
						assert Base.getReferenceType(bytes) == Base.TYPE_REFERENCE_VALUE;
						int ordinal = Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_VALUE, bytes);
						NamedValue handle = (NamedValue)super.getTarget().getNamedValue(ordinal);
						System.out.write(handle.getValue(), 0, handle.getLength());
					} else {
						System.out.write(bytes, 0, bytes.length);
					}
				}
			}
			return IEffector.RTE_EFFECT_NONE;
		}
	}

	private final class CountEffector extends BaseParameterizedEffector<Transductor, int[]> {
		private CountEffector(final Transductor transductor) {
			super(transductor, Bytes.encode("count"));
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
				int valueOrdinal = this.getTarget().getValueOrdinal(valueName);
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
				super.setParameter(parameterIndex, new int[] { count, signalOrdinal });
				return super.getParameter(parameterIndex);
			} else {
				throw new TargetBindingException(String.format("%1$s.%2$s: invalid signal '%3$%s' for count effector",
					super.getTarget().getName(), super.getName(), Bytes.decode(parameterList[1], parameterList[1].length)));
			}		
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().count(super.getParameter(parameterIndex));
		}
	}

	private final class StartEffector extends BaseParameterizedEffector<Transductor, Integer> {
		private StartEffector(final Transductor transductor) {
			super(transductor, Bytes.encode("start"));
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
			super(transductor, Bytes.encode("pause"));
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTE_EFFECT_PAUSE;
		}
	}

	private final class StopEffector extends BaseEffector<Transductor> {
		private StopEffector(final Transductor transductor) {
			super(transductor, Bytes.encode("stop"));
		}

		@Override
		public int invoke() throws EffectorException {
			return this.getTarget().popTransducer();
		}
	}
}
