/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;

import org.junit.Test;

import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.Jrte;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.base.BaseTarget;


public class LinuxKernelLogTest {

	@Test
	public void testRun() throws RteException, FileNotFoundException {
		try {
			File f = new File("test-patterns/inputs/kern.log");
			Jrte jrte = new Jrte(new File("build/patterns/Jrte.gears"), "com.characterforming.jrte.base.BaseTarget");
			IInput[] inputs = new IInput[] {
					jrte.input(new FileInputStream(f), Charset.defaultCharset()),
					jrte.input(new char[][] { new char[] { 'n','i','l' } })
			};

			ITransduction t = jrte.transduction(new BaseTarget());
			long t0 = System.currentTimeMillis();
			t.start("linuxkernel");
			t.input(inputs);
			long t1 = System.currentTimeMillis();
			t.run();
			long t2 = System.currentTimeMillis();
			System.out.print(String.format("%1$6d %2$6d", t1 - t0, t2 - t1));
			System.out.println();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
