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
package org.drftpd.plugins.sitebot.plugins.dailystats.announce;

import java.util.Collection;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.sitebot.AnnounceInterface;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.OutputWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.sitebot.plugins.dailystats.UserStats;
import org.drftpd.plugins.sitebot.plugins.dailystats.event.StatsEvent;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class StatsAnnouncer implements AnnounceInterface {

	private AnnounceConfig _config;

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;
		_keyPrefix = this.getClass().getName();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void stop() {
		// The plugin is unloading so stop asking for events
		GlobalContext.getEventService().unsubscribe(StatsEvent.class, this);
	}

	public String[] getEventTypes() {
		String[] types = {"dailystats"};
		return types;
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onStatsEvent(StatsEvent event) {
		AnnounceWriter writer = _config.getSimpleWriter("dailystats");
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			Collection<UserStats> outputStats = event.getOutputStats();
			String statsType = event.getType();
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			sayOutput(ReplacerUtils.jprintf(_keyPrefix+"."+statsType, env, _bundle), writer);
			int count = 1;
			for (UserStats line : outputStats) {
				env.add("num",count);
				env.add("name",line.getName());
				env.add("files",line.getFiles());
				env.add("bytes",line.getBytes());
				sayOutput(ReplacerUtils.jprintf(_keyPrefix+"."+statsType+".item", env, _bundle), writer);
				count++;
			}
			if (count == 1) {
				sayOutput(ReplacerUtils.jprintf(_keyPrefix+"."+statsType+".none", env, _bundle), writer);
			}
		}
	}

	private void sayOutput(String output, AnnounceWriter writer) {
		StringTokenizer st = new StringTokenizer(output,"\n");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			for (OutputWriter oWriter : writer.getOutputWriters()) {
				oWriter.sendMessage(token);
			}
		}
	}
}
