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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IModel;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransduction;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.BaseReceptorEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.CompilationException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/** Model compiler implements ITarget for the ribose compiler model. */
public final class ModelCompiler extends Model implements ITarget, AutoCloseable {

	private static final long VERSION = 210;
	private static final String AMBIGUOUS_STATE_MESSAGE = "%1$s: Ambiguous state %2$d";

	static final int MIN_PRODUCT_LENGTH = Integer.parseInt(System.getProperty("ribose.product.threshold", "-1"));
	static final int MIN_SUM_SIZE = Integer.parseInt(System.getProperty("ribose.sum.threshold", "-1"));
	static final int MIN_SCAN_SIZE = 255;

	private Bytes transducerName;
	int transducerOrdinal;
	private final Assembler assembler;
	private ITransductor transductor;
	private HashMap<Integer, Integer> inputStateMap;
	private HashMap<Integer, ArrayList<Transition>> stateTransitionMap;
	private HashMap<Ints, Integer> effectorVectorMap;
	private ArrayList<HashSet<Token>> tapeTokens;
	private int[] effectorVectors;
	private List<String> errors;
	private Header header = null;
	private Transition[] transitions = null;
	private int[] inputEquivalenceIndex;
	private int[][][] kernelMatrix;
	private int transition;

	record Transition (int from, int to, int tape, Bytes symbol, boolean isFinal) {}

	record Header (int version, int tapes, int transitions, int states, int symbols) {}

	final class HeaderEffector extends BaseReceptorEffector<ModelCompiler> {
		// Receiver fields with default values used if transductor fields are empty
		public int version = -1, tapes = -1, transitions = -1, states = -1, symbols = -1;

		HeaderEffector(ModelCompiler compiler) throws CharacterCodingException {
			super(compiler, "header", "Automaton",
				new String[] { "version", "tapes", "transitions", "states", "symbols" });
			super.setEffector(this);
		}

		@Override
		public int invoke(int parameterIndex) throws EffectorException {
			int rtx = super.invoke(parameterIndex);
			super.getTarget().putHeader(new Header(
				this.version, this.tapes, this.transitions, this.states, this.symbols));
			super.resetReceivers(parameterIndex);
			return rtx;
		}
	}

	final class TransitionEffector extends BaseReceptorEffector<ModelCompiler> {
		// Receiver fields with default values used if transductor fields are empty
		public int from = -1, to = -1, tape = -1, length = -1;
		public byte[] symbol = Bytes.EMPTY_BYTES;

		TransitionEffector(ModelCompiler compiler) throws CharacterCodingException {
			super(compiler, "transition", "Automaton",
				new String[] { "from","to","tape", "length","symbol" });
			super.setEffector(this);
		}

		@Override
		public int invoke(int parameterIndex) throws EffectorException {
			int rtx = super.invoke(parameterIndex);
			boolean isFinal = this.to == 1 && this.tape == 0 && this.symbol.length == 0;
			ModelCompiler.this.putTransition(new Transition(
				this.from, this.to, this.tape, new Bytes(this.symbol), isFinal));
			super.resetReceivers(parameterIndex);
			return rtx;
		}
	}

	final class AutomatonEffector extends BaseEffector<ModelCompiler> {
		AutomatonEffector(ModelCompiler compiler) throws CharacterCodingException {
			super(compiler, "automaton");
		}

		@Override
		public int invoke() throws EffectorException {
			try {
				ModelCompiler.this.putAutomaton();
			} catch (CharacterCodingException e) {
				throw new EffectorException(e);
			}
			return IEffector.RTX_NONE;
		}
	}

	public ModelCompiler() {
		super();
		this.assembler = null;
	}

	private ModelCompiler(final File modelPath, Class<?> targetClass, TargetMode targetMode)
	throws ModelException, CharacterCodingException {
		super(modelPath, targetClass, targetMode);
		assert super.modelPath.exists();
		assert super.targetMode.isLive();
		this.assembler = new Assembler(this);
		this.transductor = null;
		this.inputStateMap = null;
		this.stateTransitionMap = null;
		this.transducerName = null;
		this.transducerOrdinal = -1;
		this.effectorVectorMap = null;
		this.inputEquivalenceIndex = null;
		this.kernelMatrix = null;
		this.header = null;
		this.transitions = null;
		this.transition = 0;
		this.errors = new ArrayList<>();
		this.tapeTokens = new ArrayList<>(3);
		for (int tape = 0; tape < 3; tape++)
			this.tapeTokens.add(tape, new HashSet<>(128));
		HashSet<Token> parameterTokens = this.tapeTokens.get(2);
		parameterTokens.add(new Token(Model.ALL_FIELDS_NAME, Model.ALL_FIELDS_ORDINAL));
		for (Signal signal : Signal.values())
			if (!signal.isNone())
				parameterTokens.add(new Token(signal.reference().bytes(), signal.signal()));
	}

	@Override // com.characterforming.ribose.IModel.getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override // com.characterforming.ribose.IModel.getEffectors()
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		try {
			return new IEffector<?>[] {
				new HeaderEffector(this),
				new TransitionEffector(this),
				new AutomatonEffector(this)
			};
		} catch (CharacterCodingException e) {
			throw new TargetBindingException(e);
		}
	}

	@Override // AutoCloseable.close()
	public void close() {
		super.close();
	}

	public static boolean compileAutomata(Class<?> targetClass, File riboseModelFile, File inrAutomataDirectory) throws ModelException {
		Logger rtcLogger = Base.getCompileLogger();

		try (ModelCompiler proxyCompiler = new ModelCompiler()) {
			if (!proxyCompiler.createModelFile(riboseModelFile, rtcLogger))
				return false;

			if (targetClass == ModelCompiler.class) {
				boolean saved = false;
				try (ModelCompiler compiler = new ModelCompiler(riboseModelFile, targetClass, TargetMode.LIVE_COMPILER)) {
					if (compiler.compileCompiler(inrAutomataDirectory)) {
						Argument[][] compiledParameters = compiler.compileModelParameters(compiler.errors);
						saved = compiler.validate() && compiler.save(compiledParameters);
						assert saved || compiler.deleteOnClose;
						assert saved == !compiler.hasErrors();
						if (!saved)
							for (String error : compiler.getErrors())
								rtcLogger.severe(error);
					}
				} catch (Exception e) {
					rtcLogger.log(Level.SEVERE, e, () -> String.format("%1$s caught compiling ribose compiler directory '%2$s'.",
						e.getClass().getSimpleName(), inrAutomataDirectory.getPath()));
				}
				return saved;
			}

			File compilerModelFile =  proxyCompiler.lookupCompilerModel();
			if (compilerModelFile == null || !compilerModelFile.exists()) {
				rtcLogger.log(Level.SEVERE, "TCompile.model not found in ribose jar.");
				return false;
			}

			boolean saved = false;
			try (
				IModel compilerRuntime = IModel.loadRiboseModel(compilerModelFile);
				ModelCompiler compiler = new ModelCompiler(riboseModelFile, targetClass, TargetMode.LIVE_TARGET);
			) {
				compiler.setTransductor(compilerRuntime.transductor(compiler));
				for (final String filename : inrAutomataDirectory.list())
					if (filename.endsWith(Base.AUTOMATON_FILE_SUFFIX))
						compiler.compileTransducer(new File(inrAutomataDirectory, filename));
				Argument[][] compiledParameters = compiler.compileModelParameters(compiler.errors);
				saved = compiler.validate() && compiler.save(compiledParameters);
				assert saved || compiler.deleteOnClose;
				assert saved == !compiler.hasErrors();
				if (!saved)
					for (String error : compiler.getErrors())
						rtcLogger.severe(error);
			} catch (Exception e) {
				rtcLogger.log(Level.SEVERE, e, () -> String.format("%1$s caught compiling automata directory '%2$s'.",
					e.getClass().getSimpleName(), inrAutomataDirectory.getPath()));
			} finally {
				assert saved || !riboseModelFile.exists();
			}
			return saved;
		}
	}

	private boolean compileCompiler(File inrAutomataDirectory) {
		Automaton automaton = new Automaton(this, super.rtcLogger);
		return automaton.assemble(inrAutomataDirectory);
	}

	private File lookupCompilerModel() {
		File compilerModelFile = null;
		String compilerModelPath = ModelCompiler.class.getPackageName().replace('.', '/') + "/TCompile.model";
		if (ModelCompiler.class.getClassLoader().getResource(compilerModelPath) != null)
			try {
				compilerModelFile = File.createTempFile("TCompile", ".model");
				compilerModelFile.deleteOnExit();
				try (
					InputStream mis = ModelCompiler.class.getClassLoader().getResourceAsStream(compilerModelPath);
					OutputStream mos = new FileOutputStream(compilerModelFile)
				) {
					byte[] data = new byte[4096];
					int read = -1;
					do {
						read = mis.read(data);
						if (read > 0)
							mos.write(data, 0, read);
					} while (read >= 0);
				}
			} catch (IOException e) {
				compilerModelFile = null;
			}
		return compilerModelFile;
	}

	ModelCompiler reset(File inrFile) throws CharacterCodingException {
		String name = inrFile.getName();
		name = name.substring(0, name.length() - Base.AUTOMATON_FILE_SUFFIX.length());
		this.transducerName = Codec.encode(name);
		this.transducerOrdinal = super.addTransducer(this.transducerName);
		this.inputStateMap = new HashMap<>(256);
		this.stateTransitionMap = null;
		this.effectorVectorMap = null;
		this.inputEquivalenceIndex = null;
		this.kernelMatrix = null;
		this.header = null;
		this.transitions = null;
		this.transition = 0;
		return this;
	}

	private boolean createModelFile(File riboseModelFile, Logger rtcLogger) {
		try {
			if (riboseModelFile.createNewFile())
				return true;
			else
				rtcLogger.log(Level.SEVERE, () -> String.format("Can't overwrite existing model file : %1$s",
					riboseModelFile.getPath()));
		} catch (IOException e) {
			rtcLogger.log(Level.SEVERE, e, () -> String.format("Exception caught creating model file : %1$s",
				riboseModelFile.getPath()));
		}
		return false;
	}

	private boolean compileTransducer(File inrFile) {
		int size = (int)inrFile.length();
		byte[] bytes = null;
		try (DataInputStream f = new DataInputStream(new FileInputStream(inrFile))) {
			this.reset(inrFile);
			int position = 0, length = size;
			bytes = new byte[length];
			while (length > 0) {
				int read = f.read(bytes, position, length);
				position += read;
				length -= read;
			}
			assert position == size;
		} catch (FileNotFoundException e) {
			this.addError(String.format("%1$s: File not found '%2$s'",
				this.transducerName, inrFile.getPath()));
			return false;
		} catch (IOException e) {
			this.addError(String.format("%1$s: IOException compiling '%2$s'; %3$s",
				this.transducerName, inrFile.getPath(), e.getMessage()));
			return false;
		}
		try (ITransduction transduction = super.transduction(this.transductor)) {
			transduction.reset();
			Bytes automaton = Codec.encode("Automaton");
			if (this.transductor.signal(Signal.NIL).push(bytes, size).start(automaton).status().isRunnable()
			&& this.transductor.run().status().isPaused())
				this.transductor.signal(Signal.EOS).run();
			assert !this.transductor.status().isRunnable();
			this.saveTransducer();
		} catch (Exception e) {
			String msg = String.format("%1$s: Failed to compile '%2$s'",
				this.transducerName, inrFile.getPath());
			this.rtcLogger.log(Level.SEVERE, msg, e);
			this.addError(msg);
			return false;
		}
		assert this.transductor.status().isStopped();
		return true;
	}

	private boolean validate() {
		for (Token token : this.tapeTokens.get(0)) {
			if (!token.isLiteral() && !token.isSignal())
				this.addError(String.format("Error: Invalid %2$s token '%1$s' on tape 0",
					token.asString(), token.getTypeName()));
			else if (token.getSymbol().bytes().length > 1
			&& !this.tapeTokens.get(2).contains(token))
				this.addError(String.format("Error: Unrecognized signal reference '%1$s' on tape 0",
					token.asString()));
		}
		for (Token token : this.tapeTokens.get(1)) {
			if (!token.isLiteral())
				this.addError(String.format("Error: Invalid %2$s token '%1$s' on tape 1",
					token.asString(), token.getTypeName()));
			else if (this.getEffectorOrdinal(token.getSymbol()) < 0)
				this.addError(String.format("Error: Unrecognized effector token '%1$s' on tape 1",
					token.asString()));
		}
		for (Token token : this.tapeTokens.get(2)) {
			if (token.isTransducer() && super.getTransducerOrdinal(token.getSymbol()) < 0)
				this.addError(String.format("Error: Unrecognized transducer token '%1$s' on tape 1",
					token.asString()));
			else if (token.isSignal()
			&& super.getSignalOrdinal(token.getSymbol()) > Signal.EOS.signal()
			&& !this.tapeTokens.get(0).contains(token))
				this.addError(String.format("Error: Signal token '%1$s' on tape 2 is never referenced on tape 0",
					token.asString()));
		}
		for (Entry<Bytes, Integer> e : super.transducerOrdinalMap.entrySet()) {
			int ordinal = e.getValue();
			if (super.transducerNameIndex[ordinal] == null
			|| !super.transducerNameIndex[ordinal].equals(e.getKey())
			|| super.transducerOffsetIndex[ordinal] <= 0)
				this.addError(String.format("'%1$s': referenced but not compiled in model",
					e.getKey().asString()));
		}

		if (super.transducerOrdinalMap.isEmpty())
			this.addError("Error: The model is empty");
		return !this.hasErrors();
	}

	void saveTransducer() throws ModelException, CharacterCodingException {
		super.writeTransducer(this.transducerName, this.transducerOrdinal, this.inputEquivalenceIndex, this.kernelMatrix, this.effectorVectors);
		int nStates = this.kernelMatrix.length;
		int nInputs = this.kernelMatrix[0].length;
		int nTransitions = 0;
		for (int input = 0; input < nInputs; input++)
			for (int state = 0; state < nStates; state++)
				if (this.kernelMatrix[state][input][1] != 0)
					nTransitions++;
		final int transitionCount = nTransitions;
		final int fieldCount = super.getFieldCount(this.transducerOrdinal);
		double sparsity = 100 * (1 - ((double)nTransitions / (double)(nStates * nInputs)));
		String info = String.format(
			"%1$21s: %2$5d input classes %3$5d states %4$5d transitions; %5$5d fields; (%6$.0f%% nul)",
				this.getTransducerName(), nInputs, nStates, transitionCount, fieldCount, sparsity);
		super.rtcLogger.log(Level.INFO, () -> info);
		System.out.println(info);
	}

	String getTransducerName() {
		return this.transducerName.asString();
	}

	void putHeader(Header header) {
		if (header.version != ModelCompiler.VERSION)
			this.addError(String.format("%1$s: Invalid INR version %2$d",
				getTransducerName(), header.version));
		if (header.tapes > 3)
			this.addError(String.format("%1$s: Invalid tape count %2$d",
				getTransducerName(), header.tapes));
		this.header = header;
		this.transitions = new Transition[header.transitions];
		stateTransitionMap = new HashMap<>((header.states * 5) >> 2);
	}

	void putTransition(Transition transition) {
		assert this.header.transitions == this.transitions.length;
		if (!transition.isFinal) {
			if (transition.tape < 0)
				this.addError(String.format("%1$s: Epsilon transition from state %2$d to %3$d (use :dfamin to remove these)",
					this.getTransducerName(), transition.from, transition.to));
			else if (transition.symbol.getLength() == 0)
				this.addError(String.format("%1$s: Empty symbol on tape %2$d",
					this.getTransducerName(), transition.tape));
			else {
				this.transitions[this.transition++] = transition;
				if (transition.tape == 0)
					this.inputStateMap.putIfAbsent(transition.from, this.inputStateMap.size());
				if (transition.tape == 1 || transition.symbol.getLength() > 1) {
					Token token = new Token(transition.symbol.bytes(), -1, transducerOrdinal);
					Bytes symbol = token.getSymbol();
					if (transition.tape == 0 && token.isLiteral() && transition.symbol.getLength() > 1)
						this.tapeTokens.get(0).add(new Token(Token.reference(Token.Type.SIGNAL, symbol.bytes()),
							super.addSignal(symbol), transducerOrdinal));
					else if (transition.tape == 1)
						this.tapeTokens.get(1).add(token);
					else if (transition.tape == 2 && !token.isLiteral()) {
						if (token.isField())
							token.setOrdinal(super.addField(this.transducerOrdinal, symbol));
						else if (token.isTransducer())
							token.setOrdinal(super.addTransducer(symbol));
						else if (token.isSignal())
							token.setOrdinal(super.addSignal(symbol));
						this.tapeTokens.get(2).add(token);
					}
				}
				ArrayList<Transition> outgoing = this.stateTransitionMap.get(transition.from);
				if (outgoing == null) {
					outgoing = new ArrayList<>(16);
					this.stateTransitionMap.put(transition.from, outgoing);
				}
				outgoing.add(transition);
			}
		}
	}

	void putAutomaton() throws CharacterCodingException {
		final Integer[] inrInputStates = this.getInrStates();
		if (inrInputStates == null) {
			this.addError("Empty automaton " + this.getTransducerName());
			return;
		}

		for (final ArrayList<Transition> transitionList : this.stateTransitionMap.values())
			transitionList.trimToSize();

		final int[][][] transitionMatrix = new int[super.getSignalLimit()][inrInputStates.length][2];
		for (int i = 0; i < transitionMatrix.length; i++)
			for (int j = 0; j < inrInputStates.length; j++) {
				transitionMatrix[i][j][0] = j;
				transitionMatrix[i][j][1] = 0;
			}

		this.effectorVectorMap = new HashMap<>(1024);
		this.effectorVectorMap.put(new Ints(new int[] { 0 }), 0);
		for (final Integer inrInputState : inrInputStates) {
			for (final Transition t : this.getTransitions(inrInputState)) {
				if (t.tape == 0 && !t.isFinal) {
					try {
						final int rteState = this.inputStateMap.get(t.from);
						final int inputOrdinal = super.getInputOrdinal(t.symbol.bytes());
						final Chain chain = this.chain(t);
						if (chain != null) {
							final int[] effectVector = chain.getEffectVector();
							transitionMatrix[inputOrdinal][rteState][0] = this.inputStateMap.get(chain.getOutS());
							if (chain.isEmpty())
								transitionMatrix[inputOrdinal][rteState][1] = 1;
							else if (chain.isEffector())
								transitionMatrix[inputOrdinal][rteState][1] = effectVector[0];
							else if (chain.isParametricEffector())
								transitionMatrix[inputOrdinal][rteState][1] = Transducer.action(-1 * effectVector[0], effectVector[1]);
							else
								transitionMatrix[inputOrdinal][rteState][1] = -1 * this.effectorVectorMap.computeIfAbsent(
									new Ints(effectVector), absent -> this.effectorVectorMap.size());
						}
					} catch (CompilationException e) {
						this.addError(String.format("%1$s: %2$s",
							this.getTransducerName(), e.getMessage()));
					}
				} else if (t.tape != 0)
					this.addError(String.format(ModelCompiler.AMBIGUOUS_STATE_MESSAGE,
						this.getTransducerName(), t.from));
			}
		}

		Assembler.Assembly assembly = this.assembler.assemble(transitionMatrix, effectorVectorMap);
		this.inputEquivalenceIndex = assembly.inputEquivalents();
		this.kernelMatrix = assembly.transitions();
		this.effectorVectors = assembly.effects();
	}

	private boolean hasErrors() {
		return !this.errors.isEmpty();
	}

	void addError(final String message) {
		if (!this.errors.contains(message))
			this.errors.add(message);
	}

	private List<String> getErrors() {
		return this.errors;
	}

	private Chain chain(final Transition transition) {
		assert transition.tape != 1 && transition.tape != 2
		: "Invalid tape number for chain(InrTransition) : " + transition.toString();
		if (transition.isFinal)
			return null;
		int errorCount = this.errors.size();
		int effectorOrdinal = -1, effectorPos = 0, parameterPos = 0;
		byte[][] parameterList = new byte[16][];
		int[] effectorVector = new int[16];
		ArrayList<Transition> outT = null;
		for (
			outT = this.getTransitions(transition.to);
			outT != null && outT.size() == 1 && outT.get(0).tape > 0;
			outT = this.getTransitions(outT.get(0).to)
		) {
			final Transition t = outT.get(0);
			if (t.tape == 1) {
				if ((effectorPos + 3) >= effectorVector.length)
					effectorVector = Arrays.copyOf(effectorVector, effectorVector.length > 4 ? (effectorVector.length * 3) >> 1 : 5);
				if (effectorOrdinal >= 0 && parameterPos > 0) {
					assert((effectorPos > 0) && (effectorOrdinal == effectorVector[effectorPos - 1]));
					final Argument argument = new Argument(this.transducerOrdinal, new BytesArray(Arrays.copyOf(parameterList, parameterPos)));
					int parameterOrdinal = super.compileParameters(effectorOrdinal, argument);
					effectorVector[effectorPos] = parameterOrdinal;
					effectorVector[effectorPos - 1] *= -1;
					parameterList = new byte[8][];
					++effectorPos;
				}
				Bytes effectorSymbol = t.symbol;
				effectorOrdinal = super.getEffectorOrdinal(effectorSymbol);
				if (effectorOrdinal >= 0)
					effectorVector[effectorPos++] = effectorOrdinal;
				else
					this.addError(String.format("%1$s: Unrecognized effector '%2$s'",
						this.getTransducerName(), effectorSymbol.toString()));
				parameterPos = 0;
			} else if (t.tape == 2) {
				if (effectorOrdinal >= 0) {
					if (parameterPos >= parameterList.length)
						parameterList = Arrays.copyOf(parameterList, parameterList.length > 4 ? (parameterList.length * 3) >> 1 : 5);
					parameterList[parameterPos] = t.symbol.bytes();
					++parameterPos;
				}
			} else
				this.addError(String.format("%1$s: Invalid tape number %2$d (tape 1 or 2 expected)",
					this.getTransducerName(), t.tape));
		}
		int outS = -1;
		if (outT == null || outT.isEmpty() || outT.get(0).isFinal)
			outS = 0;
		else if (outT.get(0).tape == 0)
			outS = outT.get(0).from;
		else {
			assert outT.size() > 1;
			this.addError(String.format(AMBIGUOUS_STATE_MESSAGE,
				this.getTransducerName(), outT.get(0).from));
		}
		if (this.errors.size() == errorCount) {
			assert effectorVector.length > (effectorPos + 2);
			assert effectorPos == 0 || effectorOrdinal == effectorVector[effectorPos - 1];
			assert parameterPos == 0 || effectorPos > 0;
			if (parameterPos > 0) {
				effectorVector[effectorPos - 1] *= -1;
				effectorVector[effectorPos++] = super.compileParameters(
					effectorOrdinal, new Argument(this.transducerOrdinal,
						new BytesArray(Arrays.copyOf(parameterList, parameterPos))));
			}
			effectorVector[effectorPos++] = 0;
			assert effectorVector.length >= effectorPos;
			if (effectorVector.length > effectorPos)
				effectorVector = Arrays.copyOf(effectorVector, effectorPos);
			return new Chain(effectorVector, outS);
		}
		return null;
	}

	private Integer[] getInrStates() {
		Integer[] inrStates = new Integer[this.inputStateMap.size()];
		inrStates = this.inputStateMap.keySet().toArray(inrStates);
		Arrays.sort(inrStates);
		return inrStates;
	}

	ArrayList<Transition> getTransitions(final int inrState) {
		return this.stateTransitionMap.get(inrState);
	}

	private void setTransductor(ITransductor transductor) {
		this.transductor = transductor;
	}
}
