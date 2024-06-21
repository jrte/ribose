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

package com.characterforming.ribose.base;

import java.lang.reflect.Field;
import java.nio.charset.CharacterCodingException;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.IToken;
import com.characterforming.ribose.IOutput;

/**
 * Base class for receptor effectors, which map transducer fields to Java receiver fields
 * expressed by a subclassing effector and inject decoded transducer field values into
 * the respective receiver fields. All receptor effectors must subclass this class, which
 * implements the receptor. Its {@link #invoke(int)} method effects field conversion and
 * injection for all of the receiver fields. Default values are applied to receiver fields
 * when transducer fields are empty. The default values are extracted from the initial
 * values of the respective receiver fields as set by the subclassing effector. The
 * {@link BaseEffector#invoke()} method provides the parameterless {@code invoke()}
 * method; subclasses may override this with their own {@code invoke()} implementation.
 * <br><br>
 * A receptor effector can have only one parametric form, which lists the transducer
 * fields to be received, and can only be called from the transducer that expresses those
 * fields. Subclass receiver fields must be {@code public} and may be of any primitive
 * Java type or a {@code byte[]} or {@code char[]} array. When the receptor effector s
 * invoked it calls {@link BaseReceptorEffector#invoke(int)}, which overwrites the
 * receiver fields in the effector with the converted value from the respective transducer
 * fields, or with the default value if a transducer field is empty. The effector immediately
 * dispatches the receiver field values into the target. The receiver field values are then
 * stale and should not be used outside the scope of the effector's {@code #invoke()} method.
 * <br><br>
 * The subclass effector must call {@link setEffector(BaseReceptorEffector)} from
 * its constructor after calling the constructor for this base class.
 * <br><br>
 * Example:
 * <br><pre>
 * record Header(int version, int tapes, int transitions, int states, int symbols) {}
 *
 * final class HeaderEffector extends BaseReceptorEffector&lt;ModelCompiler&gt; {
 *   // Receiver fields with default values used if transductor fields are empty
 *   public int version = -1, tapes = -1, transitions = -1, states = -1, symbols = -1;
 *
 *   HeaderEffector(ModelCompiler compiler) throws CharacterCodingException {
 *     // compiler is the effector's target, automaton is the name of the transducer
 *     super(compiler, "header", "automaton",
 *       new String[] { "version", "tapes", "transitions", "states", "symbols" });
 *     // superclass uses reflection to identify receiver fields and default values
 *     super.setEffector(this);
 *   }
 *
 *   &commat;Override
 *   public int invoke(int parameterIndex) throws EffectorException {
 *     // superclass decodes transducer fields to Java primitives and sets receiver fields
 *     int rtx = super.invoke(parameterIndex);
 *     // subclass gathers receiver values into a Header record for the compiler
 *     super.getTarget().putHeader(new Header(
 *       this.version, this.tapes, this.transitions, this.states, this.symbols));
 *     return rtx;
 *   }
 * }</pre>
 *
 * @author Kim Briggs
 */
public abstract class BaseReceptorEffector<T extends ITarget> extends BaseParametricEffector<T, Receiver[]> {

	private BaseReceptorEffector<T> effector;
	private byte[][] receptorFieldNames;
	private final String transducerName;

	/**
	 * Constructor
	 *
	 * @param target the transductor target bound to the effector
	 * @param effectorName the effector name
	 * @param transducerName the name of the transducer the subclass is bound to
	 * @param receiverFieldNames enumerates subclass receiver fields
	 * @throws CharacterCodingException if the effector name can not be encoded
	 */
	protected BaseReceptorEffector(T target, String effectorName, String transducerName, String[] receiverFieldNames)
	throws CharacterCodingException {
		super(target, effectorName);
		this.transducerName = transducerName;
		this.receptorFieldNames = new byte[receiverFieldNames.length][];
		for (int i = 0; i < receiverFieldNames.length; i++)
			this.receptorFieldNames[i] = Codec.encode(receiverFieldNames[i]).bytes();
		this.effector = null;
	}

	/**
	 * Set a reference to the subclassing receptor effector instance. The subclass
	 * must call this from its constructor immediately after calling the its super
	 * class constructor.
	 *
	 * @param effector the {@code BaseReceptorEffector} effector subclass instance
	 */
	protected void setEffector(BaseReceptorEffector<T> effector) {
		this.effector = effector;
	}

	@Override // @see com.characterforming.ribose.IParametricEffector#invoke(int)
	public int invoke(int parameter) throws EffectorException {
		assert parameter == 0 && super.parameters.length == 1;
		assert super.output.isTransducerRunning(this.transducerName);
		for (Receiver r : super.parameters[parameter]) {
			try {
				final Field fld = r.field();
				final int idx = r.fieldIndex();
				final Object def = r.defaultValue();
				final IEffector<?> eff = this.effector;
				final IOutput out =  super.output;
				switch (r.type()) {
					case BOOLEAN:
						fld.setBoolean(eff, out.asBoolean(idx, (boolean)def));
						break;
					case BYTE:
						fld.setByte(eff, out.asByte(idx, (byte)def));
						break;
					case BYTES:
						fld.set(eff, out.asBytes(idx, (byte[])def));
						break;
					case CHAR:
						fld.setChar(eff, out.asChar(idx, (char)def));
						break;
					case CHARS:
						fld.set(eff, out.asChars(idx, (char[])def));
						break;
					case STRING:
						fld.set(eff, out.asString(idx, (String)def));
						break;
					case SHORT:
						fld.setShort(eff, out.asShort(idx, (short)def));
						break;
					case INT:
						fld.setInt(eff, out.asInteger(idx, (int)def));
						break;
					case LONG:
						fld.setLong(eff, out.asLong(idx, (long)def));
						break;
					case DOUBLE:
						fld.setDouble(eff, out.asDouble(idx, (double)def));
						break;
					case FLOAT:
						fld.setFloat(eff, out.asFloat(idx, (float)def));
						break;
					default:
						throw new EffectorException(String.format(
							"%1$s.%2$s[]: Unimplemented field type conversion type '%3$s'",
								super.target.getName(), super.getName(), r.type().name()));
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new EffectorException(String.format(
					"%1$s.%2$s[]: Field '%3$s' is inaccessible",
						super.target.getName(), super.getName(), r.field().getName()), e);
			}
		}
		return IEffector.RTX_NONE;
	}

	@Override // @see com.characterforming.ribose.IParametricEffector#allocateParameters(int)
	public Receiver[][] allocateParameters(int parameterCount) {
		return new Receiver[parameterCount][];
	}

	@Override // IParametricEffector#compileParameter(IToken[])
	public Receiver[] compileParameter(final IToken[] parameterList) throws TargetBindingException {
		if (super.parameters.length != 1)
			throw new TargetBindingException(String.format(
				"%1$s.%2$s[]: receptor effector field list cannot be overridden",
					super.target.getName(), super.getName()));
		int receiver = 0;
		Receiver[] receivers = new Receiver[parameterList.length];
		for (IToken token : parameterList)
			if (token.isField()) {
				String fieldName = "?";
				try {
					fieldName = Codec.decode(token.getSymbol().bytes());
					Field field = this.effector.getClass().getDeclaredField(fieldName);
					field.setAccessible(true);
					assert field.canAccess(this.effector);
					receivers[receiver++] = this.newFieldReceiver(field, token.getOrdinal());
				} catch (CharacterCodingException e) {
					throw new TargetBindingException(
						String.format("%1$s.%2$s[]: receptor effector can not decode field parameter %3$d",
							super.target.getName(), super.getName(), receiver));
				} catch (NoSuchFieldException e) {
					throw new TargetBindingException(String.format(
						"%1$s.%2$s[]: receptor effector does not accept field parameter '%3$s'",
							super.target.getName(), super.getName(), fieldName));
				}
			} else
				throw new TargetBindingException(String.format(
					"%1$s.%2$s[]: receptor effector accepts only field parameters",
						super.target.getName(), super.getName()));
		return receivers;
	}

	/***
	* Reset receiver fields to default (initial) values after dispatching received
	* values in {@link #invoke(int)}.
	*
	* @param parameterIndex identifies a subset of fields to reset
	* @throws EffectorException if the field is inaccessible
	*/
	protected void resetReceivers(int parameterIndex) throws EffectorException {
		for (Receiver r : this.parameters[parameterIndex])
			try {
				r.field().set(this.effector, r.defaultValue());
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new EffectorException(String.format(
					"%1$s.%2$s: unable to reset field",
						this.getName(), r.field().getName()));
			}
	}

	private Receiver newFieldReceiver(Field field, int fieldOrdinal) throws TargetBindingException {
		Class<?> type = field.getType();
		try {
			if (!type.isArray()) {
				if (boolean.class.equals(type))
					return new Receiver(Receiver.FieldType.BOOLEAN, field, field.getBoolean(this.effector), fieldOrdinal);
				if (byte.class.equals(type))
					return new Receiver(Receiver.FieldType.BYTE, field, field.getByte(this.effector), fieldOrdinal);
				if (char.class.equals(type))
					return new Receiver(Receiver.FieldType.CHAR, field, field.getChar(this.effector), fieldOrdinal);
				if (String.class.equals(type))
					return new Receiver(Receiver.FieldType.STRING, field, field.get(this.effector), fieldOrdinal);
				if (short.class.equals(type))
					return new Receiver(Receiver.FieldType.SHORT, field, field.getShort(this.effector), fieldOrdinal);
				if (int.class.equals(type))
					return new Receiver(Receiver.FieldType.INT, field, field.getInt(this.effector), fieldOrdinal);
				if (long.class.equals(type))
					return new Receiver(Receiver.FieldType.LONG, field, field.getLong(this.effector), fieldOrdinal);
				if (float.class.equals(type))
					return new Receiver(Receiver.FieldType.FLOAT, field, field.getFloat(this.effector), fieldOrdinal);
				if (double.class.equals(type))
					return new Receiver(Receiver.FieldType.DOUBLE, field, field.getDouble(this.effector), fieldOrdinal);
			} else {
				Class<?> elementType = type.getComponentType();
				if (byte.class.equals(elementType))
					return new Receiver(Receiver.FieldType.BYTES, field, field.get(this.effector), fieldOrdinal);
				if (char.class.equals(elementType))
					return new Receiver(Receiver.FieldType.CHARS, field, field.get(this.effector), fieldOrdinal);
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new TargetBindingException(String.format(
				"%1$s.%2$s[]: Field '%3$s' is inaccessible",
					super.target.getName(), super.getName(), field.getName()), e);
		}
		throw new TargetBindingException(String.format(
			"%1$s.%2$s[]: receptor effector can not accept field parameter '%3$s' (invalid type)",
				super.target.getName(), super.getName(), field.getName()));
	}
}
