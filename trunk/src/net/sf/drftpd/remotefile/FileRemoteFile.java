package net.sf.drftpd.remotefile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import net.sf.drftpd.InvalidDirectoryException;
import net.sf.drftpd.slave.RootBasket;

/**
 * A wrapper for java.io.File to the net.sf.drftpd.RemoteFile structure.
 * 
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class FileRemoteFile extends RemoteFile {
//	private File file;
//	private String root;

	/**
	 * Creates 
	 * @param root
	 * @param file
	 * @throws IOException
	 */

//	public FileRemoteFile(String root, File file) throws IOException {
//		this.root = root;
//		this.file = file;
//
//		if (!file.getCanonicalPath().equals(file.getAbsolutePath())) {
//			System.out.println(
//				"FileRemoteFile: warning: not serializing possible symlink: "
//					+ file.getAbsolutePath());
//			throw new InvalidDirectoryException(
//				"Not following symlink: " + file.getAbsolutePath());
//		}
//	}
	//List slaves;
	RootBasket rootBasket;
	String path;

	public FileRemoteFile(RootBasket rootBasket) throws IOException {
		this(rootBasket, "");
	}
	
	public FileRemoteFile(RootBasket rootBasket, String path) throws IOException {
		//if(path.length() != 0) {
		//	if(path.charAt(path.length()-1) == File.separatorChar) path = path.substring(0, path.length()-1);
		//}
		this.path = path;
		this.rootBasket = rootBasket;
		//this.slaves = slaves;

		//check that the roots in the rootBasket are in sync
		boolean first = true;
		for (Iterator iter = rootBasket.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			File file = new File(root.getPath()+"/"+path);
			//System.out.println("File: "+file);
			
			if (!file.exists()) continue;

			if(first) {
				isDirectory = file.isDirectory();
				isFile = file.isFile();
			} else {
				if(file.isDirectory() != isDirectory) throw new IOException("rootBasket out of sync");
				if(file.isFile() != isFile) throw new IOException("rootBasket out of sync");
			}
					
			if (!file.getCanonicalPath().equals(file.getAbsolutePath())) {
				System.out.println(
					"FileRemoteFile: warning: not serializing possible symlink: "
						+ file.getAbsolutePath());
				throw new InvalidDirectoryException(
					"Not following symlink: " + file.getAbsolutePath());
			}
		}
	}
	private File getFile() {
		try {
			return rootBasket.getFile(getPath());
		} catch(FileNotFoundException ex) {
			throw new RuntimeException(ex); 
		}
	}
	/**
	 * @see net.sf.drftpd.RemoteFile#getName()
	 */
	public String getName() {
		return path.substring(path.lastIndexOf(File.separatorChar)+1);
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getParent()
	 */
	public String getParent() {
		throw new NoSuchMethodError();
		//return file.getParent();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getPath()
	 */
	public String getPath() {
		return path;
		//throw new NoSuchMethodError();
		//return file.getPath();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getGroup()
	 */
	public String getGroup() {
		return "drftpd";
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#getUser()
	 */
	public String getOwner() {
		return "drftpd";
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#isDirectory()
	 */
	public boolean isDirectory() {
		return isDirectory;
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#isFile()
	 */
	public boolean isFile() {
		return isFile;
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#lastModified()
	 */
	public long lastModified() {
		return this.getFile().lastModified();
	}

	/**
	 * @see net.sf.drftpd.RemoteFile#length()
	 */
	public long length() {
		return this.getFile().length();
	}

	/**
	 * Returns an array of FileRemoteFile:s representing the contents of the directory this FileRemoteFile represents.
	 * @see net.sf.drftpd.RemoteFileTree#listFiles()
	 */
	public RemoteFile[] listFiles() {
		return (RemoteFile[]) getFiles().toArray(new FileRemoteFile[0]);
	}
	
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getFiles()
	 */
	public Collection getFiles() {
		if (!isDirectory()) {
			throw new IllegalArgumentException("listFiles() called on !isDirectory()");
		}
		Vector filefiles = new Vector();
		for (Iterator iter = rootBasket.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			File file = new File(root+"/"+path);
			if(!file.exists()) continue;
			String tmpFiles[] = file.list(); //returns null if not a dir, blah!

			for (int i = 0; i < tmpFiles.length; i++) {
				try {
					filefiles.add(new FileRemoteFile(rootBasket, path+File.separatorChar+tmpFiles[i]));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return filefiles;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#getSlaves()
	 */
	public Collection getSlaves() {
		return new ArrayList();
	}

}
