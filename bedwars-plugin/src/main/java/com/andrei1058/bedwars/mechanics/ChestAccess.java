package com.andrei1058.bedwars.mechanics;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Team chest ownership: a container on a team's island belongs to that team. Only its members may use
 * it while the team is alive; once the team is eliminated (bed gone, no members left) anyone may.
 */
public final class ChestAccess {

    private ChestAccess() {
    }

    /**
     * @return true for the containers protected like team chests (chest, trapped chest, barrel).
     */
    public static boolean isTeamContainer(Material m) {
        if (m == Material.CHEST || m == Material.TRAPPED_CHEST) return true;
        return m.name().equals("BARREL");
    }

    public static @Nullable ITeam owner(IArena arena, Location location) {
        ITeam owner = null;
        int isRad = arena.getConfig().getInt(ConfigPath.ARENA_ISLAND_RADIUS);
        for (ITeam t : arena.getTeams()) {
            Location spawn = t.getSpawn();
            if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(location.getWorld())) continue;
            if (spawn.distance(location) <= isRad) {
                owner = t;
            }
        }
        return owner;
    }

    /**
     * @return true if the player may open / use this team container.
     */
    public static boolean canUse(IArena arena, Player player, Block block) {
        ITeam owner = owner(arena, block.getLocation());
        if (owner == null || owner.isMember(player)) return true;
        return owner.getMembers().isEmpty() && owner.isBedDestroyed();
    }
}
