/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.engine;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.DomainErrorException;
import com.characterforming.jrte.EffectorException;
import com.characterforming.jrte.GearboxException;
import com.characterforming.jrte.IEffector;
import com.characterforming.jrte.IInput;
import com.characterforming.jrte.INamedValue;
import com.characterforming.jrte.IParameterizedEffector;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.InputException;
import com.characterforming.jrte.MarkLimitExceededException;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.TargetNotFoundException;
import com.characterforming.jrte.TransducerNotFoundException;
import com.characterforming.jrte.base.BaseEffector;
import com.characterforming.jrte.base.BaseParameterizedEffector;
import com.characterforming.jrte.engine.input.SignalInput;

/**
 * Runtime transduction instances are instantiated using Jrte.bind(). Client
 * applications
 * drive the transduction using the Transduction.run() method, which processes
 * the bound
 * IInput stack until one of the following conditions is satisfied: <br>
 * <ol>
 * <li>the input stack is empty
 * <li>the transducer stack is empty
 * <li>an effector returns RTE_TRANSDUCTION_PAUSE
 * <li>the transduction throws an exception.
 * </ol>
 * 
 * @author kb
 */
public final class Transduction implements ITransduction {
	private final static Logger logger = Logger.getLogger(Transduction.class.getName());

	/**
	 * Predefined signal symbols
	 */
	public static final String[] RTE_SIGNAL_NAMES = {
			"nul", "nil", "eol", "eos"
	};

	/**
	 * Inline effector names
	 */
	public static final String[] RTE_INLINE_EFFECTORS = {
		"0", "1", "paste", "count", "mark", "reset", "retry"
	};

	/**
	 * Type reference prefixes for parameter tape references to transducers,
	 * signals, values
	 */
	static final char TYPE_REFERENCE_TRANSDUCER = '@';
	static final char TYPE_REFERENCE_SIGNAL = '!';
	static final char TYPE_REFERENCE_VALUE = '~';

	private static final char[] ANONYMOUS_VALUE_REFERENCE = new char[] { Transduction.TYPE_REFERENCE_VALUE };

	private static final int RTE_EFFECTOR_NUL = 0;
	private static final int RTE_EFFECTOR_NIL = 1;
	private static final int RTE_EFFECTOR_PASTE = 2;
	private static final int RTE_EFFECTOR_COUNT = 3;
	private static final int RTE_EFFECTOR_MARK = 4;
	private static final int RTE_EFFECTOR_RESET = 5;
	private static final int RTE_EFFECTOR_RETRY = 6;

	private static final int INITIAL_NAMED_VALUE_BUFFERS = 32;
	private static final int INITIAL_NAMED_VALUE_CHARS = 256;

	private static final char[] EMPTY = {};

	private final Gearbox gearbox;
	private final ITarget target;
	private final IEffector<?>[] effectors;
	private final int nulSignal;
	private final int eosSignal;
	private final SignalInput[] signalInputs;
	private final HashMap<String, Integer> valueNameIndexMap;
	private final NamedValue[] namedValueHandles;
	private Stack<IInput> inputStack;
	private Stack<TransducerState> transducerStack;
	private int selectionIndex;
	private char[] selectionValue;
	private int selectionPosition;
	private char[][] namedValue;
	private int[] valueLength;

	private final IEffector<?>[] base = {
			new InlineEffector(this, "0"),
			new InlineEffector(this, "1"),
			new PasteEffector(this),
			new InlineEffector(this, "count"),
			new InlineEffector(this, "mark"),
			new InlineEffector(this, "reset"),
			new InlineEffector(this, "retry"),
			new SelectEffector(this),
			new CopyEffector(this),
			new CutEffector(this),
			new ClearEffector(this),
			new InEffector(this),
			new OutEffector(this),
			new SaveEffector(this),
			new CounterEffector(this),
			new StartEffector(this),
			new ShiftEffector(this),
			new PauseEffector(this),
			new StopEffector(this),
			new EndEffector(this)
	};

	private static class TransducerState {
		Transducer transducer;
		int[] countdown;
		int state;
		int[] nameIndex;
		int[] valueLengths;
		char[][] namedValues;

		public TransducerState(final int state, final Transducer transducer) {
			this.state = state;
			this.transducer = transducer;
			this.countdown = new int[2];
			this.namedValues = null;
			this.valueLengths = null;
			this.nameIndex = null;
		}

		void save(final int[] nameIndex, final char[][] namedValues, final int[] valueLengths) {
			this.nameIndex = nameIndex;
			if (this.namedValues == null) {
				this.namedValues = new char[nameIndex.length][];
				this.valueLengths = new int[nameIndex.length];
			}
			for (int i = 0; i < nameIndex.length; i++) {
				final int v = nameIndex[i];
				this.namedValues[i] = namedValues[v];
				this.valueLengths[i] = valueLengths[v];
				namedValues[v] = null;
				valueLengths[v] = 0;
			}
		}

		void restore(final char[][] namedValues, final int[] valueLengths) {
			for (int i = 0; i < this.nameIndex.length; i++) {
				final int v = this.nameIndex[i];
				namedValues[v] = this.namedValues[i];
				valueLengths[v] = this.valueLengths[i];
				this.namedValues[i] = null;
				this.valueLengths[i] = 0;
			}
			this.nameIndex = null;
		}

		void reset(final int state, final Transducer transducer) {
			this.state = state;
			this.transducer = transducer;
			this.nameIndex = null;
			this.countdown[0] = 0;
			this.countdown[1] = 0;
		}

		@Override
		public String toString() {
			return String.format(this.transducer != null ? this.transducer.getName() : "empty");
		}
	}

	public Transduction(final Gearbox gearbox, final ITarget target, final boolean warn) throws TargetNotFoundException, GearboxException, TargetBindingException {
		this.target = target;
		this.gearbox = gearbox;
		this.gearbox.getSignalOrdinal("nil");
		this.nulSignal = this.gearbox.getSignalOrdinal("nul");
		this.eosSignal = this.gearbox.getSignalOrdinal("eos");
		try {
			this.signalInputs = this.getSignalInputs();
		} catch (final InputException e) {
			throw new GearboxException("Internal error creating transduction SignalInput array", e);
		}
		this.valueLength = new int[Transduction.INITIAL_NAMED_VALUE_BUFFERS];
		this.namedValue = new char[Transduction.INITIAL_NAMED_VALUE_BUFFERS][];
		this.valueNameIndexMap = new HashMap<String, Integer>(this.valueLength.length);
		this.selectionIndex = this.getNamedValueReference(Transduction.ANONYMOUS_VALUE_REFERENCE, true);
		this.selectionValue = this.namedValue[this.selectionIndex];
		this.selectionPosition = 0;
		this.effectors = this.bind(this.target, warn);
		this.namedValueHandles = new NamedValue[this.valueNameIndexMap.size()];
		for (final String name : this.valueNameIndexMap.keySet()) {
			final int valueIndex = this.valueNameIndexMap.get(name);
			this.namedValueHandles[valueIndex] = new NamedValue(name, valueIndex, null, 0);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#start(String)
	 */
	@Override
	public void start(final String transducerName) throws GearboxException, TransducerNotFoundException, RteException {
		if (this.target == null) {
			throw new RteException(String.format("Transduction not bound, cannot start %1$s", transducerName));
		}
		Transducer transducer = this.gearbox.loadTransducer(this.gearbox.getTransducerOrdinal(transducerName));
		if (transducer != null) {
			this.inputStack = null;
			final TransducerState[] state = new TransducerState[] { new TransducerState(0, transducer) };
			this.transducerStack = new Stack<TransducerState>(state, false);
			this.selectionIndex = this.getNamedValueReference(Transduction.ANONYMOUS_VALUE_REFERENCE, false);
			this.selectionValue = this.namedValue[this.selectionIndex] = new char[Transduction.INITIAL_NAMED_VALUE_CHARS];
			this.selectionPosition = 0;
			this.clear();
		} else {
			throw new GearboxException(String.format("Unknown transducer (%s)", transducerName));
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#input(IInput[])
	 */
	@Override
	public void input(final IInput[] inputs) throws RteException {
		if (this.inputStack == null) {
			this.inputStack = new Stack<IInput>(inputs, true);
		} else {
			this.inputStack.put(inputs);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#run()
	 */
	@Override
	public int run() throws RteException {
		CharBuffer inputBuffer = null;
		int position = 0, limit = 0;
		for (IInput input = this.inputStack.peek(); input != null; input = this.inputStack.pop()) {
			inputBuffer = input.get();
			if (inputBuffer != null && inputBuffer.hasRemaining()) {
				position = inputBuffer.position();
				limit = inputBuffer.limit();
				break;
			}
		}
		String debug = null;
		int state = 0;
		try {
			char[] inputArray = inputBuffer.array();
T:			while (this.status() == ITransduction.RUNNABLE) {
				final TransducerState transducerState = this.transducerStack.peek();
				final Transducer transducer = transducerState.transducer;
				final int[] inputFilter = transducer.getInputFilter();
				final int[][] transitionMatrix = transducer.getTransitionMatrix();
				final int[] effectorVector = transducer.getEffectorVector();
				
				char lastError = 0;
				int currentInput = 0;
				int status = this.status();
				state = transducerState.state;
				while (status == ITransduction.RUNNABLE) {
					if (position < limit) {
						currentInput = inputArray[position++];
					} else {
						currentInput = this.eosSignal;
						inputBuffer.position(position);
						for (IInput input = this.inputStack.peek(); input != null; input = this.inputStack.pop()) {
							inputBuffer = input.get();
							if (inputBuffer != null && inputBuffer.hasRemaining()) {
								inputArray = inputBuffer.array();
								position = inputBuffer.position();
								currentInput = inputArray[position++];
								limit = inputBuffer.limit();
								break;
							}
						}
					}
					
					final int transition[] = transitionMatrix[state + inputFilter[currentInput]];
					state = transition[0];
					int action = transition[1];
					int effect = BaseEffector.RTE_TRANSDUCTION_RUN;
					switch (action) {
						case Transduction.RTE_EFFECTOR_NUL:
							if (currentInput == this.nulSignal) {
								debug = this.getErrorInput(inputArray, position, state);
								throw new DomainErrorException(String.format("Domain error in %1$s %2$s", transducer.getName(), debug));
							} else if (currentInput != this.eosSignal) {
								effect |= this.in(this.nulSignal);
								lastError = (char)currentInput;
							}
							break;
						case Transduction.RTE_EFFECTOR_NIL:
							break;
						case Transduction.RTE_EFFECTOR_PASTE:
							if (this.selectionPosition >= this.selectionValue.length) {
								this.selectionValue = Arrays.copyOf(this.selectionValue, (this.selectionPosition * 3) >> 1);
							}
							this.selectionValue[this.selectionPosition++] = (char) currentInput;
							break;
						case Transduction.RTE_EFFECTOR_COUNT:
							if (--transducerState.countdown[1] <= 0) {
								effect |= this.in(transducerState.countdown[0]);
								transducerState.countdown[1] = 0;
							}
							break;
						case Transduction.RTE_EFFECTOR_MARK:
							this.mark(inputBuffer, position);
							limit = -1;
							break;
						case Transduction.RTE_EFFECTOR_RESET:
							position = this.reset(inputBuffer, position);
							break;
						case Transduction.RTE_EFFECTOR_RETRY:
							if (currentInput == this.nulSignal) {
								effect |= this.in(new char[][] { { lastError } });
							}
							break;
						default:
							if (action > 0) {
								effect |= this.effectors[action].invoke();
							} else {
								int index = -1 * action;
								for (action = effectorVector[index]; action != Transduction.RTE_EFFECTOR_NUL; action = effectorVector[++index]) {
									switch (action) {
										case Transduction.RTE_EFFECTOR_NIL:
											break;
										case Transduction.RTE_EFFECTOR_PASTE:
											if (this.selectionPosition >= this.selectionValue.length) {
												this.selectionValue = Arrays.copyOf(this.selectionValue, (this.selectionPosition * 3) >> 1);
											}
											this.selectionValue[this.selectionPosition++] = (char) currentInput;
											break;
										case Transduction.RTE_EFFECTOR_COUNT:
											if (--transducerState.countdown[1] <= 0) {
												effect |= this.in(transducerState.countdown[0]);
												transducerState.countdown[1] = 0;
											}
											break;
										case Transduction.RTE_EFFECTOR_MARK:
											this.mark(inputBuffer, position);
											limit = -1;
											break;
										case Transduction.RTE_EFFECTOR_RESET:
											position = this.reset(inputBuffer, position);
											break;
										case Transduction.RTE_EFFECTOR_RETRY:
											if (currentInput == this.nulSignal) {
												effect |= this.in(new char[][] { { lastError } });
											}
											break;
										default:
											effect |= action > 0 ? this.effectors[action].invoke() : ((IParameterizedEffector<?, ?>) this.effectors[-action]).invoke(effectorVector[++index]);
											break;
									}
								}
							}
					}
					
					if (effect != BaseEffector.RTE_TRANSDUCTION_RUN) {
						final int clutch = effect & (BaseEffector.RTE_TRANSDUCTION_START | BaseEffector.RTE_TRANSDUCTION_STOP | BaseEffector.RTE_TRANSDUCTION_SHIFT);
						if (0 != (effect & (BaseEffector.RTE_TRANSDUCTION_START | BaseEffector.RTE_TRANSDUCTION_STOP | BaseEffector.RTE_TRANSDUCTION_PUSH | BaseEffector.RTE_TRANSDUCTION_POP))) {
							for (int mask = 1; mask < BaseEffector.RTE_TRANSDUCTION_PAUSE; mask <<= 1) {
								if ((mask & effect) != 0) {
									switch (mask) {
										case BaseEffector.RTE_TRANSDUCTION_START:
											this.transducerStack.get(this.transducerStack.size() - 2).state = state;
											break;
										case BaseEffector.RTE_TRANSDUCTION_STOP:
											break;
										case BaseEffector.RTE_TRANSDUCTION_PUSH:
											if ((null != inputBuffer) && (position <= limit)) {
												inputBuffer.position(limit);
											}
											limit = -1;
											break;
										case BaseEffector.RTE_TRANSDUCTION_POP:
											limit = -1;
											break;
										default:
											break;
									}
								}
							} 
						}
						if (effect >= BaseEffector.RTE_TRANSDUCTION_PAUSE) {
							break T;
						} else if (clutch != BaseEffector.RTE_TRANSDUCTION_RUN) {
							break;
						}
					}
					
					if (currentInput == this.eosSignal) {
						IInput input = this.inputStack.peek();
						inputBuffer = (null != input) ? input.get() : null;
						if (null != inputBuffer) {
							inputArray = inputBuffer.array();
							position = inputBuffer.position();
							limit = inputBuffer.limit();
						}
						status = status();
					}
				}
			}
		} finally {
			if (!this.transducerStack.isEmpty()) {
				this.transducerStack.peek().state = state;
			}
			if (inputBuffer != null) {
				inputBuffer.position(position);
			}
			this.updateSelectedNamedValue();
		}
		return this.status();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#status()
	 */
	@Override
	public int status() {
		if (this.transducerStack != null && !this.transducerStack.isEmpty()) {
			return this.inputStack != null && !this.inputStack.isEmpty() ? ITransduction.RUNNABLE : ITransduction.PAUSED;
		} else {
			return ITransduction.END;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#getTarget()
	 */
	@Override
	public ITarget getTarget() {
		return this.target;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#listValueNames()
	 */
	@Override
	public String[] listValueNames() {
		return this.valueNameIndexMap.keySet().toArray(new String[this.valueNameIndexMap.size()]);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#getValueNameIndex()
	 */
	@Override
	public int getValueNameIndex(final String valueName) throws TargetBindingException {
		final Integer nameIndex = this.valueNameIndexMap.get(valueName);
		if (nameIndex == null) {
			throw new TargetBindingException(String.format("Named value index is null for name '%1$s'", valueName));
		}
		return nameIndex;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#getNamedValue()
	 */
	@Override
	public final INamedValue getNamedValue(final int nameIndex) {
		this.updateSelectedNamedValue();
		if (nameIndex < this.namedValueHandles.length && this.namedValueHandles[nameIndex] != null) {
			final NamedValue namedValueHandle = this.namedValueHandles[nameIndex];
			namedValueHandle.setValue(this.namedValue[nameIndex]);
			namedValueHandle.setLength(this.valueLength[nameIndex]);
			return namedValueHandle;
		} else {
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#getSelectedValue()
	 */
	@Override
	public final INamedValue getSelectedValue() {
		this.updateSelectedNamedValue();
		if (this.selectionIndex < this.namedValueHandles.length && this.namedValueHandles[this.selectionIndex] != null) {
			final NamedValue namedValueHandle = this.namedValueHandles[this.selectionIndex];
			namedValueHandle.setValue(this.namedValue[this.selectionIndex]);
			namedValueHandle.setLength(this.valueLength[this.selectionIndex]);
			return namedValueHandle;
		} else {
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#getName()
	 */
	@Override
	public String getName() {
		return this.getClass().getName();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#bind(ITransduction)
	 */
	@Override
	public IEffector<?>[] bind(final ITransduction transduction) throws TargetBindingException {
		return this.base;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#getTransduction()
	 */
	@Override
	public ITransduction getTransduction() {
		return this;
	}

	/**
	 * For internal use
	 * 
	 * @return The transduction target effectors
	 */
	public Gearbox getGearbox() {
		return this.gearbox;
	}

	/**
	 * For internal use
	 * 
	 * @return The transduction target effectors
	 */
	public IEffector<?>[] getEffectors() {
		return this.effectors;
	}

	private String getErrorInput(final char[] inputArray, final int position, final int state) {
		int start = (position < 16) ? 0 : position - 16;
		String in = String.copyValueOf(inputArray, start, position - start);
		return String.format("[state=%1$d; char=%2$d; pos=%3$d; <%4$s>]", state, (int)inputArray[position - 1], position, in);
	}

	private SignalInput[] getSignalInputs() throws InputException {
		final SignalInput[] signals = new SignalInput[this.gearbox.getSignalCount()];
		for (int i = 0; i < signals.length; i++) {
			signals[i] = new SignalInput(new char[][] { new char[] { (char) (this.gearbox.getSignalBase() + i) } });
		}
		return signals;
	}

	Integer getNamedValueReference(final char[] chars, final boolean allocateIndex) {
		if (chars != null && chars.length > 0 && chars[0] == Transduction.TYPE_REFERENCE_VALUE) {
			final String valueName = new String(chars, 1, chars.length - 1);
			Integer nameIndex = this.valueNameIndexMap.get(valueName);
			if (nameIndex == null && allocateIndex) {
				nameIndex = this.valueNameIndexMap.size();
				this.valueNameIndexMap.put(valueName, nameIndex);
				this.valueLength[nameIndex] = 0;
			}
			return nameIndex;
		}
		return null;
	}

	void updateSelectedNamedValue() {
		this.namedValue[this.selectionIndex] = this.selectionValue;
		this.valueLength[this.selectionIndex] = this.selectionPosition;
	}

	char[] copyNamedValue(final int nameIndex) {
		this.updateSelectedNamedValue();
		if (this.namedValue[nameIndex] != null) {
			return Arrays.copyOf(this.namedValue[nameIndex], this.valueLength[nameIndex]);
		} else {
			return Transduction.EMPTY;
		}
	}

	void ensureNamedValueCapacity(final int maxIndex) {
		if (this.namedValue.length < maxIndex) {
			this.valueLength = Arrays.copyOf(this.valueLength, maxIndex);
			this.namedValue = Arrays.copyOf(this.namedValue, maxIndex);
			for (int i = this.namedValue.length; i < maxIndex; i++) {
				this.namedValue[i] = null;
			}
		}
	}

	int select(final int selectionIndex) {
		final int currentIndex = this.selectionIndex;
		this.selectionIndex = selectionIndex;
		this.valueLength[currentIndex] = this.selectionPosition;
		this.selectionPosition = this.valueLength[selectionIndex];
		this.namedValue[currentIndex] = this.selectionValue;
		this.selectionValue = this.namedValue[selectionIndex];
		if (this.selectionValue == null) {
			this.selectionValue = new char[Transduction.INITIAL_NAMED_VALUE_CHARS];
		}
		return BaseEffector.RTE_TRANSDUCTION_RUN;
	}

	int paste(final char[] text) {
		final int end = this.selectionPosition + text.length;
		if (end > this.selectionValue.length) {
			this.selectionValue = Arrays.copyOf(this.selectionValue, (end * 3) >> 1);
		}
		System.arraycopy(text, 0, this.selectionValue, this.selectionPosition, text.length);
		this.selectionPosition += text.length;
		return BaseEffector.RTE_TRANSDUCTION_RUN;
	}

	int copy(final int nameIndex) {
		int length = this.valueLength[nameIndex];
		if (length != 0) {
			if ((this.selectionPosition + length) > this.selectionValue.length) {
				this.selectionValue = Arrays.copyOf(this.selectionValue, length + ((this.selectionValue.length * 3) >> 1));
				this.namedValue[this.selectionIndex] = this.selectionValue;
			}
			System.arraycopy(this.namedValue[nameIndex], 0, this.selectionValue, this.selectionPosition, length);
			this.selectionPosition += length;
		}
		return BaseEffector.RTE_TRANSDUCTION_RUN;
	}

	int cut(final int nameIndex) {
		if (nameIndex != this.selectionIndex) {
			this.copy(nameIndex);
			this.valueLength[nameIndex] = 0;
		}
		return BaseEffector.RTE_TRANSDUCTION_RUN;
	}

	int clear(final int nameIndex) {
		this.valueLength[nameIndex] = 0;
		if (nameIndex == this.selectionIndex) {
			this.selectionPosition = 0;
		}
		return BaseEffector.RTE_TRANSDUCTION_RUN;
	}

	int clear() {
		Arrays.fill(this.valueLength, 0);
		this.selectionPosition = 0;
		return BaseEffector.RTE_TRANSDUCTION_RUN;
	}

	private void mark(CharBuffer inputBuffer, final int position) throws EffectorException {
		inputBuffer.position(position);
		IInput input = this.inputStack.peek();
		try {
			if (!inputBuffer.hasRemaining()) {
				do {
					do {
						inputBuffer = input.get();
					} while (inputBuffer != null && !inputBuffer.hasRemaining());
					if (inputBuffer == null) {
						input = !this.inputStack.isEmpty() ? this.inputStack.pop() : null;
						inputBuffer = input != null ? input.get() : null;
					}
				} while (inputBuffer != null && !inputBuffer.hasRemaining());
			}
			if (inputBuffer != null && input != null) {
				input.mark();
			}
		} catch (final InputException e) {
			throw new EffectorException("Unable to mark input", e);
		}
	}

	private int reset(final CharBuffer inputBuffer, final int position) throws EffectorException {
		inputBuffer.position(position);
		try {
			return this.inputStack.peek().reset();
		} catch (final MarkLimitExceededException e) {
			throw new EffectorException("Unable to reset input", e);
		} catch (final InputException e) {
			throw new EffectorException("Unable to reset input", e);
		}
	}

	int counter(final int[] countdown) {
		System.arraycopy(countdown, 0, this.transducerStack.peek().countdown, 0, countdown.length);
		return BaseEffector.RTE_TRANSDUCTION_RUN;
	}

	int save(final int[] nameIndexes) {
		this.transducerStack.peek().save(nameIndexes, this.namedValue, this.valueLength);
		return BaseEffector.RTE_TRANSDUCTION_RUN;
	}

	int in(final int signal) throws InputException {
		this.inputStack.push(this.signalInputs[signal - this.gearbox.getSignalBase()].rewind());
		return BaseEffector.RTE_TRANSDUCTION_PUSH;
	}

	int in(final char[][] input) throws InputException {
		if (input.length == 1 && input[0].length == 1 && input[0][0] >= this.gearbox.getSignalBase()) {
			return this.in(input[0][0]);
		} else {
			this.inputStack.push(new SignalInput(input));
			return BaseEffector.RTE_TRANSDUCTION_PUSH;
		}
	}

	int pushTransducer(final Integer transducerOrdinal) throws EffectorException {
		try {
			final TransducerState transducerState = this.transducerStack.next();
			if (transducerState == null) {
				this.transducerStack.push(new TransducerState(0, this.gearbox.loadTransducer(transducerOrdinal)));
			} else {
				transducerState.reset(0, this.gearbox.loadTransducer(transducerOrdinal));
			}
			return BaseEffector.RTE_TRANSDUCTION_START;
		} catch (final TransducerNotFoundException e) {
			throw new EffectorException(String.format("The start effector failed to load %1$s", this.gearbox.getTransducerName(transducerOrdinal)), e);
		} catch (final GearboxException e) {
			throw new EffectorException(String.format("The start effector failed to load %1$s", this.gearbox.getTransducerName(transducerOrdinal)), e);
		}
	}

	int shiftTransducer(final int transducerOrdinal) throws EffectorException {
		final TransducerState transducerState = this.transducerStack.peek();
		try {
			transducerState.state = 0;
			transducerState.transducer = this.gearbox.loadTransducer(transducerOrdinal);
			return BaseEffector.RTE_TRANSDUCTION_SHIFT;
		} catch (final TransducerNotFoundException e) {
			throw new EffectorException(String.format("The shift effector failed to load %1$s", this.gearbox.getTransducerName(transducerOrdinal)), e);
		} catch (final GearboxException e) {
			throw new EffectorException(String.format("The shift effector failed to load %1$s", this.gearbox.getTransducerName(transducerOrdinal)), e);
		}
	}

	int popTransducer() throws EffectorException {
		try {
			this.transducerStack.peek().reset(0, null);
			final TransducerState popped = this.transducerStack.pop();
			if (popped != null) {
				if (popped.nameIndex != null) {
					popped.restore(this.namedValue, this.valueLength);
				}
				return BaseEffector.RTE_TRANSDUCTION_STOP;
			}
			return BaseEffector.RTE_TRANSDUCTION_END;
		} catch (final Exception e) {
			throw new EffectorException("The stop effector failed", e);
		}
	}

	private IEffector<?>[] bind(final ITarget target, final boolean warn) throws TargetBindingException {
		final Map<String, Integer> effectorOrdinalMap = this.gearbox.getEffectorOrdinalMap();
		final byte[][][][] effectorParametersIndex = this.gearbox.getEffectorParametersIndex();
		final IEffector<?>[] effectors = new IEffector<?>[effectorOrdinalMap.size()];
		final ArrayList<String> unbound = new ArrayList<String>();
		for (final IEffector<?>[] effectorList : new IEffector[][] { this.base, target.bind(this) }) {
			for (final IEffector<?> effector : effectorList) {
				final Integer ordinalIndex = effectorOrdinalMap.get(effector.getName());
				if (ordinalIndex != null) {
					if (effectors[ordinalIndex] == null) {
						effectors[ordinalIndex] = effector;
						final byte[][][] effectorParameters = effectorParametersIndex[ordinalIndex];
						if (effector instanceof IParameterizedEffector) {
							if (effectorParameters != null) {
								final IParameterizedEffector<?, ?> parameterizedEffector = (IParameterizedEffector<?, ?>) effector;
								parameterizedEffector.newParameters(effectorParameters.length);
								// TODO: provide named parameter for anonymous->0 and shift loop i = 1; i <= length
								for (int i = 0; i < effectorParameters.length; i++) {
									try {
										parameterizedEffector.setParameter(i, effectorParameters[i]);
									} catch (final Exception e) {
										final String message = String.format("Unable to compile parameters for effector '%1$s': %2$s", parameterizedEffector.getName(), this.gearbox.parameterToString(effectorParameters[i]));
										Transduction.logger.log(Level.SEVERE, message, e);
										unbound.add(message);
									}
								}
							} else if (warn) {
								// TODO: provide named parameter for anonymous->0 and remove this warning
								Transduction.logger.warning(String.format("%1$s.%2$s: effector requires parameters\n", target.getName(), effector.getName()));
							}
						} else if (effectorParameters != null) {
							final String message = String.format("%1$s.%2$s: effector does not accept parameters\n", target.getName(), effector.getName());
							Transduction.logger.severe(message);
							unbound.add(message);
						}
					} else if (warn) {
						Transduction.logger.info(String.format("%1$s.%2$s: effector cannot be overridden\n", target.getName(), effector.getName()));
					}
				} else if (warn) {
					Transduction.logger.info(String.format("%1$s.%2$s: effector not referenced in gearbox\n", target.getName(), effector.getName()));
				}
			}
		}
		for (final Map.Entry<String, Integer> entry : effectorOrdinalMap.entrySet()) {
			if (effectors[entry.getValue()] == null) {
				unbound.add(String.format("%1$s.%2$s: effector not found in target", target.getName(), entry.getKey()));
			}
		}
		if (unbound.size() > 0) {
			final TargetBindingException e = new TargetBindingException(target.getName());
			e.setUnboundEffectorList(unbound);
			throw e;
		}
		return effectors;
	}

	private final static class InlineEffector extends BaseEffector<Transduction> {
		private InlineEffector(final Transduction transduction, final String name) {
			super(transduction, name);
		}

		@Override
		public final int invoke() throws EffectorException {
			throw new EffectorException(String.format("Cannot bind inline effector '%1$s'", super.getName()));
		}
	}

	private final static class PasteEffector extends BaseParameterizedEffector<Transduction, char[]> {
		private PasteEffector(final Transduction transduction) {
			super(transduction, "paste");
		}

		@Override
		public void newParameters(final int parameterCount) {
			super.setParameters(new char[parameterCount][]);
		}

		@Override
		public final int invoke() throws EffectorException {
			throw new EffectorException("Cannot bind inline effector 'paste'");
		}

		@Override
		public void setParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The paste effector accepts at most one parameter");
			}
			super.setParameter(parameterIndex, super.decodeParameter(parameterList[0]));
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().paste(super.getParameter(parameterIndex));
		}
	}

	private final static class SelectEffector extends BaseNamedValueEffector {
		private SelectEffector(final Transduction transduction) {
			super(transduction, "select");
		}

		@Override
		public final int invoke() throws EffectorException {
			return super.getTarget().select(0);
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().select(super.getParameter(parameterIndex));
		}
	}

	private final static class CopyEffector extends BaseNamedValueEffector {
		private CopyEffector(final Transduction transduction) {
			super(transduction, "copy");
		}

		@Override
		public final int invoke() throws EffectorException {
			return super.getTarget().copy(super.getParameter(0));
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().copy(super.getParameter(parameterIndex));
		}
	}

	private final static class CutEffector extends BaseNamedValueEffector {
		private CutEffector(final Transduction transduction) {
			super(transduction, "cut");
		}

		@Override
		public final int invoke() throws EffectorException {
			return super.getTarget().copy(super.getParameter(0));
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().cut(super.getParameter(parameterIndex));
		}
	}

	private final static class ClearEffector extends BaseNamedValueEffector {
		private ClearEffector(final Transduction transduction) {
			super(transduction, "clear");
		}

		@Override
		public final int invoke() throws EffectorException {
			return super.getTarget().clear(0);
		}

		@Override
		public void setParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length == 1 && parameterList[0].length == 2
					&& (char) parameterList[0][0] == Transduction.TYPE_REFERENCE_VALUE
					&& (char) parameterList[0][1] == '*') {
				super.setParameter(parameterIndex, -1);
			} else {
				super.setParameter(parameterIndex, parameterList);
			}
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			final int nameIndex = super.getParameter(parameterIndex);
			return nameIndex >= 0 ? super.getTarget().clear(nameIndex) : super.getTarget().clear();
		}
	}

	private final static class InEffector extends BaseInputOutputEffector {
		private InEffector(final Transduction transduction) {
			super(transduction, "in");
		}

		@Override
		public final int invoke() throws EffectorException {
			try {
				return super.getTarget().in(new char[][] { super.getTarget().getSelectedValue().getValue() });
			} catch (InputException e) {
				throw new EffectorException("The in effector failed", e);
			}
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			try {
				return super.getTarget().in(super.getParameter(parameterIndex));
			} catch (final InputException e) {
				throw new EffectorException("The in effector failed", e);
			}
		}
	}

	private final static class OutEffector extends BaseInputOutputEffector {
		private final static boolean isOutEnabled = System.getProperty("jrte.out.enabled", "true").equals("true");

		private OutEffector(final Transduction transduction) {
			super(transduction, "out");
		}

		@Override
		public final int invoke() throws EffectorException {
			System.out.print(super.getTarget().getSelectedValue().getValue());
			return BaseEffector.RTE_TRANSDUCTION_RUN;
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			if (OutEffector.isOutEnabled) {
				for (final char[] chars : super.getParameter(parameterIndex)) {
					System.out.print(chars);
				}
			}
			return BaseEffector.RTE_TRANSDUCTION_RUN;
		}
	}

	private final static class SaveEffector extends BaseParameterizedEffector<Transduction, int[]> {
		private SaveEffector(final Transduction transduction) {
			super(transduction, "save");
		}

		@Override
		public void newParameters(final int parameterCount) {
			super.setParameters(new int[parameterCount][]);
		}

		@Override
		public final int invoke() throws EffectorException {
			throw new EffectorException("The save effector requires parameters");
		}

		@Override
		public void setParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			final int[] nameIndices = new int[parameterList.length];
			for (int i = 0; i < parameterList.length; i++) {
				final char[] valueName = super.decodeParameter(parameterList[i]);
				if (parameterList[i][0] == Transduction.TYPE_REFERENCE_VALUE) {
					final Integer nameIndex = super.getTarget().getNamedValueReference(valueName, false);
					if (nameIndex != null) {
						super.getTarget().ensureNamedValueCapacity(nameIndex);
						nameIndices[i] = nameIndex;
					} else {
						throw new TargetBindingException(String.format("Unrecognized value reference `%1$s` for %2$s effector", new String(valueName), this.getName()));
					}
				} else {
					throw new TargetBindingException(String.format("Invalid value reference `%1$s` for %2$s effector, requires type indicator ('%3$c') before the value name", new String(valueName), this.getName(), Transduction.TYPE_REFERENCE_VALUE));
				}
			}
			super.setParameter(parameterIndex, nameIndices);
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().save(super.getParameter(parameterIndex));
		}
	}

	private final static class CounterEffector extends BaseParameterizedEffector<Transduction, int[]> {
		private CounterEffector(final Transduction transduction) {
			super(transduction, "counter");
		}

		@Override
		public void newParameters(final int parameterCount) {
			super.setParameters(new int[parameterCount][]);
		}

		@Override
		public final int invoke() throws EffectorException {
			throw new EffectorException("The counter effector requires two parameters");
		}

		@Override
		public void setParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 2) {
				throw new TargetBindingException("The counter effector requires two parameters");
			}
			final char[] signal = super.getTarget().getGearbox().getSignalReference(super.decodeParameter(parameterList[0]));
			if (signal == null) {
				throw new TargetBindingException(String.format("The counter signal '%1$s' is unrecognized", new String(signal)));
			}
			final int ordinal = signal[0];
			final String counterValue = new String(super.decodeParameter(parameterList[1]));
			try {
				super.setParameter(parameterIndex, new int[] { ordinal, Integer.parseInt(counterValue) });
			} catch (final NumberFormatException e) {
				throw new TargetBindingException(String.format("The counter value '%1$s' is not numeric", counterValue));
			}
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().counter(super.getParameter(parameterIndex));
		}
	}

	private final static class StartEffector extends BaseParameterizedEffector<Transduction, Integer> {
		private StartEffector(final Transduction transduction) {
			super(transduction, "start");
		}

		@Override
		public void newParameters(final int parameterCount) {
			super.setParameters(new Integer[parameterCount]);
		}

		@Override
		public final int invoke() throws EffectorException {
			throw new EffectorException("The start effector requires a parameter");
		}

		@Override
		public void setParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The start effector accepts at most one parameter");
			}
			char[] parameter = super.decodeParameter(parameterList[0]);
			String parameterString = new String(parameter);
			if (parameterList[0][0] == Transduction.TYPE_REFERENCE_TRANSDUCER) {
				final String transducerName = super.getTarget().getGearbox().getTransducerReference(parameter);
				final Integer transducerOrdinal = (null != transducerName) ? super.getTarget().getGearbox().getTransducerOrdinal(transducerName) : null;
				if (transducerOrdinal != null) {
					super.setParameter(parameterIndex, transducerOrdinal);
				} else {
					throw new TargetBindingException(String.format("Null transducer reference for start effector: %s", parameterString));
				}
			} else {
				throw new TargetBindingException(String.format("Invalid transducer reference `$s` for start effector, requires type indicator ('$c') before the transducer name", parameterString, Transduction.TYPE_REFERENCE_TRANSDUCER));
			}
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().pushTransducer(super.getParameter(parameterIndex));
		}
	}

	private final static class ShiftEffector extends BaseParameterizedEffector<Transduction, Integer> {
		private ShiftEffector(final Transduction transduction) {
			super(transduction, "shift");
		}

		@Override
		public void newParameters(final int parameterCount) {
			super.setParameters(new Integer[parameterCount]);
		}

		@Override
		public final int invoke() throws EffectorException {
			throw new EffectorException("The shift effector requires a parameter");
		}

		@Override
		public void setParameter(final int parameterIndex, final byte[][] parameterList) throws TargetBindingException {
			if (parameterList.length != 1) {
				throw new TargetBindingException("The shift effector accepts at most one parameter");
			}
			char[] parameter = super.decodeParameter(parameterList[0]);
			String parameterString = new String(parameter);
			if (parameterList[0][0] == Transduction.TYPE_REFERENCE_TRANSDUCER) {
				final String transducerName = super.getTarget().getGearbox().getTransducerReference(parameter);
				final Integer transducerOrdinal = (null != transducerName) ? super.getTarget().getGearbox().getTransducerOrdinal(transducerName) : null;
				if (transducerOrdinal != null) {
					super.setParameter(parameterIndex, transducerOrdinal);
				} else {
					throw new TargetBindingException(String.format("Null transducer reference for shift effector: %s", parameterString));
				}
			} else {
				throw new TargetBindingException(String.format("Invalid transducer reference `$s` for shift effector, requires type indicator ('$c') before the transducer name", parameterString, Transduction.TYPE_REFERENCE_TRANSDUCER));
			}
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			return super.getTarget().shiftTransducer(super.getParameter(parameterIndex));
		}
	}

	private final static class PauseEffector extends BaseEffector<Transduction> {
		private PauseEffector(final Transduction transduction) {
			super(transduction, "pause");
		}

		@Override
		public final int invoke() throws EffectorException {
			return BaseEffector.RTE_TRANSDUCTION_PAUSE;
		}
	}

	private final static class StopEffector extends BaseEffector<Transduction> {
		private StopEffector(final Transduction transduction) {
			super(transduction, "stop");
		}

		@Override
		public final int invoke() throws EffectorException {
			return this.getTarget().popTransducer();
		}
	}

	private final static class EndEffector extends BaseEffector<Transduction> {
		private EndEffector(final Transduction transduction) {
			super(transduction, "end");
		}

		@Override
		public final int invoke() throws EffectorException {
			return BaseEffector.RTE_TRANSDUCTION_POP;
		}
	}
}
