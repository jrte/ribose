[To skip this long screed and learn how to build and work with ribose jump to the [Disclaimer](#disclaimer) at the end. Or check [this](https://www.cis.upenn.edu/~alur/Icalp17.pdf) out. Honestly, I just now stumbled upon [Rajeev Alur](https://en.wikipedia.org/wiki/Rajeev_Alur) and streaming string transducers. I built my first implementation, with INR and C function pointers, in the late 1980s at Bell Northern Research in Ottawa. Been watching the world stumble by without it ever since, encumbering serialized forms of even the simplest object models with all manners of [ill-fitting suits](https://en.wikipedia.org/wiki/Comparison_of_data_serialization_formats). Why don't modern programming languages and computing machines have built in support for semirings and automata?]
# The General Idea
Ribose (formerly jrte) is about inversion of control for high-volume text analysis and information extraction and transformation in general. Many stream-oriented tasks, such as cleaning and extraction for data analytic workflows, involve recognizing and acting upon features embedded, more or less sparsely, within a larger context. Software developers receive some onerous help in that regard from generic software libraries that support common document standards (eg, XML, JSON, MS Word, etc.), but dependency on these libraries adds complexity, vulnerability and significant runtime costs to software deployments. And these libraries are of no use at all when information is presented in idiomatic formats that require custom software to deserialize.

Ribose specializes _[ginr](https://github.com/ntozubod/ginr/blob/main/doc/intro_1988/inr_intro.md)_, an industrial strength open source compiler for multidimensional regular patterns, to produce finite state transducers (FSTs) that map syntactic features to semantic effector methods expressed by a target class. Ribose transduction patterns are composed and manipulated using algebraic (semiring) operators and compiled to FSTs for runtime deployment. Regular patterns may be nested to cover context-free inputs, and the ribose runtime supports unbounded lookahead to resolve ambiguities or deal with context-sensitive inputs. Inputs are presented to ribose runtime transducers as streams of byte-encoded information and regular or context-free inputs are transduced in linear time.

There is quite a lot of byte-encoded information being passed around these days (right-click in any browser window and "View Page Source" to see a sample) and it is past time to think of better ways to process this type of data than crunching it on instruction-driven calculator machines. Ribose and ginr promote a pattern-oriented, data driven approach to designing, developing and processing information workflows. Ribose is a ship-in-a-bottle showpiece put together to shine a spotlight on ginr and to demonstrate what computing might be if finite state transduction, augmented with a transducer stack and coupled with a classical CPU/RAM computer, was a common modality for processing sequential information (i.e., almost everything except arithmetic).

The general idea is to show how to make information work for you rather than you having to work to instruct a computer about how to work with information. Or, at least, how to reduce costs associated with information workflows. This idea outlined below and explored, a bit snarkily, in the stories posted in the _[ribose wiki](https://github.com/jrte/ribose/wiki)_. This has no connection whatsoever with POSIX and Perl 'regular expressions' (regex) or 'pattern expression grammars' (PEGs), that are commonly used for ad hoc pattern matching. In the following I refer to the algebraic expressions used to specify ribose transducers as 'regular patterns' to distinguish them from regex and PEG constructs.
## An Overview an Example and Some History
Regular patterns and automata are to computing ecosystems what soil and microbiota are to the stuff living above ground. Strange that we don't see explicit support for their construction and runtime use in modern programming languages and computing machines. Ribose is presented only to demonstrate the general idea of pattern-oriented design and development. It successfully runs a limited suite of test cases and it can be used to build domain-specific ribose models, but it is not regularly maintained nor suitable for general use. Others are encouraged to clone and improve it or implement more robust expressions of the general idea. **Rust**, anyone?

Ribose suggests a pattern-oriented approach to information that minimizes dependency on external libraries and could reduce complexity, vulnerability and development and runtime costs in information workflows. Ribose generalizes the *transducer* design pattern that is commonly applied to `filter`, `map` and `reduce` collections of data in functional programming paradigms. Common usage of this design pattern treats the presentation of inputs as a simple series **T\*** without structure. Ribose extends and refines this design pattern, allowing transducers to precisely navigate (filter), map (select effect vector) and reduce (execute effect vector) complex information under the direction of syntactic cues in the input.

Here the `filter` component is expressed as a collection of nested regular patterns describing an input source, using symbolic algebraic expressions to articulate the syntactic structure of the input. These unary input patterns are then extended to binary transduction patterns that `map` syntactic features to effect vectors that incrementally `reduce` information extracted from the input. The syntactic structure provides a holistic navigable map of the input and exposes cut points where semantic actions should be applied. This presents a clear separation of syntactic and semantic concerns: Syntax is expressed in a purely symbolic domain where patterns are described and manipulated algebraically, while semantics are expressed poetically in a native programming language as effectors in a domain-specific target class. Syntactic patterns are articulated without concern for target semantics and effector implementation of semantic actions is greatly simplified in the absence of syntactic concerns.

The ribose runtime operates multiple concurrent transductions, each encapsulated in a `Transductor` object that provides a set of core compositing and control effectors and coordinates a transduction process. Nested FSTs are pushed and popped on transductor stacks, with two-way communication between caller and callee effected by injecting information for immediate transduction. Incremental effects are applied synchronously as each input symbol is read, culminating in complete reduction and assimilation of the input into the target domain. For regular and most context-free input patterns transduction is effected in a single pass without lookahead. Context-sensitive or ambiguous input patterns can be classified and resolved with unbounded lookahead `(select clear paste* in)` or backtracking `(mark reset)` using core transductor effectors.

The ribose runtime transduces `byte*` streams simply and only because `byte` is the least common denominator for data representation in most networks and computing machines. Ginr compiles complex Unicode glyphs in ribose patterns to multiple UTF-8 byte transitions, so all ribose transductions are effected in the byte domain and only extracted features are decoded and widened to 16-bit Unicode code points. Binary data can be embedded with text (eg, **\`a\x00b\`**), using self-terminating binary patterns or prior length information, as long as they are distinguishable from other artifacts in the information stream. Raw binary encodings may be especially useful in domains, such as online gaming or real-time process control, that demand compact and efficient messaging protocols with relaxed readability requirements. Semantic effectors may also inject previously captured bytes or out-of-band signals, such as countdown termination, into the input stream to direct the course of transductions.
### _An Example_
Here is a simple example, taken from the ribose transducer that reduces the serialized form of compiled ginr automata to construct ribose transducers. The input pattern is simple and unambiguous and is easily expressed:
```
header = 'INR' (digit+ tab):4 digit+ nl; # a fixed alphabetic constant, 4 tab-delimited unsigned integers and a final unsigned integer delimited by newline
transition = (digit+ tab):4 byte* nl;    # 4 tab-delimited unsigned integers followed by a sequence of bytes of length indicated by the 4th integer, ending with newline
automaton = header transition*;          # the complete automaton
```
The `automaton` pattern is extended to check for a specific tag and marshal 5 integer fields into an immutable `Header` record and an array of `Transition` records. Fields are extracted to raw `byte[]` arrays using the `clear`, `select` and `paste` effectors until a newline triggers a domain-specific `header` or `transition` effector to decode and marshal them into the `Header` and `Transition` records. Finally the transitions are reduced in the `automaton` effector to a 259x79 transition matrix, which the ribose compiler will reduce to a 13x27 transition matrix by coalescing equivalent inputs symbols (eg, digits in this scenario) using the equivalence relation on the input domain induced by the ginr transition matrix.
```
Sign = ('-', paste)?;
Number = (digit, paste)+;
Symbol = (byte, paste count)* eol;
Field = select[X] clear;
Eol = cr? nl;
Inr = 'INR';

Automaton = nil? (
# header
  (Inr, Field@(X,`~version`)) Number
  (tab, Field@(X,`~tapes`)) Number
  (tab, Field@(X,`~transitions`)) Number
  (tab, Field@(X,`~states`)) Number
  (tab, Field@(X,`~symbols`)) Number
  (Eol, header (Field@(X,`~from`)))
# transitions
  (
    Number
    (tab, Field@(X,`~to`)) Number
    (tab, Field@(X,`~tape`)) Sign? Number
    (tab, Field@(X,`~length`)) Number
    (tab, (Field@(X,`~symbol`)) count[`~length` `!eol`]) Symbol
    (Eol, transition (Field@(X,`~from`)))
  )*
# automaton
  (eos, automaton stop)
):dfamin;

Automaton$(0,1 2):prsseq `build/compiler/Automaton.pr`;
```
The final `prsseq` operator verifies that the `Automaton$(0, 1 2)` automaton is a single-valued _function_ mapping the input semiring into the semiring of effectors and effector parameters. The branching and repeating patterns expressed in the input syntax drive the selection of non-branching effect vectors, obviating much of the fine-grained control logic that would otherwise be expressed in line with effect in a typical programming language, without support from an external parsing library. Most of the work is performed by transductor effectors that latch bytes into named fields that, when complete, are decoded and assimilated into the target domain by a tightly focussed domain-specific effector.

Expressions such as this can be combined with other expressions using concatenation, union, repetition and composition operators to construct more complex patterns. More generally, ribose patterns are amenable to algebraic manipulation in the semiring, and ginr enables this to be exploited to considerable advantage. For example, `Transducer = Header Transition* eos` covers a complete serialized automaton, `Transducer210 = ('INR210' byte* eos) @@ Transducer` restricts `Transducer` to accept only version 210 automata (ginr's `@` composition operator absorbs matching input and reduces pattern arity, the `@@` join operator retains matching input and preserves arity).

In a nutshell, _algorithms_ are congruent to _patterns_. The _logic_ is in the _syntax_.
### _Ginr_`*`
Ginr is the star of the ribose circus. It was developed by J Howard Johnson at the University of Waterloo in the early 1980s. One of its first applications was to [transduce the typesetting code for the Oxford English Dictionary](https://cs.uwaterloo.ca/research/tr/1986/CS-86-20.pdf) from an archaic layout to SGML. I first used it at Bell Northern Research to implement a ribose-like framework to serve in a distributed database mediation system involving diverse remote services and data formats. The involved services were all driven by a controller transducing conversational scripts from a control channel. The controller drove a serial data link, transmitting queries and commands to remote services on the output channel and switching context-specific response transducers onto the input channel. Response transducers reduced query responses to SQL statements for the mediator and reduced command responses to guidance to be injected into the control channel to condition the course of the ongoing conversation.

Ginr subsequently disappeared from the public domain and has only recently been [published](https://github.com/ntozubod/ginr) with an open source license on GitHub. It has been upgraded with substantial improvements, including 32-bit state and symbol enumerators and compiler support for transcoding Unicode symbols to UTF-8 bytes. It provides a [full complement of algebraic operators](https://github.com/jrte/ribose/wiki/Ginr) that can be applied to reliably produce very complex (and very large) automata. Large and complex patterns can be decomposed into smaller and simpler patterns, compiled to FSTs, and reconstituted on ribose runtime stacks, just as complex procedural algorithms in Java are decomposed into simpler methods that Java threads orchestrate on call stacks in the JVM runtime.
## Basic Concepts
Ginr operates in a symbolic domain involving a finite set of symbols and algebraic semiring operators that recombine symbols to express syntactic patterns. Support for Unicode symbols and binary data is built in, and Unicode in ginr source patterns is rendered as UTF-8 byte sequences in compiled automata. UTF-8 text is transduced without decoding and extracted bytes are decoded only in target effectors. Ribose transducer patterns may introduce additional atomic symbols as tokens representing out-of-band (>255) control signals.

Input patterns are expressed in `{byte,signal}*` semirings, and may involve UTF-8 and binary bytes from an external source as well as control signals interjected by target effectors. Ribose transducer patterns are expressed in `(input,effector,parameter)*` semirings, mapping input patterns onto parameterized effectors expressed by domain-specific target classes. They identify syntactic features of interest in the input and apply target effectors to extract and assimilate features into the target domain.

A ribose model is associated with a target class and is a container for related collections of transducers, target effectors, static effector parameters, control signals and field registers for accumulating extracted bytes. The `ITransductor` implementation that governs ribose transductions provides a base set of effectors to
- extract and compose data in selected fields *(`select, paste, copy, cut, clear`)*,
- count down from preset value and signal end of countdown *(`count`)*
- push/pop transducers on the transduction stack *(`start, stop`)*,
- mark/reset at a point in the input stream *(`mark, reset`)*,
- inject input for immediate transduction *(`in, signal`)*,
- or write extracted data to an output stream *(`out`)*.

All ribose models implicitly inherit the transductor effectors, along with an extensible set of control signals `{nul,nil,eol,eos}` and an anonymous field that is preselected for every transduction and reselected when `select` is invoked with no parameter. New signals and fields referenced in transducer patterns implicitly extend the base signal and field collections. Additional effectors may be defined in specialized `ITarget` implementation classes.

The ribose transductor implements `ITarget` and its effectors are sufficient for most ribose models that transduce input to standard output via the `out[...]` effector. Domain-specific target classes may extend `SimpleTarget` to express additional effectors, typically as inner classes specializing `BaseEffector<Target>` or `BaseParameterizedEffector<Target,ParameterType>`. All effectors are provided with a reference to the containing target instance and an `IOutput` view for extracting fields as `byte[]`, integer, floating point or Unicode `char[]` values, typically for inclusion in immutable value objects that are incorporated into the target model.

Targets need not be monolithic. In fact, every ribose transduction involves a composite target comprised of the transductor and at least one other target class (eg, `SimpleTarget`). In a composite target one target class is selected as the representative target, which instantiates and gathers effectors from subordinate targets to merge with its own effectors into a single collection to merge with the transductor effectors. Composite targets allow separable concerns within complex semantic domains to be encapsulated in discrete interoperable and reusable targets. For example, a validation model containing a collection of transducers that syntactically recognize domain artifacts would be bound to a target expressing effectors to support semantic validation. The validation model and target, supplied by the service vendor, can then be combined with specialized models in receiving domains to obtain a composite model including validation and reception models and effectors. With some ginr magic receptor patterns can be joined with corresponding validator patterns to obtain receptors that validate in stepwise synchrony with reception and assimilation into the receiving domain.

Perhaps not today but Java service vendors with a pattern orientation could do a lot to encourage and streamline service uptake by providing transductive validation models containing _patterns_ describing exported domain artifacts along with validation models and targets. In consumer domains, the vendor patterns would provide concise and highly readable syntactic and semantic maps of the artifacts in the vendor's domain. Here they can serve as starting points for preparing specialized receptor patterns that call out to effectors in consumer target models, and these patterns can be joined with the service validation patterns as described above. Service vendors could also include in their validation models transducers for rendering domain artifacts in other forms, eg structured text or some or other markup language, allowing very concisely serialized artifacts to be comprehensible without sacrificing brevity and efficient parsing.
# Everything is Code
In computing ecosystems regular patterns and their equivalent automata, like microbiota in biological ecosystems, are ubiquitous and do almost all of the work. String them out on another construct like a stack or a spine and they can perform new tricks.

Consider ribonucleic acid (RNA), a strip of sugar (ribose) molecules strung together, each bound to one of four nitrogenous bases (A|T|G|C), encoding genetic information. Any ordered contiguous group of three bases constitutes a *codon*, and 61 of the 64 codons are mapped deterministically onto the 21 amino acids used in protein synthesis (the other three are control codons). This mapping is effected by a remarkable molecular machine, the *ribosome*, which ratchets messenger RNA (mRNA) through an aperture to align the codons for translation and build a protein molecule, one amino acid at a time (click on the image below to see a real-time animation of this process). Over aeons, nature has programmed myriad mRNA scripts and compiled them into DNA libraries to be distributed among the living. So this trick of using sequential information from one domain (eg, mRNA->codons) to drive a process in another domain (amino acids->protein) is not new.

<p align="center">
  <a href="https://www.youtube.com/watch?v=TfYf_rPWUdY"><img src="https://github.com/jrte/ribose/raw/master/etc/markdown/bicycle-gears.png"></a>
</p>

For a more recent example, consider a **C** function compiled to a sequence of machine instructions with an entry point (call) and maybe one or more exit points (return). This can be decomposed into a set of vectors of non-branching instructions, each terminating with a branch (or return) instruction. These vectors are ratcheted through the control unit of a CPU and each sequential instruction is decoded and executed to effect specific local changes in the state of the machine. Branching instructions evaluate machine state to select the next vector for execution. All of this is effected by a von Neumann CPU, chasing an instruction pointer. As long as the stack pointer is fixed on the frame containing the function the instruction pointer will trace a regular pattern within the bounds of the compiled function. This regularity would be obvious in the source code for the function as implemented in a procedural programming language like **C**, where the interplay of concatenation (';'), union (if/else/switch) and repetition (while/do/for) is apparent. It may not be so obvious in source code written in other, eg functional, programming languages, but it all gets compiled down to machine code to run on von Neumann CPUs, on the ground or in the cloud.

Programming instruction-driven machines to navigate complex patterns in sequential data or asynchronous workflows is an arduous task in any modern programming language, requiring a mess of fussy, fine-grained twiddling that is error prone and difficult to compose and maintain. Refactoring the twiddling into a nest of regular input patterns leaves a simplified collection of code snippets that just need to be sequenced correctly as effectors, and extending input patterns to orchestrate effector sequencing via transduction seems like a natural thing to do. Transducer patterns expressed in symbolic terms can be manipulated using well-founded and wide-ranging algebraic techniques, often without impacting effector semantics. Effector semantics are very specific and generally expressed in a few lines of code, free from syntactic concerns, in a procedural programming language. Their algebraic properties also enable regular patterns to be reflected in other mathematical domains where they may be amenable to productive analysis.

Ribose presents a pattern-oriented, transductive approach to sequential information processing, factoring syntactic concerns into nested patterns that coordinate the application of tightly focussed effector functions of reduced complexity. Patterns are expressed algebraically as regular expressions in the byte semiring and extended as rational functions into effector semirings, and effectors are implemented as tightly focussed methods expressed by a target object in the receiver's domain. This approach is not new; IBM produced an FST-driven [XML Accelerator](https://web.archive.org/web/20120930202629/http://www.research.ibm.com/XML/IBM_Zurich_XML_Accelerator_Engine_paper_2004May04.pdf) to transduce complex data schemata (XML, then JSON) at wire speed. They did it the hard way, sweating over lists of transitions, apparently still unaware of semiring algebra. They deployed it alongside [WebSphere](https://web.archive.org/web/20080622054818/http://www-306.ibm.com/software/integration/datapower/xa35/) but the range of acceptable input formats is solely selected by the vendor. We know that sequential data can be processed at wire speeds using transduction technology, and we have _"Universal Turing Machines"_ capable of running any computable _"algorithm"_. Where is the _"Universal Transduction Machine"_ that can recognize any nesting of regular  _"patterns"_ constructed from the `byte*` semiring and transduce conformant data into the receiving domain?

From an extreme but directionally correct perspective it can be said that almost all software processes operating today are running on programmable calculators loaded with zigabytes of RAM. Modern computing machines are the multigenerational inheritors of von Neumann's architecture, which was originally developed to support numeric use cases like calculating ballistic trajectories. These machines are "[Turing ~~tarpits~~ complete](https://en.wikipedia.org/wiki/Brainfuck)", so all that is required to accommodate textual data is a numeric encoding of text characters. [Programmers can do the rest](https://www.cs.nott.ac.uk/~pszgmh/Parsing.hs). Since von Neumann's day we've seen lots of giddy-up but the focus in [machine development](https://github.com/jrte/ribose/raw/master/reference/50_Years_of_Army_Computing.pdf) has mainly been on miniaturization and optimizations to compensate for RAM access lag ([John Backus' 'von Neumann bottleneck'](https://cs.wellesley.edu/~cs251/s19/notes/backus-turing-lecture.pdf)). Sadly, when the first text character enumerations were implemented, their designers failed to note that their text ordinals constituted the basis for a text semiring wherein syntactic patterns in textual media could be extended to [direct effects](https://github.com/jrte/ribose/wiki#math-is-hard) within a target semantic domain.

It is great mystery why support for semiring algebra is nonexistent in almost all programming languages and why hardware support for finite state transduction is absent from commercial computing machinery, even though a much greater proportion of computing bandwidth is now consumed to process sequential byte-encoded information. It may have something to do with money and the vaunted market forces that drive continuous invention and refinement. The folks that design and develop computing hardware and compilers are heavily invested in the von Neumann status quo, and may directly or indirectly extract rents for CPU and RAM resources. They profit enormously as, globally, the machines arduously generate an ever-increasing volume of data to feed back into themselves. So the monetary incentive to improve support for compute-intensive tasks like parsing reams of text may be weak. Meanwhile, transduction technology has been extensively developed and widely deployed. It is the basis for lexical analysis in compiler technology and natural language processing, among other things. But it is buried within [proprietary or specialized software](https://semiring.com/) and is inaccessible to most developers.

Unfortunately I can only imagine what commercial hardware and software engineering tools would be like today if they had evolved with FST logic and pattern algebra built in from the get go. But it's a sure bet that the machines would burn a lot less oil and software development workflows would be more streamlined and productive. Information architects would work with domain experts to design serialized representations for domain artifacts and recombine these in nested regular patterns to realize more complex forms for internal persistence and transmission between processing nodes. These data representations would be designed and implemented simply, directly and efficiently without involving external data data representation schemes like XML or JSON. There's a _[Big Use Case](https://github.com/jrte/ribose/wiki#big-use-case-make-it-all-go-away)_ for that.

See _[Everything is Hard](https://github.com/jrte/ribose/wiki/Stories#everything-is-hard)_.
# The Ribose Manifesto
Ribose encourages *pattern-oriented design and development*, which is based almost entirely on semiring algebra. Input patterns in text and other symbolic domains are expressed and manipulated algebraically and extended to map syntactic features onto vectors of machine instructions. A transducer stack extends the range of transduction to cover context-free input structures that escape semiring confinement. Effector access to RAM and an input stack support transductions involving context-sensitive inputs.

In this way the notion of a *program*, whereby a branching and looping series of *instructions select data* in RAM and mutate machine state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into target state. Here *target*, *effector* and *pattern* are analogues of *machine*, *instruction*, *program*. A *transducer* is a compiled *pattern* and a *transduction* is a *process* that applies a specific input sequence to a stack of nested transducers to direct the application of effectors to a target model in RAM.

Ribose suggests that the von Neumann CPU model would benefit from inclusion of finite state transduction logic to coordinate the sequencing of instructions under the direction of nested transducers driven by streams of numerically encoded sequential media, and that programming languages should express robust support for semiring algebra to enable construction of multidimensional regular patterns and compilation to FSTs as first-order objects. Transduction of data from input channel (file, socket, etc.) interfaces into user space should be supported by operating system kernels. Runtime support for transduction requires little more than an transducer stack and a handful of `byte[]` buffers.

The [Burroughs Corporation B5000](https://en.wikipedia.org/wiki/Burroughs_large_systems), produced in 1961, was first to present a stack-oriented instruction set to support emergent compiler technology for stack-centric programming languages (eg, Algol). The call stack then became the locus of control in runtime process execution of code as nested regular patterns of machine instructions. Who will be first, in the 21st century, to introduce robust compiler support for regular patterns and automata? Will hardware vendors follow suit and introduce pattern-oriented instruction sets to harness their blazing fast calculators to data-driven transductors of sequential information? Will it take 75 years to learn how to work effectively in pattern-oriented design and development environments? Will XML ever go away?

Architects who want a perfect zen koan to break their minds on should contemplate the essential value of abstract data representation languages that can express everything without knowing anything. Developers might want to bone up on semiring algebra. It may look intimidating at first but it is just like arithmetic with nonnegative integers and addition and multiplication and corresponding identity elements **0** and **1**, but with sets of strings and union and concatenation with identity elements **Ø** (empty set) and **ϵ** (empty string). The '__`*`__' semiring operator is defined as the union of all concatenation powers of its operand. Analogous rules apply identically in both domains, although concatenation does not commute in semirings. See _[Math is Hard?](https://github.com/jrte/ribose/wiki#math-is-hard)_ for a scolding.

Best of all, semiring algebra is stable and free from bugs and vulnerabilities. Ginr is mature and stable, although it needs much more testing in diverse symbolic domains, and its author should receive some well-deserved recognition and kudos. Ribose is a hobby horse, created only to lend some support to my claims about pattern-oriented design and development.

<p align="center">
  <a href="https://en.wikipedia.org/wiki/Ribose"><img src="https://github.com/jrte/ribose/raw/master/etc/markdown/ribose.png"></a>
</p>

Good luck with all that.
# Disclaimer
Ribose is presented for demonstration only and is not regularly maintained. You may use it to compile and run the included examples, or create your own transducers to play with. Or clone and refine it and make it available to the rest of the world. Transcode it to **C** and wrap it in a Python thing. Do what you will, it's open source.

Binary executable copies of `ginr` (for Linux) and `ginr.exe` (for Windows) are included in `etc/ginr` for personal use (with the author's permission); ginr guidance is [reposted in the sidebar](https://github.com/jrte/ribose/wiki/Ginr) in the ribose wiki. You are encouraged to clone or download and build ginr directly from the [ginr repo](https://github.com/ntozubod/ginr).

Ribose has been developed and tested with OpenJDK 11 and 17 in Ubuntu 18 and Windows 10. It should build on any unix-ish platform, including `git bash`, `Msys2\mingw` or Windows Subsystem for Linux (WSL) for Windows, with `ant`, `java`, `bash`, `cat`, `wc`, `grep` in the executable search path. The `JAVA_HOME` and `ANT_HOME` environment variables must be set properly, eg `export JAVA_HOME=$(readlink ~/jdk-17.0.7)`.

Clone the ribose repo and run `ant clean package` to percolate the ribose and test libraries and API documentation into the `jars/` and `javadoc/` directories. This will also build the ribose compiler and test models from transducer patterns in the `patterns/` directory. The `ant test` target runs the CI test suite. The default target selected with `ant` alone performs a clean build and runs the CI tests.

```
-: # set home paths for java and ant
-: export JAVA_HOME="$(realpath ./jdk-17.0.7)"
-: export ANT_HOME="$(realpath ./ant-1.10.12)"
-: # clone ribose
-: git clone https://github.com/jrte/ribose.git
Cloning into 'ribose'...
...
Resolving deltas: 100% (2472/2472), done.
-: # build ribose, test and javadoc jars and compiler, test models
-: cd ribose
-: ant package
Buildfile: F:\Ubuntu\git\jrte\build.xml
...
BUILD SUCCESSFUL
-: # list build products
-: ls jars
ribose-0.0.2.jar  ribose-0.0.2-api.jar  ribose-0.0.2-test.jar
-: jar -tvf jars/ribose-0.0.2.jar|grep -oE '[a-z/]+TCompile.model'
com/characterforming/jrte/engine/TCompile.model
-: find . -name '*.model' -o -name '*.map'
./build/Test.map
./build/Test.model
./TCompile.map
./TCompile.model
-: # run the CI tests
-: ant test
```

Instructions for building ribose models and running transductors are included the javadoc pages in `javadoc/`. The documentation for the `com.characterforming.ribose` package specifies the arguments for the runnable `Ribose` class and presents the ribose runtime service interfaces. The main interfaces are `IRuntime`, `ITransductor` and `ITarget`. The runnable `Ribose` class can be executed directly or from the shell scripts in the project root:

- _rinr_: compile ginr patterns from a folder containing ginr source files (\*.inr) to DFAs (\*.dfa)
- _ribose compile | run | decompile_:
  - _compile_: compile a collection of DFAs into a ribose model for a specific target class
  - _run_: run a transduction from a byte stream onto a target instance
  - _decompile_: decompile a transducer

The shell scripts are tailored to work within the ribose repo environment but can serve as templates for performing equivalent operations in other environments. Other than ginr ribose has no dependencies and is contained entirely within the ribose jar file.

See the javadoc overview, package and interface documentation for information regarding use of the ribose compiler and transduction runtime API in the JVM.

For some background reading and a historical perspective visit the [ribose wiki](https://github.com/jrte/ribose/wiki).

See [LICENSE](https://github.com/jrte/ribose/raw/master/LICENSE) for ribose licensing details.
# Postscript
Please somebody burn this into an FPGA and put it in a box like [this](https://web.archive.org/web/20080622054818/http://www-306.ibm.com/software/integration/datapower/xa35/) and sell it. But don't bind the sweetness to a monster and hide it in the box; be sure to provide and maintain a robust compiler for generalized rational functions and relations ([hint](https://github.com/ntozubod/ginr)). Show information architects that they can encode basic domain artifacts as patterns in text semirings (eg, `UNICODE*`) and combine patterns to represent more complex artifacts for persistence and transmission and decoding off the wire. Know that they understand their domains far better than you and can do this without heavy handed guidance from externalities like IBM, Microsoft, Amazon, Google or yourself.

You never know. Folks who process vast volumes of byte encoded textual data (eg, `UTF-8*`) off the wire to feed their search engines, or from persistent stores to feed their giant AI brains, might find unimagined ways to repurpose your box. And you won't have to tweak your FPGA one bit, if you've done it right, because these novel adaptations will be effected outside the box, in the pattern domain.  A thriving pattern-oriented community will share libraries of patterns to cover common artifacts and everyone will love you.

Then XML will go away. JSON too. **_Think_** about it.

Thank you.