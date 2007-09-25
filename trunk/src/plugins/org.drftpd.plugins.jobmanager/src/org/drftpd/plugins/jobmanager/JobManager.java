/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.jobmanager;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.bushe.swing.event.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.event.ReloadEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;

/**
 * @author zubov
 * @version $Id: JobManager.java 1787 2007-09-19 10:22:58Z zubov $
 */
public class JobManager implements PluginInterface, EventSubscriber {
	private static final Logger logger = Logger.getLogger(JobManager.class);

	private boolean _isStopped = false;

	private Set<Job> _queuedJobSet;

	private boolean _useCRC;
	
	private boolean _useSSL;

	private long _sleepSeconds;

	private TimerTask _runJob = null;

	/**
	 * Keeps track of all jobs and controls them
	 */
	public JobManager() {

	}

	public synchronized void addJobsToQueue(Collection<Job> jobs) {
		ArrayList<Job> jobs2 = new ArrayList<Job>(jobs);
		for (Iterator jobiter = jobs2.iterator(); jobiter.hasNext();) {
			Job job = (Job) jobiter.next();
			Collection<RemoteSlave> slaves;
			try {
				slaves = job.getFile().getSlaves();
			} catch (FileNotFoundException e) {
				job.abort();
				jobiter.remove();
				continue;
			}

			for (Iterator<RemoteSlave> iter = slaves.iterator(); iter.hasNext();) {
				RemoteSlave slave = iter.next();

				if (job.getDestinationSlaves().contains(slave.getName())) {
					try {
						job.sentToSlave(slave);
					} catch (FileNotFoundException e) {
						// I'd like to simply remove it, but I'm not sure how to handle that
						// the job may be isDone() and the code below will throw an error if true
						// this is going to be a small race condition since above we check if file exists
						// bug I'm willing to accept --zubov
					}
				}
			}
			if (job.isDone()) {
				jobiter.remove();
			}
		}
		_queuedJobSet.addAll(jobs2);
	}

	public synchronized void addJobToQueue(Job job) {
		addJobsToQueue(Collections.singletonList(job));
	}

	/**
	 * Gets all jobs.
	 */
	public synchronized Set<Job> getAllJobsFromQueue() {
		return Collections.unmodifiableSet(_queuedJobSet);
	}

	public synchronized Job getNextJob(Set<RemoteSlave> busySlaves, Set skipJobs) {
		for (Iterator iter = _queuedJobSet.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();

			if (tempJob.isDone()) {
				iter.remove();

				continue;
			}

			if (tempJob.isTransferring()) {
				continue;
			}

			if (skipJobs.contains(tempJob)) {
				continue;
			}

			Collection availableSlaves = null;

			try {
				availableSlaves = tempJob.getFile().getAvailableSlaves();
			} catch (NoAvailableSlaveException e) {
				continue; // can't transfer what isn't online
			} catch (FileNotFoundException e) {
				tempJob.abort();
				iter.remove();
				continue;
			}

			if (!busySlaves.containsAll(availableSlaves)) {
				return tempJob;
			}
		}

		return null;
	}

	public boolean isStopped() {
		return _isStopped;
	}

	public void processJob() {
		Job job = null;
		RemoteSlave sourceSlave = null;
		RemoteSlave destSlave = null;

		Collection<RemoteSlave> availableSlaves;
		try {
			availableSlaves = getGlobalContext().getSlaveManager()
					.getAvailableSlaves();
		} catch (NoAvailableSlaveException e1) {
			return; // can't transfer with no slaves
		}

		Set<RemoteSlave> busySlavesDown = new HashSet<RemoteSlave>();
		Set<Job> skipJobs = new HashSet<Job>();

		synchronized (this) {
			while (!busySlavesDown.containsAll(availableSlaves)) {
				job = getNextJob(busySlavesDown, skipJobs);
				if (job == null) {
					return;
				}
				Collection<RemoteSlave> destinationSlaveObjects = null;
				try {
					destinationSlaveObjects = job.getSlaveObjects(job.getDestinationSlaves());
				} catch (ObjectNotFoundException e2) {
					logger.debug("Slave no longer exists!", e2);
					job.abort();
					continue;
				}

				try {
					sourceSlave = getGlobalContext().getSlaveSelectionManager()
							.getASlaveForJobDownload(job.getFile(),job.getSlaveObjects(
									job.getSlavesToTransferTo()));
				} catch (NoAvailableSlaveException e) {
					try {
						busySlavesDown.addAll(job.getFile().getSlaves());
					} catch (FileNotFoundException e1) {
						// can't transfer
						return;
					}
					continue;
				} catch (FileNotFoundException e) {
					job.abort();
					// can't transfer
					return;
				} catch (ObjectNotFoundException e) {
					job.abort();
					// can't transfer
					return;
				}

				if (sourceSlave == null) {
					logger.debug("Unable to find a suitable job for transfer");
					return;
				}
				try {
					availableSlaves.removeAll(job.getFile().getSlaves());
					destSlave = getGlobalContext().getSlaveSelectionManager()
							.getASlaveForJobUpload(job.getFile(),
									destinationSlaveObjects, sourceSlave);
					
					break; // we have a source slave and a destination
					// slave,

					// transfer!
				} catch (NoAvailableSlaveException e) {
					// job was ready to be sent, but it had no slave that was
					// ready to accept it
					skipJobs.add(job);

					continue;
				} catch (FileNotFoundException e) {
					// can't transfer
					return;
				}
			}
			// sourceSlave will always be null if destSlave is null
			if (destSlave == null /* || sourceSlave == null */) {
				// all slaves are offline or busy
				return;
			}
		}

		// file is not deleted and is available, we are ready to process

		try {
			job.transfer(useCRC(), useSecureTransfers(), sourceSlave, destSlave);
		} catch (FileNotFoundException e) {
			job.abort();
			// file is deleted, hah! stupid race conditions
			return;
		}
		if (job.isDone()) {
			logger.debug("Job is finished, removing job " + job.getFile());
			removeJobFromQueue(job);
		}
	}

	private GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public void reload() {
		Properties p = GlobalContext.getGlobalContext().getPluginsConfig()
				.getPropertiesForPlugin("jobmanager.conf");
		_useCRC = p.getProperty("useCRC", "true").equals("true");
		_useSSL = p.getProperty("useSSLTransfers", "true").equals("true"); 
		_sleepSeconds = 1000 * Long.parseLong(PropertyHelper.getProperty(p,
				"sleepSeconds", "30"));
		if (_runJob != null) {
			_runJob.cancel();
			getGlobalContext().getTimer().purge();
		}
		_runJob = new TimerTask() {
			public void run() {
				if (_isStopped) {
					return;
				}
				new JobTransferThread(getJobManager()).start();
			}
		};
		getGlobalContext().getTimer().schedule(_runJob, 0, _sleepSeconds);
	}

	public synchronized void removeJobFromQueue(Job job) {
		_queuedJobSet.remove(job);
	}

	public void startJobs() {
		_isStopped = false;
	}

	private JobManager getJobManager() {
		return this;
	}

	public void stopJob(Job job) {
		job.abort();
		removeJobFromQueue(job);
	}

	public void stopJobs() {
		_isStopped = true;
	}

	private boolean useCRC() {
		return _useCRC;
	}
	
	private boolean useSecureTransfers() {
		return _useSSL;
	}

	public void startPlugin() {
		GlobalContext.getEventService().subscribe(ReloadEvent.class,this);
		logger.info("JobManager plugin loaded successfully");
		_queuedJobSet = new TreeSet<Job>(new JobComparator());
		reload();
	}

	public void stopPlugin(String reason) {
		if (_runJob != null) {
			_runJob.cancel();
			getGlobalContext().getTimer().purge();
		}
		if (_queuedJobSet != null) {
			synchronized (this) {
				for (Job job : _queuedJobSet) {
					job.abort();
				}
				_queuedJobSet.clear();
			}
		}
		GlobalContext.getEventService().unsubscribe(ReloadEvent.class, this);
		logger.info("JobManager plugin unloaded successfully");
	}

	public void onEvent(Object event) {
		if (event instanceof ReloadEvent) {
			reload();
		}
	}
}
