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

import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.Time;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author mog
 * @version $Id: MintimeonlineFilterTest.java,v 1.1 2004/02/27 01:02:21 mog Exp $
 */
public class MintimeonlineFilterTest extends TestCase {

	public MintimeonlineFilterTest(String name) {
		super(name);
	}

	public static TestSuite suite() {
		return new TestSuite(MintimeonlineFilterTest.class);
	}

	public static class RS extends RemoteSlave {

		public RS(String name) {
			super(name, null);
		}

		public long getLastTransferForDirection(char dir) {
			return System.currentTimeMillis() - Time.parseTime("1m");
		}
	}
	public void setUp() {
		BasicConfigurator.configure();
	}
	public void testSimple() throws NoAvailableSlaveException {
		Properties p = new Properties();
		p.put("1.multiplier", "1");
		p.put("1.mintime", "2m");

		Filter f = new MintimeonlineFilter(null, 1, p);

		RemoteSlave rslaves[] = { new RS("slave1")};
		ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
		f.process(sc, null, null, Transfer.TRANSFER_UNKNOWN, null);
		assertEquals(-Time.parseTime("1m"), sc.getBestSlaveScore().getScore());
	}
}
