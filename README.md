# The General Idea
Ribose (formerly jrte) is about inversion of control for high-volume text analysis and information extraction and transformation in general. Many stream oriented tasks, such as cleaning and extraction for data analytic workflows, involve recognizing and acting upon features embedded, more or less sparsely, within a larger context. Software developers receive some onerous help in that regard from generic software libraries that support common document standards (eg, XML, JSON, MS Word, etc), but dependency on these libraries adds complexity, vulnerability and significant runtime costs to software deployments. And these libraries are of no use at all when information is presented in idiomatic formats that require specialized software to deserialize.

Ribose specializes _[ginr](https://github.com/ntozubod/ginr)_, an industrial strength open source compiler for multidimensional regular patterns, to produce finite state transducers (FSTs) that map syntactic features to semantic effector methods expressed by a target class. Ribose transduction patterns are composed and manipulated using algebraic (`*`-semiring) operators and compiled to FSTs for runtime deployment. Regular patterns may be nested to cover context-free inputs, and the ribose runtime supports unbounded lookahead to resolve ambiguities or deal with context-sensitive inputs. Inputs are presented to ribose runtime transducers as streams of byte-encoded information and regular or context-free inputs are transduced in linear time.

There is quite a lot of byte-encoded information being passed around these days (right-click in any browser window and "View Page Source" to see a sample) and it is past time to think of better ways to process this type of data than crunching it on instruction-driven calculator machines. Ribose and ginr promote a pattern oriented, data driven approach to designing, developing and processing information workflows. Ribose is a ship-in-a-bottle showpiece put together to shine a spotlight on ginr and to demonstrate what computing might be if finite state transduction, augmented with a transducer stack and coupled with a classical CPU/RAM computer, was a common modality for processing sequential information (i.e. almost everything except arithmetic). 

The general idea is to show how to make information work for you rather than you having to work to instruct a computer about how to work with information. Or, at least, how to reduce costs associated with information workflows. This idea outlined below and explored, a bit snarkily, in the stories posted in the _[ribose wiki](https://github.com/jrte/ribose/wiki)_. This has no connection whatsoever with POSIX and Perl 'regular expressions' (regex) or 'pattern expression grammars' (PEGs), that are commonly used for ad hoc pattern matching. In the following I refer to the algebraic expressions used to specify ribose transducers as 'regular patterns' to distinguish them from regex and PEG constructs. 
## Some Motivation, and A Simple Example
Ribose demonstrates a pattern oriented approach to information that minimizes dependency on external libraries and reduces complexity, vulnerability and development and runtime costs in information workflows. Ribose generalizes the *transducer* design pattern that is commonly applied to `filter`, `map` and `reduce` collections of data in functional programming paradigms. Common usage of this design pattern treats the presentation of inputs as a simple series **T\*** without structure. Ribose extends and refines this design pattern, allowing transducers to precisely navigate (filter), map (select effect vector) and reduce (execute effect vector) complex information under the direction of syntactic cues in the input. 

Here the `filter` component is expressed as a collection of nested regular patterns describing an input source, using symbolic algebraic expressions to articulate the syntactic structure of the input. These unary input patterns are then extended to binary transduction patterns that `map` syntactic features to effect vectors that incrementally `reduce` information extracted from the input. The syntactic structure provides a holistic navigable map of the input and exposes cut points where semantic actions should be applied. This presents a clear separation of syntactic and semantic concerns: Syntax is expressed in a purely symbolic domain where patterns are described and manipulated algebraically, while semantics are expressed poetically in a native programming language as effectors in a domain-specific target class. Effector implementation of semantic actions is greatly simplified in the absence of syntactic concerns.

The ribose model compiler and runtime are simple, compact (≾120K) and free from external dependencies. The runtime operates multiple concurrent transductions, each encapsulated in a `Transductor` object that coordinates a transduction process. Nested FSTs are pushed and popped on transductor stacks, with two-way communication between caller and callee effected by injecting input parameters and return values into the input stream for immediate transduction. Incremental effects are applied synchronously as each input symbol (byte or out-of-band control signal) is read, culminating in complete reduction and assimilation of the input into the target domain. For regular and most context-free input patterns transduction is effected in a single pass without lookahead. Unbounded lookahead and backtracking, supported by `mark` and `reset` effectors, can be used to resolve context-sensitive or ambiguous input patterns.

A simple example, taken from the ribose transducer that reduces the serialized form of compiled ginr automata to construct ribose transducers: The input pattern `('INR' (digit+ tab):4 digit+ nl)` is extended to check for a specific tag and marshal 5 integer fields into an immutable `Header` value class. Fields are extracted to named values in raw `byte[]` arrays using the `clear`, `select` and `paste` effectors before the domain-specific `header` effector is finally invoked to decode and marshal them into a `Header` object.
```
# INR210	3	565	127	282

Header = (
  ('INR', clear[`~version`] select[`~version`]) (digit, paste)+
  (tab, clear[`~tapes`] select[`~tapes`]) (digit, paste)+
  (tab, clear[`~transitions`] select[`~transitions`]) (digit, paste)+
  (tab, clear[`~states`] select[`~states`]) (digit, paste)+
  (tab, clear[`~symbols`] select[`~symbols`]) (digit, paste)+
  (nl, header)
);
```
This example is developed more fully below and in the ribose wiki. It is previewed here to show the algebraic binary structure of ribose transducer patterns. The branching and repeating patterns expressed in the input syntax drive the selection of non-branching effect vectors, obviating much of the fine-grained control logic that would otherwise be expressed in line with effect in a high-level programming language without support from an external parsing library. Expressions such as this can be combined with other expressions using concatenation, union, repetition and composition operators to construct more complex patterns. More generally, ribose patterns are amenable to algebraic manipulation in the `*`-semiring, and ginr enables this to be exploited to considerable advantage. For example `Transducer = Header Transition* eos` covers a complete serialized automaton, `Transducer210 = ('INR210' byte* eos) @@ Transducer` restricts `Transducer` to accept only version 210 automata (ginr's `@` composition operator absorbs matching input and reduces pattern arity, the `@@` join operator retains matching input and conserves arity). 

Ginr is the star of the ribose circus. It was developed by J Howard Johnson at the University of Waterloo in the early 1980s on a 16-bit VAX computer. One of its first applications was to [transduce the typesetting code for the entire Oxford English Dictionary](https://cs.uwaterloo.ca/research/tr/1986/CS-86-20.pdf) from an archaic layout to SGML. I first used it at Bell Northern Research to implement a ribose-like framework to serve in a distributed database mediation system involving diverse remote services and data formats. The involved services were all driven by a controller transducing conversational scripts from a control channel. The controller drove a serial data link, transmitting queries and commands to remote services on the output channel and switching context-specific response transducers onto the input channel. Response transducers reduced query responses to SQL statements for the mediator and reduced command responses to guidance to be injected into the control channel to condition the course of the ongoing conversation.

Ginr subsequently disappeared from the public domain and has only recently been republished with an open source license on GitHub. It has been upgraded with substantial improvements, including 32-bit state and symbol enumerators and native support for transcoding Unicode symbols to UTF-8 bytes. It provides a full complement of algebraic operators that can be applied to reliably produce very complex (and very large) automata. Large and complex patterns can be decomposed into smaller and simpler patterns, compiled to FSTs, and reconstituted on ribose runtime stacks, just as complex procedural algorithms in Java are decomposed into simpler methods that Java threads orchestrate on call stacks in the JVM to effect the algorithm.
## Basic Concepts
Ginr operates in a symbolic domain involving a finite set of symbols and algebraic semiring operators that recombine symbols to express syntactic patterns. Support for Unicode symbols is built in, and Unicode in ginr source patterns is rendered as UTF-8 byte sequences in compiled automata. Ribose transduction patterns introduce additional atomic symbols as tokens representing transducers, effectors, byte-encoded effector parameters, out-of-band (>255) input signals, or named values. 

Ribose transducers operate on `byte*` inputs, which may include binary data and/or UTF-8 encoded text. UTF-8 text is transduced without decoding and extracted bytes are decoded only when required. Input need not be textual and may be hierarchical; for example, a byte-level tokenizing transducer may generate a stream of tokens that are then transduced by a top-level compiler, or low-level event handlers in a distributed event-driven workflow may transduce incoming messages and emit signals to a controlling transducer to effect governance. In some cases complex inputs, such a series of multivariate tuples emitted by a remote sensor, can be reduced by a finite equivalence relation to a series of equivalence class enumerators for pattern-driven transduction.

A ribose model is comprised of FSTs compiled from a collection of transducer patterns, an application-defined target class expressing domain-specific effectors, static effector parameters, an enumeration of out-of-band signals that may be injected into input byte streams for transduction control, and a collection of named values for accumulating extracted bytes. The base target class (`BaseTarget`) provides a core set of effectors to
- extract and compose data in selected named values *(`select, paste, copy, cut, clear`)*, 
- count down from preset value and signal end of countdown *(`count`)*
- push/pop transducers on the transduction stack *(`start, stop`)*, 
- mark/reset at a point in the input stream *(`mark, reset`)*, 
- write extracted data to an output stream *(`out`)*,
- or inject input for immediate transduction *(`in`)*.

All ribose models implicitly inherit these core ribose effectors, along with an enumeration of signals (`nil`, `nul`, `eol`, `eos`) and an anonymous named value that is preselected for every transduction and reselected when `select` is invoked with no parameter. The `BaseTarget` class, without extension, is sufficient for ribose models that include only text transforms that terminate at the `out[...]` effector -- additional signals and named values referenced in the collected patterns are implicitly included in all models.

More complex targets presenting domain-specific effectors can be implemented in a target class that extends `BaseTarget` and overrides the `ITarget` interface methods `String getName()` and `IEffector[] getEffectors()`. All effectors are provided with a reference to the containing target instance and an `IOutput` view for extracting named values as `byte[]`, integer, floating point, or Unicode `char[]` values, typically for inclusion in immutable value objects that are incorporated into the target model.
## Some Examples
Here are some examples to get things started. These are abridged from the ginr source for the patterns in the ribose test suite (`patterns/test/*.inr`). 

Ribose specializes ginr to accept only regular patterns comprised of ternary terms *(input, effector\[parameters\] ...)*, using the tape shifting operator `[]` to associate parameters with effectors (so `(X,Y[Z])` is equivalent to `(X,Y,Z)`). Such terms are combined and composed using regular operators to specify and recombine ribose transducers. Below, the `(X,Y,Z)$(0,1 2) → (X,Y Z)` operation uses the projection operator `$` to flatten effectors and parameters onto a single tape in canonical order. The `:prsseq` operator is then applied to verify that the pattern is a single-valued (subsequential) function `X → Y Z` sequentially mapping input to target effectors and parameters and to provide a readable listing of the compiled FST transitions. 
### Hello World
```
Hello = (nil, out[`Hello World`] stop);

(Hello$(0,1 2)):prsseq;
(START)  nil  [ out Hello World stop ]  (FINAL)

ribose --nil Hello
Hello World
```
### Fibonacci
This next example computes unary Fibonacci numbers from unary inputs, using built-in ribose compositing effectors to manipulate a collection of user-defined named values (`~X`) that accumulate data mapped from the input. This is interesting because, formally, FSTs effect linear transforms and can only produce regular outputs while the Fibonacci sequence is not regular (it is strictly context sensitive). This is possible because the Fibonacci FST generates a regular sequence of effectors that retain intermediate results in named value registers. 
```
Fibonacci = (
  (
    ('0', select[`~q`] paste['1')
    ('0', select[`~r`] cut[`~p`] select[`~p`] copy[`~q`] select[`~q`] cut[`~r`])*
  )?
  (nl, paste out stop)
);

(Fibonacci$(0,1 2)):prsseq;
(START)  0  [ select ~q paste 1 ]                                   1
(START)  nl [ paste out stop ]                                      (FINAL)
1        0  [ select ~r cut ~p select ~p copy ~q select ~q cut ~r ] 1
1        nl [ paste out stop ]                                      (FINAL)

$ for n in '' 0 00 000 0000 00000 000000 0000000 00000000 000000000 0000000000; do echo $n | ribose Fibonacci; done

1
1
11
111
11111
11111111
1111111111111
111111111111111111111
1111111111111111111111111111111111
1111111111111111111111111111111111111111111111111111111
```
### Tuple Extraction
This example was previewed above. It is taken from the ribose model compiler, which transduces serialized ginr automata to construct FSTs for inclusion in a ribose runtime model. Ginr serializes compiled pattern automata to tab-delimited ASCII text files beginning with a header line listing ginr version number and the number of tapes, transitions, states, and symbols. Below, the `Header` pattern determines how ribose deserializes the header line, transducing it into an immutable value object in the target model. This is presented in more detail in the ribose wiki (see [Ginr as a Service Provider](https://github.com/jrte/ribose/wiki#ginr-as-a-service-provider)). Here it is refined a bit to show how ginr's composition operator (`@`) can be used to replace formal parameters in a template pattern (`Field`).
```
# INR210	3	565	127	282

Field = clear[X] select[X];
Number = (digit, paste)+;
Header = (
  ('INR', Field @ (X,`~version`)*) Number
  (tab, Field @ (X,`~tapes`)*) Number
  (tab, Field @ (X,`~transitions`)*) Number
  (tab, Field @ (X,`~states`)*) Number
  (tab, Field @ (X,`~symbols`)*) Number
  (nl, header)
);
```
The model compiler target class, `ModelCompiler`, implements the `ITarget` interface and expresses a `HeaderEffector<ModelCompiler>` effector class. The compiled `Header` transducer maps the `header` token in the pattern to this effector's `invoke()` method and calls it when the final `nl` token is read. The effector uses its `IOutput` view to decode raw bytes from named values to integer-valued fields within a `Header` object bound to the target instance. 
```
class Header {
  final int version;
  final int tapes;
  final int transitions;
  final int states;
  final int symbols;
  Header(int version, int tapes, int transitions, int states, int symbols)
  throws EffectorException {
    this.version = version;
    this.tapes = tapes;
    this.transitions = transitions;
    this.states = states;
    this.symbols = symbols;
    // Apply value range and sanity checks here
  }
}

class HeaderEffector extends BaseEffector<ModelCompiler> {
  INamedValue fields[];

  HeaderEffector(ModelCompiler automaton) {
    super(automaton, Bytes.encode("header"));
  }

  @Override // Called once, when the containing model is loaded into the ribose runtime
  public void setOutput(IOutput output)
  throws TargetBindingException {
    super.setOutput(output);
    fields = new INamedValue[] {
      super.output.getNamedValue(Bytes.encode("version")),
      super.output.getNamedValue(Bytes.encode("tapes")),
      super.output.getNamedValue(Bytes.encode("transitions")),
      super.output.getNamedValue(Bytes.encode("states")),
      super.output.getNamedValue(Bytes.encode("symbols"))
    };
  }

  @Override // Called once for each input automaton, when the header line has been consumed
  public int invoke()
  throws EffectorException {
    Header h = new Header(
      (int)fields[0].asInteger(),
      (int)fields[1].asInteger(),
      (int)fields[2].asInteger(),
      (int)fields[3].asInteger(),
      (int)fields[4].asInteger()
    );
    super.target.header = h;
    super.target.transitions = new Transition[h.transitions];
    super.target.stateTransitionMap = new HashMap<Integer, ArrayList<Transition>>((h.states * 5) >> 2);
    return IEffector.RTE_EFFECT_NONE;
  }
}
```
This demonstrates the clear separation of syntactic (input) and semantic (target) concerns obtained with pattern oriented design. All of the fine-grained character-level code that would otherwise be involved to navigate the input stream is relegated to transduction patterns expressed in a symbolic, algebraic framework supported by a robust compiler for multidimensional regular patterns. Target and effector implementations are thereby greatly reduced. For comparison, here is a snippet of **C** source from the ginr repo that decodes the tab-delimited sequence of ASCII digits representing the tape count in the header line:
```
if ( c < '0' || c > '9' ) { fail(8); }
number_tapes = c - '0';
c = getc( fp );
while ( c != '\t' ) {
  if ( c < '0' || c > '9' ) { fail(9); }
  number_tapes = number_tapes * 10  +  ( c - '0' );
  c = getc( fp );
  if ( number_tapes >= MAXSHORT ) { fail(10); }
}
A-> A_nT = number_tapes;
```
Here, as with most ribose solutions, the effector implementation and supporting value class involve no external libraries outside the ribose runtime and are completely decoupled from the syntactic structure of the input. That is not to say that the target class and effectors, composed in a native programming language, cannot interact with more complex components in the application or service domain. But these interactions are orthogonal and opaque to transduction patterns since they are either encapsulated by effectors or occur downstream from transduction processes. 
### Navigating Noisy Inputs (Nullification)
It is often necessary to extract features that are embedded in otherwise irrelevant or noisy inputs. The ribose runtime supports this by injecting a `nul` signal into the input stream whenever no transition is defined for a received input. Any ribose pattern can be extended to handle a `nul` signal at any point by simply skipping over input with nil effect until a synchronization pattern marking the beginning of the innermost enclosing loop is recognized and then continuing as before. 

```
#<af-start id="3" threadId="0000000000329C80" totalBytesRequested="16" timestamp="2021-04-05T23:12:58.597" intervalms="5243.429" type="nursery" />
#<cycle-start id="4" type="scavenge" contextid="0" timestamp="2021-04-05T23:12:58.597" intervalms="5243.468" />
#<gc-start id="5" type="scavenge" contextid="4" timestamp="2021-04-05T23:12:58.597">

interval = (
  '<cycle-start id="' digit+ '" type="scavenge" contextid="' digit+ '" timestamp="' (digit+ ('.:-T':alph))+ digit+ '" '
  ('intervalms="', select[`~interval`] clear)
  (digit, paste)+ ('.', paste) (digit, paste)+
  ('"', out[`~interval` NL])
  space* '/>' (utf8 - '<')*
);

# tape alphabets (with nl mapped to NL on (interval$2) so all alphabets are disjoint)
a0 = (interval$0):alph;
a1 = (interval$1):alph;
a2 = (interval$2):alph;

# nullify to construct a synchronizing pattern for everything that is not an interval
null = (
  (
    # map all input symbols x->x|nul in (interval$0) and flatten interval to one tape
    ((AnyOrNul* @ interval)$(0 1 2))
    # copy everything up to and including the first nul, projecting back onto 3 tapes
  @ ((a0$(0,0))* (a1$(0,,0))* (a2$(0,,,0))*)*
    (nul$(0,0)) (nul* a0* a1* a2*)* 
  )
  # absorb input up to next '<' after nul
  (utf8 - '<')*
);

# revert NL to nl on (interval$2), transduce recognized intervals, ignore null input
Tintervals = ( 
  (null* interval*)* @ (((a2 - NL)$(0,0))* (NL, nl)*)*
);
```
Nullification can be applied similarly to subpatterns to effect fine-grained context-sensitive error handling. The technique of flattening a transducer pattern and  transforming it with an editor pattern while projecting the result back onto the original tapes can be applied in many other ways. Like [CRISPR](https://www.newscientist.com/definition/what-is-crispr/) in genetics, it can be used to inject new behaviors or alter existing behaviors in existing transducer patterns.
## Resolving Ambiguous Inputs (Classification)
It is sometimes necessary to look ahead in the input, without effect, to syntactically validate a prospective feature or resolve an ambiguous pattern before selecting a course of action. The snippet below, from the `LinuxKernelStrict` transducer in the ribose test suite, demonstrates this using the built-in `mark` and `reset` effectors. The _`header`_ and _`capture`_ subpatterns, referenced but not shown here, effect field extraction from iptables messages in Linux kernel logs.
```
#May 15 07:58:52 kb-ubuntu kernel: [ 1794.599801] DROPPED IN=eth0 OUT= MAC=01:00:5e:00:00:fb:00:13:20:c0:36:32:08:00 SRC=192.168.144.101 DST=224.0.0.251 LEN=32 TOS=0x00 PREC=0x00 TTL=1 ID=8596 OPT (94040000) PROTO=2
#May 16 07:59:13 kb-ubuntu kernel: [  285.950056] __ratelimit: 225 callbacks suppressed

# distribute paste effector over any input sequence of bytes
PasteAny = (byte, paste)*;

# reorder and write fields extracted from the header and capture subpatterns to stdout
store = out[
  `~timestamp` '|' `~hostname` '|' `~tag` '|' `~in` '|' `~out` '|' `~macaddress` '|' `~srcip`
  '|' `~dstip` '|' `~protocol` '|' `~srcport` '|' `~dstport` `\n`
];

LinuxKernelDropped = (
  header (space, select[`~tag`]) ('DROPPED' @@ PasteAny) capture (nl, store in[`!nil`] stop)
):dfamin;

LinuxKernelLimited = (
  header (space, select[`~tag`]) ('LIMITED' @@ PasteAny) capture (nl, store in[`!nil`] stop)
):dfamin;

LinuxKernelAborted = (
  header (space, select[`~tag`]) ('ABORTED' @@ PasteAny) capture (nl, store in[`!nil`] stop)
):dfamin;

dropped = LinuxKernelDropped$0;
limited = LinuxKernelLimited$0;
aborted = LinuxKernelAborted$0;

next = reset clear[`~*`] select[`~timestamp`];
line = (dropped | limited | aborted) / nl;
null = (line:pref) nul (byte - nl)*;

LinuxKernelStrict = (
  (
    (nil, mark)
    (
      (
         (dropped, next start[`@LinuxKernelDropped`])
      |  (limited, next start[`@LinuxKernelLimited`])
      |  (aborted, next start[`@LinuxKernelAborted`])
      )
    | null (nl, in[`!nil`])
    )
  )*
):dfamin;
```
This also demonstrates nesting of ribose transducers. The top-level `LinuxKernelStrict` transducer marks an input anchor and looks ahead to verify syntax before selecting one of three transducers. It then resets to the input anchor and starts the selected transducer to reduce the marked input and inject a `nil` signal into the input before returning to the top-level transducer.

---
### Some Metrics (Intermission)
The charts below summarize the results of a data extraction benchmarking runoff between Java regex and three ribose variants. Initial heap size (2M) and region size (1M) were identical for all runs. The `LinuxKernel` transducer does no lookahead, `LinuxKernelLoose` looks ahead only as far as the distinguishing tag, and `LinuxKernelStrict` looks ahead up to the end of line. All benchmark outputs (regex and ribose) were binary equivalent. All three ribose transducers presented similar heap usage profiles, incurring 2 G1 collections and driving the heap to a maximal 4M size (2M young, 1M survivor). Regex incurred 54 collections, reclaiming more than 450M of transient object space as the heap grew to 20M (8M young, 1M survivor). 

![LinuxKernelBench](https://github.com/jrte/ribose/raw/master/etc/markdown/LinuxKernelLog.png)

Ginr automata are state-minimized but the ribose model compiler is able to obtain some lossless compression of ginr FSTs by coalescing equivalent input symbols to eliminate columns in the state transition matrix and collapsing vectors of >1 effectors and parameters to eliminate connecting states and transitions. The dimensions of the ginr FST and the ribose transducer compiled for `LinuxKernelStrict` are shown in the table below. 

|        | Inputs | States | Transitions | Size |
| ------:| ------:| ------:| -----------:| ----:|
| **Ginr**   |    260 |     83 |        1232 | 25K |
| **Ribose** |     30 |     58 |         343 |  7K |

Size is important in the ribose runtime because FSTs transition functions are represented in 2-dimensional (state, input) arrays and these are often sparse. Only about 20% of the cells in the `LinuxKernelStrict` transition matrix are populated, the rest are dead cells that trigger `nul` when hit. Successive transitions may reference cells that are widely distributed in RAM and this can degrade the performance of the L1 data cache, forcing more traffic on the CPU-RAM bus. I vented some steam about this in one of my favorite rants, see _[A Few Words about Time and Space](https://github.com/jrte/ribose/wiki/Stories#a-few-words-about-time-and-space)_ on the wiki.

---
## Everything is Code
In computing ecosystems regular patterns and their equivalent automata, like microbes in biological ecosystems, are ubiquitous and do almost all of the work. String them out on another construct like a stack or a spine and they can perform new tricks. 

Consider ribonucleic acid (RNA), a strip of sugar (ribose) molecules strung together, each bound to one of four nitrogenous bases (A|U|G|C|), encoding genetic information. Any ordered contiguous group of 3 bases constitutes a *codon*, and 61 of the 64 codons are mapped deterministically onto the 21 amino acids used in protein synthesis (the other three are control codons). This mapping is effected by remarkable molecular machine, the *ribosome*, which ratchets messenger RNA (mRNA) through an aperture to align the codons for translation and build a protein molecule, one amino acid at a time (click on the gears below to see a real-time animation of this process). Over aeons, nature has programmed myriad mRNA scripts and compiled them into DNA libraries to be distributed among the living. So this trick of using sequential information from one domain (eg, mRNA->codons) to drive a process in another domain (amino acids->protein) is not new. 

<p align="center">
  <a href="https://www.youtube.com/watch?v=TfYf_rPWUdY"><img src="https://github.com/jrte/ribose/raw/master/etc/markdown/2-gears-white.jpeg"></a>
</p>

For a more recent example, consider a **C** function compiled to sequences of machine instructions with an entry point (call) and maybe one or more exit points (return). This can be decomposed into a set of vectors of non-branching instructions, each terminating with a branch (or return) instruction. These vectors are ratcheted through the control unit of a CPU and each sequential instruction is mapped (decoded and executed) to effect specific local changes in the state of the machine. Branching instructions evaluate machine state to select the next vector for execution. All of this is effected by a von Neumann CPU, chasing an instruction pointer. As long as the stack pointer is fixed on the frame containing a function the instruction pointer will trace a regular pattern within the bounds of the compiled function. This regularity would be obvious in the source code for the function as implemented in a procedural programming language like **C**, where the interplay of concatenation (';'), union (if/else/switch) and repetition (while/do/for) is apparent. It may not be so obvious in source code written in other, eg functional, programming languages, but it all gets compiled down to machine code to run on von Neumann CPUs, on the ground or in the cloud. 

From an extreme but directionally correct perspective it can be said that almost all software processes operating today are running on programmable calculators with keyboards and heaps of RAM. Modern computing machines are the multigenerational inheritors of von Neumann's architecture, which was originally developed to support numeric use cases. These machines are "[Turing complete](https://en.wikipedia.org/wiki/Brainfuck)", so all that is required to accommodate textual data is a numeric encoding of text characters. [Programmers can do the rest](https://www.cs.nott.ac.uk/~pszgmh/Parsing.hs). Since von Neumann's day we've seen lots of giddy-up but the focus in machine development has mainly been on miniaturization and optimizations to compensate for RAM access lag.

Programming instruction-driven machines to navigate complex patterns in sequential data or asynchronous workflows is an arduous task in any modern programming language, requiring a mess of fussy, fine-grained twiddling that is error prone and difficult to compose and maintain. Refactoring the twiddling into a nest of regular input patterns leaves a simplified collection of code snippets that just need to be sequenced correctly as effectors, and extending input patterns to orchestrate effector sequencing via transduction seems like a natural thing to do. Transducer patterns expressed in symbolic terms can be manipulated using well-founded and wide-ranging algebraic techniques, often without impacting effector semantics. Effector semantics are very specific and generally expressed in a few lines of code, free from syntactic concerns, in a procedural programming language. 

It is great mystery why support for `*`-semiring algebra is nonexistent in almost all programming idioms and why hardware support for finite state transduction is absent from commercial computing machinery, even though a much greater proportion of computing bandwidth is now consumed to process sequential byte-encoded information. It may have something to do with money and the vaunted market forces that drive continuous invention and refinement. The folks that design and develop computing hardware and compilers are heavily invested in the von Neumann status quo, and may directly or indirectly extract rents for CPU and RAM bandwidth. They profit enormously as, globally, the machines generate an ever-increasing volume of data to feed back into themselves. So the monetary incentive to improve support for compute-intensive tasks like parsing reams of text may be weak.

Unfortunately I can only imagine what commercial hardware and software engineering tools would be like today if they had evolved with FST logic and pattern algebra built in from the get go. But it's a sure bet that the machines would burn a lot less oil and software development workflows would be more streamlined and productive.

See _[Everything is Hard](https://github.com/jrte/ribose/wiki/Stories#everything-is-hard)_.
# The Ribose Manifesto
Ribose encourages *pattern oriented programming*, which is based almost entirely on semi-ring algebra. Input patterns in text and other symbolic domains can be expressed and manipulated algebraically and extended to map syntactic features onto vectors of machine instructions. A transducer stack extends the range of transduction to cover context-free input structures that escape semi-ring confinement. Effector access to RAM and an input stack support transductions involving context-sensitive inputs. 

In this way the notion of a *program*, whereby a branching and looping series of *instructions select data* in RAM and mutate target state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into target state. Here *target*, *effector* and *pattern* are analogues of *machine*, *instruction*, *program*. A *transduction* is a *process* that applies a specific input sequence to a stack of nested FSTs to direct the application of effectors to a target model in RAM.

Ribose suggests that the von Neumann CPU model would benefit from inclusion of finite state transduction logic to coordinate the sequencing of instructions under the direction of FSTs driven by streams of numerically encoded sequential media, and that programming languages should express robust support for `*`-semiring algebra to enable construction of multidimensional regular patterns and compilation to runtime FSTs. 

Developers might want to bone up on `*`-semiring algebra. It may look intimidating at first but it is just like arithmetic with nonnegative integers and addition and multiplication and corresponding identity elements **0** and **1**, but with sets of strings and union and concatenation with identity elements **Ø** (empty set) and **ϵ** (empty string). The '__`*`__' semiring operator is defined as the union of all concatenation powers of its operand. Analogous rules apply identically in both domains, although concatenation does not commute in semirings. 

Best of all, `*`-semiring algebra is stable and free from bugs and vulnerabilities. 

Good luck with all that.
# Disclaimer
Ribose is presented for demonstration only and is not regularly maintained. You may use it to compile and run the included examples, or create your own transducers to play with. Or clone and refine it and make it available to the rest of the world. Transcode it to **C** and wrap it in a Python thing. Do what you will, it's open source.

Clone the ginr repo and `cd src/; make -f Makefile install` to build and install ginr in `~/bin`. A [ginr user guide](https://github.com/ntozubod/ginr/blob/main/doc/intro_1988/inr_intro.pdf) can be found in the ginr repo. 

Clone the ribose repo and run `ant -f build.xml ribose` to build the ribose library in the `jars/` folder. The `test` ant target runs the CI test suite.

For some background reading and a historical perspective visit the [ribose wiki](https://github.com/jrte/ribose/wiki).

To build a ribose runtime model emulate the procedure for building `Test.model` in `build.xml`.

To learn how to use ribose models in the JVM runtime see the ribose javadoc pages. 

The current version of ribose is packaged in `jars/ribose-0.0.0.jar`.

See [LICENSE](https://github.com/jrte/ribose/raw/master/LICENSE) for ribose licensing details.

---

![YourKit](https://www.yourkit.com/images/yklogo.png)

The jrte project uses YourKit to identify and eliminate bottlenecks and turbulence in stream processing workflows and to reduce object creation to a bare minimum while maintaining high throughput.

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.
