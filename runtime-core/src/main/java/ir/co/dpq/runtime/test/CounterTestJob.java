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
package ir.co.dpq.runtime.test;

import ir.co.dpq.runtime.*;
import ir.co.dpq.runtime.jobs.*;

public class CounterTestJob extends Job {
	
	private int count;
	private int delay;
	private String name;
	private String subTask;

	public CounterTestJob(){
		this(100,1);
	}

	public CounterTestJob(int count, int delay) {
		this.count = count;
		this.delay = delay;
		this.name = "Counting with dealy";
		this.subTask = "Counting in round ";
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		monitor.beginTask(name, count);
		for (int index = 0x0; index < count; index++) {
			monitor.subTask(subTask + index);
			monitor.worked(1);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			try {
				Thread.sleep(1000 * delay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		monitor.done();
		return Status.OK_STATUS;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public int getDelay() {
		return delay;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	public String getSubTask() {
		return subTask;
	}

	public void setSubTask(String subTask) {
		this.subTask = subTask;
	}

}
