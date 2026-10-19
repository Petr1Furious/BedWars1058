package com.andrei1058.bedwars.mechanics;

import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.shop.ShopItemTags;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Locale;

import static com.andrei1058.bedwars.BedWars.nms;

/**
 * Hypixel punch-to-deposit: left-clicking your team chest (or your ender chest) moves every item of the
 * held type from your inventory into it, with a chat message and a sound.
 */
public class PunchDepositListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPunch(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        boolean ender = b.getType() == Material.ENDER_CHEST;
        if (!ender && !ChestAccess.isTeamContainer(b.getType())) return;

        Player p = e.getPlayer();
        IArena a = Arena.getArenaByPlayer(p);
        if (a == null) return;
        if (a.getStatus() != GameState.playing || !a.isPlayer(p) || a.isSpectator(p) || a.isReSpawning(p)) return;

        ItemStack held = nms.getItemInHand(p);
        if (held == null || held.getType() == Material.AIR) return;
        if (!isDepositable(held)) return;
        // no block damage while depositing
        e.setCancelled(true);

        if (!ender && !ChestAccess.canUse(a, p, b)) {
            p.sendMessage(Language.getMsg(p, Messages.INTERACT_CHEST_CANT_OPEN_TEAM_ELIMINATED));
            return;
        }

        Inventory target;
        if (ender) {
            target = p.getEnderChest();
        } else {
            BlockState state = b.getState();
            if (!(state instanceof InventoryHolder)) return;
            target = ((InventoryHolder) state).getInventory();
        }

        ItemStack type = held.clone();
        int moved = deposit(p.getInventory(), target, type);
        if (moved == 0) {
            p.sendMessage(Language.getMsg(p, MechanicsMessages.DEPOSIT_FULL));
            return;
        }
        p.updateInventory();
        try {
            p.playSound(b.getLocation(), "minecraft:entity.item.pickup", 0.8f, 1.2f);
        } catch (RuntimeException ignored) {
        }
        p.sendMessage(Language.getMsg(p, ender ? MechanicsMessages.DEPOSIT_ENDER_CHEST : MechanicsMessages.DEPOSIT_TEAM_CHEST)
                .replace("{amount}", String.valueOf(moved))
                .replace("{item}", displayName(type)));
    }

    /**
     * Move every stack similar to {@code type} (storage slots and off-hand, not armour) into the target.
     *
     * @return amount moved.
     */
    private static int deposit(PlayerInventory from, Inventory target, ItemStack type) {
        int moved = 0;
        ItemStack[] contents = from.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            // 36-39 armour; 40 off-hand on 1.9+
            if (slot >= 36 && slot <= 39) continue;
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() == Material.AIR || !stack.isSimilar(type)) continue;
            int amount = stack.getAmount();
            HashMap<Integer, ItemStack> left = target.addItem(stack.clone());
            int remaining = left.values().stream().mapToInt(ItemStack::getAmount).sum();
            moved += amount - remaining;
            if (remaining <= 0) {
                from.setItem(slot, null);
            } else {
                ItemStack rest = stack.clone();
                rest.setAmount(remaining);
                from.setItem(slot, rest);
                break; // target is full
            }
        }
        return moved;
    }

    private static boolean isDepositable(ItemStack i) {
        if (nms.isSword(i) || nms.isTool(i) || nms.isBow(i) || nms.isArmor(i)) return false;
        String name = i.getType().name();
        switch (name) {
            case "SHEARS":
            case "COMPASS":
            case "CROSSBOW":
            case "TRIDENT":
            case "MACE":
                return false;
            default:
                break;
        }
        if (name.endsWith("_SPEAR")) return false;
        // permanent shop items and plugin items (default sword, lobby items, ...)
        if (ShopItemTags.hasUpgradeId(i)) return false;
        return !nms.isCustomBedWarsItem(i);
    }

    private static String displayName(ItemStack i) {
        switch (i.getType().name()) {
            case "IRON_INGOT":
                return "Iron";
            case "GOLD_INGOT":
                return "Gold";
            case "DIAMOND":
                return "Diamond";
            case "EMERALD":
                return "Emerald";
            default:
                break;
        }
        ItemMeta meta = i.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        StringBuilder sb = new StringBuilder();
        for (String w : i.getType().name().toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
