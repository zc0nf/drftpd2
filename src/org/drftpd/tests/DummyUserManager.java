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
package org.drftpd.tests;

import net.sf.drftpd.master.ConnectionManager;

import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import java.util.Collection;
import java.util.Collections;


/**
 * @author mog
 * @version $Id: DummyUserManager.java,v 1.7 2004/11/08 18:39:31 mog Exp $
 */
public class DummyUserManager extends AbstractUserManager {
    private User _user;

    public DummyUserManager() {
        super();
    }

    public User createUser(String username) {
        throw new UnsupportedOperationException();
    }

    public User create(String username) throws UserFileException {
        DummyUser u = new DummyUser(username, this);
        add(u);

        return u;
    }

    public void delete(String string) {
        throw new UnsupportedOperationException();
    }

    public Collection getAllGroups() throws UserFileException {
        throw new UnsupportedOperationException();
    }

    public void add(User user) {
        _users.put(user.getUsername(), user);
    }

    public User getUserByNameUnchecked(String username)
        throws NoSuchUserException, UserFileException {
        throw new UnsupportedOperationException();
    }

    public User getUserByName(String username) {
        return _user;
    }

    public void init(ConnectionManager mgr) {
        throw new UnsupportedOperationException();
    }

    public void saveAll() throws UserFileException {
        throw new UnsupportedOperationException();
    }

    public void setUser(User user) {
        _user = user;
    }

    public Collection getAllUsers() throws UserFileException {
        return Collections.unmodifiableCollection(_users.values());
    }
}
