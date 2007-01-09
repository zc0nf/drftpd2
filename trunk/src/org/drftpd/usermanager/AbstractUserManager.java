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
package org.drftpd.usermanager;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.FileExistsException;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.KeyNotFoundException;

import se.mog.io.PermissionDeniedException;

/**
 * This is the base class of all the user manager classes. If we want to add a
 * new user manager, we have to override this class.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @version $Id$
 */
public abstract class AbstractUserManager implements UserManager {
	protected HashMap<String, SoftReference<User>> _users;

	private static final Logger logger = Logger
			.getLogger(AbstractUserManager.class);

	public abstract void init() throws UserFileException;
	
	protected abstract File getUserpathFile();

	protected void createSiteopUser() throws UserFileException {
		User user = createUser("drftpd");
		user.setGroup("drftpd");
		user.setPassword("drftpd");
		user.getKeyedMap().setObject(UserManagement.RATIO, new Float(0));
		user.getKeyedMap().setObject(UserManagement.GROUPSLOTS, 0);
		user.getKeyedMap().setObject(UserManagement.LEECHSLOTS, 0);
		user.getKeyedMap().setObject(UserManagement.MAXLOGINS, 0);
		user.getKeyedMap().setObject(UserManagement.MAXLOGINSIP, 0);
		user.getKeyedMap().setObject(UserManagement.MAXSIMUP, 0);
		user.getKeyedMap().setObject(UserManagement.MAXSIMDN, 0);
		// user.getKeyedMap().setObject(Statistics.LOGINS,0);
		user.getKeyedMap().setObject(UserManagement.CREATED, new Date());
		user.getKeyedMap().setObject(UserManagement.LASTSEEN, new Date());
		user.getKeyedMap()
				.setObject(UserManagement.WKLY_ALLOTMENT, new Long(0));
		user.getKeyedMap().setObject(UserManagement.COMMENT, "Auto-Generated");
		user.getKeyedMap().setObject(UserManagement.IRCIDENT, "N/A");
		user.getKeyedMap().setObject(UserManagement.TAGLINE, "drftpd");
		user.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
		// user.getKeyedMap().setObject(Nuke.NUKED,0);
		// user.getKeyedMap().setObject(Nuke.NUKEDBYTES,new Long(0));

		try {
			user.addIPMask("*@127.0.0.1");
			user.addIPMask("*@0:0:0:0:0:0:0:1");
		} catch (DuplicateElementException e) {
		}

		try {
			user.addSecondaryGroup("siteop");
		} catch (DuplicateElementException e1) {
		}

		user.commit();
	}

	public User create(String username) throws UserFileException {
		try {
			getUserByName(username);
			// bad, .xml file already exists.
			throw new FileExistsException("User " +username+ " already exists");
		} catch (IOException e) {
			// bad, some I/O error ocurred.
			throw new UserFileException(e);
		} catch (NoSuchUserException e) {
			// good, no such user was found. create it!
		}

		User user = createUser(username);
		user.commit();

		return user;
	}

	protected abstract User createUser(String username);

	/**
	 * final for now to remove duplicate implementations
	 */
	public void delete(String username) {
		if (!getUserFile(username).delete())
			throw new RuntimeException(new PermissionDeniedException());
		_users.remove(username);
	}

	protected abstract File getUserFile(String username);

	public Collection getAllGroups() {
		Collection users = getAllUsers();
		ArrayList<String> ret = new ArrayList<String>();

		for (Iterator iter = users.iterator(); iter.hasNext();) {
			User myUser = (User) iter.next();
			Collection myGroups = myUser.getGroups();

			for (Iterator iterator = myGroups.iterator(); iterator.hasNext();) {
				String myGroup = (String) iterator.next();

				if (!ret.contains(myGroup)) {
					ret.add(myGroup);
				}
			}

			if (!ret.contains(myUser.getGroup())) {
				ret.add(myUser.getGroup());
			}
		}

		return ret;
	}

	/**
	 * Get all user names in the system.
	 */
	public abstract Collection<User> getAllUsers();
	
	public Collection getAllUsersByGroup(String group) {
		Collection<User> c = new ArrayList<User>();

		for (Iterator iter = getAllUsers().iterator(); iter.hasNext();) {
			User user = (User) iter.next();

			if (user.isMemberOf(group)) {
				c.add(user);
			}
		}

		return c;
	}

	public User getUserByNameIncludeDeleted(String username)
			throws NoSuchUserException, UserFileException {
		User user = getUserByNameUnchecked(username);
		return user;
	}

	public User getUserByName(String username) throws NoSuchUserException,
			UserFileException {
		User user = getUserByNameIncludeDeleted(username);

		if (user.isDeleted()) {
			throw new NoSuchUserException(user.getName() + " is deleted");
		}

		return user;
	}

	public static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public User getUserByIdent(String ident) throws NoSuchUserException,
			UserFileException {
		for (Iterator iter = getAllUsers().iterator(); iter.hasNext();) {
			User user = (User) iter.next();
			try {
				String uident = (String) user.getKeyedMap().getObject(
						UserManagement.IRCIDENT);
				if (uident.equals(ident)) {
					return user;
				}
			} catch (KeyNotFoundException e1) {
			}
		}
		throw new NoSuchUserException("No user found with ident = " + ident);
	}

	public abstract User getUserByNameUnchecked(String username)
			throws NoSuchUserException, UserFileException;

	public void remove(User user) {
		_users.remove(user.getName());
	}

	protected void rename(User oldUser, String newUsername)
			throws UserExistsException, UserFileException {
		if (!_users.containsKey(newUsername)) {
			try {
				getUserByNameUnchecked(newUsername);
			} catch (NoSuchUserException e) {
				_users.remove(oldUser.getName());
				_users.put(newUsername, new SoftReference<User>(oldUser));
				return;
			}
		}

		throw new UserExistsException("user " + newUsername + " exists");
	}

	public void saveAll() {
		logger.info("Saving userfiles");
		for (User user : getAllUsers()) {
			try {
				user.commit();
			} catch (UserFileException e) {
				logger.error("Error saving: '"+user.getName()+"'", e);
				continue;
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.drftpd.master.cron.TimeEventInterface#resetDay(java.util.Date)
	 */
	public void resetDay(Date d) {
		for (User user : getAllUsers()) {			
			user.resetDay(d);
			try {
				user.commit();
			} catch (UserFileException e) {
				logger.error("Error saving: '"+user.getName()+"'", e);
				continue;
			}
		}
	} 

	/* (non-Javadoc)
	 * @see org.drftpd.master.cron.TimeEventInterface#resetHour(java.util.Date)
	 */
	public void resetHour(Date d) {
		for (User user : getAllUsers()) {			
			user.resetHour(d);
			try {
				user.commit();
			} catch (UserFileException e) {
				logger.error("Error saving: '"+user.getName()+"'", e);
				continue;
			}
		}
	} 


	/* (non-Javadoc)
	 * @see org.drftpd.master.cron.TimeEventInterface#resetMonth(java.util.Date)
	 */
	public void resetMonth(Date d) {

		for (User user : getAllUsers()) {			
			user.resetMonth(d);	
			try {
				user.commit();
			} catch (UserFileException e) {
				logger.error("Error saving: '"+user.getName()+"'", e);
				continue;
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.drftpd.master.cron.TimeEventInterface#resetWeek(java.util.Date)
	 */
	public void resetWeek(Date d) {

		for (User user : getAllUsers()) {			
			user.resetWeek(d);	
			try {
				user.commit();
			} catch (UserFileException e) {
				logger.error("Error saving: '"+user.getName()+"'", e);
				continue;
			}
		}

	}

	/* (non-Javadoc)
	 * @see org.drftpd.master.cron.TimeEventInterface#resetYear(java.util.Date)
	 */
	public void resetYear(Date d) {

		for (User user : getAllUsers()) {			
			user.resetYear(d);		
			try {
				user.commit();
			} catch (UserFileException e) {
				logger.error("Error saving: '"+user.getName()+"'", e);
				continue;
			}
		}
	}
}
