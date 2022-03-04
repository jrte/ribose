/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTest {

	public void testRun() {
		Pattern regex = Pattern.compile("(?:a{9}b)");
		char[] achars = new char[10000000];
		Arrays.fill(achars, 'a');
		for (int i = 9; i < achars.length; i += 10) {
			achars[i] = 'b';
		}
		int count = 0;
		long t0 = 0, t1 = 0;
		for (int i = 0; i < 20; i++) {
			Matcher matcher = regex.matcher(CharBuffer.wrap(achars));
			t0 = System.currentTimeMillis();
			while (matcher.find()) {
				count++;
			}
			t1 = System.currentTimeMillis() - t0;
			System.out.print(String.format("%4d", t1));
		}
		assert count == (achars.length * 2);
		System.out.println(t1 > 0 ? String.format(" : %,12d chars/s (%,d)", (long)10000000*1000 / t1, achars.length) : "");
	}
}
