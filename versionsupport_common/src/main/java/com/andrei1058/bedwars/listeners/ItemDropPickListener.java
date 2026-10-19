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

package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.generator.IGenerator;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.events.player.PlayerGeneratorCollectEvent;
import com.andrei1058.bedwars.api.server.ServerType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

import static com.andrei1058.bedwars.support.version.common.VersionCommon.api;

public class ItemDropPickListener {

    // 1.11 or older
    public static class PlayerDrop implements Listener {
        @EventHandler
        public void onDrop(PlayerDropItemEvent e){
            if (manageDrop(e.getPlayer(), e.getItemDrop())) e.setCancelled(true);
        }
    }

    // 1.11 or older
    public static class PlayerPickup implements Listener {
        @SuppressWarnings("deprecation")
        @EventHandler
        public void onDrop(PlayerPickupItemEvent e){
            if (managePickup(e.getItem(), e.getPlayer(), e.getRemaining())) e.setCancelled(true);
        }
    }

    // 1.13 or newer
    public static class EntityDrop implements Listener {
        @EventHandler
        public void onDrop(EntityDropItemEvent e){
            if (manageDrop(e.getEntity(), e.getItemDrop())) e.setCancelled(true);
        }
    }

    // 1.12 or newer
    public static class EntityPickup implements Listener {
        @EventHandler
        public void onPickup(EntityPickupItemEvent e){
            if (managePickup(e.getItem(), e.getEntity(), e.getRemaining())) e.setCancelled(true);
        }
    }

    // 1.9 or newer
    public static class ArrowCollect implements Listener {
        @EventHandler
        public void onArrowPick(PlayerPickupArrowEvent e){
            if (api.getArenaUtil().isSpectating(e.getPlayer())) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * @return true if event should be cancelled
     */
    private static boolean managePickup(Item item, LivingEntity player, int remaining) {
        if (!(player instanceof Player)) return false;
        if (api.getServerType() == ServerType.MULTIARENA) {
            //noinspection ConstantConditions
            if (player.getLocation().getWorld().getName().equalsIgnoreCase(api.getLobbyWorld())) {
                return true;
            }
        }
        IArena a = api.getArenaUtil().getArenaByPlayer((Player) player);
        if (a == null) return false;
        if (!a.isPlayer((Player) player)) {
            return true;
        }
        if (a.getStatus() != GameState.playing) {
            return true;
        }
        if (a.getRespawnSessions().containsKey(player)) {
            return true;
        }
        if (item.getItemStack().getType() == Material.ARROW) {
            item.setItemStack(api.getVersionSupport().createItemStack(item.getItemStack().getType().toString(), item.getItemStack().getAmount(), (short) 0));
            return false;
        }

        if (item.getItemStack().getType().toString().equals("BED")) {
            item.remove();
            return true;
        }

        String genTag = api.getVersionSupport().getGeneratorItemTag(item);
        if (genTag != null) {
            //Cancel event if player is afk
            if (api.getAFKUtil().isPlayerAFK((Player) player)) return true;
            //Call ore pick up event
            PlayerGeneratorCollectEvent event = new PlayerGeneratorCollectEvent((Player) player, item, a);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return true;
            int picked = item.getItemStack().getAmount() - Math.max(0, remaining);
            if (picked > 0) {
                splitWithTeammates(a, (Player) player, item, genTag, picked);
            }
        }
        return false;
    }

    /**
     * Hypixel generator split: teammates of the picker standing near the team forge the item came from
     * get the same amount of iron / gold.
     */
    private static void splitWithTeammates(IArena a, Player picker, Item item, String genTag, int amount) {
        Material type = item.getItemStack().getType();
        if (type != Material.IRON_INGOT && type != Material.GOLD_INGOT) return;
        int sep = genTag.lastIndexOf('|');
        String teamName = sep < 0 ? genTag : genTag.substring(0, sep);
        if (teamName.isEmpty()) return; // diamond / emerald generator
        if (!api.getConfigs().getMainConfig().getBoolean(ConfigPath.GENERAL_CONFIGURATION_ENABLE_GEN_SPLIT)) return;
        ITeam pickerTeam = a.getTeam(picker);
        if (pickerTeam == null || pickerTeam.getMembers().size() <= 1) return;
        ITeam genTeam = a.getTeam(teamName);
        if (genTeam == null) return;

        // the generator this item came from: the nearest one of that team
        Location itemLoc = item.getLocation();
        Location genLoc = null;
        double best = Double.MAX_VALUE;
        for (IGenerator g : genTeam.getGenerators()) {
            Location l = g.getLocation();
            if (l == null || l.getWorld() == null || !l.getWorld().equals(itemLoc.getWorld())) continue;
            double d = l.distanceSquared(itemLoc);
            if (d < best) {
                best = d;
                genLoc = l;
            }
        }
        if (genLoc == null) return;

        double radius = api.getConfigs().getMainConfig().getYml().getDouble(ConfigPath.GENERAL_CONFIGURATION_GEN_SPLIT_RADIUS, 2.0);
        double radiusSq = radius * radius;
        for (Player mate : pickerTeam.getMembers()) {
            if (mate.equals(picker)) continue;
            if (!a.isPlayer(mate) || a.isSpectator(mate) || a.isReSpawning(mate)) continue;
            if (!mate.getWorld().equals(genLoc.getWorld())) continue;
            if (mate.getLocation().distanceSquared(genLoc) > radiusSq) continue;
            ItemStack copy = new ItemStack(type, amount);
            for (ItemStack excess : mate.getInventory().addItem(copy).values()) {
                mate.getWorld().dropItem(mate.getLocation(), excess);
            }
            try {
                mate.playSound(mate.getLocation(), "minecraft:entity.item.pickup", 0.2f, 1.8f);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * @return true to cancel the event.
     */
    private static boolean manageDrop(Entity player, Item item) {
        if (!(player instanceof Player)) return false;
        if (api.getServerType() == ServerType.MULTIARENA) {
            //noinspection ConstantConditions
            if (player.getLocation().getWorld().getName().equalsIgnoreCase(api.getLobbyWorld())) {
                return true;
            }
        }
        IArena a = api.getArenaUtil().getArenaByPlayer((Player) player);
        if (a == null) return false;

        if (!a.isPlayer((Player) player)) {
            return true;
        }

        if (a.getStatus() != GameState.playing) {
            return true;
        } else {
            ItemStack i = item.getItemStack();
            if (i.getType() == Material.COMPASS) {
                return true;
            }
        }

        return a.getRespawnSessions().containsKey(player);
    }
}
