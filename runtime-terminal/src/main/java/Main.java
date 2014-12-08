import ir.co.dpq.runtime.jobs.JobManager;
import ir.co.dpq.runtime.terminal.TerminaProgressProvider;
import ir.co.dpq.runtime.test.CounterTestJob;

public class Main {
	public static void main(String[] argv) throws Exception {
		JobManager.getInstance().setProgressProvider(
				new TerminaProgressProvider());
		CounterTestJob job1 = new CounterTestJob(50, 1);
		job1.schedule();
		CounterTestJob job = new CounterTestJob();
		job.schedule();
		job.join();
	}

}