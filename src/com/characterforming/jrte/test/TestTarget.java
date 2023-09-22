package com.characterforming.jrte.test;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.IField;
import com.characterforming.ribose.ITarget;
import com.characterforming.ribose.base.BaseEffector;
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
		return new IEffector<?>[] {
			new IntegerValueEffector(this),
			new RealValueEffector(this),
			new StringValueEffector(this)
		};
	}

	@Override // ITarget#getName()
	public String getName() {
		return this.getClass().getSimpleName();
	}

	private class IntegerValueEffector extends BaseEffector<TestTarget> {
		IntegerValueEffector(TestTarget target) {
			super(target, "integer");
		}

		@Override
		public int invoke() throws EffectorException {
			IField field = super.output.getField();
			String string = field.toString();
			long integer = 0;
			try {
				integer = Long.parseLong(string);
			} catch (NumberFormatException e) {
				return super.output.signal(fail);
			}
			return super.output.signal(integer == field.asInteger() ? pass : fail);
		}
	}

	private class RealValueEffector extends BaseEffector<TestTarget> {
		RealValueEffector(TestTarget target) {
			super(target, "real");
		}

		@Override
		public int invoke() throws EffectorException {
			IField field = super.output.getField();
			String string = field.toString();
			double real = 0.0;
			try {
				real = Double.parseDouble(string);
			} catch (NumberFormatException e) {
				return super.output.signal(fail);			
			}
			return super.output.signal(real == field.asReal() ? pass : fail);
		}
	}

	private class StringValueEffector extends BaseEffector<TestTarget> {
		StringValueEffector(TestTarget target) {
			super(target, "string");
		}

		@Override
		public int invoke() throws EffectorException {
			IField field = super.output.getField();
			return super.output.signal(field.toString().equals(field.asString()) ? pass : fail);
		}
	}
}
