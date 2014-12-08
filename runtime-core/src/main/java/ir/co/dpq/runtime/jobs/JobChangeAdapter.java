package ir.co.dpq.runtime.jobs;

/**
 * This adapter class provides default implementations for the methods described
 * by the <code>IJobChangeListener</code> interface.
 * <p>
 * Classes that wish to listen to the progress of scheduled jobs can extend this
 * class and override only the methods which they are interested in.
 * </p>
 * 
 * @see IJobChangeListener
 * @since 3.0
 */
public class JobChangeAdapter implements IJobChangeListener {
	/*
	 * (non-Javadoc)
	 * 
	 * @see IJobChangeListener#aboutToRun(IJobChangeEvent) This default
	 * implementation does nothing
	 */
	public void aboutToRun(IJobChangeEvent event) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IJobChangeListener#awake(IJobChangeEvent) This default
	 * implementation does nothing
	 */
	public void awake(IJobChangeEvent event) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IJobChangeListener#done(IJobChangeEvent) This default implementation
	 * does nothing
	 */
	public void done(IJobChangeEvent event) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IJobChangeListener#running(IJobChangeEvent) This default
	 * implementation does nothing
	 */
	public void running(IJobChangeEvent event) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IJobChangeListener#scheduled(IJobChangeEvent) This default
	 * implementation does nothing
	 */
	public void scheduled(IJobChangeEvent event) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IJobChangeListener#sleeping(IJobChangeEvent) This default
	 * implementation does nothing
	 */
	public void sleeping(IJobChangeEvent event) {
		// do nothing
	}
}
