/*******************************************************************************
 * Copyright (c) 2004, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM - Initial API and implementation
 *******************************************************************************/
package ir.co.dpq.internal.jobs;

import ir.co.dpq.runtime.Status;
import ir.co.dpq.runtime.jobs.IJobStatus;
import ir.co.dpq.runtime.jobs.Job;

/**
 * Standard implementation of the IJobStatus interface.
 */
public class JobStatus extends Status implements IJobStatus {
	private Job job;

	/**
	 * Creates a new job status with no interesting error code or exception.
	 * @param severity
	 * @param job
	 * @param message
	 */
	public JobStatus(int severity, Job job, String message) {
		super(severity, JobManager.PI_JOBS, 1, message, null);
		this.job = job;
	}

	/* (non-Javadoc)
	 * @see ir.co.dpq.runtime.jobs.IJobStatus#getJob()
	 */
	@Override
	public Job getJob() {
		return job;
	}
}
