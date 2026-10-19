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


package com.andrei1058.bedwars.listeners.dropshandler;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent;
import com.andrei1058.bedwars.api.language.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.andrei1058.bedwars.api.language.Language.getMsg;

public class PlayerDrops {

    private PlayerDrops() {
    }

    /**
     * if bedWars should handle drops behavior.
     * <p>
     * Final kills (Hypixel style): the victim's resources go to the killer's inventory, the victim's
     * ender chest drops at the killer's team forge, nothing else drops. Without a killer the resources
     * are lost and the ender chest drops at the victim's own forge.
     * <p>
     * Normal kills: resources go to the killer, or drop on the floor without one.
     *
     * @return true if event drops must be cleared.
     */
    public static boolean handlePlayerDrops(IArena arena, Player victim, Player killer, ITeam victimsTeam, ITeam killersTeam, PlayerKillEvent.PlayerKillCause cause, List<ItemStack> inventory) {
        if (arena.getConfig().getBoolean(ConfigPath.ARENA_NORMAL_DEATH_DROPS)) {
            return false;
        }
        if (killer != null && killer.getUniqueId().equals(victim.getUniqueId())) {
            killer = null;
        }
        if (killer != null && killersTeam == null) {
            killersTeam = arena.getTeam(killer);
        }

        if (cause.isFinalKill() || (victimsTeam != null && victimsTeam.isBedDestroyed())) {
            // ender chest -> killer's forge, or the victim's own forge without a killer
            ITeam forgeTeam = killersTeam != null && !killersTeam.equals(victimsTeam) ? killersTeam : victimsTeam;
            if (forgeTeam != null) {
                Location dropsLocation = forgeLocation(arena.getWorld(), forgeTeam);
                for (ItemStack item : victim.getEnderChest().getContents()) {
                    if (item == null || item.getType() == Material.AIR) continue;
                    arena.getWorld().dropItem(dropsLocation, item);
                }
                victim.getEnderChest().clear();
            }
            // resources -> killer, lost otherwise
            if (killer != null && canReceive(arena, killer) && !cause.isDespawnable()) {
                giveResources(killer, inventory);
            }
            return true;
        }

        // regular kill
        if (killer == null || cause.isDespawnable() || cause.isPvpLogOut()
                || cause == PlayerKillEvent.PlayerKillCause.PLAYER_PUSH) {
            dropItems(victim, inventory);
            return true;
        }
        if (canReceive(arena, killer)) {
            giveResources(killer, inventory);
        }
        return true;
    }

    private static boolean canReceive(IArena arena, Player killer) {
        return arena.isPlayer(killer) && !arena.isReSpawning(killer) && !arena.isSpectator(killer);
    }

    public static Location forgeLocation(World world, ITeam team) {
        Vector v = team.getKillDropsLocation();
        return new Location(world, v.getBlockX() + 0.5, v.getY(), v.getBlockZ() + 0.5);
    }

    private static boolean isResource(@Nullable ItemStack i) {
        if (i == null) return false;
        Material m = i.getType();
        return m == Material.DIAMOND || m == Material.EMERALD || m == Material.IRON_INGOT || m == Material.GOLD_INGOT;
    }

    private static void giveResources(Player killer, List<ItemStack> inventory) {
        Map<Material, Integer> materialDrops = new EnumMap<>(Material.class);
        for (ItemStack i : inventory) {
            if (!isResource(i)) continue;
            killer.getInventory().addItem(i.clone());
            materialDrops.merge(i.getType(), i.getAmount(), Integer::sum);
        }

        for (Map.Entry<Material, Integer> entry : materialDrops.entrySet()) {
            String msg = "";
            int amount = entry.getValue();
            switch (entry.getKey()) {
                case DIAMOND:
                    msg = getMsg(killer, Messages.PLAYER_DIE_REWARD_DIAMOND).replace("{meaning}", amount == 1 ?
                            getMsg(killer, Messages.MEANING_DIAMOND_SINGULAR) : getMsg(killer, Messages.MEANING_DIAMOND_PLURAL));
                    break;
                case EMERALD:
                    msg = getMsg(killer, Messages.PLAYER_DIE_REWARD_EMERALD).replace("{meaning}", amount == 1 ?
                            getMsg(killer, Messages.MEANING_EMERALD_SINGULAR) : getMsg(killer, Messages.MEANING_EMERALD_PLURAL));
                    break;
                case IRON_INGOT:
                    msg = getMsg(killer, Messages.PLAYER_DIE_REWARD_IRON).replace("{meaning}", amount == 1 ?
                            getMsg(killer, Messages.MEANING_IRON_SINGULAR) : getMsg(killer, Messages.MEANING_IRON_PLURAL));
                    break;
                case GOLD_INGOT:
                    msg = getMsg(killer, Messages.PLAYER_DIE_REWARD_GOLD).replace("{meaning}", amount == 1 ?
                            getMsg(killer, Messages.MEANING_GOLD_SINGULAR) : getMsg(killer, Messages.MEANING_GOLD_PLURAL));
                    break;
                default:
                    break;
            }
            killer.sendMessage(msg.replace("{amount}", String.valueOf(amount)));
        }
    }

    private static void dropItems(Player player, List<ItemStack> inventory) {
        for (ItemStack i : inventory) {
            if (!isResource(i)) continue;
            player.getLocation().getWorld().dropItemNaturally(player.getLocation(), i);
        }
    }
}
