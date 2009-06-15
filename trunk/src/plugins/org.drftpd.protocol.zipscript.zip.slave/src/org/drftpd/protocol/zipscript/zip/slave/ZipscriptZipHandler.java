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
package org.drftpd.protocol.zipscript.zip.slave;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.drftpd.protocol.slave.AbstractHandler;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.async.AsyncResponseDizInfo;
import org.drftpd.protocol.zipscript.zip.common.async.AsyncResponseZipCRCInfo;
import org.drftpd.slave.Slave;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;

import se.mog.io.File;
import sun.misc.BASE64Encoder;
import de.schlichtherle.io.archive.zip.Zip32InputArchive;
import de.schlichtherle.io.rof.SimpleReadOnlyFile;
import de.schlichtherle.util.zip.ZipEntry;

/**
 * Handler for Zip requests.
 * @author djb61
 * @version $Id$
 */
public class ZipscriptZipHandler extends AbstractHandler {

	public ZipscriptZipHandler(SlaveProtocolCentral central) {
		super(central);
	}

	public AsyncResponse handleZipCRC(AsyncCommandArgument ac) {
		return new AsyncResponseZipCRCInfo(ac.getIndex(), 
				checkZipFile(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
	}

	public AsyncResponse handleZipDizInfo(AsyncCommandArgument ac) {
		return new AsyncResponseDizInfo(ac.getIndex(), 
				getDizInfo(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
	}

	private boolean checkZipFile(Slave slave, String path) {
		boolean integrityOk = true;
		InputStream entryStream = null;
		Zip32InputArchive zipFile = null;
		try {
			File file = slave.getRoots().getFile(path);
			zipFile = new Zip32InputArchive(new SimpleReadOnlyFile(file),"UTF-8",true,false);
			for (Enumeration<?> zipEntries = zipFile.getArchiveEntries();zipEntries.hasMoreElements();) {
				ZipEntry zipEntry = (ZipEntry)zipEntries.nextElement();
				entryStream = zipFile.getCheckedInputStream(zipEntry);
				byte[] buff = new byte[65536];
				while (entryStream.read(buff) != -1) {
					// do nothing, we are only checking for crc
				}
				entryStream.close();
			}
		} catch (IOException e) {
			// Catch all IOExceptions not just CRC32Exception as a badly truncated zip can trigger an EOFException etc
			integrityOk = false;
		} finally {
			if (zipFile != null) {
				try {
					zipFile.close();
				} catch (IOException e) {
					// don't care at this point, just cleaning up descriptors
				}
			}
		}
		return integrityOk;
	}

	private DizInfo getDizInfo(Slave slave, String path) {
		DizInfo dizInfo = new DizInfo();
		InputStream entryStream = null;
		Zip32InputArchive zipFile = null;
		try {
			File file = slave.getRoots().getFile(path);
			zipFile = new Zip32InputArchive(new SimpleReadOnlyFile(file),"UTF-8",true,false);
			for (Enumeration<?> zipEntries = zipFile.getArchiveEntries();zipEntries.hasMoreElements();) {
				ZipEntry zipEntry = (ZipEntry)zipEntries.nextElement();
				if (zipEntry.getName().toLowerCase().equals("file_id.diz")) {
					entryStream = zipFile.getCheckedInputStream(zipEntry);
					byte[] buff = new byte[65536];
					StringBuilder dizBuffer = new StringBuilder();
					int bytesRead = 0;
					while (bytesRead != -1) {
						bytesRead = entryStream.read(buff);
						if (bytesRead != -1) {
							String dizBlock = new String(buff,0,bytesRead,"8859_1");
							dizBuffer.append(dizBlock);
						}
					}
					entryStream.close();
					String dizString = dizBuffer.toString();
					int total = getDizTotal(dizString);
					if (total > 0) {
						dizInfo.setValid(true);
						dizInfo.setTotal(total);
						dizString = new BASE64Encoder().encode(dizBuffer.toString().getBytes("8859_1"));
						//dizString = Base64.bytetoB64(dizBuffer.toString().getBytes("8859_1"));
						dizInfo.setString(dizString);
					}
					break;
				}
			}
		} catch (IOException e) {
			// Something wrong with the .diz entry in this file, just return with no diz info
		} finally {
			if (zipFile != null) {
				try {
					zipFile.close();
				} catch (IOException e) {
					// don't care at this point, just cleaning up descriptors
				}
			}
		}
		return dizInfo;
	}

	private int getDizTotal(String dizString) {
		int total = 0;
		String regex = "[\\[\\(\\<\\:\\s][0-9oOxX]*/([0-9oOxX]*[0-9])[\\]\\)\\>\\s]";
		Pattern p = Pattern.compile(regex);

		// Compare the diz file to the pattern compiled above
		Matcher m = p.matcher(dizString);

		if (m.find()) {
			total = new Integer(m.group(1).replaceAll("[oOxX]", "0"));
		}

		return total;
	}
}
