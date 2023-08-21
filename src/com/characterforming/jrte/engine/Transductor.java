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
import java.util.logging.Logger;

import com.characterforming.jrte.engine.Model.TargetMode;
import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IField;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.IToken;
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
 * Runtime transductor instances are instantiated using {@link IRuntime#transductor(ITarget)}
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

	private static final int SIGNUL = Signal.NUL.signal();
	private static final int SIGEOS = Signal.EOS.signal();

	private static final int MATCH_NONE = 0;
	private static final int MATCH_SUM = 1;
	private static final int MATCH_PRODUCT = 2;
	private static final int MATCH_SCAN = 3;
	
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
	private byte[] matchProduct;
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
		this.matchMode = MATCH_NONE;
		this.matchSum = null;
		this.matchProduct = null;
		this.matchPosition = 0;
		this.matchByte = 0;
		this.errorInput = -1;
		this.signalLimit = this.model.getSignalLimit();
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.metrics = new Metrics();

		if (this.mode == TargetMode.RUN) {
			this.inputStack = new InputStack(INITIAL_STACK_SIZE);
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
		if (this.mode == TargetMode.RUN) {
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
		if (input.length < limit) {
			limit = input.length;
		}
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
		this.matchMode = MATCH_NONE;
		if (this.status() == Status.NULL) {
			throw new RiboseException("run: Transductor instantiated as model for compiler is inoperable in the runtime");
		}
		return this;
	}

	@Override	// @see com.characterforming.ribose.ITransductor#run()
	public ITransductor run() throws RiboseException {
		if (this.transducerStack.isEmpty()) {
			return this;
		}
		this.metrics.reset();
		this.errorInput = -1;
		int token = -1, state = 0, last = -1, signal = 0;
		if (this.prologue != null) {
			signal = this.prologue.signal();
			this.prologue = null;
		}
		Input input = Input.empty;
		try {
T:		do {
				// start a pushed transducer, or resume caller after pushed transducer is popped
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
					} else {
						while (input.position >= input.limit) {
							input = this.inputStack.pop();
							if (input == Input.empty) {
								token = -1;
								break T;
							}
						}
						token = 0xff & input.array[input.position++];
					}
					
					// absorb self-referencing (msum,mscan) or sequential (mproduct) transitions with nil effect
					if (this.matchMode != MATCH_NONE && token < SIGNUL) {
						switch (this.matchMode) {
						case MATCH_SUM:
							token = sumTrap(input, token);
							break;
						case MATCH_PRODUCT:
							token = productTrap(input, token);
							break;
						case MATCH_SCAN:
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
					int action;
S:				do {
						long transition = transitionMatrix[state + inputFilter[token]];
						last = state; state = Transducer.state(transition);
						action = Transducer.action(transition);
						if (action == PASTE) {
							this.selected.append((byte)token);
						} else if (action != NIL) {
							break S;
						}
						if (input.position < input.limit) {
							token = 0xff & input.array[input.position++];
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
							if ((signal < SIGNUL) || (signal >= this.signalLimit)) {
								signal = SIGNUL;
							}
						}
						int stackeffect = aftereffects & (IEffector.RTX_START | IEffector.RTX_STOP);
						if (stackeffect == IEffector.RTX_START) {
							assert this.transducerStack.tos() > 0 && this.transducer == this.transducerStack.get(this.transducerStack.tos() - 1);
							this.transducer.state = state;
						}
						if (0 != (aftereffects & (IEffector.RTX_PAUSE | IEffector.RTX_STOPPED))) {
							break T;
						} else if (stackeffect != 0) {
							break I;
						}
					}
				} while (this.status().isRunnable());
			} while (this.status().isRunnable());

			if (this.output != null) {
				this.output.flush();
			}
			if (token == SIGNUL) {
				throw new DomainErrorException(this.getErrorInput(last, state));
			} else if (token == SIGEOS) {
				this.inputStack.pop();
				assert this.inputStack.isEmpty();
			}
		} catch (IOException e) {
			throw new EffectorException("Unable to write() to output", e);
		} finally {
			// Prepare to pause (or stop) transduction
			this.metrics.bytes = this.inputStack.getBytesRead();
			this.metrics.allocated = this.inputStack.getBytesAllocated();
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
	public void metrics(Metrics accumulator) {
		this.metrics.update(accumulator);
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
		return (this.selected != null) ? this.selected.getOrdinal() : -1;
	}

	@Override // @see com.characterforming.ribose.IOutput#getField(int)
	public IField getField(final int fieldOrdinal) {
		if (this.mode == TargetMode.RUN && this.fieldHandles != null
		&& fieldOrdinal < this.fieldHandles.length) {
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
		assert this.mode == TargetMode.RUN || this.selected == null;
		return this.selected;
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
			index = 0 - action;
		}
		int aftereffects = IEffector.RTX_NONE;
E:	do {
			if (index > 0) {
				action = effectorVector[index++];
				if (action < 0) {
					action = 0 - action;
					parameter = effectorVector[index++];
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
					if ((token != SIGNUL && token != SIGEOS)) {
						++this.metrics.errors;
						this.errorInput = token;
						aftereffects |= IEffector.rtxSignal(SIGNUL);
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
					this.inputStack.push(this.selected.getData(), this.selected.getLength());
					aftereffects |= IEffector.RTX_INPUT;
					break;
				case OUT:
					if (this.output != null) {
						this.output.write(this.selected.getData(), 0, this.selected.getLength());
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
		final int anchor = input.position;
		final long[] matchMask = this.matchSum;
		while (0 != (matchMask[token >> 6] & (1L << (token & 0x3f)))) {
			if (input.position < input.limit) {
				token = 0xff & input.array[input.position++];
			} else {
				this.metrics.sum += (input.position - anchor);
				return -1;
			}
		}
		this.metrics.sum += (input.position - anchor);
		this.matchMode = MATCH_NONE;
		return token;
	}

	private int productTrap(Input input, int token) {
		final byte[] product = this.matchProduct;
		byte match = (byte)(0xff & token);
		int mpos = this.matchPosition;
		assert mpos <= product.length;
		while (mpos < product.length) {
			if (match == product[mpos++]) {
				if (mpos == product.length) {
					break;
				} else if (input.position < input.limit) {
					match = input.array[input.position++];
				} else {
					this.matchPosition = mpos;
					return -1;
				}
			} else {
				this.errorInput = 0xff & match;
				this.metrics.product += mpos;
				this.matchMode = MATCH_NONE;
				return SIGNUL;
			}
		}
		this.metrics.product += mpos;
		this.matchMode = MATCH_NONE;
		return 0xff & match;
	}

	private int scanTrap(Input input, int token) {
		final int anchor = input.position;
		final int matchToken = this.matchByte;
		while (token != matchToken) {
			if (input.position < input.limit) {
				token = 0xff & input.array[input.position++];
			} else {
				this.metrics.scan += (input.position - anchor);
				return -1;
			}
		}
		this.metrics.scan += (input.position - anchor);
		this.matchMode = MATCH_NONE;
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
		StringBuilder message = new StringBuilder(256);
		message.append(String.format("Domain error on (%1$d~%2$d) in %3$s [%4$d]->[%5$d]%n,\tTransducer stack:%n",
			this.errorInput, this.errorInput >= 0 ? top.inputFilter[this.errorInput] : this.errorInput, 
			top.name, last, state));
		for (int i = this.transducerStack.tos(); i >= 0; i--) {
			TransducerState t = this.transducerStack.get(i);
			int s = t.state / t.inputEquivalents;
			message.append(String.format("\t\t%1$20s state:%2$3d; accepting", t.name, s));
			for (int j = 0; j < top.inputEquivalents; j++) {
				if (Transducer.action(t.transitionMatrix[t.state + j]) != Transductor.NUL) {
					message.append(String.format(" (%1$d)->[%2$d]", j,
						Transducer.state(t.transitionMatrix[t.state + j]) / t.inputEquivalents));
				}
			}
			message.append(Base.LINEEND);
		}
		message.append(Base.LINEEND).append("\tInput stack:").append(Base.LINEEND);
		for (int i = this.inputStack.tos(); i >= 0; i--) {
			final Input input = this.inputStack.get(i);
			if (input.array == null) {
				message.append("\t\t(null)").append(Base.LINEEND);
			} else if (!input.hasRemaining()) {
				message.append("[ ]").append(Base.LINEEND);
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
				message.append(String.format("\t\t[ char='%1$s' (0x%2$02X); pos=%3$d; length=%4$d < ",
					inchar, inbyte, position, input.array.length));
				while (start < end) {
					int ubyte = Byte.toUnsignedInt(input.array[start]);
					int equiv = top.inputFilter[ubyte];
					if ((ubyte < 0x20) || (ubyte > 0x7e)) {
						message.append(String.format((start != position) ? "%1$02X~%2$d " : "[%1$02X~%2$d] ", ubyte, equiv));
					} else {
						message.append(String.format((start != position) ? "%1$c~%2$d " : "[%1$c~%2$d] ", (char)input.array[start], equiv));
					}
					start += 1;
				}
				message.append("> ]").append(Base.LINEEND);
			} else {
				message.append("\t\t[ < end-of-input > ]").append(Base.LINEEND);
			}
		}
		return message.toString();
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
			for (IToken token : super.getParameter(parameterIndex)) {
				if (token.getType() == IToken.Type.FIELD) {
					IField field = getField(token.getSymbolOrdinal());
					if (field != null) {
						selected.append(field.copyValue());
					}
				} else if (token.getType() == IToken.Type.LITERAL) {
					selected.append(token.getLiteralValue());
				} else {
					throw new EffectorException(String.format("Invalid token `%1$s` for effector '%2$s'", 
						token.toString(), super.getName()));
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
		public int invoke() throws EffectorException {
			return IEffector.rtxSignal(Signal.NIL.signal());
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			return IEffector.rtxSignal(super.getParameter(parameterIndex));
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#iallocateParameters(int)
		public Integer[] allocateParameters(int parameterCount) {
			return new Integer[parameterCount];
		}
	
			@Override
		public Integer compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The signal effector accepts at most one parameter");
			}
			IToken token = parameterList[0];
			if (token.getType() == IToken.Type.SIGNAL) {
				int ordinal = token.getSymbolOrdinal();
				if (ordinal < 0) {
					throw new TargetBindingException(String.format("Null signal reference for signal effector: %s", token.toString()));
				}
				return ordinal;
			} else {
				throw new TargetBindingException(String.format("Invalid signal reference `%s` for signal effector, requires type indicator ('%c') before the transducer name",
					token.toString(), IToken.SIGNAL_TYPE));
			}
		}

		@Override
		public String showParameterType(int parameterIndex) {
			return "Integer";
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
			IToken[] parameters = super.getParameter(parameterIndex);
			byte[][] tokens = new byte[parameters.length][];
			for (int i = 0; i < parameters.length; i++) {
				if (parameters[i].getType() == IToken.Type.FIELD) {
					IField field = getField(parameters[i].getSymbolOrdinal());
					tokens[i] = (field != null) ? field.copyValue() : Bytes.EMPTY_BYTES;
				} else {
					assert parameters[i].getType() == IToken.Type.LITERAL;
					tokens[i] = parameters[i].getLiteralValue();
				}
			}
			inputStack.put(tokens);
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
				for (final IToken token : super.getParameter(parameterIndex)) {
					try {
						if (token.getType() == IToken.Type.FIELD) {
							Field field = (Field)getField(token.getSymbolOrdinal());
							if (field != null) {
								super.target.output.write(field.getData(), 0, field.getLength());
							}
						} else {
							assert token.getType() == IToken.Type.LITERAL;
							byte[] data = token.getLiteralValue();
							super.target.output.write(data, 0, data.length);
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

		@Override // @see com.characterforming.ribose.IParameterizedEffector#iallocateParameters(int)
		public int[][] allocateParameters(int parameterCount) {
			return new int[parameterCount][];
		}
	
		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			assert (transducer == transducerStack.peek()) || (transducer == transducerStack.get(transducerStack.tos()-1));
			System.arraycopy(super.getParameter(parameterIndex), 0, transducer.countdown, 0, 2);
			if (transducer.countdown[0] < 0) {
				IField field = getField(-1 - transducer.countdown[0]);
				transducer.countdown[0] = (field != null) ? (int)field.asInteger() : -1;
			}
			return transducer.countdown[0] <= 0 ? IEffector.rtxSignal(transducer.countdown[1]) : IEffector.RTX_NONE;
		}

		@Override
		public int[] compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 2) {
				throw new TargetBindingException(String.format("%1$S.%2$S: effector requires two parameters",
					super.getTarget().getName(), super.getName()));
			}
			int count = -1;
			if (parameterList[0].getType() == IToken.Type.FIELD) {
				Bytes fieldName = new Bytes(parameterList[0].getSymbolName());
				int fieldOrdinal = getFieldOrdinal(fieldName);
				if (fieldOrdinal < 0) {
					throw new TargetBindingException(String.format("%1$s.%2$s: Field %3$s not found",
						super.getTarget().getName(), super.getName(), fieldName.toString()));
				}
				count = -1 - fieldOrdinal;
			} else if (parameterList[0].getType() == IToken.Type.LITERAL) {
				byte[] value = parameterList[0].getLiteralValue();
				count = Base.decodeInt(value, value.length);
			} else {
				byte[] value = parameterList[0].getLiteralValue();
				throw new TargetBindingException(String.format("%1$s.%2$s: invalid field|counter '%3$%s' for count effector",
					super.getTarget().getName(), super.getName(), Bytes.decode(super.getDecoder(), value, value.length)));
			}
			if (parameterList[1].getType() == IToken.Type.SIGNAL) {
				int signalOrdinal = parameterList[1].getSymbolOrdinal();
				assert signalOrdinal >= SIGNUL;
				return new int[] { count, signalOrdinal };
			} else {
				throw new TargetBindingException(String.format("%1$s.%2$s: invalid signal '%3$%s' for count effector",
					super.getTarget().getName(), super.getName(), parameterList[1].toString()));
			}
		}

		@Override 
		public String showParameterType(int parameterIndex) {
			return "int[]";
		}
	}

	private final class StartEffector extends BaseParameterizedEffector<Transductor, Integer> {
		private StartEffector(final Transductor transductor) {
			super(transductor, "start");
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException("The start effector requires a parameter");
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#iallocateParameters(int)
		public Integer[] allocateParameters(int parameterCount) {
			return new Integer[parameterCount];
		}
	
		@Override
		public Integer compileParameter(final IToken[] parameterTokens) throws TargetBindingException {
			if (parameterTokens.length != 1) {
				throw new TargetBindingException("The start effector accepts only one parameter");
			}
			if (parameterTokens[0].getType() == IToken.Type.TRANSDUCER) {
				return parameterTokens[0].getSymbolOrdinal();
			} else {
				throw new TargetBindingException(String.format("Invalid transducer reference `%s` for start effector, requires type indicator ('%c') before the transducer name",
					parameterTokens[0].toString(), IToken.TRANSDUCER_TYPE));
			}
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			try {
				transducerStack.push(model.loadTransducer(super.getParameter(parameterIndex)));
			} catch (final ModelException e) {
				byte[] bytes = model.getTransducerName(super.getParameter(parameterIndex));
				throw new EffectorException(String.format("The start effector failed to load %1$s", 
					Bytes.decode(super.getDecoder(), bytes, bytes.length)), e);
			}
			return IEffector.RTX_START;
		}

		@Override
		public String showParameterType(int parameterIndex) {
			return "Integer";
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

		@Override // @see com.characterforming.ribose.IParameterizedEffector#iallocateParameters(int)
		public long[][] allocateParameters(int parameterCount) {
			return new long[parameterCount][];
		}
	
		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (matchMode == MATCH_NONE) {
				matchMode = MATCH_SUM;
				matchSum = super.getParameter(parameterIndex);
			} else {
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MSUM=%d",
					matchMode, MATCH_SUM));
			}
			return IEffector.RTX_NONE;
		}

		@Override
		public long[] compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The msum effector accepts at most one parameter (a byte array of length >1)");
			}
			long[] byteMap = new long[] {0, 0, 0, 0};
			for (byte b : parameterList[0].getLiteralValue()) {
				final int i = Byte.toUnsignedInt(b);
				byteMap[i >> 6] |= 1L << (i & 0x3f);
			}
			return byteMap;
 		}

		@Override
		public String showParameterType(int parameterIndex) {
			return "long[]";
		}

		@Override
		public String showParameterTokens(int parameterIndex) {
			long[] sum = super.getParameter(parameterIndex);
			StringBuilder sb = new StringBuilder();
			int endBit = 0, startBit = -1;
			for (int j = 0; j < sum.length; j++) {
				for (int k = 0; k < 64; k++, endBit++) {
					if (0 == (sum[j] & (1L << k))) {
						if (startBit >= 0) {
							this.printRange(sb, startBit, endBit);
							startBit = -1;
						}
					} else if (startBit < 0) {
						startBit = endBit;
					}
				}
			}
			if (startBit >= 0) {
				this.printRange(sb, startBit, endBit);
			}
			return sb.toString();
		}

		private void printRange(StringBuilder sb, int startBit, int endBit) {
			if (endBit > (startBit + 1)) {
				sb.append(startBit > 32 && startBit < 127
				?	String.format(" %c", (char)startBit)
				:	String.format(" #%x", startBit));
				sb.append((endBit-1) > 32 && (endBit-1) < 127
				?	String.format("-%c", (char)(endBit-1))
				:	String.format("-#%x", (endBit-1)));
			} else {
				sb.append(startBit > 32 && startBit < 127
				?	String.format(" %c", (char)startBit)
				:	String.format(" #%x", startBit));
			}
		}
	}

	private final class MproductEffector extends BaseParameterizedEffector<Transductor, byte[]> {
		private MproductEffector(final Transductor transductor) {
			super(transductor, "mproduct");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (matchMode == MATCH_NONE) {
				matchMode = MATCH_PRODUCT;
				matchProduct = super.getParameter(parameterIndex);
				matchPosition = 0;
			} else {
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MPRODUCT=%d",
					matchMode, MATCH_PRODUCT));
			}
			return IEffector.RTX_NONE;
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#iallocateParameters(int)
		public byte[][] allocateParameters(int parameterCount) {
			return new byte[parameterCount][];
		}
	
		@Override
		public byte[] compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The mproduct effector accepts at most one parameter (a byte array of length >1)");
			}
			byte[] tokens = parameterList[0].getLiteralValue();
			return Arrays.copyOf(tokens, tokens.length);
 		}

		@Override
		public String showParameterType(int parameterIndex) {
			return "byte[]";
		}

		@Override
		public String showParameterTokens(int parameterIndex) {
			byte[] product = super.getParameter(parameterIndex);
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < product.length; j++) {
				sb.append(32 < product[j] && 127 > product[j]
				?	String.format(" %c", (char)product[j])
				:	String.format(" #%x", product[j]));
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
			if (matchMode == MATCH_NONE) {
				matchMode = MATCH_SCAN;
				matchByte = super.getParameter(parameterIndex);
			} else {
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MSCAN=%d",
					matchMode, MATCH_SCAN));
			}
			return IEffector.RTX_NONE;
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#iallocateParameters(int)
		public Integer[] allocateParameters(int parameterCount) {
			return new Integer[parameterCount];
		}
	
		@Override
		public Integer compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The mscan effector accepts at most one parameter (a byte array of length 1)");
			}
 			return 0xff & parameterList[0].getLiteralValue()[0];
		}

		@Override
		public String showParameterType(int parameterIndex) {
			return "Integer";
		}

		@Override
		public String showParameterTokens(int parameterIndex) {
			int scanbyte = super.getParameter(parameterIndex);
			return 32 < scanbyte && 127 > scanbyte
			?	String.format(" %c", (char)scanbyte)
			:	String.format(" #%x", scanbyte);
		}
	}
}
