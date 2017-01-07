package com.characterforming.jrte.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectTest {
	public static final class A {
		private final ReflectTest r;

		public A(final ReflectTest r) {
			this.r = r;
		}

		public long m() {
			return this.r.m();
		}
	}

	long m() {
		return System.currentTimeMillis();
	}

	private long z() {
		final A a = new A(this);
		final long t0 = System.currentTimeMillis();
		long t = t0;
		for (int i = 0; i < 1000000; i++) {
			t = a.m();
		}
		return t - t0;
	}

	private long x() {
		final long t0 = System.currentTimeMillis();
		long t = t0;
		for (int i = 0; i < 1000000; i++) {
			t = this.m();
		}
		return t - t0;
	}

	private long y() throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		final Method w = this.getClass().getDeclaredMethod("m", (Class<?>[]) null);
		final long t0 = System.currentTimeMillis();
		long t = t0;
		for (int i = 0; i < 1000000; i++) {
			t = (Long) w.invoke(this, (Object[]) null);
		}
		return t - t0;
	}

	@org.junit.Test
	public void reflectTest() throws SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		System.out.format("%1$d %2$d %3$d", this.x(), this.z(), this.y());
	}
}
