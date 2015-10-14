package ir.co.dpq.runtime;

public class MultiStatus implements IStatus{

	public MultiStatus() {
		// TODO Auto-generated constructor stub
	}

	public MultiStatus(String piJobs, int pluginError, String msg, IllegalStateException illegalStateException) {
		// TODO Auto-generated constructor stub
	}

	public MultiStatus(String piJobs, int pluginError, String string, Throwable e) {
		// TODO Auto-generated constructor stub
	}

	public void add(Status child) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IStatus[] getChildren() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getCode() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Throwable getException() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getMessage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getPlugin() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getSeverity() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isMultiStatus() {
		return true;
	}

	@Override
	public boolean isOK() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean matches(int severityMask) {
		// TODO Auto-generated method stub
		return false;
	}

	public void merge(Object status) {
		// TODO Auto-generated method stub
		
	}

}
