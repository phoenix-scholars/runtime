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

import java.util.LinkedList;
import java.util.Queue;

public class JobQueue extends LinkedList<Job> implements Queue<Job> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4433759896875842628L;


	public JobQueue() {
		super();
	}
	
	public JobQueue(boolean b) {
		super();
	}

	public JobQueue(boolean b, boolean c) {
		super();
	}

	/**
	 * Add job to the end of queue
	 * @param job
	 */
	public void enqueue(Job job) {
		this.addLast(job);
	}
}
