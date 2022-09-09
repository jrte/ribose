# The General Idea
Ribose (formerly jrte) is about inversion of control for high-volume text analysis and information extraction and transformation in general. Many stream oriented tasks, such as cleaning and extraction for data analytic workflows, involve recognizing and acting upon features embedded, more or less sparsely, within a larger context. Software developers receive some onerous help in that regard from generic software libraries that support common document standards (eg, XML, JSON, MS Word, etc), but dependency on these libraries adds complexity, vulnerability and significant runtime costs to software deployments. And these libraries are of no use at all when information is presented in idiomatic formats that require specialized software to deserialize.

Ribose specializes _[ginr](https://github.com/ntozubod/ginr)_, an industrial strength open source compiler for multidimensional regular patterns, to produce finite state transducers (FSTs) that map syntactic features to semantic effector methods expressed by a target class. Ribose transduction patterns are composed and manipulated using algebraic (\*-semiring) operators and compiled to FSTs for runtime deployment. Patterns may be nested to cover context-free inputs, and the ribose runtime supports unbounded lookahead to resolve ambiguities or deal with context-sensitive inputs.

Ribose is a ship-in-a-bottle showpiece put together to demonstrate what computing might be if finite state transduction, augmented with a transducer stack and coupled with a classical CPU/RAM computer, was a common modality for processing sequential information (i.e. almost everything except arithmetic). This has no connection whatsoever with POSIX and Perl 'regular expressions' (regex) or 'pattern expression grammars' (PEG), that are commonly used for ad hoc pattern matching. In the following I refer to the algebraic expressions used to specify ribose transducers as 'regular patterns' to distinguish them from regex and PEG constructs. 

The general idea is to show how to make information work for you rather than you having to work to instruct a computer about how to work with information. Or, at least, how to reduce costs associated with information workflows. This is outlined below and explored, a bit snarkily, in the stories posted in the _[ribose wiki](https://github.com/jrte/ribose/wiki)_.
## Some Motivation, and A Simple Example
Ribose demonstrates a pattern oriented approach to information that minimizes dependency on external libraries and reduces complexity, vulnerability and development and runtime costs in information workflows. Ribose generalizes the *transducer* design pattern that is commonly used to filter, map and reduce collections of data in functional programming paradigms. Common usage of this design pattern treats the presentation of inputs as a simple series **T\*** without structure. Ribose extends and refines this design pattern, allowing transducers to precisely navigate (filter), map (select effect vector) and reduce (execute effect vector) complex information under the direction of syntactic cues in the input. 

Here the `filter` component is expressed as a collection of nested regular patterns describing an input source, using symbolic algebraic (\*-semiring) expressions to articulate the syntactic structure of the input. These unary input patterns are then extended to binary transduction patterns that `map` syntactic features to effect vectors that incrementally `reduce` information extracted from the input. The syntactic structure provides a holistic map of the input and exposes cut points where semantic actions should be applied. This presents a clear separation of syntactic and semantic concerns: Syntax is expressed in a purely symbolic domain where patterns are described and manipulated algebraically, while semantics are implemented in effectors expressed poetically in a native programming language in a domain-specific target class. Effector implementation of semantic actions is greatly simplified in the absence of syntactic concerns.

The ribose runtime operates multiple concurrent transductions, each encapsulated in a `Transductor` object that coordinates a transduction process. Nested FSTs are pushed and popped on transductor stacks, with two-way communication between caller and callee effected by injecting input parameters and return values into the input stream for immediate transduction. Incremental effects are applied immediately as each input symbol (byte or out-of-band signal) is read, culminating in complete reduction and assimilation of the input into the target domain. For regular and most context-free input patterns transduction is effected in a single pass without lookahead. Unbounded lookahead and backtracking, supported by `mark` and `reset` effectors, can be used to resolve context-sensitive or ambiguous input patterns.

A simple example, taken from the ribose transducer that reduces the serialized form of compiled ginr automata to construct ribose transducers: The input pattern `('INR' (digit+ tab):5 nl)` is extended to check for a specific tag and marshal 5 integer fields into an immutable value object. Fields are extracted as named values in raw `byte[]` arrays using the `select`, `paste`, and `cut` effectors before the domain-specific `header` effector is finally invoked to decode them to integer-valued fields in an immutable `Header` object.
```
# INR210	3	565	127	282
Header = ('INR', clear select)
	(digit, paste)+ (tab, select[`~version`] cut select)
	(digit, paste)+ (tab, select[`~tapes`] cut select)
	(digit, paste)+ (tab, select[`~transitions`] cut select)
	(digit, paste)+ (tab, select[`~states`] cut select)
	(digit, paste)+ (nl, select[`~symbols`] cut select header)
);
```
This example is developed more fully below and in the ribose wiki. It is previewed here to show the algebraic binary structure of ribose transducer patterns. The branching and repeating patterns expressed in the input syntax drive the selection of non-branching effect vectors, obviating all of the fine-grained control logic that would otherwise be expressed in line with effect in a high-level programming language without support from an external parsing library. Expressions such as this can be combined with other expressions using concatenation, alternation, repetition and composition operators to construct more complex patterns. More generally, ribose patterns are amenable to algebraic manipulation in the *-semiring, and ginr enables this to be exploited to considerable advantage. For example `Transducer = Header Transition*` covers a complete serialized automaton, `Transducer210 = 'INR210' (digit* tab* nl*)* @@ Transducer` restricts `Transducer` to accept only version 210 automata (ginr's `@` composition operator absorbs matching input and reduces pattern arity, the `@@` join operator retains matching input and conserves arity). 

Ginr is the star of the ribose circus. It was developed by J Howard Johnson at the University of Waterloo in the early 1980s on a 16-bit VAX computer. One of its first applications was to [transduce the typesetting code for the entire Oxford English Dictionary](https://cs.uwaterloo.ca/research/tr/1986/CS-86-20.pdf) from an archaic layout to SGML. I first used it at Bell Northern Research to implement a ribose-like framework to serve in a distributed database mediation system involving diverse remote services and data formats. The involved services were all driven by a controller transducing conversational scripts from a control channel. The controller drove a serial data link, transmitting commands and queries to remote services on a serial output channel and switching context-specific response transducers onto the serial input channel. Response transducers reduced input data and injected guidance into the control channel to condition the course of the ongoing conversation.

Ginr subsequently disappeared from the public domain and has only recently been republished with an open source license on GitHub. It has been upgraded with substantial improvements, including expansion of state and input cardinality from 2^16 to 2^32 and native support for transcoding Unicode inputs to UTF-8 bytes. It provides a full complement of algebraic operators that be applied to reliably produce very complex (and very large) automata. Large and complex patterns can be decomposed into smaller and simpler patterns, compiled to FSTs, and reconstituted on ribose runtime stacks, just as complex procedural algorithms in Java are decomposed into simpler methods and composed by Java threads on call stacks in the Java runtime.
## The Basics
Ginr operates in a symbolic domain involving a finite set of symbols and algebraic semiring operators that recombine symbols to express syntactic patterns. Support for Unicode symbols is built in, and Unicode in ginr source patterns is rendered as UTF-8 byte sequences in compiled automata. Ribose transduction patterns introduce additional atomic symbols as tokens representing transducers, effectors, byte-encoded effector parameters, out-of-band (>255) input signals, or named values. 

Ribose transducers operate on `byte*` inputs, which may include binary data and/or UTF-8 encoded text. UTF-8 text is transduced without decoding and extracted bytes are decoded only when required. Input need not be textual and may be hierarchical; for example, a byte-level tokenizing transducer may generate a stream of tokens that are then transduced by a top-level compiler, or low-level event handlers in a distributed event-driven workflow may transduce incoming messages and emit signals to a controlling transducer to effect governance. In some cases complex inputs, such a series of multivariate tuples emitted by a remote sensor, can be reduced by a finite equivalence relation to a series of equivalence class enumerators for pattern-driven transduction.

A ribose model is comprised of FSTs compiled from a collection transduction patterns, an application-defined target class expressing domain-specific effectors, static effector parameters, an enumeration of out-of-band (>255) signals that may be injected into input byte streams for transduction control, and a collection of named values for accumulating extracted bytes. The base target class (`BaseTarget`) provides a core set of effectors to
- extract and compose data in selected named values *(select, paste, copy, cut, clear)*, 
- count down from preset value and signal end of countdown *(count)*
- push/pop transducers on the transduction stack *(start, stop)*, 
- mark/reset at a point in the input stream *(mark, reset)*, 
- write extracted data to an output stream *(out)*,
- or inject input for immediate transduction *(in)*.

All ribose models implicitly inherit these core ribose effectors, along with an enumeration of signals (`nil`, `nul`, `eol`, `eos`) and an anonymous named value that is preselected for every transduction and reselected when `select` is invoked with no parameter. The `BaseTarget` class, without extension, is sufficient for ribose models that include only simple text transforms that terminate at the `out[...]` effector -- additional signals and named values referenced in the collected patterns are implicitly included in all models.

More complex targets presenting domain-specific effectors can be implemented in a target class that extends `BaseTarget` and overrides the `ITarget` interface methods `String getName()` and `IEffector[] getEffectors()`. All effectors are provided with a reference to the containing target instance and an `IOutput` view of named values as `byte[]`, integer, floating point, or Unicode `char[]`, typically for inclusion in immutable value objects that are incorporated into the target model.
## Some Examples
Here are some examples to get things started. Ribose specializes ginr to accept only regular patterns comprised of ternary terms *(input, effector\[parameters\] ...)*, using the tape shifting operator `[]` to associate parameters with effectors (so `(X, Y[Z])` is equivalent to `(X, Y, Z)`). Such terms are combined and composed using regular operators to specify and recombine ribose transducers. Below, the `(X, Y, Z))$(0, 1 2) -> (X, Y Z)` operation uses the projection operator `$` to flatten effectors and parameters onto a single tape in canonical order. The `:prsseq` operator is then applied to verify that the pattern is a single-valued (subsequential) function sequentially mapping input to target effectors `X -> Y Z` and to provide a readable listing of the compiled FST transitions. 
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
Ginr represents compiled patterns as FSTs in tab-delimited ASCII text files beginning with a header line listing ginr version number and the number of tapes, transitions, states, and symbols. This example, demonstrating simple field extraction and marshaling, was previewed above (`INR (digit+ tab):5 nl`):
```
# INR210	3	565	127	282
Header = ('INR', clear[`~version`] clear[`~tapes`] clear[`~transitions`] clear[`~states`] clear[`~symbols`] clear select)
	(digit, paste)+ (tab, select[`~version`] cut select)
	(digit, paste)+ (tab, select[`~tapes`] cut select)
	(digit, paste)+ (tab, select[`~transitions`] cut select)
	(digit, paste)+ (tab, select[`~states`] cut select)
	(digit, paste)+ (nl, select[`~symbols`] cut select header)
);
```
This transducer pattern initially, on receipt of the INR version prefix, `clear`s a collection of named values and pre`select`s the anonymous value to receive digits `paste`d from the input stream. On receipt of a tab or newline delimiting a numeric field the appropriate named value is `select`ed to receive data `cut` (implicitly cleared) from the anonymous value, which is then reselected to receive the next field. All of the above is accomplished using the built-in compositing effectors. When this is done a specialized effector, `header`, is invoked to marshal the header fields into the target model (`ModelCompiler`). This is supported by an immutable data object and simple Java `HeaderEffector` class that implements the `IEffector` interface (value range and sanity checks omitted):
```
class Header {
	final int version;
	final int tapes;
	final int transitions;
	final int states;
	final int symbols;
	Header(int version, int tapes, int transitions, int states, int symbols) 
		throws EffectorException
	{
		this.version = version;
		this.tapes = tapes;
		this.transitions = transitions;
		this.states = states;
		this.symbols = symbols;
	//	Apply value range and sanity checks here
	}
}

class HeaderEffector extends BaseEffector<ModelCompiler> {
	INamedValue fields[];

	HeaderEffector(ModelCompiler automaton) {
		super(automaton, Bytes.encode("header"));
	}

	@Override // Called once, when the containing model is loaded into the ribose runtime
	public void setOutput(IOutput output) throws TargetBindingException {
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
	public int invoke() throws EffectorException {
		Header h = new Header(
			(int)fields[0].asInteger(),
			(int)fields[1].asInteger(),
			(int)fields[2].asInteger(),
			(int)fields[3].asInteger(),
			(int)fields[4].asInteger()
		);
		target.header = h;
		target.stateTransitionMap = new HashMap<Integer, ArrayList<Transition>>((h.states * 5) >> 2);
		target.transitions = new Transition[h.transitions];
		return IEffector.RTE_EFFECT_NONE;
	}
}
```
The snippets above support the ribose model compiler, which imports compiled ginr automata and reduces them to construct ribose transducers for inclusion in ribose runtime models. This example is presented in more detail in the ribose wiki (see [Ginr as a Service Provider](https://github.com/jrte/ribose/wiki#ginr-as-a-service-provider)). This demonstrates the clear separation of syntactic (input) and semantic (target) concerns. All of the fine-grained character-level code that would otherwise be involved to navigate the input stream is relegated to transduction patterns expressed in a symbolic, algebraic framework supported by a robust compiler for multidimensional regular patterns. Target and effector implementations are thereby greatly reduced. For comparison, here is a snippet of C source from the ginr repo that decodes the number of tapes from the header pattern `(digit+ tab)`:
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
# <cycle-start id="4" type="scavenge" contextid="0" timestamp="2021-04-05T23:12:58.597" intervalms="5243.468" />
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
		# inject nul? everywhere in the input and flatten to one tape
		((AnyOrNul* @ interval)$(0 1 2))
		# copy everything up to and including the first nul, projecting back onto 3 tapes
	@	((a0$(0,0))* (a1$(0,,0))* (a2$(0,,,0))*)*
		(nul$(0,0)) (nul* a0* a1* a2*)* 
	)
	# absorb input up to next '<' after nul
	(nul* (utf8 - '<')*)*
);

# revert NL to nl on (interval$2), transduce recognized intervals, ignore null input
Tintervals = ( 
	(null* interval*)* @ (((a2 - NL)$(0,0))* (NL, nl)*)*
):dfamin;

# verify transducer is single-valued and print subsequential map
Tintervals$(0,1 2):prsseq `build/patterns/automata/Tintervals.pr`;

# persist transducer for incorporation in ribose model
Tintervals:save `build/patterns/automata/Tintervals.dfa`;
```
Nullification for subpatterns requiring specific synchronization patterns can be applied similarly to effect fine-grained context-sensitive error handling.
## Resolving Ambiguous Inputs (Classification)
It is sometimes necessary to look ahead in the input, without effect, to syntactically validate a prospective feature or resolve an ambiguous pattern before selecting a course of action. Ribose supports this in two ways: using `mark/reset` effectors or using the `paste` effector to copy input until a classifying enumerator can be selected and injected into the input, followed by the copied input. The snippet below, from the `LinuxKernelStrict` transducer in the ribose test suite, demonstrates the `mark/reset` method. 
```
LinuxKernelDropped = (
	header (space, select[`~tag`]) ('DROPPED' @@ PasteAny) capture (nl, store in[`!nil`] stop)
);

LinuxKernelLimited = (
	header (space, select[`~tag`]) ('LIMITED' @@ PasteAny) capture (nl, store in[`!nil`] stop)
);

LinuxKernelAborted = (
	header (space, select[`~tag`]) ('ABORTED' @@ PasteAny) capture (nl, store in[`!nil`] stop)
);

LinuxKernelInput = ((LinuxKernelDropped$0) | (LinuxKernelLimited$0) | (LinuxKernelAborted$0));

LinuxKernelPrefix = (LinuxKernelInput / nl):pref;

LinuxKernelStrict = (
	(
		(nil, mark clear[`~*`] select[`~timestamp`])
		(
			(
				((LinuxKernelDropped$0), reset start[`@LinuxKernelDropped`])
			|	((LinuxKernelLimited$0), reset start[`@LinuxKernelLimited`])
			|	((LinuxKernelAborted$0), reset start[`@LinuxKernelAborted`])
			)
		|	LinuxKernelPrefix nul notnl* (nl, in[`!nil`])
		)
	)*
):dfamin;
```
This also demonstrates nesting of ribose FSTs. The top-level `LinuxKernelStrict` transducer marks an input anchor and looks ahead to verify syntax before selecting one of three transducers. It then resets to the input anchor and starts the selected transducer to reduce the marked input before signaling `nil` and returning to the top-level transducer. The charts below show the results of a data extraction benchmarking runoff between Java regex and three ribose variants. 

![LinuxKernelBench](https://user-images.githubusercontent.com/24707461/169666924-49f934dc-0f43-4ce0-ad65-0809508a2541.png)

The `LinuxKernel` FST does no lookahead, `LinuxKernelLoose` looks ahead only as far as the distinguishing tag, and `LinuxKernelStrict` looks ahead up to the end of line. All benchmark outputs (regex and ribose) were binary equivalent.
## Everything is Code
Consider ribonucleic acid (RNA), a strip of sugar (ribose) molecules strung together, each bound to one of four nitrogenous bases (A|U|G|C|), encoding genetic information. Any ordered contiguous group of 3 bases constitutes a *codon*, and the 64 codons are mapped deterministically onto the 21 amino acids used in protein synthesis. This mapping is effected by remarkable molecular machine, the *ribosome*, which ratchets messenger RNA (mRNA) through an aperture to align the codons for translation and build a protein molecule, one amino acid at a time (click on the gears below to see a real-time animation of this process). Over aeons, nature has programmed myriad mRNA scripts and compiled them into DNA libraries to be distributed among the living. So this trick of using sequential information from one domain (eg, mRNA->codons) to drive a process in another domain (amino acids->protein) is not new. 

<p align="center">
  <a href="https://www.youtube.com/watch?v=TfYf_rPWUdY"><img src="https://github.com/jrte/ribose/blob/master/etc/javadoc/api/resources/2-gears-white.jpeg"></a>
</p>

For a more recent example, consider machine code expressed as sequences of instructions with an entry point (call) and maybe one or more exit points (return). This could represent a function compiled from an algorithmic programming language, eg **C**, that can be decomposed into a set of vectors of non-branching instructions, each terminating with a branch (or return) instruction. These vectors are ratcheted through the control unit of a CPU and each sequential instruction is mapped (decoded and executed) to effect specific local changes in the state of the machine. Branching instructions evaluate machine state to select the next vector for execution. All of this is effected by a von Neumann CPU, which sequentially decodes and executes instructions as they are presented by an instruction pointer.

Regular patterns and their equivalent automata, like bacteria in biological ecosystems, are ubiquitous in computing ecosystems and do almost all of the work. As long as the stack pointer is fixed on the frame containing a function the instruction pointer will trace a regular pattern within the bounds of the compiled function. This regularity would be obvious in the source code for the function as implemented in a procedural programming language like **C**, where the interplay of concatenation (';'), alternation (if/else/switch) and repetition (while/do/for) is apparent. It may not be so obvious in source code written in other, eg functional, programming languages, but it all gets compiled down to machine code to run on von Neumann CPUs, on the ground or in the cloud. 

From an extreme but directionally correct perspective it can be said that almost all software processes operating today are running on programmable calculators with keyboards and lots of RAM. Since von Neumann's day we've seen lots of giddy-up but the focus in machine development has mainly been on miniaturization and optimizations to compensate for RAM access lag. Modern computing machines are the multigenerational inheritors of von Neumann's architecture, which was originally developed to support numeric use cases. But these machines are "Turing complete" ([just like BrainF*ck!](https://en.wikipedia.org/wiki/Brainfuck)), so all that was required to accommodate textual data was a numeric encoding of text characters. Programmers could do the rest. 

Navigating complex text natively is an arduous task in any modern programming language, requiring reams of fussy character-level twiddling that is error prone and difficult to compose and maintain. It is great mystery why support for semi-ring algebra is nonexistent in almost all programming idioms and why hardware support for finite state transduction is absent in commercial CPUs. It may have something to do with money and the vaunted market forces that drive continuous invention and refinement. When the folks that design and develop computing hardware and compilers also lease CPU and RAM bandwidth the monetary incentive to improve support for compute-intensive tasks like parsing reams of text may be weak.

Algorithmic programming languages face a similar difficulty in expressing control solutions for event-driven workflows in distributed environments, where events are reported asynchronously with respect to the controlling process that receives and responds to external events. In these contexts, the programming language can only express the local actions that effect controller responses to events. The workflow control structures that describe the pattern of events that drive the workflow must be expressed in some other idiom, like a flow chart, that can be compiled to something like a finite state transducer mapping events to local controller actions. This latter construction can then be represented, perhaps as an incomprehensible cascade of `switch` statements branching down to specific responses from a top-level event loop, in the programming language to complete the system.

Ribose suggests that the von Neumann model would benefit from inclusion of a finite state transduction unit (FTU) to coordinate the sequencing of instructions under the direction of FSTs driven by streams of numerically encoded sequential media. In this way the notion of a *program*, whereby a branching and looping series of *instructions select data* in RAM and mutate application state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into application state. Here *target*, *effector* and *pattern* are analogues of *machine*, *instruction*, *program*. A *transduction* is a *process* that applies a specific input sequence to a stack of nested FSTs compiled from regular patterns to direct the application of effectors to a target model in RAM.

Ribose encourages _pattern-oriented programming_, based almost entirely on semi-ring algebra. Patterns can be expressed and  manipulated algebraically. A transducer stack extends the range of applicable use cases to include context-free input structures that escape semi-ring confinement. The addition of an input stack allows transduction processes to inject media for immediate transduction, and effector access to RAM extends the range of transduction use cases into context-sensitive territory. Similar advantages accrue from applying pattern algebra in other symbolic domains. Complex real-time control systems that effect rule-based governance can be expressed concisely as transducer patterns mapping event flows to control actions that effect governance.
# Disclaimer
Ribose is intended for demonstration only and is not regularly maintained. You may use it to compile and run the included examples, or create your own trasducers to play with. See [LICENSE](https://github.com/jrte/ribose/blob/master/LICENSE) for licensing details.

Run `ant -f build.xml ribose` to build the ribose library in the `jars/` folder. The `test` ant target runs the CI test suite.

To learn how to harness ribose in development workflows, see the tutorial and examples on the [ribose wiki](https://github.com/jrte/ribose/wiki).

For some background reading and historical perspective visit the [Stories](https://github.com/jrte/ribose/wiki/Stories) page.

The current version of ribose is packaged in `jars/ribose-0.0.0.jar`.

![YourKit](https://www.yourkit.com/images/yklogo.png)

The jrte project uses YourKit to identify and eliminate bottlenecks and turbulence in stream processing workflows and to reduce object creation to a bare minimum while maintaining high throughput.

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.
