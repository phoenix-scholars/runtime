package ir.co.dpq.runtime.terminal;

import ir.co.dpq.runtime.IProgressMonitor;
import ir.co.dpq.runtime.jobs.Job;

/**
 * پیشرفت کار را در خط فرمان برای یک وظیفه ایجاد می‌کند.
 */
public class TerminaProgressMonitor implements IProgressMonitor {

	private volatile boolean cancelled;
	private String name;
	private int totalWork;
	private boolean done;
	private String subTask;
	private int worked;
	private Job job;
	private TerminaProgressProvider provider;

	public void beginTask(String name, int totalWork) {
		this.name = name;
		this.totalWork = totalWork;
		this.cancelled = false;
		this.done = false;
	}

	public void done() {
		this.done = true;
	}

	public void internalWorked(double work) {
		// TODO Auto-generated method stub
	}

	public boolean isCanceled() {
		return cancelled;
	}

	public void setCanceled(boolean value) {
		this.cancelled = value;
	}

	public void setTaskName(String name) {
		this.name = name;
	}

	public void subTask(String name) {
		this.subTask = name;
	}

	public void worked(int worked) {
		this.worked += worked;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getTotalWork() {
		return totalWork;
	}

	public void setTotalWork(int totalWork) {
		this.totalWork = totalWork;
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	public String getSubTask() {
		return subTask;
	}

	public void setSubTask(String subTask) {
		this.subTask = subTask;
	}

	public int getWorked() {
		return worked;
	}

	public void setWorked(int woked) {
		this.worked = woked;
	}

	public int getPersent() {
		if(totalWork == 0x0)
			return 0x0;
		return (int) (100f * worked / totalWork);
	}

	public Job getJob() {
		return job;
	}

	public void setJob(Job job) {
		this.job = job;
	}

	public TerminaProgressProvider getProvider() {
		return provider;
	}

	public void setProvider(TerminaProgressProvider provider) {
		this.provider = provider;
	}

	@Override
	public String toString() {
		StringBuilder bar = new StringBuilder();
		bar.append(this.getName() + "\n");
		bar.append(this.getSubTask() + "\n");

		int percent = getPersent();
		bar.append("[");
		for (int i = 0; i < 50; i++) {
			if (isDone()) {
				bar.append("=");
			} else if (i < (percent / 2)) {
				bar.append("=");
			} else if (i == (percent / 2)) {
				bar.append(">");
			} else {
				bar.append(" ");
			}
		}
		if (isDone()) {
			bar.append("]   DONE     ");
		} else {
			bar.append("]   " + percent + "%     ");
		}
		return bar.toString();
	}
}
