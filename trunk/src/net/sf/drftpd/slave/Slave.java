package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

import net.sf.drftpd.SFVFile;

/**
 * Slave interface, this interface is used to initate transfers to and from remote slaves.
 * @author Morgan Christiansson <mog@linux.nu>
 * @version $Id: Slave.java,v 1.26 2003/12/23 13:38:21 mog Exp $
 */
public interface Slave extends Remote {
	public long checkSum(
		String path)
		throws RemoteException, IOException;
	
	public Transfer listen(boolean encrypted) throws RemoteException, IOException;
	public Transfer connect(InetSocketAddress addr, boolean encrypted) throws RemoteException;
	/**
	 * Get statistics for this slave, usefull when deciding which slave to use when transferring files.
	 */
	public SlaveStatus getSlaveStatus() throws RemoteException;

	/**
	 * Check to see if slave is still up.
	 */
	public void ping() throws RemoteException;
	
	public SFVFile getSFVFile(String path)
		throws RemoteException, IOException;
	
	/**
	 * Rename files.
	 */
	public void rename(String from, String toDirPath, String toName)
		throws RemoteException, IOException;
		
	/**
	 * Delete files.
	 */
	public void delete(String path) throws RemoteException, IOException;
}
