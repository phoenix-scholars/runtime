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
package ir.co.dpq.runtime;

public interface IProgressMonitor {

	/**
	 * Constant indicating an unknown amount of work.
	 */
	static final int UNKNOWN = 0x0;

	/**
	 * Notifies that the main task is beginning.
	 * 
	 * Notifies that the main task is beginning. This must only be called once
	 * on a given progress monitor instance.
	 * 
	 * @param name
	 *            the name (or description) of the main task
	 * @param totalWork
	 *            the total number of work units into which the main task is
	 *            been subdivided. If the value is UNKNOWN the implementation is
	 *            free to indicate progress in a way which doesn't require the
	 *            total number of work units in advance.
	 */
	void beginTask(String name, int totalWork);

	/**
	 * Notifies that the work is done; that is, either the main task is
	 * completed or the user canceled it.
	 * 
	 * Notifies that the work is done; that is, either the main task is
	 * completed or the user canceled it. This method may be called more than
	 * once (implementations should be prepared to handle this case).
	 */
	void done();

	/**
	 * Internal method to handle scaling correctly. This method must not be
	 * called by a client. Clients should always use the method
	 * </code>worked(int)</code>.
	 * 
	 * @param work
	 *            the amount of work done
	 */
	void internalWorked(double work);

	/**
	 * Returns whether cancelation of current operation has been requested.
	 * 
	 * Returns whether cancelation of current operation has been requested.
	 * Long-running operations should poll to see if cancelation has been
	 * requested.
	 * 
	 * @return true if cancellation has been requested, and false otherwise
	 */
	boolean isCanceled();

	/**
	 * Sets the cancel state to the given value.
	 * 
	 * @param value
	 *            true indicates that cancelation has been requested (but not
	 *            necessarily acknowledged); false clears this flag
	 */
	void setCanceled(boolean value);

	/**
	 * Sets the task name to the given value.
	 * 
	 * Sets the task name to the given value. This method is used to restore the
	 * task label after a nested operation was executed. Normally there is no
	 * need for clients to call this method.
	 * 
	 * 
	 * @param name
	 *            the name (or description) of the main task
	 */
	void setTaskName(String name);

	/**
	 * Notifies that a subtask of the main task is beginning.
	 * 
	 * Notifies that a subtask of the main task is beginning. Subtasks are
	 * optional; the main task might not have subtasks.
	 * 
	 * @param name
	 *            the name (or description) of the subtask
	 */
	void subTask(String name);

	/**
	 * Notifies that a given number of work unit of the main task has been
	 * completed.
	 * 
	 * Notifies that a given number of work unit of the main task has been
	 * completed. Note that this amount represents an installment, as opposed to
	 * a cumulative amount of work done to date.
	 * 
	 * @param work
	 *            a non-negative number of work units just completed
	 */
	void worked(int work);

}
