#!/bin/bash
cd $(dirname $(readlink -f $0))/../..
java -cp build/java/jrte-HEAD.jar:build/java/jrte-HEAD-test.jar:jars/test/junit-4.8.2.jar com.characterforming.jrte.test.TestRunner $*
