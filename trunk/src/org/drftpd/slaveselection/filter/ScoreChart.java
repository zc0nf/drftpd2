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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.RemoteSlave;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: ScoreChart.java,v 1.3 2004/05/18 20:28:18 zubov Exp $
 */
public class ScoreChart {
	public static class SlaveScore implements Comparable {
		private RemoteSlave _rslave;
		private long _score;

		public SlaveScore(RemoteSlave rslave) {
			_rslave = rslave;
		}

		public void addScore(long score) {
			//logger.debug("Added "+score+" to "+getRSlave().getName());
			_score += score;
		}
		public int compareTo(Object o) {
			SlaveScore s = (SlaveScore) o;
			//int thisVal = this.value;
			//int anotherVal = anotherInteger.value;
			return (getScore()<s.getScore()? -1 : (getScore()==s.getScore()? 0 : 1));
		}
		public RemoteSlave getRSlave() {
			return _rslave;
		}
		public long getScore() {
			return _score;
		}
		public String toString() {
			return "SlaveScore[rslave="+getRSlave().getName()+",score="+getScore()+"]";
		}
	}

	private static final Logger logger = Logger.getLogger(ScoreChart.class);

	private ArrayList _scoreChart;

	/**
	 * @param slaves Collection of ONLINE slaves
	 * (this is not verified by scorechart, you can use offline slaves for JUnit tests etc.)
	 */
	public ScoreChart(Collection slaves) {
		_scoreChart = new ArrayList();
		for (Iterator iter = slaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			_scoreChart.add(new SlaveScore(rslave));
		}
	}
	public RemoteSlave getBestSlave() throws NoAvailableSlaveException {
		return getBestSlaveScore().getRSlave();
	}

	public SlaveScore getBestSlaveScore() throws NoAvailableSlaveException {
		SlaveScore bestscore;
		Iterator iter = getSlaveScores().iterator();
		if (!iter.hasNext())
			throw new NoAvailableSlaveException();
		bestscore = (SlaveScore) iter.next();
		logger.debug(bestscore);
		for (; iter.hasNext();) {
			SlaveScore score = (SlaveScore) iter.next();
			logger.debug(score);
			if (score.getScore() > bestscore.getScore()) {
				bestscore = score;
			}
		}
		if (bestscore == null) throw new NoAvailableSlaveException("This is not supposed to be thrown");
		return bestscore;
	}

	public SlaveScore getSlaveScore(RemoteSlave rslave)
		throws ObjectNotFoundException {
		for (Iterator iter = _scoreChart.iterator(); iter.hasNext();) {
			SlaveScore score = (SlaveScore) iter.next();
			if (score.getRSlave().equals(rslave))
				return score;
		}
		throw new ObjectNotFoundException(rslave.getName()+" not in ScoreChart");
	}

	public Collection getSlaveScores() {
		return _scoreChart;
	}

	public void removeSlaveScore(RemoteSlave rslave) {
		for (Iterator iter = _scoreChart.iterator(); iter.hasNext();) {
			SlaveScore score = (SlaveScore) iter.next();
			if(score.getRSlave().equals(rslave))
				iter.remove();
		}
		
	}

}
