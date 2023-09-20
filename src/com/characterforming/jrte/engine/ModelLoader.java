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
 * and GNU Lesser Public License along with super program.	See
 * LICENSE-gpl-3.0. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IParameterizedEffector;
import com.characterforming.ribose.IModel;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.IToken;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.ITransductor.Metrics;
import com.characterforming.ribose.base.BaseParameterizedEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

/** Model loader load model files and arbitrates concurrent trandsucer loading. */
public final class ModelLoader extends Model implements IModel {
	private AtomicIntegerArray transducerAccessIndex;
	private AtomicReferenceArray<Transducer> transducerObjectIndex;

	private ModelLoader(final File modelPath)
	throws ModelException {
		super(modelPath);
		this.transducerAccessIndex = null;
		this.transducerObjectIndex = null;
	}
	
	/**
	 * Bind target instance to runtime model.
	 *
	 * @param modelFile the model file
	 * @return the loaded model instance
	 * @throws ModelException if things don't work out
	 */
	public static ModelLoader loadModel(File modelPath)
	throws ModelException {
		ModelLoader model = new ModelLoader(modelPath);
		model.seek(0);
		long indexPosition = model.readLong();
		final String loadedVersion = model.readString();
		if (!loadedVersion.equals(Base.RTE_VERSION)) {
			throw new ModelException(
				String.format("Current model version '%1$s' does not match version string '%2$s' from model file '%3$s'",
					Base.RTE_VERSION, loadedVersion, model.modelPath.getPath()));
		}
		final String targetClassname = model.readString();
		if (!targetClassname.equals(model.targetClass.getName())) {
			throw new ModelException(
				String.format("Can't load model for target class '%1$s'; '%2$s' is target class for model file '%3$s'",
					model.targetName, targetClassname, model.modelPath.getPath()));
		}
		model.modelVersion = loadedVersion;
		model.seek(indexPosition);
		model.signalOrdinalMap = model.readOrdinalMap(Base.RTE_SIGNAL_BASE);
		model.fieldOrdinalMap = model.readOrdinalMap(0);
		model.effectorOrdinalMap = model.readOrdinalMap(0);
		model.transducerOrdinalMap = model.readOrdinalMap(0);
		int transducerCount = model.transducerOrdinalMap.size();
		model.transducerNameIndex = new Bytes[transducerCount];
		model.transducerAccessIndex = new AtomicIntegerArray(transducerCount);
		model.transducerObjectIndex = new AtomicReferenceArray<>(transducerCount);
		model.transducerOffsetIndex = new long[transducerCount];
		for (int transducerOrdinal = 0; transducerOrdinal < transducerCount; transducerOrdinal++) {
			model.transducerNameIndex[transducerOrdinal] = new Bytes(model.readBytes());
			model.transducerOffsetIndex[transducerOrdinal] = model.readLong();
			assert model.transducerOrdinalMap.get(model.transducerNameIndex[transducerOrdinal]) == transducerOrdinal;
		}
		model.initializeProxyEffectors();
		List<String> errors = new ArrayList<>(32);
		for (int effectorOrdinal = 0; effectorOrdinal < model.effectorOrdinalMap.size(); effectorOrdinal++) {
			byte[][][] effectorParameters = model.readBytesArrays();
			IToken[][] parameterTokens = new IToken[effectorParameters.length][];
			for (int i = 0; i < effectorParameters.length; i++) {
				parameterTokens[i] = Token.getParameterTokens(model, effectorParameters[i]);
			}
			if (model.proxyEffectors[effectorOrdinal] instanceof BaseParameterizedEffector<?, ?> effector) {
				effector.compileParameters(parameterTokens, errors);
			}
			if (model.targetMode.isLive()) {
				model.proxyEffectors[effectorOrdinal].passivate();
			}
		}
		if (!errors.isEmpty()) {
			for (String error : errors) {
				model.rtcLogger.log(Level.SEVERE, error);
			}
			throw new ModelException(String.format(
				"Failed to load '%1$s', effector parameter precompilation failed.",
					modelPath.getAbsolutePath()));
		}
		assert model.targetMode.isLive();
		return model;
	}

	@Override // @see com.characterforming.ribose.IRiboseModel#transductor(ITarget)
	public ITransductor transductor(ITarget target)
	throws ModelException {
		return this.bindTransductor(target);
	}

	@Override // @see com.characterforming.ribose.IRiboseModel#stream(Bytes, Signal, InputStream, OutputStream)
	public boolean stream(final Bytes transducer, Signal prologue, InputStream in, OutputStream out)
	throws RiboseException {
		ITarget runTarget = null;
		try {
			runTarget = (ITarget) Class.forName(this.getTargetClassname()).getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
			| InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
			this.rteLogger.log(Level.SEVERE, e, () -> String.format("Failed to instantiate target '%1$s' transducer '%2$s'",
				this.getTargetClassname(), transducer.toString(super.getDecoder())));
			return false;
		}
		return this.stream(transducer, runTarget, prologue, in, out);
	}

	@Override // @see com.characterforming.ribose.IRiboseModel#transduce(Bytes, ITarget, Signal, InputStream, OutputStream)
	public boolean stream(Bytes transducer, ITarget target, Signal prologue, InputStream in, OutputStream out) {
		String targetClassname = this.getTargetClassname();
		try {
			Metrics metrics = new Metrics();
			byte[] bytes = new byte[Base.getInBufferSize()];
			int read = in.read(bytes);
			@SuppressWarnings("unused")
			int position = read;
			if (read > 0) {
				ITransductor trex = transductor(target);
				trex.output(out);
				if (prologue != null)
					trex.signal(prologue);
				if (trex.push(bytes, read).start(transducer).status().isRunnable()) {
					do {
						if (trex.run().status().isPaused()) {
							bytes = trex.recycle(bytes);
							trex.metrics(metrics);
							assert bytes != null;
							read = in.read(bytes);
							if (read > 0) {
								trex.push(bytes, read);
								position += read;
							} else {
								break;
							}
						}
					} while (trex.status().isRunnable());
					if (trex.status().isPaused()) {
						trex.signal(Signal.EOS).run();
					}
					assert !trex.status().isRunnable();
					trex.stop();
					assert trex.status().isStopped();
				}
			}
		} catch (RiboseException | ModelException | EffectorException | DomainErrorException | IOException e) {
			this.rteLogger.log(Level.SEVERE, String.format("Transducer '%1$s' failed for target '%2$s'",
				transducer.toString(), targetClassname), e);
			return false;
		}
		return true;
	}

	@Override // Model#save()
	public void close() {
		super.close();
	}

	Transducer loadTransducer(final Integer transducerOrdinal)
	throws ModelException {
		if (0 > transducerOrdinal || transducerOrdinal >= this.transducerAccessIndex.length()) {
			throw new ModelException(String.format("RuntimeModel.loadTransducer(ordinal:%d) ordinal out of range [0,%d)",
				transducerOrdinal, this.transducerObjectIndex.length()));
		}
		if (0 == this.transducerAccessIndex.compareAndExchange(transducerOrdinal, 0, 1)) {
			try {
				super.io.seek(transducerOffsetIndex[transducerOrdinal]);
			} catch (final IOException e) {
				throw new ModelException(
					String.format("RuntimeModel.loadTransducer(ordinal:%d) IOException after seek to %d",
						transducerOrdinal, transducerOffsetIndex[transducerOrdinal]), e);
			}
			final String name = super.readString();
			final String target = super.readString();
			final int[] inputs = super.readIntArray();
			final long[] transitions = super.readTransitionMatrix();
			final int[] effects = super.readIntArray();
			final Transducer newt = new Transducer(name, target, inputs, transitions, effects);
			final Transducer oldt = this.transducerObjectIndex.compareAndExchange(transducerOrdinal, null, newt);
			final int access = this.transducerAccessIndex.compareAndExchange(transducerOrdinal, 1, 2);
			assert null == oldt && 1 == access;
		} else {
			while (1 == this.transducerAccessIndex.compareAndExchange(transducerOrdinal, 1, 1))
				Thread.onSpinWait();
		}
		final Transducer t = this.transducerObjectIndex.get(transducerOrdinal);
		assert t != null && 2 == this.transducerAccessIndex.get(transducerOrdinal);
		return t;
	}

	private Transductor bindTransductor(ITarget target)
	throws ModelException {
		if (!this.targetClass.isAssignableFrom(target.getClass())) {
			throw new ModelException(
				String.format("Cannot bind instance of target class '%1$s', can only bind to model target class '%2$s'",
					target.getClass().getName(), this.targetClass.getName()));
		}
		if (super.targetMode.isProxy()) {
			throw new ModelException(String.format("Cannot use model target instance as runtime target: $%s",
				this.targetClass.getName()));
		}
		Transductor trex = new Transductor(this);
		IEffector<?>[] trexFx = trex.getEffectors();
		IEffector<?>[] targetFx = target.getEffectors();
		IEffector<?>[] boundFx = new IEffector<?>[trexFx.length + targetFx.length];
		System.arraycopy(trexFx, 0, boundFx, 0, trexFx.length);
		System.arraycopy(targetFx, 0, boundFx, trexFx.length, targetFx.length);
		if (!checkTargetEffectors(trex, boundFx)) {
			throw new ModelException("Target effectors do not match model effectors");
		}
		trex.setFieldOrdinalMap(this.fieldOrdinalMap);
		this.bindParameters(trex, boundFx);
		return trex;
	}

	private IEffector<?>[] bindParameters(Transductor trex, IEffector<?>[] runtimeEffectors)
	throws TargetBindingException {
		assert runtimeEffectors.length == this.proxyEffectors.length;
		for (int i = 0; i < this.proxyEffectors.length; i++) {
			runtimeEffectors[i].setOutput(trex);
			if (this.proxyEffectors[i] instanceof IParameterizedEffector<?, ?> proxyEffector
			&& runtimeEffectors[i] instanceof BaseParameterizedEffector<?, ?> boundEffector) {
				boundEffector.setParameters(proxyEffector);
			}
		}
		trex.setEffectors(runtimeEffectors);
		return runtimeEffectors;
	}

	@Override
	public void decompile(final String transducerName) throws ModelException {
		Transducer trex = this.loadTransducer(this.getTransducerOrdinal(Bytes.encode(encoder.reset(), transducerName)));
		int[] effectorVectors = trex.getEffectorVector();
		int[] inputEquivalenceIndex = trex.getInputFilter();
		long[] transitionMatrix = trex.getTransitionMatrix();
		int inputEquivalentCount = trex.getInputEquivalentsCount();
		Set<Map.Entry<Bytes, Integer>> effectorOrdinalMap = this.getEffectorOrdinalMap().entrySet();
		String[] effectorNames = new String[effectorOrdinalMap.size()];
		for (Map.Entry<Bytes, Integer> entry : effectorOrdinalMap) {
			effectorNames[entry.getValue()] = Bytes
				.decode(this.decoder.reset(), entry.getKey().bytes(), entry.getKey().getLength()).toString();
		}
		System.out.printf("%s%n%nInput equivalents (equivalent: input...)%n%n", transducerName);
		for (int i = 0; i < inputEquivalentCount; i++) {
			int startToken = -1;
			System.out.printf("%4d:", i);
			for (int j = 0; j < inputEquivalenceIndex.length; j++) {
				if (inputEquivalenceIndex[j] != i) {
					if (startToken >= 0) {
						if (startToken < (j - 2)) {
							this.printStart(startToken);
							this.printEnd(j - 1);
						} else {
							this.printStart(startToken);
						}
					}
					startToken = -1;
				} else if (startToken < 0) {
					startToken = j;
				}
			}
			if (startToken >= 0) {
				int endToken = inputEquivalenceIndex.length - 1;
				this.printStart(startToken);
				if (startToken < endToken) {
					this.printEnd(endToken);
				}
			}
			System.out.printf("%n");
		}
		System.out.printf("%nState transitions (from equivalent -> to effect...)%n%n");
		for (int i = 0; i < transitionMatrix.length; i++) {
			int from = i / inputEquivalentCount;
			int equivalent = i % inputEquivalentCount;
			int to = Transducer.state(transitionMatrix[i]) / inputEquivalentCount;
			int effect = Transducer.action(transitionMatrix[i]);
			assert (effect != 0) || (to == from);
			if ((to != from) || (effect != 0)) {
				System.out.printf("%1$d %2$d -> %3$d", from, equivalent, to);
				if (effect >= 0x10000) {
					int effectorOrdinal = Transducer.effector(effect);
					if (this.proxyEffectors[effectorOrdinal] instanceof BaseParameterizedEffector<?, ?> effector) {
						int parameterOrdinal = Transducer.parameter(effect);
						System.out.printf(" %s[", effectorNames[effectorOrdinal]);
						System.out.printf(" %s ]", effector.showParameterTokens(this.getDecoder(), parameterOrdinal));
					}
				} else if (effect >= 0) {
					if (effect > 1) {
						System.out.printf(" %s", effectorNames[effect]);
					}
				} else {
					int index = (-1 * effect);
					while (effectorVectors[index] != 0) {
						if (effectorVectors[index] > 0) {
							System.out.printf(" %s", effectorNames[effectorVectors[index++]]);
						} else {
							int effectorOrdinal = -1 * effectorVectors[index++];
							if (this.proxyEffectors[effectorOrdinal] instanceof BaseParameterizedEffector<?,?> effector) {
								int parameterOrdinal = Transducer.parameter(effectorVectors[index++]);
								System.out.printf(" %s[", effectorNames[effectorOrdinal]);
								System.out.printf(" %s ]", effector.showParameterTokens(this.getDecoder(), parameterOrdinal));
							}
						}
					}
				}
				System.out.printf("%n");
			}
		}
	}

	private void printStart(int startByte) {
		if (startByte > 32 && startByte < 127) {
			System.out.printf(" %c", (char) startByte);
		} else {
			System.out.printf(" #%x", startByte);
		}
	}

	private void printEnd(int endByte) {
		if (endByte > 32 && endByte < 127) {
			System.out.printf("-%c", (char) endByte);
		} else {
			System.out.printf("-#%x", endByte);
		}
	}
}
