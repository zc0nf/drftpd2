package net.sf.drftpd.master.usermanager;
import java.util.Collection;
import java.util.List;

import net.sf.drftpd.master.ConnectionManager;

/**
 * This is the base class of all the user manager classes.
 * If we want to add a new user manager, we have to override
 * this class.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @version $Id: UserManager.java,v 1.19 2003/12/23 13:38:20 mog Exp $
 */
public interface UserManager {

	/**
	 * A kind of constuctor defined in the interface for allowing the usermanager
	 * to get a hold of the ConnectionManager object for dispatching events etc.
	 */
	public abstract void init(ConnectionManager mgr);
	public abstract User create(String username) throws UserFileException;
	/**
	 * User existance check.
	 *
	 * @param name user name
	 */
	public abstract boolean exists(String name);
	public abstract Collection getAllGroups() throws UserFileException;

	/**
	 * Get all user names in the system.
	 */
	public abstract List getAllUsers() throws UserFileException;
	public abstract Collection getAllUsersByGroup(String group)
		throws UserFileException;
	

	/**
	 * Get user by name.
	 */
	//TODO garbage collected Map of users.
	public abstract User getUserByName(String name)
		throws NoSuchUserException, UserFileException;
	public User getUserByNameUnchecked(String username) throws NoSuchUserException, UserFileException;
	public abstract void saveAll() throws UserFileException;

}
