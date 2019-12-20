/**
 * Base package provides the base classes that define the main application extension 
 * points for Jrte. Client applications can extend Jrte by subclassing {@link com.characterforming.jrte.base.BaseTarget}
 * and defining new anonymous inner classes that subclass one of the base effector classes.
 * Typically, the target class serves as a factory to produce objects that are assembled
 * from the transduced input through the actions of the target effectors. 
 * <h3>Targets</h3>
 * The {@link com.characterforming.jrte.base.BaseTarget} base class implements the 
 * {@link com.characterforming.jrte.ITarget} interface and provides the main extension point
 * for the Jrte framework. The BaseTarget class provides a set of simple effectors that assemble 
 * named values, where the value names are determined by the names supplied to the copy[] and cut[] 
 * effectors. These names and values are accessible to BaseTarget extension classes through the
 * methods provided by the base class. Client applications may extend BaseTarget with 
 * additional fields, methods, and effectors as required to suit application-specific requirements.
 * <p>
 * To create a target extension, simply subclass BaseTarget or a BaseTarget subclass 
 * and override the {@link com.characterforming.jrte.base.BaseTarget#bind(ITransduction)} 
 * method. The overridden bind(ITransduction) method must call super.bind(ITransduction)
 * and may add new anonymous inner effector classes to the list of effectors returned by
 * the superclass as required to suit application requirements. Additionally, the extension
 * class must provide a default constructor the accepts no arguments. 
 * <p>
 * For example:
 * <pre class="code">
 * public class MyTarget implements ITarget {
 *    private final IEffector&lt;MyTarget&gt;[] effectors;
 *    private ITransduction transduction;
 *    ...
 *    public MyTarget() {
 *       this.effectors = {
 *          new BaseEffector&lt;MyTarget&gt;(this, "mySimpleEffector") {
 *                 ...
 *          },
 *          ...
 *          new BaseParameterizedEffector&lt;MyTarget, char[]&gt;(this, "myParameterizedEffector") {
 *             ...
 *          }
 *       };
 *       this.transduction = null;
 *       ...
 *    }
 *    ...
 *    &#64;Override
 *    public IEffector&lt;?&gt;[] bind(ITransduction transduction) throws TargetBindingException {
 *       if (this.transduction != null) {
 *          throw new TargetBindingException("Already bound");
 *       }
 *       this.transduction = transduction;
 *       return this.effectors;
 *    } 
 * }
 * </pre>
 * This skeletal example shows how to extend a base ITarget implementation class and define 
 * additional effectors. 
 * <p>
 * Each gearbox is associated with a specific target extension class when the gearbox is compiled. 
 * A proxy target instantiated during gearbox compilation contexts to allow the compiler to 
 * enumerate the names of the effectors available in the target class. The gearbox compiler will 
 * invoke the bind(ITransduction) method on the proxy target instance with a null ITransduction 
 * parameter and will call the {@link com.characterforming.jrte.IEffector#getName()} method for 
 * each effector. No other methods of the target or effector instances will be called during 
 * gearbox compilation, and the proxy target instance will be discarded after the effector
 * names have been enumerated.
 * <p>
 * Similarly, a proxy target instantiated when the gearbox is loaded for runtime use. Again, 
 * the bind(ITransduction) method effector getName() methods are called, this time in order 
 * to verify that the effector names and types match those that were discovered during gearbox 
 * compilation. In this context, the proxy target instance is also used to compile parameter 
 * objects for all of the parameterized effectors defined for the target class, as described in 
 * some detail below. This proxy instance is also discarded as soon as the target has been validated
 * and the effector parameter objects have been compiled. This effector validation and parameter 
 * object compilation process is performed in the {@link com.characterforming.jrte.Jrte#Jrte(File, String)}
 * constructor.
 * <p>
 * Once the gearbox has been loaded and the target class has been validated for runtime use, 
 * client applications create live target instances and bind them to runtime transductions 
 * using the {@link com.characterforming.jrte.Jrte#transduction(ITarget)} method. Each
 * live target instance may be bound to at most one ITransduction instance, and the binding is 
 * effective for the lifetime of the transduction. 
 * <p>
 * Once the target has been bound to a transduction, the transduction can be started and run 
 * using the {@link com.characterforming.jrte.ITransduction#start(String)} and 
 * {@link com.characterforming.jrte.ITransduction#input(IInput[])} methods. The run() method will 
 * return normally when the input stack is exhausted, or the transducer stack is empty, 
 * or an effector returns {@link com.characterforming.jrte.base.BaseEffector#RTE_EFFECT_PAUSE}. 
 * <p>
 * Paused or excepting transduction can be continued by calling the run() method again, without
 * specifying new inputs. When the transducer stack is empty, the transduction can be 
 * restarted by calling the start() method with a (possibly different) transducer.
 * <h3>Target Effectors</h3>
 * The Jrte framework defines 2 types of effectors:
 * <ol>
 * <li><b>{@link com.characterforming.jrte.IEffector}</b>: simple effector
 * <li><b>{@link com.characterforming.jrte.IParameterizedEffector}</b>: parameterized effector
 * </ol>
 * Each effector class receives a reference to the extended target instance when it is 
 * instantiated and may interact directly with the target instance at runtime. 
 * <h4>Simple Effectors</h4>
 * The {@link com.characterforming.jrte.base.BaseEffector} base class implements the IEffector 
 * interface and specifies an abstract {@link com.characterforming.jrte.base.BaseEffector#invoke()} 
 * method that returns an int (typically returning {@link com.characterforming.jrte.base.BaseEffector#RTE_EFFECT_NONE}).
 * <pre class="code">
 * new BaseEffector&lt;BaseTarget&gt;(this, "outln") {
 *    &#64;Override
 *    public int invoke() throws EffectorException {
 *       System.out.println();
 *       return BaseEffector.RTE_EFFECT_NONE;
 *    }
 * };
 * </pre>
 * Simple effectors can interact with the transduction target class using the 
 * {@link com.characterforming.jrte.IEffector#getTarget()} method.
 * <h4>Parameterized Effectors</h4>
 * The {@link com.characterforming.jrte.base.BaseParameterizedEffector} base class 
 * implements the IParameterizedEffector interface and extends BaseEffector with an 
 * array of parameter objects, each constructible from an array of byte[] (byte[][]). 
 * This base class specifies an additional {@link com.characterforming.jrte.base.BaseParameterizedEffector#invoke(int)}
 * method that receives the numeric index of a specific parameter class instance to 
 * be used in the invocation. The invoke() method inherited from BaseEffector may 
 * optionally be implemented; if not implemented, it should throw 
 * {@link com.characterforming.jrte.TargetBindingException}. 
 * <p>
 * Parameterized effectors are easy to implement and to use in transductions but they 
 * require some explanation. The type of the parameters is specified in the parameterized 
 * effector class definition, as for the counter effector:
 * <pre class="code">
 * new BaseParameterizedEffector&lt;BaseTarget, int[]&gt;(this, "counter")
 * </pre>
 * Here the parameters type is <code>int[]</code>; the counter effector receives a counter 
 * signal to be raised when the countdown falls to zero and an initial value for the 
 * countdown. The following expression might be used in a transducer to copy input 
 * in the format YYYY/MM/DD into a named date value:
 * <pre class="code">
 * (space, counter[zero 10]) (any, paste count)* (zero, cut[date])
 * </pre>
 * In this example, <code>zero</code> is the counter signal and the initial countdown 
 * value is 10. The <code>zero</code> signal has an ordinal value N so the parameter for the 
 * counter invocation in response to the <code>space</code> input is {N, 10}.
 * <p>
 * During gearbox compilation, the collection of all unique parameters is enumerated for 
 * each parameterized effector, taking each token occurring between <code>[]</code>
 * parentheses as a <code>byte[]</code> array. So each reference to a parameterized effector
 * is associated in the gearbox with a enumerated <code>byte[][]</code> array, and the 
 * integer enumerator for this array is the value passed to the parameterized effector at 
 * runtime.
 * <p>
 * When the gearbox is loaded for runtime operation, each parameterized effector is 
 * associated with a <code>byte[][][]</code> array that holds all of the unique byte 
 * arrays of parameters used with the effector in any transducer included in the 
 * gearbox. These are compiled into actual parameter objects when target instances 
 * are bound to transductions. For each parameterized effector the gearbox invokes 
 * the following sequence:
 * <pre class="code">
 * effector.newParameters(rawParameters.length);
 * for (int i = 0; i &lt; rawParameters.length; i++) {
 *    effector.setParameter(i, this.charset, rawParameters[i]);
 * }
 * </pre>
 * That's a lot of technical detail, but it may help to motivate an understanding of parameterized
 * effectors. The main things to understand are that effector parameter objects are effectively static
 * in runtime contexts and that they must be constructible from byte arrays. 
 * <p>
 * Below is the full implementation for the counter effector.  
 * <pre class="code">
 * new BaseParameterizedEffector&lt;BaseTarget, int[]&gt;(this, "counter") {
 *    &#64;Override
 *    public void newParameters(int parameterCount) {
 *       super.setParameters(new int[parameterCount][]);
 *    }
 *    
 *    &#64;Override
 *    public void setParameter(int parameterIndex, Charset charset, byte[][] parameterList) throws TargetBindingException {
 *       if (parameterList.length != 2) {
 *          throw new TargetBindingException("The counter effector requires two parameters");
 *       }
 *       Chars counterName = new Chars(charset.decode(ByteBuffer.wrap(parameterList[0])));
 *       Integer signal = this.getTarget().transduction.getGearbox().getInputOrdinalMap().get(counterName);
 *       if (signal == null) {
 *          throw new TargetBindingException(String.format("The counter signal '%1$s' is unrecognized", counterName.toString()));
 *       }
 *       try {
 *          int countdown = Integer.parseInt(charset.decode(ByteBuffer.wrap(parameterList[1])).toString());
 *          super.setParameter(parameterIndex, new int[] { signal, countdown });
 *       } catch (NumberFormatException e) {
 *          throw new TargetBindingException(String.format( "The counter value '%1$s' is not numeric", charset.decode(ByteBuffer.wrap(parameterList[1])).toString()));
 *       }
 *    }
 *    
 *    &#64;Override
 *    public int invoke() throws EffectorException {
 *       throw new EffectorException("The counter effector requires two parameters");
 *    }
 * 
 *    &#64;Override
 *    public int invoke(int parameterIndex) throws EffectorException {
 *       int[] countdown = super.getParameter(parameterIndex);
 *       return this.getTarget().transduction.counter(countdown[0], countdown[1]);
 *    }
 * };
 * </pre>
 */
package com.characterforming.jrte.base;

