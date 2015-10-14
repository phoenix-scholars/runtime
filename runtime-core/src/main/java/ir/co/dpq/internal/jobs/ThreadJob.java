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

import ir.co.dpq.internal.runtime.RuntimeLog;
import ir.co.dpq.runtime.*;
import ir.co.dpq.runtime.jobs.*;

/**
 * Captures the implicit job state for a given thread. 
 */
class ThreadJob extends Job {

	/**
	 * Set to true if this thread job is running in a thread that did
	 * not own a rule already.  This means it needs to acquire the
	 * rule during beginRule, and must release the rule during endRule.
	 */
	protected boolean acquireRule = false;

	/**
	 * Indicates that this thread job did report to the progress manager
	 * that it will be blocked, and therefore when it begins it must
	 * be reported to the job manager when it is no longer blocked.
	 */
	boolean isBlocked = false;

	/**
	 * True if this ThreadJob has begun execution
	 * @GuardedBy("this")
	 */
	protected boolean isRunning = false;

	/**
	 * Used for diagnosing mismatched begin/end pairs. This field
	 * is only used when in debug mode, to capture the stack trace
	 * of the last call to beginRule.
	 */
	private RuntimeException lastPush = null;
	/**
	 * The actual job that is running in the thread that this 
	 * ThreadJob represents.  This will be null if this thread
	 * job is capturing a rule acquired outside of a job.
	 * @GuardedBy("JobManager.implicitJobs")
	 */
	protected Job realJob;
	/**
	 * The stack of rules that have been begun in this thread, but not yet ended.
	 * 	@GuardedBy("JobManager.implicitJobs")
	 */
	private ISchedulingRule[] ruleStack;
	/**
	 * Rule stack pointer.
	 * INV: 0 <= top <= ruleStack.length
	 * @GuardedBy("JobManager.implicitJobs")
	 */
	private int top;

	/**
	 * Waiting state for thread jobs is independent of the internal state. When
	 * this variable is true, this ThreadJob is waiting in joinRun()
	 * @GuardedBy("jobStateLock")
	 */
	boolean isWaiting;

	ThreadJob(ISchedulingRule rule) {
		super("Implicit Job"); //$NON-NLS-1$
		setSystem(true);
		// calling setPriority will try to acquire JobManager.lock, breaking
		// lock acquisition protocol. Since we are constructing this thread,
		// we can call internalSetPriority
		((InternalJob) this).internalSetPriority(Job.INTERACTIVE);
		ruleStack = new ISchedulingRule[2];
		top = -1;
		((InternalJob) this).internalSetRule(rule);
	}

	boolean isResumingAfterYield() {
		return false;
	}

	/**
	 * An endRule was called that did not match the last beginRule in
	 * the stack.  Report and log a detailed informational message.
	 * @param rule The rule that was popped
	 * @GuardedBy("JobManager.implicitJobs")
	 */
	private void illegalPop(ISchedulingRule rule) {
		StringBuffer buf = new StringBuffer("Attempted to endRule: "); //$NON-NLS-1$
		buf.append(rule);
		if (top >= 0 && top < ruleStack.length) {
			buf.append(", does not match most recent begin: "); //$NON-NLS-1$
			buf.append(ruleStack[top]);
		} else {
			if (top < 0)
				buf.append(", but there was no matching beginRule"); //$NON-NLS-1$
			else
				buf.append(", but the rule stack was out of bounds: " + top); //$NON-NLS-1$
		}
		buf.append(".  See log for trace information if rule tracing is enabled."); //$NON-NLS-1$
		String msg = buf.toString();
		if (JobManager.DEBUG || JobManager.DEBUG_BEGIN_END) {
			System.out.println(msg);
			Throwable t = lastPush == null ? new IllegalArgumentException() : lastPush;
			IStatus error = new Status(IStatus.ERROR, JobManager.PI_JOBS, 1, msg, t);
			RuntimeLog.log(error);
		}
		Assert.isLegal(false, msg);
	}

	/**
	 * Client has attempted to begin a rule that is not contained within
	 * the outer rule.
	 */
	private void illegalPush(ISchedulingRule pushRule, ISchedulingRule baseRule) {
		StringBuffer buf = new StringBuffer("Attempted to beginRule: "); //$NON-NLS-1$
		buf.append(pushRule);
		buf.append(", does not match outer scope rule: "); //$NON-NLS-1$
		buf.append(baseRule);
		String msg = buf.toString();
		if (JobManager.DEBUG) {
			System.out.println(msg);
			IStatus error = new Status(IStatus.ERROR, JobManager.PI_JOBS, 1, msg, new IllegalArgumentException());
			RuntimeLog.log(error);
		}
		Assert.isLegal(false, msg);

	}

	/**
	 * Returns true if the monitor is canceled, and false otherwise.
	 * Protects the caller from exception in the monitor implementation.
	 */
	static private boolean isCanceled(IProgressMonitor monitor) {
		try {
			return monitor.isCanceled();
		} catch (RuntimeException e) {
			//logged message should not be translated
			IStatus status = new Status(IStatus.ERROR, JobManager.PI_JOBS, JobManager.PLUGIN_ERROR, "ThreadJob.isCanceled", e); //$NON-NLS-1$
			RuntimeLog.log(status);
		}
		return false;
	}

	/**
	 * Returns true if this thread job was scheduled and actually started running.
	 * @GuardedBy("this")
	 */
	synchronized boolean isRunning() {
		return isRunning;
	}

	/**
	 * A reentrant method which will run this <code>ThreadJob</code> immediately if there 
	 * are no existing jobs with conflicting rules, or block until the rule can be acquired. If this 
	 * job must block, the <code>LockListener</code> is given a chance to override.
	 * If override is not granted, then this method will block until the rule is available. If 
	 * <code>LockListener#canBlock</code> returns <tt>true</tt>, then the <code>monitor</code>
	 * <i>will not</i> be periodically checked for cancellation. It will only be rechecked if this 
	 * thread is interrupted. If <code>LockListener#canBlock</code> returns <tt>false</tt> The 
	 * <code>monitor</code> <i>will</i> be checked periodically for cancellation.
	 * 
	 * When a UI is present, it is recommended that the <code>LockListener</code> 
	 * should not allow the UI thread to block without checking the <code>monitor</code>. This 
	 * ensures that the UI remains responsive. 
	 * 
	 * @see LockListener#aboutToWait(Thread)
	 * @see LockListener#canBlock()
	 * @see JobManager#transferRule(ISchedulingRule, Thread)
	
	 * @return <tt>this</tt>, or the <code>ThreadJob</code> instance that was 
	 * unblocked (due to transferRule) in the case of reentrant invocations of this method.
	 * 
	 * @param monitor - The <code>IProgressMonitor</code> used to report blocking status and 
	 * cancellation.
	 * 
	 * @throws OperationCanceledException if this job was canceled before it was started. 
	 */
	static ThreadJob joinRun(ThreadJob threadJob, IProgressMonitor monitor) {
		if (isCanceled(monitor))
			throw new OperationCanceledException();
		// check if there is a blocking thread before waiting
		InternalJob blockingJob = manager.findBlockingJob(threadJob);
		Thread blocker = blockingJob == null ? null : blockingJob.getThread();
		try {
			// just return if lock listener decided to grant immediate access
			if (manager.getLockManager().aboutToWait(blocker))
				return threadJob;
			return waitForRun(threadJob, monitor, blockingJob, blocker);
		} finally {
			manager.getLockManager().aboutToRelease();
		}
	}

	static ThreadJob waitForRun(ThreadJob threadJob, IProgressMonitor monitor, InternalJob blockingJob, Thread blocker) {
		// Ask lock manager if it safe to block this thread
		final boolean canBlock = manager.getLockManager().canBlock();
		ThreadJob result = threadJob;
		boolean interrupted = false;
		boolean waiting = false;
		try {
			waitStart(threadJob, monitor, blockingJob);
			manager.implicitJobs.addWaiting(threadJob);
			waiting = true;
			// If we're allowed to block this thread we won't be checking the monitor. In order
			// to respond to cancellation, register this monitor with the internal JobManager
			// worker thread. The worker thread will check for cancellation and will interrupt
			// this thread when the monitor is canceled. T
			if (canBlock)
				manager.beginMonitoring(threadJob, monitor);
			final Thread currentThread = Thread.currentThread();

			// Ultimately, this loop will wait until the job "runs" (acquires the rule)
			// or is canceled. However, there are many ways for that to occur. 
			// The exit conditions of this loop are:
			// 1) This job no longer conflicts with any other running job. 
			// 2) The LockManager#aboutToWait allowed this thread to run. 
			//    This usually occurs during reentrant situations. i.e. This is a UI thread, 
			//    and a syncExec is performed from conflicting job/thread. 
			// 3) A rule is transfered to this thread. This can be invoked programmatically,
			//    or commonly in JFace via ModalContext (for wizards/etc).
			// 4) Monitor is canceled.
			while (true) {
				// monitor is foreign code so do not hold locks while calling into monitor
				if (isCanceled(monitor))
					// Condition #4.
					throw new OperationCanceledException();
				// Try to run the job. If result is null, this job was allowed to run. 
				// If the result is successful, atomically release thread from waiting 
				// status. 
				blockingJob = manager.runNow(threadJob, true);
				if (blockingJob == null) {
					// Condition #1. 
					waiting = false;
					return threadJob;
				}
				blocker = blockingJob == null ? null : blockingJob.getThread();
				// the rule could have been transferred to this thread while we were waiting
				if (blocker == currentThread && blockingJob instanceof ThreadJob) {
					// now we are just the nested acquire case
					result = (ThreadJob) blockingJob;
					result.push(threadJob.getRule());
					result.isBlocked = threadJob.isBlocked;
					// Condition #3.
					return result;
				}
				// just return if lock listener decided to grant immediate access
				if (manager.getLockManager().aboutToWait(blocker))
					// Condition #2.
					return threadJob;

				// Notify the lock manager that we're about to block waiting for the scheduling rule
				manager.getLockManager().addLockWaitThread(currentThread, threadJob.getRule());
				synchronized (blockingJob.jobStateLock) {
					try {
						// Wait until we are no longer definitely blocked (not running). 
						// The actual exit conditions are listed above at the beginning of
						// this while loop
						int state = blockingJob.getState();
						//ensure we don't wait forever if the blocker is waiting, because it might have yielded to me
						if (state == Job.RUNNING && canBlock)
							blockingJob.jobStateLock.wait();
						else if (state != Job.NONE)
							blockingJob.jobStateLock.wait(250);
					} catch (InterruptedException e) {
						// This thread may be interrupted via two common scenarios. 1) If
						// the UISynchronizer is in use and this thread is a UI thread
						// and a syncExec() is performed, this thread will be interrupted
						// every 1000ms. 2) If this thread is allowed to be blocked and 
						// the progress monitor was canceled, the internal JobManager 
						// worker thread will interrupt this thread so cancellation can
						// be carried out. 
						interrupted = true;
					}
				}
				// Going around the loop again.  Ensure we're not marked as waiting for the thread
				// as external code is run via the monitor (Bug 262032).
				manager.getLockManager().removeLockWaitThread(currentThread, threadJob.getRule());
			}
		} finally {
			if (interrupted)
				Thread.currentThread().interrupt();
			//only update the lock state if we ended up using the thread job that was given to us
			waitEnd(threadJob, threadJob == result, monitor);
			if (threadJob == result) {
				if (waiting)
					manager.implicitJobs.removeWaiting(threadJob);
			}
			if (canBlock)
				// must unregister monitoring this job
				manager.endMonitoring(threadJob);
		}
	}

	/**
	 * Pops a rule. Returns true if it was the last rule for this thread
	 * job, and false otherwise.
	 * 	@GuardedBy("JobManager.implicitJobs")
	 */
	boolean pop(ISchedulingRule rule) {
		if (top < 0 || ruleStack[top] != rule)
			illegalPop(rule);
		ruleStack[top--] = null;
		return top < 0;
	}

	/**
	 * Adds a new scheduling rule to the stack of rules for this thread. Throws
	 * a runtime exception if the new rule is not compatible with the base
	 * scheduling rule for this thread.
	 * 	@GuardedBy("JobManager.implicitJobs")
	 */
	void push(final ISchedulingRule rule) {
		final ISchedulingRule baseRule = getRule();
		if (++top >= ruleStack.length) {
			ISchedulingRule[] newStack = new ISchedulingRule[ruleStack.length * 2];
			System.arraycopy(ruleStack, 0, newStack, 0, ruleStack.length);
			ruleStack = newStack;
		}
		ruleStack[top] = rule;
		if (JobManager.DEBUG_BEGIN_END)
			lastPush = (RuntimeException) new RuntimeException().fillInStackTrace();
		//check for containment last because we don't want to fail again on endRule
		if (baseRule != null && rule != null && !(baseRule.contains(rule) && baseRule.isConflicting(rule)))
			illegalPush(rule, baseRule);
	}

	/**
	 * Reset all of this job's fields so it can be reused.  Returns false if
	 * reuse is not possible
	 * 	@GuardedBy("JobManager.implicitJobs")
	 */
	boolean recycle() {
		//don't recycle if still running for any reason
		if (getState() != Job.NONE)
			return false;
		//clear and reset all fields
		acquireRule = isRunning = isBlocked = false;
		realJob = null;
		setRule(null);
		setThread(null);
		if (ruleStack.length != 2)
			ruleStack = new ISchedulingRule[2];
		else
			ruleStack[0] = ruleStack[1] = null;
		top = -1;
		return true;
	}

	/** (non-Javadoc)
	 * @see ir.co.dpq.runtime.jobs.Job#run(ir.co.dpq.runtime.IProgressMonitor)
	 */
	@Override
	public IStatus run(IProgressMonitor monitor) {
		synchronized (this) {
			isRunning = true;
		}
		return ASYNC_FINISH;
	}

	/**
	 * Records the job that is actually running in this thread, if any
	 * @param realJob The running job
	 * 	@GuardedBy("JobManager.implicitJobs")
	 */
	void setRealJob(Job realJob) {
		this.realJob = realJob;
	}

	/**
	 * Returns true if this job should cause a self-canceling job
	 * to cancel itself, and false otherwise.
	 * @GuardedBy("JobManager.implicitJobs")
	 */
	boolean shouldInterrupt() {
		return realJob == null ? true : !realJob.isSystem();
	}

	/* (non-javadoc)
	 * For debugging purposes only
	 */
	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer("ThreadJob"); //$NON-NLS-1$
		buf.append('(').append(realJob).append(',').append(getRuleStack()).append(')');
		return buf.toString();
	}

	String getRuleStack() {
		StringBuffer buf = new StringBuffer();
		buf.append('[');
		for (int i = 0; i <= top && i < ruleStack.length; i++) {
			buf.append(ruleStack[i]);
			if (i != top)
				buf.append(',');
		}
		buf.append(']');
		return buf.toString();
	}

	/**
	 * Reports that this thread was blocked, but is no longer blocked and is able
	 * to proceed.
	 * @param monitor The monitor to report unblocking to.
	 */
	static private void waitEnd(ThreadJob threadJob, boolean updateLockManager, IProgressMonitor monitor) {
		if (updateLockManager) {
			final LockManager lockManager = manager.getLockManager();
			final Thread currentThread = Thread.currentThread();
			if (threadJob.isRunning()) {
				lockManager.addLockThread(currentThread, threadJob.getRule());
				//need to re-acquire any locks that were suspended while this thread was blocked on the rule
				lockManager.resumeSuspendedLocks(currentThread);
			} else {
				//tell lock manager that this thread gave up waiting
				lockManager.removeLockWaitThread(currentThread, threadJob.getRule());
			}
		}
		if (threadJob.isBlocked) {
			threadJob.isBlocked = false;
			manager.reportUnblocked(monitor);
		}
	}

	/**
	 * Indicates the start of a wait on a scheduling rule. Report the
	 * blockage to the progress manager.
	 * @param monitor The monitor to report blocking to
	 * @param blockingJob The job that is blocking this thread, or <code>null</code>
	 */
	static private void waitStart(ThreadJob threadJob, IProgressMonitor monitor, InternalJob blockingJob) {
		threadJob.isBlocked = true;
		manager.reportBlocked(monitor, blockingJob);
	}

	/**
	 * ThreadJobs are one-shot jobs, and they must ignore all attempts to schedule them. 
	 */
	@Override
	public boolean shouldSchedule() {
		return false;
	}
}