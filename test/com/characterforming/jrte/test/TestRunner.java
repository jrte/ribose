/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.test;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.ITransductor.Status;
import com.characterforming.ribose.Ribose;
import com.characterforming.ribose.TRun;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.RiboseException;

public class TestRunner {

	/**
	 * @param args
	 * @throws InterruptedException on error
	 * @throws RiboseException on error
	 * @throws IOException on error
	 * @throws SecurityException on error
	 */
	public static void main(final String[] args) throws InterruptedException, RiboseException, SecurityException, IOException {
		if (args.length == 0) {
			System.out.println(String.format("Usage: java -cp <classpath> %1$s <model-path> [wait-ms]", TestRunner.class.getName()));
			System.exit(1);
		}
		
		Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
		final FileHandler rteHandler = new FileHandler("TestRunner.log");
		rteLogger.addHandler(rteHandler);
		rteHandler.setFormatter(new SimpleFormatter());
		
		final String modelPath = args[0];
		final long arg = args.length > 1 ? Long.parseLong(args[1]) : 0;
		final char[] achars = new char[10000000];
		for (int i = 0; i < achars.length; i++) {
			achars[i] = (i % 10 != 9) ? 'a' : 'b';
		}
		byte[] abytes = new byte[10000000];
		for (int i = 0; i < abytes.length; i++) {
			abytes[i] = (byte)((i % 10 != 9) ? 'a' : 'b');
		}

		Thread.sleep(arg);
		if (arg == 1) {
			final RegexTest regex = new RegexTest(achars);
			System.out.printf("%20s: ", "RegexTest");
			regex.testRun();
			final RegexGroupTest regexGroup = new RegexGroupTest(achars);
			System.out.printf("%20s: ", "RegexGroupTest");
			regexGroup.testRun();
		}
		
		String[] tests = new String[] {
			"SelectPasteTest", "PasteSpeedTest", "NilPauseTest", "PastePauseTest", "PasteCutTest", "StackTest", "PasteCountTest", "CounterTest", "NilSpeedTest"
		};
		final TRun target = new TRun();
		try (final IRuntime ribose = Ribose.loadRiboseModel(new File(modelPath), target)) {
			final ITransductor trex = ribose.newTransductor(target);
			for (final String test : tests) {
				long t0 = 0, t1 = 0, t2 = 0;
				System.out.format("%20s: ", test);
				for (int i = 0; i < 20; i++) {
					assert trex.status() == Status.STOPPED;
					trex.input(abytes, abytes.length);
					trex.signal(Signal.nil);
					Status status = trex.start(Bytes.encode(test));
					t0 = System.currentTimeMillis();
					while (status == Status.RUNNABLE) {
						status = trex.run();
					}
					assert status != Status.NULL;
					trex.stop();
					t1 = System.currentTimeMillis() - t0;
					assert trex.status() == Status.STOPPED;
					System.out.print(String.format("%4d", t1));
					if (i >= 10) {
						t2 += t1;
					}
				}
				double mbps = (t2 > 0) ? ((double)(10000000) / (double)(t2*1024*1024)) * (10*1000) : -1;
				System.out.println(String.format(" : %7.3f mb/s (bytes)", mbps));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
