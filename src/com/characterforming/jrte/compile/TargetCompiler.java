/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.characterforming.jrte.GearboxException;
import com.characterforming.jrte.IEffector;
import com.characterforming.jrte.IParameterizedEffector;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.compile.array.BytesArray;
import com.characterforming.jrte.engine.BaseNamedValueEffector;
import com.characterforming.jrte.engine.Gearbox;
import com.characterforming.jrte.engine.Transduction;

/**
 * @author kb
 */
public class TargetCompiler {
	private final ITarget target;
	private final IEffector<?>[] effectors;
	private final HashMap<String, Integer> effectorOrdinalMap;
	private final ArrayList<HashMap<BytesArray, Integer>> effectorParameterMaps;

	/**
	 * Constructor
	 */
	TargetCompiler(final ITarget target, IEffector<?>[] effectors) {
		this.target = target;
		this.effectors = effectors;
		this.effectorOrdinalMap = new HashMap<String, Integer>(100);
		this.effectorParameterMaps = new ArrayList<HashMap<BytesArray, Integer>>(100);
		for (IEffector<?> effector : this.effectors) {
			int effectorOrdinal = this.getEffectorOrdinal(effector);
			if (effectors[effectorOrdinal] instanceof BaseNamedValueEffector) {
				getParametersIndex(effectorOrdinal, Transduction.ANONYMOUS_VALUE_RUNTIME);
			}
		}
	}

	ITarget getTarget() {
		return this.target;
	}
	
	IEffector<?> getEffector(int effectorOrdinal) {
		return this.effectors[effectorOrdinal];
	}

	int getEffectorOrdinal(final IEffector<?> effector) {
		return this.getEffectorOrdinal(effector.getName());
	}

	int getEffectorOrdinal(String effectorName) {
		Integer effectorOrdinal = this.effectorOrdinalMap.get(effectorName);
		if (effectorOrdinal == null) {
			effectorOrdinal = this.effectorOrdinalMap.size();
			this.effectorOrdinalMap.put(effectorName, effectorOrdinal);
			this.effectorParameterMaps.add(null);
		}
		return effectorOrdinal;
	}

	int getParametersIndex(final int effectorOrdinal, final byte[][] parameterBytes) {
		HashMap<BytesArray, Integer> parametersMap = this.effectorParameterMaps.get(effectorOrdinal);
		if (parametersMap == null) {
			parametersMap = new HashMap<BytesArray, Integer>(10);
			this.effectorParameterMaps.set(effectorOrdinal, parametersMap);
		}
		byte[][] p = compileParameterBytes(effectorOrdinal, parameterBytes);
		final BytesArray parameters = new BytesArray(p);
		Integer parametersIndex = parametersMap.get(parameters);
		if (parametersIndex == null) {
			parametersIndex = parametersMap.size();
			parametersMap.put(parameters, parametersIndex);
		}
		return parametersIndex;
	}

	String[] enumerateEffectorNames() {
		final String[] effectorNames = new String[this.effectorOrdinalMap.size()];
		for (final String effectorName : this.effectorOrdinalMap.keySet()) {
			effectorNames[this.effectorOrdinalMap.get(effectorName)] = effectorName;
		}
		return effectorNames;
	}

	byte[][][][] getEffectorParameters() {
		final byte[][][][] effectorParameters = new byte[this.effectorParameterMaps.size()][][][];
		for (int effectorOrdinal = 0; effectorOrdinal < this.effectorOrdinalMap.size(); effectorOrdinal++) {
			effectorParameters[effectorOrdinal] = this.getEffectorParameters(effectorOrdinal);
		}
		return effectorParameters;
	}

	int countParameterizedEffectors() {
		return this.effectorParameterMaps.size();
	}

	byte[][][] getEffectorParameters(final int effectorOrdinal) {
		final HashMap<BytesArray, Integer> parametersMap = this.effectorParameterMaps.get(effectorOrdinal);
		if (parametersMap != null) {
			final byte[][][] parametersBytesArrays = new byte[parametersMap.size()][][];
			for (final Entry<BytesArray, Integer> entry : parametersMap.entrySet()) {
				parametersBytesArrays[entry.getValue()] = entry.getKey().getBytes();
			}
			return parametersBytesArrays;
		}
		return null;
	}

	int[] save(final Gearbox gearbox, final IEffector<?>[] effectors) throws GearboxException {
		gearbox.putString(this.getTarget().getName());
		final String[] effectorNames = this.enumerateEffectorNames();
		gearbox.putStringArray(effectorNames);
		int parameterizedEffectorCount = 0;
		for (int effectorOrdinal = 0; effectorOrdinal < effectorNames.length; effectorOrdinal++) {
			if (effectors[effectorOrdinal] instanceof IParameterizedEffector<?, ?>) {
				parameterizedEffectorCount++;
			}
		}
		gearbox.putInt(parameterizedEffectorCount);
		for (int effectorOrdinal = 0; effectorOrdinal < effectorNames.length; effectorOrdinal++) {
			if (effectors[effectorOrdinal] instanceof IParameterizedEffector<?, ?>) {
				byte[][][] effectorParameters = this.getEffectorParameters(effectorOrdinal);
				if (effectorParameters == null) {
					effectorParameters = new byte[][][] {};
				}
				gearbox.putString(effectorNames[effectorOrdinal]);
				gearbox.putBytesArrays(effectorParameters);
			}
		}
		return new int[] { effectorNames.length - parameterizedEffectorCount, parameterizedEffectorCount };
	}

	Map<String, Integer> getEffectorOrdinalMap() {
		return Collections.unmodifiableMap(this.effectorOrdinalMap);
	}

	private byte[][] compileParameterBytes(int effectorOrdinal, byte[][] bytes) {
		if (this.effectors[effectorOrdinal] instanceof BaseNamedValueEffector) {
			if ((bytes.length == 1) && (bytes[0].length == 1) && (bytes[0][0]== Transduction.ANONYMOUS_VALUE_COMPILER[0][0])) {
				return Transduction.ANONYMOUS_VALUE_RUNTIME; 
			}
		}
		return bytes;
	}
}
