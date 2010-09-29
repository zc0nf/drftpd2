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
package org.drftpd.commands.nuke;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.ResourceBundle;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commands.nuke.metadata.NukeData;
import org.drftpd.commands.nuke.metadata.NukeUserData;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.Session;
import org.drftpd.sections.SectionInterface;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.UserManagement;
import org.drftpd.event.NukeEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * nukedamount -> amount after multiplier
 * amount -> amount before multiplier
 *
 * @author mog
 * @version $Id$
 */
public class Nuke extends CommandInterface {

	private ResourceBundle _bundle;
	private String _keyPrefix;
    
    private static final Logger logger = Logger.getLogger(Nuke.class);

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
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
    public CommandResponse doSITE_NUKE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

		Session session = request.getSession();

		int multiplier;
        
        DirectoryHandle currentDir = request.getCurrentDirectory();
		String nukeDirName = st.nextToken();
        User requestUser = session.getUserNull(request.getUser());

		String nukeDirPath = VirtualFileSystem.fixPath(nukeDirName);

		if (!(nukeDirPath.startsWith(VirtualFileSystem.separator))) {
			// Not a full path, let's make it one
			if (request.getCurrentDirectory().isRoot()) {
				boolean searchIndex = request.getProperties().getProperty("search","true").
						equalsIgnoreCase("true");
				if (searchIndex) {
					// Get dirs from index system
					ArrayList<DirectoryHandle> dirsToNuke;
					try {
						dirsToNuke = NukeUtils.findNukeDirs(currentDir, requestUser, nukeDirPath);
					} catch (FileNotFoundException e) {
						logger.warn(e);
						return new CommandResponse(550, e.getMessage());
					}

					ReplacerEnvironment env = new ReplacerEnvironment();

					if (dirsToNuke.isEmpty()) {
						env.add("searchstr", nukeDirPath);
						return new CommandResponse(550, session.jprintf(_bundle,_keyPrefix+"nuke.search.empty",
								env, requestUser));
					} else if (dirsToNuke.size() == 1) {
						nukeDirPath = dirsToNuke.get(0).getPath();
					} else {
						CommandResponse response = new CommandResponse(200);

						for (DirectoryHandle nukeDir : dirsToNuke) {
							try {
								env.add("name", nukeDir.getName());
								env.add("path", nukeDir.getPath());
								env.add("owner", nukeDir.getUsername());
								env.add("group", nukeDir.getGroup());
								env.add("size", Bytes.formatBytes(nukeDir.getSize()));
								response.addComment(session.jprintf(_bundle,_keyPrefix+"nuke.search.item", env, requestUser));
							} catch (FileNotFoundException e) {
								logger.warn("Dir deleted after index search?, skip and continue: " + nukeDir.getPath());
							}
						}

						response.addComment(session.jprintf(_bundle,_keyPrefix+"nuke.search.end", env, requestUser));

						// Return matching dirs and let user decide what to nuke
						return response;
					}
				} else {
					nukeDirPath = VirtualFileSystem.separator + nukeDirPath;
				}
			} else {
				nukeDirPath = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + nukeDirPath;
			}
		}

		DirectoryHandle nukeDir;

		try {
			nukeDir = request.getCurrentDirectory().getDirectory(nukeDirPath, requestUser);
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		} catch (ObjectNotValidException e) {
			return new CommandResponse(550, nukeDirPath + " is not a directory");
		}

		nukeDirName = nukeDir.getName();

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        try {
            multiplier = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException ex) {
            logger.warn(ex, ex);
            return new CommandResponse(501, "Invalid multiplier: " + ex.getMessage());
        }
        
        // aborting transfers on the nuked dir.
        GlobalContext.getGlobalContext().getSlaveManager().cancelTransfersInDirectory(nukeDir);

        String reason = "";

        if (st.hasMoreTokens()) {
            reason = st.nextToken("").trim();
        }

		CommandResponse response = new CommandResponse(200, "Nuke succeeded");

        //get nukees with string as key
        Hashtable<String,Long> nukees = new Hashtable<String,Long>();
        
        try {
			NukeUtils.nukeRemoveCredits(nukeDir, nukees);
		} catch (FileNotFoundException e) {
			// how come this happened? the file was just there!
			logger.error(e,e);
		}
		
        // Converting the String Map to a User Map. 
        HashMap<User,Long> nukees2 = new HashMap<User,Long>(nukees.size());

        for (Entry<String,Long> entry : nukees.entrySet()) {
        	String username = entry.getKey();
            User user;

            try {
                user = GlobalContext.getGlobalContext().getUserManager().getUserByName(username);
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
                Long add = nukees2.get(null);

                if (add == null) {
                    add = 0L;
                }

				nukees2.put(null, add + entry.getValue());
            } else {
                nukees2.put(user, entry.getValue());
            }
        }

        long nukeDirSize = 0;
        long nukedAmount = 0;

		StringBuffer nukeeOutput = new StringBuffer();
        ReplacerEnvironment env = new ReplacerEnvironment();

        //update credits, nukedbytes, timesNuked, lastNuked
        for (Entry<User, Long> entry : nukees2.entrySet()) {
        	User nukee = entry.getKey();

            if (nukee == null) {
                continue;
            }

            long size = entry.getValue();
            
            long debt = NukeUtils.calculateNukedAmount(size,
                    nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), multiplier);

            nukedAmount += debt;
            nukeDirSize += size;
            nukee.updateCredits(-debt);
            nukee.updateUploadedBytes(-size);
            
            nukee.getKeyedMap().incrementLong(NukeUserData.NUKEDBYTES, debt);

            nukee.getKeyedMap().incrementInt(NukeUserData.NUKED);
			nukee.getKeyedMap().setObject(NukeUserData.LASTNUKED, System.currentTimeMillis());

            nukee.commit();

			env.add("nukedamount", Bytes.formatBytes(debt));

			nukeeOutput.append(session.jprintf(_bundle, _keyPrefix+"nuke.nukees", env, nukee));
        }
        
        
        //rename
        String toDirPath = nukeDir.getParent().getPath();
        String toName = "[NUKED]-" + nukeDir.getName();
        String toFullPath = toDirPath+"/"+toName;

        try {
            nukeDir.renameToUnchecked(nukeDir.getNonExistentDirectoryHandle(toFullPath)); // rename.
            nukeDir = currentDir.getDirectory(toFullPath, requestUser);
        } catch (IOException ex) {
            logger.warn(ex, ex);
            CommandResponse r = new CommandResponse(500, "Nuke failed!");
            r.addComment("Could not rename to \"" + toDirPath + "/" + toName + "\": " + ex.getMessage());
            return r;
        } catch (ObjectNotValidException e) {
        	return new CommandResponse(550, toFullPath + " is not a directory");
		}

		NukeData nd = new NukeData();
        nd.setUser(request.getUser());
		nd.setPath(nukeDirPath);
		nd.setReason(reason);
		nd.setNukees(nukees);
		nd.setMultiplier(multiplier);
		nd.setAmount(nukedAmount);
		nd.setSize(nukeDirSize);

        // adding to the nukelog.
        NukeBeans.getNukeBeans().add(nd);

		// adding nuke metadata to dir.
		try {
			nukeDir.addPluginMetaData(NukeData.NUKEDATA, nd);
		} catch (FileNotFoundException e) {
			logger.warn("Failed to add nuke metadata, dir gone: " + nukeDir.getPath(), e);
		}
        
        GlobalContext.getEventService().publishAsync(new NukeEvent(requestUser, "NUKE", nd));

		String section = GlobalContext.getGlobalContext().getSectionManager().lookup(nukeDir).getName();
		env.add("section", section);
		env.add("dir", nukeDirName);
		env.add("path", nukeDirPath);
		env.add("relpath", nukeDirPath.replaceAll("/"+section+"/",""));
		env.add("multiplier", ""+multiplier);
		env.add("nukedamount", Bytes.formatBytes(nukedAmount));
		env.add("reason", reason);
		env.add("size", Bytes.formatBytes(nukeDirSize));

		if (session instanceof BaseFtpConnection) {
			response.addComment(session.jprintf(_bundle, _keyPrefix+"nuke", env, requestUser));
			response.addComment(nukeeOutput);
		}

        return response;
    }

    public CommandResponse doSITE_NUKES(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		ReplacerEnvironment env = new ReplacerEnvironment();

		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().getSection(request.getArgument());

		if (request.hasArgument() && section.getName().equalsIgnoreCase("")) {
			return new CommandResponse(501, "Invalid section!");
		}

		if (NukeBeans.getNukeBeans().getAll().isEmpty()) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"nukes.empty", env, request.getUser()));
		}

        for (NukeData nd : NukeBeans.getNukeBeans().getAll()) {
			if (nd.getPath().startsWith(request.getArgument(), 1)) {
				env.add("path", nd.getPath());
				env.add("multiplier", nd.getMultiplier());
				env.add("usersnuked", nd.getNukees().size());
				env.add("size", nd.getSize());
				env.add("reason", nd.getReason());
				env.add("amount", nd.getAmount());
				env.add("nuker", nd.getUser());
				response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"nukes", env, request.getUser()));
			}
        }

		if (response.getComment().isEmpty()) {
			env.add("section", section.getName());
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"nukes.empty.section", env, request.getUser()));
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
     * @throws ImproperUsageException 
     */
    public CommandResponse doSITE_UNNUKE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }
        
        StringTokenizer st = new StringTokenizer(request.getArgument());

		Session session = request.getSession();

        DirectoryHandle currentDir = request.getCurrentDirectory();
		User user = session.getUserNull(request.getUser());
		String toName = st.nextToken();
		String toDir;
		String nukeName;

		if (!toName.startsWith(VirtualFileSystem.separator)) {
			// Not a full path, let's make it one and append [NUKED]- if needed.
			if (toName.startsWith("[NUKED]-")) {
				nukeName = toName;
				toName = toName.substring(8);
			} else {
				nukeName = "[NUKED]-" + toName;
            }
			if (request.getCurrentDirectory().isRoot()) {
				boolean searchIndex = request.getProperties().getProperty("search","true").
						equalsIgnoreCase("true");
				if (searchIndex) {
					// Get dirs from index system
					ArrayList<DirectoryHandle> dirsToUnNuke;
					try {
						dirsToUnNuke = NukeUtils.findNukeDirs(currentDir, user, nukeName);
					} catch (FileNotFoundException e) {
						logger.warn(e);
						return new CommandResponse(550, e.getMessage());
					}

					ReplacerEnvironment env = new ReplacerEnvironment();

					if (dirsToUnNuke.isEmpty()) {
						env.add("searchstr", nukeName);
						return new CommandResponse(550, session.jprintf(_bundle,_keyPrefix+"unnuke.search.empty", env, user));
					} else if (dirsToUnNuke.size() == 1) {
						toDir = dirsToUnNuke.get(0).getParent().getPath() + VirtualFileSystem.separator;
					} else {
						CommandResponse response = new CommandResponse(200);

						for (DirectoryHandle nukeDir : dirsToUnNuke) {
							try {
								env.add("name", nukeDir.getName());
								env.add("path", nukeDir.getPath());
								env.add("owner", nukeDir.getUsername());
								env.add("group", nukeDir.getGroup());
								env.add("size", Bytes.formatBytes(nukeDir.getSize()));
								response.addComment(session.jprintf(_bundle,_keyPrefix+"unnuke.search.item", env, user));
							} catch (FileNotFoundException e) {
								logger.warn("Dir deleted after index search?, skip and continue: " + nukeDir.getPath());
							}
						}

						response.addComment(session.jprintf(_bundle,_keyPrefix+"unnuke.search.end", env, user));

						// Return matching dirs and let user decide what to unnuke
						return response;
					}
				} else {
					toDir = VirtualFileSystem.separator;
				}
			} else {
				toDir = currentDir.getPath() + VirtualFileSystem.separator;
			}
		} else {
			// Full path to Nuked dir provided, append [NUKED]- if needed.
			toDir = VirtualFileSystem.fixPath(toName);
			toName = toDir.substring(toDir.lastIndexOf(VirtualFileSystem.separator)+1);
			toDir = toDir.substring(0,toDir.lastIndexOf(VirtualFileSystem.separator)+1);
			if (toName.startsWith("[NUKED]-")) {
				nukeName = toName;
				toName = toName.substring(8);
			} else {
				nukeName = "[NUKED]-" + toName;
			}
        }

        String reason;

        if (st.hasMoreTokens()) {
            reason = st.nextToken("");
        } else {
            reason = "";
        }

		DirectoryHandle nukeDir;

        try {
			nukeDir = currentDir.getDirectory(toDir+nukeName, user);
        } catch (FileNotFoundException e) {
			// Maybe dir was deleted/wiped, lets remove it from nukelog.
			try {
				NukeBeans.getNukeBeans().remove(toDir+toName);
			} catch (ObjectNotFoundException ex) {
				return new CommandResponse(500, toDir+nukeName + " doesnt exist and no nukelog for this path was found.");
			}
			return new CommandResponse(200,  toDir+nukeName + " doesnt exist, removed nuke from nukelog.");
        } catch (ObjectNotValidException e) {
			return new CommandResponse(550, toDir+nukeName + " is not a directory");
		}

        NukeData nukeData;

        try {
			nukeData = nukeDir.getPluginMetaData(NukeData.NUKEDATA);
        } catch (KeyNotFoundException ex) {
			// Try to delete from nukelog if its left there for some reason
			try {
				NukeBeans.getNukeBeans().remove(toDir+toName);
			} catch (ObjectNotFoundException e) {
				return new CommandResponse(500, "Unable to unnuke, dir is not nuked.");
			}
            return new CommandResponse(500, toDir+nukeName + " doesnt contain any nukedata and no nukelog for this path was found.");
        } catch (FileNotFoundException ex) {
            return new CommandResponse(550, "Could not find directory: " + nukeDir.getPath());
        }

		GlobalContext.getGlobalContext().getSlaveManager().cancelTransfersInDirectory(nukeDir);

		CommandResponse response = new CommandResponse(200, "Unnuke succeeded");

        try {
			nukeDir.renameToUnchecked(nukeDir.getNonExistentDirectoryHandle(toDir+toName));
			nukeDir = currentDir.getDirectory(toDir+toName, user); //updating reference.
        } catch (FileExistsException e) {
			response.addComment("Error renaming nuke, target dir already exist");
            return response;
        } catch (FileNotFoundException e) {
        	logger.fatal("How come "+nukeDir.getPath()+" was just here and now it isnt?", e);
        	response.addComment(nukeDir.getPath() + " does not exist, how?");
        	return response;
		} catch (ObjectNotValidException e) {
			return new CommandResponse(550, toDir+toName + " is not a directory");
		}
        
		StringBuffer nukeeOutput = new StringBuffer();
        ReplacerEnvironment env = new ReplacerEnvironment();

        for (NukedUser nukeeObj : NukeBeans.getNukeeList(nukeData)) {
            String nukeeName = nukeeObj.getUsername();
            User nukee;

            try {
                nukee = GlobalContext.getGlobalContext().getUserManager().getUserByName(nukeeName);
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

            nukee.getKeyedMap().incrementInt(NukeUserData.NUKED, -1);
			nukee.getKeyedMap().incrementLong(NukeUserData.NUKEDBYTES, -nukedAmount);

            nukee.commit();

			env.add("nukedamount", Bytes.formatBytes(nukedAmount));

			nukeeOutput.append(session.jprintf(_bundle, _keyPrefix+"unnuke.nukees", env, nukee));
        }

        try {
			NukeBeans.getNukeBeans().remove(toDir+toName);
        } catch (ObjectNotFoundException e) {
            response.addComment("Error removing nukelog entry, unnuking anyway.");
        }

        try {
			nukeDir.removePluginMetaData(NukeData.NUKEDATA);
        } catch (FileNotFoundException e) {
            logger.error("Failed to remove nuke metadata from '" + nukeDir.getPath() + "', dir does not exist anymore", e);
        }
        
        nukeData.setReason(reason);
        NukeEvent nukeEvent = new NukeEvent(session.getUserNull(request.getUser()), "UNNUKE", nukeData);
        GlobalContext.getEventService().publishAsync(nukeEvent);

		String section = GlobalContext.getGlobalContext().getSectionManager().lookup(nukeDir).getName();
		env.add("section", section);
		env.add("dir", nukeDir.getName());
		env.add("path", nukeDir.getPath());
		env.add("relpath", nukeDir.getPath().replaceAll("/"+section+"/",""));
		env.add("multiplier", ""+nukeData.getMultiplier());
		env.add("nukedamount", Bytes.formatBytes(nukeData.getAmount()));
		env.add("reason", reason);
		env.add("size", Bytes.formatBytes(nukeData.getSize()));

		if (session instanceof BaseFtpConnection) {
			response.addComment(session.jprintf(_bundle, _keyPrefix+"unnuke", env, user));
			response.addComment(nukeeOutput);
		}

        return response;
    }

	public CommandResponse doSITE_NUKESCLEAN(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		if (NukeBeans.getNukeBeans().getAll().isEmpty()) {
			response.addComment("Nukelog empty.");
		}

		ArrayList<String> entriesToRemove = new ArrayList<String>();

        for (NukeData nd : NukeBeans.getNukeBeans().getAll()) {
			// Construct new path with [NUKED]-
			String newPath = VirtualFileSystem.fixPath(nd.getPath());
			String fixedName = "[NUKED]-" + newPath.substring(newPath.lastIndexOf(VirtualFileSystem.separator)+1);
			newPath = newPath.substring(0,newPath.lastIndexOf(VirtualFileSystem.separator)+1) + fixedName;

			try {
				request.getCurrentDirectory().getDirectoryUnchecked(newPath);
				// Still here? .. all ok then, just continue with next item in nukelog
			} catch (FileNotFoundException e) {
				// Dir was deleted/wiped, lets remove it from nukelog.
				// Add path to list so we can delete it after going through entire nukelog
				entriesToRemove.add(nd.getPath());
			} catch (ObjectNotValidException e) {
				return new CommandResponse(550, newPath + " is not a directory");
			}
        }

		if (entriesToRemove.isEmpty()) {
			response.addComment("No entries to delete from nukelog.");
		} else {
			int deleted = 0;
			for (String path : entriesToRemove) {
				try {
					NukeBeans.getNukeBeans().remove(path);
					deleted++;
				} catch (ObjectNotFoundException e) {
					response.addComment("Error removing nukelog entry: " + path);
				}
			}
			response.addComment("Removed " + deleted + " invalid entries from the nukelog.");
		}

        return response;
    }
}
