/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte.compile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.characterforming.jrte.CompilationException;
import com.characterforming.jrte.GearboxException;
import com.characterforming.jrte.IEffector;
import com.characterforming.jrte.ITarget;
import com.characterforming.jrte.TargetBindingException;
import com.characterforming.jrte.TargetNotFoundException;
import com.characterforming.jrte.engine.Gearbox;
import com.characterforming.jrte.engine.Transduction;

public final class GearboxCompiler {
	private final static String AUTOMATON_FILE_SUFFIX = ".dfa";
	private final static Logger logger = Logger.getLogger("com.characterforming.jrte.compile.GearboxCompiler");

	public static boolean DEBUG = false;

	private final Gearbox gearbox;
	private final File inrAutomataDirectory;
	private final File gearboxOutputPath;
	private final Charset charset;
	private final TargetCompiler targetCompiler;

	public GearboxCompiler(final File inrAutomataDirectory, final File gearboxOutputPath, final Charset charset, final int maxchar, final ITarget target) throws GearboxException, TargetBindingException, TargetNotFoundException {
		this.inrAutomataDirectory = inrAutomataDirectory;
		this.gearboxOutputPath = gearboxOutputPath;
		this.charset = charset;
		this.gearbox = new Gearbox(gearboxOutputPath, charset, target, maxchar);
		Transduction transduction = new Transduction(gearbox);
		IEffector<?>[] baseEffectors = transduction.bind(transduction);
		IEffector<?>[] targetEffectors = target.bind(transduction);
		IEffector<?>[] effectors = new IEffector<?>[baseEffectors.length + targetEffectors.length];
		System.arraycopy(baseEffectors, 0, effectors, 0, baseEffectors.length);
		System.arraycopy(targetEffectors, 0, effectors, baseEffectors.length, targetEffectors.length);
		this.targetCompiler = new TargetCompiler(target, effectors);
	}

	public static void main(final String[] args) throws SecurityException, IOException {
		final FileHandler logHandler = new FileHandler("jrtc.log");
		logHandler.setFormatter(new SimpleFormatter());
		Logger.getLogger("").addHandler(logHandler);
		boolean argsOk = true;
		File inrAutomataPath = null;
		File gearboxOutputPath = null;
		String targetClassname = null;
		String charsetName = "UTF-8";
		Charset charset = null;
		ITarget target = null;
		int maxchar = 0x250;

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--target")) {
				targetClassname = (i + 1) < args.length ? args[++i] : null;
			} else if (args[i].equals("--charset")) {
				charsetName = (i + 1) < args.length ? args[++i] : null;
			} else if (args[i].equals("--maxchar")) {
				maxchar = Integer.parseInt(args[++i]);
			} else if ((i + 2) == args.length) {
				inrAutomataPath = new File(args[i]);
			} else if ((i + 1) == args.length) {
				gearboxOutputPath = new File(args[i]);
			}
		}

		if (inrAutomataPath != null && gearboxOutputPath != null && targetClassname != null && charsetName != null) {
			try {
				final Class<?> targetClass = Class.forName(targetClassname);
				final Object targetObject = targetClass.newInstance();
				if (targetObject instanceof ITarget) {
					target = (ITarget) targetObject;
				} else {
					GearboxCompiler.logger.severe(String.format("target-class '%1$s' could not be instantiated as ITarget", targetClassname));
				}
			} catch (final Exception e) {
				GearboxCompiler.logger.log(Level.SEVERE, String.format("target-class '%1$s' could not be instantiated as ITarget", targetClassname), e);
				argsOk = false;
			}
			if (!Charset.isSupported(charsetName)) {
				GearboxCompiler.logger.severe(String.format("charset-name '%1$s' is not supported", charsetName));
				argsOk = false;
			} else {
				charset = Charset.forName(charsetName);
			}
			if (!inrAutomataPath.isDirectory()) {
				GearboxCompiler.logger.severe(String.format("ginr-output-dir '%1$s' is not a directory", inrAutomataPath.getAbsolutePath()));
				argsOk = false;
			}
			if (gearboxOutputPath.exists()) {
				if (gearboxOutputPath.isDirectory()) {
					GearboxCompiler.logger.severe(String.format("gearbox-path '%1$s' is a directory", gearboxOutputPath.getAbsolutePath()));
					argsOk = false;
				}
			} else if (argsOk) {
				try {
					gearboxOutputPath.createNewFile();
				} catch (final IOException e) {
					GearboxCompiler.logger.log(Level.SEVERE, String.format("gearbox-path '%1$s' could not be created", gearboxOutputPath.getAbsolutePath()), e);
					argsOk = false;
				}
			}
		} else {
			argsOk = false;
		}

		if (!argsOk) {
			System.out.println();
			System.out.println("Usage: java [jvm-options] GearboxCompiler --target <target-class> [--charset <charset-name>] [--maxchar <max-unicode-ordinal>] <ginr-output-dir> <gearbox-path>");
			System.out.println("   target-class     -- fully qualified name of class implementing ITarget");
			System.out.println("   charset-name     -- [optional: default UTF-8] name of Java charset to use for character decoding");
			System.out.println("   max-unicode-char -- [optional: default 0x250] maximum Unicode text value");
			System.out.println("   ginr-output-dir  -- path to directory containing transducer automata compiled by ginr");
			System.out.println("   gearbox-path     -- path for output gearbox file gearbox");
			System.out.println("The <target-class> container must be included in the classpath.");
			System.out.println();
			System.exit(1);
		}

		boolean commit = false;
		GearboxCompiler gearboxCompiler = null;
		try {
			System.out.println(String.format("Jrte gearbox compiler version %1$s%2$sCopyright (C) 2011,2017 Kim T Briggs%2$sDistributed under LGPLv3 (http://www.gnu.org/licenses/lgpl-3.0.txt)", Gearbox.VERSION, System.getProperty("line.separator")));
			System.out.println(String.format("Compiling %1$s%2$s*.dfa to gearbox %3$s", inrAutomataPath.getPath(), System.getProperty("file.separator"), gearboxOutputPath.getPath()));
			gearboxCompiler = new GearboxCompiler(inrAutomataPath, gearboxOutputPath, charset, maxchar, target);
			gearboxCompiler.compile();
			commit = true;
		} catch (final GearboxException e) {
			GearboxCompiler.logger.log(Level.SEVERE, String.format("Caught GearboxException building gearbox '%1$s'", gearboxOutputPath.getAbsolutePath()), e);
			System.exit(1);
		} finally {
			if (gearboxCompiler != null) {
				try {
					gearboxCompiler.gearbox.close(commit);
				} catch (final GearboxException e) {
					GearboxCompiler.logger.log(Level.SEVERE, String.format("Caught IOException closing gearbox '%1$s'", gearboxOutputPath.getAbsolutePath()), e);
					System.exit(1);
				}
			}
		}
	}

	public void compile() throws GearboxException, TargetNotFoundException, TargetBindingException {
		boolean fail = false;
		HashMap<String, Long> transducerOffsetMap = new HashMap<String, Long>(256);
		for (final String filename : this.inrAutomataDirectory.list()) {
			if (!filename.endsWith(GearboxCompiler.AUTOMATON_FILE_SUFFIX)) {
				continue;
			}
			final File inrAutomatonFile = new File(this.inrAutomataDirectory, filename);
			final String transducerName = filename.substring(0, filename.length() - GearboxCompiler.AUTOMATON_FILE_SUFFIX.length());
			try {
				final ArrayList<String> errors = this.compileTransducer(transducerName, inrAutomatonFile);
				if (errors != null) {
					for (final String error : errors) {
						GearboxCompiler.logger.severe(String.format("%1$s: %2$s", transducerName, error));
					}
					fail = true;
				}
			} catch (final IOException e) {
				GearboxCompiler.logger.log(Level.SEVERE, String.format("Caught IOException compiling transducer '%1$s'", inrAutomatonFile.getAbsolutePath()), e);
				fail = true;
			}
		}
		try {
			final int counts[] = this.compileTarget(transducerOffsetMap);
			System.out.println(String.format("Target class %1$s: %2$d text ordinals, %3$d signal ordinals, %4$d simple effectors, %5$d parameterized effectors",
					this.targetCompiler.getTarget().getName(), this.gearbox.getSignalBase(), this.gearbox.getSignalCount(), counts[0], counts[1]));
			System.out.println(String.format("Target package %1$s", this.targetCompiler.getTarget().getClass().getPackage().getName()));
		} catch (final TargetBindingException e) {
			for (final String error : e.getUnboundEffectorList()) {
				GearboxCompiler.logger.severe(String.format("%1$s: %2$s", this.targetCompiler.getTarget().getName(), error));
			}
			fail = true;
		} catch (final Exception e) {
			e.printStackTrace(System.err);
			fail = true;
		}
		if (fail) {
			throw new GearboxException(String.format("Build failed for gearbox '%1$s'", this.gearboxOutputPath.getAbsolutePath()));
		}
	}

	private int[] compileTarget(HashMap<String, Long> transducerOffsetMap) throws GearboxException, TargetNotFoundException, TargetBindingException {
		this.gearbox.setEffectorOrdinalMap(this.targetCompiler.getEffectorOrdinalMap());
		this.gearbox.setEffectorParametersIndex(this.targetCompiler.getEffectorParameters());
		this.gearbox.addTransducer(this.targetCompiler.getTarget().getName(), this.gearbox.seek(-1));
		final Transduction testTransduction = new Transduction(this.gearbox, this.targetCompiler.getTarget(), true);
		return this.targetCompiler.save(this.gearbox, testTransduction.getEffectors());
	}

	private ArrayList<String> compileTransducer(final String transducerName, final File inrAutomatonFile) throws IOException, GearboxException {
		final TransducerCompiler transducerCompiler = new TransducerCompiler(transducerName, this.charset, this.gearbox, this.targetCompiler);
		try {
			transducerCompiler.load(inrAutomatonFile);
			this.gearbox.addTransducer(transducerName, this.gearbox.seek(-1));
			transducerCompiler.save(this.gearbox, this.targetCompiler.getTarget().getName());
		} catch (final CompilationException e) {
			return transducerCompiler.getErrors();
		}
		return null;
	}
}
