/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
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
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.Jrte;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.base.BaseTarget;
import com.characterforming.jrte.base.Bytes;

public class FileRunner {

	/**
	 * @param args
	 * @throws InterruptedException On error
	 * @throws RteException On error
	 * @throws IOException On error
	 */
	public static void main(final String[] args) throws InterruptedException, RteException, IOException {
		if ((args.length < 3) || (args.length > 5)) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <transducer-name> <input-path> <gearbox-path>", FileRunner.class.getName()));
			System.exit(1);
		}
		final boolean nil = args[0].compareTo("--nil") == 0;
		int arg = nil ? 1 : 0;
		final String transducerName = args[arg++];
		final String inputPath = args[arg++];
		final String gearboxPath = args[arg++];
		final String regex = (args.length > arg) ? args[arg++] : "";
		
		try {
			final boolean jrteOutEnabled = System.getProperty("jrte.out.enabled", "false").equals("true");
			final boolean regexOutEnabled = !regex.isEmpty() && System.getProperty("regex.out.enabled", "false").equals("true");
			if (jrteOutEnabled && regexOutEnabled) {
				System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s [--nil] <transducer-name> <input-path> <gearbox-path>\n(jrteOutputEnabled and regexOutputEnabled can't both be true)", FileRunner.class.getName()));
				System.exit(1);
			}
			
			long tjrte = 0, t0 = 0, t1 = 0;
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
			final Jrte jrte = new Jrte(new File(gearboxPath), target);
			final ITransduction trex = jrte.transduction(target);
			byte[][] input = nil
				? new byte[][] { Base.encodeReferenceOrdinal(Base.TYPE_REFERENCE_SIGNAL, Base.Signal.nil.signal()), cbuf }
				: new byte[][] { cbuf };
			int loops;
			if (!regexOutEnabled) {
				if (!jrteOutEnabled) {
					System.out.print(String.format("%20s: ", transducerName));
				}
				loops = jrteOutEnabled ? 1 : 20;
				for (int i = 0; i < loops; i++) {
					trex.start(Bytes.encode(transducerName));
					trex.input(new IInput[] { jrte.input(input) });
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
					while (trex.status() == ITransduction.Status.PAUSED);
					t1 = System.currentTimeMillis() - t0;
					if (!jrteOutEnabled) {
						System.out.print(String.format("%4d", t1));
					}
					tjrte += t1;
				}
				if (!jrteOutEnabled) {
					System.out.println(
							String.format(" : %,12d (%dx%,d)", (((long) clen * loops * 1000) / tjrte), loops, clen));
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
						}
					}
					t1 = System.currentTimeMillis() - t0;
					if (!regexOutEnabled) {
						System.out.print(String.format("%4d", t1));
					}
					tregex += t1;
				}
				if (!regexOutEnabled) {
					double tr = (double) tregex / tjrte;
					System.out.println(String.format(" : %,12d (%dx%,d) %3.2f", (((long)clen * loops * 1000) / tregex), loops, clen, tr));
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
