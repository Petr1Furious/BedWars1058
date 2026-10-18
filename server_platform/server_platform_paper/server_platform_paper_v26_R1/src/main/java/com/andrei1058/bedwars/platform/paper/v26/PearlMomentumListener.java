package com.andrei1058.bedwars.platform.paper.v26;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * Since 1.21.2 a thrown projectile inherits the thrower's movement, so pearls thrown while running,
 * sprint-jumping or falling go much further (or shorter) than they used to. BedWars is balanced around
 * the old behaviour: the pearl leaves along the look direction at the vanilla pearl speed only.
 */
final class PearlMomentumListener implements Listener {

    // ThrownEnderpearl is shot with velocity 1.5 (EnderpearlItem)
    private static final double PEARL_SPEED = 1.5;

    private BedWars api;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player player)) {
            return;
        }
        BedWars api = api();
        if (api == null || api.getArenaUtil().getArenaByPlayer(player) == null) {
            return;
        }
        if (!api.getConfigs().getMainConfig().getBoolean(ConfigPath.GENERAL_CONFIGURATION_PEARL_IGNORE_MOMENTUM)) {
            return;
        }
        pearl.setVelocity(player.getEyeLocation().getDirection().multiply(PEARL_SPEED));
    }

    private BedWars api() {
        if (api == null) {
            var registration = Bukkit.getServicesManager().getRegistration(BedWars.class);
            if (registration != null) {
                api = registration.getProvider();
            }
        }
        return api;
    }
}
