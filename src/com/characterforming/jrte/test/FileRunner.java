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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.characterforming.jrte.engine.Base;
import com.characterforming.ribose.IModel;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.ITransductor.Metrics;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.RiboseException;
import com.characterforming.ribose.base.Signal;

public class FileRunner {

	/**
	 * @param args
	 * @throws InterruptedException on error
	 * @throws RiboseException on error
	 * @throws IOException on error
	 */
	public static void main(final String[] args) throws InterruptedException, RiboseException, IOException {
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
		long blen = (int)f.length();
		if (blen <= 0) {
			System.out.println(String.format("Input file is empty: %s", inputPath));
			System.exit(1);
		}
		int exitCode = 1;
		Base.startLogging();
		final Logger rteLogger = Base.getRuntimeLogger();
		final CharsetDecoder decoder = Base.newCharsetDecoder();
		final CharsetEncoder encoder = Base.newCharsetEncoder();
		byte[] bbuf = new byte[(int)blen];
		CharBuffer charInput = null;
		try (final FileInputStream isr = new FileInputStream(f)) {
			blen = isr.read(bbuf, 0, (int)blen);
			assert blen == bbuf.length;
			charInput = decoder.reset().decode(ByteBuffer.wrap(bbuf, 0, bbuf.length));
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
						loops = 20;
						Metrics metrics = new Metrics();
						for (int i = 0; i < loops; i++) {
							assert trex.status().isStopped();
							if (trex.push(bbuf, (int)blen).status().isWaiting()
							&& (!nil || (trex.signal(Signal.NIL).status().isWaiting()))
							&& (trex.start(Bytes.encode(encoder, transducerName)).status().isRunnable())) {
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
									System.out.print(String.format("%4d", t1/(1000000)));
									tjrte += t1;
								}
								assert !trex.status().isRunnable();
								trex.stop();
								assert trex.status().isStopped();
							}
						}
						double mbps = (tjrte > 0) ? (double)(metrics.bytes*1000000000l) / (double)(tjrte*1024*1024) : -1;
						double mps = (metrics.bytes > 0) ? ((double)(100 * metrics.sum) / (double)metrics.bytes) : -1;
						double mpr = (metrics.bytes > 0) ? ((double)(100 * metrics.product) / (double)metrics.bytes) : -1;
						double msc = (metrics.bytes > 0) ? ((double)(100 * metrics.scan) / (double)metrics.bytes) : -1;
						double ekb = (metrics.bytes > 0) ? ((double)(1024 * metrics.errors) / (double)metrics.bytes) : -1;
						System.out.println(String.format(" : %8.3f mb/s %7.3f nul/kb %6.3f%% m+ %6.3f%% m* %6.3f%% m-", mbps, ekb, mps, mpr, msc));
					} else {
						try (
							final FileInputStream isr = new FileInputStream(f);
							final BufferedOutputStream osw = new BufferedOutputStream(System.out, Base.getOutBufferSize())
						) {
							ribose.stream(Bytes.encode(encoder, transducerName), nil ? Signal.NIL : Signal.NONE, isr, osw);
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
							System.out.print(String.format("%4d", count > 0 ? t1/1000000 : -1));
							tregex += t1;
						}
						assert count > 0;
					}
					double mbps = (tregex > 0) ? (double)((loops - 10)*blen*1000) / (double)tregex : -1;
					System.out.println(String.format(" : %8.3f mb/s", mbps));
				} else {
					int count = 0;
					charInput.rewind();
					byte[] bytes = new byte[Base.getOutBufferSize()];
					ByteBuffer bbuffer = ByteBuffer.wrap(bytes);
					CharBuffer cbuffer = CharBuffer.allocate(bytes.length);
					Matcher matcher = pattern.matcher(charInput);
					while (matcher.find()) {
						int k = matcher.groupCount();
						for (int j = 1; j <= k; j++) {
							String match = matcher.group(j);
							if (match == null) {
								match = "";
							}
							if (cbuffer.remaining() < (match.length() + 1)) {
								CoderResult code = encoder.encode(cbuffer.flip(), bbuffer, false);
								assert code.isUnderflow();
								System.out.write(bbuffer.array(), 0, bbuffer.position());
								bbuffer.clear(); cbuffer.clear();
							}
							cbuffer.append(match).append(j < k ? '|' : '\n');
						}
						if (k > 0) {
							count++;
						}
					}
					if (cbuffer.position() > 0) {
						CoderResult code = encoder.encode(cbuffer.flip(), bbuffer, true);
						assert code.isUnderflow();
						System.out.write(bytes, 0, bbuffer.position());
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
