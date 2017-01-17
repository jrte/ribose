#!/bin/bash
cd $(dirname $(readlink -f $0))/../..
jout="-Djrte.out.enabled=false"
if (($#>0)) && [[ "$1" == "--jrte-out" ]]; then
	jout="-Djrte.out.enabled=true"
	shift
fi
rout="-Dregex.out.enabled=false"
if (($#>0)) && [[ "$1" == "--regex-out" ]]; then
	rout="-Dregex.out.enabled=true"
	shift
fi
if (($#==3)); then
	java $jout $rout -cp build/java/jrte-HEAD.jar:build/java/jrte-HEAD-test.jar:jars/test/junit-4.8.2.jar com.characterforming.jrte.test.FileRunner $*
else
	echo "Usage: FileRunner.sh [--jrte-out] [--regex-out] <transducer> <infile-path> <gears-path>"
fi
