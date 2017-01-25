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
	 * @throws InterruptedException
	 * @throws RteException
	 * @throws IOException
	 */
	public static void main(final String[] args) throws InterruptedException, RteException, IOException {
		if (args.length != 3) {
			System.out.println(String.format("Usage: java -cp <classpath> [-Djrte.out.enabled=true ^|^ -Dregex.out.enabled=true] %s <transducer-name> <input-path> <gearbox-path>", FileRunner.class.getName()));
			System.exit(1);
		}
		boolean jrteOutEnabled = System.getProperty("jrte.out.enabled", "false").equals("true");
		boolean regexOutEnabled = System.getProperty("regex.out.enabled", "false").equals("true");
		if (jrteOutEnabled == regexOutEnabled) {
			jrteOutEnabled = regexOutEnabled = false;
		}
		final String transducerName = args[0];
		final String inputPath = args[1];
		final String gearboxPath = args[2];
		
		long tratio = 0, t0 = 0, t1 = 0;
		final File f = new File(inputPath);
		final InputStreamReader isr = new InputStreamReader(new FileInputStream(f));
		int clen = (int)f.length();
		char[][]fchar = new char[][] {new String("!nil").toCharArray(), new char[clen]};
		int count = isr.read(fchar[1], 0, clen);
		isr.close();
		
		if (jrteOutEnabled || !regexOutEnabled) {
			// "May 15 07:58:52 kb-ubuntu kernel: [ 1794.599801] DROPPED IN=eth0 OUT= MAC=01:00:5e:00:00:fb:00:13:20:c0:36:32:08:00 SRC=192.168.144.101 DST=224.0.0.251 LEN=32 TOS=0x00 PREC=0x00 TTL=1 ID=8596 OPT (94040000) PROTO=2 "
			final Jrte jrte = new Jrte(new File(gearboxPath), "com.characterforming.jrte.base.BaseTarget");
			final ITransduction t = jrte.transduction(new BaseTarget());
			if (!jrteOutEnabled) {
				System.out.print(String.format("%20s: ", transducerName));
			}
			count = jrteOutEnabled ? 1 : 20;
			for (int i = 0; i < count; i++) {
				t.start(transducerName);
				final IInput[] inputs = new IInput[] { jrte.input(fchar) };
				t.input(inputs);
				t0 = System.currentTimeMillis();
				t.run();
				t1 = System.currentTimeMillis() - t0;
				if (!jrteOutEnabled) {
					System.out.print(String.format("%6d", t1));
				}
			}
			if (!jrteOutEnabled) {
				tratio = (0 < t1) ? t1 : -1;
				System.out.println(String.format(" : %,12d (%,d)", (long) clen * 2000 / tratio, clen));
			}
		}
		
		if (regexOutEnabled || !jrteOutEnabled) {
			// 1date 3host 4tag 5in 6out 8mac 9src 10dst 11proto 13sp 14dp
			String regex = "([JFMASOND][a-z]+ [0-9]+ ([0-9]+:)+[0-9]+) ([-A-Za-z_0-9]*) kernel: \\[[ ]*[0-9]+\\.[0-9]+\\] (DROPPED|ABORTED|LIMITED) IN=([-A-Za-z_0-9]*) OUT=([-A-Za-z_0-9]*) (MAC=([A-Fa-f:0-9]*) )?SRC=([.0-9]*) DST=([.0-9]*).* PROTO=([A-Z0-9]*)(.* SPT=([0-9]*) DPT=([0-9]*))?.*\n";
			Pattern pattern = Pattern.compile(regex);
			if (!regexOutEnabled) {
				System.out.print(String.format("%20s: ", "RegEx"));
			}
			count = regexOutEnabled ? 1 : 20;
			for (int i = 0; i < count; i++) {
				Matcher matcher = pattern.matcher(CharBuffer.wrap(fchar[1]));
				t0 = System.currentTimeMillis();
				while (matcher.find()) {
					if (regexOutEnabled) {
						int k = matcher.groupCount();
						if (k >= 4) {
							System.out.printf("%s ", matcher.group(4));
							System.out.printf("%s ", matcher.group(1));
							System.out.printf("%s ", matcher.group(3));
							for (int j = 5; j < k; j++) {
								if ((j != 7) && (j != 12)) {
									System.out.printf("%s ", matcher.group(j));
								}
							}
						}
						System.out.println();
					}
				}
				t1 = System.currentTimeMillis() - t0;
				if (!regexOutEnabled) {
					System.out.print(String.format("%6d", t1));
				}
			}
			if (!regexOutEnabled) {
				long tregex = (0 < t1) ? t1 : -1;
				double tr = (double) tregex / tratio;
				System.out.println(String.format(" : %,12d %3.2f (%,d)", (long) clen * 2000 / tregex, tr, clen));
			}
		}
	}

}
