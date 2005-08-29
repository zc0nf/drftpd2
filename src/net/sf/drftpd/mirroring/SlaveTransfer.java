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
package net.sf.drftpd.mirroring;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;

import org.apache.log4j.Logger;

import org.drftpd.master.RemoteSlave;
import org.drftpd.master.RemoteTransfer;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.ConnectInfo;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.TransferFailedException;

import java.io.IOException;

import java.net.InetSocketAddress;


/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SlaveTransfer {
    private static final Logger logger = Logger.getLogger(SlaveTransfer.class);
    private RemoteSlave _destSlave;
    private LinkedRemoteFileInterface _file;
    private RemoteSlave _srcSlave;
    private RemoteTransfer _destTransfer = null;
    private RemoteTransfer _srcTransfer = null;

    /**
     * Slave to Slave Transfers
     */
    public SlaveTransfer(LinkedRemoteFileInterface file,
        RemoteSlave sourceSlave, RemoteSlave destSlave) {
        _file = file;
        _srcSlave = sourceSlave;
        _destSlave = destSlave;
    }

    long getTransfered() {
    	if(_srcTransfer == null || _destTransfer == null) return 0;
        return (_srcTransfer.getTransfered() + _destTransfer.getTransfered()) / 2;
    }

    int getXferSpeed() {
    	if(_srcTransfer == null || _destTransfer == null) return 0;
        return (_srcTransfer.getXferSpeed() + _destTransfer.getXferSpeed()) / 2;
    }

    /**
     * Returns true if the crc passed, false otherwise
     */
    protected boolean transfer(boolean checkCRC) throws SlaveException {
    	// can do encrypted slave2slave transfers by modifying the
    	// first argument in issueListenToSlave() and the third option
    	// in issueConnectToSlave(), maybe do an option later, is this wanted?
    	
        try {
            String destIndex = _destSlave.issueListenToSlave(false, false);
            ConnectInfo ci = _destSlave.fetchTransferResponseFromIndex(destIndex);
            _destTransfer = _destSlave.getTransfer(ci.getTransferIndex());
        } catch (SlaveUnavailableException e) {
            throw new SourceSlaveException(e);
        } catch (RemoteIOException e) {
            throw new SourceSlaveException(e);
        }

        try {
            String srcIndex = _srcSlave.issueConnectToSlave(_destTransfer
					.getAddress().getAddress().getHostAddress(), _destTransfer
					.getLocalPort(), false, true);
            ConnectInfo ci = _srcSlave.fetchTransferResponseFromIndex(srcIndex);
            _srcTransfer = _srcSlave.getTransfer(ci.getTransferIndex());
        } catch (SlaveUnavailableException e) {
            throw new DestinationSlaveException(e);
        } catch (RemoteIOException e) {
            throw new DestinationSlaveException(e);
        }

        try {
            _destTransfer.receiveFile(_file.getPath(), 'I', 0);
        } catch (IOException e1) {
            throw new DestinationSlaveException(e1);
        } catch (SlaveUnavailableException e1) {
            throw new DestinationSlaveException(e1);
        }

        try {
            _srcTransfer.sendFile(_file.getPath(), 'I', 0);
        } catch (IOException e2) {
            throw new SourceSlaveException(e2);
        } catch (SlaveUnavailableException e2) {
            throw new SourceSlaveException(e2);
        }

        boolean srcIsDone = false;
        boolean destIsDone = false;

        while (!(srcIsDone && destIsDone)) {
            try {
                if (_srcTransfer.getTransferStatus().isFinished()) {
                    srcIsDone = true;
                }
            } catch (TransferFailedException e7) {
                try {
                    _destTransfer.abort("srcSlave had an error");
                } catch (SlaveUnavailableException e8) {
                }

                throw new SourceSlaveException(e7);
            } catch (SlaveUnavailableException e7) {
                try {
                    _destTransfer.abort("srcSlave had an error");
                } catch (SlaveUnavailableException e8) {
                }

                throw new SourceSlaveException(e7);
            }

            try {
                if (_destTransfer.getTransferStatus().isFinished()) {
                    destIsDone = true;
                }
            } catch (TransferFailedException e6) {
                try {
                    _srcTransfer.abort("destSlave had an error");
                } catch (SlaveUnavailableException e8) {
                }

                throw new DestinationSlaveException(e6);
            } catch (SlaveUnavailableException e6) {
                try {
                    _srcTransfer.abort("destSlave had an error");
                } catch (SlaveUnavailableException e8) {
                }

                throw new DestinationSlaveException(e6);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e5) {
            }
        }

        if (!checkCRC) {
            // crc passes if we're not using it
            _file.addSlave(_destSlave);

            return true;
        }

        long dstxferCheckSum = _destTransfer.getChecksum();

        try {
            if ((dstxferCheckSum == 0) ||
                    (_file.getCheckSumCached() == dstxferCheckSum) ||
                    (_file.getCheckSumFromSlave() == dstxferCheckSum)) {
                _file.addSlave(_destSlave);

                return true;
            }
        } catch (NoAvailableSlaveException e) {
            logger.info("NoAvailableSlaveException caught getting checksum from slave",
                e);

            return false;
        } catch (IOException e) {
            logger.info("Exception caught getting checksum from slave", e);

            return false;
        }

        return false;
    }
}
