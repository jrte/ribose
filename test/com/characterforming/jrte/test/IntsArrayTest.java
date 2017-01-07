/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import static org.junit.Assert.*;

import com.characterforming.jrte.compile.array.Ints;
import com.characterforming.jrte.compile.array.IntsArray;

import org.junit.Test;

public class IntsArrayTest {
	
	Ints intsA = new Ints(new int[] { 1, 2, 3 });
	Ints intsB = new Ints(new int[] { 1, 2, 3 });
	Ints intsC = new Ints(new int[] { -1, -2, -3 });
	
	IntsArray intssA = new IntsArray(new int[][] { new int[] { 1, 2, 3 }, new int[] { 1, 2, 3 } });
	IntsArray intssB = new IntsArray(new int[][] { new int[] { 1, 2, 3 }, new int[] { 1, 2, 3 } });
	IntsArray intssC = new IntsArray(new int[][] { new int[] { -1, -2, -3 }, new int[] { 4, 5, 6 } });

	@Test
	public void testHashCode() {
		assertEquals(intsA.hashCode(), intsB.hashCode());
		assertFalse(intsA.hashCode() == intsC.hashCode());
		assertEquals(intssA.hashCode(), intssB.hashCode());
		assertFalse(intssA.hashCode() == intssC.hashCode());
	}

	@Test
	public void testEqualsObject() {
		assertTrue(intsA.equals(intsB));
		assertFalse(intsA.equals(intsC));
		assertTrue(intssA.equals(intssB));
		assertFalse(intssA.equals(intssC));
	}

}
