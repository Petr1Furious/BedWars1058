package com.andrei1058.bedwars.platform.paper.v26;

import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Applies arena game rules given with either the legacy camelCase names or the 1.21.9+ registry keys.
 * The mapping mirrors Mojang's GameRuleRegistryFix datafixer, including the inverted rules.
 */
final class GameRules26 {

    private record Legacy(String key, boolean inverted) {
    }

    private static final Map<String, Legacy> LEGACY = new HashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    static {
        map("allowEnteringNetherUsingPortals", "allow_entering_nether_using_portals");
        map("announceAdvancements", "show_advancement_messages");
        map("blockExplosionDropDecay", "block_explosion_drop_decay");
        map("commandBlockOutput", "command_block_output");
        map("enableCommandBlocks", "command_blocks_work");
        map("commandBlocksEnabled", "command_blocks_work");
        map("commandModificationBlockLimit", "max_block_modifications");
        LEGACY.put("disableElytraMovementCheck", new Legacy("elytra_movement_check", true));
        LEGACY.put("disablePlayerMovementCheck", new Legacy("player_movement_check", true));
        LEGACY.put("disableRaids", new Legacy("raids", true));
        map("doDaylightCycle", "advance_time");
        map("doEntityDrops", "entity_drops");
        map("doImmediateRespawn", "immediate_respawn");
        map("doInsomnia", "spawn_phantoms");
        map("doLimitedCrafting", "limited_crafting");
        map("doMobLoot", "mob_drops");
        map("doMobSpawning", "spawn_mobs");
        map("doPatrolSpawning", "spawn_patrols");
        map("doTileDrops", "block_drops");
        map("doTraderSpawning", "spawn_wandering_traders");
        map("doVinesSpread", "spread_vines");
        map("doWardenSpawning", "spawn_wardens");
        map("doWeatherCycle", "advance_weather");
        map("drowningDamage", "drowning_damage");
        map("enderPearlsVanishOnDeath", "ender_pearls_vanish_on_death");
        map("fallDamage", "fall_damage");
        map("fireDamage", "fire_damage");
        map("forgiveDeadPlayers", "forgive_dead_players");
        map("freezeDamage", "freeze_damage");
        map("globalSoundEvents", "global_sound_events");
        map("keepInventory", "keep_inventory");
        map("lavaSourceConversion", "lava_source_conversion");
        map("locatorBar", "locator_bar");
        map("logAdminCommands", "log_admin_commands");
        map("maxCommandChainLength", "max_command_sequence_length");
        map("maxCommandForkCount", "max_command_forks");
        map("maxEntityCramming", "max_entity_cramming");
        map("minecartMaxSpeed", "max_minecart_speed");
        map("mobExplosionDropDecay", "mob_explosion_drop_decay");
        map("mobGriefing", "mob_griefing");
        map("naturalRegeneration", "natural_health_regeneration");
        map("playersNetherPortalCreativeDelay", "players_nether_portal_creative_delay");
        map("playersNetherPortalDefaultDelay", "players_nether_portal_default_delay");
        map("playersSleepingPercentage", "players_sleeping_percentage");
        map("projectilesCanBreakBlocks", "projectiles_can_break_blocks");
        map("randomTickSpeed", "random_tick_speed");
        map("reducedDebugInfo", "reduced_debug_info");
        map("sendCommandFeedback", "send_command_feedback");
        map("showDeathMessages", "show_death_messages");
        map("snowAccumulationHeight", "max_snow_accumulation_height");
        map("spawnMonsters", "spawn_monsters");
        map("spawnRadius", "respawn_radius");
        map("spawnerBlocksEnabled", "spawner_blocks_work");
        map("spectatorsGenerateChunks", "spectators_generate_chunks");
        map("tntExplodes", "tnt_explodes");
        map("tntExplosionDropDecay", "tnt_explosion_drop_decay");
        map("universalAnger", "universal_anger");
        map("waterSourceConversion", "water_source_conversion");
    }

    private static void map(String legacy, String key) {
        LEGACY.put(legacy, new Legacy(key, false));
    }

    private GameRules26() {
    }

    static boolean apply(World world, String name, String value, Logger log) {
        name = name.trim();
        value = value.trim();
        // doFireTick became an integer radius: 0 = no fire spread, 128 = vanilla default
        if (name.equals("doFireTick")) {
            name = "fire_spread_radius_around_player";
            value = Boolean.parseBoolean(value) ? "128" : "0";
        }
        Legacy legacy = LEGACY.get(name);
        String key = legacy == null ? name : legacy.key();
        if (legacy != null && legacy.inverted()) {
            value = String.valueOf(!Boolean.parseBoolean(value));
        }
        GameRule<?> rule = lookup(key);
        if (rule == null) {
            if (WARNED.add(name)) {
                log.warning("Unknown arena game rule '" + name + "', ignored.");
            }
            return false;
        }
        return set(world, rule, value, name, log);
    }

    private static @Nullable GameRule<?> lookup(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        if (nk == null) return null;
        return Registry.GAME_RULE.get(nk);
    }

    @SuppressWarnings("unchecked")
    private static boolean set(World world, GameRule<?> rule, String value, String name, Logger log) {
        Class<?> type = rule.getType();
        try {
            if (type == Boolean.class) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                return world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
            }
            if (type == Integer.class) {
                return world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value));
            }
        } catch (IllegalArgumentException ex) {
            if (WARNED.add(name + "=" + value)) {
                log.warning("Invalid value '" + value + "' for arena game rule '" + name + "', ignored.");
            }
            return false;
        }
        if (WARNED.add(name)) {
            log.warning("Arena game rule '" + name + "' has an unsupported type " + type.getSimpleName() + ", ignored.");
        }
        return false;
    }
}
