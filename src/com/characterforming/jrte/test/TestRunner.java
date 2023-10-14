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
package com.characterforming.jrte.test;

import java.io.File;
import java.nio.CharBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.characterforming.jrte.engine.Base;
import com.characterforming.ribose.IModel;
import com.characterforming.ribose.ITransduction;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.ITransductor.Status;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.Signal;

public class TestRunner {

	/**
	 * Shell interface.
	 * 
	 * @param args arguments from the shell
	 */
	public static void main(final String[] args) {
		if (args.length == 0) {
			System.out.println(String.format("Usage: java -cp <classpath> %1$s <model-path> [wait-ms]", TestRunner.class.getName()));
			System.exit(1);
		}

		Base.startLogging();
		final Logger rteLogger = Base.getRuntimeLogger();
		final String modelPath = args[0];
		final char[] achars = new char[10000000];
		for (int i = 0; i < achars.length; i++) {
			achars[i] = ((i % 10 != 9) ? 'a' : (((i+1) % 100 != 0) ? 'b' : 'c'));
		}
		byte[] abytes = new byte[10000000];
		for (int i = 0; i < abytes.length; i++) {
			abytes[i] = (byte)((i % 10 != 9) ? 'a' : 'b');
		}

		int exitCode = 1;
		String[] tests = new String[] {
			 "NilSpeedTest", "PasteSpeedTest", "PasteCutTest", "SelectPasteTest", "PasteCountTest", "CounterTest", "NilPauseTest", "PastePauseTest", "StackTest"
		};
		try (final IModel ribose = IModel.loadRiboseModel(new File(modelPath))) {
			final ITransductor trex = ribose.transductor(new TestTarget());
			for (final String test : tests) {
				long t0 = 0, t1 = 0, t2 = 0;
				System.out.format("%20s: ", test);
				Bytes transducer = Codec.encode(test);
				for (int i = 0; i < 20; i++) {
					try (ITransduction transduction = ribose.transduction(trex)) {
						transduction.reset();
						assert trex.status() == Status.STOPPED;
						trex.signal(Signal.NIL).push(abytes, abytes.length);
						if (trex.start(transducer).status().isRunnable()) {
							t0 = System.nanoTime();
							do {
								trex.run();
							} while (trex.status().isRunnable());
							if (trex.status().isPaused()) {
								trex.signal(Signal.EOS).run();
							}
							t1 = System.nanoTime() - t0;
							if (i >= 10) {
								t2 += t1;
							}
						}
						assert !trex.status().isRunnable();
					}
					assert trex.status().isStopped();
				}
				double mbps = (t2 > 0) ? (double)(100000000l*1000000000l) / (double)(t2*1024*1024) : -1;
				System.out.println(String.format("%8.3f mb/s (bytes)", mbps));
			}
			exitCode = 0;
		} catch (Exception e) {
			System.out.println("Runtime exception thrown.");
			rteLogger.log(Level.SEVERE, "Runtime failed, exception thrown.", e);
		} finally {
			Base.endLogging();
			System.exit(exitCode);
		}
	}

	static void regexRun(char[] input, String pattern) {
		int captured = 0;
		long t0 = 0, t1 = 0, t2 = 0;
		Pattern regex = Pattern.compile(pattern, Pattern.MULTILINE);
		for (int i = 0; i < 20; i++) {
			Matcher matcher = regex.matcher(CharBuffer.wrap(input));
			t0 = System.nanoTime();
			while (matcher.find()) {
				if (matcher.group(0) != null) {
					captured += matcher.group(0).length();
				}
			}
			t1 = System.nanoTime() - t0;
			System.out.print(String.format("%4d", t1/1000000));
			if (i >= 10) {
				t2 += t1;
			}
		}
		double density = (double)100 * ((double)captured / (double)(20*input.length));
		double mbps = (t2 > 0) ? (double)(10*(long)input.length*1000) / (double)t2 : -1;
		System.out.println(String.format(" : %8.3f mb/s (chars, captured %.1f%%)", mbps, density));
	}
}
