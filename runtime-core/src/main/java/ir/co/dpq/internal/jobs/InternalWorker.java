/*******************************************************************************
 * Copyright (c) 2009, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package ir.co.dpq.internal.jobs;

import ir.co.dpq.runtime.IProgressMonitor;
import ir.co.dpq.runtime.jobs.Job;

/**
 * Used to perform internal JobManager tasks. Currently, this is limited to checking 
 * progress monitors while a thread is performing a blocking wait in ThreadJob. 
 */
public class InternalWorker extends Thread {
	private final JobManager manager;
	/**
	 * @GuardedBy("manager.monitorStack")
	 */
	private boolean canceled;

	InternalWorker(JobManager manager) {
		super("Worker-JM"); //$NON-NLS-1$
		this.manager = manager;
	}

	/**
	* Will loop until there are progress monitors to check. While there are monitors
	* registered, it will check cancelation every 250ms, and if it is canceled it will
	* interrupt the ThreadJob that is performing a blocking wait. 
	*/
	@Override
	public void run() {
		int timeout = 0;
		synchronized (manager.monitorStack) {
			while (!canceled) {
				if (manager.monitorStack.isEmpty()) {
					timeout = 0;
				} else {
					timeout = 250;
				}
				for (int i = 0; i < manager.monitorStack.size(); i++) {
					Object[] o = manager.monitorStack.get(i);
					IProgressMonitor monitor = (IProgressMonitor) o[1];
					if (monitor.isCanceled()) {
						Job job = (Job) o[0];
						Thread t = job.getThread();
						if (t != null) {
							t.interrupt();
						}
					}
				}
				try {
					manager.monitorStack.wait(timeout);
				} catch (InterruptedException e) {
					// loop
				}
			}
		}
	}

	/**
	* Terminate this thread. Once terminated, it cannot be restarted. 
	*/
	void cancel() {
		synchronized (manager.monitorStack) {
			canceled = true;
			manager.monitorStack.notifyAll();
		}
	}
}
