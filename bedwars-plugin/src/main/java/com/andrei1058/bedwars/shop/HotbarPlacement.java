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

package com.andrei1058.bedwars.shop;

import com.andrei1058.bedwars.api.arena.shop.IBuyItem;
import com.andrei1058.bedwars.api.arena.shop.IContentTier;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.andrei1058.bedwars.BedWars.nms;

/**
 * Hotbar number-key buying: pressing 1-9 over a shop item buys it into that hotbar slot.
 * <p>
 * {@link com.andrei1058.bedwars.shop.main.CategoryContent#execute} arms a one-shot target slot
 * around {@code giveItems}; the first non-armour item handed out goes to that slot and whatever
 * held it moves to the next free hotbar slot (then the main inventory).
 */
public final class HotbarPlacement {

    /** Main-inventory storage slots: 0-8 hotbar, 9-35 main. */
    private static final int STORAGE_SIZE = 36;

    private static final Map<UUID, Integer> targets = new HashMap<>();

    private HotbarPlacement() {
    }

    public static void arm(Player player, int hotbarSlot) {
        targets.put(player.getUniqueId(), hotbarSlot);
    }

    public static void disarm(Player player) {
        targets.remove(player.getUniqueId());
    }

    /**
     * Add a bought item: into the armed hotbar slot if there is one (consuming it), else as before.
     */
    public static void addItem(Player player, ItemStack item) {
        Integer slot = targets.remove(player.getUniqueId());
        if (slot == null) {
            player.getInventory().addItem(item);
            return;
        }
        place(player, item, slot);
    }

    /**
     * The first item of a tier that would go to the hotbar, or null if all of it is auto-equipped armour
     * (the number key then has no effect on placement).
     */
    public static @Nullable IBuyItem hotbarItem(IContentTier tier) {
        for (IBuyItem bi : tier.getBuyItemsList()) {
            if (bi.getItemStack() == null) continue;
            if (bi.isAutoEquip() && nms.isArmor(bi.getItemStack())) continue;
            return bi;
        }
        return null;
    }

    /**
     * Checked before any money is taken. False only when slot N holds something that must move
     * and the purchase cannot free a place for it, i.e. the inventory is genuinely full.
     */
    public static boolean canPlace(Player player, IBuyItem bought, String upgradeIdentifier, @Nullable ITeam team, int slot) {
        PlayerInventory inv = player.getInventory();
        ItemStack old = inv.getItem(slot);
        if (isEmpty(old)) return true;
        // the old tier of this very item is removed by the upgrade
        if (upgradeIdentifier.equals(ShopItemTags.upgradeId(old))) return true;
        ItemStack item = bought.getItemStack().clone();
        if (team != null) item = nms.colourItem(item, team);
        // a default (wooden) sword is removed when a sword at least as strong is bought
        if (nms.isSword(item) && nms.isSword(old) && nms.isCustomBedWarsItem(old)
                && "DEFAULT_ITEM".equals(nms.getCustomData(old)) && nms.getDamage(old) <= nms.getDamage(item)) {
            return true;
        }
        if (old.isSimilar(item) && old.getAmount() + item.getAmount() <= old.getMaxStackSize()) return true;
        return freeSlot(inv, slot) >= 0;
    }

    private static void place(Player player, ItemStack item, int slot) {
        PlayerInventory inv = player.getInventory();
        ItemStack old = inv.getItem(slot);
        if (isEmpty(old)) {
            inv.setItem(slot, item);
            return;
        }
        if (old.isSimilar(item) && old.getAmount() + item.getAmount() <= old.getMaxStackSize()) {
            old.setAmount(old.getAmount() + item.getAmount());
            inv.setItem(slot, old);
            return;
        }
        int free = freeSlot(inv, slot);
        inv.setItem(slot, item);
        if (free >= 0) {
            inv.setItem(free, old);
        } else {
            // canPlace() refuses this case up front; never lose the item if something raced it
            player.getWorld().dropItemNaturally(player.getLocation(), old);
        }
    }

    /**
     * Next free hotbar slot after {@code slot} (wrapping), then the first free main-inventory slot.
     */
    private static int freeSlot(PlayerInventory inv, int slot) {
        for (int k = 1; k < 9; k++) {
            int s = (slot + k) % 9;
            if (isEmpty(inv.getItem(s))) return s;
        }
        for (int s = 9; s < STORAGE_SIZE; s++) {
            if (isEmpty(inv.getItem(s))) return s;
        }
        return -1;
    }

    private static boolean isEmpty(@Nullable ItemStack i) {
        return i == null || i.getType() == Material.AIR || i.getAmount() <= 0;
    }
}
