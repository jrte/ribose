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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See
 * LICENSE-gpl-3.0. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte.test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.CharBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.characterforming.jrte.engine.Base;
import com.characterforming.ribose.IModel;
import com.characterforming.ribose.ITransduction;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.ITransductor.Metrics;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.Codec;
import com.characterforming.ribose.base.Signal;

public class FileRunner {

	/**
	 * Shell interface.
	 *
	 * @param args arguments from the shell
	 */
	public static void main(final String[] args) {
		final boolean nil = args[0].compareTo("--nil") == 0;
		int arg = nil ? 1 : 0;
		if ((args.length - arg) < 3) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <model-path> <transducer-name> <input-path>", FileRunner.class.getName()));
			System.exit(1);
		}
		final String modelPath = args[arg++];
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final StringBuffer regbuf = new StringBuffer();
		while (arg < args.length) {
			regbuf.append(args[arg++]);
			if (arg < args.length) {
				regbuf.append(' ');
			}
		}
		final String regex = (regbuf.length() > 0) ? regbuf.toString() : "";
		final boolean jrteOutEnabled = System.getProperty("jrte.out.enabled", "false").equals("true");
		final boolean regexOutEnabled = !regex.isEmpty() && System.getProperty("regex.out.enabled", "false").equals("true");
		if (jrteOutEnabled && regexOutEnabled) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <transducer-name> <input-path> <model-path> [regex]%n(jrteOutputEnabled and regexOutputEnabled can't both be true)",
				FileRunner.class.getName()));
			System.exit(1);
		}

		final File f = new File(inputPath);
		long byteLength = (int)f.length();
		assert byteLength < Integer.MAX_VALUE;
		if (byteLength <= 0 || byteLength > Integer.MAX_VALUE) {
			System.out.println(String.format("Input file is empty or way too big: %s", inputPath));
			System.exit(1);
		}
		int exitCode = 1;
		Base.startLogging();
		final Logger rteLogger = Base.getRuntimeLogger();
		byte[] byteInput = new byte[(int)byteLength];
		CharBuffer charInput = null;
		try (final FileInputStream isr = new FileInputStream(f)) {
			byteLength = isr.read(byteInput, 0, (int)byteLength);
			assert byteLength == byteInput.length;
			charInput = Codec.chars(byteInput, (int)byteLength);
		} catch (Exception e) {
			System.out.println("Runtime exception thrown.");
			rteLogger.log(Level.SEVERE, "Runtime failed, exception thrown.", e);
		}
		try {
			long tjrte = 0, t0 = 0, t1 = 0;
			int loops = 1;
			if (regex.isEmpty() && (jrteOutEnabled || !regexOutEnabled)) {
				try (IModel ribose = IModel.loadRiboseModel(new File(modelPath))) {
					TestTarget runTarget = new TestTarget();
					ITransductor trex = ribose.transductor(runTarget);
					if (!jrteOutEnabled) {
						System.out.print(String.format("%20s: ", transducerName));
						Bytes transducerKey = Codec.encode(transducerName);
						loops = 20;
						Metrics metrics = new Metrics();
						for (int i = 0; i < loops; i++) {
							try (ITransduction transduction = ribose.transduction(trex)) {
								transduction.reset();
								assert trex.status().isStopped();
								if (nil)
									trex.signal(Signal.NIL);
								trex.push(byteInput, (int)byteLength);
								if (trex.start(transducerKey).status().isRunnable()) {
									t0 = System.nanoTime();
									do {
										trex.run();
										if (i >= 10) {
											trex.metrics(metrics);
										}
									} while (trex.status().isRunnable());
									if (trex.status().isPaused()) {
										trex.signal(Signal.EOS).run();
									}
									t1 = System.nanoTime() - t0;
									if (i >= 10) {
										tjrte += t1;
									}
								}
								assert !trex.status().isRunnable();
							}
							assert trex.status().isStopped();
						}
						long bytes = 10 * byteLength;
						double mbps = (tjrte > 0) ? (double)(bytes*1000000000l) / (double)(tjrte*1024*1024) : -1;
						double mps = (bytes > 0) ? ((double) (100 * metrics.traps[1][1]) / (double) bytes) : -1;
						double mpr = (bytes > 0) ? ((double)(100 * metrics.traps[2][1]) / (double)bytes) : -1;
						double msc = (bytes > 0) ? ((double)(100 * metrics.traps[3][1]) / (double)bytes) : -1;
						double ekb = (bytes > 0) ? ((double)(1024 * metrics.errors) / (double)bytes) : -1;
						long sum = metrics.traps[1][0] > 0 ? metrics.traps[1][1] / metrics.traps[1][0] : 0;
						long product = metrics.traps[2][0] > 0 ? metrics.traps[2][1] / metrics.traps[2][0] : 0;
						long scan = metrics.traps[3][0] > 0 ? metrics.traps[3][1] / metrics.traps[3][0] : 0;
						String ssum = String.format("(%d/%.2f%%):msum", sum, mps);
						String sproduct = String.format("(%d/%.2f%%):mproduct", product, mpr);
						String sscan = String.format("(%d/%.2f%%):mscan", scan, msc);
						System.out.println(String.format("%8.3f mb/s %7.3f nul/kb %16s %20s %17s",
							mbps, ekb, ssum, sproduct, sscan));
						assert bytes == 0 || bytes >= 10*byteLength;
					} else {
						try (
							final FileInputStream isr = new FileInputStream(f);
							final BufferedOutputStream osw = new BufferedOutputStream(System.out, Base.getOutBufferSize())
						) {
							ribose.stream(Codec.encode(transducerName), nil ? Signal.NIL : Signal.NONE, isr, osw);
						} catch (Exception e) {
							System.out.println("Runtime exception thrown.");
							rteLogger.log(Level.SEVERE, "Runtime failed, exception thrown.", e);
						}
					}
				} catch (Exception e) {
					rteLogger.log(Level.SEVERE, "Runtime failed, exception thrown.", e);
					System.exit(exitCode);
				}
			}
			if (!regex.isEmpty() && (regexOutEnabled || !jrteOutEnabled)) {
				Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
				long tregex = 0;
				if (!regexOutEnabled) {
					System.out.print(String.format("%20s: ", "RegEx"));
					loops = 20;
					for (int i = 0; i < loops; i++) {
						Matcher matcher = pattern.matcher(charInput);
						t0 = System.nanoTime();
						int count = 0;
						while (matcher.find()) {
							int k = matcher.groupCount();
							if (0 < k) {
								for (int j = 1; j < k; j++) {
									if (matcher.group(j) != null) {
										++count;
									}
								}
								if (matcher.group(k) != null) {
									++count;
								}
							}
						}
						t1 = System.nanoTime() - t0;
						if (i >= 10) {
							tregex += t1;
						}
						assert count > 0;
					}
					double mbps = (tregex > 0) ? (double)((loops - 10)*byteLength*1000) / (double)tregex : -1;
					System.out.println(String.format("%8.3f mb/s", mbps));
				} else {
					int count = 0;
					Matcher matcher = pattern.matcher(charInput);
					while (matcher.find()) {
						int k = matcher.groupCount();
						for (int j = 1; j <= k; j++) {
							String group = matcher.group(j);
							System.out.write(group != null ? Codec.encode(group).bytes() : Bytes.EMPTY_BYTES);
							System.out.write(j < k ? '|' : '\n');
						}
						if (k > 0) {
							count++;
						}
					}
					System.out.flush();
					assert count > 0;
				}
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

}
