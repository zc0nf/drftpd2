/*
 * Created on 2003-jul-26
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.usermanager;

import java.util.Collection;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.ObjectExistsException;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public abstract class User {
	

	public abstract void addGroup(String group) throws DuplicateElementException;
	
	public abstract void addIPMask(String mask) throws DuplicateElementException;
	public abstract boolean checkIP(String masks[]);
	/**
	 * authenticates and logs in the user.
	 * @param user given password
	 */
	
	public abstract boolean checkPassword(String password);
	
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

	public abstract String getComment();
	/**
	 * Returns the credits.
	 * @return long
	 */
	public abstract long getCredits();
	/**
	 * Returns the downloadedBytes.
	 * @return long
	 */
	public abstract long getDownloadedBytes();
	/**
	 * Returns the downloadedBytesDay.
	 * @return long
	 */
	public abstract long getDownloadedBytesDay();
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
	public abstract void setGroup(String group);
	public abstract String getGroupName();
	public abstract Collection getGroups();
	/**
	 * Returns the idleTime.
	 * @return long
	 */
	public abstract long getIdleTime();
	public abstract Collection getIpMasks();
	/**
	 * Get last access time
	 */
	public abstract long getLastAccessTime();
	/**
	 * Returns the lastNuked.
	 * @return long
	 */
	public abstract long getLastNuked();
	/**
	 * Returns the logins.
	 * @return int
	 */
	public abstract int getLogins();
	/**
	 * Get user loglin time.
	 */
	public abstract int getLoginTime();
	/**
	 * Get maximum user download rate in bytes/sec
	 */
	public abstract int getMaxDownloadRate();
	/**
	 * Get the maximum idle time in second.
	 */
	public abstract int getMaxIdleTime();
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
	public abstract int getMaxSimDownloads();
	/**
	 * Returns the maxSimUploads.
	 * @return int
	 */
	public abstract int getMaxSimUploads();
	/**
	 * Get maximum user upload rate in bytes/sec.
	 */
	public abstract int getMaxUploadRate();

	/**
	 * Returns the nukedBytes.
	 * @return long
	 */
	public abstract long getNukedBytes();
	/**
	 * Returns the ratio.
	 * @return float
	 */
	public abstract float getRatio();
	/**
	 * Returns the tagline.
	 * @return String
	 */
	public abstract String getTagline();
	/**
	 * Returns the timelimit.
	 * @return int
	 */
	public abstract int getTimelimit();
	/**
	 * Returns the nuked.
	 * @return int
	 */
	public abstract int getTimesNuked();
	/**
	 * Returns the timeToday.
	 * @return long
	 */
	public abstract long getTimeToday();
	/**
	 * Returns the uploadedBytes.
	 * @return long
	 */
	public abstract long getUploadedBytes();
	/**
	 * Returns the uploadedBytesDay.
	 * @return long
	 */
	public abstract long getUploadedBytesDay();
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
	
	////////////////////////////////// autogenerated getters & setters below /////////////////////////////
	public abstract String getUsername();
	public int hashCode() {
		return getUsername().hashCode();
	}
	/**
	 * Hit user - update last access time
	 */
	public abstract void hitUser();
	/**
	 * Is still active. Compares the last access time with the
	 * current time.
	 */
	public abstract boolean isActive();
	/**
	 * Is an active user (is removable)?
	 * Compares the last access time with the specified time.
	 */
	public abstract boolean isActive(long currTime);
	/**
	 * Returns the admin.
	 * @return boolean
	 */
	public abstract boolean isAdmin();

	/**
	 * Returns the anonymous.
	 * @return boolean
	 */
	public abstract boolean isAnonymous();
	/**
	 * Returns the deleted.
	 * @return boolean
	 */
	public abstract boolean isDeleted();
	public abstract boolean isGroupAdmin();
	public abstract boolean isMemberOf(String group);
	/**
	 * Returns the nuker.
	 * @return boolean
	 */
	public boolean isNuker() {
		return isMemberOf("nuker");
	}
	/**
	 * User login.
	 */
	public abstract void login();
	/**
	 * User logout
	 */
	public abstract void logout();
	
	public abstract void purge();
	public abstract void removeGroup(String group) throws NoSuchFieldException;
	public abstract void removeIpMask(String mask) throws NoSuchFieldException;
	public abstract void rename(String username) throws ObjectExistsException, UserFileException;
	/**
	 * Sets the anonymous.
	 * @param anonymous The anonymous to set
	 */
	public abstract void setAnonymous(boolean anonymous);
	public abstract void setComment(String comment);
	/**
	 * Sets the credits.
	 * @param credits The credits to set
	 */
	public abstract void setCredits(long credits);
	/**
	 * Sets the deleted.
	 * @param deleted The deleted to set
	 */
	public abstract void setDeleted(boolean deleted);
	/**
	 * Sets the idleTime.
	 * @param idleTime The idleTime to set
	 */
	public abstract void setIdleTime(int idleTime);

	/**
	 * Sets the lastAccessTime.
	 * @param lastAccessTime The lastAccessTime to set
	 */
	public abstract void setLastAccessTime(int lastAccessTime);
	/**
	 * Sets the lastNuked.
	 * @param lastNuked The lastNuked to set
	 */
	public abstract void setLastNuked(long lastNuked);
	/**
	 * Sets the logins.
	 * @param logins The logins to set
	 */
	public abstract void setLogins(int logins);
	/**
	 * Set user maximum download rate limit.
	 * Less than or equal to zero means no limit.
	 */
	public abstract void setMaxDownloadRate(int rate);
	/**
	 * Set the maximum idle time in second.
	 */
	public abstract void setMaxIdleTime(int idleTime);
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
	public abstract void setMaxSimDownloads(int maxSimDownloads);
	/**
	 * Sets the maxSimUploads.
	 * @param maxSimUploads The maxSimUploads to set
	 */
	public abstract void setMaxSimUploads(int maxSimUploads);
	/**
	 * Set user maximum upload rate limit.
	 * Less than or equal to zero means no limit.
	 */
	public abstract void setMaxUploadRate(int rate);
	/**
	 * Sets the nukedBytes.
	 * @param nukedBytes The nukedBytes to set
	 */
	public abstract void setNukedBytes(long nukedBytes);
	public abstract void setPassword(String password);
	/**
	 * Sets the ratio.
	 * @param ratio The ratio to set
	 */
	public abstract void setRatio(float ratio);
	
	/**
	 * Sets the tagline.
	 * @param tagline The tagline to set
	 */
	public abstract void setTagline(String tagline);
	/**
	 * Sets the timelimit.
	 * @param timelimit The timelimit to set
	 */
	public abstract void setTimelimit(int timelimit);
	/**
	 * Sets the nuked.
	 * @param nuked The nuked to set
	 */
	public abstract void setTimesNuked(int nuked);
	/**
	 * Sets the timeToday.
	 * @param timeToday The timeToday to set
	 */
	public abstract void setTimeToday(int timeToday);
	//////////////////////////////// generic getters & setters ///////////////////////
	public abstract void updateCredits(long credits);
	public abstract void updateDownloadedBytes(long bytes);
	public abstract void updateDownloadedFiles(int i);
	public abstract void updateNukedBytes(long bytes);
	public abstract void updateTimesNuked(int timesNuked);
	public abstract void updateUploadedBytes(long bytes);
	public abstract void updateUploadedFiles(int i);
	/**
	 * @return
	 */
	public abstract short getGroupLeechSlots();

	/**
	 * @return
	 */
	public abstract short getGroupSlots();

	/**
	 * @param s
	 */
	public abstract void setGroupLeechSlots(short s);

	/**
	 * @param s
	 */
	public abstract void setGroupSlots(short s);
}