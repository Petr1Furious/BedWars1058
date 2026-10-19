package com.andrei1058.bedwars.arena.upgrades;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.andrei1058.bedwars.BedWars.config;
import static com.andrei1058.bedwars.BedWars.plugin;

/**
 * Heal pool particles around a team's island.
 * <p>
 * Runs on the main thread (block access is not thread safe) and samples a bounded number of random positions per
 * run instead of scanning the whole island cube, so the cost no longer grows with the island radius cubed.
 * The task cancels itself as soon as its arena stops playing, so it cannot outlive the game.
 */
public class HealPoolTask extends BukkitRunnable {

    /**
     * Upper bound of positions sampled per run (every 4 s).
     */
    private static final int MAX_SAMPLES = 400;

    private final ITeam bwt;
    private final IArena arena;
    private int maxX, minX, maxY, minY, maxZ, minZ;
    private int samples;

    // only touched from the main thread
    private static final List<HealPoolTask> healPoolTasks = new ArrayList<>();

    public HealPoolTask(ITeam bwt) {
        this.bwt = bwt;
        this.arena = bwt == null ? null : bwt.getArena();
        if (bwt == null || bwt.getSpawn() == null || arena == null) {
            return;
        }
        int radius = arena.getConfig().getInt(ConfigPath.ARENA_ISLAND_RADIUS);
        Location teamspawn = bwt.getSpawn();
        this.maxX = (teamspawn.getBlockX() + radius);
        this.minX = (teamspawn.getBlockX() - radius);
        this.maxY = (teamspawn.getBlockY() + radius);
        this.minY = (teamspawn.getBlockY() - radius);
        this.maxZ = (teamspawn.getBlockZ() + radius);
        this.minZ = (teamspawn.getBlockZ() - radius);
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        // the old full scan showed a particle on ~1/9 of the air blocks; keep that ratio for small islands
        this.samples = (int) Math.max(1, Math.min(MAX_SAMPLES, volume / 9));
        this.runTaskTimer(plugin, 0, 80L);
        healPoolTasks.add(this);
    }

    @Override
    public void run() {
        if (bwt == null || bwt.getSpawn() == null || arena == null || arena.getStatus() != GameState.playing) {
            cancel();
            healPoolTasks.remove(this);
            return;
        }
        World world = arena.getWorld();
        if (world == null) return;

        List<Player> viewers = config.getBoolean(ConfigPath.GENERAL_CONFIGURATION_HEAL_POOL_SEEN_TEAM_ONLY) ?
                bwt.getMembers() : arena.getPlayers();
        if (viewers.isEmpty()) return;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < samples; i++) {
            int x = r.nextInt(minX, maxX + 1);
            int y = r.nextInt(minY, maxY + 1);
            int z = r.nextInt(minZ, maxZ + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            if (world.getBlockAt(x, y, z).getType() != Material.AIR) continue;
            Location l = new Location(world, x + .5, y + .5, z + .5);
            for (Player p : viewers) {
                BedWars.nms.playVillagerEffect(p, l);
            }
        }
    }

    public static boolean exists(IArena arena, ITeam bwt) {
        for (HealPoolTask hpt : healPoolTasks) {
            if (hpt.getArena() == arena && hpt.getBwt() == bwt) return true;
        }
        return false;
    }

    public static void removeForArena(IArena a) {
        if (a == null) return;
        removeIf(hpt -> a.equals(hpt.getArena()));
    }

    public static void removeForArena(String worldName) {
        if (worldName == null) return;
        removeIf(hpt -> hpt.getArena() != null && worldName.equals(hpt.getArena().getWorldName()));
    }

    public static void removeForTeam(ITeam team) {
        if (team == null) return;
        removeIf(hpt -> team.equals(hpt.getBwt()));
    }

    private static void removeIf(java.util.function.Predicate<HealPoolTask> filter) {
        Iterator<HealPoolTask> it = healPoolTasks.iterator();
        while (it.hasNext()) {
            HealPoolTask hpt = it.next();
            if (hpt == null) {
                it.remove();
                continue;
            }
            if (filter.test(hpt)) {
                try {
                    hpt.cancel();
                } catch (IllegalStateException ignored) {
                    // not scheduled
                }
                it.remove();
            }
        }
    }

    public ITeam getBwt() {
        return bwt;
    }

    public IArena getArena() {
        return arena;
    }
}
