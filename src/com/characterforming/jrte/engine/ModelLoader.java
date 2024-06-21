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
import java.nio.charset.CharacterCodingException;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IParametricEffector;
import com.characterforming.ribose.IModel;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.ITransduction;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.ITransductor.Metrics;
import com.characterforming.ribose.base.BaseParametricEffector;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.DomainErrorException;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.ModelException;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;

/** Model loader load model files and arbitrates concurrent trandsucer loading. */
public final class ModelLoader extends Model implements IModel {
	private final AtomicIntegerArray transducerAccessIndex;
	private final AtomicReferenceArray<Transducer> transducerObjectIndex;

	private ModelLoader(final File modelPath)
	throws ModelException {
		super(modelPath);
		try {
			super.load();
		} catch (CharacterCodingException e) {
			throw new ModelException(e);
		}
		int size = super.transducerOrdinalMap.size();
		this.transducerAccessIndex = new AtomicIntegerArray(size);
		this.transducerObjectIndex = new AtomicReferenceArray<>(size);
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
		return new ModelLoader(modelPath);
	}

	@Override // @see com.characterforming.ribose.IModel#transductor(ITarget)
	public ITransductor transductor(ITarget target)
	throws ModelException {
		if (!super.targetClass.isAssignableFrom(target.getClass()))
			throw new ModelException(
				String.format("Cannot bind instance of target class '%1$s', can only bind to model target class '%2$s'",
					target.getClass().getName(), super.targetClass.getName()));
		if (super.targetMode.isProxy())
			throw new ModelException(String.format("Cannot use model target instance as runtime target: $%s",
				super.targetClass.getName()));
		Transductor trex = new Transductor(this);
		IEffector<?>[] trexFx = trex.getEffectors();
		IEffector<?>[] targetFx = target.getEffectors();
		IEffector<?>[] boundFx = new IEffector<?>[trexFx.length + targetFx.length];
		System.arraycopy(trexFx, 0, boundFx, 0, trexFx.length);
		System.arraycopy(targetFx, 0, boundFx, trexFx.length, targetFx.length);
		if (checkTargetEffectors(trex, boundFx)) {
			assert boundFx.length == super.proxyEffectors.length;
			for (int i = 0; i < super.proxyEffectors.length; i++) {
				try {
					boundFx[i].setOutput(trex);
				} catch (EffectorException e) {
					throw new ModelException(e);
				}
				if (super.proxyEffectors[i] instanceof IParametricEffector<?, ?> proxyEffector) {
					if (boundFx[i] instanceof BaseParametricEffector<?, ?> boundEffector)
						boundEffector.setParameters(proxyEffector);
					else
						throw new ModelException(String.format(
							"Target effector '%s' implementation must extend BaseParametricEffector<?, ?>", proxyEffector.getClass().getName()));
				}
			}
			trex.setEffectors(boundFx);
			return trex;
		}
		throw new ModelException("Target effectors do not match model effectors");
	}

	@Override // @see com.characterforming.ribose.IModel#stream(Bytes, Signal, InputStream, OutputStream)
	public boolean stream(final Bytes transducer, Signal prologue, InputStream in, OutputStream out)
	throws RiboseException {
		ITarget runTarget = null;
		try {
			Class<?> targetClass = Class.forName(super.getTargetClassname());
			runTarget = (ITarget) targetClass.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
			| InvocationTargetException | NoSuchMethodException | SecurityException
			| ClassNotFoundException e) {
			String name = transducer.asString();
			super.rteLogger.log(Level.SEVERE, e, () -> String.format(
				"Failed to instantiate target '%1$s' transducer '%2$s'",
				super.getTargetClassname(), name));
			return false;
		}
		return this.stream(transducer, runTarget, prologue, in, out);
	}

	@Override // @see com.characterforming.ribose.IModel#transduce(Bytes, ITarget, Signal, InputStream, OutputStream)
	public boolean stream(Bytes transducer, ITarget target, Signal prologue, InputStream in, OutputStream out) {
		String targetClassname = super.getTargetClassname();
		try {
			Metrics metrics = new Metrics();
			byte[] bytes = new byte[Base.getInBufferSize()];
			int read = in.read(bytes);
			@SuppressWarnings("unused")
			int position = read;
			if (read > 0) {
				ITransductor trex = transductor(target);
				try (ITransduction transduction = super.transduction(trex)) {
					transduction.reset();
					trex.output(out);
					if (prologue != null)
						trex.signal(prologue);
					trex.push(bytes, read).start(transducer);
					while (trex.status().isRunnable()) {
						if (trex.run().status().isPaused()) {
							bytes = trex.recycle(bytes);
							trex.metrics(metrics);
							assert bytes != null;
							read = in.read(bytes);
							if (read > 0) {
								trex.push(bytes, read);
								position += read;
							} else
								break;
						}
					}
					if (trex.status().isPaused())
						trex.signal(Signal.EOS).run();
					assert !trex.status().isRunnable();
				} catch (EffectorException | DomainErrorException e) {
					super.rteLogger.log(Level.SEVERE, e, () -> String.format(
						"Transducer '%1$s' failed for target '%2$s'",
						transducer.toString(), targetClassname));
				}
				assert trex.status().isStopped();
			}
		} catch (ModelException | IOException e) {
			super.rteLogger.log(Level.SEVERE, e, () -> String.format(
				"Transducer '%1$s' failed for target '%2$s'",
				transducer.toString(), targetClassname));
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
			final Transducer newt = super.readTransducer(transducerOrdinal);
			final Transducer oldt = this.transducerObjectIndex.compareAndExchange(transducerOrdinal, null, newt);
			final int access = this.transducerAccessIndex.compareAndExchange(transducerOrdinal, 1, 2);
			assert null == oldt && 1 == access;
		} else
			while (1 == this.transducerAccessIndex.compareAndExchange(transducerOrdinal, 1, 1))
				Thread.onSpinWait();
		final Transducer t = this.transducerObjectIndex.get(transducerOrdinal);
		assert t != null && 2 == this.transducerAccessIndex.get(transducerOrdinal);
		return t;
	}

	@Override
	public void decompile(final String transducerName)
	throws ModelException, CharacterCodingException {
		int transducerOrdinal = super.getTransducerOrdinal(Codec.encode(transducerName));
		Transducer trex = this.loadTransducer(transducerOrdinal);
		int[] effectorVectors = trex.effectorVector();
		int[] inputEquivalenceIndex = trex.inputFilter();
		long[] transitionMatrix = trex.transitionMatrix();
		int inputEquivalentCount = trex.getInputEquivalentsCount();
		Set<Map.Entry<Bytes, Integer>> effectorOrdinalMap = super.getEffectorOrdinalMap().entrySet();
		String[] effectorNames = new String[effectorOrdinalMap.size()];
		for (Map.Entry<Bytes, Integer> entry : effectorOrdinalMap)
			effectorNames[entry.getValue()] = Codec.decode(
				entry.getKey().bytes(), entry.getKey().getLength());
		System.out.printf("%s%n%nFields%n%n", transducerName);
		Map<Bytes, Integer> fieldMap = this.transducerFieldMaps.get(transducerOrdinal);
		Bytes[] fields = new Bytes[fieldMap.size()];
		for (Entry<Bytes, Integer> e : fieldMap.entrySet())
			fields[e.getValue()] = e.getKey();
		for (int i = 0; i < fields.length; i++)
			System.out.printf("%4d: %s%n", i, fields[i]);
		System.out.printf("%nInput equivalents (equivalent: input...)%n%n", transducerName);
		for (int i = 0; i < inputEquivalentCount; i++) {
			int startToken = -1;
			System.out.printf("%4d:", i);
			for (int j = 0; j < inputEquivalenceIndex.length; j++) {
				if (inputEquivalenceIndex[j] != i) {
					if (startToken >= 0) {
						if (startToken < (j - 1)) {
							this.printStart(startToken);
							this.printEnd(j - 1);
						} else
							this.printStart(startToken);
						startToken = -1;
					}
				} else if (startToken < 0)
					startToken = j;
			}
			if (startToken >= 0) {
				int endToken = inputEquivalenceIndex.length - 1;
				this.printStart(startToken);
				if (startToken < endToken)
					this.printEnd(endToken);
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
					if (super.proxyEffectors[effectorOrdinal] instanceof BaseParametricEffector<?, ?> effector) {
						int parameterOrdinal = Transducer.parameter(effect);
						System.out.printf(" %s[", effectorNames[effectorOrdinal]);
						System.out.printf(" %s ]", effector.showParameterTokens(parameterOrdinal));
					}
				} else if (effect >= 0) {
					if (effect > 1)
						System.out.printf(" %s", effectorNames[effect]);
				} else {
					int index = (-1 * effect);
					while (effectorVectors[index] != 0) {
						if (effectorVectors[index] > 0)
							System.out.printf(" %s", effectorNames[effectorVectors[index++]]);
						else {
							int effectorOrdinal = -1 * effectorVectors[index++];
							if (super.proxyEffectors[effectorOrdinal] instanceof BaseParametricEffector<?,?> effector) {
								int parameterOrdinal = effectorVectors[index++];
								System.out.printf(" %s[", effectorNames[effectorOrdinal]);
								System.out.printf(" %s ]", effector.showParameterTokens(parameterOrdinal));
							}
						}
					}
				}
				System.out.printf("%n");
			}
		}
	}

	private void printStart(int startByte) {
		if (startByte > 32 && startByte < 127)
			System.out.printf(" %c", (char) startByte);
		else
			System.out.printf(" #%x", startByte);
	}

	private void printEnd(int endByte) {
		if (endByte > 32 && endByte < 127)
			System.out.printf("-%c", (char) endByte);
		else
			System.out.printf("-#%x", endByte);
	}
}
