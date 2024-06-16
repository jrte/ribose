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
import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.logging.Logger;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.IModel;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.IToken;
import com.characterforming.ribose.ITransduction;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/**
 * Runtime transductor instances are instantiated using {@link IModel#transductor(ITarget)}
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
public final class Transductor implements ITransductor, ITransduction, IOutput {
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

	private Model model;
	private final boolean isProxy;
	private final ModelLoader loader;
	private TransducerState transducerStackFrame;
	private IEffector<?>[] effectors;
	private Value value;
	private Signal prologue;
	private int selected;
	private final TransducerStack transducerStack;
	private final InputStack inputStack;
	private int matchMode;
	private long[] matchSum;
	private byte[] matchProduct;
	private int matchPosition;
	private int matchByte;
	private int errorInput;
	private final int signalLimit;
	private OutputStream outputStream;
	private final Logger rtcLogger;
	private final Logger rteLogger;
	private final ITransductor.Metrics metrics;

	/**
	 * Proxy constructor
	 */
	Transductor() {
		this.model = null;
		this.loader = null;
		this.isProxy = true;
		this.transducerStack = null;
		this.selected = -1;
		this.value = null;
		this.inputStack = null;
		this.signalLimit = -1;
		this.rtcLogger = Base.getCompileLogger();
		this.rteLogger = Base.getRuntimeLogger();
		this.metrics = null;
	}

	/**
	 *  Constructor
	 *
	 * @param model The runtime model
	 * @throws ModelException if things don't work out
	 */
	Transductor(final Model model) {
		super();
		this.model = model;
		this.isProxy = false;
		this.loader = (ModelLoader)this.model;
		this.prologue = Signal.NONE;
		this.effectors = null;
		this.transducerStackFrame = null;
		this.selected = -1;
		this.value = null;
		this.inputStack = new InputStack(INITIAL_STACK_SIZE);
		this.transducerStack = new TransducerStack(INITIAL_STACK_SIZE);
		this.outputStream = System.getProperty("jrte.out.enabled", "true").equals("true") ? System.out : null;
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
	}

	// set by Model on proxy transductor after constructor
	void setModel(Model model) {
		assert this.isProxy();
		this.model = model;
	}

	// set by Model on proxy transductor after constructor
	void setEffectors(IEffector<?>[] effectors) {
		this.effectors = effectors;
	}

	@Override // @see com.characterforming.ribose.ITarget#getEffectors()
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		try {
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
		} catch (CharacterCodingException e) {
			throw new TargetBindingException(e);
		}
	}

	@Override // @see com.characterforming.ribose.ITarget#getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override // @see com.characterforming.ribose.IOutput#getLocalizedFieldIndex(Bytes, Bytes)
	public int getLocalizedFieldIndex(String transducerName, String fieldName) throws CharacterCodingException {
		int transducerOrdinal = this.model.getTransducerOrdinal(Codec.encode(transducerName));
		return this.model.getLocalField(transducerOrdinal, Codec.encode(fieldName));
	}

	@Override // @see com.characterforming.ribose.IOutput#getLocalizedFieldIndex()
	public int getLocalizedFieldndex() throws EffectorException {
		if (!this.isProxy)
			return this.selected;
		else
			throw new EffectorException("Not valid for proxy transductor");
	}

	@Override
	public String asString(int fieldOrdinal) throws EffectorException, CharacterCodingException {
		if (!this.isProxy) {
			Value v = this.transducerStack.value(fieldOrdinal);
			return Codec.decode(v.value(), v.length());
		} else
			throw new EffectorException("Not valid for proxy transductor");
	}

	@Override // @see com.characterforming.ribose.IOutput#asBytes(int)
	public byte[] asBytes(int fieldOrdinal) throws EffectorException {
		if (!this.isProxy) {
			Value v = this.transducerStack.value(fieldOrdinal);
			return Arrays.copyOf(v.value(), v.length());
		} else
			throw new EffectorException("Not valid for proxy transductor");
	}

	@Override // @see com.characterforming.ribose.IOutput#asInteger()
	public long asInteger(int fieldOrdinal) throws EffectorException {
		if (!this.isProxy) {
			Value v = this.transducerStack.value(fieldOrdinal);
			byte[] data = v.value();
			long n = 0, sign = data[0] == '-' ? -1 : 1;
			for (int i = sign > 0 ? 0 : 1; i < v.length(); i++)
				if (Character.getType(data[i]) == Character.DECIMAL_DIGIT_NUMBER)
					n = (10 * n) + (data[i] - 48);
				else
					throw new NumberFormatException(String.format(
						"Not a numeric value '%1$s'", v.toString()));
			return sign * n;
		} else
			throw new EffectorException("Not valid for proxy transductor");
	}

	@Override // @see com.characterforming.ribose.IOutput#asReal()
	public double asReal(int fieldOrdinal) throws EffectorException {
		if (!this.isProxy) {
			Value v = this.transducerStack.value(fieldOrdinal);
			byte[] data = v.value();
			double f = data[0] == '-' ? -1.0 : 1.0;
			boolean mark = false;
			long n = 0;
			for (int i = f < 0.0 ? 1 : 0; i < v.length(); i++) {
				byte digit = data[i];
				if (digit == '.')
					mark = true;
				else if (Character.getType(digit) == Character.DECIMAL_DIGIT_NUMBER) {
					n = (10 * n) + (digit - 48);
					if (mark)
						f /= 10.0;
				} else
					throw new NumberFormatException(String.format(
						"Not a floating point value '%1$s'", this.toString()));
			}
			return f * n;
		} else
			throw new EffectorException("Not valid for proxy transductor");
	}

	@Override // @see com.characterforming.ribose.IOutput#isProxy()
	public boolean isProxy() {
		return this.isProxy;
	}

	@Override // @see com.characterforming.ribose.IOutput#rtcLogger()
	public Logger getRtcLogger() {
		return this.rtcLogger;
	}

	@Override // @see com.characterforming.ribose.IOutput#getRteLogger()
	public Logger getRteLogger() {
		return this.rteLogger;
	}

	@Override // @see com.characterforming.ribose.ITransductor#recycle()
	public byte[] recycle(byte[] bytes) {
		return this.inputStack.recycle(bytes);
	}

	@Override // @see com.characterforming.ribose.ITransductor#metrics()
	public void metrics(Metrics accumulator) {
		this.metrics.update(accumulator);
	}

	@Override // @see com.characterforming.ribose.ITransductor#status()
	public Status status() {
		if (!this.isProxy) {
			assert this.inputStack != null;
			assert this.transducerStack != null;
			if (this.transducerStack.isEmpty())
				return this.inputStack.isEmpty() ? Status.STOPPED : Status.WAITING;
			else
				return this.inputStack.isEmpty() ? Status.PAUSED : Status.RUNNABLE;
		}
		return Status.PROXY;
	}

	@Override // @see com.characterforming.ribose.ITransductor#output(OutputStream)
	public OutputStream output(OutputStream output) {
		assert !isProxy();
		OutputStream out = this.outputStream;
		this.outputStream = output;
		return out;
	}

	@Override // @see com.characterforming.ribose.ITransductor#push(byte[], int)
	public ITransductor push(final byte[] input, int limit) {
		assert !isProxy();
		if (input.length < limit)
			limit = input.length;
		this.inputStack.push(input, limit);
		return this;
	}

	@Override // @see com.characterforming.ribose.ITransductor#signal(Signal)
	public ITransductor signal(Signal signal) {
		assert !isProxy();
		assert this.inputStack.isEmpty();
		if (this.inputStack.isEmpty())
			this.prologue = signal;
		return this;
	}

	@Override // @see com.characterforming.ribose.ITransductor#start(Bytes)
	public ITransductor start(final Bytes transducerName) throws ModelException {
		assert !isProxy();
		this.transducerStack.push(this.loader.loadTransducer(this.model.getTransducerOrdinal(transducerName)));
		this.transducerStack.peek().selected = Model.ANONYMOUS_FIELD_ORDINAL;
		return this;
	}

	@Override // @see com.characterforming.ribose.ITransductor#stop()
	public ITransductor stop() {
		if (this.inputStack != null) {
			this.inputStack.unmark();
			while (!this.inputStack.isEmpty()) {
				if (this.inputStack.peek().hasRemaining())
					this.inputStack.peek().clear();
				this.inputStack.pop();
			}
		}
		if (this.transducerStack != null) {
			while (!this.transducerStack.isEmpty()) {
				this.transducerStack.clear();
				this.transducerStack.pop();
			}
		}
		this.matchMode = MATCH_NONE;
		this.prologue = Signal.NONE;
		return this;
	}

	@Override // ITransduction
	public void reset() {
		this.stop();
	}

	@Override // AutoCloseable
	public void close() {
		this.stop();
	}

	@Override	// @see com.characterforming.ribose.ITransductor#run()
	public ITransductor run() throws EffectorException, DomainErrorException {
		assert !this.isProxy();
		this.metrics.reset();
		if (this.transducerStack.isEmpty())
			return this;
		int token = -1, state = 0, last = -1;
		int signal = this.prologue.signal();
		this.prologue = Signal.NONE;
		Input input = Input.empty;
		this.errorInput = -1;
		try {
T:		do {
				// start a pushed transducer, or resume caller after pushed transducer is popped
				this.transducerStackFrame = this.transducerStack.peek();
				this.selected = this.transducerStackFrame.selected;
				this.value = this.transducerStack.value(this.selected);
				Transducer transducer = this.transducerStackFrame.transducer;
				final int[] inputFilter = transducer.inputFilter();
				final long[] transitionMatrix = transducer.transitionMatrix();
				final int[] effectorVector = transducer.effectorVector();
				state = this.transducerStackFrame.state;
I:			do {
					// get next input token
					if (signal < 0) {
						token = -1;
						while (input.position >= input.limit) {
							input = this.inputStack.pop();
							if (input == Input.empty)
								break T;
						}
						token = input.array[input.position++] & 0xff;
					} else {
						assert this.matchMode == MATCH_NONE || signal == SIGEOS
						: String.format("mode=%d; signal=%d; expected mode=%d",
							this.matchMode, signal, MATCH_NONE);
						this.matchMode = MATCH_NONE;
						token = signal;
						signal = -1;
					}

					int action = NIL;
S:				do {
						switch (this.matchMode) {
						// trap runs in (nil* paste*)* effector space
						case MATCH_NONE:
							do {
								final long transition = transitionMatrix[state + inputFilter[token]];
								last = state; state = Transducer.state(transition);
								action = Transducer.action(transition);
								if (action == PASTE)
									this.value.paste((byte)token);
								else if (action != NIL)
									break S;
								token = input.position < input.limit ? input.array[input.position++] & 0xff : -1;
							} while (token >= 0);
							break;
						// absorb self-referencing (msum,mscan) or sequential (mproduct) transitions with nil effect
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
					} while (token >= 0);
					if (token < 0)
						continue I;

					// effect action and check for transducer or input stack adjustment
					int aftereffects = IEffector.RTX_NONE;
					if (action >= 0x10000) {
						if ( this.effectors[Transducer.effector(action)] instanceof IParameterizedEffector<?, ?> e)
							aftereffects = e.invoke(Transducer.parameter(action));
						else assert false;
					} else if (action >= 0)
						aftereffects = effect(action, token);
					else
						aftereffects = effect(action, token, effectorVector);

					if (aftereffects != IEffector.RTX_NONE) {
						if (0 != (aftereffects & IEffector.RTX_INPUT))
							input = this.inputStack.peek();
						if (0 != (aftereffects & IEffector.RTX_SIGNAL)) {
							signal = Transducer.signal(aftereffects);
							if (signal < SIGNUL || signal >= this.signalLimit)
								throw new EffectorException(String.format("Invalid signal raised: %d", signal));
						}
						int stackeffect = aftereffects & (IEffector.RTX_START | IEffector.RTX_STOP);
						if (stackeffect == IEffector.RTX_START) {
							assert this.transducerStack.tos() > 0
							&& this.transducerStackFrame == this.transducerStack.get(this.transducerStack.tos() - 1);
							this.transducerStackFrame.selected = this.selected;
							this.transducerStackFrame.state = state;
						} else if (stackeffect != 0)
							this.matchMode = MATCH_NONE;
						if (0 != (aftereffects & (IEffector.RTX_PAUSE | IEffector.RTX_STOPPED)))
							break T;
						else if (stackeffect != 0)
							break I;
					}
				} while (this.status().isRunnable());
			} while (this.status().isRunnable());

			if (this.outputStream != null)
				this.outputStream.flush();
			if (token == SIGNUL)
				throw new DomainErrorException(this.getErrorInput(last, state));
			else if (token == SIGEOS)
				this.inputStack.pop();
			assert (token != SIGEOS) || this.inputStack.isEmpty();
		} catch (IOException e) {
			throw new EffectorException("Unable to write() to output", e);
		} finally {
			// Prepare to pause (or stop) transduction
			this.metrics.bytes = this.inputStack.getBytesRead();
			this.metrics.allocated = this.inputStack.getBytesAllocated();
			if (transducerStackFrame == this.transducerStack.peek()) {
				this.transducerStackFrame.selected = this.selected;
				this.transducerStackFrame.state = state;
			}
		}

		// Transduction is paused or stopped; if paused it will resume on next call to run()
		return this;
	}

	private int effect(int action, int token)
	throws EffectorException, IOException {
		switch (action) {
			case NUL:
				if ((token != SIGNUL && token != SIGEOS)) {
					++this.metrics.errors;
					this.errorInput = token;
					return IEffector.signal(Signal.NUL.signal());
				} else
					return IEffector.RTX_NONE;
			case NIL:
				assert false;
				return IEffector.RTX_NONE;
			case PASTE:
				this.value.paste((byte) token);
				return IEffector.RTX_NONE;
			case SELECT:
				this.selected = Model.ANONYMOUS_FIELD_ORDINAL;
				this.value = this.transducerStack.value(this.selected);
				return IEffector.RTX_NONE;
			case COPY:
				this.value.paste(this.transducerStack.value(Model.ANONYMOUS_FIELD_ORDINAL));
				return IEffector.RTX_NONE;
			case CUT:
				this.value.paste(this.transducerStack.value(Model.ANONYMOUS_FIELD_ORDINAL));
				this.transducerStack.value(Model.ANONYMOUS_FIELD_ORDINAL).clear();
				return IEffector.RTX_NONE;
			case CLEAR:
				this.transducerStack.value(this.selected).clear();
				return IEffector.RTX_NONE;
			case COUNT:
				if (--this.transducerStackFrame.countdown <= 0) {
					this.transducerStackFrame.countdown = Integer.MAX_VALUE;
					return IEffector.signal(this.transducerStackFrame.signal);
				}
				return IEffector.RTX_NONE;
			case IN:
				this.inputStack.push(this.value.value(), this.value.length());
				return IEffector.RTX_INPUT;
			case OUT:
				if (this.outputStream != null)
					this.outputStream.write(this.value.value(), 0, this.value.length());
				return IEffector.RTX_NONE;
			case MARK:
				this.inputStack.mark();
				return IEffector.RTX_NONE;
			case RESET:
				return this.inputStack.reset();
			case PAUSE:
				return IEffector.RTX_PAUSE;
			case STOP:
				return this.transducerStack.pop() == null ? IEffector.RTX_STOPPED : IEffector.RTX_STOP;
			default:
				if (action < this.effectors.length)
					return this.effectors[action].invoke();
				throw new EffectorException(String.format("Effector ordinal %d is out of range (<%d)",
					action, this.effectors.length));
		}
	}

	// invoke a scalar effector or vector of effectors and record side effects on transducer and input stacks
	private int effect(int action, int token, int[] effectorVector)
	throws IOException, EffectorException {
		assert action < 0;
		int index = 0 - action;
		int aftereffects = IEffector.RTX_NONE;
E:	do {
			action = effectorVector[index++];
			if (action < 0 ) {
				if (this.effectors[0 - action] instanceof IParameterizedEffector<?,?> e)
					aftereffects |= e.invoke(effectorVector[index++]);
				else assert false;
			} else if (action != NUL) {
				aftereffects |= this.effect(action, token);
			} else
				break E;
		} while (true);
		return aftereffects;
	}

	private int sumTrap(Input input, int token) {
		final long[] matchMask = this.matchSum;
		final int post = input.position;
		while (0 != (matchMask[token >> 6] & (1L << (token & 0x3f))))
			if (input.position < input.limit)
				token = 0xff & input.array[input.position++];
			else {
				this.metrics.traps[MATCH_PRODUCT][1] += (input.position - post);
				return -1;
			}
		this.matchMode = MATCH_NONE;
		this.metrics.traps[MATCH_SUM][0] += 1;
		this.metrics.traps[MATCH_SUM][1] += (input.position - post);
		return token;
	}

	private int productTrap(Input input, int token) {
		final byte[] product = this.matchProduct;
		byte match = (byte)(0xff & token);
		final int post = input.position;
		int mpos = this.matchPosition;
		assert mpos <= product.length;
		while (mpos < product.length)
			if (match == product[mpos++]) {
				if (mpos == product.length) {
					token = 0xff & match;
					break;
				} else if (input.position < input.limit) {
					match = input.array[input.position++];
				} else {
					this.metrics.traps[MATCH_PRODUCT][1] += (input.position - post);
					this.matchPosition = mpos;
					return -1;
				}
			} else {
				this.errorInput = token;
				token = SIGNUL;
				break;
			}
		this.matchMode = MATCH_NONE;
		this.metrics.traps[MATCH_PRODUCT][0] += 1;
		this.metrics.traps[MATCH_PRODUCT][1] += (input.position - post);
		return token;
	}

	private int scanTrap(Input input, int token) {
		final int matchToken = this.matchByte;
		final int post = input.position;
		while (token != matchToken)
			if (input.position < input.limit)
				token = 0xff & input.array[input.position++];
			else {
				this.metrics.traps[MATCH_SCAN][1] += (input.position - post);
				return -1;
			}
		this.matchMode = MATCH_NONE;
		this.metrics.traps[MATCH_SCAN][0] += 1;
		this.metrics.traps[MATCH_SCAN][1] += (input.position - post);
		return token;
	}

	private String getErrorInput(int last, int state) {
		TransducerState top = this.transducerStack.peek();
		int eqCount = top.transducer.getInputEquivalentsCount();
		top.state = state; state /= eqCount; last /= eqCount;
		StringBuilder message = new StringBuilder(256);
		message.append(String.format("Domain error on (%1$d~%2$d) in %3$s [%4$d]->[%5$d]%n,\tTransducer stack:%n",
			this.errorInput, this.errorInput >= 0 ? top.transducer.inputFilter()[this.errorInput] : this.errorInput,
			top.transducer.getName(), last, state));
		for (int i = this.transducerStack.tos(); i >= 0; i--) {
			TransducerState t = this.transducerStack.get(i);
			long[] transitionMatrix = t.transducer.transitionMatrix();
			eqCount = t.transducer.getInputEquivalentsCount();
			int s = t.state / eqCount;
			message.append(String.format("\t\t%1$20s state:%2$3d; accepting", t.transducer.getName(), s));
			for (int j = 0; j < eqCount; j++)
				if (Transducer.action(transitionMatrix[t.state + j]) != Transductor.NUL)
					message.append(String.format(" (%1$d)->[%2$d]", j,
						Transducer.state(transitionMatrix[t.state + j]) / eqCount));
			message.append(Base.LINEEND);
		}
		message.append(Base.LINEEND).append("\tInput stack:").append(Base.LINEEND);
		for (int i = this.inputStack.tos(); i >= 0; i--) {
			final Input in = this.inputStack.get(i);
			if (in.array == null)
				message.append("\t\t(null)").append(Base.LINEEND);
			else if (!in.hasRemaining())
				message.append("[ ]").append(Base.LINEEND);
			else if (in.position < in.length) {
				assert in.position < in.length && in.length <= in.array.length ;
				int position = Math.max(0, in.position - 1);
				int start = Math.max(0, position - 8);
				int end = Math.min(start + 16, in.length);
				String inchar = "";
				int inbyte = -1;
				if (in.array[position] >= 0x20 && in.array[position] < 0x7f)
					inchar = String.format("%1$2c", (char)in.array[position]);
				else
					inchar = String.format("%1$02X", Byte.toUnsignedInt(in.array[position]));
				inbyte = Byte.toUnsignedInt(in.array[position]);
				message.append(String.format("\t\t[ char='%1$s' (0x%2$02X); pos=%3$d; length=%4$d < ",
					inchar, inbyte, position, in.array.length));
				final int[] inputFilter = top.transducer.inputFilter();
				while (start < end) {
					int ubyte = Byte.toUnsignedInt(in.array[start]);
					int equiv = inputFilter[ubyte];
					if ((ubyte < 0x20) || (ubyte > 0x7e))
						message.append(String.format((start != position) ? "%1$02X~%2$d " : "[%1$02X~%2$d] ", ubyte, equiv));
					else
						message.append(String.format((start != position) ? "%1$c~%2$d " : "[%1$c~%2$d] ", (char)in.array[start], equiv));
					start += 1;
				}
				message.append("> ]").append(Base.LINEEND);
			} else
				message.append("\t\t[ < end-of-input > ]").append(Base.LINEEND);
		}
		return message.toString();
	}

	private final class InlineEffector extends BaseEffector<Transductor> {
		private InlineEffector(final Transductor transductor, final String name) throws CharacterCodingException {
			super(transductor, name);
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException(String.format("Cannot invoke inline effector '%1$s'", super.getName()));
		}
	}

	private final class PasteEffector extends BaseInputOutputEffector {
		private PasteEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "paste");
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException("Cannot invoke inline effector 'paste'");
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			for (IToken t : super.parameters[parameterIndex]) {
				if (t instanceof Token token) {
					if (token.isField()) {
						Value field = transducerStack.value(token.getOrdinal());
						if (field != null)
							value.paste(field);
					} else if (token.isLiteral()) {
						byte[] bytes = token.getSymbol().bytes();
						value.paste(bytes, bytes.length);
					} else
						throw new EffectorException(String.format("Invalid token `%1$s` for effector '%2$s'",
							token.asString(), super.getName()));
				}
			}
			return IEffector.RTX_NONE;
		}
	}

	private final class SelectEffector extends BaseFieldEffector {
		private SelectEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "select");
		}

		@Override
		public int invoke() throws EffectorException {
			selected = Model.ANONYMOUS_FIELD_ORDINAL;
			value = transducerStack.value(selected);
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			selected = super.parameters[parameterIndex];
			assert selected != Model.ALL_FIELDS_ORDINAL;
			if (selected != Model.ALL_FIELDS_ORDINAL)
				value = transducerStack.value(selected);
			return IEffector.RTX_NONE;
		}
	}

	private final class CopyEffector extends BaseFieldEffector {
		private CopyEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "copy");
		}

		@Override
		public int invoke() throws EffectorException {
			assert false;
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int fieldOrdinal = super.parameters[parameterIndex];
			assert fieldOrdinal != Model.ALL_FIELDS_ORDINAL;
			value.paste(transducerStack.value(fieldOrdinal));
			return IEffector.RTX_NONE;
		}
	}

	private final class CutEffector extends BaseFieldEffector {
		private CutEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "cut");
		}

		@Override
		public int invoke() throws EffectorException {
			return this.invoke(0);
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int fieldOrdinal = super.parameters[parameterIndex];
			assert fieldOrdinal != Model.ALL_FIELDS_ORDINAL;
			value.paste(transducerStack.value(fieldOrdinal));
			transducerStack.value(fieldOrdinal).clear();
			return IEffector.RTX_NONE;
		}
	}

	private final class ClearEffector extends BaseFieldEffector {
		private ClearEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "clear");
		}

		@Override
		public int invoke() throws EffectorException {
			transducerStack.clear();
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			final int nameIndex = super.parameters[parameterIndex];
			int index = (nameIndex >= 0) ? nameIndex : selected;
			assert index >= -1;
			if (index != Model.ALL_FIELDS_ORDINAL)
				transducerStack.clear(index);
			else
				transducerStack.clear();
			return IEffector.RTX_NONE;
		}
	}

	private final class SignalEffector extends BaseParameterizedEffector<Transductor, Integer> {
		private SignalEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "signal");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.signal(Signal.NIL.signal());
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			return IEffector.signal(super.parameters[parameterIndex]);
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#allocateParameters(int)
		public Integer[] allocateParameters(int parameterCount) {
			return new Integer[parameterCount];
		}

		@Override
		public Integer compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 1)
				throw new TargetBindingException("The signal effector accepts exactly one parameter");
			else if (parameterList[0] instanceof Token token) {
				if (token.isSignal()) {
					int ordinal = token.getOrdinal();
					if (ordinal < 0) {
						String signame;
						try {
							byte[] sigbytes = token.getSymbol().bytes();
							signame = Codec.decode(sigbytes);
						} catch (CharacterCodingException e) {
							signame = "<decodng error>";
						}
						throw new TargetBindingException(String.format(
							"Unkown signal reference for signal effector: %s",
							signame));
					}
					return ordinal;
				} else {
					throw new TargetBindingException(String.format(
						"Invalid signal reference `%s` for signal effector, requires type indicator ('!') before the transducer name",
						token.asString()));
				}
			} else {
				throw new TargetBindingException(String.format("Unknown IToken implementation class '%1$s'",
					parameterList[0].getClass().getTypeName()));
			}
		}
	}

	private final class InEffector extends BaseInputOutputEffector {
		private InEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "in");

		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException("The default in[] effector is inlined");
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			IToken[] parameters = super.parameters[parameterIndex];
			byte[][] tokens = new byte[parameters.length][];
			for (int i = 0; i < parameters.length; i++) {
				if (parameters[i].isField()) {
					Value field = transducerStack.value(parameters[i].getOrdinal());
					tokens[i] = (field != null) ? field.value() : Bytes.EMPTY_BYTES;
				} else {
					assert parameters[i].isLiteral();
					tokens[i] = parameters[i].getSymbol().bytes();
				}
			}
			inputStack.put(tokens);
			return IEffector.RTX_INPUT;
		}
	}

	private final class OutEffector extends BaseInputOutputEffector {
		private OutEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "out");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_NONE;
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (outputStream != null) {
				for (final IToken token : super.parameters[parameterIndex]) {
					try {
						if (token.isField()) {
							Value field = transducerStack.value(token.getOrdinal());
							if (field != null)
								outputStream.write(field.value(), 0, field.length());
						} else {
							assert token.isLiteral();
							byte[] data = token.getSymbol().bytes();
							outputStream.write(data, 0, data.length);
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
		private CountEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "count");
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException(String.format("Cannot invoke inline effector '%1$s'", super.getName()));
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#allocateParameters(int)
		public int[][] allocateParameters(int parameterCount) {
			return new int[parameterCount][];
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			assert (transducerStackFrame == transducerStack.peek()) || (transducerStackFrame == transducerStack.get(transducerStack.tos()-1));
			int[] parameter = super.parameters[parameterIndex];
			transducerStackFrame.countdown = parameter[0];
			transducerStackFrame.signal = parameter[1];
			if (transducerStackFrame.countdown < 0)
				transducerStackFrame.countdown = (int)asInteger(-1 - transducerStackFrame.countdown);
			return transducerStackFrame.countdown <= 0 ? IEffector.signal(transducerStackFrame.signal) : IEffector.RTX_NONE;
		}

		@Override
		public int[] compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 2)
				throw new TargetBindingException(String.format("%1$S.%2$S: effector requires two parameters",
					super.target.getName(), super.getName()));
			int count = -1;
			if (parameterList[0].isField())
				count = -1 - parameterList[0].getOrdinal();
			else if (parameterList[0].isLiteral()) {
				byte[] v = parameterList[0].getSymbol().bytes();
				count = Base.decodeInt(v, v.length);
			} else
				throw new TargetBindingException(String.format("%1$s.%2$s[]: invalid field|counter for count effector",
					super.target.getName(), super.getName()));
			if (parameterList[1].isSignal()) {
				int signalOrdinal = parameterList[1].getOrdinal();
				assert signalOrdinal >= SIGNUL;
				return new int[] { count, signalOrdinal };
			} else
				throw new TargetBindingException(String.format("%1$s.%2$s[]: invalid signal '%3$%s' for count effector",
					super.target.getName(), super.getName(), parameterList[1].asString()));
		}
	}

	private final class StartEffector extends BaseParameterizedEffector<Transductor, Integer> {
		private StartEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "start");
		}

		@Override
		public int invoke() throws EffectorException {
			throw new EffectorException("The start effector requires a parameter");
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#allocateParameters(int)
		public Integer[] allocateParameters(int parameterCount) {
			return new Integer[parameterCount];
		}

		@Override
		public Integer compileParameter(final IToken[] parameterTokens) throws TargetBindingException {
			if (parameterTokens.length != 1)
				throw new TargetBindingException("The start effector accepts only one parameter");
			else if (!parameterTokens[0].isTransducer())
				throw new TargetBindingException(String.format(
					"Invalid transducer reference `%s` for start effector, requires type indicator ('@') before the transducer name",
					parameterTokens[0]));
			return parameterTokens[0].getOrdinal();
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			int transducerOrdinal = super.parameters[parameterIndex];
			try {
				transducerStack.push(loader.loadTransducer(transducerOrdinal));
				transducerStack.peek().selected = Model.ANONYMOUS_FIELD_ORDINAL;
			} catch (final ModelException e) {
				throw new EffectorException(String.format(
					"The start effector failed to load transducer with ordinal number %1$d",
					transducerOrdinal), e
				);
			}
			return IEffector.RTX_START;
		}
	}

	private final class PauseEffector extends BaseEffector<Transductor> {
		private PauseEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "pause");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_PAUSE;
		}
	}

	private final class MsumEffector extends BaseParameterizedEffector<Transductor, long[]> {
		private MsumEffector(final Transductor transductor) throws CharacterCodingException {
			super(transductor, "msum");
		}

		@Override
		public int invoke() throws EffectorException {
			return IEffector.RTX_NONE;
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#allocateParameters(int)
		public long[][] allocateParameters(int parameterCount) {
			return new long[parameterCount][];
		}

		@Override
		public int invoke(final int parameterIndex) throws EffectorException {
			if (matchMode == MATCH_NONE) {
				matchMode = MATCH_SUM;
				matchSum = super.parameters[parameterIndex];
			} else
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MSUM=%d",
					matchMode, MATCH_SUM));
			return IEffector.RTX_NONE;
		}

		@Override
		public long[] compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 1)
				throw new TargetBindingException("The msum effector accepts at most one parameter (a byte array of length >1)");
			long[] byteMap = new long[] {0, 0, 0, 0};
			for (byte b : parameterList[0].getSymbol().bytes())
				byteMap[(b & 0xff) >>> 6] |= 1L << (b & 0x3f);
			return byteMap;
 		}

		@Override
		public String showParameterTokens(int parameterIndex) {
			long[] sum = super.parameters[parameterIndex];
			StringBuilder sb = new StringBuilder();
			int endBit = 0, startBit = -1;
			for (int j = 0; j < sum.length; j++) {
				for (int k = 0; k < 64; k++, endBit++)
					if (0 == (sum[j] & (1L << k))) {
						if (startBit >= 0) {
							this.printRange(sb, startBit, endBit);
							startBit = -1;
						}
					} else if (startBit < 0)
						startBit = endBit;
			}
			if (startBit >= 0)
				this.printRange(sb, startBit, endBit);
			return sb.toString();
		}

		private void printRange(StringBuilder sb, int startBit, int endBit) {
			sb.append(startBit > 32 && startBit < 127
			?	String.format(" %c", (char)startBit)
			:	String.format(" #%x", startBit));
			if (endBit > (startBit + 1))
				sb.append((endBit-1) > 32 && (endBit-1) < 127
				?	String.format("-%c", (char)(endBit-1))
				:	String.format("-#%x", (endBit-1)));
		}
	}

	private final class MproductEffector extends BaseParameterizedEffector<Transductor, byte[]> {
		private MproductEffector(final Transductor transductor) throws CharacterCodingException {
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
				matchProduct = super.parameters[parameterIndex];
				matchPosition = 0;
			} else {
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MPRODUCT=%d",
					matchMode, MATCH_PRODUCT));
			}
			return IEffector.RTX_NONE;
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#allocateParameters(int)
		public byte[][] allocateParameters(int parameterCount) {
			return new byte[parameterCount][];
		}

		@Override
		public byte[] compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 1)
				throw new TargetBindingException("The mproduct effector accepts at most one parameter (a byte array of length >1)");
			byte[] product = parameterList[0].getSymbol().bytes();
			return Arrays.copyOf(product, product.length);
 		}

		@Override
		public String showParameterTokens(int parameterIndex) {
			byte[] product = super.parameters[parameterIndex];
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
		private MscanEffector(final Transductor transductor) throws CharacterCodingException {
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
				matchByte = super.parameters[parameterIndex];
			} else
				throw new EffectorException(String.format("Illegal attempt to override match mode %d with MSCAN=%d",
					matchMode, MATCH_SCAN));
			return IEffector.RTX_NONE;
		}

		@Override // @see com.characterforming.ribose.IParameterizedEffector#allocateParameters(int)
		public Integer[] allocateParameters(int parameterCount) {
			return new Integer[parameterCount];
		}

		@Override
		public Integer compileParameter(final IToken[] parameterList) throws TargetBindingException {
			if (parameterList.length != 1)
				throw new TargetBindingException("The mscan effector accepts at most one parameter (a byte array of length 1)");
			return 0xff & parameterList[0].getSymbol().bytes()[0];
		}

		@Override
		public String showParameterTokens(int parameterIndex) {
			int scanbyte = super.parameters[parameterIndex];
			return 32 < scanbyte && 127 > scanbyte
			?	String.format(" %c", (char)scanbyte)
			:	String.format(" #%x", scanbyte);
		}
	}
}
