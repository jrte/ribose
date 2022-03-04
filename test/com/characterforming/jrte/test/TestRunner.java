/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.characterforming.jrte.ByteInput;
import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.Jrte;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.base.Bytes;

public class TestRunner {

	/**
	 * @param args
	 * @throws InterruptedException On error
	 * @throws RteException On error
	 * @throws IOException 
	 * @throws SecurityException 
	 */
	public static void main(final String[] args) throws InterruptedException, RteException, SecurityException, IOException {
		if (args.length == 0) {
			System.out.println(String.format("Usage: java -cp <classpath> %1$s <gearbox-path> [wait-ms]", TestRunner.class.getName()));
			System.exit(1);
		}
		
		Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
		final FileHandler rteHandler = new FileHandler("jrte.log");
		rteLogger.addHandler(rteHandler);
		rteHandler.setFormatter(new SimpleFormatter());
		
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
		final byte[] achars = new byte[10000000];
		for (int i = 0; i < achars.length; i++) {
			achars[i] = (byte)((i % 10 != 9) ? 'a' : 'b');
		}
		BaseTarget target = new BaseTarget();
		final Jrte jrte = new Jrte(new File(gearboxPath), target);
		final ITransduction trex = jrte.transduction(target);
		final ByteInput nilinput = (ByteInput) jrte.input(new byte[][] {Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, Base.Signal.nil.signal()), achars});
		ByteInput[] inputs = new ByteInput[] {
			nilinput, nilinput, nilinput, nilinput, nilinput, nilinput, nilinput, nilinput, nilinput
		};
		String[] tests = new String[] {
				"NilSpeedTest", "PasteSpeedTest", "NilPauseTest", "PastePauseTest", "PasteCutTest", "SelectPasteTest", "CounterTest", "PasteCountTest", "StackTest"
		};
		int n = 0;
		for (final String test : tests) {
			long t1 = 0, t2 = 0;
			System.out.format("%20s: ", test);
			for (int i = 0; i < 20; i++) {
				trex.input(new IInput[] { inputs[n] });
				trex.start(Bytes.encode(test));
				t1 = System.currentTimeMillis();
				do {
					trex.run();
				}
				while (trex.status().equals(ITransduction.Status.RUNNABLE));
				t2 = System.currentTimeMillis() - t1;
				trex.stop();
				System.out.print(String.format("%4d", t2));
			}
			System.out.println(t2 > 0 ? String.format(" : %,12d bytes/s (%,d)", (long)10000000*1000 / t2, achars.length) : "");
			n++;
		}
	}

}
