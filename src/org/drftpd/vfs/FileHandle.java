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
package org.drftpd.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;

import org.drftpd.master.RemoteSlave;

/**
 * @author zubov
 * @version $Id$
 */
public class FileHandle extends InodeHandle implements FileHandleInterface {

	public FileHandle(String path) {
		super(path);
	}

	@Override
	protected VirtualFileSystemFile getInode() throws FileNotFoundException {
		VirtualFileSystemInode inode = super.getInode();
		if (inode instanceof VirtualFileSystemFile) {
			return (VirtualFileSystemFile) inode;
		}
		throw new ClassCastException("FileHandle object pointing to Inode:"
				+ inode);
	}

	public Set<RemoteSlave> getSlaves() throws FileNotFoundException {
		HashSet<RemoteSlave> slaves = new HashSet<RemoteSlave>();
		for (String slave : getInode().getSlaves()) {
			try {
				slaves.add(getGlobalContext().getSlaveManager().getRemoteSlave(
						slave));
			} catch (ObjectNotFoundException e) {
				getInode().removeSlave(slave);
			}
		}
		return slaves;
	}

	public Collection<RemoteSlave> getAvailableSlaves()
			throws NoAvailableSlaveException, FileNotFoundException {
		HashSet<RemoteSlave> rslaves = new HashSet<RemoteSlave>();
		for (RemoteSlave rslave : getSlaves()) {
			if (rslave.isAvailable()) {
				rslaves.add(rslave);
			}
		}
		if (rslaves.isEmpty()) {
			throw new NoAvailableSlaveException();
		}
		return rslaves;
	}

	public void setCheckSum(long checksum) throws FileNotFoundException {
		getInode().setChecksum(checksum);
	}

	public void removeSlave(RemoteSlave sourceSlave)
			throws FileNotFoundException {
		getInode().removeSlave(sourceSlave.getName());
	}

	RemoteSlave getASlaveForFunction() throws FileNotFoundException,
			NoAvailableSlaveException {
		for (RemoteSlave rslave : getAvailableSlaves()) {
			return rslave;
		}
		throw new NoAvailableSlaveException("No slaves are online for file "
				+ this);
	}

	public long getCheckSum() throws NoAvailableSlaveException,
			FileNotFoundException {
		long checksum = getInode().getChecksum();
		if (checksum == 0L) {
			while (true) {
				RemoteSlave rslave = getASlaveForFunction();
				try {
					checksum = rslave.getCheckSumForPath(getPath());
					getInode().setChecksum(checksum);
					return checksum;
				} catch (IOException e) {
					rslave.setOffline(e);
				} catch (SlaveUnavailableException e) {
					continue;
				}
			}
		}
		return checksum;
	}

	public void addSlave(RemoteSlave destinationSlave)
			throws FileNotFoundException {
		getInode().addSlave(destinationSlave.getName());
	}

	public long getXfertime() throws FileNotFoundException {
		return getInode().getXfertime();
	}

	public boolean isAvailable() throws FileNotFoundException {
		try {
			return !getAvailableSlaves().isEmpty();
		} catch (NoAvailableSlaveException e) {
			return false;
		}
	}

	public void setSize(long size) throws FileNotFoundException {
		getInode().setSize(size);
	}

	@Override
	public boolean isDirectory() {
		return false;
	}

	@Override
	public boolean isFile() {
		return true;
	}

	@Override
	public boolean isLink() {
		return false;
	}

}
