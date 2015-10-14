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
 * نام گروه دانش پژوهان ققنوس ممکن است در محصولات به دست آمده از این اثر درج
 * نشود که در این حالت با مطالبی که در بالا اورده شده در تضاد نیست. برای اطلاع
 * بیشتر در مورد حق نشر آدرس زیر مراجعه کنید:
 * 
 * http://dpq.co.ir/licenses
 */
package ir.co.dpq.runtime.jobs;

import ir.co.dpq.runtime.*;

import java.util.HashSet;
import java.util.Iterator;

public class JobManager implements IJobManager {

	/**
	 * The unique identifier constant of this plug-in.
	 */
	public static final String PI_JOBS = "org.eclipse.core.jobs"; //$NON-NLS-1$

	/**
	 * Status code constant indicating an error occurred while running a
	 * plug-in. For backward compatibility with Platform.PLUGIN_ERROR left at
	 * (value = 2).
	 */
	public static final int PLUGIN_ERROR = 2;

	static boolean DEBUG = false;
	private static JobManager instance;

	/**
	 * The lock for synchronizing all activity in the job manager. To avoid
	 * deadlock, this lock must never be held for extended periods, and must
	 * never be held while third party code is being called.
	 * 
	 * @GuardedBy("itself")
	 */
	private final Object lock = new Object();

	/**
	 * @GuardedBy("lock")
	 */
	private ProgressProvider progressProvider = null;

	/**
	 * The pool of worker threads.
	 */
	private WorkerPool pool;

	/**
	 * True if this manager is active, and false otherwise. A job manager starts
	 * out active, and becomes inactive if it has been shutdown.
	 */
	private volatile boolean active = true;
	/**
	 * Jobs that are currently running. Should only be modified from changeState
	 * 
	 * @GuardedBy("lock")
	 */
	private final HashSet<Job> running;

	/**
	 * Jobs that are currently yielding. Should only be modified from
	 * changeState
	 * 
	 * @GuardedBy("lock")
	 */
	private final HashSet<Job> yielding;

	/**
	 * Jobs that are sleeping. Some sleeping jobs are scheduled to wake up at a
	 * given start time, while others will sleep indefinitely until woken.
	 * Should only be modified from changeState
	 * 
	 * @GuardedBy("lock")
	 */
	private final JobQueue sleeping;
	/**
	 * True if this manager has been suspended, and false otherwise. A job
	 * manager starts out not suspended, and becomes suspended when
	 * <code>suspend</code> is invoked. Once suspended, no jobs will start
	 * running until <code>resume</code> is called.
	 * 
	 * @GuardedBy("lock")
	 */
	private boolean suspended = false;

	/**
	 * jobs that are waiting to be run. Should only be modified from changeState
	 * 
	 * @GuardedBy("lock")
	 */
	private final JobQueue waiting;

	/**
	 * ThreadJobs that are waiting to be run. Should only be modified from
	 * changeState
	 * 
	 * @GuardedBy("lock")
	 */
	final JobQueue waitingThreadJobs;

	private JobManager() {
		instance = this;
		initDebugOptions();
		synchronized (lock) {
			waiting = new JobQueue(false);
			waitingThreadJobs = new JobQueue(false, false);
			sleeping = new JobQueue(true);
			running = new HashSet<Job>(10);
			yielding = new HashSet<Job>(10);
			pool = new WorkerPool(this);
		}
		// pool.setDaemon(JobOSGiUtils.getDefault().useDaemonThreads());
		// internalWorker = new InternalWorker(this);
		// internalWorker.setDaemon(JobOSGiUtils.getDefault().useDaemonThreads());
		// internalWorker.start();
	}

	/**
	 * Returns the job manager singleton. For internal use only.
	 */
	public static synchronized JobManager getInstance() {
		if (instance == null)
			instance = new JobManager();
		return instance;
	}

	public IJobManager schedule(Job job, long delay, boolean reschedule) {
		if (!active)
			throw new IllegalStateException("Job manager has been shut down."); //$NON-NLS-1$
		Assert.isNotNull(job, "Job is null"); //$NON-NLS-1$
		Assert.isLegal(delay >= 0, "Scheduling delay is negative"); //$NON-NLS-1$
		synchronized (lock) {
			// if the job is already running, set it to be rescheduled when done
			if (job.getState() == Job.RUNNING) {
				job.setStartTime(delay);
				return this;
			}
			// can't schedule a job that is waiting or sleeping
			if (job.getInternalState() != Job.NONE)
				return this;
			if (JobManager.DEBUG) {
				JobManager.debug("Scheduling job: " + job); //$NON-NLS-1$
			}
			// remember that we are about to schedule the job
			// to prevent multiple schedule attempts from succeeding (bug 68452)
			changeState(job, Job.ABOUT_TO_SCHEDULE);
		}
		// notify listeners outside sync block
		// jobListeners.scheduled((Job) job, delay, reschedule);
		// schedule the job
		doSchedule(job, delay);
		// call the pool outside sync block to avoid deadlock
		pool.jobQueued();
		return this;
	}

	/**
	 * Performs the scheduling of a job. Does not perform any notifications.
	 */
	private void doSchedule(Job job, long delay) {
		synchronized (lock) {
			// job may have been canceled already
			int state = job.getInternalState();
			if (state != Job.ABOUT_TO_SCHEDULE && state != Job.SLEEPING)
				return;
			if (delay > 0) {
				job.setStartTime(System.currentTimeMillis() + delay);
				changeState(job, Job.SLEEPING);
			} else {
				job.setStartTime(System.currentTimeMillis() + delayFor(job.getPriority()));
				// job.setWaitQueueStamp(waitQueueCounter.increment());
				changeState(job, Job.WAITING);
			}
		}
	}

	/**
	 * Atomically updates the state of a job, adding or removing from the
	 * necessary queues or sets.
	 */
	private void changeState(Job job, int newState) {
		boolean blockedJobs = false;
		synchronized (lock) {
			synchronized (job.jobStateLock) {
				job.jobStateLock.notifyAll();
				int oldState = job.getInternalState();
				switch (oldState) {
				case Job.YIELDING:
					yielding.remove(job);
				case Job.NONE:
				case Job.ABOUT_TO_SCHEDULE:
					break;
				case Job.BLOCKED:
					// remove this job from the linked list of blocked jobs
					job.remove();
					break;
				case Job.WAITING:
					try {
						waiting.remove(job);
					} catch (RuntimeException e) {
						// Assert.isLegal(false,
						// "Tried to remove a job that wasn't in the queue");
						// //$NON-NLS-1$
					}
					break;
				case Job.SLEEPING:
					try {
						sleeping.remove(job);
					} catch (RuntimeException e) {
						// Assert.isLegal(false,
						// "Tried to remove a job that wasn't in the queue");
						// //$NON-NLS-1$
					}
					break;
				case Job.RUNNING:
				case Job.ABOUT_TO_RUN:
					running.remove(job);
					// add any blocked jobs back to the wait queue
					Job blocked = job.previous();
					job.remove();
					blockedJobs = blocked != null;
					while (blocked != null) {
						Job previous = blocked.previous();
						changeState(blocked, Job.WAITING);
						blocked = previous;
					}
					break;
				default:
					// Assert.isLegal(
					// false,
					// "Invalid job state: " + job + ", state: " + oldState);
					// //$NON-NLS-1$ //$NON-NLS-2$
				}
				job.setInternalState(newState);
				switch (newState) {
				case Job.NONE:
					job.setStartTime(Job.T_NONE);
					job.setWaitQueueStamp(Job.T_NONE);
					job.setRunCanceled(false);
				case Job.BLOCKED:
					break;
				case Job.WAITING:
					waiting.enqueue(job);
					break;
				case Job.SLEEPING:
					try {
						sleeping.enqueue(job);
					} catch (RuntimeException e) {
						throw new RuntimeException("Error changing from state: " + oldState); //$NON-NLS-1$
					}
					break;
				case Job.RUNNING:
				case Job.ABOUT_TO_RUN:
					// These flags must be reset in all cases, including
					// resuming from yield
					job.setStartTime(Job.T_NONE);
					job.setWaitQueueStamp(Job.T_NONE);
					running.add(job);
					break;
				case Job.YIELDING:
					yielding.add(job);
				case Job.ABOUT_TO_SCHEDULE:
					break;
				// default:
				// Assert.isLegal(
				// false, "Invalid job state: " + job + ", state: " + newState);
				// //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		}
		// notify queue outside sync block
		if (blockedJobs)
			pool.jobQueued();
	}

	public static void debug(String msg) {
		StringBuffer msgBuf = new StringBuffer(msg.length() + 40);
		// if (DEBUG_TIMING) {
		// //lazy initialize to avoid overhead when not debugging
		// if (DEBUG_FORMAT == null)
		// DEBUG_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS"); //$NON-NLS-1$
		// DEBUG_FORMAT.format(new Date(), msgBuf, new FieldPosition(0));
		// msgBuf.append('-');
		// }
		msgBuf.append('[').append(Thread.currentThread()).append(']').append(msg);
		System.out.println(msgBuf.toString());
	}

	public boolean isActive() {
		return true;
	}

	/**
	 * Returns the next job to be run, or null if no jobs are waiting to run.
	 * The worker must call endJob when the job is finished running.
	 */
	protected Job startJob(Worker worker) {
		Job job = null;
		while (true) {
			job = nextJob();
			if (job == null)
				return null;
			// must perform this outside sync block because it is third party
			// code
			boolean shouldRun = shouldRun(job);
			// check for listener veto
			if (shouldRun) {
				// jobListeners.aboutToRun(job);
			}
			// listeners may have canceled or put the job to sleep
			boolean endJob = false;
			synchronized (lock) {
				Job internal = job;
				synchronized (internal.jobStateLock) {
					if (internal.getInternalState() == Job.ABOUT_TO_RUN) {
						if (shouldRun && !internal.isAboutToRunCanceled()) {
							internal.setProgressMonitor(createMonitor(job));
							// change from ABOUT_TO_RUN to RUNNING
							internal.setThread(worker);
							internal.setInternalState(Job.RUNNING);
							internal.jobStateLock.notifyAll();
							break;
						}
						internal.setAboutToRunCanceled(false);
						endJob = true;
						// fall through and end the job below
					}
				}
			}
			if (endJob) {
				// job has been vetoed or canceled, so mark it as done
				endJob(job, Status.CANCEL_STATUS, true);
				continue;
			}
		}
		// jobListeners.running(job);
		return job;

	}

	/**
	 * Returns the estimated time in milliseconds before the next job is
	 * scheduled to wake up. The result may be negative. Returns
	 * InternalJob.T_INFINITE if there are no sleeping or waiting jobs.
	 */
	protected long sleepHint() {
		synchronized (lock) {
			// wait forever if job manager is suspended
			if (suspended)
				return Job.T_INFINITE;
			if (!waiting.isEmpty())
				return 0L;
			// return the anticipated time that the next sleeping job will wake
			Job next = sleeping.peek();
			if (next == null)
				return Job.T_INFINITE;
			return next.getStartTime() - System.currentTimeMillis();
		}
	}

	/**
	 * Removes and returns the first waiting job in the queue. Returns null if
	 * there are no items waiting in the queue. If an item is removed from the
	 * queue, it is moved to the running jobs list.
	 */
	private Job nextJob() {
		synchronized (lock) {
			// do nothing if the job manager is suspended
			if (suspended)
				return null;
			// tickle the sleep queue to see if anyone wakes up
			long now = System.currentTimeMillis();
			Job job = sleeping.peek();
			while (job != null && job.getStartTime() < now) {
				job.setStartTime(now + delayFor(job.getPriority()));
				// job.setWaitQueueStamp(waitQueueCounter.increment());
				changeState(job, Job.WAITING);
				job = sleeping.peek();
			}
			// process the wait queue until we find a job whose rules are
			// satisfied.
			while ((job = waiting.peek()) != null) {
				Job blocker = findBlockingJob(job);
				if (blocker == null)
					break;
				// queue this job after the job that's blocking it
				changeState(job, Job.BLOCKED);
				// assert job does not already belong to some other data
				// structure
				// Assert.isTrue(job.next() == null);
				// Assert.isTrue(job.previous() == null);
				blocker.addLast(job);
			}
			// the job to run must be in the running list before we exit
			// the sync block, otherwise two jobs with conflicting rules could
			// start at once
			if (job != null) {
				changeState(job, Job.ABOUT_TO_RUN);
				if (JobManager.DEBUG)
					JobManager.debug("Starting job: " + job); //$NON-NLS-1$
			}
			return (Job) job;
		}
	}

	/**
	 * Returns a new progress monitor for this job. Never returns null.
	 * 
	 * @GuardedBy("lock")
	 */
	private IProgressMonitor createMonitor(Job job) {
		IProgressMonitor monitor = null;
		if (progressProvider != null)
			monitor = progressProvider.createMonitor(job);
		if (monitor == null)
			monitor = new NullProgressMonitor();
		return monitor;
	}

	/**
	 * Returns the delay in milliseconds that a job with a given priority can
	 * tolerate waiting.
	 */
	private long delayFor(int priority) {
		// these values may need to be tweaked based on machine speed
		switch (priority) {
		// case Job.INTERACTIVE :
		// return 0L;
		case Job.SHORT:
			return 50L;
		case Job.LONG:
			return 100L;
		// case Job.BUILD :
		// return 500L;
		// case Job.DECORATE :
		// return 1000L;
		default:
			// Assert.isTrue(false, "Job has invalid priority: " + priority);
			// //$NON-NLS-1$
			return 0;
		}
	}

	/**
	 * Invokes {@link Job#shouldRun()} while guarding against unexpected
	 * failures.
	 */
	private boolean shouldRun(Job job) {
		try {
			return job.shouldRun();
		} catch (Exception | LinkageError | AssertionError e) {
			// RuntimeLog.log(new Status(IStatus.ERROR, JobManager.PI_JOBS,
			// JobManager.PLUGIN_ERROR, "Error invoking shouldRun() method on: "
			// + job, t)); //$NON-NLS-1$
		}
		// if the should is unexpectedly failing it is safer not to run it
		return false;
	}

	/**
	 * Indicates that a job was running, and has now finished. Note that this
	 * method can be called under OutOfMemoryError conditions and thus must be
	 * paranoid about allocating objects.
	 */
	protected void endJob(Job job, IStatus result, boolean notify) {
		long rescheduleDelay = Job.T_NONE;
		synchronized (lock) {
			// if the job is finishing asynchronously, there is nothing more to
			// do for now
			if (result == Job.ASYNC_FINISH)
				return;
			// if job is not known then it cannot be done
			if (job.getState() == Job.NONE)
				return;
			if (JobManager.DEBUG && notify)
				JobManager.debug("Ending job: " + job); //$NON-NLS-1$
			job.setResult(result);
			job.setProgressMonitor(null);
			job.setThread(null);
			rescheduleDelay = job.getStartTime();
			changeState(job, Job.NONE);
		}
		// notify listeners outside sync block
		final boolean reschedule = active && rescheduleDelay > Job.T_NONE && job.shouldSchedule();
		if (notify) {
			// jobListeners.done((Job) job, result, reschedule);
		}
		// reschedule the job if requested and we are still active
		if (reschedule)
			schedule(job, rescheduleDelay, reschedule);
		// log result if it is warning or error
		if ((result.getSeverity() & (IStatus.ERROR | IStatus.WARNING)) != 0) {
			// RuntimeLog.log(result);
		}
	}

	private void initDebugOptions() {
		// DEBUG =
		// JobOSGiUtils.getDefault().getBooleanDebugOption(OPTION_DEBUG_JOBS,
		// false);
		// DEBUG_BEGIN_END =
		// JobOSGiUtils.getDefault().getBooleanDebugOption(OPTION_DEBUG_BEGIN_END,
		// false);
		// DEBUG_YIELDING =
		// JobOSGiUtils.getDefault().getBooleanDebugOption(OPTION_DEBUG_YIELDING,
		// false);
		// DEBUG_YIELDING_DETAILED =
		// JobOSGiUtils.getDefault().getBooleanDebugOption(OPTION_DEBUG_YIELDING_DETAILED,
		// false);
		// DEBUG_DEADLOCK =
		// JobOSGiUtils.getDefault().getBooleanDebugOption(OPTION_DEADLOCK_ERROR,
		// false);
		// DEBUG_LOCKS =
		// JobOSGiUtils.getDefault().getBooleanDebugOption(OPTION_LOCKS, false);
		// DEBUG_TIMING =
		// JobOSGiUtils.getDefault().getBooleanDebugOption(OPTION_DEBUG_JOBS_TIMING,
		// false);
		// DEBUG_SHUTDOWN =
		// JobOSGiUtils.getDefault().getBooleanDebugOption(OPTION_SHUTDOWN,
		// false);
	}

	/**
	 * Returns a running or blocked job whose scheduling rule conflicts with the
	 * scheduling rule of the given waiting job. Returns null if there are no
	 * conflicting jobs. A job can only run if there are no running jobs and no
	 * blocked jobs whose scheduling rule conflicts with its rule.
	 */
	protected Job findBlockingJob(Job waitingJob) {
		if (waitingJob.getRule() == null)
			return null;
		synchronized (lock) {
			if (running.isEmpty())
				return null;
			// check the running jobs
			boolean hasBlockedJobs = false;
			for (Iterator<Job> it = running.iterator(); it.hasNext();) {
				Job job = (Job) it.next();
				if (waitingJob.isConflicting(job))
					return job;
				if (!hasBlockedJobs)
					hasBlockedJobs = job.previous() != null;
			}
			// there are no blocked jobs, so we are done
			if (!hasBlockedJobs)
				return null;
			// check all jobs blocked by running jobs
			for (Iterator<Job> it = running.iterator(); it.hasNext();) {
				Job job = (Job) it.next();
				while (true) {
					job = job.previous();
					if (job == null)
						break;
					if (waitingJob.isConflicting(job))
						return job;
				}
			}
		}
		return null;
	}

	/**
	 * 
	 * @see IJobManager#setProgressProvider(IProgressProvider)
	 */
	public IJobManager setProgressProvider(ProgressProvider provider) {
		progressProvider = provider;
		return this;
	}

	/**
	 * Returns true if the given job is blocking the execution of a non-system
	 * job.
	 */
	protected boolean isBlocking(Job runningJob) {
		synchronized (lock) {
			// if this job isn't running, it can't be blocking anyone
			if (runningJob.getState() != Job.RUNNING)
				return false;
			// if any job is queued behind this one, it is blocked by it
			Job previous = runningJob.previous();
			while (previous != null) {
				// ignore jobs of lower priority (higher priority value means
				// lower priority)
				if (previous.getPriority() < runningJob.getPriority()) {
					if (!previous.isSystem())
						return true;
					// implicit jobs should interrupt unless they act on behalf
					// of system jobs
					// if (previous instanceof ThreadJob && ((ThreadJob)
					// previous).shouldInterrupt())
					// return true;
				}
				previous = previous.previous();
			}
			// none found
			return false;
		}
	}

	public void wakeUp(Job job, long delay) {
		// TODO Auto-generated method stub

	}

	/**
	 * Cancels a job
	 */
	protected boolean cancel(Job job) {
		IProgressMonitor monitor = null;
		boolean runCanceling = false;
		synchronized (lock) {
			switch (job.getState()) {
			case Job.NONE:
				return true;
			case Job.RUNNING:
				// cannot cancel a job that has already started (as opposed to
				// ABOUT_TO_RUN)
				if (job.getInternalState() == Job.RUNNING) {
					monitor = job.getProgressMonitor();
					runCanceling = !job.isRunCanceled();
					if (runCanceling)
						job.setRunCanceled(true);
					break;
				}
				// signal that the job should be canceled before it gets a
				// chance to run
				job.setAboutToRunCanceled(true);
				return false;
			default:
				changeState(job, Job.NONE);
			}
		}
		// call monitor and canceling outside sync block
		if (monitor != null) {
			if (runCanceling) {
				if (!monitor.isCanceled())
					monitor.setCanceled(true);
				job.canceling();
			}
			return false;
		}
		// only notify listeners if the job was waiting or sleeping
		// jobListeners.done((Job) job, Status.CANCEL_STATUS, false);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.jobs.Job#job(org.eclipse.core.runtime.jobs.Job)
	 */
	protected void join(Job job) {
		final IJobChangeListener listener;
		final Semaphore barrier;
		synchronized (lock) {
			int state = job.getState();
			if (state == Job.NONE)
				return;
			// don't join a waiting or sleeping job when suspended (deadlock
			// risk)
			if (suspended && state != Job.RUNNING)
				return;
			// it's an error for a job to join itself
			if (state == Job.RUNNING && job.getThread() == Thread.currentThread())
				throw new IllegalStateException("Job attempted to join itself"); //$NON-NLS-1$
			// the semaphore will be released when the job is done
			barrier = new Semaphore(null);
			listener = new JobChangeAdapter() {
				public void done(IJobChangeEvent event) {
					barrier.release();
				}
			};
			job.addJobChangeListener(listener);
			// compute set of all jobs that must run before this one
			// add a listener that removes jobs from the blocking set when they
			// finish
		}
		// XXX: wait until listener notifies this thread.
		// try {
		// while (true) {
		// //notify hook to service pending syncExecs before falling asleep
		// lockManager.aboutToWait(job.getThread());
		// try {
		// if (barrier.acquire(Long.MAX_VALUE))
		// break;
		// } catch (InterruptedException e) {
		// //loop and keep trying
		// }
		// }
		// } finally {
		// lockManager.aboutToRelease();
		// job.removeJobChangeListener(listener);
		// }
	}

}
