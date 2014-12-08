package ir.co.dpq.runtime.terminal;

import static org.fusesource.jansi.Ansi.ansi;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import ir.co.dpq.runtime.IProgressMonitor;
import ir.co.dpq.runtime.jobs.Job;
import ir.co.dpq.runtime.jobs.ProgressProvider;

import org.fusesource.jansi.Ansi;

public class TerminaProgressProvider extends ProgressProvider {

	Hashtable<Job, TerminaProgressMonitor> progresses;

	public TerminaProgressProvider() {
		progresses = new Hashtable<Job, TerminaProgressMonitor>();
		new Thread() {
			public void run() {
				while (true) {
					try {
						TerminaProgressProvider.this.printProgBar();
						Thread.sleep(1000);
					} catch (Exception ex) {
					}
				}
			};
		}.start();
	}

	protected void printProgBar() {
		StringBuilder bars = new StringBuilder();
		Enumeration<Job> keys = progresses.keys();
		ArrayList<Job> done = new ArrayList<Job>();
		while (keys.hasMoreElements()) {
			Job key = keys.nextElement();
			TerminaProgressMonitor progress = progresses.get(key);
			if (progress.isDone() || progress.isCanceled()) {
				done.add(key);
			} else {
				String ptext = progress.toString();
				bars.append("\n" + ptext);
			}
		}

		for (Job job : done) {
			progresses.remove(job);
		}

		Ansi a = ansi();
		a = a.eraseScreen();
		a = a.a(bars.toString());
		System.out.println(a.reset());
	}

	@Override
	public IProgressMonitor createMonitor(Job job) {
		TerminaProgressMonitor m = new TerminaProgressMonitor();
		m.setJob(job);
		this.progresses.put(job, m);
		return m;
	}
}
