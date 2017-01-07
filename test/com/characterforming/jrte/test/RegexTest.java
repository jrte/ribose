/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;


public class RegexTest {

	@Test
	public void testRun() {
		Pattern regex = Pattern.compile("a*b");
		char[] achars = new char[10000000];
		Arrays.fill(achars, 'a');
		for (int i = 9; i < achars.length; i += 10) {
			achars[i] = 'b';
		}
		for (int i = 0; i < 10; i++) {
			Matcher matcher = regex.matcher(CharBuffer.wrap(achars));
			long t0 = System.currentTimeMillis();
			while (matcher.find()) {
			}
			long t1 = System.currentTimeMillis() - t0;
			System.out.print(String.format("%1$6d", t1));
		}
		System.out.println();
	}
}
