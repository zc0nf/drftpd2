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
package net.sf.drftpd.mirroring;

import java.util.Comparator;

/**
 * @author zubov
 * @version $Id: JobComparator.java,v 1.4 2004/02/21 05:28:21 zubov Exp $
 *
 */
public class JobComparator implements Comparator {

	/**
	 * Compares Jobs
	 */
	public JobComparator() {
	}

	/* (non-Javadoc)
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(Object arg0, Object arg1) {
		Job job1 = (Job) arg0;
		Job job2 = (Job) arg1;
		if (job1.getPriority() > job2.getPriority())
			return -1;
		if (job1.getPriority() < job2.getPriority())
			return 1;
		if (job1.getTimeCreated() < job2.getTimeCreated()) { //older
			return -1;
		}
		if (job1.getTimeCreated() > job2.getTimeCreated()) { //younger
			return 1;
		}
		// same priority, and same time
		return 0;
	}

}
