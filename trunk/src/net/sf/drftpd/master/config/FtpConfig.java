/*
 * Created on 2003-jul-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerFormat;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class FtpConfig {
	private int _maxUsersExempt;
	private int _maxUsersTotal = Integer.MAX_VALUE;
	private String loginPrompt = 
		"This program is free software; you can redistribute it and/or" +
		" modify it under the terms of the GNU General Public License.  " +
		"Distributed FTP Daemon http://drftpd.mog.se" +
		" : Service ready for new user.";
	private static Logger logger = Logger.getLogger(FtpConfig.class);
	private ArrayList _creditcheck;

	private ArrayList _creditloss;
	private ArrayList _delete;
	private ArrayList _deleteown;
	private ArrayList _dirlog;
	private ArrayList _download;
//	private ArrayList _eventplugin;
	private ArrayList _hideinwho;
	private ArrayList _makedir;
	private ArrayList _msgpath;
	private ArrayList _pre;
	private ArrayList _privpath;
	private ArrayList _rename;
	private ArrayList _renameown;
	private ArrayList _request;
	private ArrayList _upload;

	String cfgFileName;
	private ConnectionManager connManager;
	private long freespaceMin;
	private String newConf = "perms.conf";
	private Map replacerFormats;

	/**
	 * Constructor that allows reusing of cfg object
	 * 
	 */
	public FtpConfig(
		Properties cfg,
		String cfgFileName,
		ConnectionManager connManager)
		throws IOException {
		this.cfgFileName = cfgFileName;
		loadConfig(cfg, connManager);
	}

	public boolean checkDelete(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _delete.iterator());
	}

	public boolean checkDeleteOwn(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _deleteown.iterator());
	}

	public boolean checkDirLog(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _dirlog.iterator());
	}
	/**
	 * Also checks privpath for permission
	 * @return true if fromUser is allowed to download the file path
	 */
	public boolean checkDownload(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _download.iterator());
	}

	/**
	 * @return true if fromUser should be hidden
	 */
	public boolean checkHideInWho(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _hideinwho.iterator());
		//		for (Iterator iter = _hideinwhos.iterator(); iter.hasNext();) {
		//			StringPathPermission perm = (PathPermission) iter.next();
		//			if (perm.checkPath(path)) {
		//				return !perm.check(fromUser);
		//			}
		//		}
		//		return false;
	}
	/**
	 * @return true if fromUser is allowed to mkdir in path
	 */
	public boolean checkMakeDir(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _makedir.iterator());
		//		for (Iterator iter = _makedir.iterator(); iter.hasNext();) {
		//			PathPermission perm = (PathPermission) iter.next();
		//			if(perm.checkPath(path)) {
		//				return perm.check(fromUser);
		//			}
		//		}
		//		return true;
	}

	public int getMaxUsersTotal() {
		return _maxUsersTotal;
	}
	public int getMaxUsersExempt() {
		return _maxUsersExempt;
	}
	public String getLoginPrompt() {
		return loginPrompt;
	}
	private boolean checkPathPermssion(
		User fromUser,
		LinkedRemoteFile path,
		Iterator iter) {
		while (iter.hasNext()) {
			PathPermission perm = (PathPermission) iter.next();
			if (perm.checkPath(path)) {
				return perm.check(fromUser);
			}
		}
		return false;
	}

	/**
	 * @return true if fromUser is allowed to pre in path
	 */
	public boolean checkPre(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _pre.iterator());
	}

	/**
	 * @return true if user fromUser is allowed to see path
	 */
	public boolean checkPrivPath(User fromUser, LinkedRemoteFile path) {
		for (Iterator iter = _privpath.iterator(); iter.hasNext();) {
			PathPermission perm = (PathPermission) iter.next();
			if (perm.checkPath(path)) {
				// path matched, if user is in ACL he's allowed access
				return perm.check(fromUser);
			}
		}
		// default is to allow access
		return true;
	}

	public boolean checkRename(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _rename.iterator());
	}

	public boolean checkRenameOwn(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _rename.iterator());
	}
	public boolean checkRequest(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _request.iterator());
	}
	/**
	 * @return true if fromUser is allowed to upload in directory path
	 */
	public boolean checkUpload(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _upload.iterator());
	}

	public void directoryMessage(
		FtpReply response,
		User user,
		LinkedRemoteFile dir) {

		for (Iterator iter = _msgpath.iterator(); iter.hasNext();) {
			MessagePathPermission perm = (MessagePathPermission) iter.next();
			if (perm.checkPath(dir)) {
				if (perm.check(user)) {
					perm.printMessage(response);
				}
			}
		}
	}

	public float getCreditLossRatio(LinkedRemoteFile path, User fromUser) {
		for (Iterator iter = _creditloss.iterator(); iter.hasNext();) {
			RatioPathPermission perm = (RatioPathPermission) iter.next();
			if (perm.checkPath(path)) {
				if (perm.check(fromUser)) {
					return perm.getRatio();
				} else {
					return fromUser.getRatio() == 0.0 ? 0 : 1;
				}
			}
		}
		//default credit loss ratio is 1
		return fromUser.getRatio() == 0.0 ? 0 : 1;
	}
	public long getFreespaceMin() {
		return freespaceMin;
	}

	public ReplacerFormat getReplacerFormat(String key) {
		ReplacerFormat ret = (ReplacerFormat) replacerFormats.get(key);
		if (ret == null)
			throw new NoSuchFieldError("No ReplacerFormat for " + key);
		return ret;
	}

	public SlaveManagerImpl getSlaveManager() {
		return this.connManager.getSlaveManager();
	}

	/**
	 * return true if file is visible + is readable by user
	 * @deprecated
	 */
	public boolean hasReadPermission(User user, LinkedRemoteFile directory) {
		return checkPrivPath(user, directory);
	}
	public void loadConfig(Properties cfg, ConnectionManager connManager)
		throws IOException {
		loadConfig2();
		try {
			replacerFormats =
				loadFormats(new FileInputStream("replacerformats.conf"));
		} catch (FormatterException e) {
			throw (IOException) new IOException().initCause(e);
		}
		this.connManager = connManager;
		this.freespaceMin = Bytes.parseBytes(cfg.getProperty("freespace.min"));
	}
	private void loadConfig2() throws IOException {
		ArrayList creditcheck = new ArrayList();
		ArrayList creditloss = new ArrayList();
		ArrayList delete = new ArrayList();
		ArrayList deleteown = new ArrayList();
		ArrayList dirlog = new ArrayList();
		ArrayList download = new ArrayList();
		ArrayList hideinwho = new ArrayList();
		ArrayList makedirs = new ArrayList();
		ArrayList msgpath = new ArrayList();
		//ArrayList eventplugin = new ArrayList();
		ArrayList pre = new ArrayList();
		ArrayList privpath = new ArrayList();
		ArrayList rename = new ArrayList();
		ArrayList renameown = new ArrayList();
		ArrayList request = new ArrayList();
		ArrayList upload = new ArrayList();

		LineNumberReader in = new LineNumberReader(new FileReader(newConf));
		String line;
		GlobCompiler globComiler = new GlobCompiler();
		while ((line = in.readLine()) != null) {
			StringTokenizer st = new StringTokenizer(line);
			if (!st.hasMoreTokens())
				continue;
			String command = st.nextToken();

			try {
				if (command.equals("privpath")) {
					privpath.add(
						new PatternPathPermission(
							globComiler.compile(st.nextToken()),
							makeUsers(st)));
				}
				// login_prompt <string>
				else if (command.equals("login_prompt")) {
					loginPrompt = line.substring(13);
				}
				//max_users <maxUsersTotal> <maxUsersExempt>
				else if (command.equals("max_users")) {
					_maxUsersTotal = Integer.parseInt(st.nextToken());
					_maxUsersExempt = Integer.parseInt(st.nextToken());					
				}
				//msgpath <path> <filename> <flag/=group/-user>
				else if (command.equals("msgpath")) {
					String path = st.nextToken();
					String messageFile = st.nextToken();
					msgpath.add(
						new MessagePathPermission(
							path,
							messageFile,
							makeUsers(st)));
				}
				//creditloss <multiplier> <path> <permissions>
				else if (command.equals("creditloss")) {
					float multiplier = Float.parseFloat(st.nextToken());

					String path = st.nextToken();
					Collection users = makeUsers(st);
					creditloss.add(
						new RatioPathPermission(multiplier, path, users));
				}
				//creditcheck <path> <ratio> [<-user|=group|flag> ...]
				else if (command.equals("creditcheck")) {
					float multiplier = Float.parseFloat(st.nextToken());
					String path = st.nextToken();
					Collection users = makeUsers(st);
					creditloss.add(
						new RatioPathPermission(multiplier, path, users));
				} else if (command.equals("dirlog")) {
					makePermission(dirlog, st);
				} else if (command.equals("hideinwho")) {
					makePermission(hideinwho, st);
				} else if (command.equals("makedir")) {
					makePermission(makedirs, st);
				} else if (command.equals("pre")) {
					makePermission(pre, st);
				} else if (command.equals("upload")) {
					makePermission(upload, st);
				} else if (command.equals("download")) {
					makePermission(download, st);
				} else if (command.equals("delete")) {
					makePermission(delete, st);
				} else if (command.equals("deleteown")) {
					makePermission(deleteown, st);
				} else if (command.equals("rename")) {
					makePermission(rename, st);
				} else if (command.equals("renameown")) {
					makePermission(renameown, st);
				} else if (command.equals("request")) {
					makePermission(request, st);
//				} else if (command.equals("plugin")) {
//					String clazz = st.nextToken();
//					ArrayList argsCollection = new ArrayList();
//					while (st.hasMoreTokens()) {
//						argsCollection.add(st.nextToken());
//					}
//					String args[] =
//						(String[]) argsCollection.toArray(new String[0]);
//					try {
//						Class SIG[] =
//							{
//								FtpConfig.class,
//								ConnectionManager.class,
//								String[].class };
//						Constructor met =
//							Class.forName(clazz).getConstructor(SIG);
//						Object obj =
//							met.newInstance(
//								new Object[] { this, connManager, args });
//						eventplugin.add(obj);
//					} catch (Throwable e) {
//						logger.log(Level.FATAL, "Error loading " + clazz, e);
//					}
				}
			} catch (Exception e) {
				logger.warn(
					"Exception when reading "
						+ newConf
						+ " line "
						+ in.getLineNumber(),
					e);
			}
		}

		creditcheck.trimToSize();
		_creditcheck = creditcheck;

		creditloss.trimToSize();
		_creditloss = creditloss;

		delete.trimToSize();
		_delete = delete;

		deleteown.trimToSize();
		_deleteown = deleteown;

		dirlog.trimToSize();
		_dirlog = dirlog;

		download.trimToSize();
		_download = download;

		hideinwho.trimToSize();
		_hideinwho = hideinwho;

		makedirs.trimToSize();
		_makedir = makedirs;

		msgpath.trimToSize();
		_msgpath = msgpath;

		pre.trimToSize();
		_pre = pre;

		privpath.trimToSize();
		_privpath = privpath;

//		eventplugin.trimToSize();
//		_eventplugin = eventplugin;

		rename.trimToSize();
		_rename = rename;

		renameown.trimToSize();
		_renameown = renameown;

		request.trimToSize();
		_request = request;

		upload.trimToSize();
		_upload = upload;

	}

	private Map loadFormats(InputStream in)
		throws FormatterException, IOException {
		Properties props = new Properties();
		props.load(in);
		Hashtable replacerFormats = new Hashtable();
		for (Iterator iter = props.entrySet().iterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			replacerFormats.put(
				(String) entry.getKey(),
				ReplacerFormat.createFormat((String) entry.getValue()));
		}
		return replacerFormats;
	}

	private void makePermission(ArrayList arr, StringTokenizer st)
		throws MalformedPatternException {
		arr.add(
			new PatternPathPermission(
				new GlobCompiler().compile(st.nextToken()),
				makeUsers(st)));
	}

	public static ArrayList makeUsers(StringTokenizer st) {
		ArrayList users = new ArrayList();
		while (st.hasMoreTokens()) {
			users.add(st.nextToken());
		}
		return users;
	}

	/**
	 * 
	 * @param cfg
	 * @throws NumberFormatException
	 */
	public void reloadConfig() throws FileNotFoundException, IOException {
		Properties cfg = new Properties();
		cfg.load(new FileInputStream(cfgFileName));
		loadConfig(cfg, connManager);
	}

}
