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
package se.mog.io;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 * 
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
class UnixFileSystem extends FileSystem {
	public File[] listMounts() throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader("/etc/mtab"));

		try {
			Vector mountPoints = new Vector();
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.charAt(0) == '#') {
					continue;
				}

				Enumeration st = new StringTokenizer(line, " \t");

				if (!st.hasMoreElements()) {
					continue;
				}

				/* String fs_spec = */
				st.nextElement();

				if (!st.hasMoreElements()) {
					System.err
							.println("WARN: /etc/mtab is corrupt, skipping line");

					continue;
				}

				// String fs_file = st.nextToken();
				mountPoints.add(new File((String) st.nextElement()));

				/*
				 * String fs_vfstype = st.nextToken(); String fs_mntops =
				 * st.nextToken() int fs_freq =
				 * Integer.parseInt(st.nextToken()); int fs_passno =
				 * Integer.parseInt(st.nextToken());
				 */
			}

			return (File[]) mountPoints.toArray(new File[mountPoints.size()]);
		} finally {
			reader.close();
		}
	}

	public static void main(String[] args) throws IOException {
		File[] mounts = new UnixFileSystem().listMounts();

		for (int i = 0; i < mounts.length; i++) {
			System.out.println(mounts[i]);
		}
	}

	public native DiskFreeSpace getDiskFreeSpace(File file);
}