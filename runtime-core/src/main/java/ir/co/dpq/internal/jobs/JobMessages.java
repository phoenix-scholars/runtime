/**********************************************************************
 * Copyright (c) 2005, 2012 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package ir.co.dpq.internal.jobs;

import java.util.Date;
//import org.eclipse.osgi.util.NLS;
import java.util.ResourceBundle;

/**
 * Job plugin message catalog
 */
public class JobMessages
// extends NLS
{
	// private static final String BUNDLE_NAME =
	// "ir.co.dpq.internal.jobs.messages"; //$NON-NLS-1$

	// Job Manager and Locks
	public static String jobs_blocked0;
	public static String jobs_blocked1;
	public static String jobs_internalError;
	public static String jobs_waitFamSub;
	public static String jobs_waitFamSubOne;
	// metadata
	public static String meta_pluginProblems;

	static {
		// load message values from bundle file
		reloadMessages();
	}

	public static void reloadMessages() {
		// NLS.initializeMessages(BUNDLE_NAME, JobMessages.class);
		ResourceBundle resource = ResourceBundle.getBundle(JobMessages.class.getName());

		jobs_blocked0 = resource.getString("jobs_blocked0");
		jobs_blocked1 = resource.getString("jobs_blocked1");
		jobs_internalError = resource.getString("jobs_internalError");
		jobs_waitFamSub = resource.getString("jobs_waitFamSub");
		jobs_waitFamSubOne = resource.getString("jobs_waitFamSubOne");
		meta_pluginProblems = resource.getString("meta_pluginProblems");
	}

	/**
	 * Print a debug message to the console. Pre-pend the message with the
	 * current date and the name of the current thread.
	 */
	public static void message(String message) {
		StringBuffer buffer = new StringBuffer();
		buffer.append(new Date(System.currentTimeMillis()));
		buffer.append(" - ["); //$NON-NLS-1$
		buffer.append(Thread.currentThread().getName());
		buffer.append("] "); //$NON-NLS-1$
		buffer.append(message);
	}
}