package net.sf.drftpd.master;

import net.sf.drftpd.LinkedRemoteFile;
import java.io.File;
import java.io.Writer;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Iterator;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.EOFException;

import java.util.Hashtable;
//import ranab.io.FileRegularFilter;
//import ranab.util.DateUtils;

/**
 * This class is responsible to handle all virtual directory activities.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */
public class VirtualDirectory {

	private final static String NEWLINE = "\r\n";
	private final static String DELIM = " ";

	private String mstRoot = "/";
	private String mstCurrDir = "/";

	private LinkedRemoteFile root;

	/**
	 * Default constructor does nothing
	 */
	public VirtualDirectory(LinkedRemoteFile root) {
		this.root = root;
	}

	/**
	 * Set root directory. Root directory string will always
	 * end with '/'.
	 */
	/*
	public void setRootDirectory(String root) {
	   if(root == null) {
	       root = new File("/");
	   }
	   mstRoot = normalizeSeparateChar(root.getAbsolutePath());
	    
	   // if not ends with '/' - add one
	   if(mstRoot.charAt(mstRoot.length()-1) != '/') {
	       mstRoot = mstRoot + '/';
	   }
	   mstCurrDir = "/";
	}
	*/

	/**
	 * Set root directory.
	 */
	public void setRootDirectory(String root) throws IOException {
		//File rootFile = new File(root).getCanonicalFile();
		//setRootDirectory(rootFile);

		mstRoot = normalizeSeparateChar(root);

		// if not ends with '/' - add one
		if (mstRoot.charAt(mstRoot.length() - 1) != '/') {
			mstRoot = mstRoot + '/';
		}
		mstCurrDir = "/";
	}

	/**
	 * Get current working directory.
	 */
	public String getCurrentDirectoryName() {
		return mstCurrDir;
	}

	public LinkedRemoteFile getCurrentDirectory() {
		try {
			return root.lookupFile(mstCurrDir);
		} catch(FileNotFoundException ex) {
			throw new RuntimeException(ex.toString());
		}
	}

	/**
	 * Get root directory.
	 */
	public String getRootDirectory() {
		return mstRoot;
	}

	/**
	 * Get physical name (wrt the machine root).
	 */
	public String getPhysicalName(String virtualName) {
		virtualName = normalizeSeparateChar(virtualName);
		return replaceDots(virtualName);
	}

	/**
	 * Get virtual name (wrt the virtual root). 
	 * The return value will never end with '/' unless it is '/'. 
	 */
	public String getAbsoluteName(String virtualName) {
		virtualName = normalizeSeparateChar(virtualName);
		String physicalName = replaceDots(virtualName);

		String absoluteName =
			physicalName.substring(mstRoot.length() - 1).trim();
		return removeLastSlash(absoluteName);
	}

	public LinkedRemoteFile getAbsoluteFile(String virtualName) throws FileNotFoundException {
		return root.lookupFile(getAbsoluteName(virtualName));
	}
	/**
	 * Get virtual name (wrt the virtual root). The virtual
	 * name will never end with '/' unless it is '/'. 
	 */
	public String getVirtualName(String physicalName) {
		physicalName = normalizeSeparateChar(physicalName);
		if (!physicalName.startsWith(mstRoot)) {
			return null;
		}

		String virtualName =
			physicalName.substring(mstRoot.length() - 1).trim();
		return removeLastSlash(virtualName);
	}

	/**
	 * Change directory. The current directory will never have '/'
	 * at the end unless it is '/'.
	 * @param dirName change the current working directory.
	 * @return true if success
	 */
	public boolean changeDirectory(String virtualDir) {

		String physicalDir = getPhysicalName(virtualDir);
		if (physicalDir.equals("")) {
			physicalDir = "/";
		}

		StringTokenizer st = new StringTokenizer(physicalDir, "/");
		/*
		     Map dir = filesystem;
		while(st.hasMoreTokens()) {
		    String nextToken = st.nextToken();
		    System.out.println(nextToken);
		    Object o = dir.get(nextToken);
		    if(o == null) return false;
		    if(!(o instanceof Map)) return false;
		    dir = (Map)o;
		}
		     */
		LinkedRemoteFile dirFl;
		try {
			dirFl = root.lookupFile(physicalDir);
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
			return false;
		}
		if (dirFl.isDirectory()) {
			mstCurrDir = physicalDir.substring(mstRoot.length() - 1).trim();
			mstCurrDir = removeLastSlash(mstCurrDir);
			return true;
		}
		return false;
	}

	/**
	 * Check read permission.
	 */
	public boolean hasReadPermission(String fileName, boolean bPhysical) {
		/*
		    if(bPhysical) {
		        fileName = normalizeSeparateChar(fileName);
		    }
		    else {
		        fileName = getPhysicalName(fileName);
		    }
		
		    if(!fileName.startsWith(mstRoot)) {
		        return false;
		    }
		
		    return new File(fileName).canRead();
		*/
		return true;
	}

	/**
	 * Chech file write/delete permission.
	 */
	public boolean hasWritePermission(String fileName, boolean bPhysical) {

		// if virtual name - get the physical name
		if (bPhysical) {
			fileName = normalizeSeparateChar(fileName);
		} else {
			fileName = getPhysicalName(fileName);
		}

		if (!fileName.startsWith(mstRoot)) {
			return false;
		}

		return new File(fileName).canWrite();
	}

	/**
	 * Check file create permission.
	 */
	public boolean hasCreatePermission(String fileName, boolean bPhysical) {

		// if virtual name - get the physical name
		if (bPhysical) {
			fileName = normalizeSeparateChar(fileName);
		} else {
			fileName = getPhysicalName(fileName);
		}

		return fileName.startsWith(mstRoot);
	}

	/**
	 * Print file list. Detail listing.
	 * <pre>
	 *   -a : display all (including hidden files)
	 * </pre>
	 * @return true if success
	 */
	public boolean printList(String argument, Writer out) throws IOException {
		String lsDirName = "./";
		String options = "";
		String pattern = "*";

		// get options, directory name and pattern
		if (argument != null) {
			argument = argument.trim();
			StringBuffer optionsSb = new StringBuffer(4);
			StringTokenizer st = new StringTokenizer(argument, " ");
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				if (token.charAt(0) == '-') {
					if (token.length() > 1) {
						optionsSb.append(token.substring(1));
					}
				} else {
					lsDirName = token;
				}
			}
			options = optionsSb.toString();
		}

		// check options
		boolean bAll = options.indexOf('a') != -1;
		boolean bDetail = options.indexOf('l') != -1;

		// check pattern
		lsDirName = getPhysicalName(lsDirName);
		int slashIndex = lsDirName.lastIndexOf('/');
		if ((slashIndex != -1) && (slashIndex != (lsDirName.length() - 1))) {
			pattern = lsDirName.substring(slashIndex + 1);
			lsDirName = lsDirName.substring(0, slashIndex + 1);
		}

		/*
		Map lstDirObj = filesystem;
		StringTokenizer st = new StringTokenizer(lsDirName, "/");
		while(st.hasMoreTokens()) {
		String nextToken = st.nextToken();
		System.out.println(nextToken);
		Object obj = lstDirObj.get(nextToken);
		if(lstDirObj==null) return false;
		if(!(lstDirObj instanceof Map)) return false;
		lstDirObj = (Map)obj;
		}*/
		LinkedRemoteFile lstDirObj = root.lookupFile(lsDirName);
		if (lstDirObj == null) {
			return false;
		}
		/*
		    // check directory
		    File lstDirObj = new File(lsDirName);
		    if(!lstDirObj.exists()) {
		        return false;
		    }
		*/

		if (!lstDirObj.isDirectory()) {
			return false;
		}

		// get file list
		LinkedRemoteFile flLst[];
		//if ( (pattern == null) || pattern.equals("*") || pattern.equals("") ) {
		//    flLst = lstDirObj.listFiles();
		//} else {
		flLst = lstDirObj.listFiles(); //new FileRegularFilter(pattern));
		//}
		//Iterator i = lstDirObj.entrySet().iterator();
		// print file list
		System.out.println("Listing " + lstDirObj);
		if (flLst != null) {
			for (int i = 0; i < flLst.length; i++) {
				if ((!bAll) && flLst[i].isHidden()) {
					continue;
				}
				System.out.println("List " + flLst[i]);
				printLine(flLst[i], out);
			}
		}
		System.out.println("Listed.");
		return true;
	}

	/**
	 * Print file list.
	 * <pre>
	 *   -l : detail listing
	 *   -a : display all (including hidden files)
	 * </pre>
	 * @return true if success
	 */
	public boolean printNList(String argument, Writer out) throws IOException {

		String lsDirName = "./";
		String options = "";
		String pattern = "*";

		// get options, directory name and pattern
		if (argument != null) {
			argument = argument.trim();
			StringBuffer optionsSb = new StringBuffer(4);
			StringTokenizer st = new StringTokenizer(argument, " ");
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				if (token.charAt(0) == '-') {
					if (token.length() > 1) {
						optionsSb.append(token.substring(1));
					}
				} else {
					lsDirName = token;
				}
			}
			options = optionsSb.toString();
		}

		// check options
		boolean bAll = options.indexOf('a') != -1;
		boolean bDetail = options.indexOf('l') != -1;

		// check pattern
		lsDirName = getPhysicalName(lsDirName);
		int slashIndex = lsDirName.lastIndexOf('/');
		if ((slashIndex != -1) && (slashIndex != (lsDirName.length() - 1))) {
			pattern = lsDirName.substring(slashIndex + 1);
			lsDirName = lsDirName.substring(0, slashIndex + 1);
		}

		// check directory
		//File lstDirObj = new File(lsDirName);
		LinkedRemoteFile lstDirObj = root.lookupFile(lsDirName);
		if (!lstDirObj.exists()) {
			return false;
		}
		if (!lstDirObj.isDirectory()) {
			return false;
		}

		// get file list
		LinkedRemoteFile flLst[];
		if ((pattern == null) || pattern.equals("*") || pattern.equals("")) {
			flLst = lstDirObj.listFiles();
		} else {
			flLst = lstDirObj.listFiles(); //new FileRegularFilter(pattern));
		}

		// print file list
		if (flLst != null) {
			for (int i = 0; i < flLst.length; i++) {
				if ((!bAll) && flLst[i].isHidden()) {
					continue;
				}
				if (bDetail) {
					//printLine(flLst[i], out);
				} else {
					out.write(getName(flLst[i]) + NEWLINE);
				}
			}
		}
		return true;
	}

	/**
	 * Get file owner.
	 */
	private static String getOwner(LinkedRemoteFile fl) {
		return fl.getUser();
	}

	/**
	 * Get group name
	 */
	private static String getGroup(LinkedRemoteFile fl) {
		return fl.getGroup();
	}

	/**
	 * Get link count
	 */
	private static String getLinkCount(LinkedRemoteFile fl) {
		if (fl.isDirectory()) {
			return String.valueOf(3);
		} else {
			return String.valueOf(1);
		}
	}

	/**
	 * Get size
	 */
	private static String getLength(LinkedRemoteFile fl) {
		String initStr = "            ";
		long sz = 0;
		if (fl.isFile()) {
			sz = fl.length();
		}
		String szStr = String.valueOf(sz);
		if (szStr.length() > initStr.length()) {
			return szStr;
		}
		return initStr.substring(0, initStr.length() - szStr.length()) + szStr;
	}

	/**
	 * Get last modified date string.
	 */
	private static String getLastModified(LinkedRemoteFile fl) {
		long modTime = fl.lastModified();
		Date date = new Date(modTime);
		return DateUtils.getUnixDate(date);
	}

	/**
	 * Get file name.
	 */
	private static String getName(LinkedRemoteFile fl) {
		String flName = fl.getName();
		flName = normalizeSeparateChar(flName);

		int lastIndex = flName.lastIndexOf("/");
		if (lastIndex == -1) {
			return flName;
		} else {
			return flName.substring(lastIndex + 1);
		}
	}
	private String getName(File file) {
		throw new RuntimeException("File is deprecated!");
	}

	/**
	 * Get permission string.
	 */
	private static String getPermission(LinkedRemoteFile fl) {

		StringBuffer sb = new StringBuffer(13);
		if (fl.isDirectory()) {
			sb.append('d');
		} else {
			sb.append('-');
		}

		if (fl.canRead()) {
			sb.append('r');
		} else {
			sb.append('-');
		}

		if (fl.canWrite()) {
			sb.append('w');
		} else {
			sb.append('-');
		}
		if (fl.isDirectory()) {
			sb.append("x");
		} else {
			sb.append("-");
		}
		sb.append("------");
		return sb.toString();
	}

	/**
	 * Normalize separate characher. Separate character should be '/' always.
	 */
	private static String normalizeSeparateChar(String pathName) {
		pathName = pathName.replace(File.separatorChar, '/');
		pathName = pathName.replace('\\', '/');
		return pathName;
	}

	/**
	 * Replace dots. Returns physical name.
	 * @param inArg the virtaul name
	 */
	private String replaceDots(String inArg) {

		// get the starting directory
		String resArg;
		if (inArg.charAt(0) != '/') {
			resArg = mstRoot + mstCurrDir.substring(1);
		} else {
			resArg = mstRoot;
		}

		// strip last '/'
		if (resArg.charAt(resArg.length() - 1) == '/') {
			resArg = resArg.substring(0, resArg.length() - 1);
		}

		// replace ., ~ and ..        
		StringTokenizer st = new StringTokenizer(inArg, "/");
		while (st.hasMoreTokens()) {

			String tok = st.nextToken().trim();

			// . => current directory
			if (tok.equals(".")) {
				continue;
			}

			// .. => parent directory (if not root)
			if (tok.equals("..")) {
				if (resArg.startsWith(mstRoot)) {
					int slashIndex = resArg.lastIndexOf('/');
					if (slashIndex != -1) {
						resArg = resArg.substring(0, slashIndex);
					}
				}
				continue;
			}

			// ~ => home directory (in this case /)
			if (tok.equals("~")) {
				resArg = mstRoot.substring(0, mstRoot.length() - 1).trim();
				continue;
			}

			resArg = resArg + '/' + tok;
		}

		// add last slash if necessary
		if (!inArg.equals("") && (inArg.charAt(inArg.length() - 1) == '/')) {
			resArg = resArg + '/';
		}

		// final check
		if (resArg.length() < mstRoot.length()) {
			resArg = mstRoot;
		}

		return resArg;
	}

	/**
	 * Get each directory line.
	 */
	public void printLine(LinkedRemoteFile fl, Writer out) throws IOException {
		out.write(getPermission(fl));
		out.write(DELIM);
		out.write(DELIM);
		out.write(DELIM);
		out.write(getLinkCount(fl));
		out.write(DELIM);
		out.write(getOwner(fl));
		out.write(DELIM);
		out.write(getGroup(fl));
		out.write(DELIM);
		out.write(getLength(fl));
		out.write(DELIM);
		out.write(getLastModified(fl));
		out.write(DELIM);
		out.write(getName(fl));
		out.write(NEWLINE);
	}

	/**
	 * Get each directory line.
	 */
	/*
	public void printLine(File fl, Writer out) throws IOException {
	    out.write(getPermission(fl));
	    out.write(DELIM);
	    out.write(DELIM);
	    out.write(DELIM);
	    out.write(getLinkCount(fl));
	    out.write(DELIM);
	    out.write(getOwner(fl));
	    out.write(DELIM);
	    out.write(getGroup(fl));
	    out.write(DELIM);
	    out.write(getLength(fl));
	    out.write(DELIM);
	    out.write(getLastModified(fl));
	    out.write(DELIM);
	    out.write(getName(fl));
	out.write(NEWLINE);
	}
	*/

	/**
	 * If the string is not '/', remove last slash.
	 */
	private String removeLastSlash(String str) {
		if ((str.length() > 1) && (str.charAt(str.length() - 1) == '/')) {
			str = str.substring(0, str.length() - 1);
		}
		return str;
	}

}
