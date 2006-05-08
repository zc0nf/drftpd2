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
package org.drftpd.commands;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.dynamicdata.Key;
import org.drftpd.nuke.NukeBeans;
import org.drftpd.nuke.NukeData;
import org.drftpd.nuke.NukeUtils;
import org.drftpd.nuke.Nukee;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * nukedamount -> amount after multiplier
 * amount -> amount before multiplier
 *
 * @author mog
 * @version $Id$
 */
public class Nuke implements CommandHandler, CommandHandlerFactory {
    public static final Key NUKED = new Key(Nuke.class, "nuked", Integer.class);
    public static final Key NUKEDBYTES = new Key(Nuke.class, "nukedBytes",
            Long.class);
    private static final Logger logger = Logger.getLogger(Nuke.class);
    public static final Key LASTNUKED = new Key(Nuke.class, "lastNuked",
            Long.class);

    public Nuke() {
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
     * @throws ImproperUsageException
     */
    private Reply doSITE_NUKE(BaseFtpConnection conn) throws ImproperUsageException {
        if (!conn.getRequest().hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument(),
                " ");

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        int multiplier;
        LinkedRemoteFileInterface nukeDir;
        String nukeDirName;

        try {
            nukeDirName = st.nextToken();
            nukeDir = conn.getCurrentDirectory().getFile(nukeDirName);
        } catch (FileNotFoundException e) {
            Reply response = new Reply(550, e.getMessage());

            return response;
        }

        if (!nukeDir.isDirectory()) {
            Reply response = new Reply(550,
                    nukeDirName + ": not a directory");

            return response;
        }

        String nukeDirPath = nukeDir.getPath();

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        try {
            multiplier = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException ex) {
            logger.warn("", ex);

            return new Reply(501, "Invalid multiplier: " + ex.getMessage());
        }
        conn.getGlobalContext().getSlaveManager().cancelTransfersInDirectory(nukeDir);

        String reason;

        if (st.hasMoreTokens()) {
            reason = st.nextToken("").trim();
        } else {
            reason = "";
        }

        //get nukees with string as key
        Hashtable<String,Long> nukees = new Hashtable<String,Long>();
        NukeUtils.nukeRemoveCredits(nukeDir, nukees);

        Reply response = new Reply(200, "NUKE suceeded");

        //// convert key from String to User ////
        HashMap<User,Long> nukees2 = new HashMap<User,Long>(nukees.size());

        for (Iterator iter = nukees.keySet().iterator(); iter.hasNext();) {
            String username = (String) iter.next();
            User user;

            try {
                user = conn.getGlobalContext().getUserManager().getUserByName(username);
            } catch (NoSuchUserException e1) {
                response.addComment("Cannot remove credits from " + username +
                    ": " + e1.getMessage());
                logger.warn("", e1);
                user = null;
            } catch (UserFileException e1) {
                response.addComment("Cannot read user data for " + username +
                    ": " + e1.getMessage());
                logger.warn("", e1);
                response.setMessage("NUKE failed");

                return response;
            }

            // nukees contains credits as value
            if (user == null) {
                Long add = (Long) nukees2.get(null);

                if (add == null) {
                    add = new Long(0);
                }

                nukees2.put(user,
                    new Long(add.longValue() +
                        ((Long) nukees.get(username)).longValue()));
            } else {
                nukees2.put(user, nukees.get(username));
            }
        }

        long nukeDirSize = 0;
        long nukedAmount = 0;

        //update credits, nukedbytes, timesNuked, lastNuked
        for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
            AbstractUser nukee = (AbstractUser) iter.next();

            if (nukee == null) {
                continue;
            }

            long size = ((Long) nukees2.get(nukee)).longValue();

            long debt = NukeUtils.calculateNukedAmount(size,
                    nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), multiplier);

            nukedAmount += debt;
            nukeDirSize += size;
            nukee.updateCredits(-debt);
            nukee.updateUploadedBytes(-size);
            nukee.getKeyedMap().incrementObjectLong(NUKEDBYTES, debt);

            nukee.getKeyedMap().incrementObjectLong(NUKED);
            nukee.getKeyedMap().setObject(Nuke.LASTNUKED, new Long(System.currentTimeMillis()));

            try {
                nukee.commit();
            } catch (UserFileException e1) {
                response.addComment("Error writing userfile: " +
                    e1.getMessage());
                logger.log(Level.WARN, "Error writing userfile", e1);
            }

            response.addComment(nukee.getName() + " " +
                Bytes.formatBytes(debt));
        }
        
        NukeData nd = 
			new NukeData(conn.getUserNull().getName(), nukeDirPath, reason, nukees, multiplier, nukedAmount, nukeDirSize);

        NukeEvent nuke = new NukeEvent(conn.getUserNull(), "NUKE", nd);
        NukeBeans.getNukeBeans().add(nd);
        
        //rename
        String toDirPath;
        String toName = "[NUKED]-" + nukeDir.getName();
        try {
            toDirPath = nukeDir.getParentFile().getPath();
        } catch (FileNotFoundException ex) {
            logger.fatal("", ex);

            return Reply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN;
        }
        try {
            nukeDir.renameTo(toDirPath, toName);
            nukeDir.createDirectory(conn.getUserNull().getName(),
                conn.getUserNull().getGroup(), "REASON-" + reason);
        } catch (IOException ex) {
            logger.warn("", ex);
            response.addComment(" cannot rename to \"" + toDirPath + "/" +
                toName + "\": " + ex.getMessage());
            response.setCode(500);
            response.setMessage("NUKE failed");

            return response;
        }
        
        conn.getGlobalContext().dispatchFtpEvent(nuke);

        return response;
    }

    private Reply doSITE_NUKES(BaseFtpConnection conn) {
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();

        for (NukeData nd : NukeBeans.getNukeBeans().getAll()) {
            response.addComment(nd.toString());
        }

        return response;
    }

    /**
     * USAGE: site unnuke <directory> <message>
     *         Unnuke a directory.
     *
     *         ex. site unnuke shit NOT CRAP
     *
     *         This will unnuke the directory 'shit' with the comment 'NOT CRAP'.
     *
     *         NOTE: You can enclose the directory in braces if you have spaces in the name
     *         ex. site unnuke {My directory name} justcause
     *
     *         You need to configure glftpd to keep nuked files if you want to unnuke.
     *         See the section about glftpd.conf.
     */
    private Reply doSITE_UNNUKE(BaseFtpConnection conn) {
        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument());

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        String toName = st.nextToken();
        String toPath;

        {
            StringBuffer toPath2 = new StringBuffer(conn.getCurrentDirectory()
                                                        .getPath());

            if (toPath2.length() != 1) {
                toPath2.append("/"); // isn't /
            }

            toPath2.append(toName);
            toPath = toPath2.toString();
        }

        String toDir = conn.getCurrentDirectory().getPath();
        String nukeName = "[NUKED]-" + toName;

        String reason;

        if (st.hasMoreTokens()) {
            reason = st.nextToken("");
        } else {
            reason = "";
        }

        LinkedRemoteFileInterface nukeDir;

        try {
            nukeDir = conn.getCurrentDirectory().getFile(nukeName);
        } catch (FileNotFoundException e2) {
            return new Reply(200,
                nukeName + " doesn't exist: " + e2.getMessage());
        }
        
        conn.getGlobalContext().getSlaveManager().cancelTransfersInDirectory(nukeDir);

        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        NukeData nukeData;

        try {
            nukeData = NukeBeans.getNukeBeans().get(toPath);
        } catch (ObjectNotFoundException ex) {
            response.addComment(ex.getMessage());

            return response;
        }

        //Map nukees2 = new Hashtable();
        //		for (Iterator iter = nuke.getNukees().entrySet().iterator();
        //			iter.hasNext();
        //			) {
        for (Iterator iter = NukeBeans.getNukeeList(nukeData).iterator(); iter.hasNext();) {
            //Map.Entry entry = (Map.Entry) iter.next();
            Nukee nukeeObj = (Nukee) iter.next();

            //String nukeeName = (String) entry.getKey();
            String nukeeName = nukeeObj.getUsername();
            User nukee;

            try {
                nukee = conn.getGlobalContext().getUserManager().getUserByName(nukeeName);
            } catch (NoSuchUserException e) {
                response.addComment(nukeeName + ": no such user");

                continue;
            } catch (UserFileException e) {
                response.addComment(nukeeName + ": error reading userfile");
                logger.fatal("error reading userfile", e);

                continue;
            }

            long nukedAmount = NukeUtils.calculateNukedAmount(nukeeObj.getAmount(),
                    nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO),
                    nukeData.getMultiplier());

            nukee.updateCredits(nukedAmount);
            nukee.updateUploadedBytes(nukeeObj.getAmount());

            nukee.getKeyedMap().incrementObjectInt(NUKED, -1);

            try {
                nukee.commit();
            } catch (UserFileException e3) {
                logger.log(Level.FATAL,
                    "Eroror saveing userfile for " + nukee.getName(), e3);
                response.addComment("Error saving userfile for " +
                    nukee.getName());
            }

            response.addComment(nukeeName + ": restored " +
                Bytes.formatBytes(nukedAmount));
        }

        try {
            NukeBeans.getNukeBeans().remove(toPath);
        } catch (ObjectNotFoundException e) {
            response.addComment("Error removing nukelog entry");
        }

        try {
            nukeDir.renameTo(toDir, toName);
        } catch (FileExistsException e1) {
            response.addComment(
                "Error renaming nuke, target dir already exists");
        } catch (IOException e1) {
            response.addComment("Error: " + e1.getMessage());
            logger.log(Level.FATAL,
                "Illegaltargetexception: means parent doesn't exist", e1);
        }

        try {
            LinkedRemoteFileInterface reasonDir = nukeDir.getFile("REASON-" +
                    nukeData.getReason());

            if (reasonDir.isDirectory()) {
                reasonDir.delete();
            }
        } catch (FileNotFoundException e3) {
            logger.debug("Failed to delete 'REASON-" + nukeData.getReason() +
                "' dir in UNNUKE", e3);
        }
        nukeData.setReason(reason);
        NukeEvent nukeEvent = new NukeEvent(conn.getUserNull(), "UNNUKE", nukeData);
        conn.getGlobalContext().dispatchFtpEvent(nukeEvent);

        return response;
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException, ImproperUsageException {

    	String cmd = conn.getRequest().getCommand();

        if ("SITE NUKE".equals(cmd)) {
            return doSITE_NUKE(conn);
        }

        if ("SITE NUKES".equals(cmd)) {
            return doSITE_NUKES(conn);
        }

        if ("SITE UNNUKE".equals(cmd)) {
            return doSITE_UNNUKE(conn);
        }

        throw UnhandledCommandException.create(Nuke.class, conn.getRequest());
    }
    
    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    	NukeBeans.newInstance();
    }

	public void unload() {
	}
}