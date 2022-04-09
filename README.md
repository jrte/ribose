# The General Idea
Ribose (formerly jrte) is about inversion of control for high-volume text analysis and information extraction and transformation. It is a ship-in-a-bottle showpiece thrown together to demonstrate what computing might be if finite-state transduction, augmented with a transducer stack and coupled with a classical CPU/RAM computer, was a common modality for processing sequential information (i.e. almost everything except arithmetic). The general idea is to show how to make information work for you rather than you having to work to instruct a computer about how to work with information. Or, at least, how to reduce costs associated with information workflows. The general idea is outlined below and explored, a bit snarkily, in the _[Discussions](https://github.com/jrte/ribose/discussions)_.

<p align="center">
  <img src="https://github.com/jrte/ribose/blob/master/etc/javadoc/api/resources/2-gears-white.jpeg">
</p>

An input source presents as a generator of sequences of ordinal numbers (eg, UNICODE ordinal, quantized analog signal, composite multivariate metric sample). If those sequences have a coherent pattern that can be described in terms of catenation, union and repetition then that description determines a unique regular set. This input pattern can then be extended to interleave at each input ordinal a sequence of output ordinals, which ribose associates with effector methods expressed by a target class bound to the transduction. This input/output pattern description is compiled by [ginr](https://github.com/ntozubod/ginr) to build a state-minimized finite state transducer or FST (ginr is a regular expression compiler that implements a wide range of operations on regular sets).

In this way the notion of an *algorithm*, whereby a branching and looping series of *instructions select data* in RAM and mutate application state, is replaced by a *pattern* that extends a description of an input source so that the *data select instructions (effectors)* that merge the input data into application state. Here *target*, *effector* and *transduction* are analogues of *machine*, *instruction*, *program*. Most complex patterns can be decomposed into subpatterns, yielding collections of small cooperating transducers that are stitched together on the ribose transduction stack in response to cues in the overall pattern. Traditional *APIs* describing method or web service interfaces are replaced by syntactic *patterns* describing specific media containers. These patterns are extended by developers to produce collections of transducers that map conformant data streams onto target effectors to reduce and assimilate incoming data into the target domain. 
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
This is _pattern-oriented programming_ and there is nothing poetic about it. It is based almost entirely on semi-ring algebra, so patterns can be expressed and  manipulated algebraically. When domain concerns escape the semi-ring we add a transducer stack and extend the range of applicable use cases to include context-free input structures and continue. The addition of an input stack allows transduction processes to inject media into the input stream, extending the range of potential use cases into context-sensitive territory (eg, the Fibonacci transducer maps the dull monoid **0*** onto the context-sensitive Fibonacci series). Similar advantages accrue from applying pattern algebra in other symbolic domains. Complex real-time control systems that effect rule-based governance can be expressed concisely as transducer patterns mapping event flows to control actions that effect governance.

For some background reading and historical perspective visit the [Discussions](https://github.com/jrte/ribose/discussions) page. To learn how to harness ribose in development workflows, see the tutorial and examples on the [ribose wiki](https://github.com/jrte/ribose/wiki).

The current version of ribose is packaged in ribose-0.0.0.jar.

See [LICENSE](https://github.com/jrte/ribose/blob/master/LICENSE) for licensing details.
