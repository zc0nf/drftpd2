/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package net.sf.drftpd.mirroring;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.Bytes;
import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.commands.UnhandledCommandException;
import org.drftpd.master.RemoteSlave;

import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;


/**
 * CommandHandler plugin for viewing and manipulating the JobManager queue.
 *
 * @author mog
 * @version $Id: JobManagerCommandHandler.java,v 1.19 2004/07/09 17:08:38 zubov
 *          Exp $
 */
public class JobManagerCommandHandler implements CommandHandlerFactory,
    CommandHandler {
    public JobManagerCommandHandler() {
        super();
    }

    /**
     * USAGE: <file><priority>[destslave ...]
     *
     * @param conn
     * @return
     */
    private Reply doADDJOB(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!conn.getRequest().hasArgument()) {
            return new Reply(501,
                conn.jprintf(JobManagerCommandHandler.class, "addjob.usage"));
        }

        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument());
        LinkedRemoteFileInterface lrf;

        try {
            lrf = conn.getCurrentDirectory().lookupFile(st.nextToken());
        } catch (FileNotFoundException e) {
            return new Reply(500, "File does not exist");
        }

        int priority;

        try {
            priority = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException e) {
            return new Reply(501,
                conn.jprintf(JobManagerCommandHandler.class, "addjob.usage"));
        }

        int timesToMirror;

        try {
            timesToMirror = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException e) {
            return new Reply(501,
                conn.jprintf(JobManagerCommandHandler.class, "addjob.usage"));
        }

        HashSet<RemoteSlave> destSlaves = new HashSet<RemoteSlave>();
        Reply reply = new Reply(200);

        while (st.hasMoreTokens()) {
            String slaveName = st.nextToken();
            RemoteSlave rslave;

            try {
                rslave = conn.getGlobalContext().getSlaveManager()
                             .getRemoteSlave(slaveName);
            } catch (ObjectNotFoundException e1) {
                reply.addComment(slaveName +
                    "was not found, cannot add to destination slave list");

                continue;
            }

            destSlaves.add(rslave);
        }

        if (destSlaves.size() == 0) {
            return new Reply(501,
                conn.jprintf(JobManagerCommandHandler.class, "addjob.usage"));
        }

        Job job = new Job(lrf, destSlaves, priority, timesToMirror);
        conn.getGlobalContext().getConnectionManager().getJobManager()
            .addJobToQueue(job);

        ReplacerEnvironment env = new ReplacerEnvironment();
        env.add("job", job);
        reply.addComment(conn.jprintf(JobManagerCommandHandler.class,
                "addjob.success", env));

        return reply;
    }

    private Reply doLISTJOBS(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        Reply reply = new Reply(200);
        int count = 0;
        ReplacerEnvironment env = new ReplacerEnvironment();

        for (Iterator<Job> iter = new ArrayList<Job>(conn.getGlobalContext()
                                               .getConnectionManager()
                                               .getJobManager()
                                               .getAllJobsFromQueue()).iterator();
                iter.hasNext();) {
            count++;

            Job job = iter.next();
            env.add("job", job);
            env.add("count", new Integer(count));

            if (job.isTransferring()) {
                env.add("speed", Bytes.formatBytes(job.getSpeed()));
                env.add("progress", Bytes.formatBytes(job.getProgress()));
                env.add("total", Bytes.formatBytes(job.getFile().length()));
                env.add("srcslave", job.getSrcSlaveName());
                env.add("destslave", job.getDestSlaveName());
                reply.addComment(conn.jprintf(JobManagerCommandHandler.class,
                        "listjobrunning", env));
            } else {
                reply.addComment(conn.jprintf(JobManagerCommandHandler.class,
                        "listjobwaiting", env));
            }
        }

        return reply;
    }

    private Reply doREMOVEJOB(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!conn.getRequest().hasArgument()) {
            return new Reply(501,
                conn.jprintf(JobManagerCommandHandler.class, "removejob.usage"));
        }

        String filename = conn.getRequest().getArgument();
        Job job = null;
        List jobs = new ArrayList<Job>(conn.getGlobalContext().getConnectionManager()
                                      .getJobManager().getAllJobsFromQueue());
        ReplacerEnvironment env = new ReplacerEnvironment();
        env.add("filename", filename);

        for (Iterator iter = jobs.iterator(); iter.hasNext();) {
            job = (Job) iter.next();

            if (job.getFile().getName().equals(filename)) {
                env.add("job", job);
                conn.getGlobalContext().getConnectionManager().getJobManager()
                    .stopJob(job);

                return new Reply(200,
                    conn.jprintf(JobManagerCommandHandler.class,
                        "removejob.success", env));
            }
        }

        return new Reply(200,
            conn.jprintf(JobManagerCommandHandler.class, "removejob.fail", env));
    }

    private Reply doSTARTJOBS(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        conn.getGlobalContext().getConnectionManager().getJobManager()
            .startJobs();

        return new Reply(200, "JobTransfers will now start");
    }

    private Reply doSTOPJOBS(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        conn.getGlobalContext().getConnectionManager().getJobManager().stopJobs();

        return new Reply(200,
            "All JobTransfers will stop after their current transfer");
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        String cmd = conn.getRequest().getCommand();

        if ("SITE LISTJOBS".equals(cmd)) {
            return doLISTJOBS(conn);
        }

        if ("SITE REMOVEJOB".equals(cmd)) {
            return doREMOVEJOB(conn);
        }

        if ("SITE ADDJOB".equals(cmd)) {
            return doADDJOB(conn);
        }

        if ("SITE STOPJOBS".equals(cmd)) {
            return doSTOPJOBS(conn);
        }

        if ("SITE STARTJOBS".equals(cmd)) {
            return doSTARTJOBS(conn);
        }

        throw UnhandledCommandException.create(JobManagerCommandHandler.class,
            conn.getRequest());
    }

    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
