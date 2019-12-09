/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
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
 * Domain errors (inputs with no transition defined) are handled by emitting a
 * nul signal, giving the transduction an opportunity to handle it with an
 * explicit transition on nul. For most text transducers, domain errors can be avoided
 * entirely by transducing the transducer. For example, with line-oriented text, 
 * all possible interleavings of domain errors in the input can be modeled by replacing
 * each non-nl input (x) with (x|nul) in the original transducer definition. The 
 * resulting transducer can then be pruned to produce a hardened transducer that accepts
 * ((any - nl)* nl)* and silently resynchronizes with the input after a domain error. If 
 * a domain error occurs on a nul signal, a {@link DomainErrorException} is thrown. 
 * <p>
 * IInput.get() will return null and force eos whenever it is called after the
 * last input ordinal is returned from the input stack. Transducers can explicitly handle
 * this by including a transition on eos. If eos is not explicitly handled the transduction
 * will simply stop and ({@link ITransduction#status()} will return {@link STOPPED}).
 * 
 * @author kb
 */
public interface ITransduction extends ITarget {
	/**
	 * Transduction is paused, waiting for input; will be runnable when input is available
	 */
	public final static int PAUSED = IEffector.RTE_EFFECT_PAUSE;

	/**
	 * Transduction is runnable when status() == RUNNABLE
	 */
	public final static int RUNNABLE = IEffector.RTE_EFFECT_NONE;

	/**
	 * Transduction is not runnable because transduction stack is empty 
	 */
	public final static int STOPPED = IEffector.RTE_EFFECT_STOPPED;

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
	 * <td><b>{@link STOPPED}</b></td>
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

	/**
	 * Get the current selected value
	 * 
	 * @return The selected value wrapped in an {@link INamedValue} instance
	 */
	public INamedValue getSelectedValue();
}
