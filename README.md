# The General Idea
Ribose (formerly jrte) is about inversion of control for high-volume text analysis and information extraction and transformation. It is a ship-in-a-bottle showpiece thrown together to demonstrate what computing might be if finite-state transduction, augmented with a transducer stack and coupled with a classical CPU/RAM computer, was a common modality for processing sequential information (i.e. almost everything except arithmetic). The general idea is to show how to make information work for you rather than you having to work to instruct a computer about how to work with information. Or, at least, how to reduce costs associated with information workflows. The general idea is outlined below and explored, a bit snarkily, in the _[Discussions](https://github.com/jrte/ribose/discussions)_.

<p align="center">
  <img src="https://github.com/jrte/ribose/blob/master/etc/javadoc/api/resources/2-gears-white.jpeg">
</p>

An input source presents as a generator of sequences of ordinal numbers (eg, UNICODE ordinal, quantized analog signal, composite multivariate metric sample). If those sequences have a coherent pattern that can be described in terms of catenation, union and repetition then that description determines a unique regular set. This input pattern can then be extended to interleave at each input ordinal a sequence of output ordinals, which ribose associates with effector methods expressed by a target class bound to the transduction. This input/output pattern description is compiled by [ginr](https://github.com/ntozubod/ginr) to build a state-minimized finite state transducer or FST (ginr is a regular expression compiler that implements a wide range of operations on regular sets).

In this way the notion of an *algorithm*, whereby a branching and looping series of *instructions select data* in RAM and mutate application state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into application state. Here *target*, *effector* and *transduction* are analogues of *machine*, *instruction*, *program*. Most complex patterns can be decomposed into subpatterns, yielding collections of small cooperating transducers that are stitched together on the ribose transduction stack in response to cues in the overall pattern.  Traditional *APIs* describing method or web service interfaces are replaced by syntactic *patterns* describing specific media ccontainers. These patterns are extended by developers to produce collections of transducers that map conformant data streams onto target effectors and reduce and assimilate incoming data into the target domain. 

This is _pattern-oriented programming_ and it offers a novel approach to dealing with serially encoded information. Computing has undergone a torturous evolutionary path since its origins in the Manhatten project and has culminated in the realization that most algorithmic programming languages and the machine code artifacts that they compile to are inherently rife with defects and security issues. Much of this has to do with the fact that modern CPU architectures evolved from programmable calculators and are not well suited for dealing with textual or other sequential information. To address this major software and hardware vendors are pushing for more fine-grained application and service encapsulation and the use of wire protocols such as REST to express APIs and exchange information between services running in encapsulated process spaces. Complex stacks of software are required to mediate between information source and consumer and effectively hide much of the complexity of parsing and building generic object models from source in the consumer domain. 

In that context, a pattern-oriented approach makes sense since it separates syntactic and semantic concerns and eliminates dependency on external software libraries to handle mundane text processing tasks. Development tasks are simplified since the low-level syntactic concerns relating to recognizing features of interest are encoded in the input and removed from the expression of semantic concerns in effector implementations. For example, given a semantically labeled syntactic map of the response to a web service API, developers can reduce it to a simpler syntactic pattern that recognizes only the features of interest and maps these onto patterns of effector invocations that reduce the received response specifically and directly into the receiving process. This obviates a great stack of software libraries that would otherwise be engaged to parse the response generically and build up a more substantial object model including unwanted material.

For text transduction the range of acceptable inputs includes most context-free patterns, such as XML and JSON. To support this, each transduction process has a transduction stack, enabling transducers to call other (and themselves, recursively). The range of input ordinals (0..255) can be extended with a set of signal ordinals, and an input stack enables target effectors to push out-of-band information for immediate transduction, thereby interleaving a synchronous control channel with the input stream. Also, and perhaps most importantly, target and effectors (lightweight Java classes) have access to RAM. These features enable transducers in the ribose runtime to effect transforms that are out of range of simple FSTs, as the Fibonacci example below demonstrates (the Fibonacci sequence is strictly context-sensitive).

Transduction need not be limited to text. Ignoring text ordinals completely, the input domain can be any discrete (or quantized analog) and finite set. For example, for an event-driven system that generates events asynchronously the events can be catagorized and enumerated and used as terms to express a regular pattern as a governing or control plane explicating the real-time behaviour of the system. This pattern can be extended to map to control effectors that effect governance and control where necessary. This would be a wonderful alternative to hand-coding control in a cascading and disjointed series of `switch` statements in Java or C. Ask any developer who has had to decipher such a thing.

At bottom, ribose is about harnessing semi-ring algebra for sequences of text and other sequential media in the same manner that von Neumann's inheritors harnessed numeric algebra for modern computer architecture. Put regular patterns into the picture, right up front. Let a transducer compiled from a stack of regular patterns do all of the sniffing and picking of `char` and let you know precisely when each interesting bit is available for you to assimilate into your application or service domain. Text has always been an afterthought in mainstream computing and it is a great sorrow that the potential for an arithmetic of strings was not realized along with EBCDIC and its inheritors. Working with text with the basic tools (eg, `char`) available in algorithmic programming languages is an abysmally tedious and error-prone task. 

The Powers that Be have gently nudged things this way (SOAP,XML) and that (REST,JSON), always promoting adoption with sophisticated stacks of software libraries that shield application and service developers and consumers from the pain of picking through char[] arrays. Now they want them all in the cloud, encapsulated in tightly-focused microservices that interact over serial channels. They specify their service APIs by presenting examples of what typical requests and responses "look like" and leave you to figure out what their contents "mean". Alternatively, they could specify what they "look like" formally and abstractly as nested regular patterns with semantic labels marking the locations of potentially interesting features.

Such an API presentation would be a perfect map for service consumers to derive a receiving pattern that ignored irrelevant semantic features, identified and extracted only specific features of particular interest to the consumer, and directed assimilation of extracted information into the consumer domain. This would reduce domain dependency on external software to a small component hosting a \[ginr+ribose\]-like transduction stack and make it much easier for developers to extract domain-specific information from responses to remote service API requests and other information sources. Transduction can, in principle, be applied to information directly at the socket layer, eliminating a great deal of CPU churn in the software stack.

Another noticeable feature of most remote service APIs (I'm guessing) is that they do not often allow for fine-grained specificity in selection of information to include in the response but always send back a generic superset for receiver to select from. This seems wasteful but likely saves time and money for the service provider since it can use more generic software to service requests. What might be gained by representing API requests in formal syntactic terms and transducing requests into the service domain? This is a question that I haven't explored with ribose but I think there is great potential for pattern-driven navigation of data stores. I'll leave that as an exercise for myself and the reader for now. 

## Using ginr+ribose
Ginr compiles input expressions to state-minimized FSTs. The ribose model compiler performs another transition-minimizing transformation on compiled automata to coalesce equivalent input symbols so that transducers have maximally compact representations for runtime use. For example, if 10 digit symbols are used equivalently in all states in an FST they can be reduced to a single symbol in the transduction transition matrix. The ribose runtime loads transducers on demand and provides a simple interface for running and controlling transduction processes.

Compared to Java regex, ginr provides a more robust regular expression compiler and the ribose runtime executes complex regex-equivalent text transductions with much greater throughput than the Java regex runtime. Below are the results of a benchmarking runoff between Java regex and 3 different ribose transductions (see ginr [source](https://github.com/jrte/ribose/blob/master/test-patterns/LinuxKernelLog.inr) for the ginr patterns compiled for these):

![iptables data extraction from 20MB Linux kernel log](https://github.com/jrte/ribose/blob/master/etc/javadoc/LinuxKernelLog.png)

Ribose is a toy transduction model specialized for Java to demonstrate how transduction can simplify working with text and other sequential information sources. To begin, start with a ginr pattern. Ginr patterns for ribose are composed from terms that look like this:

```
   (<input>, <effector>[`<token>` ...] ...)
```
where the input pattern on the left-hand side is a regular expression describing a feature in the input with a recognizable end-point (byte or signal) that triggers the invocation of a sequence of effectors, which may or may not be parameterized with a series of back-quoted tokens. These terms are composed in a regular pattern that, on one hand (the left one), describes the input completely to whatever degree of specificity is required to get the job done, and one the other hand (right) specifies the actions to be taken when the delimiting byte or signal is received. 

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

$ for n in '' 0 00 000 0000 00000 000000 0000000 00000000 000000000; do echo $n | ribose Fibonacci; done

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

Ribose transduction patterns are composed (in ginr) in the Unicode `char` domain and ginr maps them into the UTF-8 encoded `byte` domain. Ribose text transductions are driven byte UTF-8 byte streams and extract text features as such. The ribose transduction stack presents a set of basic effectors that are themselves sufficient for many text transduction tasks that stream from standard input to standard output. Ribose users can supplement these by implementing one or more _target_ classes with domain-specific effectors to otherwise assimilate extracted features. Each effector receives from the transduction a view of extracted features and is invoked precisely when feature extraction is complete. These views provide methods to allow effectors to recover features from UTF-8 encodings as Java (Unicode) strings, integers and double-precision floating point numeric values. Each target is a both container for a collection of effectors and a target for effector actions. Targets may interact also with other components outside the scope of trandsduction, as required. You just make it happen.

The ribose component interfaces provide access to the ribose compiler, which assembles ribose transducers from DFAs compiled by ginr into a runtime  model. The model is bound at compile time to an application-defined target class that the compiler instantiates to discover 1 or more effector classes. The model target instantiates its effectors and presents them to the compiler for parameter validation. Effectors are simple, presenting only a parameterless `invoke()` (apply effect to target) method to transductions, or are parameterized by a generic type `T` and present additional methods `newParameters(int count)` (->instantiate T\[count\]), `compileParameter(int ordinal, byte[][] bytes)` (->T\[ordinal\] = new T(bytes\[\]\[\])) and `invoke(int ordinal)` (->use T[ordinal] to apply effect to target). The byte arrays presented to be compiled are the sequences of [`tokens`] discovered in association with effector references in any of the transducers compiled into the model. 

Ginr encodes backquoted tokens as mixed UTF-8 byte encodings of 16-bit Unicode characters and unprintable bytes (represented as \xHH when embedded in tokens). What you see between \[\] in `effector[with parameters]` is what the effector will receive as mixed UTF-8 and binary bytes with `compileParameter(int, byte[][])`. The effector can use the static `Bytes.decode(byte[])` method to recover `String` representations, and anything can be constructed from a string. So that's all good. Maybe even better with 3 or 4 strings. As many as are required, they can be acquired for free by including them in a ribose pattern for ginr. 

A compiled ribose model includes the transducers assembled from ginr DFAs, a model target, the target's effectors, and their parameter `byte[][]`s. Additionally, it enumerates some special artifacts that may be referenced in ribose patterns. Tokens of the form `!signal` are signals that can be raised using the built-in in\[`!signal`\] effector. Transducers (referenced as `@name`) select[`~value`] and then `paste` bytes extracted from input or paste\[`some embellishment`\], all of which will be appended to the named `~value` to be assimilated into the target when complete. Any number of signals and values can be defined, just give them right form of name and reference then in ribose patterns. They're free too. 

So there are these named transducers and signals and values floating around and the static `Ribose.compileRuntimeModel()` method binds them all together with a target, effectors and effector parameters in a single model. The `Ribose.loadRuntimeModel(File modelFIle, ITarget modelTarget)` method, also static,loads a model file and goes through parameter binding with the model target. This is not a live target, it is only used to instantiate model effectors for parameter compilation when the model loads. When the model is completely loaded, `IRiboseRuntime.newTransduction(ITarget liveTarget)` will clone precompiled parameter objects from model to live effectors and  bind live target and effectors to a runtime transduction stack. `ITransduction` provides a simple interface for controlling runtime transductions. 

The base target effectors are:

| Effector[`Parameter` ... ] | Description |
| --------------------------- |:----------- |
| 0 | Null effector halts and throws DomainException |
| 0 | Nil effector does nothing (no-op) |
| paste | Extend the current selection by appending the current input character |
| paste[`text`] | Extend the current selection by appending text |
| select | Select the anonymous value as current selection |
| select[`~name`] | Select a named value as current selection |
| copy | Extend the current selection by appending the anonymous named value |
| copy[`~name`] | Extend the current selection by appending and clearing a named value |
| cut | Extend the current selection by appending and clearing the anonymous named value |
| cut[`~name`] | Extend the current selection by appending a named value and clear the named value |
| clear | Clear the current selection |
| clear[`~name`] | Clear the named value |
| count[`digit+` `!signal`] | Set up a counter signal and initial counter value |
| count | Decrement the counter and push `!signal` onto the input stack when result is 0 |
| in | Push current selection onto the input stack |
| in[...] | Push onto the input stack a signal or a sequence of mixed text and named values |
| out | Write value of current selection to System.out |
| out[...] | Write a sequence of mixed text and named values to System.out |
| mark | Mark a position in the current input |
| reset | Resets the input to the last mark |
| start[`@transducer`] | Push a named transducer onto the transducer stack |
| pause | Pause the transduction loop and return from run() method |
| stop | Stop the current transducer and pop the transducer stack |

Classes that implement `ITarget` must provide a nullary constructor that will instantiate a model target and present its model effectors to the ribose compiler and runtime. The model compiler instantiates and binds the model target and effector instances to a model `ITransduction` instance. Model effector instances must be capable of compiling parameters but and are never otherwise invoked in compilation context. In the runtime each invocation of a parameterized effectoc receives an ordinal number that selects a precompiled parameter to apply. 

## Building and Running Ribose Transducers
To get started, build ginr and ribose and see the examples below for building a model and running transducers from testing artifacts included in the repo. You may then want to prepare transducers for other test artifacts you may have available. You will need ginr v2.1.0c or greater to build transducers to use in the ribose runtime. Ginr can be cloned or downloaded from https://github.com/ntozubod/ginr. I have included a copy of the ginr executable compiled for Linux in the ribose repo to enable Github CI builds to run transduction test cases. Also included is a whitepaper providing a detailed presentation of ginr with some examples illustrating usage of the available operators. These artifacts can be found [here](https://github.com/jrte/ribose/tree/master/etc/ginr). More current information can be found at the [ginr repo](https://github.com/ntozubod/ginr).

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
ginr must be on your search PATH before building ribose. To build ribose and related test artifacts,

```
	ant -f build.xml all-clean
```

Transducers are defined in ginr source files (`*.inr`), and compiled FSTs to are saved to DFA (`*.dfa`) files in an intermediate build directory  (`:save ...` in ginr source). It is helpful to define a base set of common artifacts in a prologue. To compile the complete suite of transducers pipe the catenation of prologue with `*.inr` source files into ginr. Then run the ribose compiler as shown below to assemble FSTs into a model for runtime use with its associated target.

```
	automata=build/patterns/automata
	model=build/patterns/BsaeTarget.model
	target=com.characterforming.jrte.base.BaseTarget
	cat test-patterns/!prologue test-patterns/*.inr | ginr
	etc/sh/compile --target $target $automata $model
```

Any class that implements `ITarget` can serve as target class. Typically, the target implementation marshals snippets of data (`INamedValue` wrappers for byte arrays) extracted from transduction input by its effectors into domain-specific objects for application or service use. Composite targets, composed of a main target that presents to the ribose compiler and runtime a collection of effectors from some number of component `ITarget` instances, are also possible. In any case, the target class determines what information to present to its effectors when they are instantiated. Effector implementations should be _stateless_, holding only references to statically-bound objects (normally only a reference to the target that expresses them). Parameterized effectors will also have their parameter arrays, which are protected in their BaseParamaterizedEffector superclass. 

To use a `transducer` compiled into a `model` to transduce an `input` file, for example:

```
	transducer=LinuxKernelBytes,decode
	input=test-patterns/inputs/kern.log
	model=build/patterns/BaseTarget.model
	etc/sh/ribose --nil $transducer $input $model
```

The entire file contents will be loaded into RAM and presented to the transduction as a single `byte[]` array (see issue #6 to see why this is necessary, for now). The `--nil` option presents an initial `!nil` signal to the transduction, before presenting input file contents. This can be used for one-time initialization as may be required in some cases, eg when the start state would otherwise belong in a loop.

To use the ribose runtime to set up and run transductions in a Java application, include ribose-0.0.0.jar in classpath. No supporting external jars are required. See related Javadoc and `etc/sh/*.sh` for more details and examples relating to compilation and runtime use of ribose models for tranduction.

To build the Javadoc materials (only) for ribose,

```
	ant -f javadoc.xml javadoc
```

The current version of ribose is packaged in ribose-0.0.0.jar.

See [Discussions](https://github.com/jrte/ribose/discussions) for stories and caveats.

See [LICENSE](https://github.com/jrte/ribose/blob/master/LICENSE) for licensing details.
