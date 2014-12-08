/*
 * حق نشر 1392-1402 دانش پژوهان ققنوس
 * حقوق این اثر محفوظ است.
 * 
 * استفاده مجدد از متن و یا نتایج این اثر در هر شکل غیر قانونی است مگر اینکه متن حق
 * نشر بالا در ابتدای تمامی مستندهای و یا برنامه‌های به دست آمده از این اثر
 * بازنویسی شود. این کار باید برای تمامی مستندها، متنهای تبلیغاتی برنامه‌های
 * کاربردی و سایر مواردی که از این اثر به دست می‌آید مندرج شده و در قسمت تقدیر از
 * صاحب این اثر نام برده شود.
 * 
 * نام گروه دانش پژوهان ققنوس ممکن است در محصولات به در آمده شده از این اثر درج
 * نشود که در این حالت با مطالبی که در بالا اورده شده در تضاد نیست. برای اطلاع
 * بیشتر در مورد حق نشر آدرس زیر مراجعه کنید:
 * 
 * http://dpq.co.ir/licenses
 */
package ir.co.dpq.runtime.jobs;

import ir.co.dpq.runtime.*;

public abstract class Job {

	/**
	 * Job status return value that is used to indicate asynchronous job
	 * completion.
	 * 
	 * @see Job#run(IProgressMonitor)
	 * @see Job#done(IStatus)
	 */
	public static final IStatus ASYNC_FINISH = new Status(IStatus.OK,
			JobManager.PI_JOBS, 1, "", null);//$NON-NLS-1$

	/**
	 * Job priority constant (value 20) for short background jobs. Short
	 * background jobs are jobs that typically complete within a second, but may
	 * take longer in some cases. Short jobs are given priority over all other
	 * jobs except interactive jobs.
	 * 
	 * @see #getPriority()
	 * @see #setPriority(int)
	 * @see #run(IProgressMonitor)
	 */
	public static final int SHORT = 20;

	/**
	 * Job priority constant (value 30) for long-running background jobs.
	 * 
	 * @see #getPriority()
	 * @see #setPriority(int)
	 * @see #run(IProgressMonitor)
	 */
	public static final int LONG = 30;

	/**
	 * Job state code (value 16) indicating that a job has been removed from the
	 * wait queue and is about to start running. From an API point of view, this
	 * is the same as RUNNING.
	 */
	static final int ABOUT_TO_RUN = 0x10;

	/**
	 * Job state code (value 32) indicating that a job has passed scheduling
	 * precondition checks and is about to be added to the wait queue. From an
	 * API point of view, this is the same as WAITING.
	 */
	static final int ABOUT_TO_SCHEDULE = 0x20;

	/**
	 * Job state code (value 8) indicating that a job is blocked by another
	 * currently running job. From an API point of view, this is the same as
	 * WAITING.
	 */
	static final int BLOCKED = 0x08;

	/**
	 * Job state code (value 64) indicating that a job is yielding. From an API
	 * point of view, this is the same as WAITING.
	 */
	static final int YIELDING = 0x40;

	/**
	 * Job state code (value 0) indicating that a job is not currently sleeping,
	 * waiting, or running (i.e., the job manager doesn't know anything about
	 * the job).
	 * 
	 * @see #getState()
	 */
	public static final int NONE = 0;

	/**
	 * Job state code (value 1) indicating that a job is sleeping.
	 * 
	 * @see #run(IProgressMonitor)
	 * @see #getState()
	 */
	public static final int SLEEPING = 0x01;

	/**
	 * Job state code (value 2) indicating that a job is waiting to run.
	 * 
	 * @see #getState()
	 * @see #yieldRule(IProgressMonitor)
	 */
	public static final int WAITING = 0x02;

	/**
	 * Job state code (value 4) indicating that a job is currently running
	 * 
	 * @see #getState()
	 */
	public static final int RUNNING = 0x04;

	// flag mask bits
	private static final int M_STATE = 0xFF;

	/*
	 * flag on a job indicating that it was about to run, but has been canceled
	 */
	private static final int M_ABOUT_TO_RUN_CANCELED = 0x0400;

	/*
	 * Flag on a job indicating that it was canceled when running. This flag is
	 * used to ensure that #canceling is only ever called once on a job in case
	 * of recursive cancelation attempts.
	 */
	private static final int M_RUN_CANCELED = 0x0800;

	/**
	 * Start time constant indicating a job should be started at a time in the
	 * infinite future, causing it to sleep forever.
	 */
	static final long T_INFINITE = Long.MAX_VALUE;

	/**
	 * Start time constant indicating that the job has no start time.
	 */
	static final long T_NONE = -1;

	private ListenerList listeners = null;
	private volatile int flags = NONE;
	private Thread thread;
	private int priority = LONG;
	private String name;
	private volatile IProgressMonitor monitor;
	protected static final JobManager manager = JobManager.getInstance();

	/**
	 * Volatile because it is usually set via a Worker thread and is read via a
	 * client thread.
	 */
	private volatile IStatus result;

	/**
	 * If the job is waiting, this represents the time the job should start by.
	 * If this job is sleeping, this represents the time the job should wake up.
	 * If this job is running, this represents the delay automatic rescheduling,
	 * or -1 if the job should not be rescheduled.
	 * 
	 * @GuardedBy("manager.lock")
	 */
	private long startTime;

	/**
	 * This lock will be held while performing state changes on this job. It is
	 * also used as a notifier used to wake up yielding jobs or waiting
	 * ThreadJobs when 1) a conflicting job completes and releases a scheduling
	 * rule, or 2) when a this job changes state.
	 * 
	 * See also the lock ordering protocol explanation in JobManager's
	 * documentation.
	 * 
	 * @GuardedBy("itself")
	 */
	final Object jobStateLock = new Object();

	private long waitQueueStamp;

	private Object schedulingRule;

	/**
	 * Returns the result of this job's last run.
	 * 
	 * @return the result of this job's last run, or <code>null</code> if this
	 *         job has never finished running.
	 */
	public final IStatus getResult() {
		return result;
	}

	public void setResult(IStatus result) {
		this.result = result;
	}

	/**
	 * Returns the state of the job. Result will be one of:
	 * <ul>
	 * <li><code>Job.RUNNING</code> - if the job is currently running.</li>
	 * <li><code>Job.WAITING</code> - if the job is waiting to be run.</li>
	 * <li><code>Job.SLEEPING</code> - if the job is sleeping.</li>
	 * <li><code>Job.NONE</code> - in all other cases.</li>
	 * </ul>
	 * <p>
	 * Note that job state is inherently volatile, and in most cases clients
	 * cannot rely on the result of this method being valid by the time the
	 * result is obtained. For example, if <tt>getState</tt> returns
	 * <tt>RUNNING</tt>, the job may have actually completed by the time the
	 * <tt>getState</tt> method returns. All clients can infer from invoking
	 * this method is that the job was recently in the returned state.
	 * 
	 * @return the job state
	 */
	public final int getState() {
		int state = flags & M_STATE;
		switch (state) {
		// blocked and yielding state is equivalent to waiting state for clients
		case YIELDING:
		case BLOCKED:
			return Job.WAITING;
		case ABOUT_TO_RUN:
			return Job.RUNNING;
		case ABOUT_TO_SCHEDULE:
			return Job.WAITING;
		default:
			return state;
		}
	}

	final int getInternalState() {
		return flags & M_STATE;
	}

	final void setInternalState(int newState) {
		flags = (flags & ~M_STATE) | newState;
	}

	/**
	 * Returns whether this job was canceled when it was about to run
	 */
	final boolean isAboutToRunCanceled() {
		return (flags & M_ABOUT_TO_RUN_CANCELED) != 0;
	}

	/**
	 * Sets whether this job was canceled when it was about to run
	 */
	final void setAboutToRunCanceled(boolean value) {
		flags = value ? flags | M_ABOUT_TO_RUN_CANCELED : flags
				& ~M_ABOUT_TO_RUN_CANCELED;

	}

	/**
	 * Returns whether this job was canceled when it was running.
	 */
	final boolean isRunCanceled() {
		return (flags & M_RUN_CANCELED) != 0;
	}

	/**
	 * Sets whether this job was canceled when it was running
	 */
	final void setRunCanceled(boolean value) {
		flags = value ? flags | M_RUN_CANCELED : flags & ~M_RUN_CANCELED;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Job#isBlocking()
	 */
	protected boolean isBlocking() {
		return manager.isBlocking(this);
	}

	/**
	 * Returns the thread that this job is currently running in.
	 * 
	 * @return the thread this job is running in, or <code>null</code> if this
	 *         job is not running or the thread is unknown.
	 */
	public final Thread getThread() {
		return this.thread;
	}

	/**
	 * Executes this job. Returns the result of the execution.
	 * <p>
	 * The provided monitor can be used to report progress and respond to
	 * cancellation. If the progress monitor has been canceled, the job should
	 * finish its execution at the earliest convenience and return a result
	 * status of severity {@link IStatus#CANCEL}. The singleton cancel status
	 * {@link Status#CANCEL_STATUS} can be used for this purpose. The monitor is
	 * only valid for the duration of the invocation of this method.
	 * <p>
	 * This method must not be called directly by clients. Clients should call
	 * <code>schedule</code>, which will in turn cause this method to be called.
	 * <p>
	 * Jobs can optionally finish their execution asynchronously (in another
	 * thread) by returning a result status of {@link #ASYNC_FINISH}. Jobs that
	 * finish asynchronously <b>must</b> specify the execution thread by calling
	 * <code>setThread</code>, and must indicate when they are finished by
	 * calling the method <code>done</code>.
	 * 
	 * @param monitor
	 *            the monitor to be used for reporting progress and responding
	 *            to cancelation. The monitor is never <code>null</code>
	 * @return resulting status of the run. The result must not be
	 *         <code>null</code>
	 * @see #ASYNC_FINISH
	 * @see #done(IStatus)
	 */
	protected abstract IStatus run(IProgressMonitor monitor);

	/**
	 * Schedules this job to be run. The job is added to a queue of waiting
	 * jobs, and will be run when it arrives at the beginning of the queue.
	 * <p>
	 * This is a convenience method, fully equivalent to
	 * <code>schedule(0L)</code>.
	 * </p>
	 * 
	 * @see #schedule(long)
	 */
	public final void schedule() {
		if (shouldSchedule())
			manager.schedule(this, 0, false);
	}

	/**
	 * Schedules this job to be run after a specified delay. The job is put in
	 * the {@link #SLEEPING} state until the specified delay has elapsed, after
	 * which the job is added to a queue of {@link #WAITING} jobs. Once the job
	 * arrives at the beginning of the queue, it will be run at the first
	 * available opportunity. </p>
	 * <p>
	 * Jobs of equal priority and <code>delay</code> with conflicting scheduling
	 * rules are guaranteed to run in the order they are scheduled. No
	 * guarantees are made about the relative execution order of jobs with
	 * unrelated or <code>null</code> scheduling rules, or different priorities.
	 * <p>
	 * If this job is currently running, it will be rescheduled with the
	 * specified delay as soon as it finishes. If this method is called multiple
	 * times while the job is running, the job will still only be rescheduled
	 * once, with the most recent delay value that was provided.
	 * </p>
	 * <p>
	 * Scheduling a job that is waiting or sleeping has no effect.
	 * </p>
	 * 
	 * @param delay
	 *            a time delay in milliseconds before the job should run
	 * @see ISchedulingRule
	 */
	public final void schedule(long delay) {
		if (shouldSchedule())
			manager.schedule(this, delay, false);
	}

	/**
	 * Changes the name of this job. If the job is currently running, waiting,
	 * or sleeping, the new job name may not take effect until the next time the
	 * job is scheduled.
	 * <p>
	 * The job name is a human-readable value that is displayed to users. The
	 * name does not need to be unique, but it must not be <code>null</code>.
	 * 
	 * @param name
	 *            the name of the job.
	 */
	public final void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	/**
	 * Sets the priority of the job. This will not affect the execution of a
	 * running job, but it will affect how the job is scheduled while it is
	 * waiting to be run.
	 * 
	 * @param priority
	 *            the new job priority. One of INTERACTIVE, SHORT, LONG, BUILD,
	 *            or DECORATE.
	 */
	public final void setPriority(int priority) {
		this.priority = priority;
	}

	public int getPriority() {
		return this.priority;
	}

	/**
	 * Sets the thread that this job is currently running in, or
	 * <code>null</code> if this job is not running or the thread is unknown.
	 * <p>
	 * Jobs that use the {@link #ASYNC_FINISH} return code should tell the job
	 * what thread it is running in. This is used to prevent deadlocks.
	 * 
	 * @param thread
	 *            the thread that this job is running in.
	 * 
	 * @see #ASYNC_FINISH
	 * @see #run(IProgressMonitor)
	 */
	public final void setThread(Thread thread) {
		this.thread = thread;
	}

	/**
	 * Returns whether this job should be run. If <code>false</code> is
	 * returned, this job will be discarded by the job manager without running.
	 * <p>
	 * This method is called immediately prior to calling the job's run method,
	 * so it can be used for last minute precondition checking before a job is
	 * run. This method must not attempt to schedule or change the state of any
	 * other job.
	 * </p>
	 * <p>
	 * Clients may override this method. This default implementation always
	 * returns <code>true</code>.
	 * </p>
	 * 
	 * @return <code>true</code> if this job should be run and
	 *         <code>false</code> otherwise
	 */
	public boolean shouldRun() {
		return true;
	}

	/**
	 * Returns whether this job should be scheduled. If <code>false</code> is
	 * returned, this job will be discarded by the job manager without being
	 * added to the queue.
	 * <p>
	 * This method is called immediately prior to adding the job to the waiting
	 * job queue.,so it can be used for last minute precondition checking before
	 * a job is scheduled.
	 * </p>
	 * <p>
	 * Clients may override this method. This default implementation always
	 * returns <code>true</code>.
	 * </p>
	 * 
	 * @return <code>true</code> if the job manager should schedule this job and
	 *         <code>false</code> otherwise
	 */
	public boolean shouldSchedule() {
		return true;
	}

	/**
	 * Sets a time to start, wake up, or schedule this job, depending on the
	 * current state
	 * 
	 * @param time
	 *            a time in milliseconds
	 * @GuardedBy("manager.lock")
	 */
	final void setStartTime(long time) {
		startTime = time;
	}

	public long getStartTime() {
		return startTime;
	}

	/**
	 * Returns the job's progress monitor, or null if it is not running.
	 */
	final IProgressMonitor getProgressMonitor() {
		return monitor;
	}

	/**
	 * Sets the progress monitor to use for the next execution of this job, or
	 * for clearing the monitor when a job completes.
	 * 
	 * @param monitor
	 *            a progress monitor
	 */
	final void setProgressMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

	public ISchedulingRule getRule() {
		// TODO Auto-generated method stub
		return null;
	}

	public void addLast(Job job) {
		// TODO Auto-generated method stub

	}

	public Job previous() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isSystem() {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Job#wakeUp(long)
	 */
	protected void wakeUp(long delay) {
		manager.wakeUp(this, delay);
	}

	/**
	 * @param waitQueueStamp
	 *            The waitQueueStamp to set.
	 * @GuardedBy("manager.lock")
	 */
	void setWaitQueueStamp(long waitQueueStamp) {
		this.waitQueueStamp = waitQueueStamp;
	}

	/**
	 * @return Returns the waitQueueStamp.
	 * @GuardedBy("manager.lock")
	 */
	long getWaitQueueStamp() {
		return waitQueueStamp;
	}

	/**
	 * Removes this entry from any list it belongs to. Returns the receiver.
	 */
	final Job remove() {
		// if (next != null)
		// next.setPrevious(previous);
		// if (previous != null)
		// previous.setNext(next);
		// next = previous = null;
		return this;
	}

	/**
	 * Returns true if this job conflicts with the given job, and false
	 * otherwise.
	 */
	final boolean isConflicting(Job otherJob) {
		ISchedulingRule otherRule = otherJob.getRule();
		if (schedulingRule == null || otherRule == null)
			return false;
		// if one of the rules is a compound rule, it must be asked the
		// question.
		// if (schedulingRule.getClass() == MultiRule.class)
		// return schedulingRule.isConflicting(otherRule);
		// return otherRule.isConflicting(schedulingRule);
		return false;
	}

	/**
	 * Stops the job. If the job is currently waiting, it will be removed from
	 * the queue. If the job is sleeping, it will be discarded without having a
	 * chance to resume and its sleeping state will be cleared. If the job is
	 * currently executing, it will be asked to stop but there is no guarantee
	 * that it will do so.
	 * 
	 * @return <code>false</code> if the job is currently running (and thus may
	 *         not respond to cancelation), and <code>true</code> in all other
	 *         cases.
	 */
	public final boolean cancel() {
		return manager.cancel(this);
	}

	/**
	 * A hook method indicating that this job is running and {@link #cancel()}
	 * is being called for the first time.
	 * <p>
	 * Subclasses may override this method to perform additional work when a
	 * cancelation request is made. This default implementation does nothing.
	 * 
	 * @since 3.3
	 */
	protected void canceling() {
		// default implementation does nothing
	}

	/**
	 * Waits until this job is finished. This method will block the calling
	 * thread until the job has finished executing, or until this thread has
	 * been interrupted. If the job has not been scheduled, this method returns
	 * immediately. A job must not be joined from within the scope of its run
	 * method.
	 * <p>
	 * If this method is called on a job that reschedules itself from within the
	 * <tt>run</tt> method, the join will return at the end of the first
	 * execution. In other words, join will return the first time this job exits
	 * the {@link #RUNNING} state, or as soon as this job enters the
	 * {@link #NONE} state.
	 * </p>
	 * <p>
	 * If this method is called while the job manager is suspended, this job
	 * will only be joined if it is already running; if this job is waiting or
	 * sleeping, this method returns immediately.
	 * </p>
	 * <p>
	 * Note that there is a deadlock risk when using join. If the calling thread
	 * owns a lock or object monitor that the joined thread is waiting for,
	 * deadlock will occur.
	 * </p>
	 * 
	 * @exception InterruptedException
	 *                if this thread is interrupted while waiting
	 * @see ILock
	 * @see IJobManager#suspend()
	 */
	public final void join() throws InterruptedException {
		manager.join(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Job#addJobListener(IJobChangeListener)
	 */
	protected synchronized void addJobChangeListener(IJobChangeListener listener) {
		if (listeners == null)
			listeners = new ListenerList(ListenerList.IDENTITY);
		listeners.add(listener);
	}

	protected synchronized void removeJobChangeListener(
			IJobChangeListener listener) {
		if (listeners != null) {
			listeners.remove(listener);
			if (listeners.isEmpty())
				listeners = null;
		}
	}
}
