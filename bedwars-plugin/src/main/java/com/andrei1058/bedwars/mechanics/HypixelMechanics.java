package com.andrei1058.bedwars.mechanics;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Registers the Hypixel-style mechanics: punch-to-deposit, invisible-player footprints and the
 * related messages and config defaults. Final kill drops, the generator split and the trap rules live
 * in their own classes.
 */
public final class HypixelMechanics {

    public static final String FOOTPRINTS_ENABLED = "footprints.enabled";
    public static final String FOOTPRINTS_INTERVAL = "footprints.interval-ticks";
    public static final String FOOTPRINTS_SPECTATORS = "footprints.show-to-spectators";
    public static final String FOOTPRINTS_PARTICLE = "footprints.particle";
    public static final String PUNCH_DEPOSIT_ENABLED = "punch-to-deposit";

    private HypixelMechanics() {
    }

    public static void init(BedWars plugin) {
        MechanicsMessages.saveDefaults();

        YamlConfiguration yml = BedWars.config.getYml();
        yml.addDefault(ConfigPath.GENERAL_CONFIGURATION_GEN_SPLIT_RADIUS, 2.0);
        yml.addDefault(FOOTPRINTS_ENABLED, true);
        yml.addDefault(FOOTPRINTS_INTERVAL, 4);
        yml.addDefault(FOOTPRINTS_SPECTATORS, true);
        yml.addDefault(FOOTPRINTS_PARTICLE, "DUST");
        yml.addDefault(PUNCH_DEPOSIT_ENABLED, true);
        yml.options().copyDefaults(true);
        BedWars.config.save();

        if (yml.getBoolean(PUNCH_DEPOSIT_ENABLED)) {
            Bukkit.getPluginManager().registerEvents(new PunchDepositListener(), plugin);
        }
        if (yml.getBoolean(FOOTPRINTS_ENABLED)) {
            long interval = Math.max(1, yml.getInt(FOOTPRINTS_INTERVAL));
            Bukkit.getScheduler().runTaskTimer(plugin, new FootprintTask(yml.getBoolean(FOOTPRINTS_SPECTATORS), yml.getString(FOOTPRINTS_PARTICLE, "DUST")), interval, interval);
        }
    }
}
