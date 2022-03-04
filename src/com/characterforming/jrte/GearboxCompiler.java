/***
 * JRTE is a recursive transduction engine for Java
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
 * You should have received copies of the GNU General Public License
 * and GNU Lesser Public License along with this program.  See 
 * LICENSE-lgpl-3.0 and LICENSE-gpl-3.0. If not, see 
 * <http://www.gnu.org/licenses/>.
 */

package com.characterforming.jrte;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.characterforming.jrte.base.Base;
import com.characterforming.jrte.engine.Gearbox;
import com.characterforming.jrte.engine.Gearbox.Gear;

public final class GearboxCompiler {
	private final static Logger rtcLogger = Logger.getLogger(Base.RTC_LOGGER_NAME);

	public static boolean DEBUG = false;

	public GearboxCompiler() throws SecurityException, IOException {
	}

	public static void main(final String[] args) throws SecurityException, IOException {
		File inrAutomataPath = null;
		File gearboxOutputPath = null;
		String targetClassname = null;
		ITarget target = null;

		boolean argsOk = (args.length == 4);
		if (argsOk) {
			if (args[0].equals("--target")) {
				targetClassname = args[1];
			}
			inrAutomataPath = new File(args[2]);
			gearboxOutputPath = new File(args[3]);
		}
		
		if (argsOk) {
			try {
				final Class<?> targetClass = Class.forName(targetClassname);
				final Object targetObject = targetClass.getDeclaredConstructor().newInstance();
				if (targetObject instanceof ITarget) {
					target = (ITarget) targetObject;
				} else {
					GearboxCompiler.rtcLogger.log(Level.SEVERE, String.format("target-class '%1$s' could not be instantiated as ITarget", targetClassname));
				}
			} catch (Exception e) {
				GearboxCompiler.rtcLogger.log(Level.SEVERE, String.format("target-class '%1$s' could not be instantiated as ITarget", targetClassname), e);
				argsOk = false;
			}
			if (!inrAutomataPath.isDirectory()) {
				GearboxCompiler.rtcLogger.log(Level.SEVERE, String.format("ginr-output-dir '%1$s' is not a directory", inrAutomataPath.getPath()));
				argsOk = false;
			}
			if (gearboxOutputPath.isDirectory()) {
				GearboxCompiler.rtcLogger.log(Level.SEVERE, String.format("gearbox-path '%1$s' is a directory", gearboxOutputPath.getPath()));
				argsOk = false;
			}
		}

		if (!argsOk) {
			System.out.println();
			System.out.println("Usage: java [jvm-options] GearboxCompiler --target <target-class> <ginr-output-dir> <gearbox-path>");
			System.out.println("   target-class     -- fully qualified name of class implementing ITarget");
			System.out.println("   ginr-output-dir  -- path to directory containing transducer automata compiled by ginr");
			System.out.println("   gearbox-path     -- path for output gearbox file gearbox");
			System.out.println("The <target-class> container must be included in the classpath.");
			System.out.println();
			System.exit(1);
		}

		Gearbox gearbox = null;
		boolean committed = false;
		try {
			System.out.println(String.format("Jrte gearbox compiler version %1$s%2$sCopyright (C) 2011,2022 Kim T Briggs%2$sDistributed under LGPLv3 (http://www.gnu.org/licenses/lgpl-3.0.txt)", Base.RTE_VERSION, System.getProperty("line.separator")));
			System.out.println(String.format("Compiling %1$s%2$s*.dfa to gearbox %3$s", inrAutomataPath.getPath(), System.getProperty("file.separator"), gearboxOutputPath.getPath()));
			gearbox = new Gearbox(Gear.compile, gearboxOutputPath, target);
			committed = gearbox.compile(inrAutomataPath);
		} catch (Exception e) {
			String msg = String.format("Caught Exception compiling gearbox '%1$s'", gearboxOutputPath.getPath());
			GearboxCompiler.rtcLogger.log(Level.SEVERE, msg, e);
			if (gearbox != null) {
				gearbox.setDeleteOnClose(true);
			}
		} finally {
			if (gearbox != null) {
				try {
					gearbox.close();
					gearbox = null;
				} catch (Exception e) {
					String msg = String.format("Caught Exception closing gearbox '%1$s'", gearboxOutputPath.getPath());
					GearboxCompiler.rtcLogger.log(Level.SEVERE, msg, e);
				}
			}
		}
		System.exit(committed ? 0 : 1);
	}
}
