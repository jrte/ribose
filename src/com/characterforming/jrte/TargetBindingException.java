/**
 * Copyright (c) 2011,2017, Kim T Briggs, Hampton, NB.
 */
package com.characterforming.jrte;

import java.util.ArrayList;
import java.util.List;

/**
 * Thrown when there is a problem binding target effectors to a transduction
 * 
 * @author kb
 */
public class TargetBindingException extends GearboxException {

	private static final long serialVersionUID = 1L;
	private List<String> unboundEffectorList = null;

	public TargetBindingException() {
	}

	public TargetBindingException(final String message) {
		super(message);
	}

	public TargetBindingException(final Throwable cause) {
		super(cause);
	}

	public TargetBindingException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public void setUnboundEffectorList(final List<String> unbound) {
		this.unboundEffectorList = unbound;
	}

	public List<String> getUnboundEffectorList() {
		return this.unboundEffectorList != null ? this.unboundEffectorList : new ArrayList<String>(0);
	}
}
