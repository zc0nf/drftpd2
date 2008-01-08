/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.plugins.archive.archivetypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.apache.log4j.Logger;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.DuplicateArchiveException;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.sections.SectionInterface;

/**
 * @author zubov
 * @version $Id: ArchiveHandler.java 1787 2007-09-19 10:22:58Z zubov $
 */
public class ArchiveHandler extends Thread {
	protected final static Logger logger = Logger
			.getLogger(ArchiveHandler.class);

	private ArchiveType _archiveType;
	
	private ArrayList<Job> _jobs = null;

	public ArchiveHandler(ArchiveType archiveType) {
		super(archiveType.getClass().getName() + " archiving "
				+ archiveType.getSection().getName());
		_archiveType = archiveType;
	}

	public ArchiveType getArchiveType() {
		return _archiveType;
	}

	public SectionInterface getSection() {
		return _archiveType.getSection();
	}
	
	public ArrayList<Job> getJobs() {
		if (_jobs == null) {
			return (ArrayList<Job>) Collections.EMPTY_LIST;
		}
		return new ArrayList<Job>(_jobs);
	}

	public void run() {
		try {
			synchronized (_archiveType._parent) {
				if (_archiveType.getDirectory() == null) {
					_archiveType.setDirectory(_archiveType
							.getOldestNonArchivedDir());
				}

				if (_archiveType.getDirectory() == null) {
					return; // all done
				}
				try {
					_archiveType._parent.addArchiveHandler(this);
				} catch (DuplicateArchiveException e) {
					logger.warn("Directory -- " + _archiveType.getDirectory()
							+ " -- is already being archived ");
					return;
				}
			}

			if (_archiveType.getRSlaves() == null) {
				Set<RemoteSlave> destSlaves = _archiveType
						.findDestinationSlaves();

				if (destSlaves == null) {
					_archiveType.setDirectory(null);
					return; // no available slaves to use
				}

				_archiveType
						.setRSlaves(Collections.unmodifiableSet(destSlaves));
			}

			_jobs = _archiveType.send();
			_archiveType.waitForSendOfFiles(_jobs);
			logger.info("Done archiving "
					+ getArchiveType().getDirectory().getPath());
		} catch (Exception e) {
			logger.warn("", e);
		} finally {
			_archiveType._parent.removeArchiveHandler(this);
		}
	}
}