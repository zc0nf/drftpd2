/*
 * Created on 2003-aug-08
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class Bytes {
	//1,000,000,000,000,000,000 = exa
	static long EXA = 1000000000000000000L;
	//1,000,000,000,000,000 = peta
	static long PETA = 1000000000000000L;
	//1,000,000,000,000 = terra
	static long TERRA = 1000000000000L;
	//1,000,000,000 GiB
	static long GIGA = 1000000000L;
	//1,000,000 MiB
	static long MEGA = 1000000L;
	//1,000 KiB
	static long KILO = 1000L;

	public static String formatBytes(long bytes) {
		DecimalFormatSymbols formatsymbols = new DecimalFormatSymbols();
		formatsymbols.setDecimalSeparator('.');
		DecimalFormat format = new DecimalFormat("#.#", formatsymbols);
		
		if (bytes > TERRA * 1000) {
			return (float) bytes / TERRA + "TiB";
		} else if (bytes > TERRA) {
			return format.format((float) bytes / TERRA) + "TiB";

		} else if (bytes > GIGA * 1000) {
			return (float) bytes / GIGA + "GiB";
		} else if (bytes > GIGA) {
			return format.format((float) bytes / GIGA) + "GiB";

		} else if (bytes > MEGA * 1000) {
			return (float) bytes / MEGA + "MiB";
		} else if (bytes > MEGA) {
			return format.format((float) bytes / MEGA) + "MiB";

		} else if (bytes > KILO * 1000) {
			return (float) bytes / KILO + "KiB";
		} else if (bytes > KILO) {
			return format.format((float) bytes / KILO) + "KiB";
		}
		return Long.toString(bytes);
	}

	//	public static void main(String args[]) {
	//		System.out.println(formatBytes(1543543));
	//	}
}
