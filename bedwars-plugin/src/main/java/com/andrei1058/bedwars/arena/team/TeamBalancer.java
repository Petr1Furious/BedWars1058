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

package com.andrei1058.bedwars.arena.team;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * Post-pass run after whichever {@link com.andrei1058.bedwars.api.arena.team.ITeamAssigner} placed the players
 * at game start (the built-in ones or an add-on's, e.g. TeamSelector).
 * <p>
 * Assigners tend to fill the first team with room before moving on, which with a large {@code maxInTeam}
 * puts every player who did not pick a team into the same team. This pass:
 * <ol>
 *     <li>redistributes players who made no explicit choice (no team selected, no party in the arena)
 *     evenly, always onto the least populated team;</li>
 *     <li>if all players still end up in a single team (everybody chose it, or one party), splits that
 *     team in two, moving team-selector choices before parties and whole parties before splitting one.</li>
 * </ol>
 */
public final class TeamBalancer {

    private static final String TEAM_SELECTOR_PLUGIN = "BedWars1058-TeamSelector";
    private static final String TEAM_SELECTOR_MANAGER = "com.andrei1058.bedwars.teamselector.teamselector.TeamManager";
    private static boolean teamSelectorHookFailed = false;

    private TeamBalancer() {
    }

    public static void balance(@NotNull IArena arena) {
        List<ITeam> teams = arena.getTeams();
        if (teams.size() < 2) return;

        List<Player> assigned = new ArrayList<>();
        for (Player p : arena.getPlayers()) {
            if (arena.getTeam(p) != null) assigned.add(p);
        }
        if (assigned.size() < 2) return;

        Set<UUID> chosen = getTeamSelectorChoices(arena, assigned);
        List<List<Player>> parties = getParties(arena, assigned);
        Set<UUID> inParty = new HashSet<>();
        parties.forEach(party -> party.forEach(p -> inParty.add(p.getUniqueId())));

        // 1. spread players who made no choice
        List<Player> free = new ArrayList<>();
        for (Player p : assigned) {
            if (!chosen.contains(p.getUniqueId()) && !inParty.contains(p.getUniqueId())) free.add(p);
        }
        Map<UUID, ITeam> before = new HashMap<>();
        for (Player p : free) {
            ITeam t = arena.getTeam(p);
            before.put(p.getUniqueId(), t);
            unassign(t, p);
        }
        for (Player p : free) {
            ITeam target = leastPopulated(arena, teams);
            if (target == null) target = before.get(p.getUniqueId());
            target.addPlayers(p);
        }

        // 2. never start with a single team
        ITeam only = null;
        for (ITeam t : teams) {
            if (t.getMembers().isEmpty()) continue;
            if (only != null) {
                logResult(arena, before);
                return;
            }
            only = t;
        }
        if (only == null || only.getMembers().size() < 2) return;
        ITeam other = null;
        for (ITeam t : teams) {
            if (t != only && t.getMembers().size() < arena.getMaxInTeam()) {
                other = t;
                break;
            }
        }
        if (other == null) return;

        // movable units in the order we prefer to move them: single players first, then parties (smallest first)
        List<List<Player>> units = new ArrayList<>();
        Set<UUID> inUnit = new HashSet<>();
        for (List<Player> party : parties) {
            List<Player> here = new ArrayList<>();
            for (Player p : party) {
                if (only.isMember(p)) here.add(p);
            }
            if (!here.isEmpty()) {
                units.add(here);
                here.forEach(p -> inUnit.add(p.getUniqueId()));
            }
        }
        for (Player p : new ArrayList<>(only.getMembers())) {
            if (!inUnit.contains(p.getUniqueId())) units.add(Collections.singletonList(p));
        }
        units.sort(Comparator.comparingInt(List::size));

        int total = only.getMembers().size();
        int half = (total + 1) / 2;
        int room = arena.getMaxInTeam() - other.getMembers().size();
        List<Player> moved = new ArrayList<>();
        for (List<Player> unit : units) {
            if (moved.size() + unit.size() > half || moved.size() + unit.size() > room) continue;
            moved.addAll(unit);
        }
        if (moved.isEmpty()) {
            // everybody is in one party (or parties too large to move whole): split the largest one
            List<Player> largest = units.get(units.size() - 1);
            for (int i = 0; i < Math.min(total / 2, room) && i < largest.size(); i++) {
                moved.add(largest.get(i));
            }
        }
        for (Player p : moved) {
            unassign(only, p);
            other.addPlayers(p);
        }
        BedWars.plugin.getLogger().info("Arena " + arena.getArenaName() + ": all " + total + " players were in team "
                + only.getName() + ", moved " + moved.size() + " to team " + other.getName());
        logResult(arena, before);
    }

    private static void logResult(IArena arena, Map<UUID, ITeam> before) {
        StringBuilder sb = new StringBuilder();
        for (ITeam t : arena.getTeams()) {
            if (t.getMembers().isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.getName()).append('=').append(t.getMembers().size());
        }
        BedWars.plugin.getLogger().info("Arena " + arena.getArenaName() + " teams at start: " + sb
                + " (" + before.size() + " players without a choice were spread)");
    }

    private static ITeam leastPopulated(IArena arena, List<ITeam> teams) {
        ITeam best = null;
        for (ITeam t : teams) {
            if (t.getMembers().size() >= arena.getMaxInTeam()) continue;
            if (best == null || t.getMembers().size() < best.getMembers().size()) best = t;
        }
        return best;
    }

    private static void unassign(ITeam team, Player p) {
        if (team instanceof BedWarsTeam) {
            ((BedWarsTeam) team).unassignPlayer(p);
        } else {
            team.getMembers().remove(p);
            //noinspection deprecation
            team.destroyBedHolo(p);
        }
    }

    /**
     * Groups of at least two arena players sharing a party.
     */
    private static List<List<Player>> getParties(IArena arena, List<Player> assigned) {
        List<List<Player>> parties = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Player p : assigned) {
            if (seen.contains(p.getUniqueId())) continue;
            if (!BedWars.getParty().hasParty(p)) continue;
            List<Player> members = BedWars.getParty().getMembers(p);
            if (members == null) continue;
            List<Player> group = new ArrayList<>();
            for (Player m : members) {
                if (assigned.contains(m) && seen.add(m.getUniqueId())) group.add(m);
            }
            if (!group.contains(p) && seen.add(p.getUniqueId())) group.add(p);
            if (group.size() >= 2) parties.add(group);
        }
        return parties;
    }

    /**
     * Players who picked a team with the TeamSelector add-on, read through reflection so the add-on stays optional.
     */
    private static Set<UUID> getTeamSelectorChoices(IArena arena, List<Player> players) {
        Set<UUID> chosen = new HashSet<>();
        if (teamSelectorHookFailed) return chosen;
        Plugin addon = Bukkit.getPluginManager().getPlugin(TEAM_SELECTOR_PLUGIN);
        if (addon == null || !addon.isEnabled()) return chosen;
        try {
            Class<?> manager = Class.forName(TEAM_SELECTOR_MANAGER, true, addon.getClass().getClassLoader());
            Object instance = manager.getMethod("getInstance").invoke(null);
            Method getPlayerTeam = manager.getMethod("getPlayerTeam", Player.class, IArena.class);
            for (Player p : players) {
                if (getPlayerTeam.invoke(instance, p, arena) != null) chosen.add(p.getUniqueId());
            }
        } catch (Throwable t) {
            teamSelectorHookFailed = true;
            BedWars.plugin.getLogger().log(Level.WARNING, "Could not read TeamSelector choices; "
                    + "treating every player as not having picked a team", t);
        }
        return chosen;
    }
}
