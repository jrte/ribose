Jrte is about inversion of control for high-volume text analysis and information extraction and transformation. It is a ship-in-a-bottle showpiece thrown together to demonstrate what computing might be if finite-state transduction, augmented with a transducer stack and coupled with a classical CPU/RAM computer, was a common modality for processing sequential information (i.e. almost everything except arithmetic). The general idea is to show how to make information work for you rather than you having to work to instruct a computer about how to work with information. Or, at least, how to reduce costs associated with information workflows. The general idea is outlined below and explored, a bit snarkily, in the _[Discussions](https://github.com/jrte/ribose/discussions)_.

<p align="center">
  <img src="https://github.com/jrte/ribose/blob/master/etc/javadoc/api/resources/2-gears-white.jpeg">
</p>

An input source presents as a generator of sequences of ordinal numbers (eg, UNICODE ordinal, quantized analog signal, composite multivariate metric sample). If those sequences have a coherent pattern that can be described in terms of catenation, union and repetition then that description determines a unique regular set. This input pattern can then be extended to interleave at each input ordinal a sequence of output ordinals, which Jrte associates with effector methods expressed by a target class bound to the transduction. This input/output pattern description is compiled by [ginr](https://github.com/ntozubod/ginr) to build a state-minimized finite state transducer or FST (ginr is a regular expression compiler that implements a wide range of operations on regular sets).
```
Fibonacci = (
   # fib(0): ~r, ~q, ~p ∈ 0* preset to empty string 𝛆, congruent with 𝟎 in 𝝢
   (
      # fib(1): ~q <- 0
      ('0', select[`~q`] paste)
      # fib(n>1): cycle (~r) <- (~q) <- (~p) <- (~p)(~r), (~r) <- 𝛆
      ('0', select[`~r`] cut[`~p`] select[`~p`] copy[`~q`] select[`~q`] cut[`~r`])*
   )?
   # (~q) is selected and holds fib(n) 0s, so append nl and print result
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

In this way the notion of an *algorithm*, whereby a branching and looping series of *instructions select data* in RAM and mutate application state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into application state. Here *target*, *effector* and *transduction* are analogues of *machine*, *instruction*, *program*. Most complex patterns can be decomposed into subpatterns, yielding collections of small cooperating transducers that are stitched together on the jrte transduction stack in response to cues in the overall pattern. 

The range of acceptable inputs includes most context-free patterns, such as XML and JSON. To support this, each transduction process has a transduction stack, enabling transducers to call other (and themselves, recursively). The range of input ordinals can be extended with a set of signal ordinals, and an input stack enables target effectors to push out-of-band information for immediate transduction, thereby interleaving a synchronous control channel with the input stream. Also, and perhaps most importantly, Jrte target effectors (Java methods) have access to RAM. These features enable transducers in the Jrte runtime to effect transforms that are out of range of simple FSTs, as the Fibonacci example above demonstrates (the Fibonacci sequence is strictly context-sensitive).

Ginr compiles input expressions to state-minimized FSTs. The jrte compiler performs another transition-minimizing transformation on compiled automata to coalesce equivalent input symbols so that transducers have maximally compact representations for runtime use. For example, if 10 digit symbols are used equivalently in all states in an FST they can be reduced to a single symbol in the transduction transition matrix. The jrte runtime loads transducers on demand and provides a simple interface for running and controlling transduction processes.

Compared to Java regex, ginr provides a more robust regular expression compiler and the jrte runtime executes regex-equivalent text transductions with much greater throughput than the Java regex runtime. Below are the results of a benchmarking runoff between Java regex and 3 different jrte transductions (see ginr [source](https://github.com/jrte/ribose/blob/master/test-patterns/LinuxKernelLog.inr) for patterns compiled for these, also see *Time is Money. RAM is Cheap* in the Javadoc overview for other benchmarking details):

![iptables data extraction from 20MB Linux kernel log](https://github.com/jrte/ribose/blob/master/etc/javadoc/LinuxKernelLog.png)

## Using Jrte

Jrte is a mature WIP and interested parties are encouraged to use the discussions section here to contact [me](https://github.com/jrte) for support. The general idea is to enable Java developers to embed the jrte runtime and develop transductions for domain-specific targets. The included test transductions use the base target, which provides simple select, cut, copy, paste, clear, in, out and other basic operational effectors that are themselves sufficient for most generic text mining use cases. Java developers can roll their own domain-specific targets with new effectors by extending `BaseTarget` or any subclass and implementing `ITarget.bind()` to return an array enumerating the new effectors. The new effectors as well as the any effector inherited from superclasses can then be used in domain-specific patterns, compiled to transducers and archived to the target gearbox (note: the relationship between target and gearbox is  one-to-one).

The base target effectors are:

| Effector[`Parameter` ...] | Description |
| --- | --- |
|start[`@transducer`]|Push a named transducer onto the transducer stack|
|stop|Stop the current transducer and pop the transducer stack|
|shift[`@transducer`]|Replace current transducer on top of transducer stack with a named transducer|
|select|Select the anonymous value as current selection|
|select[`~name`]|Select a named value as current selection|
|paste|Extend the current selection by appending the current input character|
|paste[`text`]|Extend the current selection by appending text|
|copy[`~name`]|Extend the current selection by appending a named value|
|cut[`~name`]|Extend the current selection by appending a named value and clear the named value|
|clear|Clear the current selection|
|clear[`~name`]|Clear the named value|
|counter[`!signal` `digit+`]|Set up a counter signal and initial counter value|
|count|Decrement the counter and push `!signal` onto the input stack when result is 0|
|in|Push current selection onto the input stack|
|in[...]|Push onto the input stack a signal or a sequence of mixed text and named values|
|mark|Mark a position in the current input|
|reset|Resets the input to the last mark|
|end|Pops the input stack|
|out|Write value of current selection to System.out|
|out[...]|Write a sequence of mixed text and named values to System.out|

Each `ITarget` implementation must provide a nullary constructor. The gearbox compiler will instantiate and bind() a target instance to a null `Transduction` to obtain the target effectors to compile to and will use these to verify parameters used in compiled ginr source; these effector instances must be capable of compiling parameters in the absence of an associated runtime `Transduction` instance. Effector parameters in jrte patterns are always presented to ginr as a series of tokens with backquotes, which may include binary `\xHH` bytes. These are compiled to an array of byte arrays for each parameterized effector call in ginr source.

In the jrte runtime the collection of parameters associated with each effector instance is enumerated. When the target is bound to a transduction runtime will call the each effector's `newParameters(int)` method to indicate the number of enumerated parameters. The effector is expected to instantiate an array of specified capacity to receive compiled parameter objects.  It will then call each effector's `setParameter(int, byte[][])` once method for each enumerated parameter passing the parameter's enumerator and an array of byte arrays to be used to construct a parameter object. The `BaseParameterizedEffector.decodeParameter(byte[])` method is available to decode UTF-8 bytes in parameter lists to `char[]` array, which can be wrapped as `String` if required for parameter object construction.

When binding is complete each parameterized effector has an array of parameter objects indexed by parameter enumerators. At runtime, a parameter enumerator is passed to the effector's `invoke(int)` method to select the parameter object to be used in the call.

The gearbox produced by the jrte gearbox compiler includes collections of compiled transducers, value names, and signals. The names selected for these artifacts must be prefixed by a special symbol, eg `@Transducer`, `~NamedValue`, `!Signal`, which acts as a poor man's namespace. The intention is to ensure that these names do not clash with effector names or input tokens, so that each tape (`(input-tape, effector-tape[parameter-tape])`) has symbols that are disjoint with respact to other tapes. This allows compiled transducers to be edited in ginr, for example, to harden them against domain errors (input with no defined transition is current state). See the `Tintervals` transducer in the test suite to see how this is done. (Note: Transducer, value, and signal names are always enclosed with backquotes (impossible to show in markdowm, afaik) when they are used on the parameter tape. To reference a signal on the input tape, remove the `!` prefix, for example, (`nul`, select[`~error`] start[`@HandleDomainError`]).

In the current implementation of the jrte gearbox, all named artifacts are global. This was a poor design decision that will present problems in multithreaded contexts. In future revisions mutable named values will be localized to each `Transduction` instance. Transducers and signals are immutable in the jrte runtime. 

## Building and Running Jrte Transducers

To get started, build ginr and jrte and see the examples below for building a gearbox and running transducers from testing artifacts included in the repo. You may then want to prepare transducers for other test artifacts you may have available. You will need ginr v2.1.0c or greater to build transducers to use in the jrte runtime. Ginr can be cloned or downloaded from https://github.com/ntozubod/ginr. I have included a copy of the ginr executable compiled for Linux in the jrte repo to enable Github CI builds to run transduction test cases. Also included is a whitepaper providing a detailed presentation of ginr with some examples illustrating usage of the available operators. These artifacts can be found [here](https://github.com/jrte/ribose/tree/master/etc/ginr). More current information can be found at the [ginr repo](https://github.com/ntozubod/ginr).

To build ginr,

```
	mkdir -p ~/bin
	pushd src
	make -f Makefile install
	popd
```

This will install the ginr executable in ~/bin. It can be run interactively (w/o line-edit) or from a catention of files into stdin. Single- or multi-tape expressions are compiled on receipt of a semicolon(;). Compiled expressions can be assigned to variables and referenced in other expressions for inline inclusion. 

```
>~/bin/ginr
# :help;

Copyright (C) 1988 J Howard Johnson

Operations by priority (highest to lowest):

+   *   ?               postfix operators for 1 or more, 0 or more, 0 or 1
<concatenation>         no explicit operator
\   /                   left factor, right factor
&   -                   intersection, set difference
|   !   ||  !!          union, exclusive or, elseor, shuffle
$                       project
@   @@                  composition, join
<all colon operators>   see :help colonops; for details
,                       Cartesian product within (), union within {}

All operators associate from left to right.
Parentheses may be used to indicate a specific order of evaluation.
{,,,} is a set constructor.
(,,,) is a tuple construtor.
[   ] is the tape-shifting operator
'   ' is a string of single letter tokens.
`   ` is a token containing arbitrary symbols.
^     indicates the empty word (or an explicit concatenation operator).


Colon operations (postfix operators at lowest priority)

Transformation Operators               Displaying Operators
:acomp      Active complement          :card       Print cardinality
:alph       Active alphabet            :enum       Enumerate language
:clsseq     Subsequential closure      :length     Display min word length
:comp       Complement w.r.t. SIGMA*   :pr         Display automaton
:lenmin     Words of min length        :prsseq     Subsequential display
:pref       Set of prefixes            :report     Display report line
:rev        Reverse                    :stems #    Print tape # stems
:sseq       Subsequential transducer
:LMsseq     LM Subsequential transducer
:GMsseq     GM Subsequential transducer
:suff       Set of suffixes            Coercing operators
:<number>   Concatenation power        :update :nfa :trim :lameq
:(<number>) Composition power          :lamcm :closed :dfa :dfamin

:enum may take an optional argument to specify the quantity of output.


IO operations

:pr <filename>        Postfix operator to display automaton into a file
:save <filename>      Postfix operator to save automaton in compressed form
:load <filename>      Operator without left argument to get value from a file
:readwords <filename> Operator with no argument to load a word file

:get <variable>       Operator with no arguments to get value from a variable

<var> = :load;        Short for <var> = :load <var>;
:save <var>;          Short for <var> :save <var>;
```
ginr must be on your search PATH before building jrte. To build jrte and related javadoc artifacts,

```
	ant -f build.xml all-clean
```

Transducers are defined in ginr source files (`*.inr`), and compiled FSTs to are saved to DFA (`*.dfa`) files in an intermediate build directory  (`:save ...` in ginr source). It is helpful to define a base set of common artifacts in a prologue. To compile the complete suite of transducers pipe the catenation of prologue with `*.inr` source files into ginr. Then run the jrte compiler as shown below to assemble FSTs into a gearbox for runtime use with its associated target.

```
	automata=build/patterns/automata
	gearbox=build/patterns/Jrte.gears
	target=com.characterforming.jrte.base.BaseTarget
	cat test-patterns/!prologue test-patterns/*.inr | ginr
	java -cp build/java/jrte-HEAD.jar com.characterforming.jrte.compile.GearboxCompiler --maxchar 128 --target $target $automata $gearbox
```

Any subclass of `BaseTarget` can serve as target class. The `--maxchar` parameter specifies the maximal input ordinal expected in input. The value 128 limits input text to 7-bit ASCII. To transduce UTF-8 text in the encoded domain use `--maxchar=256`. Also, while ginr recognizes tokens enclosed in `back-quotes` as first-order atomic entities (like 'A'), and these identifiers may include `\xHH` bytes, inclusion of `\x00` will force truncation. However, jrte will recognize the stand-alone `\x00` token as a null byte in compiled ginr automata.

To use a `transducer` compiled into a `gearbox` to transduce an `input` file, for example:

```
	transducer=LinuxKernel
	input=test-patterns/inputs/kern.log
	gearbox=build/patterns/Jrte.gears
	java -cp build/java/jrte-HEAD.jar com.characterforming.jrte.Jrte --nil $transducer $input $gearbox
```

The entire file contents will be loaded into RAM and presented to the transduction as a single `char[]` array (see issue #6 to see why this is necessary, for now). The `--nil` option presents an initial `!nil` signal to the transduction, before presenting input file contents. This can be used for one-time initialization as may be required in some cases, eg when the start state would otherwise belong in a loop.

To use the jrte runtime to set up and run transductions in a Java application, include jrte-HEAD.jar in classpath. Classes in `com.characterforming.jrte.compile.*` classes are never loaded by the jrte runtime. Application classes import supporting classes from runtime `com.characterforming.jrte` packages and subpackages as required. See related Javadoc and `etc/sh/*.sh` for more details and examples relating to runtime use.

To build the Javadoc materials (only) for Jrte,

```
	ant -f build.xml javadoc
```

See [Discussions](https://github.com/jrte/ribose/discussions) for stories and caveats.

See [LICENSE](https://github.com/jrte/ribose/blob/master/LICENSE) for licensing details.
