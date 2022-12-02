/***
 * Ribose is a recursive transduction engine for Java
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
 * You should have received a copy of the GNU General Public License
 * along with this program (LICENSE-gpl-3.0). If not, see
 * <http://www.gnu.org/licenses/#GPL>.
 */

package com.characterforming.jrte.test;

import java.nio.CharBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexGroupTest {
	private final char[] input;

	public RegexGroupTest(char[] achars) {
		input = achars;
	}

	public void testRun() {
		int count = 0;
		long t0 = 0, t1 = 0, t2 = 0;
		Pattern regex = Pattern.compile("(a{9}b)");
		for (int i = 0; i < 20; i++) {
			Matcher matcher = regex.matcher(CharBuffer.wrap(input));
			t0 = System.currentTimeMillis();
			while (matcher.find()) {
				count += matcher.groupCount();
			}
			t1 = System.currentTimeMillis() - t0;
			System.out.print(String.format("%4d", (count > 0) ? t1 : -1));
			if (i >= 10) {
				t2 += t1;
			}
		}
		double mbps = (t2 > 0) ? ((double)(10000000) / (double)(t2*1024*1024)) * (10*1000) : -1;
		System.out.println(String.format(" : %7.3f mb/s (chars)", mbps));
	}
}
