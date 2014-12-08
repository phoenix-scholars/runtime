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

/**
 * The progress provider supplies the job manager with progress monitors for
 * running jobs.  There can only be one progress provider at any given time.
 * <p>
 * This class is intended for use by the currently executing Eclipse application.
 * Plug-ins outside the currently running application should not reference or 
 * subclass this class.
 * </p>
 * 
 * @see IJobManager#setProgressProvider(ProgressProvider)
 * @since 3.0
 */
public abstract class ProgressProvider {
	
	/**
	 * Provides a new progress monitor instance to be used by the given job.
	 * This method is called prior to running any job that does not belong to a
	 * progress group. The returned monitor will be supplied to the job's
	 * <code>run</code> method.
	 * 
	 * @see #createProgressGroup()
	 * @see Job#setProgressGroup(IProgressMonitor, int)
	 * @param job the job to create a progress monitor for
	 * @return a progress monitor, or <code>null</code> if no progress monitoring 
	 * is needed.
	 */
	public abstract IProgressMonitor createMonitor(Job job);

	/**
	 * Returns a progress monitor that can be used to provide
	 * aggregated progress feedback on a set of running jobs.
	 * This method implements <code>IJobManager.createProgressGroup</code>,
	 * and must obey all rules specified in that contract.
	 * <p>
	 * This default implementation returns a new
	 * <code>NullProgressMonitor</code>  Subclasses may override.
	 * 
	 * @see IJobManager#createProgressGroup()
	 * @return a progress monitor
	 */
	public IProgressMonitor createProgressGroup() {
		return new NullProgressMonitor();
	}

	/**
	 * Returns a progress monitor that can be used by a running job
	 * to report progress in the context of a progress group. This method
	 * implements <code>Job.setProgressGroup</code>.  One of the
	 * two <code>createMonitor</code> methods will be invoked
	 * prior to each execution of a job, depending on whether a progress
	 * group was specified for the job.
	 * <p>
	 * The provided monitor must be a monitor returned by the method
	 * <code>createProgressGroup</code>.  This method is responsible
	 * for asserting this and throwing an appropriate runtime exception
	 * if an invalid monitor is provided.
	 * <p>
	 * This default implementation returns a new
	 * <code>SubProgressMonitor</code>.  Subclasses may override.
	 * 
	 * @see IJobManager#createProgressGroup()
	 * @see Job#setProgressGroup(IProgressMonitor, int)
	 * @param job the job to create a progress monitor for
	 * @param group the progress monitor group that this job belongs to
	 * @param ticks the number of ticks of work for the progress monitor
	 * @return a progress monitor, or <code>null</code> if no progress monitoring 
	 * is needed.
	 */
	public IProgressMonitor createMonitor(Job job, IProgressMonitor group, int ticks) {
		return new SubProgressMonitor(group, ticks);
	}

	/**
	 * Returns a progress monitor to use when none has been provided
	 * by the client running the job.  
	 * <p>
	 * This default implementation returns a new
	 * <code>NullProgressMonitor</code>  Subclasses may override.
	 * 
	 * @return a progress monitor
	 */
	public IProgressMonitor getDefaultMonitor() {
		return new NullProgressMonitor();
	}
}