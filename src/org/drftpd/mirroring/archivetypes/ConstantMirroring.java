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
package org.drftpd.mirroring.archivetypes;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.mirroring.JobManager;

import org.apache.log4j.Logger;

import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.plugins.Archive;

import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;


/**
 * @author zubov
 */
public class ConstantMirroring extends ArchiveType {
    private static final Logger logger = Logger.getLogger(ConstantMirroring.class);
    private long _slaveDeadAfter;

    public ConstantMirroring(Archive archive, SectionInterface section,
        Properties p) {
        super(archive, section, p);
        _slaveDeadAfter = 1000 * 60 * Integer.parseInt(p.getProperty(
                section.getName() + ".slaveDeadAfter", "0"));

        if (_numOfSlaves < 2) {
			throw new IllegalArgumentException(
					"numOfSlaves has to be > 1 for section "
							+ section.getName());
		}
        if (_slaveList.isEmpty()) {
			_slaveList = null;
		} else if (_numOfSlaves > _slaveList.size()) {
			throw new IllegalArgumentException(
					"numOfSlaves has to be <= the size of the destination slave list for section "
							+ section.getName());
		}
    }

    public void cleanup(ArrayList jobList) {
        recursiveCleanup(getDirectory());
    }

    private void recursiveCleanup(LinkedRemoteFileInterface lrf) {
        for (Iterator iter = new ArrayList(lrf.getFiles()).iterator();
                iter.hasNext();) {
            LinkedRemoteFileInterface src = (LinkedRemoteFileInterface) iter.next();

            if (src.isLink()) {
                continue;
            }

            if (src.isFile()) {
                Collection slaves = new ArrayList(src.getSlaves());

                if (slaves.isEmpty()) {
                    // couldn't mirror file, it's deleted
                    src.delete();

                    continue;
                }

                Iterator offlineSlaveIter = slaves.iterator();

                while ((slaves.size() > _numOfSlaves) &&
                        offlineSlaveIter.hasNext()) { // remove OFFline slaves until size is okay

                    RemoteSlave slave = (RemoteSlave) offlineSlaveIter.next();

                    if (!slave.isAvailable()) {
                        src.removeSlave(slave);
                        slave.simpleDelete(src.getPath());
                    }

                    offlineSlaveIter.remove();
                }

                slaves = new ArrayList(src.getSlaves());

                Iterator onlineSlaveIter = slaves.iterator();

                while ((slaves.size() > _numOfSlaves) &&
                        onlineSlaveIter.hasNext()) { // remove ONline slaves until size is okay

                    RemoteSlave slave = (RemoteSlave) onlineSlaveIter.next();
                    slave.simpleDelete(src.getPath());
                    src.removeSlave(slave);
                    onlineSlaveIter.remove();
                }
            } else { // src.isDirectory()
                recursiveCleanup(src);
            }
        }
    }

    public HashSet<RemoteSlave> findDestinationSlaves() {
        return new HashSet<RemoteSlave>(_parent.getGlobalContext()
                                  .getSlaveManager().getSlaves());
    }

    protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
        throws IncompleteDirectoryException, OfflineSlaveException {
        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface src = (LinkedRemoteFileInterface) iter.next();

            if (src.isLink()) {
                continue;
            }

            if (src.isFile()) {
                Collection<RemoteSlave> slaves;

                slaves = src.getSlaves();
                for (Iterator<RemoteSlave> slaveIter = slaves.iterator(); slaveIter.hasNext();) {
                	RemoteSlave rslave = slaveIter.next();
                	if (!rslave.isAvailable()) {
                		long offlineTime = System.currentTimeMillis() - rslave.getLastTimeOnline();
                		if (offlineTime > _slaveDeadAfter) {
                			// slave is considered dead
                			slaveIter.remove();
                		}
                	}
                }

                if (slaves.size() != _numOfSlaves) {
                    return false;
                }
            } else if (src.isDirectory()) {
                return isArchivedDir(src);
            }
        }

        return true;
    }


    private ArrayList recursiveSend(LinkedRemoteFileInterface lrf) {
        ArrayList jobQueue = new ArrayList();
        JobManager jm = _parent.getGlobalContext().getJobManager();

        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface src = (LinkedRemoteFileInterface) iter.next();

            if (src.isFile()) {

                Job job = new Job(src, getRSlaves(), 3, _numOfSlaves, true);
                logger.info("Adding " + job + " to the job queue");
                jobQueue.add(job);
            } else {
                jobQueue.addAll(recursiveSend(src));
            }
        }

        return jobQueue;
    }

    public String toString() {
        return "ConstantMirroring=[directory=[" + getDirectory().getPath() +
        "]dest=[" + outputSlaves(getRSlaves()) + "]numOfSlaves=[" +
        _numOfSlaves + "]]";
    }
}
