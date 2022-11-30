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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.characterforming.ribose.IRuntime;
import com.characterforming.ribose.ITransductor;
import com.characterforming.ribose.ITransductor.Status;
import com.characterforming.ribose.Ribose;
import com.characterforming.ribose.TRun;
import com.characterforming.ribose.base.Base;
import com.characterforming.ribose.base.Base.Signal;
import com.characterforming.ribose.base.Bytes;
import com.characterforming.ribose.base.RiboseException;

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
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <transducer-name> <input-path> <model-path>", FileRunner.class.getName()));
			System.exit(1);
		}
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String modelPath = args[arg++];
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
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <transducer-name> <input-path> <model-path> [regex]\n(jrteOutputEnabled and regexOutputEnabled can't both be true)", FileRunner.class.getName()));
			System.exit(1);
		}

		final Logger rteLogger = Logger.getLogger("FileRunner");
		final FileHandler rteHandler = new FileHandler("FileRunner.log", true);
		rteHandler.setFormatter(new SimpleFormatter());
		rteLogger.addHandler(rteHandler);
		rteLogger.setLevel(Level.WARNING);

		final File f = new File(inputPath);
		final CharsetDecoder decoder = Base.getRuntimeCharset().newDecoder();
		final CharsetEncoder encoder = Base.getRuntimeCharset().newEncoder();
		try (final FileInputStream isr = new FileInputStream(f)) {
			long ejrte = 0, tjrte = 0, t0 = 0, t1 = 0;
			int clen = (int)f.length();
			if (clen <= 0) {
				System.out.println(String.format("Input file is empty: %s", inputPath));
				System.exit(1);
			}
			byte[] cbuf = new byte[clen];
			clen = isr.read(cbuf, 0, clen);
			assert clen > 0;
			int loops = 1;
			if (jrteOutEnabled || !regexOutEnabled) {
				try (IRuntime ribose = Ribose.loadRiboseModel(new File(modelPath), new TRun())) {
					TRun runTarget = new TRun();
					ITransductor trex = ribose.newTransductor(runTarget);
					if (!jrteOutEnabled) {
						System.out.print(String.format("%20s: ", transducerName));
						loops = 20;
						for (int i = 0; i < loops; i++) {
							assert trex.status().isStopped();
							if (trex.push(cbuf, clen).status().isWaiting()
							&& (!nil || (trex.push(Signal.nil).status().isWaiting()))
							&& (trex.start(Bytes.encode(encoder, transducerName)).status().isRunnable())) {
								t0 = System.currentTimeMillis();
								while (trex.run().status() == Status.RUNNABLE) {
									ejrte += trex.getErrorCount();
								}
								ejrte += trex.getErrorCount();
								t1 = System.currentTimeMillis() - t0;
								if (i >= 10) {
									tjrte += t1;
								}
								assert trex.status().isPaused() || trex.status().isStopped();
								System.out.print(String.format("%4d", t1));
								trex.stop();
							}
						}
						double epkb = (double)(ejrte*1024) / (double)(clen*loops);
						double mbps = (tjrte > 0) ? ((double)clen / (double)(tjrte*1024*1024)) * (loops - 10) * 1000 : -1;
						System.out.println(String.format(" : %7.3f mb/s %7.3f nul/kb", mbps, epkb));
					} else {
						t0 = System.currentTimeMillis();
						if (nil) {
							ribose.transduce(runTarget, Bytes.encode(encoder, transducerName), Signal.nil, isr, System.out);
						} else {
							ribose.transduce(runTarget, Bytes.encode(encoder, transducerName), isr, System.out);
						}
						tjrte = System.currentTimeMillis() - t0;
						double mbps = (tjrte > 0) ? ((double)clen / (double)(tjrte*1024*1024)) * 1000 : -1;
						rteLogger.log(Level.INFO, String.format("%20s : %7.3f mb/s; %s (%,d bytes)", transducerName, mbps, inputPath, clen));
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
			if (regexOutEnabled || !jrteOutEnabled) {
				CharBuffer charInput = decoder.decode(ByteBuffer.wrap(cbuf, 0, cbuf.length));
				Pattern pattern = Pattern.compile(regex);
				long tregex = 0;
				if (!regexOutEnabled) {
					System.out.print(String.format("%20s: ", "RegEx"));
					loops = 20;
					for (int i = 0; i < loops; i++) {
						Matcher matcher = pattern.matcher(charInput);
						t0 = System.currentTimeMillis();
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
						t1 = System.currentTimeMillis() - t0;
						System.out.print(String.format("%4d", count > 0 ? t1 : -1));
						if (i >= 10) {
							tregex += t1;
						}
						assert count > 0;
					}
					double tr = (tjrte > 0) ? (double) tregex / tjrte : -1;
					double mbps = (tregex > 0) ? ((double)clen / (double)(tregex*1024*1024)) * (loops - 10) * 1000 : -1;
					System.out.println(String.format(" : %7.3f mb/s %7.3f ribose:regex", mbps, tr));
				} else {
					int count = 0;
					Matcher matcher = pattern.matcher(charInput);
					t0 = System.currentTimeMillis();
					try {
						byte[] bytes = new byte[Base.getOutBufferSize()];
						ByteBuffer bbuf = ByteBuffer.wrap(bytes);
						CharBuffer sbuf = CharBuffer.allocate(bytes.length);
						while (matcher.find()) {
							int k = matcher.groupCount();
							for (int j = 1; j <= k; j++) {
								String match = matcher.group(j);
								if (match == null) {
									match = "";
								}
								if (sbuf.remaining() < (match.length() + 1)) {
									CoderResult code = encoder.encode(sbuf.flip(), bbuf, false);
									assert code.isUnderflow();
									System.out.write(bbuf.array(), 0, bbuf.position());
									bbuf.clear(); sbuf.clear();
								}
								sbuf.append(match).append(j < k ? '|' : '\n');
							}
							if (k > 0) {
								count++;
							}
						}
						if (sbuf.position() > 0) {
							CoderResult code = encoder.encode(sbuf.flip(), bbuf, true);
							assert code.isUnderflow();
							System.out.write(bytes, 0, bbuf.position());
						}
						System.out.flush();
					} catch (Exception e) {
						System.out.println("Runtime exception thrown.");
						rteLogger.log(Level.SEVERE, "Runtime failed, exception thrown.", e);
						System.exit(1);
					}
					assert count > 0;
					tregex = System.currentTimeMillis() - t0;
					double mbps = (tregex > 0) ? ((double)clen / (double)(tregex*1024*1024)) * 1000 : -1;
					rteLogger.log(Level.INFO, String.format("%20s : %7.3f mb/s; %s (%,d bytes)", "RegEx", mbps, inputPath, clen));
				}
			}
		} catch (Exception e) {
			System.out.println("Runtime exception thrown.");
			rteLogger.log(Level.SEVERE, "Runtime failed, exception thrown.", e);
			System.exit(1);
		} catch (AssertionError e) {
			System.out.println("Runtime assertion failed.");
			rteLogger.log(Level.SEVERE, "Runtime assertion failed", e);
			System.exit(1);
		} finally {
			System.out.flush();
		}
		System.exit(0);
	}

}
