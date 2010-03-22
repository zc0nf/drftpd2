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
package org.drftpd.commands.zipscript;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author djb61
 * @version $Id$
 */
public class SFVTools {

	protected Collection<FileHandle> getSFVFiles(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData) 
	throws IOException, FileNotFoundException, NoAvailableSlaveException, SlaveUnavailableException {
		Collection<FileHandle> files = new ArrayList<FileHandle>();
		SFVInfo sfvInfo = sfvData.getSFVInfo();

		for (String name : sfvInfo.getEntries().keySet()) {
			FileHandle file = new FileHandle(dir.getPath()+VirtualFileSystem.separator+name);
			if (file.exists() && file.getXfertime() != -1) {
				files.add(file);
			}
		}
		return files;
	}

	protected long getSFVTotalBytes(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData) 
	throws IOException, FileNotFoundException, NoAvailableSlaveException, SlaveUnavailableException {
		long totalBytes = 0;

		for (FileHandle file : getSFVFiles(dir, sfvData)) {
			totalBytes += file.getSize();
		}
		return totalBytes;
	}

	protected long getSFVLargestFileBytes(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData) 
	throws IOException, FileNotFoundException, NoAvailableSlaveException, SlaveUnavailableException {
		long largestFileBytes = 0;

		for (FileHandle file : getSFVFiles(dir, sfvData)) {
			if (file.getSize() > largestFileBytes) {
				largestFileBytes = file.getSize();
			}
		}
		return largestFileBytes;
	}

	protected long getSFVTotalXfertime(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
	throws IOException, FileNotFoundException, NoAvailableSlaveException, SlaveUnavailableException {
		long totalXfertime = 0;

		for (FileHandle file : getSFVFiles(dir, sfvData)) {
			totalXfertime += file.getXfertime();
		}
		return totalXfertime;
	}

	protected long getXferspeed(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
	throws IOException, FileNotFoundException, NoAvailableSlaveException, SlaveUnavailableException {
		long totalXfertime = getSFVTotalXfertime(dir, sfvData);
		if (totalXfertime / 1000 == 0) {
			return 0;
		}

		return getSFVTotalBytes(dir, sfvData) / (totalXfertime / 1000);
	}
}