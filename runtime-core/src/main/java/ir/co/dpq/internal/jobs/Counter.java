/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package ir.co.dpq.internal.jobs;

/**
 * Simple thread-safe long counter.
 * @ThreadSafe
 */
public class Counter {
	private long value = 0L;

	public Counter() {
		super();
	}

	public synchronized long increment() {
		return value++;
	}
}
