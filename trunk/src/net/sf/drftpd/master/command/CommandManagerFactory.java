package net.sf.drftpd.master.command;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;

import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * Istantiates the CommandManager instances that holds per-connection CommandHandlers.
 */
public class CommandManagerFactory {

	private ConnectionManager _connMgr;
	private Logger logger = Logger.getLogger(CommandManagerFactory.class);
	/**
	 * Class => CommandHandler
	 */
	private Hashtable _hnds;
	/**
	 * String=> Class
	 */
	private Hashtable _cmds;
	public void reload() {
		unload();
		load();
	}
	private void unload() {
		for (Iterator iter = _hnds.values().iterator(); iter.hasNext();) {
			((CommandHandler) iter.next()).unload();
		}
	}

	private void load() {
		Hashtable cmds = new Hashtable();
		Hashtable hnds = new Hashtable();
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("commandhandlers.conf"));
		} catch (IOException e) {
			throw new FatalException("Error loading commandhandlers.conf", e);
		}
		//		URLClassLoader classLoader;
		//		try {
		//			classLoader =
		//				URLClassLoader.newInstance(
		//					new URL[] {
		//						new URL("file:classes/"),
		//						new URL("file:lib/log4j-1.2.8.jar")});
		//		} catch (MalformedURLException e1) {
		//			throw new RuntimeException(e1);
		//		}
		for (Iterator iter = props.entrySet().iterator(); iter.hasNext();) {
			try {
				Map.Entry entry = (Map.Entry) iter.next();

				Class hndclass = Class.forName((String) entry.getValue());

//				Class hndclass =
//					Class.forName(
//						(String) entry.getValue(),
//						false,
//						classLoader);
				CommandHandler hndinstance =
					(CommandHandler) hnds.get(hndclass);
				if (hndinstance == null) {
					hndinstance = (CommandHandler) hndclass.newInstance();
					hndinstance.load(this);
					hnds.put(hndclass, hndinstance);
				}
				String cmd = (String) entry.getKey();
				if (cmds.containsKey(cmd))
					throw new ObjectExistsException(cmd + " is already mapped");
				cmds.put(cmd, hndclass);
			} catch (Exception e) {
				throw new FatalException("", e);
			}
		}
		_cmds = cmds;
		_hnds = hnds;
	}

	public CommandManagerFactory(ConnectionManager connMgr) {
		_connMgr = connMgr;
		//		Login login = new Login();
		//		handlers = new Hashtable();
		//		handlers.put("USER", login);
		//		handlers.put("PASS", login);
		//		handlers = new ArrayList();
		//		handlers.add(new Login());
		//		handlers.add(new Dir());
		//		handlers.add(new List());
		//		handlers.add(new DataConnectionHandler());
		//		handlers.add(new Search());
		//		handlers.add(new UserManagment());
		//_conn = conn;
		//login.init(conn);
		//Hashtable handlers = new Hashtable();
		load();
	}

	public CommandManager initialize(BaseFtpConnection conn) {
		CommandManager mgr = new CommandManager(conn, this);
		return mgr;
	}

	/**
	 * Class => CommandHandler
	 */
	public Hashtable getHandlersMap() {
		return _hnds;
	}

	/**
	 * String=> Class
	 */
	public Hashtable getCommandsMap() {
		return _cmds;
	}
	public CommandHandler getHandler(Class clazz)
		throws ObjectNotFoundException {
		CommandHandler ret = (CommandHandler) _hnds.get(clazz);
		if (ret == null)
			throw new ObjectNotFoundException();
		return ret;
		//		for (Iterator iter = hnds.iterator(); iter.hasNext();) {
		//			CommandHandler handler = (CommandHandler) iter.next();
		//			if (handler.getClass().equals(clazz))
		//				return handler;
		//		}
		//		throw new ObjectNotFoundException();
	}

	/**
	 * 
	 */
	public ConnectionManager getConnectionManager() {
		return _connMgr;
	}
}
