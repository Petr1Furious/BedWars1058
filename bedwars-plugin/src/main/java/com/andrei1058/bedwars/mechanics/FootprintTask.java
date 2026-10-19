package com.andrei1058.bedwars.mechanics;

import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.arena.Arena;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.andrei1058.bedwars.BedWars.nms;

/**
 * Hypixel footprints: invisible players leave small dark particles where they walk, visible to the
 * other teams (and optionally spectators), never to their own team. Runs on the main thread every few
 * ticks and only looks at the arenas' invisible-player maps, so it costs nothing without invisibility.
 */
public class FootprintTask implements Runnable {

    private static final double MIN_MOVE_SQ = 0.15 * 0.15;
    private static final double VIEW_DISTANCE_SQ = 48 * 48;
    private static final double SIDE_OFFSET = 0.18;

    private final boolean showToSpectators;
    private final String particle;
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Set<UUID> leftFoot = new HashSet<>();

    public FootprintTask(boolean showToSpectators, String particle) {
        this.showToSpectators = showToSpectators;
        this.particle = particle;
    }

    @Override
    public void run() {
        if (lastLocation.isEmpty() && noInvisiblePlayers()) return;
        Set<UUID> seen = new HashSet<>();
        for (IArena a : Arena.getArenas()) {
            if (a.getStatus() != GameState.playing) continue;
            if (a.getShowTime().isEmpty()) continue;
            for (Player p : a.getShowTime().keySet()) {
                if (!p.isOnline() || !a.isPlayer(p) || a.isReSpawning(p)) continue;
                UUID id = p.getUniqueId();
                seen.add(id);
                Location now = p.getLocation();
                Location prev = lastLocation.put(id, now);
                if (prev == null || prev.getWorld() != now.getWorld()) continue;
                //noinspection deprecation
                if (!p.isOnGround()) continue;
                double dx = now.getX() - prev.getX(), dz = now.getZ() - prev.getZ();
                double moved = dx * dx + dz * dz;
                if (moved < MIN_MOVE_SQ) continue;

                // alternate left / right of the walking direction
                double len = Math.sqrt(moved);
                double side = leftFoot.remove(id) ? -SIDE_OFFSET : SIDE_OFFSET;
                if (side > 0) leftFoot.add(id);
                Location print = new Location(now.getWorld(),
                        now.getX() - dz / len * side, now.getY() + 0.05, now.getZ() + dx / len * side);
                show(a, p, print);
            }
        }
        lastLocation.keySet().retainAll(seen);
        leftFoot.retainAll(seen);
    }

    private static boolean noInvisiblePlayers() {
        for (IArena a : Arena.getArenas()) {
            if (!a.getShowTime().isEmpty()) return false;
        }
        return true;
    }

    private void show(IArena a, Player walker, Location print) {
        ITeam team = a.getTeam(walker);
        for (Player viewer : a.getPlayers()) {
            if (viewer.equals(walker)) continue;
            if (team != null && team.isMember(viewer)) continue;
            send(viewer, print);
        }
        if (showToSpectators) {
            for (Player viewer : a.getSpectators()) {
                send(viewer, print);
            }
        }
    }

    private void send(Player viewer, Location print) {
        if (viewer.getWorld() != print.getWorld()) return;
        if (viewer.getLocation().distanceSquared(print) > VIEW_DISTANCE_SQ) return;
        nms.showFootprint(viewer, print, particle);
    }
}
