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
package org.drftpd.plugins;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.Nukee;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.GroupPosition;
import net.sf.drftpd.master.UploaderPosition;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.SFVFile;
import org.drftpd.SFVFile.SFVStatus;
import org.drftpd.commands.Nuke;
import org.drftpd.commands.UserManagement;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.FormatterException;


/**
 * @author flowman
 * @version $Id$
 */
public class GlftpdLog extends FtpListener {
    private static Logger logger = Logger.getLogger(GlftpdLog.class);

    private PrintWriter _out;
    DateFormat DATE_FMT = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy ",
            Locale.ENGLISH);

    public GlftpdLog() throws UnknownHostException, IOException {
        _out = new PrintWriter(new FileWriter("logs/glftpd.log"));
    }

    public void actionPerformed(Event event) {
        try {
            if (event instanceof DirectoryFtpEvent) {
                actionPerformedDirectory((DirectoryFtpEvent) event);
            } else if (event instanceof NukeEvent) {
                actionPerformedNuke((NukeEvent) event);
            } else if (event instanceof SlaveEvent) {
                actionPerformedSlave((SlaveEvent) event);
            } else if (event instanceof InviteEvent) {
                actionPerformedInvite((InviteEvent) event);
            } else if (event.getCommand().equals("SHUTDOWN")) {
                MessageEvent mevent = (MessageEvent) event;
                print("SHUTDOWN: \"" + mevent.getMessage() + "\"");
            }
        } catch (FormatterException ex) {
        }
    }

    private void actionPerformedDirectory(DirectoryFtpEvent direvent)
        throws FormatterException {
        if ("MKD".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "NEWDIR", direvent.getDirectory());
        } else if ("REQUEST".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "REQUEST", direvent.getDirectory());
        } else if ("REQFILLED".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "REQFILLED", direvent.getDirectory());
        } else if ("RMD".equals(direvent.getCommand())) {
            sayDirectorySection(direvent, "DELDIR", direvent.getDirectory());
        } else if ("WIPE".equals(direvent.getCommand())) {
            if (direvent.getDirectory().isDirectory()) {
                sayDirectorySection(direvent, "WIPE", direvent.getDirectory());
            }
       } else if ("PRE".equals(direvent.getCommand())) {
               sayDirectorySection(direvent, "PRE", direvent.getDirectory());
        } else if (direvent.getCommand().equals("STOR")) {
            actionPerformedDirectorySTOR((TransferEvent) direvent);
        } else {
            // Unhandled DirectoryEvent:
        }
    }

    private void sayDirectorySection(DirectoryFtpEvent direvent, String string,
        LinkedRemoteFileInterface dir) throws FormatterException {
        // TYPE = NEWDIR, DELDIR, WIPE, etc.
        // TYPE: "/path/to/release" "username" "group" "tagline" 
        print("" + string + ": \"" + dir.getPath() + "\" \"" +
            direvent.getUser().getName() + "\" \"" +
            direvent.getUser().getGroup() + "\" \"" +
            direvent.getUser().getKeyedMap().getObjectString(UserManagement.TAGLINE) + "\"");
    }

    private void actionPerformedDirectorySTOR(TransferEvent direvent)
        throws FormatterException {
        if (!direvent.isComplete()) {
            return;
        }

        LinkedRemoteFileInterface dir;

        try {
            dir = direvent.getDirectory().getParentFile();
        } catch (FileNotFoundException e) {
            throw new FatalException(e);
        }

        SFVFile sfvfile;

        try {
            sfvfile = dir.lookupSFVFile();

            // throws IOException, ObjectNotFoundException, NoAvailableSlaveException
        } catch (FileNotFoundException ex) {
            // No sfv file in in dir
            return;
        } catch (NoAvailableSlaveException e) {
            // No available slave with .sfv 
            return;
        } catch (IOException e) {
            // IO error reading .sfv
            return;
        }

        long starttime = Long.MAX_VALUE;

        for (Iterator iter = sfvfile.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.lastModified() < starttime) {
                starttime = file.lastModified();
            }
        }

        if (!sfvfile.hasFile(direvent.getDirectory().getName())) {
            return;
        }

        int halfway = (int) Math.floor((double) sfvfile.size() / 2);

        ///// start ///// start ////
        //check if new racer
        String username = direvent.getUser().getName();
        SFVStatus sfvstatus = sfvfile.getStatus();

        if ((sfvfile.size() - sfvstatus.getMissing()) != 1) {
            for (Iterator iter = sfvfile.getFiles().iterator(); iter.hasNext();) {
                LinkedRemoteFileInterface sfvFileEntry = (LinkedRemoteFileInterface) iter.next();

                if (sfvFileEntry == direvent.getDirectory()) {
                    continue;
                }

                if (sfvFileEntry.getUsername().equals(username)) {
                    break;
                }

                if (!iter.hasNext()) {
                    // RACE: "/path/to/release" "new_racer_username" "new_racer_group"
                    // "whois_he_racing" "new_racers_speed" "files_left" "time_raced"
                    print("RACE: \"" + dir.getPath() + "\" \"" +
                        direvent.getUser().getName() + "\" \"" +
                        direvent.getUser().getGroup() + "\" \"" +
                        sfvfile.getXferspeed() + "\" \"" +
                        Integer.toString(sfvstatus.getMissing()) + "\" \"" +
                        Long.toString((direvent.getTime() - starttime) / 1000) +
                        "\"");
                }
            }
        }

        //COMPLETE
        if (sfvstatus.isFinished()) {
            Collection racers = SiteBot.userSort(sfvfile.getFiles(), "bytes",
                    "high");
            Collection groups = topFileGroup(sfvfile.getFiles());
            Collection fast = SiteBot.userSort(sfvfile.getFiles(), "xferspeed",
                    "high");
            Collection slow = SiteBot.userSort(sfvfile.getFiles(), "xferspeed",
                    "low");

            UploaderPosition fastestuser = (UploaderPosition) fast.iterator()
                                                                  .next();
            UploaderPosition slowestuser = (UploaderPosition) slow.iterator()
                                                                  .next();

            User fastuser;
            User slowuser;

            try {
                fastuser = getGlobalContext().getUserManager()
                              .getUserByName(fastestuser.getUsername());
                slowuser = getGlobalContext().getUserManager()
                              .getUserByName(slowestuser.getUsername());
            } catch (NoSuchUserException e2) {
                return;
            } catch (UserFileException e2) {
                logger.fatal("Error reading userfile", e2);
                return;
            }

            //COMPLETERACE: "/path/to/release" "release_size" "release_files" "release_avrage_speed"
            // "release_total_upload_time" "number_of_racers" "number_of_racing_groups"
            // "fastest_uploader_username" "fastest_uploader_group" "fastest_uploaders_speed"
            // "slowest_uploader_username" "slowest_uploader_group" "slowest_uploaders_speed" 
            print("COMPLETE: \"" + dir.getPath() + "\" \"" +
                sfvfile.getTotalBytes() + "\" \"" +
                Integer.toString(sfvfile.size()) + "\" \"" +
                sfvfile.getXferspeed() + "\" \"" +
                Long.toString((direvent.getTime() - starttime) / 1000) +
                "\" \"" + Integer.toString(racers.size()) + "\" \"" +
                Integer.toString(groups.size()) + "\" \"" +
                fastuser.getName() + "\" \"" + fastuser.getGroup() +
                "\" \"" + fastestuser.getXferspeed() + "\" \"" +
                slowuser.getName() + "\" \"" + slowuser.getGroup() +
                "\" \"" + slowestuser.getXferspeed() + "\"");

            print("STATS: \"" + dir.getPath() + "\" \"UserTop:\"");

            int position = 1;

            for (Iterator iter = racers.iterator(); iter.hasNext();) {
                UploaderPosition stat = (UploaderPosition) iter.next();

                User raceuser;

                try {
                    raceuser = getGlobalContext().getUserManager()
                                  .getUserByName(stat.getUsername());
                } catch (NoSuchUserException e2) {
                    continue;
                } catch (UserFileException e2) {
                    logger.log(Level.FATAL, "Error reading userfile", e2);

                    continue;
                }

                // STATSUSER: "/path/to/release" "race_place" "username" "group" "mb_uploaded"
                // "files_uploaded" "percent_uploaded" "avrage_speed" 
                print("STATSUSER: \"" + dir.getPath() + "\" \"" +
                    new Integer(position++) + "\" \"" + raceuser.getName() +
                    "\" \"" + raceuser.getGroup() + "\" \"" +
                    stat.getBytes() + "\" \"" +
                    Integer.toString(stat.getFiles()) + "\" \"" +
                    Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
                    "\" \"" + stat.getXferspeed() + "\"");
            }

            print("STATS: \"" + dir.getPath() + "\" \"GroupTop:\"");

            position = 1;

            for (Iterator iter = groups.iterator(); iter.hasNext();) {
                GroupPosition stat = (GroupPosition) iter.next();

                // STATSGROUP: "/path/to/release" "race_place" "group" "mb_uploaded"
                // "files_uploaded" "percent_uploaded" "avrage_speed"
                print("STATSGROUP: \"" + dir.getPath() + "\" \"" +
                    new Integer(position++) + "\" \"" + stat.getGroupname() +
                    "\" \"" + stat.getBytes() + "\" \"" +
                    Integer.toString(stat.getFiles()) + "\" \"" +
                    Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
                    "\" \"" + stat.getXferspeed() + "\"");
            }

            //HALFWAY
        } else if ((sfvfile.size() >= 4) &&
                (sfvstatus.getMissing() == halfway)) {
            Collection uploaders = SiteBot.userSort(sfvfile.getFiles(),
                    "bytes", "high");
            UploaderPosition stat = (UploaderPosition) uploaders.iterator()
                                                                .next();

            User leaduser;

            try {
                leaduser = getGlobalContext().getUserManager()
                              .getUserByName(stat.getUsername());
            } catch (NoSuchUserException e3) {
                return;
            } catch (UserFileException e3) {
                logger.log(Level.FATAL, "Error reading userfile", e3);

                return;
            }

            // HALFWAY: "/path/to/release" "leading_username" "group" "mb_uploaded" 
            // "files_uploaded" "percent_uploaded" "avrage_speed" "files_left"
            print("HALFWAY: \"" + dir.getPath() + "\" \"" +
                leaduser.getName() + "\" \"" + leaduser.getGroup() +
                "\" \"" + stat.getBytes() + "\" \"" +
                Integer.toString(stat.getFiles()) + "\" \"" +
                Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
                "\" \"" + stat.getXferspeed() + "\" \"" +
                Integer.toString(sfvstatus.getMissing()) + "\"");
        }
    }

    private void actionPerformedSlave(SlaveEvent sevent)
        throws FormatterException {
        if (sevent.getCommand().equals("ADDSLAVE")) {
            SlaveStatus status;

            try {
                status = sevent.getRSlave().getSlaveStatusAvailable();
            } catch (SlaveUnavailableException e) {
                return;
            }

            print("SLAVEONLINE: \"" + sevent.getRSlave().getName() + "\" \"" +
                sevent.getMessage() + "\" \"" + status.getDiskSpaceCapacity() +
                "\" \"" + status.getDiskSpaceAvailable() + "\"");
        } else if (sevent.getCommand().equals("DELSLAVE")) {
            print("SLAVEOFFLINE: \"" + sevent.getRSlave().getName() + "\"");
        }
    }

    private void actionPerformedInvite(InviteEvent event) {
        String user = event.getIrcNick();
        print("INVITE: \"" + user + "\" \"" + event.getUser().getName() +"\" \"" + event.getUser().getGroup() +"\"");
    }

    private void actionPerformedNuke(NukeEvent event) throws FormatterException {
        String cmd = event.getCommand();

        if (cmd.equals("NUKE")) {
            print("NUKE: \"" + event.getPath() + "\" \"" +
                event.getUser().getName() + "\" \"" +
                event.getUser().getGroup() + "\" \"" +
                String.valueOf(event.getMultiplier()) + " " + event.getSize() +
                "\" \"" + event.getReason() + "\"");

            int position = 1;
            long nobodyAmount = 0;

            for (Iterator iter = event.getNukees2().iterator(); iter.hasNext();) {
                Nukee stat = (Nukee) iter.next();

                User raceuser;

                try {
                    raceuser = getGlobalContext().getUserManager()
                                  .getUserByName(stat.getUsername());
                } catch (NoSuchUserException e2) {
                    nobodyAmount += stat.getAmount();

                    continue;
                } catch (UserFileException e2) {
                    logger.log(Level.FATAL, "Error reading userfile", e2);

                    continue;
                }

                long nukedamount = Nuke.calculateNukedAmount(stat.getAmount(),
                        raceuser.getKeyedMap().getObjectFloat(UserManagement.RATIO),
                        event.getMultiplier());

                print("NUKEE: \"" + raceuser.getName() + "\" \"" +
                    raceuser.getGroup() + "\" \"" + position++ + "\" \"" +
                    stat.getAmount() + " " + nukedamount + "\"");
            }

            if (nobodyAmount != 0) {
                print("NUKEE: \"" + "nobody" + "\" \"" + "nogroup" + "\" \"" +
                    "?" + "\" \"" + nobodyAmount + " " + nobodyAmount + "\"");
            }
        } else if (cmd.equals("UNNUKE")) {
            print("UNNUKE: \"" + event.getPath() + "\" \"" +
                event.getUser().getName() + "\" \"" +
                event.getUser().getGroup() + "\" \"" +
                String.valueOf(event.getMultiplier()) + " " + event.getSize() +
                "\" \"" + event.getReason() + "\"");
        }
    }

    public static Collection topFileGroup(Collection files) {
        ArrayList<GroupPosition> ret = new ArrayList<GroupPosition>();

        for (Iterator iter = files.iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
            String groupname = file.getGroupname();

            GroupPosition stat = null;

            for (Iterator iter2 = ret.iterator(); iter2.hasNext();) {
                GroupPosition stat2 = (GroupPosition) iter2.next();

                if (stat2.getGroupname().equals(groupname)) {
                    stat = stat2;

                    break;
                }
            }

            if (stat == null) {
                stat = new GroupPosition(groupname, file.length(), 1,
                        file.getXfertime());
                ret.add(stat);
            } else {
                stat.updateBytes(file.length());
                stat.updateFiles(1);
                stat.updateXfertime(file.getXfertime());
            }
        }

        Collections.sort(ret);

        return ret;
    }

    public void print(String line) {
        print(new Date(), line);
    }

    public void print(Date date, String line) {
        _out.println(DATE_FMT.format(date) + line);
        _out.flush();
    }

    public void unload() {
    }
}