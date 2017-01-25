/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.test;

import java.io.File;

import org.junit.Test;

import com.characterforming.jrte.DomainErrorException;
import com.characterforming.jrte.EffectorException;
import com.characterforming.jrte.GearboxException;
import com.characterforming.jrte.IInput;
import com.characterforming.jrte.ITransduction;
import com.characterforming.jrte.InputException;
import com.characterforming.jrte.Jrte;
import com.characterforming.jrte.RteException;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.TargetNotFoundException;
import com.characterforming.jrte.TransducerNotFoundException;
import com.characterforming.jrte.base.BaseTarget;

public class HelloWorldTest {

	@Test
	public void testJrte() throws GearboxException, TargetBindingException, InputException, TransducerNotFoundException, TargetNotFoundException, DomainErrorException, EffectorException, RteException {
		final Jrte jrte = new Jrte(new File("build/patterns/Jrte.gears"), "com.characterforming.jrte.base.BaseTarget");
		final IInput[] input = new IInput[] { jrte.input(new char[][] { "~nil".toCharArray(), "hello world".toCharArray() }) };
		final ITransduction t = jrte.transduction(new BaseTarget());
		t.start("_HelloWorld");
		t.input(input);
	}
}
