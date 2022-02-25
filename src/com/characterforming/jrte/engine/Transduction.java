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
 * <li>an effector returns RTE_EFFECT_PAUSE
 * <li>the transduction throws an exception.
 * </ol>
 * 
 * @author kb
 */
public final class Transduction implements ITransduction {
	private final static Logger logger = Logger.getLogger(Transduction.class.getName());

	// Predefined signal symbols
	public static final String[] RTE_SIGNAL_NAMES = {
		"nul", "nil", "eol", "eos"
	};
	
	// Type reference prefixes for parameter tape references to transducers
	static final char TYPE_REFERENCE_TRANSDUCER = '@'; 
	static final char TYPE_REFERENCE_SIGNAL = '!';
	static final char TYPE_REFERENCE_VALUE = '~';

	// Number and default length of named values to preinitialize
	private static final int INITIAL_NAMED_VALUE_BUFFERS = 32;
	private static final int INITIAL_NAMED_VALUE_CHARS = 256;

	// The name of the anonymous value (explicitly referenced as `~`). 
	public static final char[] ANONYMOUS_VALUE_REFERENCE = new char[] { };

	// The runtime image of the anonymous value (explicitly referenced as `~`). 
	public static final byte[][] ANONYMOUS_VALUE_RUNTIME = new byte[][] { { } };

	// The compiler image of the anonymous value (explicitly referenced as `~`). 
	public static final byte[][] ANONYMOUS_VALUE_COMPILER = new byte[][] { { (byte)TYPE_REFERENCE_VALUE } };

	// Preinitialized empty char array returned in lieu of null value when named value is undefined.
	private static final char[] EMPTY = {};

	// Inline effector names
	public static final String[] RTE_INLINE_EFFECTORS = {
		"0", "1", "paste", "count", "mark", "reset", "echo"
	};

	// Inline effector ordinals
	static final int RTE_EFFECTOR_NUL = 0;
	static final int RTE_EFFECTOR_NIL = 1;
	static final int RTE_EFFECTOR_PASTE = 2;
	static final int RTE_EFFECTOR_COUNT = 3;
	static final int RTE_EFFECTOR_MARK = 4;
	static final int RTE_EFFECTOR_RESET = 5;
	static final int RTE_EFFECTOR_ECHO = 6;
	
	// Base target (this) effectors.
	private final IEffector<?>[] base = {
		new InlineEffector(this, "0"),
		new InlineEffector(this, "1"),
		new PasteEffector(this),
		new InlineEffector(this, "count"),
		new InlineEffector(this, "mark"),
		new InlineEffector(this, "reset"),
		new InlineEffector(this, "echo"),
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
	};

	static class TransducerState {
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
			return this.transducer != null ? this.transducer.getName() : "empty";
		}
	}

	private final Gearbox gearbox;
	private final ITarget target;
	private final IEffector<?>[] effectors;
	private final int nulSignal;
	private final int eosSignal;
	private final SignalInput[] signalInputs;
	private final HashMap<String, Integer> valueNameIndexMap;
	private final NamedValue[] namedValueHandles;
	private InputStack inputStack;
	private TransducerStack transducerStack;
	private int selectionIndex;
	private char[] selectionValue;
	private int selectionPosition;
	private char[][] namedValue;
	private int[] valueLength;

	/**
	 *  Runtime constructor
	 *  
	 * @param gearbox The gearbox 
	 * @param target The transduction target
	 * @param warn Print warnings during runtime binding
	 * @throws TargetNotFoundException On error
	 * @throws GearboxException On error
	 * @throws TargetBindingException On error
	 */
	public Transduction(final Gearbox gearbox, final ITarget target, final boolean warn) throws TargetNotFoundException, GearboxException, TargetBindingException {
		this.target = target;
		this.gearbox = gearbox;
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

	/**
	 *  Gearbox constructor instantiates base effector array only for parameter construction during compilation
	 *  
	 * @param gearbox The gearbox 
	 */
	public Transduction(final Gearbox gearbox) {
		this.target = this;
		this.gearbox = gearbox;
		this.nulSignal = this.gearbox.getSignalOrdinal("nul");
		this.eosSignal = this.gearbox.getSignalOrdinal("eos");
		this.signalInputs = null;
		this.valueLength = null;
		this.namedValue = null;
		this.valueNameIndexMap = null;
		this.selectionIndex = 0;
		this.selectionValue = null;
		this.selectionPosition = 0;
		this.effectors = null;
		this.namedValueHandles = null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#start(String)
	 */
	public void start(final String transducerName) throws GearboxException, TransducerNotFoundException, RteException {
		if (this.target == null) {
			throw new RteException(String.format("Transduction not bound, cannot start %1$s", transducerName));
		}
		Transducer transducer = this.gearbox.loadTransducer(this.gearbox.getTransducerOrdinal(transducerName));
		if (transducer != null) {
			this.transducerStack = new TransducerStack(8);
			this.transducerStack.push(transducer);
			this.selectionIndex = this.getNamedValueReference(Transduction.ANONYMOUS_VALUE_REFERENCE, true);
			this.selectionValue = this.namedValue[this.selectionIndex] = new char[Transduction.INITIAL_NAMED_VALUE_CHARS];
			this.selectionPosition = 0;
			this.inputStack = null;
			this.clear();
		} else {
			throw new GearboxException(String.format("Unknown transducer (%s)", transducerName));
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#input(IInput[])
	 */
	public void input(final IInput[] inputs) throws RteException {
		if (this.inputStack == null) {
			this.inputStack = new InputStack(inputs.length);
			for (int i = 0; i < inputs.length; i++) {
				this.inputStack.push(inputs[i]);
			}
		} else {
			this.inputStack.put(inputs);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#run()
	 */
	public int run() throws RteException {
		CharBuffer inputBuffer = null;
		int position = 0, limit = 0;
		String debug = null;
		int state = 0;
		try {
T:			while (this.status() == ITransduction.RUNNABLE) {
				final TransducerState transducerState = this.transducerStack.peek();
				final Transducer transducer = transducerState.transducer;
				final int[] inputFilter = transducer.getInputFilter();
				final int[][] transitionMatrix = transducer.getTransitionMatrix();
				final int[] effectorVector = transducer.getEffectorVector();
				
				state = transducerState.state;
				inputBuffer = null;
				
				int errorInput = 0;
				int currentInput = 0;
				char[] inputArray = null;
				int status = this.status();
				while (status == ITransduction.RUNNABLE) {
					
					// Get next input symbol from local array, or try to refresh it if exhausted
					if (position < limit) {
						currentInput = inputArray[position++];
					} else {
						IInput input = this.inputStack.peek();
						while (input != null) {
							inputBuffer = input.get();
							if (inputBuffer != null) {
								inputArray = inputBuffer.array();
								position = inputBuffer.position();
								limit = inputBuffer.limit();
								currentInput = inputArray[position++];
								break;
							}
							input = this.inputStack.pop();
						}
						if (input == null) {
							inputBuffer = null;
							if ((currentInput != this.eosSignal) && (currentInput != this.nulSignal)) {
								currentInput = this.eosSignal;
							} else {
								break T;
							} 
						} 
					}
					
					// Filter input to equivalence ordinal and map ordinal and state to next state and action
					final int transition[] = transitionMatrix[state + inputFilter[currentInput]];
					state = transition[0];
					int action = transition[1];
					int index = 0;
					if (action < Transduction.RTE_EFFECTOR_NUL) {
						index = -action;
						action = effectorVector[index++];
					}
					
					// Invoke a vector of 1 or more effectors and record side effects on transduction and input stacks 
					int effect = IEffector.RTE_EFFECT_NONE;
					do {
						switch (action) {
							case Transduction.RTE_EFFECTOR_NUL:
								if (currentInput == this.nulSignal) {
									debug = this.getErrorInput(state, position);
									throw new DomainErrorException(String.format("Domain error in %1$s %2$s", transducer.getName(), debug));
								} else if (currentInput != this.eosSignal) {
									effect |= this.in(this.nulSignal);
									errorInput = currentInput;
								}
								break;
							case Transduction.RTE_EFFECTOR_NIL:
								break;
							case Transduction.RTE_EFFECTOR_PASTE:
								if (this.selectionPosition >= this.selectionValue.length) {
									this.selectionValue = Arrays.copyOf(this.selectionValue, (this.selectionPosition * 3) >> 1);
								}
								this.selectionValue[this.selectionPosition++] = (char)currentInput;
								break;
							case Transduction.RTE_EFFECTOR_COUNT:
								if (--transducerState.countdown[0] <= 0) {
									effect |= this.in(transducerState.countdown[1]);
									transducerState.countdown[0] = 0;
								}
								break;
							case Transduction.RTE_EFFECTOR_MARK:
								if (inputBuffer != null) {
									effect |= this.mark(inputBuffer, position);
								}
								break;
							case Transduction.RTE_EFFECTOR_RESET:
								if (inputBuffer != null) {
									inputBuffer.position(position);
									effect |= this.reset(inputBuffer);
									position = this.inputStack.peek().get().position();
								}
							break;
							case Transduction.RTE_EFFECTOR_ECHO:
								effect |= this.in(new char[][] { { (char)((currentInput == this.nulSignal) ? errorInput : currentInput) } });
								break;
							default:
								if (action > 0) {
									effect |= this.effectors[action].invoke();
								} else {
									effect |= ((IParameterizedEffector<?, ?>)this.effectors[-action]).invoke(effectorVector[index++]);
								}
								break;
						}
						if (index > 0) {
							action = effectorVector[index++];
						} else {
							break;
						}
					} while (action != Transduction.RTE_EFFECTOR_NUL);
					
					if ((position == limit) || (effect != IEffector.RTE_EFFECT_NONE)) {
						// Synchronize position with input buffer at limit and when input/transducer stacks are modified 
						if (inputBuffer != null) {
							inputBuffer.position(position);
						}
						
						// Handle side effects relating to transduction status (input/transduction stacks)
						if (effect != IEffector.RTE_EFFECT_NONE) {
							status = this.status();
							
							// Input pushed, popped, marked or reset, or transducer stack pushed, popped, or shifted?
							if (0 != (effect & (IEffector.RTE_EFFECT_PUSH | IEffector.RTE_EFFECT_POP | IEffector.RTE_EFFECT_START | IEffector.RTE_EFFECT_STOP | IEffector.RTE_EFFECT_SHIFT))) {
								limit = -1; 
								if (0 != (effect & (IEffector.RTE_EFFECT_START | IEffector.RTE_EFFECT_STOP | IEffector.RTE_EFFECT_SHIFT))) {
									if (0 != (effect & IEffector.RTE_EFFECT_START)) {
										this.transducerStack.get(this.transducerStack.tos() - 1).state = state;
									}
									break;
								}
							}
							
							// Transduction paused or stopped?
							if (effect >= IEffector.RTE_EFFECT_PAUSE) {
								break T;
							} 
						}
					}
										
					// Check for input that might have been pushed while handling end of stream signal
					if (currentInput == this.eosSignal) {
						status = this.status();
					}
				}
			}
		} finally {
			
			// Prepare to pause (or stop) transduction
			if (!this.transducerStack.isEmpty()) {
				this.transducerStack.peek().state = state;
			}
			if (inputBuffer != null) {
				inputBuffer.position(position);
			}
			this.updateSelectedNamedValue();
		}
		
		// Transduction is paused or stopped; if paused it will resume on next call to run()
		return this.status();
	}

	private int mark(CharBuffer inputBuffer, int position) throws InputException {
		CharBuffer buffer = null;
		inputBuffer.position(position);
		IInput input = this.inputStack.peek();
		while (input != null) {
			buffer = input.get();
			if ((buffer != null) && buffer.hasRemaining()) {
				input.mark();
				break;
			}
			input = this.inputStack.pop();
		}
		return (buffer != inputBuffer) ? IEffector.RTE_EFFECT_POP : IEffector.RTE_EFFECT_NONE;
	}

	private int reset(CharBuffer inputBuffer) throws InputException {
		IInput input = this.inputStack.peek();
		CharBuffer[] buffers = input.reset();
		if ((buffers != null) && (buffers.length > 0)) {
			buffers[0].reset();
			for (int i = 1; i < buffers.length; i++) {
				buffers[i].rewind();
			}
			assert(buffers[buffers.length - 1] == inputBuffer);
			if (buffers.length > 2) {
				buffers = Arrays.copyOfRange(buffers, 0, buffers.length - 2);
				this.inputStack.push(new SignalInput(buffers));
			}
			return IEffector.RTE_EFFECT_PUSH;
		}
		return IEffector.RTE_EFFECT_NONE;
	}

	int select(final int selectionIndex) {
		final int currentIndex = this.selectionIndex;
		this.valueLength[currentIndex] = this.selectionPosition;
		this.namedValue[currentIndex] = this.selectionValue;
		this.selectionIndex = selectionIndex;
		this.selectionPosition = this.valueLength[selectionIndex];
		this.selectionValue = this.namedValue[selectionIndex];
		if (this.selectionValue == null) {
			this.selectionValue = new char[Transduction.INITIAL_NAMED_VALUE_CHARS];
			assert(this.selectionPosition == 0);
		}
		return IEffector.RTE_EFFECT_NONE;
	}

	int paste(final char[] text) {
		final int end = this.selectionPosition + text.length;
		if (end > this.selectionValue.length) {
			this.selectionValue = Arrays.copyOf(this.selectionValue, (end * 3) >> 1);
		}
		System.arraycopy(text, 0, this.selectionValue, this.selectionPosition, text.length);
		this.selectionPosition += text.length;
		return IEffector.RTE_EFFECT_NONE;
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
		return IEffector.RTE_EFFECT_NONE;
	}

	int cut(final int nameIndex) {
		this.copy(nameIndex);
		this.valueLength[nameIndex] = 0;
		return IEffector.RTE_EFFECT_NONE;
	}

	int clear(final int nameIndex) {
		int index = (nameIndex == -2) ? this.selectionIndex : nameIndex;
		if (index >= 0) {
			this.valueLength[index] = 0;
			if (index == this.selectionIndex) {
				this.selectionPosition = 0;
			}
		} else if (index == -1) {
			return clear();
		}
		return IEffector.RTE_EFFECT_NONE;
	}

	int clear() {
		Arrays.fill(this.valueLength, 0);
		this.selectionPosition = 0;
		return IEffector.RTE_EFFECT_NONE;
	}

	int counter(final int[] countdown) {
		System.arraycopy(countdown, 0, this.transducerStack.peek().countdown, 0, countdown.length);
		return IEffector.RTE_EFFECT_NONE;
	}

	int save(final int[] nameIndexes) {
		this.transducerStack.peek().save(nameIndexes, this.namedValue, this.valueLength);
		return IEffector.RTE_EFFECT_NONE;
	}

	int in(final int signal) throws InputException {
		SignalInput input = this.signalInputs[signal - this.gearbox.getSignalBase()];
		input.rewind();
		this.inputStack.push(input);
		return IEffector.RTE_EFFECT_PUSH;
	}

	int in(final char[][] input) throws InputException {
		if (input.length == 1 && input[0].length == 1 && input[0][0] >= this.gearbox.getSignalBase()) {
			return this.in(input[0][0]);
		} else {
			this.inputStack.push(new SignalInput(input));
			return IEffector.RTE_EFFECT_PUSH;
		}
	}

	int pushTransducer(final Integer transducerOrdinal) throws EffectorException {
		try {
			this.transducerStack.push(this.gearbox.loadTransducer(transducerOrdinal));
			return IEffector.RTE_EFFECT_START;
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
			return IEffector.RTE_EFFECT_SHIFT;
		} catch (final TransducerNotFoundException e) {
			throw new EffectorException(String.format("The shift effector failed to load %1$s", this.gearbox.getTransducerName(transducerOrdinal)), e);
		} catch (final GearboxException e) {
			throw new EffectorException(String.format("The shift effector failed to load %1$s", this.gearbox.getTransducerName(transducerOrdinal)), e);
		}
	}

	int popTransducer() throws EffectorException {
		try {
			final TransducerState popped = this.transducerStack.pop();
			if (popped != null) {
				if (popped.nameIndex != null) {
					popped.restore(this.namedValue, this.valueLength);
				}
				return IEffector.RTE_EFFECT_STOP;
			}
			return IEffector.RTE_EFFECT_STOPPED;
		} catch (final Exception e) {
			throw new EffectorException("The stop effector failed", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#status()
	 */
	public int status() {
		if (this.transducerStack != null && !this.transducerStack.isEmpty()) {
			if (this.inputStack != null && !this.inputStack.isEmpty()) {
				return ITransduction.RUNNABLE;
			} else {
				return ITransduction.PAUSED;
			}
		} else {
			return ITransduction.STOPPED;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#getTarget()
	 */
	public ITarget getTarget() {
		return this.target;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#listValueNames()
	 */
	public String[] listValueNames() {
		return this.valueNameIndexMap.keySet().toArray(new String[this.valueNameIndexMap.size()]);
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITransduction#getValueNameIndex()
	 */
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
	public String getName() {
		return this.getClass().getName();
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#bind(ITransduction)
	 */
	public IEffector<?>[] bind(final ITransduction transduction) throws TargetBindingException {
		return this.base;
	}

	/*
	 * (non-Javadoc)
	 * @see com.characterforming.jrte.engine.ITarget#getTransduction()
	 */
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

	private String getErrorInput(final int state, final int index) {
		String error = "\n\tTransducer stack:\n";
		this.transducerStack.peek().state = state;
		for (int t = this.transducerStack.tos(); t >= 0; t--) {
			TransducerState transducerState = this.transducerStack.get(t);
			error += String.format("\t\t%1$s (state %2$d)\n", transducerState.transducer.getName(), transducerState.state);
		}
		error += "\tInput stack:\n";
		for (int i = this.inputStack.tos(); i >= 0 ; i--) {
			final CharBuffer input = this.inputStack.get(i).current();
			if (input != null) {
				char[] array = input.array();
				int position = (i < this.inputStack.tos()) ? index - 1: input.position();
				int start = Math.max(0, position - 8);
				int end = Math.min(start + 16, input.limit());
				String inch;
				if (Character.getType(array[position]) != Character.CONTROL) {
					inch = String.format("%1$c", (int) array[position]);
				} else {
					inch = String.format("0x%1$x", (int) array[position]);
				}
				error += String.format("\t\t[ char='%1$s' (0x%2$x); pos=%3$d; < ", inch, (int) array[position], position);
				while (start < end) {
					char ch = array[start];
					if (Character.getType(ch) != Character.CONTROL) {
						error += String.format((start != position) ? "%1$c " : "[%1$c] ", ch);
					} else {
						error += String.format((start != position) ? "0x%1$x " : "[0x%1$x] ", (int)ch);
					}
					start += 1;
				}
				error += "> ]\n";
			} else {
				error += "[ < end-of-input > ]\n";
			} 
		}
		return error;
	}

	private SignalInput[] getSignalInputs() throws InputException {
		final SignalInput[] signals = new SignalInput[this.gearbox.getSignalCount()];
		for (int i = 0; i < signals.length; i++) {
			signals[i] = new SignalInput(new char[][] { new char[] { (char) (this.gearbox.getSignalBase() + i) } });
		}
		return signals;
	}

	Integer getNamedValueReference(final char[] chars, final boolean allocateIndex) {
		final String valueName = new String(chars);
		Integer nameIndex = this.valueNameIndexMap.get(valueName);
		if (nameIndex == null && allocateIndex) {
			nameIndex = this.valueNameIndexMap.size();
			this.valueNameIndexMap.put(valueName, nameIndex);
			this.valueLength[nameIndex] = 0;
		}
		return nameIndex;
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

	char[] copyNamedValue() {
		return this.copyNamedValue(this.selectionIndex);
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
								int parameterIndex = 0;
								parameterizedEffector.newParameters(effectorParameters.length);
								for (int i = 0; i < effectorParameters.length; i++) {
									try {
										parameterizedEffector.setParameter(parameterIndex++, effectorParameters[i]);
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
			throw new EffectorException(String.format("Cannot invoke inline effector '%1$s'", super.getName()));
		}
	}

	private final static class PasteEffector extends BaseInputOutputEffector {
		private PasteEffector(final Transduction transduction) {
			super(transduction, "paste");
		}

		@Override
		public final int invoke() throws EffectorException {
			throw new EffectorException("Cannot invoke inline effector 'paste'");
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			int effect = 0;
			char[][] text = super.getParameter(parameterIndex);
			for (char[] chars : text) {
				effect |= super.getTarget().paste(chars);
			}
			return effect;
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
			return super.getTarget().copy(0);
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
			return super.getTarget().cut(0);
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
			return super.getTarget().clear(-2);
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
			return super.getTarget().clear(nameIndex);
		}
	}

	private final static class InEffector extends BaseInputOutputEffector {
		private InEffector(final Transduction transduction) {
			super(transduction, "in");
		}

		@Override
		public final int invoke() throws EffectorException {
			try {
				return super.getTarget().in(new char[][] { super.getTarget().copyNamedValue() });
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
			INamedValue handle = super.getTarget().getSelectedValue();
			char value[] = handle.getValue();
			int length = handle.getLength();
			for (int i = 0; i < length; i++) {
				System.out.print(value[i]);
			}
			System.out.flush();
			return IEffector.RTE_EFFECT_NONE;
		}

		@Override
		public final int invoke(final int parameterIndex) throws EffectorException {
			if (OutEffector.isOutEnabled) {
				for (final char[] chars : super.getParameter(parameterIndex)) {
					System.out.print(chars);
				}
			}
			return IEffector.RTE_EFFECT_NONE;
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
					final char[] referenceName = Arrays.copyOfRange(valueName, 1, valueName.length);
					final Integer referenceIndex = super.getTarget().getNamedValueReference(referenceName, true);
					if (referenceIndex != null) {
						super.getTarget().ensureNamedValueCapacity(referenceIndex);
						nameIndices[i] = referenceIndex;
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
				super.setParameter(parameterIndex, new int[] { Integer.parseInt(counterValue), ordinal });
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
			return IEffector.RTE_EFFECT_PAUSE;
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
}
