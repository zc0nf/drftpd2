package net.sf.drftpd.slave;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public interface Transfer extends Remote {
	public static final char TRANSFER_RECEIVING_UPLOAD='R';
	public static final char TRANSFER_SENDING_DOWNLOAD='S';
	public static final char TRANSFER_THROUGHPUT='A';
	public static final char TRANSFER_UNKNOWN='U';
	
	public long getChecksum() throws RemoteException;
	public int getLocalPort() throws RemoteException;
	public long getTransfered() throws RemoteException;
	public int getXferSpeed() throws RemoteException;
	
	public void receiveFile(String dirname, String filename, long offset) throws RemoteException, IOException;
	public void sendFile(String path, char mode, long resumePosition, boolean checksum) throws RemoteException, IOException;
	public long getTransferTime() throws RemoteException;
	
	public void abort() throws RemoteException;
}
