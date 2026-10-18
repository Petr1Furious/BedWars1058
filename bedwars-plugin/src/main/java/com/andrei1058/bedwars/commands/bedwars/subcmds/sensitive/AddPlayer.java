/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.commands.bedwars.subcmds.sensitive;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.command.ParentCommand;
import com.andrei1058.bedwars.api.command.SubCommand;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.Misc;
import com.andrei1058.bedwars.commands.bedwars.MainCommand;
import com.andrei1058.bedwars.configuration.Permissions;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code /bw addPlayer <arena|group|random> <player|selector>... [-f]}
 * <p>
 * Puts one or more players into a game, the same way {@code /bw join} would for them, but run by an
 * admin (or the console). Targets are player names or vanilla selectors such as {@code @a} or
 * {@code @a[distance=..10]}. Party checks are skipped. Players already in another game are left alone
 * unless {@code -f} is given, which takes them out of it first.
 */
public class AddPlayer extends SubCommand {

    public AddPlayer(ParentCommand parent, String name) {
        super(parent, name);
        setPriority(12);
        showInList(true);
        setPermission(Permissions.PERMISSION_ADD_PLAYER);
        setDisplayInfo(Misc.msgHoverClick("§6 ▪ §7/" + getParent().getName() + " " + getSubCommandName()
                        + " §6<arena|group|random> <player|@selector...> §7[-f]",
                "§fPut players into a game.\n§fSelectors like §e@a§f work.\n§e-f §fmoves players out of their current game first."
                        + "\n§fPermission: §c" + Permissions.PERMISSION_ADD_PLAYER,
                "/" + getParent().getName() + " " + getSubCommandName() + " ", ClickEvent.Action.SUGGEST_COMMAND));
    }

    @Override
    public boolean execute(String[] args, CommandSender s) {
        List<String> targets = new ArrayList<>();
        boolean force = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f") || args[i].equalsIgnoreCase("--force")) {
                force = true;
            } else {
                targets.add(args[i]);
            }
        }
        if (args.length < 1 || targets.isEmpty()) {
            s.sendMessage("§c▪ §7Usage: §o/" + getParent().getName() + " " + getSubCommandName()
                    + " <arena|group|random> <player|@selector...> [-f]");
            return true;
        }

        String destination = args[0];
        IArena arena = Arena.getArenaByName(destination);
        if (arena == null) {
            arena = Arena.getArenaByIdentifier(destination);
        }
        boolean group = arena == null && (destination.equalsIgnoreCase("random")
                || MainCommand.isArenaGroup(destination) || destination.contains("+"));
        if (arena == null && !group) {
            s.sendMessage("§c▪ §7No arena or arena group called §e" + destination + "§7.");
            return true;
        }

        Set<Player> players = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String target : targets) {
            Collection<Player> resolved = resolve(s, target);
            if (resolved == null) {
                continue; // invalid selector, already reported
            }
            if (resolved.isEmpty()) {
                unknown.add(target);
            } else {
                players.addAll(resolved);
            }
        }

        List<String> added = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> moving = new ArrayList<>();
        for (Player player : players) {
            IArena current = Arena.getArenaByPlayer(player);
            if (current != null) {
                if (current == arena) {
                    failed.add(player.getName() + " (already there)");
                    continue;
                }
                if (!force) {
                    failed.add(player.getName() + " (in " + current.getArenaName() + ", use -f)");
                    continue;
                }
                if (current.isSpectator(player)) {
                    current.removeSpectator(player, false);
                } else {
                    current.removePlayer(player, false);
                }
                // leaving teleports the player back to the lobby first, join afterwards
                IArena finalArena = arena;
                moving.add(player.getName());
                Bukkit.getScheduler().runTaskLater(BedWars.plugin, () -> {
                    if (player.isOnline() && !join(player, finalArena, destination)) {
                        s.sendMessage("§c▪ §7Could not add §e" + player.getName() + "§7 to §e" + destination + "§7.");
                    }
                }, 5L);
                continue;
            }
            if (join(player, arena, destination)) {
                added.add(player.getName());
            } else {
                failed.add(player.getName());
            }
        }

        String where = arena == null ? destination : arena.getDisplayName() + " §7(" + arena.getArenaName() + ")";
        if (!added.isEmpty()) {
            s.sendMessage("§6 ▪ §7Added to §e" + where + "§7: §f" + String.join("§7, §f", added));
        }
        if (!moving.isEmpty()) {
            s.sendMessage("§6 ▪ §7Moving to §e" + where + "§7: §f" + String.join("§7, §f", moving));
        }
        if (!failed.isEmpty()) {
            s.sendMessage("§c▪ §7Could not add: §f" + String.join("§7, §f", failed));
        }
        if (!unknown.isEmpty()) {
            s.sendMessage("§c▪ §7No online player matches: §f" + String.join("§7, §f", unknown));
        }
        return true;
    }

    private static boolean join(Player player, @Nullable IArena arena, String destination) {
        if (arena != null) {
            return arena.addPlayer(player, true);
        }
        if (destination.equalsIgnoreCase("random")) {
            return Arena.joinRandomArena(player);
        }
        return Arena.joinRandomFromGroup(player, destination);
    }

    /**
     * A player name, or an entity selector (needs the 1.13+ API, called reflectively because this module
     * compiles against the 1.8 API).
     */
    @SuppressWarnings("unchecked")
    private static @Nullable Collection<Player> resolve(@NotNull CommandSender sender, @NotNull String target) {
        if (!target.startsWith("@")) {
            Player player = Bukkit.getPlayerExact(target);
            return player == null ? List.of() : List.of(player);
        }
        try {
            Method select = Bukkit.class.getMethod("selectEntities", CommandSender.class, String.class);
            List<Entity> entities = (List<Entity>) select.invoke(null, sender, target);
            List<Player> players = new ArrayList<>();
            for (Entity entity : entities) {
                if (entity instanceof Player) {
                    players.add((Player) entity);
                }
            }
            return players;
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            sender.sendMessage("§c▪ §7Invalid selector §e" + target + "§7: " +
                    (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            return null;
        }
    }

    @Override
    public List<String> getTabComplete() {
        List<String> tab = new ArrayList<>();
        tab.add("random");
        if (BedWars.config.getYml().get("arenaGroups") != null) {
            tab.addAll(BedWars.config.getYml().getStringList("arenaGroups"));
        }
        for (IArena arena : Arena.getArenas()) {
            tab.add(arena.getArenaName());
        }
        return tab;
    }

    @Override
    public boolean canSee(CommandSender s, com.andrei1058.bedwars.api.BedWars api) {
        return hasPermission(s);
    }
}
