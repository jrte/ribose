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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
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
		if ((args.length < 3) || (args.length > 5)) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <transducer-name> <input-path> <model-path>", FileRunner.class.getName()));
			System.exit(1);
		}
		final boolean nil = args[0].compareTo("--nil") == 0;
		int arg = nil ? 1 : 0;
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String modelPath = args[arg++];
		final String regex = (args.length > arg) ? args[arg++] : "";
		final boolean jrteOutEnabled = System.getProperty("jrte.out.enabled", "false").equals("true");
		final boolean regexOutEnabled = !regex.isEmpty() && System.getProperty("regex.out.enabled", "false").equals("true");
		if (jrteOutEnabled && regexOutEnabled) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <transducer-name> <input-path> <model-path>\n(jrteOutputEnabled and regexOutputEnabled can't both be true)", FileRunner.class.getName()));
			System.exit(1);
		}
		
		final Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
		final FileHandler rteHandler = new FileHandler("FileHandler.log");
		rteHandler.setFormatter(new SimpleFormatter());
		rteLogger.addHandler(rteHandler);
		
		final File f = new File(inputPath);
		try (final DataInputStream isr = new DataInputStream(new FileInputStream(f))) {
			long ejrte = 0, tjrte = 0, t0 = 0, t1 = 0;
			int clen = (jrteOutEnabled || regexOutEnabled) ? 0 : (int)f.length();
			byte[] cbuf = null;
			CharBuffer charInput = null;
			if (clen > 0) {
				cbuf = new byte[clen];
				clen = isr.read(cbuf, 0, clen);
				charInput = Charset.defaultCharset().decode(ByteBuffer.wrap(cbuf, 0, cbuf.length));
			}
			
			int loops = 1;
			TRun modelTarget = new TRun();
			if (jrteOutEnabled || !regexOutEnabled) {
				try (IRuntime ribose = Ribose.loadRiboseModel(new File(modelPath), modelTarget)) {
					TRun runTarget = new TRun();
					ITransductor trex = ribose.newTransductor(runTarget);
					if (!jrteOutEnabled) {
						System.out.print(String.format("%20s: ", transducerName));
						loops = 20;
						for (int i = 0; i < loops; i++) {
							trex.input(cbuf, clen);
							if (nil) {
								trex.signal(Signal.nil);
							}
							Status status = trex.start(Bytes.encode(transducerName));
							t0 = System.currentTimeMillis();
							while (status == Status.RUNNABLE) {
								status = trex.run();
								ejrte += trex.getErrorCount();
							}
							assert status != Status.NULL;
							trex.stop();
							t1 = System.currentTimeMillis() - t0;
							System.out.print(String.format("%4d", t1));
							if ((loops == 1) || (i >= 10)) {
								tjrte += t1;
							}
						}
						double epkb = (double)(ejrte*1024) / (double)10000000;
						double mbps = (tjrte > 0) ? ((double)(clen) / (double)(tjrte*1024*1024)) * (Math.min(loops,10)*1000) : -1;
						System.out.println(String.format(" : %7.3f mb/s %7.3f nul/kb", mbps, epkb));
					} else if (nil) {
						ribose.transduce(modelTarget, Bytes.encode(transducerName), Signal.nil, isr);
					} else {
						ribose.transduce(modelTarget, Bytes.encode(transducerName), isr);
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
				Pattern pattern = Pattern.compile(regex);
				if (!regexOutEnabled) {
					System.out.print(String.format("%20s: ", "RegEx"));
					long tregex = 0;
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
						if ((loops == 1) || (i >= 10)) {
							tregex += t1;
						}
						assert count > 0;
					}
					double tr = (tjrte > 0) ? (double) tregex / tjrte : -1;
					double mbps = (tjrte > 0) ? ((double)(clen) / (double)(tregex*1024*1024)) * (Math.min(loops,10)*1000) : -1;
					System.out.println(String.format(" : %7.3f mb/s %7.3f ribose:regex", mbps, tr));
				} else {
					int count = 0;
					try (BufferedReader reader = new BufferedReader(new InputStreamReader(isr), 64<<10)) {
						String line = reader.readLine();
						while (line != null) {
							Matcher matcher = pattern.matcher(line);
							while (matcher.find()) {
								int k = matcher.groupCount();
								if (0 < k) {
									for (int j = 1; j < k; j++) {
										String match = matcher.group(j) != null ? matcher.group(j) : "";
										System.out.printf("%s|", match);
									}
									String match = matcher.group(k) != null ? matcher.group(k) : "";
									System.out.printf("%s", match);
									System.out.println();
								}
								count += k;
							}
							line = reader.readLine();
						}
					}
					assert count > 0;
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
