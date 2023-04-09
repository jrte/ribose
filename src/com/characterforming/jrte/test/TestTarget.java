package com.characterforming.jrte.test;

import com.characterforming.ribose.IEffector;
import com.characterforming.ribose.INamedValue;
import com.characterforming.ribose.base.BaseEffector;
import com.characterforming.ribose.base.BaseTarget;
import com.characterforming.ribose.base.EffectorException;
import com.characterforming.ribose.base.TargetBindingException;

public class TestTarget extends BaseTarget {
	private static final int fail = 266;
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
			INamedValue value = super.output.getSelectedValue();
			String string = value.toString();
			long integer = 0;
			try {
				integer = Long.parseLong(string);
			} catch (NumberFormatException e) {
				return IEffector.rtxSignal(fail);
			}
			return integer == value.asInteger() ? IEffector.RTX_NONE : IEffector.rtxSignal(fail);
		}
	}

	private class RealValueEffector extends BaseEffector<TestTarget> {
		RealValueEffector(TestTarget target) {
			super(target, "real");
		}

		@Override
		public int invoke() throws EffectorException {
			INamedValue value = super.output.getSelectedValue();
			String string = value.toString();
			double real = 0.0;
			try {
				real = Double.parseDouble(string);
			} catch (NumberFormatException e) {
				return IEffector.rtxSignal(fail);			
			}
			return real == value.asReal() ? IEffector.RTX_NONE : IEffector.rtxSignal(fail);
		}
	}

	private class StringValueEffector extends BaseEffector<TestTarget> {
		StringValueEffector(TestTarget target) {
			super(target, "string");
		}

		@Override
		public int invoke() throws EffectorException {
			INamedValue value = super.output.getSelectedValue();
			return value.toString().equals(value.asString()) ? IEffector.RTX_NONE : IEffector.rtxSignal(fail);
		}
	}
}