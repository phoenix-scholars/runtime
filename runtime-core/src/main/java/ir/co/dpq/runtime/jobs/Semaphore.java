package ir.co.dpq.runtime.jobs;

public class Semaphore {
	protected long notifications;
	protected Runnable runnable;

	public Semaphore(Runnable runnable) {
		this.runnable = runnable;
		notifications = 0;
	}

	/**
	 * Attempts to acquire this semaphore. Returns true if it was successfully
	 * acquired, and false otherwise.
	 */
	public synchronized boolean acquire(long delay) throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException();
		long start = System.currentTimeMillis();
		long timeLeft = delay;
		while (true) {
			if (notifications > 0) {
				notifications--;
				return true;
			}
			if (timeLeft <= 0)
				return false;
			wait(timeLeft);
			timeLeft = start + delay - System.currentTimeMillis();
		}
	}

	/**
	 * Attempt to acquire the semaphore without waiting. Returns true if
	 * successfully acquired, false otherwise.
	 */
	public synchronized boolean attempt() {
		if (notifications > 0) {
			notifications--;
			return true;
		}
		return false;
	}

	public boolean equals(Object obj) {
		return (runnable == ((Semaphore) obj).runnable);
	}

	public int hashCode() {
		return runnable == null ? 0 : runnable.hashCode();
	}

	public synchronized void release() {
		notifications++;
		notifyAll();
	}

	// for debug only
	public String toString() {
		return "Semaphore(" + runnable + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}