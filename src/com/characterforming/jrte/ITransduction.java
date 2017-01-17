/**
 * Copyright (c) 2011, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

/**
 * Interface for runtime transductions. A transduction binds an IInput stack,
 * transducer stack, and an ITarget instance. When the run() method is called
 * the transduction will read input ordinals from the IInput on the top of the
 * input stack and invoke the effectors triggered by each input transition
 * until one of the following conditions is satisfied:
 * <ol>
 * <li>the input stack is empty
 * <li>the transducer stack is empty
 * <li>an effector returns RTE_PAUSE
 * <li>an exception is thrown
 * </ol>
 * As long as the input and transducer stacks are not empty it may be possible
 * to call the run() method again after it returns if, for example, the pause
 * effector causes the previous call to return or a DomainErrorException was
 * thrown and you want to persevere after driving the input to a recognizable
 * location (eg, end of nearest containing loop).
 * <p>
 * Domain errors (inputs with no transition defined) are handled by emiting a
 * nul signal, giving the transduction an opportunity to handle it with an
 * explicit transition on nul. If a domain error occurs on a nul signal, a
 * {@link DomainErrorException} is thrown. 
 * <p>
 * For most transducers, domain errors can be avoided entirely by transducing 
 * the transducer. For example, with line-oriented text, all possible interleavings
 * of domain errors in the input can be modeled by replacing each non-nl input (x)
 * with (x|nul) in the original transducer Ts. The resulting transducer Te can then 
 * be pruned to produce a transducer Ts that accepts ((text - nl)* nl)*. 
 * <p>
 * The general formula is:
 * <pre class="code">
 * 
 * Ts=('a',paste) ((('b',paste)* ('c',paste)*)* ('d', in[`!nil`]))?;
 * t0=(Ts$0):alph;
 * t1=(Ts$1):alph;
 * Tn = {({nul,'a'}, 'a'), ({nul,'b'}, 'b'), ({nul,'c'}, 'c'), ({nul,'d'}, 'd')}*;
 * Te=(((Tn* @ Ts)$(0 1)) @ ((t0$(0,0))* (t1$(0,,0))*)* (nul$(0,0)) (t0* t1*)*) (nul* t0*)*;
 * Tt=Ts (nil nl, out[`~`] clear[`~`]) | Te (nl, clear[`~`]);
 * </pre>
 * <p>
 * The resulting transducer Tt will transition on nul to a state that accepts anything without
 * effect until end of line:
 * <pre class="code">
 * 
 * Tt:pr;
 * (START) 0.a 2
 * (START) 0.nul 3
 * 2 1.paste 4
 * 3 0.\n 5
 * 3 0.a 3
 * 3 0.b 3
 * 3 0.c 3
 * 3 0.d 3
 * 3 0.nul 3
 * 4 0.b 6
 * 4 0.c 6
 * 4 0.d 7
 * 4 0.nul 3
 * 4 0.nil 8
 * 5 1.clear 9
 * 6 1.paste 10
 * 7 1.in 11
 * 8 0.\n 12
 * 9 2.~ 13
 * 10 0.b 6
 * 10 0.c 6
 * 10 0.d 7
 * 10 0.nul 3
 * 11 2.!nil 14
 * 12 1.out 15
 * 13 -| (FINAL)
 * 14 0.nil 8
 * 15 2.~ 5
 * </pre>
 * <p>
 * Note that in the example above, the paste effector may be invoked before a domain 
 * error occurs, resulting in an incomplete transaction. If the transaction cannot 
 * be undone when a domain error occurs, Ts can be transduced to produce a transducer 
 * that uses the mark/reset effectors (see below) to look ahead and start Ts only when 
 * the lookahead recognizes a valid line or consumes the line with no effect if a domain
 * error occurs; see LinuxKernelLog.inr in the test patterns for an example of this. The
 * main idea is that domain errors can be handled by switching to a free running pattern
 * that involves no effectors but seeks to synchronize with the input stream at the beginning
 * of the innermost containing loop. In the example this was effected by adding a term 
 * after receiving nul that consumed all input until but not including newline. 
 * <p>
 * IInput.get() will return null and force eos whenever it is called after the
 * last input ordinal is returned from the input stack. Transducers can explicitly handle
 * this by including a transition on eos. If eos is not explicitly handled the transduction
 * will simply stop and ({@link ITransduction#status()} will return {@link END}).
 * <h2>Transduction Effectors</h2>
 * The Jrte implementation of the ITransduction interface extends {@link BaseTarget} and 
 * expresses effectors to control the transducer and input stacks, copy input characters,
 * manipulate named values from copied from input sequences, and manage countdown counters.
 * <table border="1" width="100%" cellpadding="3" cellspacing="0" summary="">
 * <tr bgcolor="#CCCCFF" class="TableHeadingColor">
 * <th align="left"><b>Transduction Effector</b></th>
 * <th align="left"><b>Effect on Transduction</b></th>
 * </tr>
 * <tr>
 * <td><b>0</b></td>
 * <td>Signal an undefined transition (equivalent to in[`!nul`])</td>
 * </tr>
 * <tr>
 * <td><b>1</b></td>
 * <td>Does nothing</td>
 * </tr>
 * <tr>
 * <td><b>start[`@transducer`]</b></td>
 * <td>Push a named transducer onto the transducer stack</td>
 * </tr>
 * <tr>
 * <td><b>shift[`@transducer`]</b></td>
 * <td>Replace current transducer on top of transducer stack with a named
 * transducer</td>
 * </tr>
 * <tr>
 * <td><b>stop</b></td>
 * <td>Stop the current transducer and pop the transducer stack</td>
 * </tr>
 * <tr>
 * <td><b>pause</b></td>
 * <td>Pause the transduction, return from run() -- call run() to resume
 * transduction</td>
 * </tr>
 * <tr>
 * <td><b>in[(`!signal`|`~name`|`text`)+]</b></td>
 * <td>Push a sequence of mixed text, named values, and signal ordinals onto the
 * input stack</td>
 * </tr>
 * <tr>
 * <td><b>out[(`!signal`|`~name`|`text`)+]</b></td>
 * <td>Write a sequence of mixed text, named values, and signal ordinals to
 * System.out</td>
 * </tr>
 * <tr>
 * <td><b>mark</b></td>
 * <td>Mark a position in the current input</td>
 * </tr>
 * <tr>
 * <td><b>reset</b></td>
 * <td>Resets the input to the last mark</td>
 * </tr>
 * <tr>
 * <td><b>end</b></td>
 * <td>Pops the input stack</td>
 * </tr>
 * <tr>
 * <td><b>select</b></td>
 * <td>Select the anonymous value as current selection</td>
 * </tr>
 * <tr>
 * <td><b>select[`~name`]</b></td>
 * <td>Select a named value as current selection</td>
 * </tr>
 * <tr>
 * <td><b>paste</b></td>
 * <td>Extend the current selection by appending the current input character</td>
 * </tr>
 * <tr>
 * <td><b>paste[`text`]</b></td>
 * <td>Extend the current selection by appending text</td>
 * </tr>
 * <tr>
 * <td><b>copy[`~name`]</b></td>
 * <td>Copy the current selection into a named value</td>
 * </tr>
 * <tr>
 * <td><b>cut[`~name`]</b></td>
 * <td>Copy the current selection into a named value and clear the selection</td>
 * </tr>
 * <tr>
 * <td><b>clear</b></td>
 * <td>Clear the current selection</td>
 * </tr>
 * <tr>
 * <td><b>clear[`~name`]</b></td>
 * <td>Clear the named value</td>
 * </tr>
 * <tr>
 * <td><b>clear[`~*`]</b></td>
 * <td>Clear all named values and the anonymous value</td>
 * </tr>
 * <tr>
 * <td><b>save[~name+]</b></td>
 * <td>Push a set of named values onto the value stack before starting a new
 * transducer, to be restored when new transducer stops</td>
 * </tr>
 * <tr>
 * <td><b>counter[`!signal` `digit+`]</b></td>
 * <td>Set up a counter signal and initial counter value</td> 
 * </tr>
 * <tr>
 * <td><b>count</b></td>
 * <td>Decrement the counter and produce in[`!signal`] when counter is 0</td>
 * </tr>
 * </table>
 * <p>
 * These transduction effectors are expressed by the base {@link ITarget}
 * class {@link com.characterforming.jrte.base.BaseTarget}, and every ITarget 
 * implementation class must subclass BaseTarget. This permits the BaseTarget 
 * effectors to be invoked from any transducer, in addition to the effectors 
 * expressed by BaseTarget subclasses bound to the transducer's containing 
 * gearbox. 
 * <h3>Transduction Effectors</h3> The inclusion of a transducer call stack
 * allows large transducers to be decomposed into smaller transducers, with 
 * concomitant reduction of RAM footprint. A new transducer can be pushed 
 * onto the transducer stack using the <b>start[]</b> effector. The new 
 * transducer will then run until the <b>stop</b> effector is called. The 
 * <b>shift[]</b> effector replaces the transducer on the top of the call
 * stack,  allowing transducers to be chained (concatenated). 
 * <p>To force a return from the {@link ITransduction#run()} method, use the 
 * <b>pause</b> effector.
 * <h3>Input and Output Effectors</h3> Transductions are driven by a stack/queue
 * of {@link IInput} instances and process each input is a single pass.
 * <p>Some transducer design patterns require a pattern identification transduction
 * to look ahead to select a specific transducer to perform text extraction. In 
 * these cases the <b>mark</b> effector can be used to mark the point of departure.
 * After the pattern has been identified and mapped to a specific transducer, the
 * <b>reset</b> effector can be used to restore the input stream to the marked
 * position before starting the selected transducer. 
 * <p>
 * The <b>in[]</b> pushes signals, named values, or text onto the input stack as
 * a single IInput stream for immediate processing. This is mainly used to raise
 * out-of-band signals during text processing. This allows, for example, 
 * transducers to be called like functions using the <b>start</b> effector and 
 * return values using <b>in[..]</b> that the calling transducer can use to 
 * discriminate ambiguous cases.
 * <p>
 * The <b>out[]</b> effector prints signals, named values, or text onto
 * System.out. It is mainly used for debugging/verifying transducers. In 
 * practice, it can be supplemented with analogous effectors that decorate 
 * captured values with any format -- XML, JSON, CSV ...
 * The <b>end</b> effector terminates the current input stream and pops the 
 * input stack.
 * <h3>Compositing Effectors</h3> Each transduction has a set of named values,
 * including an anonymous value that can be used as a scratch pad for copying
 * input. At any time, any value can be selected using the <b>select[]</b>
 * effector. The anonymous value is preselected in the
 * {@link ITransduction#start(String)} method and can be reselected using the
 * <b>select</b> effector. The selected value is called the <i>selection</i>
 * <p>
 * The <b>paste</b> effector extends the selection with the current input
 * character. The <b>paste[]</b> effector extends the selection with the
 * specified parameter text. The <b>clear</b> effector clears the selection, and
 * the <b>clear[]</b> effector clears the specified named value. To clear all
 * named values, including the anonymous value and the selection, use
 * <b>clear[`~*`]</b>. The <b>cut[]</b> and <b>copy[]</b> effectors extend the 
 * specified named value with the contents of the selected value. The selection
is cleared by <b>cut[]</b> and is not cleared by <b>copy[]</b>.
 * <p>
 * The collection of named values is global to the transduction and are not
 * automatically pushed with the transducer stack. If the calling transducer is
 * required to save values that might otherwise be overwritten by the called
 * transducer, the caller can use the <b>save[]</b> effector to push a subset of
 * named values onto the value stack before started the called transducer. The
 * saved values will be restored when the called transducer returns.
 * <h3>Counter Effectors</h3> Each transducer is provided with a single counter.
 * This is set up by the <b>counter[]</b> effector with countdown value and a
 * counter signal. The counter signal is raised when the <b>count</b> effector
 * decrements the countdown to zero. This will be extended at some point to 
 * include named counters as well as counters that count up.
 * <h3>Application Effectors</h3> Application-defined ITarget classes can
 * implement additional {@link IEffector} classes to assemble domain objects
 * in the application target class. When the application target is bound to 
 * a transduction with {@link Jrte#transduction(ITarget)}, it can control 
 * the transduction and access transduction products (eg, named values) 
 * through this interface. The gearbox compiler will discover all of the 
 * base and application effectors using Java reflection during the target
 * binding process.
 * 
 * @author kb
 */
public interface ITransduction extends ITarget {
	/**
	 * Transduction is paused, waiting for input; will be runnable when input is available
	 */
	public final static int PAUSED = IEffector.RTE_TRANSDUCTION_PAUSE;

	/**
	 * Transduction is runnable when status() == RUNNABLE
	 */
	public final static int RUNNABLE = IEffector.RTE_TRANSDUCTION_RUN;

	/**
	 * Transduction is not runnable because transduction stack is empty 
	 */
	public final static int END = IEffector.RTE_TRANSDUCTION_END;

	/**
	 * Set up or reset a transduction with a specified transducer at its start
	 * state on top of the transducer stack. To process input, call the
	 * {@link #input(IInput[])} method.
	 * 
	 * @param transducer The name of the transducer to start
	 * @throws GearboxException
	 * @throws TransducerNotFoundException
	 * @throws RteException
	 */
	public void start(String transducer) throws RteException;

	/**
	 * Set up transduction inputs.
	 * 
	 * @param inputs Initial (or additional) inputs in LIFO order, inputs[0] is
	 *           last out
	 * @throws RteException If this method is called on an active transduction
	 */
	public void input(IInput[] inputs) throws RteException;

	/**
	 * Run the transduction with current input until the input or transduction
	 * stack is empty, or an effector returns RTE_PAUSE, or an exception is thrown.
	 * 
	 * @return Run status of transduction at point of return 
	 * @see status()
	 * @throws DomainErrorException If a nul signal is presented and no
	 *            transition is defined to handle it
	 * @throws EffectorException If an effector throws an exception
	 * @throws InputException If there is a problem relating to the transduction
	 *            input stack
	 * @throws RteException If this method is called on a running transduction
	 */
	public int run() throws RteException;

	/**
	 * Test the status of the transduction's input and transducer stacks.
	 * 
	 * <table border="1" width="100%" cellpadding="3" cellspacing="0" summary="">
	 * <tr bgcolor="#CCCCFF" class="TableHeadingColor">
	 * <th align="left"><b>Status</b></th>
	 * <th align="left"><b>Description</b></th>
	 * </tr>
	 * <tr>
	 * <td><b>{@link END}</b></td>
	 * <td>Transduction stack is empty.</td>
	 * </tr>
	 * <tr>
	 * <td><b>{@link PAUSED}</b></td>
	 * <td>Transduction stack not empty, input stack empty.</td>
	 * </tr>
	 * <tr>
	 * <td><b>{@link RUNNABLE}</b></td>
	 * <td>Transduction stack not empty, input stack not empty.</td>
	 * </tr>
	 * </table>
	 * <p>
	 * A paused transduction can be resumed by calling run() when new input becomes available in 
	 * the input stack. Transducers may deliberately to break out of run() with transducer and input
	 * stacks not empty and allow the caller to determine future course, in which case transduction 
	 * can be run() again immediately as long as status() ==  RUNNABLE.
	 * 
	 * @return Transduction status
	 */
	public int status();

	/**
	 * Get the target for this transduction
	 * 
	 * @return The target object
	 */
	public ITarget getTarget();

	/**
	 * List the names of all values defined for this target 
	 * 
	 * @return An array of value names in index order
	 */
	public String[] listValueNames();

	/**
	 * Get the numeric index for a defined named value
	 * 
	 * @param valueName The name of the value
	 * @return The numeric index of the value
	 * @throws TargetBindingException If valueName is not recognized
	 */
	public int getValueNameIndex(String valueName) throws TargetBindingException;

	/**
	 * Get the current value for a named value
	 * 
	 * @param nameIndex The numeric index of the named value to get
	 * @return The named value wrapped in an {@link INamedValue} instance
	 */
	public INamedValue getNamedValue(int nameIndex);
}
