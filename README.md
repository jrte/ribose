You will need ginr v2.0.2 from googlecode to build jrte. Ginr can be downloaded from https://github.com/ntozubod/ginr.

After unpacking the tarball, to build ginr, 

```
	mkdir -p ~/bin 
	cd src
	make -f Makefile install
```	

This will install the ginr executable in ~/bin. 

ginr must be on your search PATH before building jrte. To build jrte, 

```
	ant -f build.xml all-clean
```	

To compile transducers defined in `*.inr` files in a `patterns` directory using a common prologue `!prologue` to `*.dfa`
files in an `automata` directory and assemble them into a `gearbox` for the base target class `BaseTarget`,
for example,

```
	patterns=test-patterns/patterns
	automata=build/patterns/automata
	target=com.characterforming.jrte.base.BaseTarget
	compiler=com.characterforming.jrte.compile.GearboxCompiler
	gearbox=build/patterns/Jrte.gears
	cat $patterns/!prologue $patterns/*.inr | ginr 
	java -cp build/java/jrte-HEAD.jar $compiler --maxchar 128 --target $target $automata $gearbox
```

Any subclass of BaseTarget can serve as target class. The `--maxchar` parameter specifies the maximal input ordinal 
expected in input. The value 128 limits input text to 7-bit ASCII. Use `--maxchar=256` if 8-bit characters are encoded 
in ginr source files using `\xdd` equivalents. Note than 7-bit non-printing and all 8-bit characters must be encoded as
`\xdd` equivalents. While `\x00` can be used as an input character ginr tokens of length >1 containing `\x00` will be 
truncated.

To transduce input <file> with any transducer compiled into a gearbox,

```
	runtime=com.characterforming.jrte.Jrte
	gearbox=build/patterns/Jrte.gears
	cat <file> | java -cp build/java/jrte-HEAD.jar $runtime [--nil] <transducer> <gearbox>
```

See Javadoc at doc/index.html and etc/sh/*.sh for examples showing how to run simple transductions.

See LICENSE for licensing details.