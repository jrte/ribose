# The General Idea
Ribose (formerly jrte) is about inversion of control for high-volume text analysis and information extraction and transformation. It is a ship-in-a-bottle showpiece thrown together to demonstrate what computing might be if finite-state transduction, augmented with a transducer stack and coupled with a classical CPU/RAM computer, was a common modality for processing sequential information (i.e. almost everything except arithmetic). The general idea is to show how to make information work for you rather than you having to work to instruct a computer about how to work with information. Or, at least, how to reduce costs associated with information workflows. The general idea is outlined below and explored, a bit snarkily, in the stories posted in the _[ribose wiki](https://github.com/jrte/ribose/wiki)_.

<p align="center">
  <img src="https://github.com/jrte/ribose/blob/master/etc/javadoc/api/resources/2-gears-white.jpeg">
</p>

An input source presents as a generator of sequences of categorical ordinal numbers obtained by mapping items in the source domain onto a finite set of categories. For text sources that present UTF-8 this can be the identity map on 0..247 with illegal 248..255 mapped to 248. For other sources the input ordinals and categories will be specific to the domain. An event-driven workflow may categorize events by name or id, signal processing systems may quantize signal to category, an automated real-time control system may reduce composite multivariate metric samples by encoding contemporaneous metric properties to a finite set of categories. 

If input sequences have a coherent pattern that can be described in terms of catenation, union and repetition then that pattern determines a unique determinstic finite automaton (DFA), and the state transition function for the DFA determines a further categorical reduction of inputs since some groups of input ordinals will behave identically in every state. The input pattern can then be extended to interleave at each input ordinal a sequence of output ordinals, which ribose associates with effector methods expressed by a target class bound to the transduction. This input/output pattern description is compiled to build a state-minimized finite state transducer (FST) that can recogize syntactic features in the input and direct the invocation of target effectors to assimilate input into the target.

In this way the notion of a *program*, whereby a branching and looping series of *instructions select data* in RAM and mutate application state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into application state. Here *target*, *effector* and *transduction* are analogues of *machine*, *instruction*, *program*. Most complex patterns can be decomposed into subpatterns, yielding collections of small cooperating transducers that are stitched together on the ribose transduction stack in response to cues in the overall pattern. For a use case, consider replacing traditional *APIs* for web or microservice interfaces with syntactic *patterns* describing the message content in specific media containers. These patterns can be extended by developers to produce collections of transducers that map conformant data streams onto target effectors to reduce and assimilate incoming data into the target domain. 

[Ginr](https://github.com/ntozubod/ginr) is an industrial strength compiler for multi-dimensional regular expressions. It was developed by J Howard Johnson at the University of Waterloo long ago on a 16-bit VAX computer. One of its first applications was to transduce the [Oxford English Dictionary](https://cs.uwaterloo.ca/research/tr/1986/CS-86-20.pdf) from an archaic layout to SGML. It then disappeared from view until it was released as open source on GitHub. More recently it has been substantially upgraded to allow construction of DFAs with >64K states and >64K input categories. I first used ginr to build a ribose-like framework for context-free transduction in the late 1980s and saved my team a great big pile of work, but with ginr out of reach it was not possible to develop it further at the time. Now ginr is open source and I am presenting it with ribose as a back-to-the-future way forward. Hopefully, it can be used to teach new developers how to recognize and articulate regular patterns. These skills cut to the core of what computing is and will help them get their work done more simply and effectively.

Transduction with ginr and ribose is nothing like applying [`regex`](https://perldoc.perl.org/perlre) or [`PEG`](https://en.wikipedia.org/wiki/Parsing_expression_grammar) patterns, which provide very limited means for interacting with applications and have no application outside of text domains. Ribose transducers can be programmed to map any given context-free pattern **P** onto different target models that express target-specific effectors. The software `stAX` that would otherwise be required to parse input are eliminated since all of the character-by-character picking through arrays of bytes is subsumed in the transduction pattern. Freed from these dependencies developers discover that the remaining work is relatively simple and tightly focused and they can quickly assemble the effectors required to complete the target model. Applications and services that transduce high volumes of data over sockets or other serial channels can significantly reduce CPU/RAM provisioning because much irrelvant parsing that would otherwise be performed in a generic software stack is elminiated in specialized transducers. 

![LinuxKernelBench](https://user-images.githubusercontent.com/24707461/169666924-49f934dc-0f43-4ce0-ad65-0809508a2541.png)

The above charts summarize the LinuxKernel* runs from the build test suite. These transductions reduce a 20Mb kernel log file to 7.5Mb of extracted IPtables information. There is one run with regex and one run with each of three ribose transducers, and all produce identical outputs. The first chart shows run times for 20 runs with all input held in RAM and output muted. The second chart shows G1 GC activity for a single run with a streaming input source and 64Kb input buffer with output enabled. Only one series is plotted for the ribose transductions as they are all identical -- there were only 2 GCs, the first reclaimed <1Mb and both ocurred within the first 200ms of the run. The shortest run time in the GC collection trials was 7.8s (ribose LinuxKernel, no backtracking) and the longest was 10.7s (regex).

Below is a simple pattern showing how ginr patterns are specialized for ribose. Ribose patterns are composed of regular patterns involving two or three tapes

>`(... input, effector[parameter...] ...)`, 

each specifying a delimited pattern of input symbols and a vector of possibly parameterized effectors to be invoked when the terminal input symbol is received. 

The Fibonacci transducer uses the built-in ribose editing effectors to compute **fib(N)** in unary symbols.
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

$ for n in '' 0 00 000 0000 00000 000000 0000000 00000000 000000000 0000000000; do echo $n | ribose Fibonacci; done

0
0
00
000
00000
00000000
0000000000000
000000000000000000000
0000000000000000000000000000000000
0000000000000000000000000000000000000000000000000000000
```
This is _pattern-oriented programming_. It is based almost entirely on semi-ring algebra, so patterns can be expressed and  manipulated algebraically. When domain concerns escape the semi-ring we add a transducer stack and extend the range of applicable use cases to include context-free input structures and continue. The addition of an input stack allows transduction processes to inject media into the input stream, extending the range of potential use cases into context-sensitive territory (eg, the Fibonacci transducer maps the dull monoid **0*** onto the context-sensitive Fibonacci series). Similar advantages accrue from applying pattern algebra in other symbolic domains. Complex real-time control systems that effect rule-based governance can be expressed concisely as transducer patterns mapping event flows to control actions that effect governance.

Run `ant -f build.xml ribose` to build the ribose library in the `jars/` folder. The `test` ant target runs the CI test suite.

To learn how to harness ribose in development workflows, see the tutorial and examples on the [ribose wiki](https://github.com/jrte/ribose/wiki).

For some background reading and historical perspective visit the [Stories](https://github.com/jrte/ribose/wiki/Stories) page.

The current version of ribose is packaged in `jars/ribose-0.0.0.jar`.

See [LICENSE](https://github.com/jrte/ribose/blob/master/LICENSE) for licensing details.
