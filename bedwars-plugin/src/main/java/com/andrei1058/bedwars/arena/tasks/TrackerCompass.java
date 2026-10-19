package com.andrei1058.bedwars.arena.tasks;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import static com.andrei1058.bedwars.BedWars.nms;
import static com.andrei1058.bedwars.api.language.Language.getMsg;

/**
 * Hypixel-style tracker: a compass bought in the shop once every enemy bed is gone. While a player carries it,
 * it points at the nearest enemy (updated every second by {@link GamePlayingTask}); while held, the action bar shows
 * the tracked team and the distance.
 */
public final class TrackerCompass {

    private TrackerCompass() {
    }

    public static boolean isTracker(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.COMPASS;
    }

    public static boolean isHoldingTracker(Player player) {
        // compiled against the 1.8 API: main hand only
        return isTracker(nms.getItemInHand(player));
    }

    private static boolean carriesTracker(Player player) {
        for (ItemStack i : player.getInventory().getContents()) {
            if (isTracker(i)) return true;
        }
        return false;
    }

    /**
     * @return true if no other team that still has players has its bed.
     */
    public static boolean allEnemyBedsDestroyed(IArena arena, ITeam own) {
        for (ITeam t : arena.getTeams()) {
            if (t == own) continue;
            if (t.getMembers().isEmpty()) continue;
            if (!t.isBedDestroyed()) return false;
        }
        return true;
    }

    @Nullable
    public static Player nearestEnemy(IArena arena, Player player, ITeam own) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player other : arena.getPlayers()) {
            if (other == player) continue;
            if (arena.isSpectator(other) || arena.isReSpawning(other)) continue;
            ITeam t = arena.getTeam(other);
            if (t == null || t == own) continue;
            if (!other.getWorld().equals(player.getWorld())) continue;
            double d = other.getLocation().distanceSquared(player.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    /**
     * Called once per second while the arena is playing.
     */
    public static void tick(IArena arena) {
        for (Player p : arena.getPlayers()) {
            if (arena.isSpectator(p) || arena.isReSpawning(p)) continue;
            ITeam own = arena.getTeam(p);
            if (own == null) continue;
            if (!carriesTracker(p)) continue;
            if (!allEnemyBedsDestroyed(arena, own)) continue;
            Player target = nearestEnemy(arena, p, own);
            if (target == null) continue;
            p.setCompassTarget(target.getLocation());
            if (isHoldingTracker(p)) {
                ITeam tt = arena.getTeam(target);
                String color = tt.getColor().chat().toString();
                int distance = (int) p.getLocation().distance(target.getLocation());
                nms.playAction(p, getMsg(p, Messages.FORMATTING_ACTION_BAR_TRACKING)
                        .replace("{team}", color + tt.getDisplayName(Language.getPlayerLanguage(p)))
                        .replace("{distance}", color + distance).replace("&", "§"));
            }
        }
    }
}
