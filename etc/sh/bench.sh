#! /bin/bash
for i in $(seq $1); do
	g="build/patterns/Jrte.gears"
	i="test-patterns/inputs/kern-10.log"
	d="-Djrte.out.enabled=false -Dregex.out.enabled=false"
	c="jars/jrte-HEAD-test.jar:jars/jrte-HEAD.jar:jars/test/junit-4.8.2.jar"
	java $d -Dfile.encoding=UTF-8 -classpath $c com.characterforming.jrte.test.FileRunner $2 "$i" "$g"
done
