/*
 * Created on 2003-dec-01
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd;

import java.net.InetAddress;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * @author mog
 * @version $Id: HostMask.java,v 1.1 2003/12/02 20:40:50 mog Exp $
 */
public class HostMask {
	private static final Logger logger = Logger.getLogger(HostMask.class);
	private String _identMask;
	private String _hostMask;

	public HostMask(String string) {
		int pos = string.indexOf('@');
		_identMask = string.substring(0, pos);
		_hostMask = string.substring(pos + 1);
	}

	public String getHostMask() {
		return _hostMask;
	}

	public String getIdentMask() {
		return _identMask;
	}

	/**
	 * Is ident used?
	 * @return false is ident mask equals "*"
	 */
	public boolean isIdentMaskSignificant() {
		return !_identMask.equals("*");
	}

	public boolean matches(String ident, InetAddress address) {
		Perl5Matcher m = new Perl5Matcher();

		GlobCompiler c = new GlobCompiler();
		try {
			if (!m.matches(ident, c.compile(getIdentMask()))) {
				return false;
			}
			Pattern p = c.compile(getHostMask());
			if (!m.matches(address.getHostAddress(), p)) {
				return false;
			}
			if (!m.matches(address.getHostName(), p)) {
				return false;
			}
			return true;
		} catch (MalformedPatternException ex) {
			logger.warn("", ex);
			return false;
		}
	}
}
