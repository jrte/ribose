Jrte is about inversion of control for high-volume text analysis and information extraction and transformation. An input source presents as a generator of sequences of ordinal numbers (eg, UNICODE ordinal, quantized analog signal, composite multivariate metric sample). If those sequences have a coherent pattern that can be described in terms of catenation, union and repetition then that description determines a unique regular set. This input pattern can then be extended to interleave at each input ordinal a sequence of output ordinals, which Jrte associates with effector methods expressed by a target class bound to the transduction. This input/output pattern description is compiled by [ginr](https://github.com/ntozubod/ginr) to build a state-minimized finite state transducer or FST (ginr is a regular expression compiler that implements a wide range of operations on regular sets). 
```
Fibonacci = (
   # ~r, ~q, ~p are sequences of 0s preset to empty string ^
   (
      # fib(0): ~q <- 0
      ('0', select[`~q`] paste)
      # fib(n>1): cycle (~r) <- (~q) <- (~p) <- (~p)(~r), (~r) <- ^
      ('0', select[`~r`] cut[`~p`] select[`~p`] copy[`~q`] select[`~q`] cut[`~r`])*
   )?
   # (~q) is empty or selected and holds the result, so append nl and print result
   (nl, paste out stop)
);

(Fibonacci$(0,1 2)):prsseq;

(START)  0  [ select ~q paste ]                                     1
(START)  nl [ paste out stop ]                                      (FINAL)
1        0  [ select ~r cut ~p select ~p copy ~q select ~q cut ~r ] 1
1        nl [ paste out stop ]                                      (FINAL)

$ for n in '' 0 00 000 0000 00000 000000 0000000 00000000 000000000; do echo $n | jrte Fibonacci; done

0
0
00
000
00000
00000000
0000000000000
000000000000000000000
0000000000000000000000000000000000
```

In this way the notion of an *algorithm*, whereby a branching and looping series of instructions select data in RAM and mutate application state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into application state. Here *target*, *effector* and *transduction* are analogues of *machine*, *instruction*, *program*. Transducers can nested just as algorithms are nested in calling chains so that complex input patterns can be decomposed and built in an orderly manner, and the range of acceptable inputs includes most context-free patterns, such as XML and JSON. To support this, each transduction process has a transducer stack, so transducers can call other (and themselves, recursively), and an input stack, enabling called transducers to comunicate with callers. The range of input ordinals can be extended with a set of signal ordinals that can be used to generate and interleave a control channel with the source input. Also, and perhaps most importantly, Jrte target effectors (Java methods) have access to RAM. These features enable transucers in the Jrte runtime to effect transforms that are out of range of simple FSTs, as the Fibonacci example above demonstrates (the Fibonacci sequence is strictly context-sensitive).

Ginr compiles input expressions to state-minimized FSTs. The jrte compiler performs another transition-minimizing transformation on compiled automata to coalesce equivalent input symbols so that transducers have maximally compact representations for runtime use. For example, if 10 digit symbols are used equivalently in all states in an FST they can be reduced to a single symbol in the transduction transition matrix. The jrte runtime loads transducers on demand and provides a simple interface for running and controlling transduction processes. 

Compared to Java regex, ginr provides a more robust regular expression compiler and the jrte runtime executes regex-equivalent text transductions with much greater throughput than the Java regex runtime. Below are the results of a benchmarking runoff between Java regex and 3 different jrte transductions (see *Time is Money. RAM is Cheap* in the Javadoc overview to see the regex and ginr expressions compiled and other benchmarking details):

![iptables data extraction from 20MB Linux kernel log](https://github.com/jrte/ribose/blob/master/etc/javadoc/LinuxKernelLog.png)

Jrte is a mature WIP and interested parties are encouraged to use the discussions section here to contact [me](https://github.com/jrte) for support. The general idea is to enable Java developers to embed the jrte runtime and develop transductions for domain-specific targets. The included test transductions use the base target, which provides simple select, cut, copy, paste, clear, input/output and other basic operational effectors that are themselves sufficient for most generic text mining use cases. 

To get started, build ginr and jrte and see the examples below for building a gearbox and running transducers from testing artifacts included in the repo or preparing transducers for other test artifacts you may have available. You will need ginr v2.0.2 to build transducers to use in the jrte runtime. Ginr can be cloned or downloaded from https://github.com/ntozubod/ginr. At present the licensing status of the ginr repo is ambiguous (no license is provided) but, for jrte usage, ginr is required only during your build process, to compile automata to be packaged into a jrte gearbox. There is no need to include any artifacts from the ginr repo or products derived from ginr artifacts in deployed Java applications that include the jrte runtime or gearboxes. I have included a copy of the ginr executable compiled for Linux in the jrte repo to enable Github CI builds to run transduction test cases.

To build ginr, 

```
	mkdir -p ~/bin 
	cd src
	make -f Makefile install
```	

This will install the ginr executable in ~/bin. 

ginr must be on your search PATH before building jrte. To build jrte and related javadoc artifacts, 

```
	ant -f build.xml all-clean
```	

To compile transducers defined in `*.inr` files in a directory using a common prologue `!prologue` to `*.dfa`
files in an `automata` directory and assemble them into a `gearbox` for the base target class `target`,
for example,

```
	automata=build/patterns/automata
	gearbox=build/patterns/Jrte.gears
	target=com.characterforming.jrte.base.BaseTarget
	cat test-patterns/!prologue test-patterns/*.inr | ginr 
	java -cp build/java/jrte-HEAD.jar com.characterforming.jrte.compile.GearboxCompiler --maxchar 128 --target $target $automata $gearbox
```

Any subclass of `BaseTarget` can serve as target class. The `--maxchar` parameter specifies the maximal input ordinal expected in input. The value 128 limits input text to 7-bit ASCII. Use `--maxchar=256` if 8-bit characters are required -- these must be encoded in ginr source files using `\xdd` equivalents. Note that at present 7-bit non-printing and all 8-bit characters must be encoded as `\xdd` equivalents for ginr. While `\x00` can be used as an input character ginr tokens of length >1 containing `\x00` will be truncated.

To use a `transducer` compiled into a `gearbox` to transduce an `input` file, for example (--nil sends an initial !nil signal to start transduction),

```
	transducer=LinuxKernel
	input=test-patterns/inputs/kern.log
	gearbox=build/patterns/Jrte.gears
	cat $input | java -cp build/java/jrte-HEAD.jar com.characterforming.jrte.Jrte --nil $transducer $gearbox
```

To use the jrte runtime to set up and run transductions in a Java application, include jrte-HEAD.jar in classpath. Classes in `com.characterforming.jrte.compile.*` classes are never loaded by the jrte runtime. Application classes import supporting classes from runtime `com.characterforming.jrte` packages and subpackages as required. See related Javadoc and `etc/sh/*.sh` for more details and examples relating to runtime usage. 

To build the Javadoc materials (only) for Jrte, 

```
	ant -f build.xml javadoc
```

See LICENSE for licensing details.
