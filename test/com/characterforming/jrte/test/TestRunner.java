/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.io.File;
import java.util.Arrays;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.Jrte;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.base.BaseEffector;
import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.engine.input.SignalInput;

public class TestRunner {

	/**
	 * @param args
	 * @throws InterruptedException On error
	 * @throws RteException On error
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
			System.out.printf("%20s: ", "RegexTest");
			regex.testRun();
			final RegexGroupTest regexGroup = new RegexGroupTest();
			System.out.printf("%20s: ", "RegexGroupTest");
			regexGroup.testRun();
		}
		final char[] achars = new char[10000000];
		Arrays.fill(achars, 'a');
		for (int i = 9; i < achars.length; i += 10) {
			achars[i] = 'b';
		}
		final Jrte jrte = new Jrte(new File(gearboxPath), "com.characterforming.jrte.base.BaseTarget");
		final ITransduction t = jrte.transduction(new BaseTarget());
		final SignalInput input = (SignalInput) jrte.input(new char[][] {achars});
		final SignalInput nilinput = (SignalInput) jrte.input(new char[][] {new String("!nil").toCharArray(), achars});
		SignalInput[] inputs = new SignalInput[] {
			nilinput, nilinput, nilinput, nilinput, nilinput, nilinput, nilinput, nilinput, nilinput
		};
		String[] tests = new String[] {
				"StackTest", "NilSpeedTest", "PasteSpeedTest", "NilPauseTest", "PastePauseTest", "PasteCutTest", "SelectPasteTest", "CounterTest", "PasteCountTest"
		};
		int n = 0;
		for (final String test : tests) {
			long t1 = 0, t2 = 0;
			System.out.format("%20s: ", test);
			for (int i = 0; i < 20; i++) {
				t.start(test);
				inputs[n].rewind();
				t.input(new IInput[] { inputs[n] });
				t1 = System.currentTimeMillis();
				while (t.status() == ITransduction.RUNNABLE) {
					t.run();
				}
				t2 = System.currentTimeMillis() - t1;
				System.out.print(String.format("%6d", t2));
			}
			System.out.println(t2 > 0 ? String.format(" : %,12d bytes/s", (long)10000000*2000 / t2) : "");
			n++;
		}
	}

}
