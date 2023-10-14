package com.characterforming.ribose;

/**
 * A simple wrapper for transductors that can be used in a {@code 
 * try-with-transductor} block to ensure that the transductor's
 * {@link ITransductor#stop()} method is called when the transduction
 * is autoclosed at the end of the try. This clears the transductor's
 * transducer and input stacks but does not tear down the transductor,
 * which can be reused in subsequent transductions.
 * <br><br>
 * For example:
 * <br><pre>
 *  try (ITransduction transduction = super.transduction(this.transductor)) {
 *    transduction.reset();
 *    assert this.transductor.status().isStopped();
 *    Bytes automaton = Codec.encode("Automaton");
 *    if (this.transductor.stop().push(bytes, size).signal(Signal.NIL).start(automaton).status().isRunnable()
 *    &amp;&amp; this.transductor.run().status().isPaused()) {
 *      this.transductor.signal(Signal.EOS).run();
 *    }
 *  } finally {
 *    assert this.transductor.status().isStopped();
 *  }
 * </pre>
 * 
 * @see IModel#transduction(ITransductor)
 */
public interface ITransduction extends AutoCloseable {
	/**
	 * Reset the transduction before commencing. This ensures that
	 * the transducer and input stacks are empty and the transductor
	 * is ready to start a transduction.
	 */
	void reset();

	/**
	 * Implements {@link AutoCloseable#close()}
	 */
	@Override
	void close();
}
