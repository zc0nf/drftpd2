package net.sf.drftpd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
/**
 * Represents the file attributes of a remote file.
 * 
 * @author Morgan Christiansson <mog@linux.nu>
 */
public class RemoteFile implements Serializable {

	/**
	 * Creates an empty RemoteFile directory, usually used as an empty root directory that
	 * <link>{merge()}</link> can be called on.
	 */
	public RemoteFile() {
		canRead = true;
		canWrite = false;
		lastModified = System.currentTimeMillis();
		length = 0;
		isDirectory = true;
		isFile = false;
		//path = "/";
		parent = null;
		/*
		 * is this the right name to set? maybe null or "/" is more approperiate?
		 * if name is / the filename splitting methods will get wierd results.
		 * if name is null NullPointerException might occur
		 */
		name = "";
		files = new Hashtable();
		slaves = new Vector(1); // there will always be at least 1 RemoteSlave.
	}

	/**
	 * The slave argument may be null, if it is null, no slaves will be added.
	 */
	public RemoteFile(RemoteSlave slave, File file) {
		this(slave, (RemoteFile) null, file);
	}

	/**
	 * Creates a RemoteFile from file or creates a directory tree representation.
	 * @param file file that this RemoteFile object should represent.
	 */
	public RemoteFile(RemoteSlave slave, RemoteFile parent, File file) {
		canRead = file.canRead();
		canWrite = file.canWrite();
		lastModified = file.lastModified();
		length = file.length();
		//isHidden = file.isHidden();
		isDirectory = file.isDirectory();
		isFile = file.isFile();
		if(parent == null) {
			name = "";
		} else {	
			name = file.getName();
		}
		//path = file.getPath();
		/* serialize directory*/
		this.parent = parent;

		slaves = new Vector(1);
		if (slave != null) {
			slaves.add(slave);
		}
		if (isDirectory()) {
			try {
				if (!file.getCanonicalPath().equals(file.getAbsolutePath())) {
					System.out.println(
						"NOT following possible symlink: " + file.getAbsolutePath());
					return;
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			/* get existing file entries */
			File cache = new File(file.getPath() + "/.drftpd");
			Hashtable oldtable = null;
			try {
				ObjectInputStream is =
					new ObjectInputStream(new FileInputStream(cache));
				oldtable = (Hashtable) is.readObject();
			} catch (FileNotFoundException ex) {
				//it's ok if it doesn't exist
			} catch (IOException ex) {
				ex.printStackTrace();
			} catch (ClassNotFoundException ex) {
				// this class must exist
				ex.printStackTrace();
				System.exit(-1);
			}
			/* END get existing file entries*/

			File dir[] = file.listFiles(new DrftpdFileFilter());
			files = new Hashtable(dir.length);
			Stack dirstack = new Stack();
			for (int i = 0; i < dir.length; i++) {
				File file2 = dir[i];
//				System.out.println("III " + file2);
				if (file2.isDirectory()) {
					dirstack.push(file2);
					continue;
				}
				RemoteFile oldfile = null;
				if (oldtable != null)
					oldfile = (RemoteFile) oldtable.get(file.getName());
				if (oldfile != null) {
					files.put(file2.getName(), oldfile);
				} else {
					files.put(
						file2.getName(),
						new RemoteFile(slave, this, file2));
				}
			}

			/*
					//don't need to serialize/cache old files... we won't save any additional data about them anyway..
						try {
							new ObjectOutputStream(
								new FileOutputStream(cache)).writeObject(
								files);
						} catch (FileNotFoundException ex) {
							System.out.println("Could not open file: " + ex.getMessage());
						} catch (Exception ex) {
							ex.printStackTrace();
						}
			*/
			// OK, now the Object is saved, continue with serializing the dir's
			Enumeration e = dirstack.elements();
			while (e.hasMoreElements()) {
				File file2 = (File) e.nextElement();
				String filename = file2.getName();
//				System.out.println(">>> " + file2.getName());
				if (oldtable != null) {
					RemoteFile oldfile = (RemoteFile) oldtable.get(filename);
					if (oldfile != null) {
						files.put(filename, oldfile);
					} else {
						files.put(filename, new RemoteFile(slave, this, file2));
					}
				} else {
					files.put(filename, new RemoteFile(slave, this, file2));
				}
			}
			System.out.println(
				"<< finished adding " + getPath() + " [" + files.size() + " entries]");
		} /* serialize directory */
	}

	public RemoteFile[] listFiles() {
		return (RemoteFile[]) files.values().toArray(new RemoteFile[0]);
	}

	public RemoteFile lookupFile(String path) throws FileNotFoundException {
		StringTokenizer st = new StringTokenizer(path, "/");
		RemoteFile currfile = this;
		while (st.hasMoreTokens()) {
			String nextToken = st.nextToken();
			currfile = (RemoteFile) currfile.getHashtable().get(nextToken);
			if (currfile == null)
				throw new FileNotFoundException();
		}
		return currfile;
	}

	/**
	 * For compatibility with java.io.File.
	 */
	public boolean exists() {
		return true;
	}

	private Vector slaves;
	public void addSlave(RemoteSlave slave) {
		slaves.add(slave);
	}
	public void addSlaves(Collection addslaves) {
		if (addslaves == null)
			throw new IllegalArgumentException("addslaves cannot be null");
		System.out.println("Adding " + addslaves + " to " + slaves);
		slaves.addAll(addslaves);
		System.out.println("slaves.size() is now " + slaves.size());
	}
	public Collection getSlaves() {
		return slaves;
	}
	private Random rand = new Random();
	public RemoteSlave getAnySlave() {
		int num = rand.nextInt(slaves.size());
		System.out.println(
			"Returning slave "
				+ num
				+ " out of "
				+ slaves.size()
				+ " possible slaves");
		return (RemoteSlave) slaves.get(num);
	}
	/*
		public void removeSlave(RemoteSlave slave) {
			slaves.remove(slave);
		}
	*/

	private String user;
	public String getUser() {
		if (user == null)
			return "drftpd";
		return user;
	}

	private String group;
	public String getGroup() {
		if (group == null)
			return "drftpd";
		return group;
	}

	/**
	 * separatorChar is always "/" as "/" is always used in FTP.
	 */
	private static final char separatorChar = '/';

	private Hashtable files;
	public Hashtable getHashtable() {
		return files;
	}

	private RemoteFile parent;
	public RemoteFile getParentFile() {
		return parent;
	}
	public String getParent() {
		if (getParentFile() == null)
			return null;
		return getParentFile().getPath();
	}

	protected boolean isDirectory;
	public boolean isDirectory() {
		return isDirectory;
	}

	protected boolean isFile;
	public boolean isFile() {
		return isFile;
	}

	//boolean isHidden;
	public boolean isHidden() {
		if (getPath().startsWith("."))
			return true;
		return false;
	}

	boolean canRead;
	public boolean canRead() {
		return canRead;
	}

	boolean canWrite;
	public boolean canWrite() {
		return canWrite;
	}

	long lastModified;
	public long lastModified() {
		return lastModified;
	}

	private long length;
	public long length() {
		return length;
	}

	//private String path;
	private String name;
	public String getName() {
		//return path.substring(path.lastIndexOf(separatorChar) + 1);
		return name;
	}

	public String getPath() {
		//return path;
		StringBuffer path = new StringBuffer("/"+getName());
		RemoteFile parent = getParentFile();
		while (parent != null && parent.getParentFile() != null) {
			path.insert(0, "/"+parent.getName());
			parent = parent.getParentFile();
		}
		return path.toString();
	}

	public boolean equals(Object obj) {
		if (obj instanceof RemoteFile
			&& ((RemoteFile) obj).getPath().equals(getPath())) {
			return true;
		}
		return false;
	}

	/**
	 * Merges two RemoteFile directories.
	 * If duplicates exist, the slaves are added to this object and the file-attributes of the oldest file (lastModified) are kept.
	 */
	public synchronized void merge(RemoteFile dir) {
		if (!isDirectory())
			throw new IllegalArgumentException("merge() called on a non-directory");
		if (!dir.isDirectory())
			throw new IllegalArgumentException("argument is not a directory");

		Hashtable map = getHashtable();
		Hashtable mergemap = dir.getHashtable();

		System.out.println(
			"Adding " + dir.getPath() + " with " + mergemap.size() + " files");

		Iterator i = mergemap.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry) i.next();
			String filename = (String) entry.getKey();
			//RemoteFile file = (RemoteFile) entry.getValue();
			RemoteFile file = (RemoteFile) files.get(filename);
			RemoteFile mergefile = (RemoteFile) entry.getValue();
			//RemoteFile mergefile = (RemoteFile) mergemap.get(getName());

			// two scenarios:, local file [does not] exists
			if (file == null) {
				// local file does not exist, just put it in the hashtable
				map.put(mergefile.getName(), mergefile);
			} else {

				if (lastModified() > mergefile.lastModified()) {
					System.out.println(
						"Last modified changed from "
							+ lastModified()
							+ " to "
							+ mergefile.lastModified());
					lastModified = mergefile.lastModified();
				} else {
					System.out.println(
						"Last modified NOT changed from "
							+ lastModified()
							+ " to "
							+ mergefile.lastModified());
				}

				// 4 scenarios: new/existing file/directory
				if (mergefile.isDirectory()) {
					if (!file.isDirectory())
						System.out.println(
							"!!! WARNING: File/Directory conflict!!");
					file.merge(mergefile);
				} else {
					if (file.isDirectory())
						System.out.println(
							"!!! WARNING: File/Directory conflict!!");
					System.out.println("merge " + mergefile + " to " + file);
					Collection slaves2 = mergefile.getSlaves();
					//System.out.println("adding slaves: "+slaves2);
					System.out.println(slaves2.size() + " slaves to add");
					file.addSlaves(slaves2);
				}
				System.out.println("Result file: " + file);
				/*
				System.out.println("Old file: "+map.get(filename));
				map.put(filename, file);
				*/
			}
		}

		// directory backdating, do other attrbiutes need "backdating"? if so fix it! :)
		if (lastModified() > dir.lastModified()) {
			lastModified = dir.lastModified();
		}
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("[net.sf.drftpd.RemoteFile[");
		//ret.append(slaves);
		Enumeration e = slaves.elements();
		ret.append("slaves:[");
		while (e.hasMoreElements()) {
			//[endpoint:[213.114.146.44:2012](remote),objID:[2b6651:ef0b3c7162:-8000, 0]]]]]
			Pattern p = Pattern.compile("endpoint:\\[(.*?):.*?\\]");
			Matcher m = p.matcher(e.nextElement().toString());
			m.find();
			ret.append(m.group(1));
			//ret.append(e.nextElement());
			if (e.hasMoreElements())
				ret.append(",");
		}
		ret.append("]");
		//ret.append("isDirectory(): " + isDirectory() + " ");
		if (isDirectory())
			ret.append("[directory: true]");
		//ret.append("isFile(): " + isFile() + " ");
		ret.append(getPath());
		ret.append("]]");
		return ret.toString();
	}
}
