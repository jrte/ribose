#!/bin/bash
cd $(dirname $(readlink -f $0))
java -cp jrte.jar:jrte.test.jar:junit-4.8.2.jar com.characterforming.jrte.test.TestRunner $*
