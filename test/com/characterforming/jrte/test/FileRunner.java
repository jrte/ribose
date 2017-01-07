/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

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
			System.out.println(String.format("Usage: java -cp <classpath> %1$s <transducer-name> <input-path> <gearbox-path>", FileRunner.class.getName()));
			System.exit(1);
		}
		final String transducerName = args[0];
		final String inputPath = args[1];
		final String gearboxPath = args[2];
		final Jrte jrte = new Jrte(new File(gearboxPath), "com.characterforming.jrte.base.BaseTarget");
		final File f = new File(inputPath);
		final FileInputStream fis = new FileInputStream(f);
		final InputStreamReader isr = new InputStreamReader(fis);
		char[][] fchar = new char[1024][];
		fchar[0] = new char[] { '!', 'n', 'i', 'l' };
		char[][] ichar = null;
		long flen = 0;
		for (int i = 1; i < fchar.length; i++) {
			fchar[i] = new char[4096];
			final int count = isr.read(fchar[i]);
			if (count < fchar[i].length) {
				if (count > 0) {
					fchar[i] = Arrays.copyOf(fchar[i], count);
				} else if (count < 0) {
					ichar = Arrays.copyOf(fchar, i);
					break;
				}
			} else if (i == fchar.length - 1) {
				fchar = Arrays.copyOf(fchar, (fchar.length * 3) / 2);
			}
			flen += count > 0 ? count : 0;
		}
		final ITransduction t = jrte.transduction(new BaseTarget());
		final long[] t1 = new long[10];
		Arrays.fill(t1, 0);
		for (int i = 0; i < 10; i++) {
			t.start(transducerName);
			final IInput[] inputs = new IInput[] { jrte.input(ichar) };
			t.input(inputs);
			t1[i] = System.currentTimeMillis();
			t.run();
			t1[i] = System.currentTimeMillis() - t1[i];
		}
		System.out.format("%1$-16s: ", transducerName);
		for (final long element : t1) {
			System.out.print(String.format("%1$6d", element));
		}
		System.out.println(String.format(" : %1$9d %2$d", flen, flen * 1000 / t1[t1.length - 1]));
	}

}
