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
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Model.TargetMode;
import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IField;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransductor;
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
 * Runtime transductor instances are instantiated using {@link IRuntime#newTransductor(ITarget)}
 * presenting a collection of {@link IEffector} and {@link IParameterizedEffector}
 * instances. Client applications drive transduction using the Transductor.run() method,
 * which processes the input stack until one of the following conditions is satisfied:
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
	static final int INITIAL_FIELD_VALUE_BYTES = 256;
	static final int INITIAL_FIELD_VALUE_BUFFERS = 256;

	/* enumeration of base effectors, all below inlined in run(), except (*) */
	private static final int NUL = 0;
	private static final int NIL = 1;
	private static final int PASTE = 2;
	private static final int SELECT = 3;
	private static final int COPY = 4;
	private static final int CUT = 5;
	private static final int CLEAR = 6;
	private static final int COUNT = 7;
	@SuppressWarnings("unused")
	private static final int SIGNAL = 8;
	private static final int IN = 9;
	private static final int OUT = 10;
	private static final int MARK = 11;
	private static final int RESET = 12;
	@SuppressWarnings("unused")
	private static final int START = 13;
	private static final int PAUSE = 14;
	private static final int STOP = 15;
	@SuppressWarnings("unused")
	private static final int MSUM = 16;
	@SuppressWarnings("unused")
	private static final int MPRODUCT = 17;
	@SuppressWarnings("unused")
	private static final int MSCAN = 18;

	private final int Mnone = 0;
	private final int Msum = 1;
	private final int Mproduct = 2;
	private final int Mscan = 3;
	private Signal prologue;
	private final Model model;
	private final TargetMode mode;
	private TransducerState transducer;
	private IEffector<?>[] effectors;
	private Field selected;
	private Field[] fieldHandles;
	private Map<Bytes, Integer> fieldOrdinalMap;
	private final TransducerStack transducerStack;
	private final InputStack inputStack;
	private int matchMode;
	private long[] matchSum;
	private int[] matchProduct;
	private int matchPosition;
	private int matchByte;
	private int errorInput;
	private final int signalLimit;
	private OutputStream output;
	private final Logger rtcLogger;
	private final Logger rteLogger;
	private final ITransductor.Metrics metrics;

	/**
	 *  Constructor
	 *
	 * @param model The runtime model
	 * @throws ModelException on error
	 */
	Transductor(final Model model, TargetMode mode) {
		super();
		this.model = model;
		this.mode = mode;
		this.prologue = null;
		this.effectors = null;
		this.transducer = null;
		this.fieldHandles = null;
		this.fieldOrdinalMap = null;
		this.output = System.getProperty("jrte.out.enabled", "true").equals("true") ? System.out : null;
		this.selected = null;
		this.matchMode = Mnone;
		this.matchSum = null;
		this.matchProduct = null;
		this.matchPosition = 0;
		this.matchByte = 0;
		this.errorInput = -1;
		this.signalLimit = this.model.getSignalLimit();
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.metrics = new ITransductor.Metrics();

		if (this.mode == TargetMode.run) {
			this.fieldOrdinalMap = this.model.getFieldMap();
			this.fieldHandles = new Field[this.fieldOrdinalMap.size()];
			for (final Entry<Bytes, Integer> entry : this.fieldOrdinalMap.entrySet()) {
				final int fieldIndex = entry.getValue();
				byte[] fieldBuffer = new byte[INITIAL_FIELD_VALUE_BYTES];
				this.fieldHandles[fieldIndex] = new Field(entry.getKey(), fieldIndex, fieldBuffer, 0);
			}
			this.selected = this.fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL];
			this.inputStack = new InputStack(INITIAL_STACK_SIZE, this.model.getSignalCount(), this.fieldHandles.length);
			this.transducerStack = new TransducerStack(INITIAL_STACK_SIZE);
		} else {
			this.inputStack = null;
			this.transducerStack = null;
		}
	}

	@Override // @see com.characterforming.ribose.ITarget#getEffectors()
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		if (this.model.getModelVersion().equals(Base.RTE_VERSION)) {
			return new IEffector<?>[] {
			/* 0*/ new InlineEffector(this, "0"),
			/* 1*/ new InlineEffector(this, "1"),
			/* 2*/ new PasteEffector(this),
			/* 3*/ new SelectEffector(this),
			/* 4*/ new CopyEffector(this),
			/* 5*/ new CutEffector(this),
			/* 6*/ new ClearEffector(this),
			/* 7*/ new CountEffector(this),
			/* 8*/ new SignalEffector(this),
			/* 9*/ new InEffector(this),
			/*10*/ new OutEffector(this),
			/*11*/ new InlineEffector(this, "mark"),
			/*12*/ new InlineEffector(this, "reset"),
			/*13*/ new StartEffector(this),
			/*14*/ new PauseEffector(this),
			/*15*/ new InlineEffector(this, "stop"),
			/*16*/ new MsumEffector(this),
			/*17*/ new MproductEffector(this),
			/*18*/ new MscanEffector(this)
		};
	} else if (this.model.getModelVersion().equals(Base.RTE_PREVIOUS)) {
			return new IEffector<?>[] {
			/* 0*/ new InlineEffector(this, "0"),
			/* 1*/ new InlineEffector(this, "1"),
			/* 2*/ new PasteEffector(this),
			/* 3*/ new SelectEffector(this),
			/* 4*/ new CopyEffector(this),
			/* 5*/ new CutEffector(this),
			/* 6*/ new ClearEffector(this),
			/* 7*/ new CountEffector(this),
			/* 8*/ new SignalEffector(this),
			/* 9*/ new InEffector(this),
			/*10*/ new OutEffector(this),
			/*11*/ new InlineEffector(this, "mark"),
			/*12*/ new InlineEffector(this, "reset"),
			/*13*/ new StartEffector(this),
			/*14*/ new PauseEffector(this),
			/*15*/ new InlineEffector(this, "stop"),
			/*16*/ new MsumEffector(this),
			/*17*/ new MproductEffector(this),
			/*18*/ new MscanEffector(this)
		};
		} else {
			throw new TargetBindingException(String.format("Unsupported ribose model version '%s'.",
				this.model.getModelVersion()));
		}
	}

	@Override // @see com.characterforming.ribose.IOutput#rtcLogger()
	public Logger getRtcLogger() {
		return this.rtcLogger;
	}

	@Override // @see com.characterforming.ribose.IOutput#getRteLogger()
	public Logger getRteLogger() {
		return this.rteLogger;
	}

	@Override // @see com.characterforming.ribose.ITarget#getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override // @see com.characterforming.ribose.ITransductor#status()
	public Status status() {
		if (this.mode == TargetMode.run) {
			assert this.inputStack != null;
			assert this.transducerStack != null;
			if (this.transducerStack.isEmpty()) {
				return this.inputStack.isEmpty() ? Status.STOPPED : Status.WAITING;
			} else {
				return this.inputStack.isEmpty() ? Status.PAUSED : Status.RUNNABLE;
			}
		} else {
			return Status.NULL;
		}
	}

	@Override // @see com.characterforming.ribose.ITransductor#output(OutputStream)
	public OutputStream output(OutputStream output) {
		OutputStream out = this.output;
		this.output = output;
		return out;
	}

	@Override // @see com.characterforming.ribose.ITransductor#push(byte[], int)
	public ITransductor push(final byte[] input, int limit) {
		assert input.length >= limit;
		if (this.status() != Status.NULL) {
			this.inputStack.push(input, limit);
		}
		return this;
	}

	@Override // @see com.characterforming.ribose.ITransductor#push(Signal)
	public ITransductor signal(Signal signal) {
		if (this.status() != Status.NULL) {
			this.prologue = signal;
		}
		return this;
	}

	@Override // @see com.characterforming.ribose.ITransductor#start(Bytes)
	public ITransductor start(final Bytes transducerName) throws ModelException {
		if (this.status() != Status.NULL) {
			this.transducerStack.push(this.model.loadTransducer(this.model.getTransducerOrdinal(transducerName)));
			this.selected = this.fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL];
			this.clear();
		}
		return this;
	}

	@Override // @see com.characterforming.ribose.ITransductor#stop()
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
		if (this.fieldHandles != null) {
			this.selected = this.fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL];
			for (Field field : this.fieldHandles) {
				if (field != null) {
					field.clear();
				}
			}
		}
		this.matchMode = Mnone;
		this.matchPosition = 0;
		if (this.status() == Status.NULL) {
			RiboseException rtx = new RiboseException("run: Transduction is MODEL and inoperable");
			this.rteLogger.log(Level.SEVERE, rtx.getMessage(), rtx);
			throw rtx;
		}
		return this;
	}

	@Override	// @see com.characterforming.ribose.ITransductor#run()
	public ITransductor run() throws RiboseException, DomainErrorException {
		if (this.status() == Status.NULL) {
			return this.stop();
		}
		this.metrics.reset();
		final int nulSignal = Signal.nul.signal();
		final int eosSignal = Signal.eos.signal();
		int signal = this.prologue != null ? this.prologue.signal() : -1;
		int token = -1, state = 0, last = -1;
		Input input = Input.empty;
		this.prologue = null;
		this.errorInput = -1;
		try {
T:		do {
				// start a pushed transducer
				this.transducer = this.transducerStack.peek();
				final int[] inputFilter = this.transducer.inputFilter;
				final long[] transitionMatrix = this.transducer.transitionMatrix;
				final int[] effectorVector = this.transducer.effectorVector;
				state = this.transducer.state;
I:			do {
					// get next input token
					if (signal > 0) {
						token = signal;
						signal = 0;
					} else if (input.position < input.limit) {
						token = 0xff & (int)input.array[input.position++];
					} else {
						do {
							input = this.inputStack.pop();
							if (input == Input.empty) {
								break T;
							}
						} while (input.position >= input.limit);
						token = 0xff & (int)input.array[input.position++];
					}
					
					// absorb self-referencing (msum,mscan) or sequential (mproduct) transitions with nil effect
					if (token < nulSignal && this.matchMode != Mnone) {
						switch (this.matchMode) {
						case Msum:
							token = sumTrap(input, token);
							break;
						case Mproduct:
							token = productTrap(input, token);
							break;
						case Mscan:
							token = scanTrap(input, token);
							break;
						default:
							assert false;
							break;
						}
						if (token < 0) {
							continue I;
						}
					}

					// trap runs in (nil* paste*)* effector space
					int action = NUL;
S:				do {
						last = state;
						final long transition = transitionMatrix[state + inputFilter[token]];
						state = Transducer.state(transition);
						action = Transducer.action(transition);
						if (action == PASTE) {
							this.selected.append((byte)token);
						} else if (action != NIL) {
							break S;
						}
						if (input.position < input.limit) {
							token = input.array[input.position++];
						} else {
							continue I;
						}
					} while (true);

					// effect action and check for transducer or input stack adjustment
					int aftereffects = effect(action, token, effectorVector);
					if (aftereffects != IEffector.RTX_NONE) {
						if (0 != (aftereffects & IEffector.RTX_INPUT)) {
							input = this.inputStack.peek();
						}
						if (0 != (aftereffects & IEffector.RTX_SIGNAL)) {
							signal = aftereffects >>> 16;
							if ((signal < nulSignal) || (signal >= this.signalLimit)) {
								signal = nulSignal;
							}
						}
						int s = aftereffects & (IEffector.RTX_START | IEffector.RTX_STOP);
						if (s == IEffector.RTX_START) {
							assert this.transducer == this.transducerStack.get(this.transducerStack.tos() - 1);
							this.transducer.state = state;
						}
						if (0 != (aftereffects & (IEffector.RTX_PAUSE | IEffector.RTX_STOPPED))) {
							break T;
						} else if (s != 0) {
							break I;
						}
					}
				} while (this.status().isRunnable());
			} while (this.status().isRunnable());

			if (this.output != null) {
				this.output.flush();
			}
			if (token == nulSignal) {
				throw new DomainErrorException(this.getErrorInput(last, state));
			} else if (token == eosSignal) {
				this.inputStack.pop();
				assert this.inputStack.isEmpty();
			}
		} catch (IOException e) {
			throw new EffectorException("Unable to write() to output", e);
		} finally {
			// Prepare to pause (or stop) transduction
			this.metrics.bytes = this.inputStack.getBytesCount();
			if (transducer == this.transducerStack.peek()) {
				transducer.state = state;
			}
		}

		// Transduction is paused or stopped; if paused it will resume on next call to run()
		return this;
	}

	@Override // @see com.characterforming.ribose.ITransductor#recycle()
	public byte[] recycle(byte[] bytes) {
		return this.inputStack.recycle(bytes);
	}

	@Override // @see com.characterforming.ribose.ITransductor#metrics()
	public Metrics metrics() {
		return this.metrics;
	}

	@Override // @see com.characterforming.ribose.IOutput#getValueOrdinal(Bytes)
	public int getFieldOrdinal(final Bytes fieldName) {
		if (this.fieldOrdinalMap.containsKey(fieldName)) {
			return this.fieldOrdinalMap.get(fieldName);
		}
		return -1;
	}

	@Override // @see com.characterforming.ribose.IOutput#getSelectedOrdinal()
	public int getSelectedOrdinal() {
		assert this.selected != null;
		return (this.selected != null) ? this.selected.getOrdinal() : -1;
	}

	@Override // @see com.characterforming.ribose.IOutput#getField(int)
	public IField getField(final int fieldOrdinal) {
		assert this.fieldHandles != null;
		if (this.fieldHandles != null && fieldOrdinal < this.fieldHandles.length) {
			return this.fieldHandles[fieldOrdinal];
		} else {
			return null;
		}
	}

	@Override // @see com.characterforming.ribose.IOutput#getField(String)
	public IField getField(final Bytes fieldName) {
		return this.getField(this.getFieldOrdinal(fieldName));
	}

	@Override // @see com.characterforming.ribose.IOutput#getSelectedValue()
	public IField getSelectedField() {
		assert this.selected != null;
		return this.selected;
	}

	Model getModel() {
		return this.model;
	}

	void setEffectors(IEffector<?>[] effectors) {
		this.effectors = effectors;
	}

	void setFieldOrdinalMap(Map<Bytes, Integer> fieldOrdinalMap) {
		this.fieldOrdinalMap = fieldOrdinalMap;
		if (this.fieldOrdinalMap.size() > 0) {
			this.fieldHandles = new Field[this.fieldOrdinalMap.size()];
			for (final Entry<Bytes, Integer> entry : this.fieldOrdinalMap.entrySet()) {
				final int fieldIndex = entry.getValue();
				byte[] fieldBuffer = new byte[INITIAL_FIELD_VALUE_BYTES];
				this.fieldHandles[fieldIndex] = new Field(entry.getKey(), fieldIndex, fieldBuffer, 0);
			}
			this.selected = this.fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL];
		}
	}

	// invoke a scalar effector or vector of effectors and record side effects on transducer and input stacks
	private int effect(int action, int token, int[] effectorVector)
	throws IOException, EffectorException {
		int index = 0;
		int parameter = -1;
		if (action >= 0x10000) {
			parameter = Transducer.parameter(action);
			action = Transducer.effector(action);
			assert parameter >= 0;
		} else if (action < 0) {
			index = -action;
		}
		int aftereffects = IEffector.RTX_NONE;
E:	do {
			if (index > 0) {
				action = effectorVector[index++];
				if (action < 0) {
					parameter = effectorVector[index++];
					action *= -1;
				} else if (action != NUL) {
					parameter = -1;
				} else {
					break E;
				}
			}
			if (parameter >= 0) {
				aftereffects |= ((IParameterizedEffector<?,?>)this.effectors[action]).invoke(parameter);
			} else {
				switch (action) {
				case NUL:
					if ((token != Signal.nul.signal() && token != Signal.eos.signal())) {
						this.errorInput = token;
						++this.metrics.errors;
						aftereffects |= IEffector.rtxSignal(Signal.nul.signal());
					} else {
						aftereffects |= IEffector.RTX_STOPPED;
					}
					break;
				case NIL:
					assert false;
					break;
				case PASTE:
					this.selected.append((byte)token);
					break;
				case SELECT:
					this.selected = this.fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL];
					break;
				case COPY:
					this.selected.append(this.fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL]);
					break;
				case CUT:
					this.selected.append(this.fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL]);
					this.fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL].clear();
					break;
				case CLEAR:
					this.selected.clear();
					break;
				case COUNT:
					if (--this.transducer.countdown[0] <= 0) {
						this.transducer.countdown[0] = 0;
						aftereffects |= IEffector.rtxSignal(this.transducer.countdown[1]);
					}
					break;
				case IN:
					this.inputStack.push(this.selected.getValue(), this.selected.getLength());
					aftereffects |= IEffector.RTX_INPUT;
					break;
				case OUT:
					if (this.output != null) {
						this.output.write(this.selected.getValue(), 0, this.selected.getLength());
					}
					break;
				case MARK:
					this.inputStack.mark();
					break;
				case RESET:
					aftereffects |= this.inputStack.reset();
					break;
				case PAUSE:
					aftereffects |= IEffector.RTX_PAUSE;
					break;
				case STOP:
					aftereffects |= this.transducerStack.pop() == null ? IEffector.RTX_STOPPED : IEffector.RTX_STOP;
					break;
				default:
					aftereffects |= this.effectors[action].invoke();
					break;
				}
			}
		} while (index > 0);

		return aftereffects;
	}

	private int sumTrap(Input input, int token) {
		if (token < Signal.nul.signal()) {
			final int anchor = input.position;
			final long[] matchMask = this.matchSum;
			while (0 != (matchMask[token >> 6] & (1L << (token & 0x3f)))) {
				if (input.position < input.limit) {
					token = Byte.toUnsignedInt(input.array[input.position++]);
				} else {
					this.metrics.sum += (input.position - anchor);
					return -1;
				}
			}
			this.metrics.sum += (input.position - anchor);
		}
		this.matchMode = Mnone;
		return token;
	}

	private int productTrap(Input input, int token) {
		if (token < Signal.nul.signal()) {
			final int[] match = this.matchProduct;
			final int mlen = match.length;
			int mpos = this.matchPosition;
			assert mpos <= mlen;
			while (mpos < mlen) {
				if (token == match[mpos++]) {
					if (mpos == mlen) {
						break;
					} else if (input.position < input.limit) {
						token = Byte.toUnsignedInt(input.array[input.position++]);
					} else {
						this.matchPosition = mpos;
						return -1;
					}
				} else {
					this.errorInput = token;
					token = Signal.nul.signal();
					break;
				}
			}
			this.metrics.product += mpos;
		}
		this.matchMode = Mnone;
		return token;
	}

	private int scanTrap(Input input, int token) {
		if (token < Signal.nul.signal()) {
			final int anchor = input.position;
			final int matchByte = this.matchByte;
			while (token != matchByte) {
				if (input.position < input.limit) {
					token = Byte.toUnsignedInt(input.array[input.position++]);
				} else {
					this.metrics.scan += (input.position - anchor);
					return -1;
				}
			}
			this.metrics.scan += (input.position - anchor);
		}
		this.matchMode = Mnone;
		return token;
	}

	private int clear() {
		for (Field nv : this.fieldHandles) nv.clear();
		return IEffector.RTX_NONE;
	}

	private String getErrorInput(int last, int state) {
		TransducerState top = this.transducerStack.peek();
		top.state = state;
		last /= top.inputEquivalents;
		state /= top.inputEquivalents;
		StringBuilder output = new StringBuilder(256);
		output.append(String.format("Domain error on (%1$d~%2$d) in %3$s [%4$d]->[%5$d]%6$s,\tTransducer stack:%7$s",
			this.errorInput, this.errorInput >= 0 ? top.inputFilter[this.errorInput] : this.errorInput, 
			top.name, last, state, Base.lineEnd, Base.lineEnd));
		for (int i = this.transducerStack.tos(); i >= 0; i--) {
			TransducerState t = this.transducerStack.get(i);
			int s = t.state / t.inputEquivalents;
			output.append(String.format("\t\t%1$20s state:%2$3d; accepting", t.name, s));
			for (int j = 0; j < top.inputEquivalents; j++) {
				if (Transducer.action(t.transitionMatrix[t.state + j]) != Transductor.NUL) {
					output.append(String.format(" (%1$d)->[%2$d]", j,
						Transducer.state(t.transitionMatrix[t.state + j]) / t.inputEquivalents));
				}
			}
			output.append(Base.lineEnd);
		}
		output.append(Base.lineEnd).append("\tInput stack:").append(Base.lineEnd);
		for (int i = this.inputStack.tos(); i >= 0; i--) {
			final Input input = this.inputStack.get(i);
			if (input.array == null) {
				output.append("\t\t(null)").append(Base.lineEnd);
			} else if (!input.hasRemaining()) {
				output.append("[ ]").append(Base.lineEnd);
			} else if (Base.isReferenceOrdinal(input.array)) {
				output.append("\t\t [ !").append(Integer.toString(Base.decodeReferenceOrdinal(Base.getReferenceType(input.array), input.array)))
					.append(" ]").append(Base.lineEnd);
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
				output.append("> ]").append(Base.lineEnd);
			} else {
				output.append("\t\t[ < end-of-input > ]").append(Base.lineEnd);
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
				if (Base.getReferenceType(bytes) == Base.TYPE_REFERENCE_FIELD) {
					int fieldOrdinal = Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_FIELD, bytes);
					Field field = (Field)getField(fieldOrdinal);
					assert field != null;
					if (field != null) {
						selected.append(field.getValue());
					}
				} else {
					selected.append(bytes);
				}
			}
			return IEffector.RTX_NONE;
		}
	}

	private final class SelectEffector extends BaseFieldEffector {
		private SelectEffector(final Transductor transductor) {
			super(transductor, "select");
		}

		@Override
		public int invoke() throws EffectorException {
			selected = fieldHandles[Model.ANONYMOUS_FIELD_ORDINAL];
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int fieldOrdinal = super.getParameter(parameterIndex);
			if (fieldOrdinal != 1) {
				selected = fieldHandles[fieldOrdinal];
			}
			return IEffector.RTX_NONE;
		}
	}

	private final class CopyEffector extends BaseFieldEffector {
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
			int fieldOrdinal = super.getParameter(parameterIndex);
			if (fieldOrdinal != 1) {
				selected.append(fieldHandles[fieldOrdinal]);
			}
			return IEffector.RTX_NONE;
		}
	}

	private final class CutEffector extends BaseFieldEffector {
		private CutEffector(final Transductor transductor) {
			super(transductor, "cut");
		}

		@Override
		public int invoke() throws EffectorException {
			return this.invoke(0);
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int fieldOrdinal = super.getParameter(parameterIndex);
			if (fieldOrdinal != 1) {
				selected.append(fieldHandles[fieldOrdinal]);
				fieldHandles[fieldOrdinal].clear();
			}
			return IEffector.RTX_NONE;
		}
	}

	private final class ClearEffector extends BaseFieldEffector {
		private ClearEffector(final Transductor transductor) {
			super(transductor, "clear");
		}

		@Override
		public int invoke() throws EffectorException {
			return clear();
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			final int nameIndex = super.getParameter(parameterIndex);
			assert (nameIndex >= 0) || (nameIndex == -1);
			int index = (nameIndex >= 0) ? nameIndex : selected.getOrdinal();
			if (index != Model.CLEAR_ANONYMOUS_FIELD) {
				fieldHandles[index].clear();
			} else {
				clear();
			}
			return IEffector.RTX_NONE;
		}
	}

	private final class SignalEffector extends BaseParameterizedEffector<Transductor, Integer> {
		private SignalEffector(final Transductor transductor) {
			super(transductor, "signal");
		}

		@Override
		public void newParameters(final int parameterCount) {
			super.parameters = new Integer[parameterCount];
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.rtxSignal(Signal.nil.signal());
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			return IEffector.rtxSignal(super.parameters[parameterIndex]);
		}

		@Override
		public Integer compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The signal effector accepts at most one parameter");
			}
			assert !Base.isReferenceOrdinal(parameterList[0]);
			if (Base.getReferentType(parameterList[0]) == Base.TYPE_REFERENCE_SIGNAL) {
				final Bytes name = new Bytes(Base.getReferenceName(parameterList[0]));
				final int ordinal = getModel().getSignalOrdinal(name);
				if (ordinal >= 0) {
					super.setParameter(parameterIndex, ordinal);
				} else {
					throw new TargetBindingException(String.format("Null signal reference for signal effector: %s", name.toString()));
				}
				return this.getParameter(parameterIndex);
			} else {
				throw new TargetBindingException(String.format("Invalid signal reference `%s` for signal effector, requires type indicator ('%c') before the transducer name",
					new Bytes(parameterList[0]).toString(), Base.TYPE_REFERENCE_SIGNAL));
			}
		}

		@Override
		public String showParameter(int parameterIndex) {
			Integer signal = super.getParameter(parameterIndex);
			byte[] name = getModel().getSignalName(signal);
			return Bytes.decode(super.getDecoder(), name, name.length).toString();
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
			byte[][] parameters = super.getParameter(parameterIndex);
			byte[][] frames = parameters;
			for (int i = 0; i < parameters.length; i++) {
				if (Base.getReferenceType(parameters[i]) == Base.TYPE_REFERENCE_FIELD) {
					if (frames == parameters) {
						frames = Arrays.copyOf(parameters, parameters.length);
					}
					frames[i] = getField(Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_FIELD, frames[i])).copyValue();
				}
			}
			inputStack.put(frames);
			return IEffector.RTX_INPUT;
		}
	}

	private final class OutEffector extends BaseInputOutputEffector {

		private OutEffector(final Transductor transductor) {
			super(transductor, "out");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (super.target.output != null) {
				for (final byte[] bytes : super.getParameter(parameterIndex)) {
					try {
						if (Base.getReferenceType(bytes) == Base.TYPE_REFERENCE_FIELD) {
							Field field = (Field)getField(Base.decodeReferenceOrdinal(Base.TYPE_REFERENCE_FIELD, bytes));
							super.target.output.write(field.getValue(), 0, field.getLength());
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
		public int invoke() throws EffectorException {
			throw new EffectorException(String.format("Cannot invoke inline effector '%1$s'", super.getName()));
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			TransducerState tos = transducerStack.peek();
			assert (super.target.transducer == tos) || (super.target.transducer == super.target.transducerStack.get(super.target.transducerStack.tos()-1));
			int[] countdown = super.getParameter(parameterIndex);
			tos.countdown[1] = countdown[1];
			tos.countdown[0] = countdown[0] < 0 ? (int)getField(-1-countdown[0]).asInteger() : countdown[0];
			return transducer.countdown[0] == 0 ? IEffector.rtxSignal(countdown[1]) : IEffector.RTX_NONE;
		}

		@Override
		public void newParameters(final int parameterCount) {
			super.parameters = new int[parameterCount][];
		}

		@Override
		public int[] compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 2) {
				throw new TargetBindingException(String.format("%1$S.%2$S: effector requires two parameters",
					getName(), super.getName()));
			}
			int count = -1;
			assert !Base.isReferenceOrdinal(parameterList[0]) : "Reference ordinal presented for <count> to CountEffector[<count> <signal>]";
			byte type = Base.getReferentType(parameterList[0]);
			if (type == Base.TYPE_REFERENCE_FIELD) {
				Bytes fieldName = new Bytes(Base.getReferenceName(parameterList[0]));
				int fieldOrdinal = super.target.getFieldOrdinal(fieldName);
				if (fieldOrdinal >= 0) {
					count = -1 * (1 + fieldOrdinal);
				} else {
					throw new TargetBindingException(String.format("%1$s.%2$s: Field %3$s is not found",
						getName(), super.getName(), fieldName.toString()));
				}
			} else if (type == Base.TYPE_REFERENCE_NONE) {
				count = Base.decodeInt(parameterList[0], parameterList[0].length);
			}
			assert !Base.isReferenceOrdinal(parameterList[1]) : "Reference ordinal presented for <signal> to CountEffector[<count> <signal>]";
			if (Base.getReferentType(parameterList[1]) == Base.TYPE_REFERENCE_SIGNAL) {
				Bytes signalName = new Bytes(Base.getReferenceName(parameterList[1]));
				int signalOrdinal = getModel().getSignalOrdinal(signalName);
				assert signalOrdinal >= Signal.nul.signal();
				super.setParameter(parameterIndex, new int[] { count, signalOrdinal });
				return super.getParameter(parameterIndex);
			} else {
				throw new TargetBindingException(String.format("%1$s.%2$s: invalid signal '%3$%s' for count effector",
					getName(), super.getName(), Bytes.decode(super.getDecoder(),
					parameterList[1], parameterList[1].length)));
			}
		}

		@Override
		public String showParameter(int parameterIndex) {
			int[] param = super.parameters[parameterIndex];
			StringBuilder sb= new StringBuilder();
			if (param[0] < 0) {
				byte[] name = getModel().getFieldName(-1 * param[0]);
				byte[] field = new byte[name.length + 1];
				field[0] = Base.TYPE_REFERENCE_FIELD;
				System.arraycopy(name, 0, field, 1, name.length);
				sb.append(Bytes.decode(super.getDecoder(), field, field.length).toString());
			} else {
				sb.append(Integer.toString(param[0]));
			}
			sb.append(" ");
			byte[] signal = this.getTarget().getModel().getSignalName(param[1]);
			sb.append(Bytes.decode(super.getDecoder(), signal, signal.length).toString());
			return sb.toString();
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
				final int ordinal = getModel().getTransducerOrdinal(name);
				if (ordinal >= 0) {
					super.setParameter(parameterIndex, ordinal);
				} else {
					throw new TargetBindingException(String.format("Null transducer reference for start effector: %s", name.toString()));
				}
				return ordinal;
			} else {
				throw new TargetBindingException(String.format("Invalid transducer reference `%s` for start effector, requires type indicator ('%c') before the transducer name",
			 new Bytes(parameterList[0]).toString(), Base.TYPE_REFERENCE_TRANSDUCER));
			}
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			try {
				super.target.transducerStack.push(super.target.model.loadTransducer(super.getParameter(parameterIndex)));
			} catch (final ModelException e) {
				throw new EffectorException(String.format("The start effector failed to load %1$s", 
					super.target.model.getTransducerName(super.getParameter(parameterIndex))), e);
			}
			return IEffector.RTX_START;
		}

		@Override
		public String showParameter(int parameterIndex) {
			int ordinal = super.getParameter(parameterIndex);
			if (ordinal >= 0) {
				byte[] bytes = getModel().getTransducerName(ordinal);
				byte[] name = new byte[bytes.length + 1];
				System.arraycopy(bytes, 0, name, 1, bytes.length);
				name[0] = Base.TYPE_REFERENCE_TRANSDUCER;
				return Bytes.decode(super.getDecoder(), name, name.length).toString();
			} else {
				return "VOID";
			}
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

	private final class MsumEffector extends BaseParameterizedEffector<Transductor, long[]> {
		private MsumEffector(final Transductor transductor) {
			super(transductor, "msum");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (super.target.matchMode == Mnone) {
				super.target.matchMode = Msum;
				super.target.matchSum = super.parameters[parameterIndex];
			} else {
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MSUM=%d",
					super.target.matchMode, Msum));
			}
			return IEffector.RTX_NONE;
		}

		@Override
		public void newParameters(int parameterCount) {
			super.parameters = new long[parameterCount][];
		}
	
		@Override
		public long[] compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The msum effector accepts at most one parameter");
			}
			long[] byteMap = new long[] {0, 0, 0, 0};
			for (byte b : parameterList[0]) {
				final int bint = Byte.toUnsignedInt(b);
				byteMap[bint >> 6] |= 1L << (bint & 0x3f);
			}
			super.setParameter(parameterIndex, byteMap);
			return super.parameters[parameterIndex];
 		}

		@Override
		public String showParameter(int parameterIndex) {
			long[] sum = super.parameters[parameterIndex];
			StringBuilder sb = new StringBuilder();
			int bit = 0, startBit = -1;
			for (int j = 0; j < sum.length; j++) {
				for (int k = 0; k < 64; k++, bit++) {
					if (0 == (sum[j] & (1L << k))) {
						if (startBit >= 0) {
							if (startBit < (bit-2)) {
								if (startBit > 32 && startBit <127) {
									sb.append(String.format(" %c", (char)startBit));
								} else {
									sb.append(String.format(" #%x", startBit));
								}
								if ((bit - 1) > 32 && (bit - 1) <127) {
									sb.append(String.format("-%c", (char)(bit-1)));
								} else {
									sb.append(String.format("-#%x", (bit-1)));
								}
							} else {
								if (startBit > 32 && startBit <127) {
									sb.append(String.format(" %c", (char)startBit));
								} else {
									sb.append(String.format(" #%x", startBit));
								}
							}
							startBit = -1;
						}
					} else if (startBit < 0) {
						startBit = bit;
					}
				}
			}
			if (startBit >= 0) {
				if (bit > (startBit + 1)) {
					if (startBit > 32 && startBit < 127) {
						sb.append(String.format(" %c", (char)startBit));
					} else {
						sb.append(String.format(" #%x", startBit));
					}
					if ((bit - 1) > 32 && (bit - 1) <127) {
						sb.append(String.format(" %c", (char)(bit - 1)));
					} else {
						sb.append(String.format(" #%x", (bit - 1)));
					}
				} else {
					if ((bit - 1) > 32 && (bit - 1) <127) {
						sb.append(String.format(" %c", (char)(bit - 1)));
					} else {
						sb.append(String.format(" #%x", (bit - 1)));
					}
				}
			}
			return sb.toString();
		}
	}

	private final class MproductEffector extends BaseParameterizedEffector<Transductor, int[]> {
		private MproductEffector(final Transductor transductor) {
			super(transductor, "mproduct");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (super.target.matchMode == Mnone) {
				super.target.matchMode = Mproduct;
				super.target.matchProduct = super.parameters[parameterIndex];
				super.target.matchPosition = 0;
			} else {
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MPRODUCT=%d",
					super.target.matchMode, Mproduct));
			}
			return IEffector.RTX_NONE;
		}

		@Override
		public void newParameters(int parameterCount) {
			super.parameters = new int[parameterCount][];
		}
	
		@Override
		public int[] compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The mproduct effector accepts at most one parameter");
			}
			byte[] b = parameterList[0];
			int[] p = new int[b.length];
			for (int i = 0; i < p.length; i++) {
				p[i] = Byte.toUnsignedInt(b[i]);
			}
			super.setParameter(parameterIndex, p);
			return super.parameters[parameterIndex];
 		}

		@Override
		public String showParameter(int parameterIndex) {
			int[] product = super.parameters[parameterIndex];
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < product.length; j++) {
				if (32 < product[j] && 127 > product[j]) {
					sb.append(String.format(" %c", (char)product[j]));
				} else {
					sb.append(String.format(" #%x", product[j]));
				}
			}
			return sb.toString();
		}
	}

	private final class MscanEffector extends BaseParameterizedEffector<Transductor, Integer> {
		private MscanEffector(final Transductor transductor) {
			super(transductor, "mscan");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (super.target.matchMode == Mnone) {
				super.target.matchMode = Mscan;
				super.target.matchByte = super.parameters[parameterIndex];
			} else {
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MSCAN=%d",
					super.target.matchMode, Mscan));
			}
			return IEffector.RTX_NONE;
		}

		@Override
		public void newParameters(int parameterCount) {
			super.parameters = new Integer[parameterCount];
		}
	
		@Override
		public Integer compileParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The mproduct effector accepts at most one parameter");
			}
			super.setParameter(parameterIndex, Byte.toUnsignedInt(parameterList[0][0]));
			return super.parameters[parameterIndex];
 		}

		@Override
		public String showParameter(int parameterIndex) {
			int scanbyte = super.parameters[parameterIndex];
			if (32 < scanbyte && 127 > scanbyte) {
				return String.format("%c", (char)scanbyte);
			} else {
				return String.format("#%x", scanbyte);
			}
		}
	}
}
