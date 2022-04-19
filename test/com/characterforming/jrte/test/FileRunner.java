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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITransductor;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.base.Bytes;
import com.characterforming.ribose.IRiboseRuntime;
import com.characterforming.ribose.Ribose;

public class FileRunner {

	/**
	 * @param args
	 * @throws InterruptedException On error
	 * @throws RteException On error
	 * @throws IOException On error
	 */
	public static void main(final String[] args) throws InterruptedException, RteException, IOException {
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
		
		try {
			final boolean jrteOutEnabled = System.getProperty("jrte.out.enabled", "false").equals("true");
			final boolean regexOutEnabled = !regex.isEmpty() && System.getProperty("regex.out.enabled", "false").equals("true");
			if (jrteOutEnabled && regexOutEnabled) {
				System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <transducer-name> <input-path> <model-path>\n(jrteOutputEnabled and regexOutputEnabled can't both be true)", FileRunner.class.getName()));
				System.exit(1);
			}
			
			long ejrte = 0, tjrte = 0, t0 = 0, t1 = 0;
			final File f = new File(inputPath);
			final DataInputStream isr = new DataInputStream(new FileInputStream(f));
			int clen = (int)f.length();
			byte[] cbuf = new byte[clen];
			clen = isr.read(cbuf, 0, clen);
			isr.close();
			
			Logger rteLogger = Logger.getLogger(Base.RTE_LOGGER_NAME);
			final FileHandler rteHandler = new FileHandler("jrte.log");
			rteLogger.addHandler(rteHandler);
			rteHandler.setFormatter(new SimpleFormatter());

			BaseTarget target = new BaseTarget();
			final IRiboseRuntime ribose = Ribose.loadRiboseRuntime(new File(modelPath), target);
			final ITransductor trex = ribose.newTransductor(target);
			byte[][] input = nil ? new byte[][] { Base.Signal.nil.reference(), cbuf } : new byte[][] { cbuf };
			int loops;
			if (!regexOutEnabled) {
				if (!jrteOutEnabled) {
					System.out.print(String.format("%20s: ", transducerName));
				}
				loops = jrteOutEnabled ? 1 : 20;
				for (int i = 0; i < loops; i++) {
					trex.start(Bytes.encode(transducerName));
					trex.input(new IInput[] { ribose.input(input) });
					t0 = System.currentTimeMillis();
					do {
						switch (trex.run()) {
						case RUNNABLE:
							break;
						case PAUSED:
						case STOPPED:
							trex.stop();
							break;
						case NULL:
						default:
							assert false;
							break;
						}
					}
					while (trex.status() == ITransductor.Status.PAUSED);
					t1 = System.currentTimeMillis() - t0;
					if (!jrteOutEnabled) {
						System.out.print(String.format("%4d", t1));
					}
					if ((loops == 1) || (i >= 10)) {
						tjrte += t1;
						ejrte += trex.getErrorCount();
					}
				}
				if (!jrteOutEnabled) {
					double epkb = (double)(ejrte*1024) / (double)10000000;
					double mbps = (tjrte > 0) ? ((double)(clen) / (double)(tjrte*1024*1024)) * (Math.min(loops,10)*1000) : -1;
					System.out.println(String.format(" : %7.3f mb/s %7.3f nul/kb", mbps, epkb));
				} 
			}
			if (!jrteOutEnabled && !regex.isEmpty()) {
				Pattern pattern = Pattern.compile(regex);
				if (!regexOutEnabled) {
					System.out.print(String.format("%20s: ", "RegEx"));
				}
				long tregex = 0;
				loops = regexOutEnabled ? 1 : 20;
				CharBuffer charInput = Charset.defaultCharset().decode(
						ByteBuffer.wrap(input[input.length - 1], 0, input[input.length - 1].length)
				);
				for (int i = 0; i < loops; i++) {
					Matcher matcher = pattern.matcher(charInput);
					t0 = System.currentTimeMillis();
					int count = 0;
					while (matcher.find()) {
						if (regexOutEnabled) {
							int k = matcher.groupCount();
							if (0 < k) {
								for (int j = 1; j < k; j++) {
									System.out.printf("%s ", matcher.group(j));
								}
								System.out.printf("%s", matcher.group(k));
								System.out.println();
							}
						} else {
							count += matcher.groupCount();
						}
					}
					t1 = System.currentTimeMillis() - t0;
					if (!regexOutEnabled) {
						System.out.print(String.format("%4d", count > 0 ? t1 : -1));
					}
					if ((loops == 1) || (i >= 10)) {
						tregex += t1;
					}
				}
				if (!regexOutEnabled) {
					double tr = (tjrte > 0) ? (double) tregex / tjrte : -1;
					double mbps = (tjrte > 0) ? ((double)(clen) / (double)(tregex*1024*1024)) * (Math.min(loops,10)*1000) : -1;
					System.out.println(String.format(" : %7.3f mb/s %7.3f ribose:regex", mbps, tr));
				} 
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.out.flush();
		}
		System.exit(0);
	}

}
