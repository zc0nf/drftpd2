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
package net.sf.drftpd.master.command.plugins;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserManager;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.drftpd.tests.DummyBaseFtpConnection;
import org.drftpd.tests.DummyUser;
import org.drftpd.tests.DummyUserManager;

/**
 * @author mog
 * @version $Id: LoginTest.java,v 1.2 2004/05/31 02:47:16 mog Exp $
 */
public class LoginTest extends TestCase {
	private DummyUserManager _userManager;

	private DummyBaseFtpConnection _conn;

	private Login _login;

	private static final Logger logger = Logger.getLogger(LoginTest.class);

	public LoginTest(String name) {
		super(name);
	}

	public static TestSuite suite() {
		return new TestSuite(LoginTest.class);
	}

	public void setUp() {
		BasicConfigurator.configure();
		_login = (Login) new Login().initialize(null, null);
		_conn = new DummyBaseFtpConnection(null);
		_conn.setConnectionManager(new ConnectionManager() {
			public FtpReply canLogin(BaseFtpConnection baseconn, User user) {
				return null;
			}
			public FtpConfig getConfig() {
				try {
					return new FtpConfig(new Properties(), null, this) {
						public List getBouncerIps() {
							try {
								return Collections.singletonList(InetAddress.getByName("10.0.0.1"));
							} catch (UnknownHostException e) {
								throw new RuntimeException(e);
							}
						}
					};
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			public UserManager getUserManager() {
				return _userManager;
			}
		});
		_userManager = new DummyUserManager();
	}

	public void testIDNT() throws UnhandledCommandException, DuplicateElementException, UnknownHostException {
		_conn.setClientAddress(InetAddress.getByName("10.0.0.2"));
		_conn.setRequest(new FtpRequest("IDNT user@127.0.0.1:localhost"));
		FtpReply reply;
		reply = _login.execute(_conn);
		assertNotNull(reply);
		assertEquals(530, reply.getCode());
		assertNull(_login._idntAddress);

		_conn.setClientAddress(InetAddress.getByName("10.0.0.1"));
		reply = _login.execute(_conn);
		assertNull(String.valueOf(reply), reply);

		DummyUser user = new DummyUser("myuser");
		_userManager.setUser(user);
		_conn.setUserManager(_userManager);
		_conn.setRequest(new FtpRequest("USER myuser"));

		user.addIPMask("*@127.0.0.0");
		reply = _login.execute(_conn);
		assertEquals(530, reply.getCode());
		assertNull(_conn.getUserNull());
		logger.debug(reply.toString());

		user.addIPMask("*@localhost");
		reply = _login.execute(_conn);
		assertEquals(331, reply.getCode());
		assertEquals(user, _conn.getUserNull());
		logger.debug(reply.toString());
	}
}
