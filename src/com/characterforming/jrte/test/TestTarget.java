package com.characterforming.jrte.test;

import java.nio.charset.CharacterCodingException;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IOutput;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.Signal;
import com.characterforming.ribose.base.TargetBindingException;

public class TestTarget implements ITarget {
	private static final int fail = Signal.NUL.signal();
	private static final int pass = Signal.NIL.signal();

	/**
	 * Constructor
	 */
	public TestTarget() {
		super();
	}

	@Override
	public IEffector<?>[] getEffectors() throws TargetBindingException {
		try {
			return new IEffector<?>[] {
				new IntegerValueEffector(this),
				new RealValueEffector(this),
				new StringValueEffector(this)
			};
		} catch (CharacterCodingException e) {
			throw new TargetBindingException(e);
		}
	}

	@Override // ITarget#getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

	private class IntegerValueEffector extends BaseEffector<TestTarget> {
		IntegerValueEffector(TestTarget target) throws CharacterCodingException {
			super(target, "integer");
		}

		@Override
		public int invoke() throws EffectorException {
			long integer = 0;
			try {
				integer = Long.parseLong(Codec.decode(super.output.asBytes(0)));
			} catch (NumberFormatException | CharacterCodingException e) {
				return IEffector.signal(fail);
			}
			return IEffector.signal(integer == super.output.asInteger(0) ? pass : fail);
		}
	}

	private class RealValueEffector extends BaseEffector<TestTarget> {
		RealValueEffector(TestTarget target) throws CharacterCodingException {
			super(target, "real");
		}

		@Override
		public int invoke() throws EffectorException {
			double real = 0.0;
			try {
				real = Double.parseDouble(Codec.decode(super.output.asBytes(0)));
			} catch (NumberFormatException | CharacterCodingException e) {
				return IEffector.signal(fail);
			}
			return IEffector.signal(real == super.output.asReal(0) ? pass : fail);
		}
	}

	private class StringValueEffector extends BaseEffector<TestTarget> {
		StringValueEffector(TestTarget target) throws CharacterCodingException {
			super(target, "string");
		}

		@Override
		public void setOutput(IOutput output) throws EffectorException {
			super.setOutput(output);
		}

		@Override
		public int invoke() throws EffectorException {
			byte[] bytes = super.output.asBytes(0);
			String field;
			try {
				field = Codec.decode(bytes);
			} catch (CharacterCodingException e) {
				field = "";
			}
			return IEffector.signal(field.equals("pi") || field.equals("-pi") ? pass : fail);
		}
	}
}
