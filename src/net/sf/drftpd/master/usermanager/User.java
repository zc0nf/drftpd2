/*
 * Created on 2003-jul-26
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.usermanager;

import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.ObjectExistsException;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public abstract class User {
	/**
	 * authenticates and logs in the user.
	 * @param user given password
	 */
	
	public abstract boolean checkPassword(String password);
	public abstract void setPassword(String password);
	//////////////////////////////// generic getters & setters ///////////////////////
	public abstract void updateCredits(long credits);
	public abstract void updateDownloadedBytes(long bytes);
	public abstract void updateUploadedBytes(long bytes);
	
	////////////////////////////////// autogenerated getters & setters below /////////////////////////////
	public abstract String getUsername();
	public abstract void rename(String username) throws ObjectExistsException, UserFileException;

	public abstract String getComment();
	public abstract void setComment(String comment);
	public abstract int getMaxSimDownloads();
	public abstract void setMaxSimDownloads(int maxSimDownloads);
	/**
	 * Returns the maxSimUploads.
	 * @return int
	 */
	public abstract int getMaxSimUploads();
	/**
	 * Sets the maxSimUploads.
	 * @param maxSimUploads The maxSimUploads to set
	 */
	public abstract void setMaxSimUploads(int maxSimUploads);
	/**
	 * Get the maximum idle time in second.
	 */
	public abstract int getMaxIdleTime();
	/**
	 * Set the maximum idle time in second.
	 */
	public abstract void setMaxIdleTime(int idleTime);
	/**
	 * Get maximum user upload rate in bytes/sec.
	 */
	public abstract int getMaxUploadRate();
	/**
	 * Set user maximum upload rate limit.
	 * Less than or equal to zero means no limit.
	 */
	public abstract void setMaxUploadRate(int rate);
	/**
	 * Get maximum user download rate in bytes/sec
	 */
	public abstract int getMaxDownloadRate();
	/**
	 * Set user maximum download rate limit.
	 * Less than or equal to zero means no limit.
	 */
	public abstract void setMaxDownloadRate(int rate);
	/**
	 * Get user loglin time.
	 */
	public abstract int getLoginTime();
	/**
	 * Get last access time
	 */
	public abstract long getLastAccessTime();
	/**
	 * User login.
	 */
	public abstract void login();
	/**
	 * User logout
	 */
	public abstract void logout();
	/**
	 * Is an active user (is removable)?
	 * Compares the last access time with the specified time.
	 */
	public abstract boolean isActive(long currTime);
	/**
	 * Is still active. Compares the last access time with the
	 * current time.
	 */
	public abstract boolean isActive();
	/**
	 * Hit user - update last access time
	 */
	public abstract void hitUser();
	/**
	 * Returns the maxLogins.
	 * @return int
	 */
	public abstract int getMaxLogins();
	/**
	 * Returns the maxLoginsPerIP.
	 * @return int
	 */
	public abstract int getMaxLoginsPerIP();
	/**
	 * Sets the maxLogins.
	 * @param maxLogins The maxLogins to set
	 */
	public abstract void setMaxLogins(int maxLogins);
	/**
	 * Sets the maxLoginsPerIP.
	 * @param maxLoginsPerIP The maxLoginsPerIP to set
	 */
	public abstract void setMaxLoginsPerIP(int maxLoginsPerIP);
	/**
	 * Returns the admin.
	 * @return boolean
	 */
	public boolean isAdmin() {
		return isMemberOf("admin");
	}
	public boolean isMemberOf(String group) {
		for (Iterator iter = this.getGroups().iterator(); iter.hasNext();) {
			if(group.equals((String)iter.next())) return true;
		}
		return false;
	}
	/**
	 * Returns the nuker.
	 * @return boolean
	 */
	public boolean isNuker() {
		return isMemberOf("nuker");
	}
	/**
	 * Returns the tagline.
	 * @return String
	 */
	public abstract String getTagline();
	
	/**
	 * Sets the tagline.
	 * @param tagline The tagline to set
	 */
	public abstract void setTagline(String tagline);
	/**
	 * Returns the credits.
	 * @return long
	 */
	public abstract long getCredits();
	/**
	 * Returns the idleTime.
	 * @return long
	 */
	public abstract long getIdleTime();
	/**
	 * Returns the logins.
	 * @return int
	 */
	public abstract int getLogins();
	/**
	 * Returns the ratio.
	 * @return float
	 */
	public abstract float getRatio();
	/**
	 * Sets the credits.
	 * @param credits The credits to set
	 */
	public abstract void setCredits(long credits);
	/**
	 * Sets the idleTime.
	 * @param idleTime The idleTime to set
	 */
	public abstract void setIdleTime(int idleTime);
	/**
	 * Sets the ratio.
	 * @param ratio The ratio to set
	 */
	public abstract void setRatio(float ratio);
	/**
	 * Returns the downloadedBytes.
	 * @return long
	 */
	public abstract long getDownloadedBytes();
	/**
	 * Returns the downloadedBytesMonth.
	 * @return long
	 */
	public abstract long getDownloadedBytesMonth();
	/**
	 * Returns the downloadedBytesWeek.
	 * @return long
	 */
	public abstract long getDownloadedBytesWeek();
	/**
	 * Returns the uploadedBytes.
	 * @return long
	 */
	public abstract long getUploadedBytes();
	/**
	 * Returns the uploadedBytesMonth.
	 * @return long
	 */
	public abstract long getUploadedBytesMonth();
	/**
	 * Returns the uploadedBytesWeek.
	 * @return long
	 */
	public abstract long getUploadedBytesWeek();
	/**
	 * Sets the downloadedBytes.
	 * @param downloadedBytes The downloadedBytes to set
	 */
	public abstract void setDownloadedBytes(long downloadedBytes);
	/**
	 * Sets the downloadedBytesMonth.
	 * @param downloadedBytesMonth The downloadedBytesMonth to set
	 */
	public abstract void setDownloadedBytesMonth(long downloadedBytesMonth);
	/**
	 * Sets the downloadedBytesWeek.
	 * @param downloadedBytesWeek The downloadedBytesWeek to set
	 */
	public abstract void setDownloadedBytesWeek(long downloadedBytesWeek);
	/**
	 * Sets the uploadedBytes.
	 * @param uploadedBytes The uploadedBytes to set
	 */
	public abstract void setUploadedBytes(long uploadedBytes);
	/**
	 * Sets the uploadedBytesMonth.
	 * @param uploadedBytesMonth The uploadedBytesMonth to set
	 */
	public abstract void setUploadedBytesMonth(long uploadedBytesMonth);
	/**
	 * Sets the uploadedBytesWeek.
	 * @param uploadedBytesWeek The uploadedBytesWeek to set
	 */
	public abstract void setUploadedBytesWeek(long uploadedBytesWeek);
	/**
	 * Returns the downloadedBytesDay.
	 * @return long
	 */
	public abstract long getDownloadedBytesDay();
	/**
	 * Returns the uploadedBytesDay.
	 * @return long
	 */
	public abstract long getUploadedBytesDay();
	/**
	 * Sets the downloadedBytesDay.
	 * @param downloadedBytesDay The downloadedBytesDay to set
	 */
	public abstract void setDownloadedBytesDay(long downloadedBytesDay);
	/**
	 * Sets the uploadedBytesDay.
	 * @param uploadedBytesDay The uploadedBytesDay to set
	 */
	public abstract void setUploadedBytesDay(long uploadedBytesDay);
	/**
	 * Returns the deleted.
	 * @return boolean
	 */
	public abstract boolean isDeleted();
	/**
	 * Sets the deleted.
	 * @param deleted The deleted to set
	 */
	public abstract void setDeleted(boolean deleted);
	
	public abstract void purge();
	/**
	 * Returns the lastNuked.
	 * @return long
	 */
	public abstract long getLastNuked();
	/**
	 * Sets the lastNuked.
	 * @param lastNuked The lastNuked to set
	 */
	public abstract void setLastNuked(long lastNuked);
	/**
	 * Returns the nuked.
	 * @return int
	 */
	public abstract int getTimesNuked();
	/**
	 * Sets the nuked.
	 * @param nuked The nuked to set
	 */
	public abstract void setTimesNuked(int nuked);
	public abstract void updateTimesNuked(int timesNuked);

	/**
	 * Returns the nukedBytes.
	 * @return long
	 */
	public abstract long getNukedBytes();
	public abstract void updateNukedBytes(long bytes);
	/**
	 * Sets the nukedBytes.
	 * @param nukedBytes The nukedBytes to set
	 */
	public abstract void setNukedBytes(long nukedBytes);

	/**
	 * Returns the anonymous.
	 * @return boolean
	 */
	public abstract boolean isAnonymous();
	/**
	 * Sets the anonymous.
	 * @param anonymous The anonymous to set
	 */
	public abstract void setAnonymous(boolean anonymous);

	/**
	 * Sets the lastAccessTime.
	 * @param lastAccessTime The lastAccessTime to set
	 */
	public abstract void setLastAccessTime(int lastAccessTime);
	/**
	 * Returns the timeToday.
	 * @return long
	 */
	public abstract long getTimeToday();
	/**
	 * Sets the timeToday.
	 * @param timeToday The timeToday to set
	 */
	public abstract void setTimeToday(int timeToday);
	/**
	 * Returns the timelimit.
	 * @return int
	 */
	public abstract int getTimelimit();
	/**
	 * Sets the timelimit.
	 * @param timelimit The timelimit to set
	 */
	public abstract void setTimelimit(int timelimit);
	

	public abstract void addGroup(String group) throws DuplicateElementException;
	public abstract String getGroup();
	public abstract Collection getGroups();
	public abstract void removeGroup(String group) throws NoSuchFieldException;
	
	public abstract void addIPMask(String mask) throws DuplicateElementException;
	public abstract boolean checkIP(String masks[]);
	public abstract Collection getIpMasks();
	public abstract void removeIpMask(String mask) throws NoSuchFieldException;

	/**
	 * Returns the downloadedFiles.
	 * @return int
	 */
	public abstract int getDownloadedFiles();
	/**
	 * Returns the downloadedFilesDay.
	 * @return int
	 */
	public abstract int getDownloadedFilesDay();
	/**
	 * Returns the downloadedFilesMonth.
	 * @return int
	 */
	public abstract int getDownloadedFilesMonth();
	/**
	 * Returns the downloadedFilesWeek.
	 * @return int
	 */
	public abstract int getDownloadedFilesWeek();
	/**
	 * Returns the downloadedSeconds.
	 * @return int
	 */
	public abstract int getDownloadedSeconds();
	/**
	 * Returns the downloadedSecondsDay.
	 * @return int
	 */
	public abstract int getDownloadedSecondsDay();
	/**
	 * Returns the downloadedSecondsMonth.
	 * @return int
	 */
	public abstract int getDownloadedSecondsMonth();
	/**
	 * Returns the downloadedSecondsWeek.
	 * @return int
	 */
	public abstract int getDownloadedSecondsWeek();
	/**
	 * Returns the uploadedFiles.
	 * @return int
	 */
	public abstract int getUploadedFiles();
	/**
	 * Returns the uploadedFilesDay.
	 * @return int
	 */
	public abstract int getUploadedFilesDay();
	/**
	 * Returns the uploadedFilesMonth.
	 * @return int
	 */
	public abstract int getUploadedFilesMonth();
	/**
	 * Returns the uploadedFilesWeek.
	 * @return int
	 */
	public abstract int getUploadedFilesWeek();
	/**
	 * Returns the uploadedSeconds.
	 * @return int
	 */
	public abstract int getUploadedSeconds();
	/**
	 * Returns the uploadedSecondsDay.
	 * @return int
	 */
	public abstract int getUploadedSecondsDay();
	/**
	 * Returns the uploadedSecondsMonth.
	 * @return int
	 */
	public abstract int getUploadedSecondsMonth();
	/**
	 * Returns the uploadedSecondsWeek.
	 * @return int
	 */
	public abstract int getUploadedSecondsWeek();
	/**
	 * Sets the downloadedFiles.
	 * @param downloadedFiles The downloadedFiles to set
	 */
	public abstract void setDownloadedFiles(int downloadedFiles);
	/**
	 * Sets the downloadedFilesDay.
	 * @param downloadedFilesDay The downloadedFilesDay to set
	 */
	public abstract void setDownloadedFilesDay(int downloadedFilesDay);
	/**
	 * Sets the downloadedFilesMonth.
	 * @param downloadedFilesMonth The downloadedFilesMonth to set
	 */
	public abstract void setDownloadedFilesMonth(int downloadedFilesMonth);
	/**
	 * Sets the downloadedFilesWeek.
	 * @param downloadedFilesWeek The downloadedFilesWeek to set
	 */
	public abstract void setDownloadedFilesWeek(int downloadedFilesWeek);
	/**
	 * Sets the downloadedSeconds.
	 * @param downloadedSeconds The downloadedSeconds to set
	 */
	public abstract void setDownloadedSeconds(int downloadedSeconds);
	/**
	 * Sets the downloadedSecondsDay.
	 * @param downloadedSecondsDay The downloadedSecondsDay to set
	 */
	public abstract void setDownloadedSecondsDay(int downloadedSecondsDay);
	/**
	 * Sets the downloadedSecondsMonth.
	 * @param downloadedSecondsMonth The downloadedSecondsMonth to set
	 */
	public abstract void setDownloadedSecondsMonth(int downloadedSecondsMonth);
	/**
	 * Sets the downloadedSecondsWeek.
	 * @param downloadedSecondsWeek The downloadedSecondsWeek to set
	 */
	public abstract void setDownloadedSecondsWeek(int downloadedSecondsWeek);
	/**
	 * Sets the uploadedFiles.
	 * @param uploadedFiles The uploadedFiles to set
	 */
	public abstract void setUploadedFiles(int uploadedFiles);
	/**
	 * Sets the uploadedFilesDay.
	 * @param uploadedFilesDay The uploadedFilesDay to set
	 */
	public abstract void setUploadedFilesDay(int uploadedFilesDay);
	/**
	 * Sets the uploadedFilesMonth.
	 * @param uploadedFilesMonth The uploadedFilesMonth to set
	 */
	public abstract void setUploadedFilesMonth(int uploadedFilesMonth);
	/**
	 * Sets the uploadedFilesWeek.
	 * @param uploadedFilesWeek The uploadedFilesWeek to set
	 */
	public abstract void setUploadedFilesWeek(int uploadedFilesWeek);
	/**
	 * Sets the uploadedSeconds.
	 * @param uploadedSeconds The uploadedSeconds to set
	 */
	public abstract void setUploadedSeconds(int uploadedSeconds);
	/**
	 * Sets the uploadedSecondsDay.
	 * @param uploadedSecondsDay The uploadedSecondsDay to set
	 */
	public abstract void setUploadedSecondsDay(int uploadedSecondsDay);
	/**
	 * Sets the uploadedSecondsMonth.
	 * @param uploadedSecondsMonth The uploadedSecondsMonth to set
	 */
	public abstract void setUploadedSecondsMonth(int uploadedSecondsMonth);
	/**
	 * Sets the uploadedSecondsWeek.
	 * @param uploadedSecondsWeek The uploadedSecondsWeek to set
	 */
	public abstract void setUploadedSecondsWeek(int uploadedSecondsWeek);
	/**
	 * Sets the logins.
	 * @param logins The logins to set
	 */
	public abstract void setLogins(int logins);
	
	/**
	 * Saves the changes to the underlying medium
	 */
	public abstract void commit() throws UserFileException;
	
	/**
		 * Equality check.
		 */
	public boolean equals(Object obj) {
		if (obj instanceof User) {
			return this.getUsername().equals(((User) obj).getUsername());
		}
		if (obj instanceof String) {
			return this.getUsername().equals((String)obj);
		}
		return false;
	}
	public int hashCode() {
		return getUsername().hashCode();
	}
}