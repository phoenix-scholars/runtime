package ir.co.dpq.runtime.example.test;
import ir.co.dpq.runtime.jobs.JobManager;
import ir.co.dpq.runtime.terminal.TerminaProgressProvider;

public class Main {
	public static void main(String[] argv) throws Exception {
		JobManager.getInstance().setProgressProvider(
				new TerminaProgressProvider());
		CounterTestJob job1 = new CounterTestJob(50, 1);
		job1.schedule();
		CounterTestJob job = new CounterTestJob();
		job.schedule();
		job.addLast(job);
		job.join();
	}

}