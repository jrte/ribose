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
 * LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.CharsetEncoder;
import java.util.logging.FileHandler;
import java.util.logging.Level;
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
		
		final Logger rteLogger = Logger.getLogger("TestRunner");
		final FileHandler rteHandler = new FileHandler("TestRunner.log", true);
		rteHandler.setFormatter(new SimpleFormatter());
		rteLogger.addHandler(rteHandler);
		rteLogger.setLevel(Level.WARNING);
	
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
		final TRun proxyTarget = new TRun();
		final CharsetEncoder encoder = Base.getRuntimeCharset().newEncoder();
		try (final IRuntime ribose = Ribose.loadRiboseModel(new File(modelPath), proxyTarget)) {
			final TRun runTarget = new TRun();
			final ITransductor trex = ribose.newTransductor(runTarget);
			for (final String test : tests) {
				long t0 = 0, t1 = 0, t2 = 0;
				System.out.format("%20s: ", test);
				Bytes transducer = Bytes.encode(encoder, test);
				for (int i = 0; i < 20; i++) {
					assert trex.status() == Status.STOPPED;
					if (trex.push(abytes, abytes.length).status().isWaiting()
					&& trex.push(Signal.nil).status().isWaiting()
					&& (trex.start(transducer).status().isRunnable())) {
						t0 = System.currentTimeMillis();
						do ; while (trex.run().status().isRunnable());
						t1 = System.currentTimeMillis() - t0;
						if (i >= 10) {
							t2 += t1;
						}
						System.out.print(String.format("%4d", t1));
						assert !trex.status().hasInput();
						trex.stop();
					}
				}
				double mbps = (t2 > 0) ? ((double)(10000000) / (double)(t2*1024*1024)) * (10*1000) : -1;
				System.out.println(String.format(" : %7.3f mb/s (bytes)", mbps));
			}
		} catch (Exception e) {
			System.out.println("Runtime exception thrown.");
			rteLogger.log(Level.SEVERE, "Runtime failed, exception thrown.", e);
			System.exit(1);
		} catch (AssertionError e) {
			System.out.println("Runtime assertion failed.");
			rteLogger.log(Level.SEVERE, "Runtime assertion failed", e);
			System.exit(1);
		}
	}

}
