////////////////////////////////////////////////////////////////////////////////////////////////////
// PlotSquared - A plot manager and world generator for the Bukkit API                             /
// Copyright (c) 2014 IntellectualSites/IntellectualCrafters                                       /
//                                                                                                 /
// This program is free software; you can redistribute it and/or modify                            /
// it under the terms of the GNU General Public License as published by                            /
// the Free Software Foundation; either version 3 of the License, or                               /
// (at your option) any later version.                                                             /
//                                                                                                 /
// This program is distributed in the hope that it will be useful,                                 /
// but WITHOUT ANY WARRANTY; without even the implied warranty of                                  /
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                                   /
// GNU General Public License for more details.                                                    /
//                                                                                                 /
// You should have received a copy of the GNU General Public License                               /
// along with this program; if not, write to the Free Software Foundation,                         /
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA                               /
//                                                                                                 /
// You can contact us via: support@intellectualsites.com                                           /
////////////////////////////////////////////////////////////////////////////////////////////////////
package com.intellectualcrafters.plot.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.database.DBFunc;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.object.comment.PlotComment;
import com.intellectualcrafters.plot.util.MainUtil;
import com.intellectualcrafters.plot.util.Permissions;
import com.intellectualcrafters.plot.util.TaskManager;

public class Inbox extends SubCommand {
    public Inbox() {
        super(Command.INBOX, "Review the comments for a plot", "inbox", CommandCategory.ACTIONS, true);
    }

    @Override
    public boolean execute(final PlotPlayer plr, final String... args) {
        boolean report = false;
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reports")) {
                report = true;
            }
        }
        final Location loc = plr.getLocation();
        final Plot plot = MainUtil.getPlot(loc);
        if ((plot == null) && !report) {
            return !sendMessage(plr, C.NOT_IN_PLOT);
        }
        if ((plot != null) && !plot.hasOwner()) {
            MainUtil.sendMessage(plr, C.NOT_IN_PLOT);
            return false;
        }
        Integer tier;
        final UUID uuid = plr.getUUID();
        if (Permissions.hasPermission(plr, "plots.comment.admin")) {
            tier = 0;
        } else if ((plot != null) && plot.isOwner(uuid)) {
            tier = 1;
        } else if ((plot != null) && plot.helpers.contains(uuid)) {
            tier = 2;
        } else if ((plot != null) && plot.trusted.contains(uuid)) {
            tier = 3;
        } else {
            tier = 4;
        }
        final boolean below;
        if (args.length > 0) {
            below = false;
            switch (args[0].toLowerCase()) {
                case "admin":
                    if (tier <= 0) {
                        tier = 0;
                    } else {
                        MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.inbox.admin");
                        return false;
                    }
                    break;
                case "owner":
                    if (tier <= 1) {
                        tier = 1;
                    } else {
                        MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.inbox.owner");
                        return false;
                    }
                    break;
                case "helper":
                    if (tier <= 2) {
                        tier = 2;
                    } else {
                        MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.inbox.helper");
                        return false;
                    }
                    break;
                case "trusted":
                    if (tier <= 3) {
                        tier = 3;
                    } else {
                        MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.inbox.trusted");
                        return false;
                    }
                    break;
                case "everyone":
                    if (tier <= 4) {
                        tier = 4;
                    } else {
                        MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.inbox.everyone");
                        return false;
                    }
                    break;
                case "reports":
                    if (tier <= 0) {
                        tier = -1;
                    } else {
                        MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.inbox.admin");
                        return false;
                    }
                    break;
                default:
                    MainUtil.sendMessage(plr, C.INVALID_INBOX, Arrays.copyOfRange(new String[] { "admin", "owner", "helper", "trusted", "everyone" }, Math.max(0, tier), 4));
                    return false;
            }
        } else {
            below = true;
        }
        final String world = loc.getWorld();
        final int tier2 = tier;
        TaskManager.runTaskAsync(new Runnable() {
            @Override
            public void run() {
                ArrayList<PlotComment> comments = null;
                if (tier2 == -1) {
                    comments = DBFunc.getComments(world, null, 0, false);
                } else {
                    comments = plot.settings.getComments(tier2);
                }
                if (comments == null) {
                    comments = DBFunc.getComments(world, plot, tier2, below);
                    plot.settings.setComments(comments);
                }
                if (args.length == 2) {
                    final String[] split = args[1].toLowerCase().split(":");
                    if (!split[0].equals("clear")) {
                        MainUtil.sendMessage(plr, "&c/plot inbox [tier] [clear][:#]");
                        return;
                    }
                    if (split.length > 1) {
                        try {
                            final int index = Integer.parseInt(split[1]);
                            final PlotComment comment = comments.get(index - 1);
                            DBFunc.removeComment(world, plot, comment);
                            plot.settings.removeComment(comment);
                            MainUtil.sendMessage(plr, C.COMMENT_REMOVED, "1 comment");
                            return;
                        } catch (final Exception e) {
                            MainUtil.sendMessage(plr, "&cInvalid index:\n/plot inbox [tier] [clear][:#]");
                            return;
                        }
                    }
                    for (final PlotComment comment : comments) {
                        DBFunc.removeComment(world, plot, comment);
                    }
                    plot.settings.removeComments(comments);
                    MainUtil.sendMessage(plr, C.COMMENT_REMOVED, "all comments in that category");
                } else {
                    final List<String> recipients = Arrays.asList("A", "O", "H", "T", "E");
                    int count = 1;
                    final StringBuilder message = new StringBuilder(C.COMMENT_HEADER.s());
                    for (final PlotComment comment : comments) {
                        message.append("\n&8[&7" + comment.id + "&8]" + "&c[" + count + "]&6[" + recipients.get(tier2 == -1 ? 0 : tier2) + "] &7" + comment.senderName + "&f: " + comment.comment);
                        count++;
                    }
                    if (comments.size() == 0) {
                        message.append("&cNo messages.");
                    }
                    MainUtil.sendMessage(plr, message.toString());
                }
            }
        });
        return true;
    }
}
