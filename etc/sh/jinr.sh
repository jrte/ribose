#!/bin/bash
jdir=$(dirname $(readlink -f $0))/../..
pushd $jdir/build/patterns/automata > /dev/null
cat $jdir/test-patterns/!prologue - | ginr
popd > /dev/null
