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

/**
 * Scheduling rules are used by jobs to indicate when they need exclusive access
 * to a resource.  Scheduling rules can also be applied synchronously to a thread
 * using <tt>IJobManager.beginRule(ISchedulingRule)</tt> and 
 * <tt>IJobManager.endRule(ISchedulingRule)</tt>.  The job manager guarantees that 
 * no two jobs with conflicting scheduling rules will run concurrently. 
 * Multiple rules can be applied to a given thread only if the outer rule explicitly 
 * allows the nesting as specified by the <code>contains</code> method.  
 * <p>
 * Clients may implement this interface.
 * 
 * @see Job#getRule()
 * @see Job#setRule(ISchedulingRule)
 * @see Job#schedule(long)
 * @see IJobManager#beginRule(ISchedulingRule, org.eclipse.core.runtime.IProgressMonitor)
 * @see IJobManager#endRule(ISchedulingRule)
 * @since 3.0
 */
public interface ISchedulingRule {
	/**
	 * Returns whether this scheduling rule completely contains another scheduling
	 * rule.  Rules can only be nested within a thread if the inner rule is completely 
	 * contained within the outer rule.
	 * <p>
	 * Implementations of this method must obey the rules of a partial order relation
	 * on the set of all scheduling rules.  In particular, implementations must be reflexive
	 * (a.contains(a) is always true), antisymmetric (a.contains(b) and b.contains(a) iff a.equals(b), 
	 * and transitive (if a.contains(b) and b.contains(c), then a.contains(c)).  Implementations
	 * of this method must return <code>false</code> when compared to a rule they
	 * know nothing about.
	 * 
	 * @param rule the rule to check for containment
	 * @return <code>true</code> if this rule contains the given rule, and 
	 * <code>false</code> otherwise.
	 */
	public boolean contains(ISchedulingRule rule);

	/**
	 * Returns whether this scheduling rule is compatible with another scheduling rule.
	 * If <code>true</code> is returned, then no job with this rule will be run at the 
	 * same time as a job with the conflicting rule.  If <code>false</code> is returned, 
	 * then the job manager is free to run jobs with these rules at the same time.
	 * <p>
	 * Implementations of this method must be reflexive, symmetric, and consistent,
	 * and must return <code>false</code> when compared  to a rule they know 
	 * nothing about.
	 * <p>
	 * This method must return true if calling {@link #contains(ISchedulingRule)} on
	 * the same rule also returns true. This is required because it would otherwise
	 * allow two threads to be running concurrently with the same rule.
	 *
	 * @param rule the rule to check for conflicts
	 * @return <code>true</code> if the rule is conflicting, and <code>false</code>
	 * 	otherwise.
	 */
	public boolean isConflicting(ISchedulingRule rule);
}
