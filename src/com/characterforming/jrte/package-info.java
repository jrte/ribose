/**
 * Main package provides the transduction factory class Jrte and the interfaces that 
 * define the application integration points.
 * <p>
 * Client applications use the {@link com.characterforming.jrte.Jrte#Jrte(File, String)} 
 * constructor to load a Gearbox compiled from ginr automata. The Gearbox instantiates 
 * a proxy instance of an {@link com.characterforming.jrte.ITarget} implementation 
 * class to verify that the target class and its effectors match the target class 
 * referenced during gearbox compilation. Applications can then use the 
 * {@link com.characterforming.jrte.Jrte#transduction(ITarget)} 
 * method to instantiate an {@link com.characterforming.jrte.ITransduction}. 
 * <p>
 * A transduction binds a stack of {@link com.characterforming.jrte.IInput}, a transducer 
 * stack, and an ITarget instance. The starting transducer reads input ordinals, which 
 * may be Unicode character ordinals or signal ordinals, from the IInput on
 * the top of the input stack, popping the input stack as each IInput is 
 * exhausted. Each input drives a transition in the transducer at the top
 * of the transducer stack, which determines a (possibly empty) vector of
 * target effectors to invoke.
 * <p>
 * The {@link com.characterforming.jrte.base} package provides three IInput 
 * implementations. The {@link com.characterforming.jrte.Jrte#input(char[][])}
 * method receives an array of char[] arrays, each containing either a signal name
 * or a text sequence. It maps signal names to corresponding signal ordinals and 
 * copies text ordinals from text sequences to produce a single char[] array as
 * the input source. The {@link com.characterforming.jrte.Jrte#input(Reader)}
 * method receives a Reader as the input source, and 
 * {@link com.characterforming.jrte.Jrte#input(InputStream, Charset)} receives a 
 * raw input stream and decodes it to text using a specified Charset.
 * <p>
 * For example, it is sometimes useful to provide an initial nil signal to a 
 * transduction to provide a starting transition that sets up the transduction:
 * <pre class="code">
 * Jrte jrte = new Jrte(new File(".\jrte.gears", "com.characterforming.jrte.base.BaseTarget");
 * IInput[] inputs = new IInput[] {
 *    jrte.input(new InputStreamReader(System.in)),
 *    jrte.input(new char[][] { { 'n','i','l' } })
 * };
 * </pre>
 * In the code example above, the Jrte BaseTarget class is selected as the target. 
 * The {@link com.characterforming.jrte.base.BaseTarget} documentation describes 
 * the effectors expressed by the base target class, which are sufficient for 
 * simple transductions that write to System.out. The BaseTarget class can be 
 * extended with additional effectors for more sophisticated applications, as 
 * described in the BaseTarget documentation.
 * <p>
 * To continue the example above:
 * <pre class="code">
 * ITarget target = new BaseTarget();
 * ITransduction transduction = jrte.bind(target);
 * transduction.start("HelloWorld");
 * transduction.input(inputs);
 * while (transduction.status() == ITransduction.RUNNABLE) {
 *    transduction.run();
 * }
 * </pre>
 * If System.in contains "hello world" this transduction will write "(-: hello world :-)"
 * to System.out. The transducer HelloWorld is a 3-tape finite state automaton, where
 * the 1st tape matches the input, the 2nd tape specifies the target effectors
 * to invoke on input transitions, and the 3rd tape holds parameter values for
 * parameterised effectors. It is compiled from a regular expression by ginr and 
 * saved to a .dfa file for input to the Jrte gearbox compiler.
 * <pre class="code">
 * HelloWorld = (
 *    (nil, clear paste[`(-: `]) ('hello world' @@ PasteAny) (eos, paste[` :-)`] out outln clear)
 * );
 * HelloWorld :save `_HelloWorld.dfa`;
 * </pre>
 * <p>
 * In the ginr expression above, the PasteAny token refers to a transducer that invokes
 * the BaseTarget paste effector for any input. It is defined in a prologue that is 
 * compiled before HelloWorld.
 * <pre class="code">
 * space = ' ';
 * nl = '\x0a';
 * cr = '\x0d';
 * tab ='\t';
 * backslash = '\\';
 * digit = '0123456789':alph;
 * lower = 'abcdefghijklmnopqrstuvwxyz':alph;
 * upper = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ':alph;
 * alpha = lower | upper;
 * punct = '!"#$%&\'()*+,-./:;<=>?@[\\]^_`{|}~':alph;
 * any = space | nl | cr | tab | backslash | digit | alpha;
 *
 * PasteAny = (any, paste)*;
 * </pre>
 * Note that the PasteAny transducer is not saved to a .dfa file -- it is compiled directly 
 * into the HelloWorld automaton. When it is applied to a specific input pattern using ginr's 
 * join operator (@@) its domain is restricted to the input pattern, so the application in 
 * HelloWorld is equivalent to:
 * <pre class="code">
 * (h,paste)(e,paste)(l,paste)...(r,paste)(l,paste)(d,paste)
 * </pre>
 * Please refer to the ginr documentation at the <a href="http://code.google.com/p/ginr/downloads/list">ginr</a>
 * downloads page for full ginr documentation.
 */
package com.characterforming.jrte;

