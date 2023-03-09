# The General Idea
Ribose (formerly jrte) is about inversion of control for high-volume text analysis and information extraction and transformation in general. Many stream oriented tasks, such as cleaning and extraction for data analytic workflows, involve recognizing and acting upon features embedded, more or less sparsely, within a larger context. Software developers receive some onerous help in that regard from generic software libraries that support common document standards (eg, XML, JSON, MS Word, etc), but dependency on these libraries adds complexity, vulnerability and significant runtime costs to software deployments. And these libraries are of no use at all when information is presented in idiomatic formats that require custom software to deserialize.

Ribose specializes _[ginr](https://github.com/ntozubod/ginr/blob/main/doc/intro_1988/inr_intro.md)_, an industrial strength open source compiler for multidimensional regular patterns, to produce finite state transducers (FSTs) that map syntactic features to semantic effector methods expressed by a target class. Ribose transduction patterns are composed and manipulated using algebraic (`*`-semiring) operators and compiled to FSTs for runtime deployment. Regular patterns may be nested to cover context-free inputs, and the ribose runtime supports unbounded lookahead to resolve ambiguities or deal with context-sensitive inputs. Inputs are presented to ribose runtime transducers as streams of byte-encoded information and regular or context-free inputs are transduced in linear time.

There is quite a lot of byte-encoded information being passed around these days (right-click in any browser window and "View Page Source" to see a sample) and it is past time to think of better ways to process this type of data than crunching it on instruction-driven calculator machines. Ribose and ginr promote a pattern oriented, data driven approach to designing, developing and processing information workflows. Ribose is a ship-in-a-bottle showpiece put together to shine a spotlight on ginr and to demonstrate what computing might be if finite state transduction, augmented with a transducer stack and coupled with a classical CPU/RAM computer, was a common modality for processing sequential information (i.e. almost everything except arithmetic).

The general idea is to show how to make information work for you rather than you having to work to instruct a computer about how to work with information. Or, at least, how to reduce costs associated with information workflows. This idea outlined below and explored, a bit snarkily, in the stories posted in the _[ribose wiki](https://github.com/jrte/ribose/wiki)_. This has no connection whatsoever with POSIX and Perl 'regular expressions' (regex) or 'pattern expression grammars' (PEGs), that are commonly used for ad hoc pattern matching. In the following I refer to the algebraic expressions used to specify ribose transducers as 'regular patterns' to distinguish them from regex and PEG constructs.
## An Overview an Example and Some History
Ribose demonstrates a pattern oriented approach to information that minimizes dependency on external libraries and reduces complexity, vulnerability and development and runtime costs in information workflows. Ribose generalizes the *transducer* design pattern that is commonly applied to `filter`, `map` and `reduce` collections of data in functional programming paradigms. Common usage of this design pattern treats the presentation of inputs as a simple series **T\*** without structure. Ribose extends and refines this design pattern, allowing transducers to precisely navigate (filter), map (select effect vector) and reduce (execute effect vector) complex information under the direction of syntactic cues in the input.

Here the `filter` component is expressed as a collection of nested regular patterns describing an input source, using symbolic algebraic expressions to articulate the syntactic structure of the input. These unary input patterns are then extended to binary transduction patterns that `map` syntactic features to effect vectors that incrementally `reduce` information extracted from the input. The syntactic structure provides a holistic navigable map of the input and exposes cut points where semantic actions should be applied. This presents a clear separation of syntactic and semantic concerns: Syntax is expressed in a purely symbolic domain where patterns are described and manipulated algebraically, while semantics are expressed poetically in a native programming language as effectors in a domain-specific target class. Syntactic patterns are articulated without concern for target semantics and effector implementation of semantic actions is greatly simplified in the absence of syntactic concerns. This model can be applied to messaging in the control plane, to coordinate the flow of messages to and from processing nodes, as well as to messaging in data plane where remote request and response messages are transduced to assimilate information content and reflect guidance back into the control plane.

The ribose runtime operates multiple concurrent transductions, each encapsulated in a `Transductor` object that provides a set of base effectors and coordinates a transduction process. Nested FSTs are pushed and popped on transductor stacks, with two-way communication between caller and callee effected by injecting input parameters and return values into the input stream for immediate transduction. Incremental effects are applied synchronously as each input symbol (byte or out-of-band control signal) is read, culminating in complete reduction and assimilation of the input into the target domain. For regular and most context-free input patterns transduction is effected in a single pass without lookahead. Context-sensitive or ambiguous input patterns can be classified and resolved with unbounded lookahead `(select clear paste* in)` or backtracking `(mark reset)` using base transductor effectors.

The ribose model compiler and runtime are simple, compact (≾120K) and free from external dependencies. The ribose runtime transduces `byte*` streams simply and only because `byte` is the least common denominator for data representation in most networks and computing machines. Ginr compiles complex Unicode glyphs in ribose patterns to multiple UTF-8 byte transitions, so all ribose transductions are effected in the byte domain and only extracted features are decoded and widened to 16-bit Unicode code points. Binary data can be embedded with text (eg, **\`a\x00b\`**), using self-terminating binary patterns or prior length information, as long as they are distinguishable from other artifacts in the information stream. Raw binary encodings may be especially useful in domains, such as online gaming or realtime process control, that demand compact and efficient messaging protocols with relaxed readability requirements. Semantic effectors may also inject previously captured bytes or out-of-band signals, such as countdown termination, into the input stream to direct the course of transductions.
### _Example_
A simple example, taken from the ribose transducer that reduces the serialized form of compiled ginr automata to construct ribose transducers: The input pattern `('INR' (digit+ tab):4 digit+ nl)` is extended to check for a specific tag and marshal 5 integer fields into an immutable `Header` value object. Fields are extracted to named values in raw `byte[]` arrays using the `clear`, `select` and `paste` effectors before the domain-specific `header` effector is finally invoked to decode and marshal them into a `Header` object.
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
The expression here is intentionally explicit and redundant to show the algebraic binary structure of ribose transducer patterns. The branching and repeating patterns expressed in the input syntax drive the selection of non-branching effect vectors, obviating much of the fine-grained control logic that would otherwise be expressed in line with effect in a typical programming language without support from an external parsing library. Most of the work is performed by transductor effectors that latch bytes into named fields that, when complete, are decoded and assimilated into the target domain by a tightly focussed domain-specific effector.

Expressions such as this can be combined with other expressions using concatenation, union, repetition and composition operators to construct more complex patterns. More generally, ribose patterns are amenable to algebraic manipulation in the `*`-semiring, and ginr enables this to be exploited to considerable advantage. For example `Transducer = Header Transition* eos` covers a complete serialized automaton, `Transducer210 = ('INR210' byte* eos) @@ Transducer` restricts `Transducer` to accept only version 210 automata (ginr's `@` composition operator absorbs matching input and reduces pattern arity, the `@@` join operator retains matching input and preserves arity).
### _Ginr_`*`
Ginr is the star of the ribose circus. It was developed by J Howard Johnson at the University of Waterloo in the early 1980s on a 16-bit VAX computer. One of its first applications was to [transduce the typesetting code for the Oxford English Dictionary](https://cs.uwaterloo.ca/research/tr/1986/CS-86-20.pdf) from an archaic layout to SGML. I first used it at Bell Northern Research to implement a ribose-like framework to serve in a distributed database mediation system involving diverse remote services and data formats. The involved services were all driven by a controller transducing conversational scripts from a control channel. The controller drove a serial data link, transmitting queries and commands to remote services on the output channel and switching context-specific response transducers onto the input channel. Response transducers reduced query responses to SQL statements for the mediator and reduced command responses to guidance to be injected into the control channel to condition the course of the ongoing conversation.

Ginr subsequently disappeared from the public domain and has only recently been [published](https://github.com/ntozubod/ginr) with an open source license on GitHub. It has been upgraded with substantial improvements, including 32-bit state and symbol enumerators and compiler support for transcoding Unicode symbols to UTF-8 bytes. It provides a full complement of algebraic operators that can be applied to reliably produce very complex (and very large) automata. Large and complex patterns can be decomposed into smaller and simpler patterns, compiled to FSTs, and reconstituted on ribose runtime stacks, just as complex procedural algorithms in Java are decomposed into simpler methods that Java threads orchestrate on call stacks in the JVM runtime.

Ribose offers a novel perspective on computing, treating sequential encodings of information as entanglements of syntax and semantics to show how information architects can use `*`-semiring algebra to articulate syntactic representations of serialized information sources (eg, microservice API messages) and extend these to coordinate the action of semantic effectors that reduce and assimilate information into their target domain. In doing so they abstract from target implementation two important concerns that are difficult to express in common programming languages:

1. all syntactic concerns, which are encoded in input patterns (effector orchestration is transducted in synchrony with the information decoding sequence)
2. all concerns about input segmentation, which are relegated to the transduction runtime (patterns and targets are oblivious to breaks and pauses in the input stream)

On the other hand, ribose offers no perspective whatsoever on any particular syntactic framework or semantic domain. One goal is to support information processing and process control in diverse service domains without dependency on generalized data presentation frameworks or attendant software libraries. But the primary objective is to place the core elements of computing back in the hands of information architects and developers, who may harness pattern algebra with expert domain knowledge to most effectively encode information within their respective service domains, for persistence and transmission within the domain and for exchange with service consumers.

Regular patterns and automata are to computing ecosystems what soil and microbiota are to the stuff living above ground. Strange that we don't see explicit support for their construction and runtime use in modern programming languages and computing machines. Ribose is presented only to demonstrate the general idea of pattern oriented design and development. It successfully runs a limited suite of test cases and it can be used to build domain-specific ribose models, but it is not regularly maintained nor suitable for general use. Others are encouraged to clone and improve it or implement more robust expressions of the general idea. **Rust**, anyone?
## Basic Concepts
Ginr operates in a symbolic domain involving a finite set of symbols and algebraic semiring operators that recombine symbols to express syntactic patterns. Support for Unicode symbols and binary data is built in, and Unicode in ginr source patterns is rendered as UTF-8 byte sequences in compiled automata. UTF-8 text is transduced without decoding and extracted bytes are decoded only in target effectors. Ribose transducer patterns may introduce additional atomic symbols as tokens representing out-of-band (>255) control signals.

Input patterns are expressed in `{byte,signal}*`-semirings, and may involve UTF-8 and binary bytes from an external source as well as control signals interjected by target effectors. Ribose transducer patterns are expressed in `(input,effector,parameter)*`-semirings, mapping input patterns onto parameterized effectors expressed by domain-specific target classes. They identify syntactic features of interest in the input and apply target effectors to extract and assimilate features into the target domain. 

A ribose model is associated with a domain-specific target class and is a container for related collections of transducers, target effectors, static effector parameters, control signals and named value registers for accumulating extracted bytes. The `ITransductor` implementation that governs ribose transductions provides a base set of effectors to
- extract and compose data in selected named values *(`select, paste, copy, cut, clear`)*,
- count down from preset value and signal end of countdown *(`count`)*
- push/pop transducers on the transduction stack *(`start, stop`)*,
- mark/reset at a point in the input stream *(`mark, reset`)*,
- inject input for immediate transduction *(`in, signal`)*,
- or write extracted data to an output stream *(`out`)*.

All ribose models implicitly inherit the transductor effectors, along with an extensible set of control signals `{nul,nil,eol,eos}` and an anonymous named value that is preselected for every transduction and reselected when `select` is invoked with no parameter. New signals and named values referenced in transducer patterns implicitly extend the base signal and value collections. Additional effectors may be defined in specialized `ITarget` implementation classes.

The ribose transductor implements `ITarget` and its effectors are sufficient for most ribose models that transduce input to standard output via the `out[...]` effector. Domain-specific target classes may extend `BaseTarget` to express additional effectors, typically as inner classes specializing `BaseEffector<Target>` or `BaseParameterizedEffector<Target>`. All effectors are provided with a reference to the containing target instance and an `IOutput` view for extracting named values as `byte[]`, integer, floating point or Unicode `char[]` values, typically for inclusion in immutable value objects that are incorporated into the target model.

Targets need not be monolithic. In fact, every ribose transduction involves a composite target comprised of the transductor and at least one other target class (eg, `BaseTarget`). In a composite target one target class is selected as the representative target, which instantiates and gathers effectors from subordinate targets to merge with its own effectors into a single collection to merge with the transductor effectors. Composite targets allow separable concerns within complex semantic domains to be encapsulated in discrete interoperable and reusable targets.
## More Examples
Here are some more examples. These are abridged from the ginr source for the patterns in the ribose test suite (`patterns/test/*.inr`).
### Hello World
Ribose specializes ginr to accept only regular patterns comprised of ternary terms *(input, effector\[parameters\] ...)*, using the tape shifting operator `[]` to associate parameters with effectors (so `(X,Y[Z])` is equivalent to `(X,Y,Z)`). Such terms are combined and composed using regular operators to specify ribose transducer patterns. Below, the `(X,Y,Z)$(0,1 2) → (X,Y Z)` operation uses the projection operator `$` to flatten effectors and parameters onto a single tape in canonical order. The `:prsseq` operator is then applied to verify that the pattern is a single-valued (subsequential) function `X → Y Z` sequentially mapping input to target effectors and parameters and to provide a readable listing of the compiled FST transitions.
```
Hello = (nil, out[`Hello World`] stop);

(Hello$(0,1 2)):prsseq;
(START)  nil  [ out Hello World stop ]  (FINAL)

etc/sh/ribose --nil build/Test.model Hello -
Hello World
```
### Fibonacci
This next example computes unary Fibonacci numbers from unary inputs, using transductor compositing effectors to manipulate a collection of user-defined named values (`~X`) that accumulate data mapped from the input. This is interesting because, formally, FSTs effect linear transforms and can only produce regular outputs while the Fibonacci sequence is not regular (it is strictly context sensitive). This is possible because the Fibonacci FST generates a regular sequence of effectors that retain intermediate results in named value registers.
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

$ for i in '' 0 00 000 0000 00000 000000 0000000 00000000 000000000 0000000000; do \
> echo $i | etc/sh/ribose build/Test.model Fibonacci -; \
> done

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
This example was previewed above. It is taken from the ribose model compiler, which transduces serialized ginr automata to construct FSTs for inclusion in a ribose runtime model. Ginr serializes compiled pattern automata to tab-delimited text files beginning with a header line listing ginr version number and the number of tapes, transitions, states, and symbols. Below, the `Header` pattern determines how ribose deserializes the header line, transducing it into an immutable value object in the target model. This is presented in more detail in the ribose wiki (see [Ginr as a Service Provider](https://github.com/jrte/ribose/wiki#ginr-as-a-service-provider)). Here it is refined a bit to show how ginr's composition operator (`@`) can be used to replace formal parameters in a template pattern (`Field`).
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
The model compiler class, `ModelCompiler`, implements the `ITarget` interface and expresses a `HeaderEffector<ModelCompiler>` effector class. The compiled `Header` transducer maps the `header` token in the pattern to this effector's `invoke()` method and calls it when the final `nl` token is read. The effector uses its `IOutput` view to decode raw bytes from named values to integer-valued fields within a `Header` object bound to the target instance.
```
class Header {
  final int version;
  final int tapes;
  final int transitions;
  final int states;
  final int symbols;
  Header(int version, int tapes, int transitions, int states, int symbols)
  throws EffectorException {
  // Apply value range and sanity checks (omitted here)
    this.version = version;
    this.tapes = tapes;
    this.transitions = transitions;
    this.states = states;
    this.symbols = symbols;
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
This demonstrates the clear separation of syntactic (input) and semantic (target) concerns obtained with pattern oriented design. All of the fine-grained character-level code that would otherwise be involved to navigate the input stream is relegated to a transduction pattern expressed in a symbolic, algebraic framework supported by a robust compiler for multidimensional regular patterns. Target and effector implementations are thereby greatly reduced. In this example, as with most ribose solutions, the effector implementations and supporting value classes involve no external libraries outside the ribose runtime and are completely decoupled from the syntactic structure of the input. That is not to say that the target and effector implementations, composed in a native programming language, cannot interact with more complex components in the application or service domain. But these interactions are orthogonal and opaque to transduction patterns since they are either encapsulated by effectors or occur downstream from transduction processes.
### Navigating Noisy Inputs (Nullification)
It is often necessary to extract features that are embedded in otherwise irrelevant or noisy inputs. The ribose runtime supports this by injecting a `nul` signal into the input stream whenever no transition is defined for a received input. Any ribose pattern can be extended to handle a `nul` signal at any point by simply skipping over input with nil effect until a synchronization pattern marking the beginning of the innermost enclosing loop is recognized and then continuing as before. This extends the range of recognizable (but not necessarily acceptable) inputs to cover `byte*` while accepting only selected embedded features. In the example below, the `interval` pattern extracts `interval-ms` attribute values from specific `cycle-start` stanzas (with `type="scavenge"`) embedded in an XML document.

```
#<af-start id="3" threadId="0000000000329C80" totalBytesRequested="16" timestamp="2021-04-05T23:12:58.597" intervalms="5243.429" type="nursery" />
#<cycle-start id="4" type="scavenge" contextid="0" timestamp="2021-04-05T23:12:58.597" intervalms="5243.468" />
#<gc-start id="5" type="scavenge" contextid="4" timestamp="2021-04-05T23:12:58.597">

interval = (
  '<cycle-start id="' digit+ '" type="scavenge" contextid="' digit+ '" timestamp="' (digit+ ('.:-T':alph))+ digit+ '" '
  ('intervalms="', select[`~interval`] clear)
  (digit, paste)+ ('.', paste) (digit, paste)+
  ('"', out[`~interval` nl])
  space* '/>' (utf8 - '<')*
);

# tape alphabets
a0 = (interval$0):alph;
a1 = (interval$1):alph;
a2 = (interval$2):alph;

# map nl to NL in a2 to make tape symbols disjoint with a0
nlNL = (((a2 - nl)$(0,0))* (nl, NL)*)*;
# map NL to nl on parameter tape
NLnl = (((a2 - nl)$(0,,,0))* (NL, [[nl]])*)*;

# nullify to construct a synchronizing pattern for everything that is not an interval
null = (
  (
    # map all input symbols x->x|nul in (interval$0) and flatten result to one tape
    ((AnyOrNul* @ (interval @ nlNL))$(0 1 2))
    # copy everything but nul, projecting back onto 3 tapes
  @ ((a0$(0,0))* (a1$(0,,0))* NLnl)*
    # copy first nul back onto input tape, discard remainder of nullified pattern
    (nul$(0,0)) (nul* a0* a1* a2*)*
  )
  # after nul absorb input up to next '<'
  (utf8 - '<')*
);

# transduce recognized intervals, ignore null input
Tintervals = (
  (interval* null*)*
);
```
Nullification can be applied similarly to subpatterns to effect fine-grained context-sensitive error handling. The general technique of flattening a transducer pattern and transforming it with an editor pattern while projecting the result back onto the original tapes can be applied in many other ways. Like [CRISPR](https://www.newscientist.com/definition/what-is-crispr/) in genetics, it can be used to inject new behaviors or alter existing behaviors in transducer patterns. The `null` expression above preserves the interleaving of input and effectors in the nullified `interval` pattern up to the first `nul` signal and replaces the remainder with a pattern that synchronizes at the opening of the next XML stanza. If no effectors are involved the same result can be expressed more succinctly as shown in the next example.
## Resolving Ambiguous Inputs (Classification)
It is sometimes necessary to look ahead in the input, without effect, to syntactically validate a prospective feature or resolve an ambiguous pattern before selecting a course of action. The snippet below, from the `LinuxKernelStrict` transducer in the ribose test suite, demonstrates this using the `mark` and `reset` effectors. The _`header`_ and _`capture`_ subpatterns, referenced but not shown here, effect field extraction from iptables messages in Linux kernel logs.
```
#May 15 07:58:52 kb-ubuntu kernel: [ 1794.599801] DROPPED IN=eth0 OUT= MAC=01:00:5e:00:00:fb:00:13:20:c0:36:32:08:00 SRC=192.168.144.101 DST=224.0.0.251 LEN=32 TOS=0x00 PREC=0x00 TTL=1 ID=8596 OPT (94040000) PROTO=2
#May 16 07:59:13 kb-ubuntu kernel: [  285.950056] __ratelimit: 225 callbacks suppressed

# distribute paste effector over any input sequence of bytes
PasteAny = (byte, paste)*;

# reorder and write fields extracted from the header and capture subpatterns to stdout
store = out[
  `~timestamp` tab `~hostname` tab `~macaddress` tab `~tag` tab `~in` tab `~out` tab
  `~protocol` tab `~srcip` tab `~dstip` tab `~srcport` tab `~dstport` nl
];

LinuxKernelDropped = (
  header (space, select[`~tag`]) ('DROPPED' @@ PasteAny) capture (nl, store signal[`!nil`] stop)
):dfamin;

LinuxKernelLimited = (
  header (space, select[`~tag`]) ('LIMITED' @@ PasteAny) capture (nl, store signal[`!nil`] stop)
):dfamin;

LinuxKernelAborted = (
  header (space, select[`~tag`]) ('ABORTED' @@ PasteAny) capture (nl, store signal[`!nil`] stop)
):dfamin;

dropped = LinuxKernelDropped$0;
limited = LinuxKernelLimited$0;
aborted = LinuxKernelAborted$0;

line = (dropped | limited | aborted) / nl;
null = ((line:pref) - line) nul (byte - nl)*;
next = reset clear[`~*`] select[`~timestamp`];

LinuxKernelStrict = (
  (
    (nil, mark)
    (
      (dropped, next start[`@LinuxKernelDropped`])
    | (limited, next start[`@LinuxKernelLimited`])
    | (aborted, next start[`@LinuxKernelAborted`])
    | (null nl, signal[`!nil`])
    )
  )*
):dfamin;
```
This also demonstrates nesting of ribose transducers. The top-level `LinuxKernelStrict` transducer marks an input anchor and looks ahead to verify syntax before selecting one of three transducers. It then resets to the input anchor and pushes the selected transducer onto the transductor stack to reduce the marked input and inject a `nil` signal into the input before popping back into the top-level transducer. This is a contrived example; the `LinuxKernel` transducer in the ribose test suite effects the same transduction without lookahead.
## Some Metrics (Intermission)
The charts below summarize the results of a data extraction benchmarking runoff between Java regex and three ribose variants. These tests were run on an OpenJDK JVM (Temurin-11.0.17+8). The `LinuxKernel` transducer does no lookahead, `LinuxKernelLoose` looks ahead only as far as the distinguishing tag, and `LinuxKernelStrict` looks ahead up to the end of line. Output was muted for the throughput benchmarks shown in the chart on the left and enabled for the GC benchmark chart on the right. All GC benchmark outputs (regex and ribose) were binary equivalent. Initial heap size (2M) and region size (1M) were identical for all GC runs. All three ribose transducers presented similar heap usage profiles, incurring 2 G1 collections and driving the heap to a maximal 4M size (2 young, 1 survivor regions). Regex incurred 6 collections, reclaiming more than 77M of transient object space as the heap grew to 78M (3 young, 1 survivor regions).

![LinuxKernelBench](https://github.com/jrte/ribose/raw/master/etc/markdown/LinuxKernelLog.png)

Size is important in the ribose runtime because transducer transition functions are represented in 2-dimensional arrays (state ⊗ input) and these are often sparse. Only about 30% of the cells in the `LinuxKernelStrict` transition matrix are populated, the rest are dead cells that trigger `nul` when hit. Successive transitions may reference cells that are widely distributed in RAM and this can degrade the performance of the L1 data cache, forcing more traffic on the CPU-RAM bus. I vented some steam about this in one of my favorite rants, see _[A Few Words about Time and Space](https://github.com/jrte/ribose/wiki/Stories#a-few-words-about-time-and-space)_ on the wiki.

Ginr automata are state-minimized but the ribose model compiler is able to obtain some lossless compression of ginr FSTs by coalescing equivalent input symbols to eliminate redundancy in the state transition matrix and collapsing non-branching vectors of >1 input bytes or >1 effectors and parameters to eliminate connecting states and transitions. This is effected by eliminating the intermediate states in non-branching input vectors and matching input against a reference array outside the transition matrix (product trap). This also eliminates the uniqueness of input symbols involved in these vectors, so the granularity of the input equivalence relationship is reduced and the transition matrix is compressed in both dimensions. Additionally, a sum trap optimizes states with predominantly idempotent transitions, looping and testing each input against a bit map of idempotent inputs until an unmapped input is received to force transition to a new state.

![CompressionBenchmarks](https://github.com/jrte/ribose/raw/master/etc/markdown/compressed-throughput.png)

The sum and product traps operate in very tight loops with small working sets without reference to the transition matrix, which should result in fewer L1 cache misses. However, they may introduce turbulence in the instruction pipeline if they receive frequent shorts bursts of input, as when the product trap recognizes only a short prefix of a non-matching input sequence. The line chart on the left above shows ribose throughput versus regex with transition matrix compression and sum and product traps implemented. The benchmarking setup was as for the previous line chart. The bar chart on the right shows the number of transitions in the matrices of the ribose linux log transducers before and after compression.
## Some Use Cases (Design Patterns)
Transduction patterns cover a lot of ground, and information architects who employ them liberally will wonder why they ever got on the XML bus. Service providers and their consumers are _de facto_ domain experts and information architects should be free to express idiomatic nested regular patterns to serialize domain artifacts in persistent stores and messages in transit, as long as they syntactically differentiate semantic features and satisfy service and consumer needs. Service providers can publish API messages as nested transducer patterns mapping syntactic features in serialized domain artifacts to semantic labels (artifact names) with annotations (range or relational constraints) defined in a service domain dictionary.

Consumers of these services use the published API patterns directly, replacing semantic tokens and annotations with effectors expressed by domain-specific targets that recognize and interpret service data in the context of the consumer's semantic domain. Since all syntactic concerns are expressed in the service API, this involves simply extending the API input patterns to map onto the effectors of the domain-specific targets. Workflows, or scripted interactions involving one or more services, can be expressed as a controlling transductor and a collection of messaging transducers that receive request and or response messages and send control messages to the controller to direct the course of the workflow. With a domain dictionary such systems are self-documenting, since the deployed message and controller patterns provide complete and concise maps of the data and control planes. The principles of pattern oriented programming apply identically in the data and control planes. It's semirings, all the way down.

> _Orchestration is the automated configuration, management, and coordination of computer systems, applications, and services. Orchestration helps IT to more easily manage complex tasks and workflows._ (RedHat 2019, [What is Orchestration?](https://www.redhat.com/en/topics/automation/what-is-orchestration)).

Beyond raw text processing there are myriad other domains where a pattern orientation can reduce design and development costs. The base set of bytes can be mapped into other symbolic domains and used to encode messages and information artifacts for persistence, transfer and transduction. Realtime analog data can be quantized and the enumerated quanta can be mapped into a transduction model. Complex inputs may be reduced by a finite equivalence relation to a set of equivalence class enumerators. Such terms can be mixed with (disjoint) textual or binary data to define messages and protocols as required. 

Governance protocols for process control systems may be expressed as collections of transduction patterns that operate realtime controls in response to instrumentation inputs and interact with a controlling transductor that lords over them all. In any case, nested regular patterns provide architects and developers with well-founded methods to encode and transduce domain messages and orchestrate workflows and protocol interactions within a target domain. These patterns succinctly articulate information regarding domain artifacts and interactions that are difficult to express and maintain in most programming languages, and even more difficult to recover from source code review. 

Pattern-driven transduction would likely improve design and runtime efficiency in workflows that rely heavily on existing regex technology. Parsers and other applications that rely on existing lexical tools (eg, lex/flex) may find that ginr's structured patterns are easier to express and maintain. A compiler might transduce program source code with a lexical transductor that identifies source artifacts and encodes them in a stream optimized for concurrent consumption by a code generating transductor.

In a nutshell, lots of _algorithms_ can be replaced by _patterns_. The _logic_ is in the _syntax_.
# Everything is Code
In computing ecosystems regular patterns and their equivalent automata, like microbiota in biological ecosystems, are ubiquitous and do almost all of the work. String them out on another construct like a stack or a spine and they can perform new tricks.

Consider ribonucleic acid (RNA), a strip of sugar (ribose) molecules strung together, each bound to one of four nitrogenous bases (A|T|G|C), encoding genetic information. Any ordered contiguous group of three bases constitutes a *codon*, and 61 of the 64 codons are mapped deterministically onto the 21 amino acids used in protein synthesis (the other three are control codons). This mapping is effected by a remarkable molecular machine, the *ribosome*, which ratchets messenger RNA (mRNA) through an aperture to align the codons for translation and build a protein molecule, one amino acid at a time (click on the image below to see a realtime animation of this process). Over aeons, nature has programmed myriad mRNA scripts and compiled them into DNA libraries to be distributed among the living. So this trick of using sequential information from one domain (eg, mRNA->codons) to drive a process in another domain (amino acids->protein) is not new.

<p align="center">
  <a href="https://www.youtube.com/watch?v=TfYf_rPWUdY"><img src="https://github.com/jrte/ribose/raw/master/etc/markdown/bicycle-gears.png"></a>
</p>

For a more recent example, consider a **C** function compiled to a sequence of machine instructions with an entry point (call) and maybe one or more exit points (return). This can be decomposed into a set of vectors of non-branching instructions, each terminating with a branch (or return) instruction. These vectors are ratcheted through the control unit of a CPU and each sequential instruction is decoded and executed to effect specific local changes in the state of the machine. Branching instructions evaluate machine state to select the next vector for execution. All of this is effected by a von Neumann CPU, chasing an instruction pointer. As long as the stack pointer is fixed on the frame containing a function the instruction pointer will trace a regular pattern within the bounds of the compiled function. This regularity would be obvious in the source code for the function as implemented in a procedural programming language like **C**, where the interplay of concatenation (';'), union (if/else/switch) and repetition (while/do/for) is apparent. It may not be so obvious in source code written in other, eg functional, programming languages, but it all gets compiled down to machine code to run on von Neumann CPUs, on the ground or in the cloud.

From an extreme but directionally correct perspective it can be said that almost all software processes operating today are running on programmable calculators with keyboards and heaps of RAM. Modern computing machines are the multigenerational inheritors of von Neumann's architecture, which was originally developed to support numeric use cases like calculating ballistic trajectories. These machines are "[Turing complete](https://en.wikipedia.org/wiki/Brainfuck)", so all that is required to accommodate textual data is a numeric encoding of text characters. [Programmers can do the rest](https://www.cs.nott.ac.uk/~pszgmh/Parsing.hs). Since von Neumann's day we've seen lots of giddy-up but the focus in [machine development](https://github.com/jrte/ribose/raw/master/reference/50_Years_of_Army_Computing.pdf) has mainly been on miniaturization and optimizations to compensate for RAM access lag. Ribose offers an alternative approach to sequential information processing, refactoring syntactic concerns into patterns that coordinate the application of tightly focussed effector functions of reduced complexity. This approach is not new; John Backus described _Applicative State Transition Systems_ generically in his [Turing Award](https://cs.wellesley.edu/~cs251/s19/notes/backus-turing-lecture.pdf) paper (§14, ©ACM 1977, fair use). Ribose makes it real.

Programming instruction-driven machines to navigate complex patterns in sequential data or asynchronous workflows is an arduous task in any modern programming language, requiring a mess of fussy, fine-grained twiddling that is error prone and difficult to compose and maintain. Refactoring the twiddling into a nest of regular input patterns leaves a simplified collection of code snippets that just need to be sequenced correctly as effectors, and extending input patterns to orchestrate effector sequencing via transduction seems like a natural thing to do. Transducer patterns expressed in symbolic terms can be manipulated using well-founded and wide-ranging algebraic techniques, often without impacting effector semantics. Effector semantics are very specific and generally expressed in a few lines of code, free from syntactic concerns, in a procedural programming language. Their algebraic properties also enable regular patterns to be reflected in other mathematical domains where they may be amenable to productive analysis.

It is great mystery why support for `*`-semiring algebra is nonexistent in almost all programming languages and why hardware support for finite state transduction is absent from commercial computing machinery, even though a much greater proportion of computing bandwidth is now consumed to process sequential byte-encoded information. It may have something to do with money and the vaunted market forces that drive continuous invention and refinement. The folks that design and develop computing hardware and compilers are heavily invested in the von Neumann status quo, and may directly or indirectly extract rents for CPU and RAM bandwidth. They profit enormously as, globally, the machines arduously generate an ever-increasing volume of data to feed back into themselves. So the monetary incentive to improve support for compute-intensive tasks like parsing reams of text may be weak. Meanwhile, transduction technology has been extensively developed and widely deployed. It is the basis for lexical analysis in compiler technology and natural language processing, among other things. But it is buried within [proprietary or specialized software](https://semiring.com/) and is inaccessible to most developers.

Unfortunately I can only imagine what commercial hardware and software engineering tools would be like today if they had evolved with FST logic and pattern algebra built in from the get go. But it's a sure bet that the machines would burn a lot less oil and software development workflows would be more streamlined and productive. Information workflows would be designed and implemented simply, directly and efficiently without involving formal data exchange formats like XML or JSON (see _[Little Use Case](https://github.com/jrte/ribose/wiki#little-use-case-presenting-remote-service-apis)_ for an example). There's a _[Big Use Case](https://github.com/jrte/ribose/wiki#big-use-case-make-it-all-go-away)_ for that.

See _[Everything is Hard](https://github.com/jrte/ribose/wiki/Stories#everything-is-hard)_.
# The Ribose Manifesto
Ribose encourages *pattern oriented design and development*, which is based almost entirely on semiring algebra. Input patterns in text and other symbolic domains are expressed and manipulated algebraically and extended to map syntactic features onto vectors of machine instructions. A transducer stack extends the range of transduction to cover context-free input structures that escape semiring confinement. Effector access to RAM and an input stack support transductions involving context-sensitive inputs.

In this way the notion of a *program*, whereby a branching and looping series of *instructions select data* in RAM and mutate machine state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into target state. Here *target*, *effector* and *pattern* are analogues of *machine*, *instruction*, *program*. A *transducer* is a compiled *pattern* and a *transduction* is a *process* that applies a specific input sequence to a stack of nested transducers to direct the application of effectors to a target model in RAM.

Ribose suggests that the von Neumann CPU model would benefit from inclusion of finite state transduction logic to coordinate the sequencing of instructions under the direction of nested transducers driven by streams of numerically encoded sequential media, and that programming languages should express robust support for `*`-semiring algebra to enable construction of multidimensional regular patterns and compilation to FSTs as first-order objects. Transduction of data from input channel (file, socket, etc) interfaces into user space should be supported by operating system kernels. Runtime support for transduction requires little more than an transducer stack and a handful of `byte[]` buffers.

The [Burroughs Corporation B5000](https://en.wikipedia.org/wiki/Burroughs_large_systems), produced in 1961, was first to present a stack-oriented instruction set to support emergent compiler technology for stack-centric programming languages (eg, Algol). The call stack then became the locus of control in runtime process execution of code as nested regular patterns of machine instructions. Who will be first, in the 21st century, to introduce robust compiler support for regular patterns and automata? Will hardware vendors follow suit and introduce pattern-oriented instruction sets to harness their blazing fast calculators to data-driven transductors of sequential information? Will it take 75 years to learn how to work effectively in pattern oriented design and development environments? Will XML ever go away?

Architects who want a perfect zen koan to break their minds on should contemplate the essential value of abstract data representation languages that can express everything without knowing anything. Developers might want to bone up on `*`-semiring algebra. It may look intimidating at first but it is just like arithmetic with nonnegative integers and addition and multiplication and corresponding identity elements **0** and **1**, but with sets of strings and union and concatenation with identity elements **Ø** (empty set) and **ϵ** (empty string). The '__`*`__' semiring operator is defined as the union of all concatenation powers of its operand. Analogous rules apply identically in both domains, although concatenation does not commute in semirings. See _[Math is Hard?](https://github.com/jrte/ribose/wiki#math-is-hard)_ for a scolding.

Best of all, `*`-semiring algebra is stable and free from bugs and vulnerabilities. Ginr is mature and stable, although it needs much more testing in diverse symbolic domains, and its author should receive some well-deserved recognition and kudos. Ribose is a hobby horse, created only to lend some support to my claims about pattern oriented design and development.

<p align="center">
  <a href="https://en.wikipedia.org/wiki/Ribose"><img src="https://github.com/jrte/ribose/raw/master/etc/markdown/ribose.png"></a>
</p>

Good luck with all that.
# Disclaimer
Ribose is presented for demonstration only and is not regularly maintained. You may use it to compile and run the included examples, or create your own transducers to play with. Or clone and refine it and make it available to the rest of the world. Transcode it to **C** and wrap it in a Python thing. Do what you will, it's open source.

Clone the ribose repo and run `ant ribose` to build the ribose library and API (javadoc) in the `jars/` folder. The `test` ant target runs the CI test suite, `ci-test` runs a clean build with tests. This should work on any unix-ish platform, including `git bash` or `Msys2\mingw` for Windows, with `gcc`, `ant`, `java`, `bash`, `cat`, `wc`, `grep` in the executable search path. Binary executable copies of `ginr` (for Linux) and `ginr.exe` (for Windows) are included in `etc/ginr` (with the author's permission) for personal use. You are encouraged to clone or download and build ginr directly from the [ginr repo](https://github.com/ntozubod/ginr).

The `TRun` target is coupled with the transductor effectors and is sufficient for basic ribose runtime models containing only transductions that write output though the `out[...]` effector. Specialized `ITarget` implementations can express additional effectors that capture transduction output in simple value objects for assimilation into the target, which may also interact with other domain objects. In any case, to build a ribose model for a collection of transduction patterns, emulate the procedure for building `Test.model` (see the `compile-test-patterns` and `package-test-patterns` targets in `build.xml`).

Shell scripts are available in `etc/sh` to support compiling patterns, packaging transducers into models, and running transductions from ribose models:

- _patterns_: compile ginr patterns from a containing folder to DFAs
- _compile_: compile ginr DFAs to ribose transducers and package transducers into a ribose model
- _ribose_: start a ribose transducer on a runtime transductor to transduce an input file

To learn how to use ribose models in the JVM runtime build ribose and see the javadoc pages in `javadoc/` or `jars/ribose-0.0.1-api.jar`.

For some background reading and a historical perspective visit the [ribose wiki](https://github.com/jrte/ribose/wiki).

The current version of ribose is packaged in `jars/ribose-0.0.1.jar`.

See [LICENSE](https://github.com/jrte/ribose/raw/master/LICENSE) for ribose licensing details.

---
![YourKit](https://www.yourkit.com/images/yklogo.png)

The jrte project uses YourKit to identify and eliminate bottlenecks and turbulence in stream processing workflows and to reduce object creation to a bare minimum while maintaining high throughput.

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.
