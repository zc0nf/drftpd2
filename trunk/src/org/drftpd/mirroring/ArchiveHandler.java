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
package org.drftpd.mirroring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import net.sf.drftpd.event.listeners.Archive;

import org.apache.log4j.Logger;
import org.drftpd.sections.SectionInterface;


/*
 * @author zubov
 * @version $Id: ArchiveHandler.java,v 1.10 2004/11/02 07:32:50 zubov Exp $
 */
public class ArchiveHandler extends Thread {
    protected final static Logger logger = Archive.getLogger();
    private ArchiveType _archiveType;

    public ArchiveHandler(ArchiveType archiveType) {
        _archiveType = archiveType;
        setName(_archiveType.getClass().getName() + " archiving " +
            _archiveType.getSection().getName());
    }

    public ArchiveType getArchiveType() {
        return _archiveType;
    }

    public SectionInterface getSection() {
        return _archiveType.getSection();
    }

    public void run() {
        try {
            synchronized (_archiveType._parent) {
                if (_archiveType.getDirectory() == null) {
                    _archiveType.setDirectory(_archiveType.getOldestNonArchivedDir());
                }

                if (_archiveType.getDirectory() == null) {
                    return; // all done
                }

                _archiveType._parent.addArchiveHandler(this);
            }

            if (_archiveType.getRSlaves() == null) {
                Set destSlaves = _archiveType.findDestinationSlaves();

                if (destSlaves == null) {
                    _archiveType.setDirectory(null);

                    return; // no available slaves to use
                }

                _archiveType.setRSlaves(Collections.unmodifiableSet(destSlaves));
            }

            ArrayList jobs = _archiveType.send();
            _archiveType.waitForSendOfFiles(new ArrayList(jobs));
            _archiveType.cleanup(jobs);
            logger.info("Done archiving " +
                getArchiveType().getDirectory().getPath());
        } catch (Exception e) {
            logger.debug("", e);
        }

        Archive archive = _archiveType._parent;

        if (!archive.removeArchiveHandler(this)) {
            logger.debug(
                "This is a serious bug, unable to remove the ArchiveHandler!");
        }
    }
}
