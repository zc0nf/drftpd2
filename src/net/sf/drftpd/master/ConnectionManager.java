package net.sf.drftpd.master;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.mirroring.JobManager;
import net.sf.drftpd.permission.GlobRMIServerSocketFactory;
import net.sf.drftpd.slave.SlaveImpl;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * @version $Id: ConnectionManager.java,v 1.82 2004/01/13 20:30:53 mog Exp $
 */
public class ConnectionManager {
	public static final int idleTimeout = 300;

	private static final Logger logger =
		Logger.getLogger(ConnectionManager.class.getName());

	public static void main(String args[]) {
		System.out.println(SlaveImpl.VERSION + " master server starting.");
		System.out.println("http://drftpd.sourceforge.net");

		try {
			String cfgFileName;
			if (args.length >= 1) {
				cfgFileName = args[0];
			} else {
				cfgFileName = "drftpd.conf";
			}
			String slaveCfgFileName;
			if (args.length >= 2) {
				slaveCfgFileName = args[1];
			} else {
				slaveCfgFileName = "slave.conf";
			}

			/** load master config **/
			Properties cfg = new Properties();
			cfg.load(new FileInputStream(cfgFileName));

			PropertyConfigurator.configure(cfg);

			/** load slave config **/
			Properties slaveCfg; //used as a flag for if localslave=true
			if (cfg
				.getProperty("master.localslave", "false")
				.equalsIgnoreCase("true")) {
				slaveCfg = new Properties();
				slaveCfg.load(new FileInputStream(slaveCfgFileName));
			} else {
				slaveCfg = null;
			}

			logger.info("Starting ConnectionManager");
			ConnectionManager mgr =
				new ConnectionManager(
					cfg,
					slaveCfg,
					cfgFileName,
					slaveCfgFileName);
			/** listen for connections **/
			ServerSocket server =
				new ServerSocket(
					Integer.parseInt(
						FtpConfig.getProperty(cfg, "master.port")));
			logger.info("Listening on port " + server.getLocalPort());
			while (true) {
				mgr.start(server.accept());
			}
			//catches subclasses of Error and Exception
		} catch (Throwable th) {
			logger.error("", th);
			System.exit(0);
			return;
		}
	}

	private static String[] scrubArgs(String[] args) {
		String ret[] = new String[args.length - 1];
		System.arraycopy(args, 1, ret, 0, ret.length);
		return ret;
	}
	private FtpConfig _config;
	private JobManager _jm;
	private List _conns = Collections.synchronizedList(new ArrayList());

	private ArrayList _ftpListeners = new ArrayList();
	private String _shutdownMessage = null;
	//allow package classes for inner classes without use of synthetic methods
	private SlaveManagerImpl _slaveManager;
	private Timer _timer;
	private UserManager _usermanager;
	public ConnectionManager(
		Properties cfg,
		Properties slaveCfg,
		String cfgFileName,
		String slaveCfgFileName) {
		try {
			_config = new FtpConfig(cfg, cfgFileName, this);
		} catch (Throwable ex) {
			throw new FatalException(ex);
		}

		List rslaves = SlaveManagerImpl.loadRSlaves();
		GlobRMIServerSocketFactory ssf =
			new GlobRMIServerSocketFactory(rslaves);

		/** register slavemanager **/
		try {
			_slaveManager = new SlaveManagerImpl(cfg, rslaves, ssf, this);
		} catch (RemoteException e) {
			throw new FatalException(e);
		}

		if (slaveCfg != null) {
			try {
				new SlaveImpl(slaveCfg);
			} catch (RemoteException ex) {
				throw new FatalException(ex);
			}
		}

		try {
			_usermanager =
				(UserManager) Class
					.forName(FtpConfig.getProperty(cfg, "master.usermanager"))
					.newInstance();
			// if the below method is not run, JSXUserManager fails when trying to do a reset() on the user logging in
			_usermanager.init(this);
		} catch (Exception e) {
			throw new FatalException(
				"Cannot create instance of usermanager, check master.usermanager in drftpd-0.7.conf",
				e);
		}

		for (int i = 1;; i++) {
			String classname = cfg.getProperty("plugins." + i);
			if (classname == null)
				break;
			try {
				FtpListener ftpListener =
					(FtpListener) Class.forName(classname).newInstance();
				addFtpListener(ftpListener);
				if (ftpListener instanceof JobManager)
					_jm = (JobManager) ftpListener;
			} catch (Exception e) {
				throw new FatalException("Error loading plugins", e);
			}
		}
		_commandManagerFactory = new CommandManagerFactory(this);

		//		if (cfg.getProperty("irc.enabled", "false").equals("true")) {
		//			try {
		//				addFtpListener(
		//					new IRCListener(this, getConfig(), new String[0]));
		//			} catch (Exception e2) {
		//				throw new FatalException(e2);
		//			}
		//		}

		//addFtpListener(new RaceStatistics());

		_timer = new Timer();
		TimerTask timerLogoutIdle = new TimerTask() {
			public void run() {
				timerLogoutIdle();
			}
		};
		//run every 10 seconds
		_timer.schedule(timerLogoutIdle, 10 * 1000, 10 * 1000);

		TimerTask timerSave = new TimerTask() {
			public void run() {
				getSlaveManager().saveFilelist();
				try {
					getUserManager().saveAll();
				} catch (UserFileException e) {
					logger.log(Level.FATAL, "Error saving all users", e);
				}
			}
		};
		//run every hour 
		_timer.schedule(timerSave, 60 * 60 * 1000, 60 * 60 * 1000);
	}

	public Timer getTimer() {
		return _timer;
	}

	/**
	 * Calls init(this) on the argument
	 */
	public void addFtpListener(FtpListener listener) {
		listener.init(this);
		_ftpListeners.add(listener);
	}
	public FtpListener getFtpListener(Class clazz)
		throws ObjectNotFoundException {
		for (Iterator iter = getFtpListeners().iterator(); iter.hasNext();) {
			FtpListener listener = (FtpListener) iter.next();
			if (listener.getClass().equals(clazz))
				return listener;
		}
		throw new ObjectNotFoundException();
	}

	public void dispatchFtpEvent(Event event) {
		logger.debug("Dispatching " + event + " to " + getFtpListeners());
		for (Iterator iter = getFtpListeners().iterator(); iter.hasNext();) {
			try {
				FtpListener handler = (FtpListener) iter.next();
				handler.actionPerformed(event);
			} catch (RuntimeException e) {
				logger.log(Level.WARN, "RuntimeException dispatching event", e);
			}
		}
	}

	public FtpConfig getConfig() {
		return _config;
	}

	/**
	 * returns a <code>Collection</code> of current connections
	 */
	public List getConnections() {
		return _conns;
	}

	public List getFtpListeners() {
		return _ftpListeners;
	}
	public String getShutdownMessage() {
		return _shutdownMessage;
	}

	public SlaveManagerImpl getSlaveManager() {
		return _slaveManager;
	}

	public UserManager getUserManager() {
		return _usermanager;
	}
	public boolean isShutdown() {
		return _shutdownMessage != null;
	}

	public void remove(BaseFtpConnection conn) {
		if (!_conns.remove(conn)) {
			throw new RuntimeException("connections.remove() returned false.");
		}
		if (isShutdown() && _conns.isEmpty()) {
			_slaveManager.saveFilelist();
			try {
				getUserManager().saveAll();
			} catch (UserFileException e) {
				logger.log(Level.WARN, "Failed to save all userfiles", e);
			}
			logger.info("Shutdown complete, exiting");
			System.runFinalization();
			System.exit(0);
		}
	}
	public void shutdown(String message) {
		this._shutdownMessage = message;
		Collection conns = getConnections();
		synchronized (conns) {
			for (Iterator iter = getConnections().iterator();
				iter.hasNext();
				) {
				((BaseFtpConnection) iter.next()).stop(message);
			}
		}
		dispatchFtpEvent(new MessageEvent("SHUTDOWN", message));
	}
	private CommandManagerFactory _commandManagerFactory;
	public void start(Socket sock) throws IOException {
		BaseFtpConnection conn = new BaseFtpConnection(this, sock);
		_conns.add(conn);
		conn.start();
	}

	public void timerLogoutIdle() {
		long currTime = System.currentTimeMillis();
		synchronized (_conns) {
			for (Iterator i = _conns.iterator(); i.hasNext();) {
				BaseFtpConnection conn = (BaseFtpConnection) i.next();

				int idle = (int) ((currTime - conn.getLastActive()) / 1000);
				int maxIdleTime;
				try {
					maxIdleTime = conn.getUser().getIdleTime();
					if (maxIdleTime == 0)
						maxIdleTime = idleTimeout;
				} catch (NoSuchUserException e) {
					maxIdleTime = idleTimeout;
				}

				if (!conn.isExecuting() && idle >= maxIdleTime) {
					// idle time expired, logout user.
					conn.stop("Idle time expired: " + maxIdleTime + "s");
				}
			}
		}
	}

	public CommandManagerFactory getCommandManagerFactory() {
		return _commandManagerFactory;
	}

	public JobManager getJobManager() {
		return _jm;
	}

	public FtpReply canLogin(BaseFtpConnection baseconn) {
		User user = baseconn.getUserNull();
		int count = getConfig().getMaxUsersTotal();
		//Math.max if the integer wraps
		if (user.isExempt())
			count = Math.max(count, count + getConfig().getMaxUsersExempt());
		int userCount = 0;
		int ipCount = 0;

		// not >= because baseconn is already included
		if (_conns.size() > count)
			return new FtpReply(550, "The site is full, try again later.");
		synchronized (_conns) {
			for (Iterator iter = _conns.iterator(); iter.hasNext();) {
				BaseFtpConnection tempConnection =
					(BaseFtpConnection) iter.next();
				try {
					User tempUser = tempConnection.getUser();
					if (tempUser.getUsername().equals(user.getUsername())) {
						userCount++;
						if (tempConnection
							.getClientAddress()
							.equals(baseconn.getClientAddress())) {
							ipCount++;
						}
					}
				} catch (NoSuchUserException ex) {
					// do nothing, we found our current connection, baseconn = tempConnection
				}
			}
		}
		if (user.getMaxLoginsPerIP() > 0 && ipCount > user.getMaxLoginsPerIP())
			return new FtpReply(
				530,
				"Sorry, your maximum number of connections from this IP ("
					+ user.getMaxLoginsPerIP()
					+ ") has been reached.");
		if (user.getMaxLogins() > 0 && userCount > user.getMaxLogins())
			return new FtpReply(
				530,
				"Sorry, your account is restricted to "
					+ user.getMaxLogins()
					+ " simultaneous logins.");
		return null; // everything passed
	}
}
