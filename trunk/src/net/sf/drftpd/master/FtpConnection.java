package net.sf.drftpd.master;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.drftpd.AsciiOutputStream;
import net.sf.drftpd.Bytes;
import net.sf.drftpd.Checksum;
import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.event.irc.UploaderPosition;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.queues.NukeLog;
import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.remotefile.DirectoryRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import socks.server.Ident;

/**
 * This class handles each ftp connection. Here all the ftp command
 * methods take two arguments - a FtpRequest and a PrintWriter object. 
 * This is the main backbone of the ftp server.
 * <br>
 * The ftp command method signature is: 
 * <code>public void doXYZ(FtpRequest request, PrintWriter out)</code>.
 * <br>
 * Here <code>XYZ</code> is the capitalized ftp command. 
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */

public class FtpConnection extends BaseFtpConnection {
	private final static SimpleDateFormat DATE_FMT =
		new SimpleDateFormat("yyyyMMddHHmmss.SSS");
	private static Logger logger =
		Logger.getLogger(FtpConnection.class.getName());

	/**
	 * Used by doSITE_DUPE()
	 */
	public static void findFile(
		FtpResponse response,
		LinkedRemoteFile dir,
		Collection searchstrings,
		boolean files,
		boolean dirs) {
		for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (file.isDirectory()) {
				findFile(response, file, searchstrings, files, dirs);
			}
			if (dirs && file.isDirectory() || files && file.isFile()) {
				for (Iterator iterator = searchstrings.iterator();
					iterator.hasNext();
					) {
					String searchstring = (String) iterator.next();
					if (file.getName().toLowerCase().indexOf(searchstring) != -1) {
						response.addComment(file.getPath());
						break;
					}
				}
			}
		}
	}

	private static void nukeRemoveCredits(
		LinkedRemoteFile nukeDir,
		Hashtable nukees) {
		for (Iterator iter = nukeDir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			String owner = file.getUsername();
			Long total = (Long) nukees.get(owner);
			if (total == null)
				total = new Long(0);
			total = new Long(total.longValue() + file.length());
			nukees.put(owner, total);
		}
	}

	private NukeLog _nukelog;
	// just set mstRenFr to null instead of this extra boolean?
	//private boolean mbRenFr = false;
	private LinkedRemoteFile _renameFrom = null;

	// command state specific temporary variables

	//unneeded? "mlSkipLen == 0" is almost the same thing.
	//private boolean mbReset = false;
	private long resumePosition = 0;

	//	public final static String ANONYMOUS = "anonymous";

	private char type = 'A';

	//	private boolean mbUser = false;
	//private boolean mbPass = false;
	private UserManager userManager;
	private short xdupe = 0;
	public FtpConnection(
		Socket sock,
		UserManager userManager,
		SlaveManagerImpl slaveManager,
		LinkedRemoteFile root,
		ConnectionManager connManager,
		NukeLog nukelog,
		Writer debugLog) {

		super(connManager, sock, debugLog);
		this.userManager = userManager;
		this.slaveManager = slaveManager;

		this.setCurrentDirectory(root);
		this._nukelog = nukelog;
	}

	////////////////////////////////////////////////////////////
	/////////////////   all the FTP handlers   /////////////////
	////////////////////////////////////////////////////////////
	/**
	 * <code>ABOR &lt;CRLF&gt;</code><br>
	 *
	 * This command tells the server to abort the previous FTP
	 * service command and any associated transfer of data.
	 * No action is to be taken if the previous command
	 * has been completed (including data transfer).  The control
	 * connection is not to be closed by the server, but the data
	 * connection must be closed.  
	 * Current implementation does not do anything. As here data 
	 * transfers are not multi-threaded. 
	 */
	public void doABOR(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();
		//mDataConnection.reset();
		out.print(FtpResponse.RESPONSE_226_CLOSING_DATA_CONNECTION);
		return;
	}

	/**
	 * <code>APPE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to accept the data
	 * transferred via the data connection and to store the data in
	 * a file at the server site.  If the file specified in the
	 * pathname exists at the server site, then the data shall be
	 * appended to that file; otherwise the file specified in the
	 * pathname shall be created at the server site.
	 */
	//TODO implement APPE
	/*
	 public void doAPPE(FtpRequest request, PrintWriter out) {
	    
	     // reset state variables
	     resetState();
	     
	     // argument check
	     if(!request.hasArgument()) {
	        out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
	        return;  
	     }
	     
	     // get filenames
	     String fileName = request.getArgument();
	     fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
	     String physicalName = user.getVirtualDirectory().getPhysicalName(fileName);
	     File requestedFile = new File(physicalName);
	     String args[] = {fileName};
	     
	     // check permission
	     if(!user.getVirtualDirectory().hasWritePermission(physicalName, true)) {
	         out.write(ftpStatus.getResponse(450, request, user, args));
	         return;
	     }
	     
	     // now transfer file data
	     out.write(ftpStatus.getResponse(150, request, user, args));
	     InputStream is = null;
	     OutputStream os = null;
	     try {
	         Socket dataSoc = mDataConnection.getDataSocket();
	         if (dataSoc == null) {
	              out.write(ftpStatus.getResponse(550, request, user, args));
	              return;
	         }
	         
	         is = dataSoc.getInputStream();
	         RandomAccessFile raf = new RandomAccessFile(requestedFile, "rw");
	         raf.seek(raf.length());
	         os = user.getOutputStream( new FileOutputStream(raf.getFD()) );
	         
	         StreamConnector msc = new StreamConnector(is, os);
	         msc.setMaxTransferRate(user.getMaxUploadRate());
	         msc.setObserver(this);
	         msc.connect();
	         
	         if(msc.hasException()) {
	             out.write(ftpStatus.getResponse(451, request, user, args));
	         }
	         else {
	             mConfig.getStatistics().setUpload(requestedFile, user, msc.getTransferredSize());
	         }
	         
	         out.write(ftpStatus.getResponse(226, request, user, args));
	     }
	     catch(IOException ex) {
	         out.write(ftpStatus.getResponse(425, request, user, args));
	     }
	     finally {
	     try {
		 is.close();
		 os.close();
		 mDataConnection.reset(); 
	     } catch(Exception ex) {
		 ex.printStackTrace();
	     }
	     }
	 }
	*/

	/**
	 *    AUTHENTICATION/SECURITY MECHANISM (AUTH)
	
	  The argument field is a Telnet string identifying a supported
	  mechanism.  This string is case-insensitive.  Values must be
	  registered with the IANA, except that values beginning with "X-"
	  are reserved for local use.
	
	  If the server does not recognize the AUTH command, it must respond
	  with reply code 500.  This is intended to encompass the large
	  deployed base of non-security-aware ftp servers, which will
	  respond with reply code 500 to any unrecognized command.  If the
	  server does recognize the AUTH command but does not implement the
	  security extensions, it should respond with reply code 502.
	
	  If the server does not understand the named security mechanism, it
	  should respond with reply code 504.
	
	  If the server is not willing to accept the named security
	  mechanism, it should respond with reply code 534.
	
	  If the server is not able to accept the named security mechanism,
	  such as if a required resource is unavailable, it should respond
	  with reply code 431.
	
	  If the server is willing to accept the named security mechanism,
	  but requires security data, it must respond with reply code 334.
	
	  If the server is willing to accept the named security mechanism,
	  and does not require any security data, it must respond with reply
	  code 234.
	
	  If the server is responding with a 334 reply code, it may include
	  security data as described in the next section.
	
	  Some servers will allow the AUTH command to be reissued in order
	  to establish new authentication.  The AUTH command, if accepted,
	  removes any state associated with prior FTP Security commands.
	  The server must also require that the user reauthorize (that is,
	  reissue some or all of the USER, PASS, and ACCT commands) in this
	  case (see section 4 for an explanation of "authorize" in this
	  context).
	  
	  @see http://www.rfc-editor.org/rfc/rfc2228.txt
	 */
	public void doAUTH(FtpRequest request, PrintWriter out) {
		out.print(FtpResponse.RESPONSE_502_COMMAND_NOT_IMPLEMENTED);
		return;
	}
	/**
	 * <code>CDUP &lt;CRLF&gt;</code><br>
	 *
	 * This command is a special case of CWD, and is included to
	 * simplify the implementation of programs for transferring
	 * directory trees between operating systems having different
	 * syntaxes for naming the parent directory.  The reply codes
	 * shall be identical to the reply codes of CWD.      
	 */
	public void doCDUP(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// change directory
		try {
			setCurrentDirectory(currentDirectory.getParentFile());
		} catch (FileNotFoundException ex) {
		}

		FtpResponse response =
			new FtpResponse(
				200,
				"Directory changed to " + currentDirectory.getPath());
		out.print(response);
	}

	/**
	 * <code>CWD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command allows the user to work with a different
	 * directory for file storage or retrieval without
	 * altering his login or accounting information.  Transfer
	 * parameters are similarly unchanged.  The argument is a
	 * pathname specifying a directory.
	 */
	public void doCWD(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// get new directory name
		String dirName = "";
		if (request.hasArgument()) {
			dirName = request.getArgument();
		}
		LinkedRemoteFile newCurrentDirectory;
		try {
			newCurrentDirectory = currentDirectory.lookupFile(dirName);
		} catch (FileNotFoundException ex) {
			FtpResponse response = new FtpResponse(550, ex.getMessage());
			out.print(response);
			return;
		}
		if (!getConfig().checkPrivPath(_user, newCurrentDirectory)) {
			FtpResponse response =
				new FtpResponse(550, dirName + ": Not found");
			// reply identical to FileNotFoundException.getMessage() above
			out.print(response);
			return;
		}

		if (!newCurrentDirectory.isDirectory()) {
			out.print(new FtpResponse(550, dirName + ": Not a directory"));
			return;
		}
		currentDirectory = newCurrentDirectory;

		FtpResponse response =
			new FtpResponse(
				200,
				"Directory changed to " + currentDirectory.getPath());
		_cm.getConfig().directoryMessage(response, _user, currentDirectory);

		Collection uploaders =
			IRCListener.topFileUploaders(currentDirectory.getFiles());
		for (Iterator iter = uploaders.iterator(); iter.hasNext();) {
			UploaderPosition stat = (UploaderPosition) iter.next();

			String str1;
			try {
				str1 =
					IRCListener.formatUser(
						userManager.getUserByName(stat.getUsername()));
			} catch (NoSuchUserException e2) {
				continue;
			} catch (IOException e2) {
				logger.log(Level.FATAL, "Error reading userfile", e2);
				continue;
			}

			response.addComment(
				str1 + " [" + stat.getFiles() + "f/" + stat.getBytes() + "b]");
		}

		out.print(response.toString());
	}

	/**
	 * <code>DELE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the file specified in the pathname to be
	 * deleted at the server site.
	 */
	public void doDELE(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get filenames
		String fileName = request.getArgument();
		LinkedRemoteFile requestedFile;
		try {
			//requestedFile = getVirtualDirectory().lookupFile(fileName);
			requestedFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			out.print(
				new FtpResponse(550, "File not found: " + ex.getMessage()));
			return;
		}

		// check permission
		if (!_user.getUsername().equals(requestedFile.getUsername())
			&& !_user.isAdmin()) {
			out.print(
				new FtpResponse(
					550,
					"Permission denied. You are neither the owner or an admin."));
			return;
		}

		if (!getConfig().checkDelete(_user, requestedFile)) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_250_ACTION_OKAY.clone();

		User uploader;
		try {
			uploader =
				this.userManager.getUserByName(requestedFile.getUsername());
			uploader.updateCredits(
				(long) - (requestedFile.length() * uploader.getRatio()));
		} catch (IOException e) {
			response.addComment("Error removing credits: " + e.getMessage());
		} catch (NoSuchUserException e) {
			response.addComment("Error removing credits: " + e.getMessage());
		}

		// now delete
		//try {
		_cm.dispatchFtpEvent(
			new DirectoryFtpEvent(_user, "DELE", requestedFile));
		requestedFile.delete();
		out.print(response);
		//out.write(ftpStatus.getResponse(250, request, user, args));
		//}
		// catch{
		//	out.write(ftpStatus.getResponse(450, request, user, args));
		//}
	}
	// LIST;NLST;RETR;STOR
	public void doFEAT(FtpRequest request, PrintWriter out) {
		out.print(
			"211-Extensions supported:\r\n"
				+ " PRET\r\n"
				+ " MDTM\r\n"
				+ " SIZE\r\n"
				+ "211 End\r\n");
		return;
	}

	/**
	 * <code>HELP [&lt;SP&gt; <string>] &lt;CRLF&gt;</code><br>
	 *
	 * This command shall cause the server to send helpful
	 * information regarding its implementation status over the
	 * control connection to the user.  The command may take an
	 * argument (e.g., any command name) and return more specific
	 * information as a response.
	 */
	public void doHELP(FtpRequest request, PrintWriter out) {

		// print global help
		//		if (!request.hasArgument()) {
		FtpResponse response = new FtpResponse(214);
		response.addComment("The following commands are recognized.");
		//out.write(ftpStatus.getResponse(214, null, user, null));
		Method methods[] = this.getClass().getDeclaredMethods();
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			Class parameterTypes[] = method.getParameterTypes();
			if (parameterTypes.length == 2
				&& parameterTypes[0] == FtpRequest.class
				&& parameterTypes[1] == PrintWriter.class) {
				String commandName =
					method.getName().substring(2).replace('_', ' ');
				response.addComment(commandName);
			}
		}
		out.print(response);
		return;
		//		}
		//
		//		// print command specific help
		//		String ftpCmd = request.getArgument().toUpperCase();
		//		String args[] = null;
		//		FtpRequest tempRequest = new FtpRequest(ftpCmd);
		//		out.write(ftpStatus.getResponse(214, tempRequest, user, args));
		//		return;
	}

	/**
	 * <code>LIST [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command causes a list to be sent from the server to the
	 * passive DTP.  If the pathname specifies a directory or other
	 * group of files, the server should transfer a list of files
	 * in the specified directory.  If the pathname specifies a
	 * file then the server should send current information on the
	 * file.  A null argument implies the user's current working or
	 * default directory.  The data transfer is over the data
	 * connection
	 * 
	 *                LIST
	 *                   125, 150
	 *                      226, 250
	 *                      425, 426, 451
	 *                   450
	 *                   500, 501, 502, 421, 530
	 */
	public void doLIST(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

		String argument = request.getArgument();
		String directoryName = null;
		String options = "";
		//String pattern = "*";

		// get options, directory name and pattern
		//argument == null if there was no argument for LIST
		if (argument != null) {
			//argument = argument.trim();
			StringBuffer optionsSb = new StringBuffer(4);
			StringTokenizer st = new StringTokenizer(argument, " ");
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				if (token.charAt(0) == '-') {
					if (token.length() > 1) {
						optionsSb.append(token.substring(1));
					}
				} else {
					directoryName = token;
				}
			}
			options = optionsSb.toString();
		}

		// check options
		//		boolean allOption = options.indexOf('a') != -1;
		boolean detailOption =
			request.getCommand().equals("LIST")
				|| request.getCommand().equals("STAT")
				|| options.indexOf('l') != -1;
		//		boolean directoryOption = options.indexOf("d") != -1;
		if (!mbPasv && !mbPort && !request.getCommand().equals("STAT")) {
			out.print(FtpResponse.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS);
			return;
		}

		LinkedRemoteFile directoryFile;
		if (directoryName != null) {
			try {
				directoryFile = currentDirectory.lookupFile(directoryName);
			} catch (FileNotFoundException ex) {
				out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
				return;
			}
			if (!getConfig().checkPrivPath(_user, directoryFile)) {
				out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
				return;
			}
		} else {
			directoryFile = currentDirectory;
		}

		Socket dataSocket = null;
		Writer os;
		if (request.getCommand().equals("STAT")) {
			os = out;
			out.println("213- Status of " + request.getArgument() + ":");
		} else {
			out.print(FtpResponse.RESPONSE_150_OK);
			try {
				dataSocket = getDataSocket();
				os = new OutputStreamWriter(dataSocket.getOutputStream());
			} catch (IOException ex) {
				logger.warn("from master", ex);
				out.print(new FtpResponse(425, ex.getMessage()));
				return;
			}
		}

		ArrayList listFiles = new ArrayList(directoryFile.getFiles());
		for (Iterator iter = listFiles.iterator(); iter.hasNext();) {
			LinkedRemoteFile element = (LinkedRemoteFile) iter.next();
			if (!getConfig().checkPrivPath(_user, element))
				iter.remove();
		}
		FtpResponse response =
			(FtpResponse) FtpResponse
				.RESPONSE_226_CLOSING_DATA_CONNECTION
				.clone();

		try {
			SFVFile sfvfile = directoryFile.lookupSFVFile();
			int good = sfvfile.finishedFiles();
			if (sfvfile.size() != 0) {
				String statusDirName =
					"[" + (good * 100) / sfvfile.size() + "% complete]";
				listFiles.add(
					new DirectoryRemoteFile(
						directoryFile,
						"drftpd",
						"drftpd",
						statusDirName));
			}
		} catch (NoAvailableSlaveException e) {
			logger.log(Level.WARN, "No available slaves for SFV file");
		} catch (FileNotFoundException e) {
			// no sfv file in directory - just skip it
		} catch (IOException e) {
			logger.log(Level.WARN, "IO error loading SFV file", e);
		} catch (Throwable e) {
			response.addComment("zipscript error: " + e.getMessage());
			logger.log(Level.WARN, "zipscript error", e);
		}

		try {
			if (request.getCommand().equals("LIST")
				|| request.getCommand().equals("STAT")) {
				VirtualDirectory.printList(listFiles, os);
			} else if (request.getCommand().equals("NLST")) {
				VirtualDirectory.printNList(listFiles, detailOption, os);
			}
			os.flush();
		} catch (IOException ex) {
			logger.warn("from master", ex);
			out.print(new FtpResponse(450, ex.getMessage()));
			return;
		} finally {
			try {
				if (!request.getCommand().equals("STAT")) {
					os.flush();
					dataSocket.shutdownOutput();
					os.close();
					//dataSocket.close();
					response.addComment(status());
					out.print(response);
				} else {
					out.println("213 End of Status");
				}
			} catch (IOException e1) {
				logger.error("", e1);
			}
		}
		reset();
	}
	/**
	 * <code>MDTM &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 * 
	 * Returns the date and time of when a file was modified.
	 */
	public void doMDTM(FtpRequest request, PrintWriter out) {

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// reset state variables
		resetState();

		// get filenames
		String fileName = request.getArgument();
		LinkedRemoteFile reqFile;
		try {
			reqFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}
		//fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
		//String physicalName =
		//	user.getVirtualDirectory().getPhysicalName(fileName);
		//File reqFile = new File(physicalName);

		// now print date
		//if (reqFile.exists()) {
		out.print(
			new FtpResponse(
				213,
				DATE_FMT.format(new Date(reqFile.lastModified()))));
		//out.print(ftpStatus.getResponse(213, request, user, args));
		//} else {
		//	out.write(ftpStatus.getResponse(550, request, user, null));
		//}
	}

	/**
	 * <code>MKD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the directory specified in the pathname
	 * to be created as a directory (if the pathname is absolute)
	 * or as a subdirectory of the current working directory (if
	 * the pathname is relative).
	 * 
	 * 
	 *                MKD
	 *                   257
	 *                   500, 501, 502, 421, 530, 550
	 */
	public void doMKD(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get filenames
		//String dirName = request.getArgument();
		//if (!VirtualDirectory.isLegalFileName(fileName)) {
		//	out.println(
		//		"553 Requested action not taken. File name not allowed.");
		//	return;
		//}

		Object ret[] =
			currentDirectory.lookupNonExistingFile(request.getArgument());
		LinkedRemoteFile dir = (LinkedRemoteFile) ret[0];
		String createdDirName = (String) ret[1];

		if (!getConfig().checkMakeDir(_user, dir)) {
			out.write(FtpResponse.RESPONSE_530_ACCESS_DENIED.toString());
			return;
		}

		if (createdDirName == null) {
			out.print(
				new FtpResponse(
					550,
					"Requested action not taken. "
						+ request.getArgument()
						+ " already exists"));
			return;
		}

		if (!VirtualDirectory.isLegalFileName(createdDirName)) {
			out.print(FtpResponse.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}

		try {
			LinkedRemoteFile createdDir =
				dir.createDirectory(
					_user.getUsername(),
					_user.getGroupName(),
					createdDirName);
			out.print(
				new FtpResponse(
					257,
					"\"" + createdDir.getPath() + "\" created."));

			if (getConfig().checkDirLog(_user, createdDir)) {
				_cm.dispatchFtpEvent(
					new DirectoryFtpEvent(_user, "MKD", createdDir));
			}
			return;
		} catch (ObjectExistsException ex) {
			out.println("550 directory " + createdDirName + " already exists");
			return;
		}

		// check permission
		//		if (!getVirtualDirectory().hasCreatePermission(physicalName, true)) {
		//			out.write(ftpStatus.getResponse(450, request, user, args));
		//			return;
		//		}
	}

	/**
	 * <code>MODE &lt;SP&gt; <mode-code> &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a single Telnet character code specifying
	 * the data transfer modes described in the Section on
	 * Transmission Modes.
	 */
	public void doMODE(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (request.getArgument().equalsIgnoreCase("S")) {
			out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		} else {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		}
	}

	/**
	 * <code>NLST [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command causes a directory listing to be sent from
	 * server to user site.  The pathname should specify a
	 * directory or other system-specific file group descriptor; a
	 * null argument implies the current directory.  The server
	 * will return a stream of names of files and no other
	 * information.
	 */
	public void doNLST(FtpRequest request, PrintWriter out) {
		doLIST(request, out);
		//		// reset state variables
		//		resetState();
		//
		//		String directoryName = "./";
		//		String options = "";
		//		//String pattern = "*";
		//		String argument = request.getArgument();
		//
		//		// get options, directory name and pattern
		//		if (argument != null) {
		//			argument = argument.trim();
		//			StringBuffer optionsSb = new StringBuffer(4);
		//			StringTokenizer st = new StringTokenizer(argument, " ");
		//			while (st.hasMoreTokens()) {
		//				String token = st.nextToken();
		//				if (token.charAt(0) == '-') {
		//					if (token.length() > 1) {
		//						optionsSb.append(token.substring(1));
		//					}
		//				} else {
		//					directoryName = token;
		//				}
		//			}
		//			options = optionsSb.toString();
		//		}
		//
		//		// check options
		//		//boolean bAll = options.indexOf('a') != -1;
		//		boolean bDetail = options.indexOf('l') != -1;
		//
		//		LinkedRemoteFile directoryFile;
		//		if (directoryName != null) {
		//			try {
		//				directoryFile = currentDirectory.lookupFile(directoryName);
		//			} catch (IOException ex) {
		//				out.print(new FtpResponse(450, ex.getMessage()));
		//				return;
		//			}
		//		} else {
		//			directoryFile = currentDirectory;
		//		}
		//
		//		out.print(FtpResponse.RESPONSE_150_OK);
		//		Writer os = null;
		//		try {
		//			Socket dataSocket;
		//			try {
		//				dataSocket = getDataSocket();
		//			} catch (IOException ex) {
		//				out.print(FtpResponse.RESPONSE_425_CANT_OPEN_DATA_CONNECTION);
		//				return;
		//			}
		//
		//			if (mbPort) {
		//				os = new OutputStreamWriter(dataSocket.getOutputStream());
		//
		//				try {
		//					VirtualDirectory.printNList(
		//						directoryFile.getFiles(),
		//						bDetail,
		//						os);
		//				} catch (IOException ex) {
		//					out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
		//					return;
		//				}
		//				os.flush();
		//				FtpResponse response =
		//					(FtpResponse) (FtpResponse
		//						.RESPONSE_226_CLOSING_DATA_CONNECTION)
		//						.clone();
		//				response.addComment(status());
		//				out.print(response);
		//			} else { //mbPasv
		//				//TODO passive transfer mode
		//			}
		//		} catch (IOException ex) {
		//			ex.printStackTrace();
		//			out.print(FtpResponse.RESPONSE_425_CANT_OPEN_DATA_CONNECTION);
		//		} finally {
		//			if (os != null) {
		//				try {
		//					os.close();
		//				} catch (Exception ex) {
		//					ex.printStackTrace();
		//				}
		//			}
		//			reset();
		//		}
		//		//
		//		//		 
		//		//		 out.print(FtpResponse.RESPONSE_150_OK);
		//		//		 Writer os = null;
		//		//		 try {
		//		//		     Socket dataSoc = mDataConnection.getDataSocket();
		//		//		     if (dataSoc == null) {
		//		//		          out.write(ftpStatus.getResponse(550, request, user, null));
		//		//		          return;
		//		//		     }
		//		//		     
		//		//		     os = new OutputStreamWriter(dataSoc.getOutputStream());
		//		//		     
		//		//		     if (!VirtualDirectory.printNList(request.getArgument(), os)) {
		//		//		         out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
		//		//		     }
		//		//		     else {
		//		//		        os.flush();
		//		//		        out.write(ftpStatus.getResponse(226, request, user, null));
		//		//		     }
		//		//		 }
		//		//		 catch(IOException ex) {
		//		//		     out.write(ftpStatus.getResponse(425, request, user, null));
		//		//		 }
		//		//		 finally {
		//		//		 try {
		//		//		 os.close();
		//		//		 } catch(Exception ex) {
		//		//		 e.printStackTrace();
		//		//		 }
		//		//		     mDataConnection.reset();
		//		//		 }
	}

	/**
	 * <code>NOOP &lt;CRLF&gt;</code><br>
	 *
	 * This command does not affect any parameters or previously
	 * entered commands. It specifies no action other than that the
	 * server send an OK reply.
	 */
	public void doNOOP(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
	}

	/**
	 * <code>PASS &lt;SP&gt; <password> &lt;CRLF&gt;</code><br>
	 *
	 * The argument field is a Telnet string specifying the user's
	 * password.  This command must be immediately preceded by the
	 * user name command.
	 */
	public void doPASS(FtpRequest request, PrintWriter out) {

		// set state variables

		if (_user == null) {
			out.print(FtpResponse.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS);
			resetState();
			return;
		}
		resetState();
		//		mbPass = true;

		// set user password and login
		String pass = request.hasArgument() ? request.getArgument() : "";

		// login failure - close connection
		if (_user.checkPassword(pass)) {
			_user.updateLastAccessTime();
			FtpResponse response =
				(FtpResponse) FtpResponse.RESPONSE_230_USER_LOGGED_IN.clone();
			try {
				_cm.getConfig().welcomeMessage(response);
			} catch (IOException e) {
				logger.log(
					Level.WARN,
					"Error reading ftp-data/text/welcome.txt",
					e);
			}
			authenticated = true;
			out.print(response);
			_cm.dispatchFtpEvent(new UserEvent(_user, "LOGIN"));
		} else {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			/*
			ConnectionService conService = mConfig.getConnectionService();
			if (conService != null) {
			    conService.closeConnection(user.getSessionId());
			}
			*/
		}
	}

	/**
	 * <code>PASV &lt;CRLF&gt;</code><br>
	 *
	 * This command requests the server-DTP to "listen" on a data
	 * port (which is not its default data port) and to wait for a
	 * connection rather than initiate one upon receipt of a
	 * transfer command.  The response to this command includes the
	 * host and port address this server is listening on.
	 */
	public void doPASV(FtpRequest request, PrintWriter out) {
		reset();
		if (!preTransfer) {
			out.print(
				new FtpResponse(
					500,
					"You need to use a client supporting PRET (PRE Transfer) to use PASV"));
			return;
		}

		if (preTransferRSlave == null) {
			if (!setPasvCommand()) {
				out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
				return;
			}
		} else {
			try {
				_transfer = preTransferRSlave.getSlave().listen();
				this.mAddress = preTransferRSlave.getInetAddress();
				this.miPort = _transfer.getLocalPort();
				this.mbPasv = true;
			} catch (RemoteException e) {
				preTransferRSlave.handleRemoteException(e);
				out.print(
					new FtpResponse(450, "Remote error: " + e.getMessage()));
				return;
			} catch (NoAvailableSlaveException e) {
				out.print(FtpResponse.RESPONSE_530_SLAVE_UNAVAILABLE);
				return;
			} catch (IOException e) {
				out.print(new FtpResponse(450, e.getMessage()));
				logger.log(Level.FATAL, "", e);
				return;
			}
		}
		//InetAddress mAddress == getInetAddress();
		//miPort == getPort();

		String addrStr =
			mAddress.getHostAddress().replace('.', ',')
				+ ','
				+ (miPort >> 8)
				+ ','
				+ (miPort & 0xFF);
		out.print(
			new FtpResponse(227, "Entering Passive Mode (" + addrStr + ")."));
		out.flush();
	}

	/**
	 * <code>PORT &lt;SP&gt; <host-port> &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a HOST-PORT specification for the data port
	 * to be used in data connection.  There are defaults for both
	 * the user and server data ports, and under normal
	 * circumstances this command and its reply are not needed.  If
	 * this command is used, the argument is the concatenation of a
	 * 32-bit internet host address and a 16-bit TCP port address.
	 * This address information is broken into 8-bit fields and the
	 * value of each field is transmitted as a decimal number (in
	 * character string representation).  The fields are separated
	 * by commas.  A port command would be:
	 *
	 *   PORT h1,h2,h3,h4,p1,p2
	 * 
	 * where h1 is the high order 8 bits of the internet host address.
	 */
	public void doPORT(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		InetAddress clientAddr = null;
		int clientPort = 0;
		preTransfer = false;
		preTransferRSlave = null;
		// argument check
		if (!request.hasArgument()) {
			//Syntax error in parameters or arguments
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		StringTokenizer st = new StringTokenizer(request.getArgument(), ",");
		if (st.countTokens() != 6) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get data server
		String dataSrvName =
			st.nextToken()
				+ '.'
				+ st.nextToken()
				+ '.'
				+ st.nextToken()
				+ '.'
				+ st.nextToken();
		try {
			clientAddr = InetAddress.getByName(dataSrvName);
		} catch (UnknownHostException ex) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (!controlSocket
			.getInetAddress()
			.getHostAddress()
			.startsWith("192.168.")
			&& clientAddr.getHostAddress().startsWith("192.168.")) {
			FtpResponse response = new FtpResponse(501);
			response.addComment("==YOU'RE BEHIND A NAT ROUTER==");
			response.addComment(
				"Configure the firewall settings of your FTP client");
			response.addComment(
				"  to use your real IP: "
					+ controlSocket.getInetAddress().getHostAddress());
			response.addComment("And set up port forwarding in your router.");
			response.addComment(
				"Or you can just use a PRET capable client, see");
			response.addComment(
				"  http://drftpd.mog.se/ for PRET capable clients");
			out.print(response);
			return;
		}

		// get data server port
		try {
			int hi = Integer.parseInt(st.nextToken());
			int lo = Integer.parseInt(st.nextToken());
			clientPort = (hi << 8) | lo;
		} catch (NumberFormatException ex) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			//out.write(ftpStatus.getResponse(552, request, user, null));
			return;
		}

		setPortCommand(clientAddr, clientPort);

		//Notify the user that this is not his IP.. Good for NAT users that aren't aware that their IP has changed.
		if (!clientAddr.equals(controlSocket.getInetAddress())) {
			out.print(
				new FtpResponse(
					200,
					"FXP allowed. If you're not FXPing and set your IP to "
						+ controlSocket.getInetAddress().getHostAddress()
						+ " (usually in firewall settings)"));
			return;
		}
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doPRET(FtpRequest request, PrintWriter out) {
		FtpRequest ghostRequest = new FtpRequest(request.getArgument());
		String cmd = ghostRequest.getCommand();
		if (cmd.equals("LIST") || cmd.equals("NLST")) {
			preTransferRSlave = null;
			preTransfer = true;
			out.print(
				new FtpResponse(
					200,
					"OK, will use master for upcoming transfer"));
			return;
		} else if (cmd.equals("RETR")) {
			try {
				preTransferRSlave =
					currentDirectory
						.lookupFile(ghostRequest.getArgument())
						.getASlaveForDownload();
				preTransfer = true;
				out.print(
					new FtpResponse(
						200,
						"OK, will use "
							+ preTransferRSlave.getName()
							+ " for upcoming transfer"));
				return;
			} catch (NoAvailableSlaveException e) {
				out.print(FtpResponse.RESPONSE_530_SLAVE_UNAVAILABLE);
				reset();
				return;
			} catch (FileNotFoundException e) {
				out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
				reset();
				return;
			}
		} else if (cmd.equals("STOR")) {
			try {
				preTransferRSlave =
					slaveManager.getASlave(Transfer.TRANSFER_RECEIVING_UPLOAD);
				preTransfer = true;
				out.print(
					new FtpResponse(
						200,
						"OK, will use "
							+ preTransferRSlave.getName()
							+ " for upcoming transfer"));
				return;
			} catch (NoAvailableSlaveException e) {
				out.print(FtpResponse.RESPONSE_530_SLAVE_UNAVAILABLE);
				reset();
				return;
			}
		} else {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
			return;
		}
	}

	/**
	 * <code>PWD  &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the name of the current working
	 * directory to be returned in the reply.
	 */
	public void doPWD(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();
		out.print(
			new FtpResponse(
				257,
				"\"" + currentDirectory.getPath() + "\" is current directory"));
	}

	/**
	 * <code>QUIT &lt;CRLF&gt;</code><br>
	 *
	 * This command terminates a USER and if file transfer is not
	 * in progress, the server closes the control connection.
	 */
	public void doQUIT(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// and exit
		//out.write(ftpStatus.getResponse(221, request, user, null));
		FtpResponse response = new FtpResponse(221, "Goodbye");
		out.print(response.toString());
		stop();
		/*
		    ConnectionService conService = mConfig.getConnectionService();
		    if (conService != null) {
		        conService.closeConnection(user.getSessionId());
		    }
		*/
	}

	/**
	 * <code>REST &lt;SP&gt; <marker> &lt;CRLF&gt;</code><br>
	 *
	 * The argument field represents the server marker at which
	 * file transfer is to be restarted.  This command does not
	 * cause file transfer but skips over the file to the specified
	 * data checkpoint.  This command shall be immediately followed
	 * by the appropriate FTP service command which shall cause
	 * file transfer to resume.
	 */
	public void doREST(FtpRequest request, PrintWriter out) {
		//TODO test REST
		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// set state variables
		resetState();

		String skipNum = request.getArgument();
		try {
			resumePosition = Long.parseLong(skipNum);
		} catch (NumberFormatException ex) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (resumePosition < 0) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			resumePosition = 0;
			return;
		}
		out.print(FtpResponse.RESPONSE_350_PENDING_FURTHER_INFORMATION);
	}

	/**
	 * <code>RETR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to transfer a copy of the
	 * file, specified in the pathname, to the server- or user-DTP
	 * at the other end of the data connection.  The status and
	 * contents of the file at the server site shall be unaffected.
	 * 
	 *                RETR
	 *                   125, 150
	 *                      (110)
	 *                      226, 250
	 *                      425, 426, 451
	 *                   450, 550
	 *                   500, 501, 421, 530
	 */

	public void doRETR(FtpRequest request, PrintWriter out) {
		// set state variables
		long resumePosition = this.resumePosition;
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get filenames
		String fileName = request.getArgument();
		try {
			_transferFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			out.println("550 " + fileName + ": No such file");
			return;
		}
		if (!getConfig().checkPrivPath(_user, _transferFile)) {
			out.println("550 " + fileName + ": No such file");
			return;
		}
		if (!_transferFile.isFile()) {
			out.println("550 " + _transferFile + ": not a plain file.");
			return;
		}
		if (_user.getRatio() != 0
			&& _user.getCredits() < _transferFile.length()) {
			out.println("550 Not enough credits.");
			return;
		}

		if (!_cm.getConfig().checkDownload(_user, _transferFile)) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		//SETUP rslave
		if (mbPasv) {
			assert preTransfer == true;

			if (!_transferFile.getSlaves().contains(preTransferRSlave)) {
				out.print(FtpResponse.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS);
				return;
			}
			_rslave = preTransferRSlave;
			preTransferRSlave = null;
			//preTransfer = false;
		} else {

			try {
				_rslave =
					_transferFile.getASlave(Transfer.TRANSFER_SENDING_DOWNLOAD);
			} catch (NoAvailableSlaveException ex) {
				out.print(FtpResponse.RESPONSE_530_SLAVE_UNAVAILABLE);
				return;
			}
		}

		// SETUP this.transfer
		if (mbPort) {
			try {
				_transfer =
					_rslave.getSlave().connect(getInetAddress(), getPort());
			} catch (RemoteException ex) {
				_rslave.handleRemoteException(ex);
				out.print(
					new FtpResponse(450, "Remote error: " + ex.getMessage()));
				return;
			} catch (IOException ex) {
				out.print(
					new FtpResponse(
						450,
						ex.getClass().getName()
							+ " from slave: "
							+ ex.getMessage()));
				logger.log(Level.FATAL, "rslave=" + _rslave, ex);
				return;
			}
		} else if (mbPasv) {

			// transfere was already set up in doPASV(..., ...)
			//			try {
			//				rslave.getSlave().doListenSend(
			//					remoteFile.getPath(),
			//					getType(),
			//					resumePosition);
			//			} catch (RemoteException ex) {
			//				preTransferRSlave.handleRemoteException(ex);
			//				out.print(
			//					new FtpResponse(450, "Remote error: " + ex.getMessage()));
			//				return;
			//			} catch (NoAvailableSlaveException e1) {
			//				out.print(FtpResponse.RESPONSE_450_SLAVE_UNAVAILABLE);
			//				return;
			//			} catch (IOException ex) {
			//				out.print(
			//					new FtpResponse(
			//						450,
			//						ex.getClass().getName()
			//							+ " from slave: "
			//							+ ex.getMessage()));
			//				logger.log(Level.FATAL, "rslave=" + rslave, ex);
			//				return;
			//			}
		} else {
			out.print(FtpResponse.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS);
			return;
		}
		assert _transfer != null;

		out.print(
			new FtpResponse(
				150,
				"File status okay; about to open data connection from "
					+ _rslave.getName()
					+ "."));
		out.flush();
		try {
			_transfer.downloadFile(
				_transferFile.getPath(),
				getType(),
				resumePosition,
				true);
		} catch (RemoteException ex) {
			_rslave.handleRemoteException(ex);
			out.print(new FtpResponse(426, "Remote error: " + ex.getMessage()));
			return;
		} catch (IOException ex) {
			out.print(new FtpResponse(426, "IO error: " + ex.getMessage()));
			logger.log(Level.WARN, "from " + _rslave.getName(), ex);
			return;
		}
		//		TransferThread transferThread = new TransferThread(rslave, transfer);
		//		System.err.println("Calling interruptibleSleepUntilFinished");
		//		try {
		//			transferThread.interruptibleSleepUntilFinished();
		//		} catch (Throwable e1) {
		//			e1.printStackTrace();
		//		}
		//		System.err.println("Finished");

		FtpResponse response =
			(FtpResponse) FtpResponse
				.RESPONSE_226_CLOSING_DATA_CONNECTION
				.clone();

		try {
			long checksum = _transfer.getChecksum();
			response.addComment(
				"Checksum: " + Checksum.formatChecksum(checksum));
			if (_transferFile.getCheckSum(false) == 0) {
				_transferFile.setCheckSum(_transfer.getChecksum());
			} else if (_transferFile.getCheckSum(false) != checksum) {
				response.addComment(
					"WARNING: checksum from transfer didn't match cached checksum");
				logger.info(
					"checksum from transfer didn't match cached checksum",
					new Throwable());
			}

			try {
				if (_transferFile
					.getParentFileNull()
					.lookupSFVFile()
					.getChecksum(_transferFile.getName())
					== checksum) {
					response.addComment(
						"checksum from transfer matched checksum in .sfv");
				} else {
					response.addComment(
						"WARNING: checksum from transfer didn't match checksum in .sfv");
				}
			} catch (NoAvailableSlaveException e1) {
				response.addComment(
					"slave with .sfv offline, checksum not verified");
			} catch (FileNotFoundException e1) {
				//continue without verification
			} catch (ObjectNotFoundException e1) {
				//file not found in .sfv, continue
			} catch (IOException e1) {
				logger.info(e1);
				out.print("IO exception reading .sfv file: " + e1.getMessage());
			}

			long transferedBytes = _transfer.getTransfered();

			//TODO creditloss
			float ratio = getConfig().getCreditLossRatio(_transferFile, _user);
			if (ratio != 0) {
				_user.updateCredits(-transferedBytes);
			}
			_user.updateDownloadedBytes(transferedBytes);
			_user.commit();
		} catch (RemoteException ex) {
			_rslave.handleRemoteException(ex);
		} catch (UserFileException e) {
		}
		out.print(response);
		reset();
	}

	/**
	 * <code>RMD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the directory specified in the pathname
	 * to be removed as a directory (if the pathname is absolute)
	 * or as a subdirectory of the current working directory (if
	 * the pathname is relative).
	 */
	public void doRMD(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get file names
		String fileName = request.getArgument();
		LinkedRemoteFile requestedFile;
		try {
			requestedFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException e) {
			out.print(new FtpResponse(550, fileName + ": " + e.getMessage()));
			return;
		}

		if (!_cm.getConfig().checkDelete(_user, requestedFile)) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!requestedFile.isDirectory()) {
			out.print(new FtpResponse(550, fileName + ": Not a directory"));
			return;
		}
		if (requestedFile.dirSize() != 0) {
			out.print(new FtpResponse(550, fileName + ": Directory not empty"));
			return;
		}

		// now delete
		if (getConfig().checkDirLog(_user, requestedFile)) {
			_cm.dispatchFtpEvent(
				new DirectoryFtpEvent(_user, "RMD", requestedFile));
		}
		requestedFile.delete();
		out.print(FtpResponse.RESPONSE_250_ACTION_OKAY);
	}

	/**
	 * <code>RNFR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the old pathname of the file which is
	 * to be renamed.  This command must be immediately followed by
	 * a "rename to" command specifying the new file pathname.
	 * 
	 *                RNFR
	              450, 550
	              500, 501, 502, 421, 530
	              350
	
	 */
	public void doRNFR(FtpRequest request, PrintWriter out) {
		//out.print(new FtpResponse(500, "Command not implemented").toString());

		// reset state variable
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// set state variable

		// get filenames
		//String fileName = request.getArgument();
		//fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
		//mstRenFr = user.getVirtualDirectory().getPhysicalName(fileName);

		try {
			_renameFrom =
				currentDirectory.lookupFile(request.getArgument());
		} catch (FileNotFoundException e) {
			out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			resetState();
			return;
		}

		if (_renameFrom.hasOfflineSlaves()) {
			out.print(
				new FtpResponse(450, "Cannot rename, file has offline slaves"));
			resetState();
			return;
		}
		out.print(
			new FtpResponse(350, "File exists, ready for destination name"));
	}

	/**
	 * <code>RNTO &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the new pathname of the file
	 * specified in the immediately preceding "rename from"
	 * command.  Together the two commands cause a file to be
	 * renamed.
	 */
	public void doRNTO(FtpRequest request, PrintWriter out) {
		//out.print(new FtpResponse(500, "Command not implemented").toString());

		// argument check
		if (!request.hasArgument()) {
			resetState();
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// set state variables
		if (_renameFrom == null) {
			resetState();
			out.print(FtpResponse.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS);
			return;
		}

		if (_renameFrom.hasOfflineSlaves()) {
			out.print(
				new FtpResponse(450, "Cannot rename, file has offline slaves"));
			resetState();
			return;
		}

		Object ret[] =
			currentDirectory.lookupNonExistingFile(request.getArgument());
		LinkedRemoteFile toDir = (LinkedRemoteFile) ret[0];
		String name = (String) ret[1];

		LinkedRemoteFile fromFile = _renameFrom;
		resetState();

		if (name == null)
			name = fromFile.getName();
		//String to = toDir.getPath() + "/" + name;

		// check permission
		//if(!user.getVirtualDirectory().hasCreatePermission(physicalToFileStr, true)) {
		//   out.write(ftpStatus.getResponse(553, request, user, null));
		//   return;
		//}

		try {
			fromFile.renameTo(toDir.getPath(), name);
		} catch (IOException e) {
			out.print(FtpResponse.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN);
			e.printStackTrace();
			return;
		}

		//out.write(FtpResponse.RESPONSE_250_ACTION_OKAY.toString());
		FtpResponse response =
			new FtpResponse(
				250,
				request.getCommand() + " command successfull.");
		out.print(response);
	}

	public void doSITE_ADDIP(FtpRequest request, PrintWriter out) {
		resetState();

		if (!_user.isAdmin() && !_user.isGroupAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");
		if (args.length < 2) {
			out.println("200 USAGE: SITE ADDIP <username> <ident@ip>");
			return;
		}
		FtpResponse response = new FtpResponse(200);

		User myUser;
		try {
			myUser = userManager.getUserByName(args[0]);
			if(_user.isGroupAdmin() && !_user.getGroupName().equals(myUser.getGroupName())) {
				out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
				return;
			}
			response.addComment("Adding masks");
			for (int i = 1; i < args.length; i++) {
				String string = args[i];
				try {
					myUser.addIPMask(string);
					response.addComment(
						"Added " + string + " to " + myUser.getUsername());
				} catch (DuplicateElementException e) {
					response.addComment(
						string + " already added to " + myUser.getUsername());
				}
			}
			myUser.commit(); // throws UserFileException
			//userManager.save(user2);
		} catch (NoSuchUserException ex) {
			out.println("200 No such user: " + args[0]);
			return;
		} catch (UserFileException ex) {
			response.addComment(ex.getMessage());
			out.print(response);
			return;
		} catch (IOException ex) {
			out.print(new FtpResponse(200, "IO Error: " + ex.getMessage()));
			return;
		}
		out.print(response);
		return;
	}

	/**
	 * USAGE: site adduser <user> <password> [<ident@ip#1> ... <ident@ip#5>]
	 *	Adds a user. You can have wild cards for users that have dynamic ips
	 *	Examples: *@192.168.1.* , frank@192.168.*.* , bob@192.*.*.*
	 *	(*@192.168.1.1[5-9] will allow only 192.168.1.15-19 to connect but no one else)
	 *
	 *	If a user is added by a groupadmin, that user will have the GLOCK
	 *	flag enabled and will inherit the groupadmin's home directory.
	 *	
	 *	All default values for the user are read from file default.user in
	 *	/glftpd/ftp-data/users. Comments inside describe what is what.
	 *	Gadmins can be assigned their own default.<group> userfiles
	 *	as templates to be used when they add a user, if one is not found,
	 *	default.user will be used.
	 *	default.groupname files will also be used for "site gadduser".
	 *
	 *	ex. site ADDUSER Archimede mypassword 
	 *
	 *	This would add the user 'Archimede' with the password 'mypassword'.
	 *
	 *	ex. site ADDUSER Archimede mypassword *@127.0.0.1
	 *	
	 *	This would do the same as above + add the ip '*@127.0.0.1' at the
	 *	same time.
	 *
	 *	HOMEDIRS:
	 *	After login, the user will automatically be transferred into his/her
	 *	homedir. As of 1.16.x this dir is now "kinda" chroot'ed and they are
	 *	now unable to "cd ..".
	 *
	 *
	 * @param request
	 * @param out
	 */
	public void doSITE_ADDUSER(FtpRequest request, PrintWriter out) {
		resetState();

		if (!_user.isAdmin() && !_user.isGroupAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if(_user.isGroupAdmin() && request.getCommand().equals("SITE GADDUSER")) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (_user.isGroupAdmin()) {
			int users;
			try {
				users =
					userManager.getAllUsersByGroup(_user.getGroupName()).size();
				if (users >= _user.getGroupSlots()) {
					out.print(
						new FtpResponse(
							200,
							"Sorry, no more open slots available."));
					return;
				}
			} catch (IOException e1) {
				logger.warn("", e1);
				out.print(new FtpResponse(200, e1.getMessage()));
				return;
			}
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (!st.hasMoreTokens()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		String newUsername = st.nextToken();
		if (!st.hasMoreTokens()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		String pass = st.nextToken();
		User newUser;
		FtpResponse response = new FtpResponse(200);
		response.addComment(newUsername + " created");
		try {
			newUser = userManager.create(newUsername);
			newUser.setPassword(pass);
			newUser.setComment("Added by " + _user.getUsername());
			if (_user.isGroupAdmin()) {
				newUser.setGroup(_user.getGroupName());
			}
			if(request.getCommand().equals("SITE GADDUSER")) {
				newUser.setGroup(st.nextToken());
				response.addComment("Primary group set to "+newUser.getGroupName());
			}
			while(st.hasMoreTokens()) {
				String string = st.nextToken();
				try {
					newUser.addIPMask(string);
					response.addComment("Added IP mask " + string);
				} catch (DuplicateElementException e1) {
					response.addComment("IP mask " + string + "already added");
				}
			}
			newUser.commit();
		} catch (UserFileException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		}
		out.print(response);
	}

	/**
	 * USAGE: site change <user> <field> <value> - change a field for a user
	   site change =<group> <field> <value> - change a field for each member of group <group>
	   site change { <user1> <user2> .. } <field> <value> - change a field for each user in the list
	   site change * <field> <value>     - change a field for everyone
	
	Type "site change user help" in glftpd for syntax.
	
	Fields available:
	
	Field			Description
	-------------------------------------------------------------
	ratio		Upload/Download ratio. 0 = Unlimited (Leech)
	wkly_allotment 	The number of kilobytes that this user will be given once a week
			(you need the reset binary enabled in your crontab).
			Syntax: site change user wkly_allotment "#,###"
			The first number is the section number (0=default section),
			the second is the number of kilobytes to give.
			(user's credits are replaced, not added to, with this value)
			Only one section at a time is supported,
	homedir		This will change the user's homedir.
			NOTE: This command is disabled by default.  To enable it, add
			"min_homedir /site" to your config file, where "/site" is the
			minimum directory that users can have, i.e. you can't change
			a user's home directory to /ftp-data or anything that doesn't
			have "/site" at the beginning.
			Important: don't use a trailing slash for homedir!
			Users CAN NOT cd, list, upload/download, etc, outside of their
	                    home dir. It acts similarly to chroot() (try man chroot).
	startup_dir	The directory to start in. ex: /incoming will start the user
			in /glftpd/site/incoming if rootpath is /glftpd and homedir is /site.
			Users CAN cd, list, upload/download, etc, outside of startup_dir.
	idle_time	Sets the default and maximum idle time for this user (overrides
			the -t and -T settings on glftpd command line). If -1, it is disabled;
			if 0, it is the same as the idler flag.
	credits		Credits left to download.
	flags		+1ABC or +H or -3, type "site flags" for a list of flags.
	num_logins	# # : number of simultaneous logins allowed. The second
			number is number of sim. logins from the same IP.
	timeframe	# # : the hour from which to allow logins and the hour when logins from
	    		this user will start being rejected. This is set in a 24 hour format.
			If a user is online past his timeframe, he'll be disconnected the
			next time he does a 'CWD'.
	time_limit	Time limits, per LOGIN SESSION. (set in minutes. 0 = Unlimited)
	tagline		User's tagline.
	group_slots	Number of users a GADMIN is allowed to add.
			If you specify a second argument, it will be the
			number of leech accounts the gadmin can give (done by
			"site change user ratio 0") (2nd arg = leech slots)
	comment		Changes the user's comment (max 50 characters).
			Comments are displayed by the comment cookie (see below).
	max_dlspeed	Downstream bandwidth control (KBytes/sec) (0 = Unlimited)
	max_ulspeed	Same but for uploads
	max_sim_down	Maximum number of simultaneous downloads for this user
			(-1 = unlimited, 0 = zero [user can't download])
	max_sim_up	Maximum number of simultaneous uploads for this user
			(-1 = unlimited, 0 = zero [user can't upload])
	sratio		<SECTIONNAME> <#>
			This is to change the ratio of a section (other than default).
	
	Flags available:
	
	Flagname       	Flag	Description
	-------------------------------------------------------------
	SITEOP		1	User is siteop.
	GADMIN		2	User is Groupadmin of his/her first public
				group (doesn't work for private groups).
	GLOCK		3	User cannot change group.
	EXEMPT		4	Allows to log in when site is full. Also allows
				user to do "site idle 0", which is the same as
				having the idler flag. Also exempts the user
				from the sim_xfers limit in config file.
	COLOR		5	Enable/Disable the use of color (toggle with "site color").
	DELETED		6	User is deleted.
	USEREDIT	7	"Co-Siteop"
	ANON		8	User is anonymous (per-session like login).
	
	*NOTE* The 1 flag is not GOD mode, you must have the correct flags for the actions you wish to perform.
	*NOTE* If you have flag 1 then you DO NOT WANT flag 2
	
	Restrictions placed on users flagged ANONYMOUS.
		1.  '!' on login is ignored.
		2.  They cannot DELETE, RMDIR, or RENAME. 
		3.  Userfiles do not update like usual, meaning no stats will
	    	    be kept for these users.  The userfile only serves as a template for the starting 
		    environment of the logged in user. 
	    	    Use external scripts if you must keep records of their transfer stats.
	
	NUKE		A	User is allowed to use site NUKE.
	UNNUKE		B	User is allowed to use site UNNUKE.
	UNDUPE		C	User is allowed to use site UNDUPE.
	KICK		D	User is allowed to use site KICK.
	KILL		E	User is allowed to use site KILL/SWHO.
	TAKE		F	User is allowed to use site TAKE.
	GIVE		G	User is allowed to use site GIVE.
	USERS/USER	H	This allows you to view users ( site USER/USERS )
	IDLER		I	User is allowed to idle forever.
	CUSTOM1		J	Custom flag 1
	CUSTOM2		K	Custom flag 2
	CUSTOM3		L	Custom flag 3
	CUSTOM4		M	Custom flag 4
	CUSTOM5		N	Custom flag 5
	
	You can use custom flags in the config file to give some users access
	to certain things without having to use private groups.  These flags
	will only show up in "site flags" if they're turned on.
	
	ex. site change Archimede ratio 5
	
	This would set the ratio to 1:5 for the user 'Archimede'.
	
	ex. site change Archimede flags +2-AG
	
	This would make the user 'Archimede' groupadmin and remove his ability
	to use the commands site nuke and site give.
	
	NOTE: The flag DELETED can not be changed with site change, it
	      will change when someone does a site deluser/readd.
	 */
	public void doSITE_CHANGE(FtpRequest request, PrintWriter out) {
		final FtpResponse usageResponse =
			(FtpResponse) FtpResponse.RESPONSE_501_SYNTAX_ERROR.clone();
		usageResponse.addComment(
			"Valid fields: ratio idle_time credits num_logins_tot num_logins_ip");
		usageResponse.addComment(
			"              tagline max_sim_down max_sim_up");

		if (!_user.isAdmin() || !_user.isGroupAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		if (!request.hasArgument()) {
			out.print(usageResponse);
			return;
		}

		String command, commandArgument;
		User myUser;
		{
			String argument = request.getArgument();
			int pos1 = argument.indexOf(' ');
			if (pos1 == -1) {
				out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
				return;
			}
			String username = argument.substring(0, pos1);
			try {
				myUser = userManager.getUserByName(argument.substring(0, pos1));
			} catch (NoSuchUserException e) {
				out.print(
					new FtpResponse(
						550,
						"User " + username + " not found: " + e.getMessage()));
				return;
			} catch (IOException e) {
				out.print(
					new FtpResponse(
						550,
						"Error loading user: " + e.getMessage()));
				logger.log(Level.FATAL, "Error loading user", e);
				return;
			}
			if(_user.isGroupAdmin() && _user.getGroupName().equals(myUser.getGroupName())) {
				out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
				return;
			}

			int pos2 = argument.indexOf(' ', pos1 + 1);
			if (pos2 == -1) {
				out.print(usageResponse);
				return;
			}
			command = argument.substring(pos1 + 1, pos2);
			commandArgument = argument.substring(pos2 + 1);
		}

		//		String args[] = request.getArgument().split(" ");
		//		String command = args[1].toLowerCase();
		// 0 = user
		// 1 = command
		// 2- = argument
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		if (command == null) {
			response.addComment(
				"Valid fields: ratio idle_time credits num_logins_tot num_logins_ip");
			response.addComment(
				"              tagline max_sim_down max_sim_up");
			return;
		} else if ("credits".equalsIgnoreCase(command)) {
			myUser.setCredits(Bytes.parseBytes(commandArgument));

		} else if ("ratio".equalsIgnoreCase(command)) {
			myUser.setRatio(Float.parseFloat(commandArgument));

		} else if ("comment".equalsIgnoreCase(command)) {
			myUser.setComment(commandArgument);

		} else if ("idle_time".equalsIgnoreCase(command)) {
			myUser.setIdleTime(Integer.parseInt(commandArgument));

		} else if ("num_logins".equalsIgnoreCase(command)) {
			myUser.setMaxLogins(Integer.parseInt(commandArgument));

		} else if ("num_logins".equalsIgnoreCase(command)) {
			myUser.setMaxLoginsPerIP(Integer.parseInt(commandArgument));

		} else if ("max_dlspeed".equalsIgnoreCase(command)) {
			myUser.setRatio(Bytes.parseBytes(commandArgument));

		} else if ("max_ulspeed".equals(command)) {
			myUser.setMaxUploadRate(Integer.parseInt(commandArgument));
		} else if ("group".equals(command)) {
			myUser.setGroup(commandArgument);

			//			group_slots	Number of users a GADMIN is allowed to add.
			//					If you specify a second argument, it will be the
			//					number of leech accounts the gadmin can give (done by
			//					"site change user ratio 0") (2nd arg = leech slots)
		} else if ("group_slots".equals(command)) {
			try {
				String args[] = commandArgument.split(" ");
				if (args.length < 1 || args.length > 2) {
					out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
					return;
				}
				myUser.setGroupSlots(Short.parseShort(args[0]));
				if (args.length >= 2) {
					myUser.setGroupLeechSlots(Short.parseShort(args[1]));
				}
			} catch (NumberFormatException ex) {
				out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
				return;
			}
		}
		try {
			myUser.commit();
		} catch (UserFileException e) {
			response.addComment(e.getMessage());
		}
		out.print(response);
		return;
	}

	public void doSITE_CHECKSLAVES(FtpRequest request, PrintWriter out) {
		resetState();
		out.println(
			"200 Ok, " + slaveManager.verifySlaves() + " stale slaves removed");
	}

	/**
	 * USAGE: site chgrp <user> <group> [<group>]
	    Adds/removes a user from group(s).
	
	    ex. site chgrp archimede ftp
	    This would change the group to 'ftp' for the user 'archimede'.
	
	    ex1. site chgrp archimede ftp
	    This would remove the group ftp from the user 'archimede'.
	
	    ex2. site chgrp archimede ftp eleet
	    This moves archimede from ftp group to eleet group.
	    
	 * @param request
	 * @param out
	 */
	public void doSITE_CHGRP(FtpRequest request, PrintWriter out) {
		resetState();

		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");
		if (args.length < 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User myUser;
		try {
			myUser = userManager.getUserByName(args[0]);
		} catch (NoSuchUserException e) {
			out.print(
				new FtpResponse(200, "User not found: " + e.getMessage()));
			return;
		} catch (IOException e) {
			logger.log(Level.FATAL, "IO error reading user", e);
			out.print(
				new FtpResponse(
					200,
					"IO error reading user: " + e.getMessage()));
			return;
		}

		FtpResponse response = new FtpResponse(200);
		for (int i = 1; i < args.length; i++) {
			String string = args[i];
			try {
				myUser.removeGroup(string);
				response.addComment(
					myUser.getUsername() + " removed from group " + string);
			} catch (NoSuchFieldException e1) {
				try {
					myUser.addGroup(string);
				} catch (DuplicateElementException e2) {
					logger.log(
						Level.FATAL,
						"Error, user was not a member before",
						e2);
				}
				response.addComment(
					myUser.getUsername() + " added to group " + string);
			}
		}
		out.print(response);
		return;
	}

	public void doSITE_CHPASS(FtpRequest request, PrintWriter out) {
		resetState();

		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");
		if (args.length != 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		try {
			User user = userManager.getUserByName(args[0]);
			user.setPassword(args[1]);

			out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
			return;
		} catch (NoSuchUserException e) {
			out.print(
				new FtpResponse(200, "User not found: " + e.getMessage()));
			return;
		} catch (IOException e) {
			out.print(
				new FtpResponse(
					200,
					"Error reading userfile: " + e.getMessage()));
			logger.log(Level.FATAL, "Error reading userfile", e);
			return;
		}

	}

	/**
	 * USAGE: site delip <user> <ident@ip> ...
	 * @param request
	 * @param out
	 */
	public void doSITE_DELIP(FtpRequest request, PrintWriter out) {
		resetState();
		if (!_user.isAdmin() && !_user.isGroupAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");

		System.out.println(args.length);
		if (args.length < 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User myUser;
		try {
			myUser = userManager.getUserByName(args[0]);
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			logger.log(Level.FATAL, "IO error", e);
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		}
		if(_user.isGroupAdmin() && !_user.getGroupName().equals(myUser.getGroupName())) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		FtpResponse response = new FtpResponse(200);
		for (int i = 1; i < args.length; i++) {
			String string = args[i];
			try {
				myUser.removeIpMask(string);
				response.addComment("Removed " + string);
			} catch (NoSuchFieldException e1) {
				response.addComment(
					"Mask " + string + " not found: " + e1.getMessage());
				continue;
			}
		}
		out.print(response);
		return;
	}

	public void doSITE_DELUSER(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (!_user.isAdmin() && !_user.isGroupAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		String delUsername = request.getArgument();
		User delUser;
		try {
			delUser = this.userManager.getUserByName(delUsername);
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			out.print(
				new FtpResponse(200, "Couldn't getUser: " + e.getMessage()));
			return;
		}

		if(_user.isGroupAdmin() && !_user.getGroupName().equals(delUser.getGroupName())) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		delUser.setDeleted(true);
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_DUPE(FtpRequest request, PrintWriter out) {
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		String args[] = request.getArgument().split(" ");
		if (args.length == 0) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		ArrayList searchstrings = new ArrayList(args.length);
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		for (int i = 0; i < args.length; i++) {
			searchstrings.add(args[i].toLowerCase());
		}
		findFile(
			response,
			currentDirectory.getRoot(),
			searchstrings,
			"SITE DUPE".equals(request.getCommand()),
			"SITE SEARCH".equals(request.getCommand()));
		out.print(response);
		return;
	}

	/**
	 * USAGE: site gadduser <group> <user> <password> [<ident@ip#1 .. ident@ip#5>]
	 * Adds a user and changes his/her group to <group>.  If default.group
	 * exists, it will be used as a base instead of default.user.
	 *
	 * Only public groups can be used as <group>.
	 */
	public void doSITE_GADDUSER(FtpRequest request, PrintWriter out) {
		doSITE_ADDUSER(request, out);
	}

	public void doSITE_GIVE(FtpRequest request, PrintWriter out) {
		resetState();

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (!st.hasMoreTokens()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		User user2;
		try {
			user2 = userManager.getUserByName(st.nextToken());
		} catch (Exception e) {
			out.print(new FtpResponse(200, e.getMessage()));
			logger.warn("", e);
			return;
		}

		if (!st.hasMoreTokens()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		long credits = Bytes.parseBytes(st.nextToken());
		if (0 > credits) {
			out.print(
				new FtpResponse(200, "Credits must be a positive number."));
			return;
		}

		_user.updateCredits(-credits);
		user2.updateCredits(credits);
		out.print(
			new FtpResponse(
				200,
				"OK, gave "
					+ Bytes.formatBytes(credits)
					+ " of your credits to "
					+ user2.getUsername()));
		return;
	}
	public void doSITE_GROUPS(FtpRequest request, PrintWriter out) {
		resetState();

		Collection groups;
		try {
			groups = userManager.getAllGroups();
		} catch (IOException e) {
			logger.log(Level.FATAL, "IO error from getAllGroups()", e);
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		}
		FtpResponse response = new FtpResponse(200);
		response.addComment("All groups:");
		for (Iterator iter = groups.iterator(); iter.hasNext();) {
			String element = (String) iter.next();
			response.addComment(element);
		}

		out.print(response);
		return;
	}
	public void doSITE_KICK(FtpRequest request, PrintWriter out) {
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		String arg = request.getArgument();
		int pos = arg.indexOf(' ');

		String username;
		String message = "Kicked by " + _user.getUsername();
		if (pos == -1) {
			username = arg;
		} else {
			username = arg.substring(0, pos);
			message = arg.substring(pos + 1);
		}

		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();

		Collection conns = getConnectionManager().getConnections();
		synchronized (conns) {
			for (Iterator iter = conns.iterator(); iter.hasNext();) {
				BaseFtpConnection conn = (BaseFtpConnection) iter.next();
				try {
					if (conn.getUser().getUsername().equals(username)) {
						conn.stop(message);
					}
				} catch (NoSuchUserException e) {
				}
			}
		}
		out.print(response);
		return;
	}

	public void doSITE_KICKSLAVE(FtpRequest request, PrintWriter out) {
		reset();
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		RemoteSlave rslave;
		try {
			rslave = _cm.getSlaveManager().getSlave(request.getArgument());
		} catch (ObjectNotFoundException e) {
			out.print(new FtpResponse(200, "No such slave"));
			return;
		}
		if (!rslave.isAvailable()) {
			out.print(new FtpResponse(200, "Slave is already offline"));
			return;
		}
		rslave.setOffline("Slave kicked by " + _user.getUsername());
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_LIST(FtpRequest request, PrintWriter out) {
		resetState();
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		Map files = currentDirectory.getMap();
		for (Iterator iter = files.values().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			//if (!key.equals(file.getName()))
			//	response.addComment(
			//		"WARN: " + key + " not equals to " + file.getName());
			//response.addComment(key);
			response.addComment(file.toString());
		}
		out.print(response);
	}

	/**
	 * USAGE: site nuke <directory> <multiplier> <message>
	 * Nuke a directory
	 *
	 * ex. site nuke shit 2 CRAP
	 *
	 * This will nuke the directory 'shit' and remove x2 credits with the
	 * comment 'CRAP'.
	 *
	 * NOTE: You can enclose the directory in braces if you have spaces in the name
	 * ex. site NUKE {My directory name} 1 because_i_dont_like_it
	 * 
	 * Q)  What does the multiplier in 'site nuke' do?
	 * A)  Multiplier is a penalty measure. If it is 0, the user doesn't lose any
	 *     credits for the stuff being nuked. If it is 1, user only loses the
	 *     amount of credits he gained by uploading the files (which is calculated
	 *     by multiplying total size of file by his/her ratio). If multiplier is more
	 *     than 1, the user loses the credits he/she gained by uploading, PLUS some
	 *     extra credits. The formula is this: size * ratio + size * (multiplier - 1).
	 *     This way, multiplier of 2 causes user to lose size * ratio + size * 1,
	 *     so the additional penalty in this case is the size of nuked files. If the
	 *     multiplier is 3, user loses size * ratio + size * 2, etc.
	 */
	public void doSITE_NUKE(FtpRequest request, PrintWriter out) {
		if (!_user.isNuker()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		StringTokenizer st = new StringTokenizer(request.getArgument(), " ");

		if (!st.hasMoreTokens()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		int multiplier;
		LinkedRemoteFile nukeDir;
		String nukeDirName;
		try {
			nukeDirName = st.nextToken();
			nukeDir = currentDirectory.getFile(nukeDirName);
		} catch (FileNotFoundException e) {
			FtpResponse response = new FtpResponse(550, e.getMessage());
			out.print(response.toString());
			return;
		}
		if (!nukeDir.isDirectory()) {
			FtpResponse response =
				new FtpResponse(550, nukeDirName + ": not a directory");
			out.print(response.toString());
			return;
		}
		String nukeDirPath = nukeDir.getPath();

		if (!st.hasMoreTokens()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		try {
			multiplier = Integer.parseInt(st.nextToken());
		} catch (NumberFormatException ex) {
			ex.printStackTrace();
			out.print(
				new FtpResponse(501, "Invalid multiplier: " + ex.getMessage()));
			return;
		}

		String reason;
		if (st.hasMoreTokens()) {
			reason = st.nextToken("").trim();
		} else {
			reason = "";
		}
		//get nukees with string as key
		Hashtable nukees = new Hashtable();
		nukeRemoveCredits(nukeDir, nukees);

		FtpResponse response = new FtpResponse(200, "NUKE suceeded");

		//get nukees User with user as key
		HashMap nukees2 = new HashMap(nukees.size());
		for (Iterator iter = nukees.keySet().iterator(); iter.hasNext();) {

			String username = (String) iter.next();
			User user;
			try {
				user = userManager.getUserByName(username);
			} catch (NoSuchUserException e1) {
				response.addComment(
					"Cannot remove credits from "
						+ username
						+ ": "
						+ e1.getMessage());
				e1.printStackTrace();
				user = null;
			} catch (IOException e1) {
				response.addComment(
					"Cannot read user data for "
						+ username
						+ ": "
						+ e1.getMessage());
				logger.warn("", e1);
				response.setMessage("NUKE failed");
				out.print(response);
				return;
			}
			// nukees contains credits as value
			if (user == null) {
				Long add = (Long) nukees2.get(null);
				if (add == null) {
					add = new Long(0);
				}
				nukees2.put(
					user,
					new Long(
						add.longValue()
							+ ((Long) nukees.get(username)).longValue()));
			} else {
				nukees2.put(user, nukees.get(username));
			}
		}
		//rename
		String toDir;
		String toName = "[NUKED]-" + nukeDir.getName();
		try {
			toDir = nukeDir.getParentFile().getPath();
		} catch (FileNotFoundException ex) {
			logger.fatal("", ex);
			out.print(FtpResponse.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}
		try {
			nukeDir.renameTo(toDir, toName);
		} catch (IOException ex) {
			ex.printStackTrace();
			response.addComment(
				" cannot rename to \""
					+ toDir
					+ "/"
					+ toName
					+ "\": "
					+ ex.getMessage());
			response.setCode(500);
			response.setMessage("NUKE failed");
			out.print(response);
			return;
		}

		long nukeDirSize = 0;
		long nukedAmount = 0;

		//update credits, nukedbytes, timesNuked, lastNuked
		for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
			AbstractUser nukee = (AbstractUser) iter.next();
			if (nukee == null)
				continue;
			Long size = (Long) nukees2.get(nukee);
			Long debt =
				new Long(
					(long) (size.longValue() * nukee.getRatio()
						+ size.longValue() * (multiplier - 1)));
			nukedAmount += debt.longValue();
			nukeDirSize += size.longValue();
			nukee.updateCredits(-debt.longValue());
			nukee.updateNukedBytes(debt.longValue());
			nukee.updateTimesNuked(1);
			nukee.setLastNuked(System.currentTimeMillis());
			try {
				nukee.commit();
			} catch (UserFileException e1) {
				response.addComment(
					"Error writing userfile: " + e1.getMessage());
				logger.log(Level.WARN, "Error writing userfile", e1);
			}
			response.addComment(
				nukee.getUsername()
					+ " -"
					+ Bytes.formatBytes(debt.longValue()));
		}
		NukeEvent nuke =
			new NukeEvent(
				_user,
				"NUKE",
				nukeDirPath,
				nukeDirSize,
				nukedAmount,
				multiplier,
				reason,
				nukees);
		assert this._nukelog != null : "nukelog";
		this._nukelog.add(nuke);
		_cm.dispatchFtpEvent(nuke);
		out.print(response);
	}

	public void doSITE_NUKES(FtpRequest request, PrintWriter out) {
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		for (Iterator iter = _nukelog.getAll().iterator(); iter.hasNext();) {
			response.addComment(iter.next());
		}
		out.print(response);
	}

	public void doSITE_PASSWD(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		_user.setPassword(request.getArgument());
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	/**
	 * Syntax: SITE PRE <RELEASEDIR> [SECTION]
	 * @param request
	 * @param out
	 */
	public void doSITE_PRE(FtpRequest request, PrintWriter out) {
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		String args[] = request.getArgument().split(" ");
		if (args.length != 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		LinkedRemoteFile section;
		try {
			section = currentDirectory.getRoot().lookupFile(args[1]);
		} catch (FileNotFoundException ex) {
			out.print(
				new FtpResponse(
					200,
					"Release dir not found: " + ex.getMessage()));
			return;
		}

		LinkedRemoteFile preDir;
		try {
			preDir = currentDirectory.lookupFile(args[0]);
		} catch (FileNotFoundException e) {
			out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}
		if (!getConfig().checkPre(_user, preDir)) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		FtpResponse response = new FtpResponse(200);

		if (preDir.hasOfflineSlaves()) {
			response.setMessage(
				"Sorry, release has offline files. You don't want to PRE an incomplete release, do you? :(");
			response.setCode(550);
			out.print(response);
			return;
		}
		//AWARD CREDITS
		Hashtable awards = new Hashtable();
		preAwardCredits(preDir, awards);
		for (Iterator iter = awards.entrySet().iterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			User owner = (User) entry.getKey();
			Long award = (Long) entry.getValue();
			response.addComment(
				"Awarded "
					+ Bytes.formatBytes(award.longValue())
					+ " to "
					+ owner.getUsername());
			owner.updateCredits(award.longValue());
		}

		//RENAME
		try {
			preDir.renameTo(section.getPath(), preDir.getName());
		} catch (IOException ex) {
			out.print(new FtpResponse(200, ex.getMessage()));
			logger.warn("", ex);
		}

		//ANNOUNCE
		logger.debug("preDir after rename: " + preDir);
		_cm.dispatchFtpEvent(new DirectoryFtpEvent(_user, "PRE", preDir));

		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_PURGE(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (!_user.isAdmin() && !_user.isGroupAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		String delUsername = request.getArgument();
		User delUser;
		try {
			delUser = this.userManager.getUserByName(delUsername);
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			out.print(
				new FtpResponse(200, "Couldn't getUser: " + e.getMessage()));
			return;
		}
		if(!delUser.isDeleted()) {
			out.print(new FtpResponse(200, "User isn't deleted"));
			return;
		}
		
		if(_user.isGroupAdmin() && !_user.getGroupName().equals(delUser.getGroupName())) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		delUser.purge();
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_READD(FtpRequest request, PrintWriter out) {
		resetState();
		if (!_user.isAdmin() && !_user.isGroupAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User myUser;
		try {
			myUser = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		}
		
		if(_user.isGroupAdmin() && !_user.getGroupName().equals(myUser.getGroupName())) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		
		if (!myUser.isDeleted()) {
			out.print(new FtpResponse(200, "User wasn't deleted"));
			return;
		}
		myUser.setDeleted(false);
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_RELOAD(FtpRequest request, PrintWriter out) {
		resetState();
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		try {
			_cm.getConfig().reloadConfig();
			slaveManager.reloadRSlaves();
			slaveManager.saveFilesXML();
		} catch (IOException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			logger.log(Level.FATAL, "Error reloading config", e);
		}
		_cm.dispatchFtpEvent(new UserEvent(_user, "RELOAD"));
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_RENUSER(FtpRequest request, PrintWriter out) {
		resetState();
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");
		if (args.length != 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		try {
			userManager.getUserByName(args[0]).rename(args[1]);
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, "No such user: " + e.getMessage()));
			return;
		} catch (ObjectExistsException e) {
			out.print(new FtpResponse(200, "Target username is already taken"));
			return;
		} catch (IOException e) {
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		} catch (UserFileException e) {
			out.print(new FtpResponse(200, e.getMessage()));
		}
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	/**
	 * site replic <destslave> <path...>
	 * @param request
	 * @param out
	 */
	// won't work due to the non-interactivitiy of ftp, and due to timeouts
	//	public void doSITE_REPLIC(FtpRequest request, PrintWriter out) {
	//		resetState();
	//		if(!_user.isAdmin()) {
	//			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
	//			return;
	//		}
	//		FtpResponse usage = new FtpResponse(501, "usage: SITE REPLIC <destsave> <path...>");
	//		if(!request.hasArgument()) {
	//			out.print(usage);
	//			return;
	//		}
	//		String args[] = request.getArgument().split(" ");
	//		
	//		if(args.length < 2) {
	//			out.print(usage);
	//			return;
	//		}
	//		
	//		RemoteSlave destRSlave;
	//		try {
	//			destRSlave = slaveManager.getSlave(args[0]);
	//		} catch (ObjectNotFoundException e) {
	//			out.print(new FtpResponse(200, e.getMessage()));
	//			return;
	//		}
	//		//Slave destSlave = destRSlave.getSlave();
	//		
	//		for (int i = 1; i < args.length; i++) {
	//			try {
	//				String arg = args[i];
	//				LinkedRemoteFile file = currentDirectory.lookupFile(arg);
	//				String path = file.getPath();
	//				RemoteSlave srcRSlave =
	//					file.getASlave(Transfer.TRANSFER_SENDING_DOWNLOAD);
	//
	//				Transfer destTransfer =
	//					destRSlave.getSlave().doListenReceive(
	//						file.getParentFile().getPath(),
	//						file.getName(),
	//						0L);
	//				Transfer srcTransfer =
	//					srcRSlave.getSlave().doConnectSend(
	//						file.getPath(),
	//						'I',
	//						0L,
	//						destRSlave.getInetAddress(),
	//						destTransfer.getLocalPort());
	//				TransferThread srcTransferThread = new TransferThread(srcRSlave, srcTransfer);
	//				TransferThread destTransferThread = new TransferThread(destRSlave, destTransfer);
	////				srcTransferThread.interruptibleSleepUntilFinished();
	////				destTransferThread.interruptibleSleepUntilFinished();
	//				while(true) {
	//					out.print("200- "+srcTransfer.getTransfered()+" : "+destTransfer.getTransfered());
	//				}
	//			} catch (Exception e) {
	//				// Handle exception
	//			}
	//		}
	//		
	//	}

	public void doSITE_RESCAN(FtpRequest request, PrintWriter out) {
		resetState();
		boolean forceRescan =
			(request.hasArgument()
				&& request.getArgument().equalsIgnoreCase("force"));
		LinkedRemoteFile directory = currentDirectory;
		SFVFile sfv;
		try {
			sfv = currentDirectory.lookupSFVFile();
		} catch (Exception e) {
			out.print(
				new FtpResponse(
					200,
					"Error getting SFV File: " + e.getMessage()));
			return;
		}
		for (Iterator i = sfv.getEntries().entrySet().iterator();
			i.hasNext();
			) {
			Map.Entry entry = (Map.Entry) i.next();
			String fileName = (String) entry.getKey();
			Long checkSum = (Long) entry.getValue();
			LinkedRemoteFile file;
			try {
				file = directory.lookupFile(fileName);
			} catch (FileNotFoundException ex) {
				out.println("200- " + fileName + " MISSING");
				continue;
			}
			String status;
			long fileCheckSum;
			if (forceRescan) {
				try {
					fileCheckSum = file.getCheckSumFromSlave();
				} catch (NoAvailableSlaveException e1) {
					out.println("200- " + fileName + " NO SLAVE");
					continue;
				}
			} else {
				fileCheckSum = file.getCheckSum(true);
			}
			if (fileCheckSum == 0L) {
				status = "FAILED - failed to checksum file";
			} else if (checkSum.longValue() == fileCheckSum) {
				status = "OK";
			} else {
				status = "FAILED - checksum missmatch";
			}

			out.println(
				"200- "
					+ fileName
					+ " "
					+ Checksum.formatChecksum(checkSum.longValue())
					+ " "
					+ status);
			out.flush();
		}
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
	}
	public void doSITE_RULES(FtpRequest request, PrintWriter out) {
		try {
			out.print(
				new FtpResponse(200).addComment(
					new BufferedReader(
						new FileReader("ftp-data/text/rules.txt"))));
			return;
		} catch (IOException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		}
	}
	public void doSITE_SEARCH(FtpRequest request, PrintWriter out) {
		doSITE_DUPE(request, out);
	}

	public void doSITE_SEEN(FtpRequest request, PrintWriter out) {
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User user;
		try {
			user = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			logger.log(Level.FATAL, "", e);
			out.print(
				new FtpResponse(
					200,
					"Error reading userfile: " + e.getMessage()));
			return;
		}

		out.print(
			new FtpResponse(
				200,
				"User was last seen: " + new Date(user.getLastAccessTime())));
		return;
	}

	public void doSITE_SHUTDOWN(FtpRequest request, PrintWriter out) {
		resetState();
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		String message;
		if (!request.hasArgument()) {
			message = "Service shutdown issued by " + _user.getUsername();
		} else {
			message = request.getArgument();
		}
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		_cm.shutdown(message);
	}

	/** Lists all slaves used by the master
	 * USAGE: SITE SLAVES
	 * 
	 */
	public void doSITE_SLAVES(FtpRequest request, PrintWriter out) {
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		Collection slaves = slaveManager.getSlaves();
		FtpResponse response =
			new FtpResponse(200, "OK, " + slaves.size() + " slaves listed.");

		for (Iterator iter = slaveManager.rslaves.iterator();
			iter.hasNext();
			) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			response.addComment(rslave.toString());
		}
		out.print(response);
	}

	public void doSITE_STAT(FtpRequest request, PrintWriter out) {
		resetState();
		if (request.hasArgument()) {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
			return;
		}
		out.print(new FtpResponse(200, status()));
		return;
	}

	/**
	 * USAGE: site stats [<user>]
	    Display a user's upload/download statistics.
	
	    Definable in '/ftp-data/text/user.stats'
	
	    If you have multiple sections then this will display stats from 
	    all sections.  (But you have to copy this file to SECTIONuser.stats.
	    exmp: if you have a section called GAMES then glftpd will look
	    for the files user.stats and GAMESuser.stats in the /ftp-data/text dir.
	 */
	public void doSITE_STATS(FtpRequest request, PrintWriter out) {
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User user;
		try {
			user = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, "No such user: " + e.getMessage()));
			return;
		} catch (IOException e) {
			logger.log(Level.WARN, "", e);
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		}

		FtpResponse response = new FtpResponse(200);
		response.addComment("bytes up, files up, bytes down, files down");
		response.addComment(
			"total: "
				+ Bytes.formatBytes(user.getUploadedBytes())
				+ " "
				+ user.getUploadedFiles()
				+ "f "
				+ Bytes.formatBytes(user.getDownloadedBytes())
				+ " "
				+ user.getDownloadedFiles()
				+ "f ");
		response.addComment(
			"month: "
				+ Bytes.formatBytes(user.getUploadedBytesMonth())
				+ " "
				+ user.getUploadedFilesMonth()
				+ "f "
				+ Bytes.formatBytes(user.getDownloadedBytesMonth())
				+ " "
				+ user.getDownloadedFilesMonth()
				+ "f ");
		response.addComment(
			"week: "
				+ Bytes.formatBytes(user.getUploadedBytesWeek())
				+ " "
				+ user.getUploadedFilesWeek()
				+ "f "
				+ Bytes.formatBytes(user.getDownloadedBytesWeek())
				+ "b "
				+ user.getDownloadedFilesWeek()
				+ "f ");
		response.addComment(
			"day: "
				+ Bytes.formatBytes(user.getUploadedBytesDay())
				+ "b "
				+ user.getUploadedFilesDay()
				+ "f "
				+ Bytes.formatBytes(user.getDownloadedBytesDay())
				+ "b "
				+ user.getDownloadedFilesDay()
				+ "f ");

		out.print(response);
		return;
	}

	public void doSITE_TAGLINE(FtpRequest request, PrintWriter out) {
		resetState();

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
		}

		_user.setTagline(request.getArgument());
	}

	/**
	 * USAGE: site take <user> <kbytes> [<message>]
	 *        Removes credit from user
	 *
	 *        ex. site take Archimede 100000 haha
	 *
	 *        This will remove 100mb of credits from the user 'Archimede' and 
	 *        send the message haha to him.
	 */
	public void doSITE_TAKE(FtpRequest request, PrintWriter out) {
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (!st.hasMoreTokens()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		//String args[] = request.getArgument().split(" ");
		User user2;
		long credits;

		try {
			user2 = userManager.getUserByName(st.nextToken());
			if (!st.hasMoreTokens()) {
				out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
				return;
			}
			credits = Bytes.parseBytes(st.nextToken()); // B, not KiB
			if (0 > credits) {
				out.print(
					new FtpResponse(200, "Credits must be a positive number."));
				return;
			}
			user2.updateCredits(-credits);
		} catch (Exception ex) {
			out.println("200 " + ex.getMessage());
			return;
		}
		out.println(
			"200 OK, removed "
				+ credits
				+ "b from "
				+ user2.getUsername()
				+ ".");
	}

	public void doSITE_TIME(FtpRequest request, PrintWriter out) {
		resetState();
		if (request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		FtpResponse response = new FtpResponse(200);
		response.setMessage("Server time is: " + new Date());
		out.print(response);
		return;
	}

	/**
	 * USAGE: site unnuke <directory> <message>
	 * 	Unnuke a directory.
	 * 
	 * 	ex. site unnuke shit NOT CRAP
	 * 	
	 * 	This will unnuke the directory 'shit' with the comment 'NOT CRAP'.
	 * 
	 *         NOTE: You can enclose the directory in braces if you have spaces in the name
	 *         ex. site unnuke {My directory name} justcause
	 * 
	 * 	You need to configure glftpd to keep nuked files if you want to unnuke.
	 * 	See the section about glftpd.conf.
	 */
	public void doSITE_UNNUKE(FtpRequest request, PrintWriter out) {
		resetState();

		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (!st.hasMoreTokens()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String toName = st.nextToken();
		String toPath = this.currentDirectory.getPath() + "/" + toName;
		String toDir = currentDirectory.getPath();
		String nukeName = "[NUKED]-" + toName;

		String reason;
		if (st.hasMoreTokens()) {
			reason = st.nextToken("");
		} else {
			reason = "";
		}

		LinkedRemoteFile nukeDir;
		try {
			nukeDir = this.currentDirectory.getFile(nukeName);
		} catch (FileNotFoundException e2) {
			out.print(
				new FtpResponse(
					200,
					nukeName + " doesn't exist: " + e2.getMessage()));
			return;
		}

		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		NukeEvent nuke;
		try {
			nuke = _nukelog.get(toPath);
		} catch (ObjectNotFoundException ex) {
			response.addComment(ex.getMessage());
			out.print(response);
			return;
		}

		//Map nukees2 = new Hashtable();
		for (Iterator iter = nuke.getNukees().entrySet().iterator();
			iter.hasNext();
			) {
			Map.Entry entry = (Map.Entry) iter.next();
			String nukeeName = (String) entry.getKey();
			Long amount = (Long) entry.getValue();
			User nukee;
			try {
				nukee = userManager.getUserByName(nukeeName);
			} catch (NoSuchUserException e) {
				response.addComment(nukeeName + ": no such user");
				continue;
			} catch (IOException e) {
				response.addComment(nukeeName + ": error reading userfile");
				logger.log(Level.FATAL, "error reading userfile", e);
				continue;
			}

			nukee.updateCredits(amount.longValue());
			nukee.updateTimesNuked(1);
			try {
				nukee.commit();
			} catch (UserFileException e3) {
				logger.log(
					Level.FATAL,
					"Eroror saveing userfile for " + nukee.getUsername(),
					e3);
				response.addComment(
					"Error saving userfile for " + nukee.getUsername());
			}

			response.addComment(nukeeName + ": restored " + amount + "bytes");
		}
		try {
			_nukelog.remove(toPath);
		} catch (ObjectNotFoundException e) {
			response.addComment("Error removing nukelog entry");
		}
		try {
			nukeDir.renameTo(toDir, toName);
		} catch (ObjectExistsException e1) {
			response.addComment(
				"Error renaming nuke, target dir already exists");
		} catch (IOException e1) {
			response.addComment("Error: " + e1.getMessage());
			logger.log(
				Level.FATAL,
				"Illegaltargetexception: means parent doesn't exist",
				e1);
		}
		out.print(response);
		nuke.setCommand("UNNUKE");
		nuke.setReason(reason);
		_cm.dispatchFtpEvent(nuke);
		return;
	}

	/**
	 * USAGE: site user [<user>]
	 * 	Lists users / Shows detailed info about a user.
	 * 
	 * 	ex. site user
	 * 
	 * 	This will display a list of all users currently on site.
	 * 
	 * 	ex. site user Archimede
	 * 
	 * 	This will show detailed information about user 'Archimede'.
	 */
	public void doSITE_USER(FtpRequest request, PrintWriter out) {
		resetState();
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();

		User myUser;
		try {
			myUser = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			response.setMessage("User " + request.getArgument() + " not found");
			out.print(response.toString());
			//out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
			return;
		} catch (IOException ex) {
			out.print(new FtpResponse(200, ex.getMessage()));
			return;
		}

		response.addComment("comment: " + myUser.getComment());
		response.addComment("username: " + myUser.getUsername());
		int i = (int) (myUser.getTimeToday() / 1000);
		int hours = i / 60;
		int minutes = i - hours * 60;
		response.addComment(
			"last seen: " + new Date(myUser.getLastAccessTime()));
		response.addComment("time on today: " + hours + ":" + minutes);
		response.addComment("ratio: " + myUser.getRatio());
		response.addComment(
			"credits: " + Bytes.formatBytes(myUser.getCredits()));
		response.addComment("group: " + myUser.getGroupName());
		response.addComment("groups: " + myUser.getGroups());
		response.addComment("ip masks: " + myUser.getIpMasks());
		out.print(response);
	}

	public void doSITE_USERS(FtpRequest request, PrintWriter out) {
		resetState();

		if (request.hasArgument()) {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
			return;
		}
		FtpResponse response = new FtpResponse(200);
		Collection myUsers;
		try {
			myUsers = userManager.getAllUsers();
		} catch (IOException e) {
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			logger.log(Level.FATAL, "IO error reading all users", e);
			return;
		}
		for (Iterator iter = myUsers.iterator(); iter.hasNext();) {
			User myUser = (User) iter.next();
			response.addComment(myUser.getUsername());
		}
		out.print(response);
		return;
	}

	public void doSITE_WELCOME(FtpRequest request, PrintWriter out) {
		resetState();

		if (request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		FtpResponse response = new FtpResponse(200);

		try {
			//			response.addComment(
			//				new BufferedReader(
			//					new FileReader("ftp-data/text/welcome.txt")));
			_cm.getConfig().welcomeMessage(response);
		} catch (IOException e) {
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		}
		out.print(response);
		return;
	}

	public void doSITE_VERS(FtpRequest request, PrintWriter out) {
		resetState();
		out.print(new FtpResponse(200, ConnectionManager.VERSION));
		return;
	}

	/**
	 * Lists currently connected users.
	 */
	public void doSITE_WHO(FtpRequest request, PrintWriter out) {
		resetState();

		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		//FtpResponse response = new FtpResponse(200);
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		//		for (Iterator i = _cm.getConnections().iterator();
		//			i.hasNext();
		//			) {
		//			FtpConnection conn = (FtpConnection) i.next();
		//			if (!conn.isAuthenticated()) {
		//				response.addComment("Not yet authenticated");
		//				continue;
		//			}
		//			User user;
		//			try {
		//				user = conn.getUser();
		//			} catch (NoSuchUserException e) {
		//				throw new FatalException(e);
		//			}
		//			String command = conn.getRequest().getCommand();
		//			String username = user.getUsername();
		//
		//			if (getConfig().checkHideInWho(user, conn.getCurrentDirectory())) {
		//				continue;
		//			}
		//			if (conn.isExecuting()) {
		//				if (conn.getRequest().getCommand().equals("RETR")) {
		//					response.addComment(username + "    DL");
		//				} else if (command.equals("STOR")) {
		//					response.addComment(username + "    UL");
		//				} else {
		//					response.addComment(
		//						username + "    " + conn.getRequest().getCommand());
		//				}
		//
		//			} else {
		//				response.addComment(username + "    IDLE");
		//			}
		//		}

		try {
			ReplacerFormat formatup = getConfig().getReplacerFormat("who.up");
			ReplacerFormat formatdown =
				getConfig().getReplacerFormat("who.down");
			ReplacerFormat formatidle =
				getConfig().getReplacerFormat("who.idle");

			ReplacerEnvironment env = new ReplacerEnvironment();

			//TODO synchronized access to connections ??
			for (Iterator iter =
				getConnectionManager().getConnections().iterator();
				iter.hasNext();
				) {
				BaseFtpConnection conn = (BaseFtpConnection) iter.next();
				if (conn.isAuthenticated()) {
					User user;
					try {
						user = conn.getUser();
					} catch (NoSuchUserException e) {
						continue;
					}
					if (getConfig()
						.checkHideInWho(user, conn.getCurrentDirectory()))
						continue;
					StringBuffer status = new StringBuffer();
					env.add(
						"idle",
						(System.currentTimeMillis() - conn.getLastActive())
							/ 1000
							+ "s");
					env.add("user", user.getUsername());

					if (!conn.isExecuting()) {
						status.append(SimplePrintf.jprintf(formatidle, env));

					} else if (conn.isTransfering()) {
						if (conn.isTransfering()) {
							try {
								env.add(
									"speed",
									Bytes.formatBytes(
										conn.getTransfer().getXferSpeed())
										+ "/s");
							} catch (RemoteException e2) {
								logger.warn("", e2);
							}
							env.add("file", conn.getTransferFile().getName());
							env.add("slave", conn.getTranferSlave().getName());
						}

						if (conn.getTransferDirection()
							== Transfer.TRANSFER_RECEIVING_UPLOAD) {
							response.addComment(
								SimplePrintf.jprintf(formatup, env));

						} else if (
							conn.getTransferDirection()
								== Transfer.TRANSFER_SENDING_DOWNLOAD) {
							response.addComment(
								SimplePrintf.jprintf(formatdown, env));
						}
					}
					response.addComment(status.toString());
				}
			}
			out.print(response.toString());
			return;
		} catch (FormatterException e) {
			out.print(new FtpResponse(200, e.getMessage()));
		}
	}

	/**
	 * 
	 */
	public ConnectionManager getConnectionManager() {
		return _cm;
	}

	/**
	 * USAGE: site wipe [-r] <file/directory>
	 *                                                                                 
	 *         This is similar to the UNIX rm command.
	 *         In glftpd, if you just delete a file, the uploader loses credits and
	 *         upload stats for it.  There are many people who didn't like that and
	 *         were unable/too lazy to write a shell script to do it for them, so I
	 *         wrote this command to get them off my back.
	 *                                                                                 
	 *         If the argument is a file, it will simply be deleted. If it's a
	 *         directory, it and the files it contains will be deleted.  If the
	 *         directory contains other directories, the deletion will be aborted.
	 *                                                                                 
	 *         To remove a directory containing subdirectories, you need to use
	 *         "site wipe -r dirname". BE CAREFUL WHO YOU GIVE ACCESS TO THIS COMMAND.
	 *         Glftpd will check if the parent directory of the file/directory you're
	 *         trying to delete is writable by its owner. If not, wipe will not
	 *         execute, so to protect directories from being wiped, make their parent
	 *         555.
	 *                                                                                 
	 *         Also, wipe will only work where you have the right to delete (in
	 *         glftpd.conf). Delete right and parent directory's mode of 755/777/etc
	 *         will cause glftpd to SWITCH TO ROOT UID and wipe the file/directory.
	 *         "site wipe -r /" will not work, but "site wipe -r /incoming" WILL, SO
	 *         BE CAREFUL.
	 *                                                                                 
	 *         This command will remove the deleted files/directories from the dirlog
	 *         and dupefile databases.
	 *                                                                                 
	 *         To give access to this command, add "-wipe -user flag =group" to the
	 *         config file (similar to other site commands).
	 * 
	 * @param request
	 * @param out
	 */
	public void doSITE_WIPE(FtpRequest request, PrintWriter out) {
		resetState();
		if (!_user.isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String arg = request.getArgument();

		boolean recursive;
		if (arg.startsWith("-r ")) {
			arg = arg.substring(3);
			recursive = true;
		} else {
			recursive = false;
		}

		LinkedRemoteFile wipeFile;
		try {
			wipeFile = currentDirectory.lookupFile(arg);
		} catch (FileNotFoundException e) {
			FtpResponse response =
				new FtpResponse(
					200,
					"Can't wipe: "
						+ arg
						+ " does not exist or it's not a plain file/directory");
			out.print(response);
			return;
		}
		if (wipeFile.isDirectory() && wipeFile.dirSize() != 0 && !recursive) {
			out.print(new FtpResponse(200, "Can't wipe, directory not empty"));
			return;
		}
		if (!getConfig().checkHideInWho(_user, wipeFile)) {
			_cm.dispatchFtpEvent(
				new DirectoryFtpEvent(_user, "WIPE", wipeFile));
		}
		wipeFile.delete();
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_XDUPE(FtpRequest request, PrintWriter out) {
		out.print(FtpResponse.RESPONSE_502_COMMAND_NOT_IMPLEMENTED);
		//		resetState();
		//
		//		if (!request.hasArgument()) {
		//			if (this.xdupe == 0) {
		//				out.println("200 Extended dupe mode is disabled.");
		//			} else {
		//				out.println(
		//					"200 Extended dupe mode " + this.xdupe + " is enabled.");
		//			}
		//			return;
		//		}
		//
		//		short myXdupe;
		//		try {
		//			myXdupe = Short.parseShort(request.getArgument());
		//		} catch (NumberFormatException ex) {
		//			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
		//			return;
		//		}
		//
		//		if (myXdupe > 0 || myXdupe < 4) {
		//			out.print(
		//				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		//			return;
		//		}
		//		this.xdupe = myXdupe;
		//		out.println("200 Activated extended dupe mode " + myXdupe + ".");
	}

	/**
	 * <code>SIZE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * Returns the size of the file in bytes.
	 */
	public void doSIZE(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		LinkedRemoteFile file;
		try {
			file = currentDirectory.lookupFile(request.getArgument());
			//file = getVirtualDirectory().lookupFile(request.getArgument());
		} catch (FileNotFoundException ex) {
			out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}
		if (file == null) {
			System.out.println(
				"got null file instead of FileNotFoundException");
		}
		out.print(new FtpResponse(213, Long.toString(file.length())));
	}

	/**
	 * <code>STAT [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command shall cause a status response to be sent over
	 * the control connection in the form of a reply.
	 */
	public void doSTAT(FtpRequest request, PrintWriter out) {
		reset();
		if (request.hasArgument()) {
			doLIST(request, out);
		} else {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		}
		return;
	}

	/**
	 * <code>STOR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to accept the data
	 * transferred via the data connection and to store the data as
	 * a file at the server site.  If the file specified in the
	 * pathname exists at the server site, then its contents shall
	 * be replaced by the data being transferred.  A new file is
	 * created at the server site if the file specified in the
	 * pathname does not already exist.
	 * 
	 *                STOR
	              125, 150
	                 (110)
	                 226, 250
	                 425, 426, 451, 551, 552
	              532, 450, 452, 553
	              500, 501, 421, 530
	 * 
	 * ''zipscript�� renames bad uploads to .bad, how do we handle this with resumes?
	 */
	public void doSTOR(FtpRequest request, PrintWriter out) {
		long resumePosition = this.resumePosition;
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		//SETUP targetDir and targetFilename
		Object ret[] =
			currentDirectory.lookupNonExistingFile(request.getArgument());
		LinkedRemoteFile targetDir = (LinkedRemoteFile) ret[0];
		String targetFilename = (String) ret[1];

		if (targetFilename == null) {
			// target exists, this could be overwrite or resume
			// if(resumePosition != 0) {} // resume
			//TODO overwrite & resume files.

			out.print(
				new FtpResponse(
					550,
					"Requested action not taken. File exists"));
			return;
			//_transferFile = targetDir;
			//targetDir = _transferFile.getParent();
			//			if(_transfereFile.getOwner().equals(getUser().getUsername())) {
			//				// allow overwrite/resume
			//			}
			//			if(directory.isDirectory()) {
			//				out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			//			}
		}

		if (!VirtualDirectory.isLegalFileName(targetFilename)
			|| !getConfig().checkPrivPath(_user, targetDir)) {
			out.print(
				new FtpResponse(
					553,
					"Requested action not taken. File name not allowed."));
			return;
		}

		if (!getConfig().checkUpload(_user, targetDir)) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		//SETUP rslave
		if (preTransfer) {
			assert preTransferRSlave != null : "preTransferRSlave";
			_rslave = preTransferRSlave;
			preTransferRSlave = null;
			preTransfer = false;
		} else {
			try {
				_rslave =
					slaveManager.getASlave(Transfer.TRANSFER_RECEIVING_UPLOAD);
				assert _rslave != null : "_rslave";
			} catch (NoAvailableSlaveException ex) {
				logger.log(Level.FATAL, ex.getMessage());
				out.print(FtpResponse.RESPONSE_450_SLAVE_UNAVAILABLE);
				return;
			}
		}
		assert _rslave != null : "_rslave2";
		List rslaves = Collections.singletonList(_rslave);
		//		ArrayList rslaves = new ArrayList();
		//		rslaves.add(rslave);
		StaticRemoteFile uploadFile =
			new StaticRemoteFile(
				rslaves,
				targetFilename,
				_user.getUsername(),
				_user.getGroupName(),
				0L,
				System.currentTimeMillis(),
				0L);
		_transferFile = targetDir.addFile(uploadFile);

		//SETUP transfer
		if (mbPort) {
			try {
				_transfer =
					_rslave.getSlave().connect(getInetAddress(), getPort());
			} catch (RemoteException e1) {
				_rslave.handleRemoteException(e1);
				out.print(new FtpResponse(451, e1.getMessage()));
				return;
			} catch (NoAvailableSlaveException e1) {
				out.print(FtpResponse.RESPONSE_450_SLAVE_UNAVAILABLE);
				return;
			}
		} else {
			assert mbPasv : "mbPasv";
			// _transfer is already set up
		}

		// say we are ready to start sending

		out.print(
			new FtpResponse(
				150,
				"File status okay, about to open data connection to "
					+ _rslave.getName()
					+ "."));
		out.flush();

		// connect and start transfer
		try {
			_transfer.uploadFile(
				targetDir.getPath(),
				targetFilename,
				resumePosition);
		} catch (RemoteException ex) {
			out.print(
				new FtpResponse(
					426,
					"Remote error from " + _rslave + ": " + ex.getMessage()));
			_rslave.handleRemoteException(ex);
			_transferFile.delete();
			return;
		} catch (IOException ex) {
			//TODO let user resume
			_transferFile.delete();
			out.print(
				new FtpResponse(
					426,
					"IO Error from "
						+ _rslave.getName()
						+ ": "
						+ ex.getMessage()));
			logger.log(Level.WARN, "from " + _rslave.getName(), ex);
			return;
		}

		long transferedBytes;
		long checksum;
		try {
			transferedBytes = _transfer.getTransfered();
			// throws RemoteException
			if (resumePosition == 0) {
				checksum = _transfer.getChecksum();
				_transferFile.setCheckSum(checksum);
			} else {
				checksum = _transferFile.getCheckSum();
			}
			_transferFile.setLastModified(System.currentTimeMillis());
			_transferFile.setLength(transferedBytes);
			_transferFile.setXfertime(_transfer.getTransferTime());
		} catch (RemoteException ex) {
			_rslave.handleRemoteException(ex);
			out.print(
				new FtpResponse(
					426,
					"Error communicationg with slave: " + ex.getMessage()));
			return;
		}

		FtpResponse response =
			(FtpResponse) FtpResponse
				.RESPONSE_226_CLOSING_DATA_CONNECTION
				.clone();
		response.addComment(Bytes.formatBytes(transferedBytes) + " transfered");
		if (!targetFilename.toLowerCase().endsWith(".sfv")) {
			try {
				long sfvChecksum =
					targetDir.lookupSFVFile().getChecksum(targetFilename);
				if (checksum == sfvChecksum) {
					response.addComment(
						"zipscript - checksum match: "
							+ Long.toHexString(checksum));
				} else {
					response.addComment(
						"zipscript - checksum mismatch: "
							+ Long.toHexString(checksum)
							+ " != "
							+ Long.toHexString(sfvChecksum));
					response.addComment(" deleting file");
					response.setMessage("Checksum mismatch, deleting file");
					_transferFile.delete();

					//				getUser().updateCredits(
					//					- ((long) getUser().getRatio() * transferedBytes));
					//				getUser().updateUploadedBytes(-transferedBytes);
					response.addComment(status());
					out.print(response);
					return;
					//				String badtargetFilename = targetFilename + ".bad";
					//
					//				try {
					//					LinkedRemoteFile badtargetFile =
					//						targetDir.getFile(badtargetFilename);
					//					badtargetFile.delete();
					//					response.addComment(
					//						"zipscript - removing "
					//							+ badtargetFilename
					//							+ " to be replaced with new file");
					//				} catch (FileNotFoundException e2) {
					//					//good, continue...
					//					response.addComment(
					//						"zipscript - checksum mismatch, renaming to "
					//							+ badtargetFilename);
					//				}
					//				targetFile.renameTo(targetDir.getPath() + badtargetFilename);
				}
			} catch (NoAvailableSlaveException e) {
				response.addComment(
					"zipscript - SFV unavailable, slave with .sfv file is offline");
			} catch (ObjectNotFoundException e) {
				response.addComment(
					"zipscript - SFV unavailable, no .sfv file in directory");
			} catch (IOException e) {
				response.addComment(
					"zipscript - SFV unavailable, IO error: " + e.getMessage());
			}
		}
		//TODO creditcheck
		_user.updateCredits((long) (_user.getRatio() * transferedBytes));
		_user.updateUploadedBytes(transferedBytes);
		_user.updateUploadedFiles(1);
		try {
			_user.commit();
		} catch (UserFileException e1) {
			response.addComment("Error saving userfile: " + e1.getMessage());
		}

		if (getConfig().checkDirLog(_user, _transferFile)) {
			_cm.dispatchFtpEvent(
				new TransferEvent(
					_user,
					"STOR",
					_transferFile,
					getClientAddress(),
					_rslave.getInetAddress(),
					getType(),
					true));
		}

		response.addComment(status());
		out.print(response);
		return;
	}

	/**
	 * <code>STOU &lt;CRLF&gt;</code><br>
	 *
	 * This command behaves like STOR except that the resultant
	 * file is to be created in the current directory under a name
	 * unique to that directory.  The 250 Transfer Started response
	 * must include the name generated.
	 */
	//TODO STOU
	/*
	public void doSTOU(FtpRequest request, PrintWriter out) {
	    
	    // reset state variables
	    resetState();
	    
	    // get filenames
	    String fileName = user.getVirtualDirectory().getAbsoluteName("ftp.dat");
	    String physicalName = user.getVirtualDirectory().getPhysicalName(fileName);
	    File requestedFile = new File(physicalName);
	    requestedFile = IoUtils.getUniqueFile(requestedFile);
	    fileName = user.getVirtualDirectory().getVirtualName(requestedFile.getAbsolutePath());
	    String args[] = {fileName};
	    
	    // check permission
	    if(!user.getVirtualDirectory().hasCreatePermission(fileName, false)) {
	        out.write(ftpStatus.getResponse(550, request, user, null));
	        return;
	    }
	    
	    // now transfer file data
	    out.print(FtpResponse.RESPONSE_150_OK);
	    InputStream is = null;
	    OutputStream os = null;
	    try {
	        Socket dataSoc = mDataConnection.getDataSocket();
	        if (dataSoc == null) {
	             out.write(ftpStatus.getResponse(550, request, user, args));
	             return;
	        }
	
	        
	        is = dataSoc.getInputStream();
	        os = user.getOutputStream( new FileOutputStream(requestedFile) );
	        
	        StreamConnector msc = new StreamConnector(is, os);
	        msc.setMaxTransferRate(user.getMaxUploadRate());
	        msc.setObserver(this);
	        msc.connect();
	        
	        if(msc.hasException()) {
	            out.write(ftpStatus.getResponse(451, request, user, null));
	            return;
	        }
	        else {
	            mConfig.getStatistics().setUpload(requestedFile, user, msc.getTransferredSize());
	        }
	        
	        out.write(ftpStatus.getResponse(226, request, user, null));
	        mDataConnection.reset();
	        out.write(ftpStatus.getResponse(250, request, user, args));
	    }
	    catch(IOException ex) {
	        out.write(ftpStatus.getResponse(425, request, user, null));
	    }
	    finally {
	       IoUtils.close(is);
	       IoUtils.close(os);
	       mDataConnection.reset(); 
	    }
	}
	*/

	/**
	 * <code>STRU &lt;SP&gt; &lt;structure-code&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a single Telnet character code specifying
	 * file structure.
	 */
	public void doSTRU(FtpRequest request, PrintWriter out) {
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (request.getArgument().equalsIgnoreCase("F")) {
			out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		} else {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		}
		/*
				if (setStructure(request.getArgument().charAt(0))) {
					out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
				} else {
					out.print(FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
				}
		*/
	}

	/**
	 * <code>SYST &lt;CRLF&gt;</code><br> 
	 *
	 * This command is used to find out the type of operating
	 * system at the server.
	 */
	public void doSYST(FtpRequest request, PrintWriter out) {
		resetState();

		/*
		String systemName = System.getProperty("os.name");
		if(systemName == null) {
		    systemName = "UNKNOWN";
		}
		else {
		    systemName = systemName.toUpperCase();
		    systemName = systemName.replace(' ', '-');
		}
		String args[] = {systemName};
		*/
		out.print(FtpResponse.RESPONSE_215_SYSTEM_TYPE);
		//String args[] = { "UNIX" };
		//out.write(ftpStatus.getResponse(215, request, user, args));
	}

	/**
	 * <code>TYPE &lt;SP&gt; &lt;type-code&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument specifies the representation type.
	 */
	public void doTYPE(FtpRequest request, PrintWriter out) {
		resetState();

		// get type from argument
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// set it
		if (setType(request.getArgument().charAt(0))) {
			out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		} else {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		}
	}

	/**
	 * <code>USER &lt;SP&gt; &lt;username&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument field is a Telnet string identifying the user.
	 * The user identification is that which is required by the
	 * server for access to its file system.  This command will
	 * normally be the first command transmitted by the user after
	 * the control connections are made.
	 */
	public void doUSER(FtpRequest request, PrintWriter out) {
		resetState();
		this.authenticated = false;
		this._user = null;

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		Ident id = new Ident(controlSocket);
		String ident;
		if (id.successful) {
			ident = id.userName;
		} else {
			ident = "";
			System.out.println(
				"Failed to get ident response: " + id.errorMessage);
		}
		User newUser;
		try {
			newUser = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			out.print(new FtpResponse(530, ex.getMessage()));
			return;
		} catch (IOException ex) {
			logger.warn("", ex);
			out.print(new FtpResponse(530, "IOException: " + ex.getMessage()));
			return;
		} catch (RuntimeException ex) {
			logger.error("", ex);
			out.print(new FtpResponse(530, ex.getMessage()));
			return;
		}

		if (newUser.isDeleted()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		//		if(connManager.isShutdown() && !_user.isAdmin()) {
		//			out.print(new FtpResponse(421, ))
		//		}

		String masks[] =
			{
				ident + "@" + getClientAddress().getHostAddress(),
				ident + "@" + getClientAddress().getHostName()};

		if (!newUser.checkIP(masks)) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!slaveManager.hasAvailableSlaves() && !newUser.isAdmin()) {
			out.print(
				new FtpResponse(
					530,
					"No transfer slave(s) available, try again later."));
			return;
		}

		_user = newUser;
		out.write(
			new FtpResponse(
				331,
				"Password required for " + _user.getUsername())
				.toString());
	}

	public FtpConfig getConfig() {
		return _cm.getConfig();
	}

	/**
	 * Get output stream. Returns <code>ftpserver.util.AsciiOutputStream</code>
	 * if the transfer type is ASCII.
	 */
	public OutputStream getOutputStream(OutputStream os) {
		//os = IoUtils.getBufferedOutputStream(os);
		if (type == 'A') {
			os = new AsciiOutputStream(os);
		}
		return os;
	}
	/**
	 * Get the user data type.
	 */
	public char getType() {
		return type;
	}

	/**
	 * @param preDir
	 */
	private void preAwardCredits(LinkedRemoteFile preDir, Hashtable awards) {
		for (Iterator iter = preDir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			User owner;
			try {
				owner = userManager.getUserByName(file.getUsername());
			} catch (NoSuchUserException e) {
				logger.log(
					Level.INFO,
					"PRE: Cannot award credits to non-existing user",
					e);
				continue;
			} catch (IOException e) {
				logger.log(Level.WARN, "", e);
				continue;
			}
			Long total = (Long) awards.get(owner);
			if (total == null)
				total = new Long(0);
			total =
				new Long(
					total.longValue()
						+ (long) (file.length() * owner.getRatio()));
			awards.put(owner, total);
		}
	}

	/**
	 * Is an anonymous user?
	 */
	//	public boolean getIsAnonymous() {
	//		return ANONYMOUS.equals(getUsername());
	//
	//	}

	/**
	 * Check the user permission to execute this command.
	 */
	/*
	protected boolean hasPermission(FtpRequest request) {
		String cmd = request.getCommand();
		return user.hasLoggedIn()
			|| cmd.equals("USER")
			|| cmd.equals("PASS")
			|| cmd.equals("HELP");
	}
	*/
	/**
	 * Reset temporary state variables.
	 * mstRenFr and resumePosition
	 */
	private void resetState() {
		_renameFrom = null;

		//		mbReset = false;
		resumePosition = 0;

		//mbUser = false;
		//mbPass = false;
	}

	/**
	 * Set the data type. Supported types are A (ascii) and I (binary).
	 * @return true if success
	 */
	public boolean setType(char type) {
		type = Character.toUpperCase(type);
		if ((type != 'A') && (type != 'I')) {
			return false;
		}
		this.type = type;
		return true;
	}

	/** returns a one-line status line
	 */
	protected String status() {
		return " [Credits: "
			+ Bytes.formatBytes(_user.getCredits())
			+ "] [Ratio: 1:"
			+ _user.getRatio()
			+ "] [Disk free: "
			+ Bytes.formatBytes(
				slaveManager.getAllStatus().getDiskSpaceAvailable())
			+ "]";
	}
}
