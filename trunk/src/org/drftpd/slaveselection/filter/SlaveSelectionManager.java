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
package org.drftpd.slaveselection.filter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.Transfer;

import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

/**
 * @author mog
 * @version $Id: SlaveSelectionManager.java,v 1.13 2004/05/20 14:09:00 zubov Exp $
 */
public class SlaveSelectionManager implements SlaveSelectionManagerInterface {
	private SlaveManagerImpl _sm;
	private FilterChain _ssmiDown;
	private FilterChain _ssmiJobDown;
	private FilterChain _ssmiJobUp;
	private FilterChain _ssmiMaster;
	private FilterChain _ssmiUp;

	public SlaveSelectionManager(SlaveManagerImpl sm)
		throws FileNotFoundException, IOException {
		_sm = sm;
		reload();
	}

	/**
	 * Checksums call us with null BaseFtpConnection.
	 */
	public RemoteSlave getASlave(
		Collection rslaves,
		char direction,
		BaseFtpConnection conn,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		InetAddress source = (conn != null ? conn.getClientAddress() : null);
		String status;
		if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			status = "up";
		} else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			status = "down";
		} else {
			throw new IllegalArgumentException();
		}
		return process(
			status,
			new ScoreChart(rslaves),
			conn != null ? conn.getUserNull() : null,
			source,
			direction,
			file);
	}

	public RemoteSlave getASlaveForJobDownload(Job job)
		throws NoAvailableSlaveException {
		ArrayList slaves = new ArrayList(job.getFile().getAvailableSlaves());
		slaves.removeAll(job.getDestinationSlaves());
		if (slaves.isEmpty())
			throw new NoAvailableSlaveException();
		return process(
			"jobdown",
			new ScoreChart(slaves),
			null,
			null,
			Transfer.TRANSFER_SENDING_DOWNLOAD,
			job.getFile());
	}

	public RemoteSlave getASlaveForJobUpload(Job job)
		throws NoAvailableSlaveException {
		ArrayList slaves = new ArrayList(job.getDestinationSlaves());
		for (Iterator iter = slaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (!rslave.isAvailable())
				iter.remove();
		}
		if (slaves.isEmpty())
			throw new NoAvailableSlaveException();
		return process(
			"jobup",
			new ScoreChart(slaves),
			null,
			null,
			Transfer.TRANSFER_SENDING_DOWNLOAD,
			job.getFile());
	}

	/**
	 * Get slave for transfer to master.
	 */
	public RemoteSlave getASlaveForMaster(
		LinkedRemoteFileInterface file,
		FtpConfig cfg)
		throws NoAvailableSlaveException {
		return process(
			"master",
			new ScoreChart(file.getAvailableSlaves()),
			null,
			null,
			Transfer.TRANSFER_SENDING_DOWNLOAD,
			file);
	}

	public SlaveManagerImpl getSlaveManager() {
		return _sm;
	}

	private RemoteSlave process(
		String filterchain,
		ScoreChart sc,
		User user,
		InetAddress peer,
		char direction,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		FilterChain ssmi;
		if (filterchain.equals("down")) {
			ssmi = _ssmiDown;
		} else if (filterchain.equals("up")) {
			ssmi = _ssmiUp;
		} else if (filterchain.equals("master")) {
			ssmi = _ssmiMaster;
		} else if (filterchain.equals("jobup")) {
			ssmi = _ssmiJobUp;
		} else if (filterchain.equals("jobdown")) {
			ssmi = _ssmiJobDown;
		} else {
			throw new IllegalArgumentException();
		}
		return ssmi.getBestSlave(sc, user, peer, direction, file);
	}

	public void reload() throws FileNotFoundException, IOException {
		_ssmiDown = new FilterChain(this, "conf/slaveselection-down.conf");
		_ssmiMaster = new FilterChain(this, "conf/slaveselection-master.conf");
		_ssmiUp = new FilterChain(this, "conf/slaveselection-up.conf");
		if (_sm.getConnectionManager().isJobManagerLoaded()) {
			_ssmiJobUp =
				new FilterChain(this, "conf/slaveselection-jobup.conf");
			_ssmiJobDown =
				new FilterChain(this, "conf/slaveselection-jobdown.conf");			
		}
	}
}
