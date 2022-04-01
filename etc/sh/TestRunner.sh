#!/bin/bash
cd $(dirname $(readlink -f $0))/../..
java -cp jars/ribose-0.0.0.jar:jars/ribose-0.0.0-test.jar com.characterforming.jrte.test.TestRunner $*
