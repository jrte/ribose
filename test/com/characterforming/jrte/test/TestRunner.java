/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.io.File;
import java.util.Arrays;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.Jrte;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.engine.input.SignalInput;

public class TestRunner {

	/**
	 * @param args
	 * @throws InterruptedException
	 * @throws RteException
	 */
	public static void main(final String[] args) throws InterruptedException, RteException {
		if (args.length == 0) {
			System.out.println(String.format("Usage: java -cp <classpath> %1$s <gearbox-path> [wait-ms]", TestRunner.class.getName()));
			System.exit(1);
		}
		final String gearboxPath = args[0];
		final long arg = args.length > 1 ? Long.parseLong(args[1]) : 0;
		Thread.sleep(arg);
		if (arg == 1) {
			final RegexTest regex = new RegexTest();
			System.out.print("RegexTest       : ");
			regex.testRun();
			final RegexGroupTest regexGroup = new RegexGroupTest();
			System.out.print("RegexGroupTest  : ");
			regexGroup.testRun();
		}
		final char[] achars = new char[10000000];
		Arrays.fill(achars, 'a');
		for (int i = 9; i < achars.length; i += 10) {
			achars[i] = 'b';
		}
		final Jrte jrte = new Jrte(new File(gearboxPath), "com.characterforming.jrte.base.BaseTarget");
		final ITransduction t = jrte.transduction(new BaseTarget());
		final SignalInput input = (SignalInput) jrte.input(new char[][] {
				achars
		});
		final SignalInput nilinput = (SignalInput) jrte.input(new char[][] {
				new char[] { '!', 'n', 'i', 'l' }, achars
		});
		SignalInput[] inputs = new SignalInput[] {
				input, input, input, nilinput, nilinput, nilinput, nilinput
		};
		String[] tests = new String[] {
				"NilSpeedTest", "PasteSpeedTest", "PasteCutTest", "SelectPasteTest", "CounterTest", "PasteCountTest", "StackTest"
		};
		int n = 0;
		for (final String test : tests) {
			long ts = 0;
			System.out.format("%1$-16s: ", test);
			for (int i = 0; i < 10; i++) {
				final long t0 = System.currentTimeMillis();
				t.start(test);
				t.input(new IInput[] { inputs[n].rewind() });
				final long t1 = System.currentTimeMillis();
				t.run();
				final long t2 = System.currentTimeMillis() - t1;
				System.out.print(String.format("%1$6d", t2));
				ts += (t1 - t0);
			}
			System.out.println(String.format(" : %1$6d", ts));
			n++;
		}
		n = 0;
		inputs = new SignalInput[] { input, input };
		tests = new String[] { "NilPauseTest", "PastePauseTest" };
		for (final String test : tests) {
			long ts = 0;
			System.out.format("%1$-16s: ", test);
			for (int i = 0; i < 10; i++) {
				final long t0 = System.currentTimeMillis();
				t.start(test);
				t.input(new IInput[] { inputs[n].rewind() });
				final long t1 = System.currentTimeMillis();
				while (t.status() == ITransduction.RUNNABLE) {
					t.run();
				}
				final long t2 = System.currentTimeMillis() - t1;
				System.out.print(String.format("%1$6d", t2));
				ts += (t1 - t0);
			}
			System.out.println(String.format(" : %1$6d", ts));
			n++;
		}
	}

}
