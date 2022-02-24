/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.Jrte;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.base.BaseTarget;

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
			final InputStreamReader isr = new InputStreamReader(new FileInputStream(f));
			int clen = (int)f.length();
			char[][] fchar = nil ? new char[][] {new String("!nil").toCharArray(), new char[clen]} : new char[][] {new char[clen]};
			clen = isr.read(fchar[fchar.length - 1], 0, clen);
			isr.close();
			
			final Jrte jrte = new Jrte(new File(gearboxPath), "com.characterforming.jrte.base.BaseTarget");
			final ITransduction t = jrte.transduction(new BaseTarget());
			int loops;
			if (!regexOutEnabled) {
				if (!jrteOutEnabled) {
					System.out.print(String.format("%20s: ", transducerName));
				}
				loops = jrteOutEnabled ? 1 : 20;
				for (int i = 0; i < loops; i++) {
					t.start(transducerName);
					final IInput[] inputs = new IInput[] { jrte.input(fchar) };
					t.input(inputs);
					t0 = System.currentTimeMillis();
					t.run();
					t1 = System.currentTimeMillis() - t0;
					if (!jrteOutEnabled) {
						System.out.print(String.format("%6d", t1));
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
				for (int i = 0; i < loops; i++) {
					Matcher matcher = pattern.matcher(CharBuffer.wrap(fchar[fchar.length - 1]));
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
						System.out.print(String.format("%6d", t1));
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
	}

}
