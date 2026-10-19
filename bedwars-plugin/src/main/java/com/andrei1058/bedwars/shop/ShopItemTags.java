package com.andrei1058.bedwars.shop;

import com.andrei1058.bedwars.BedWars;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Null-safe access to the shop-upgrade identifier stored on an item.
 * <p>
 * Older version modules returned the string {@code "null"} for untagged items, the 26.x module
 * returns {@code null}. Callers must treat both (and blank) as "no identifier".
 */
public final class ShopItemTags {

    private ShopItemTags() {
    }

    /**
     * @return the shop upgrade identifier, or null when the item carries none.
     */
    public static @Nullable String upgradeId(@Nullable ItemStack item) {
        if (item == null) return null;
        String id;
        try {
            id = BedWars.nms.getShopUpgradeIdentifier(item);
        } catch (RuntimeException ex) {
            return null;
        }
        if (id == null) return null;
        id = id.trim();
        if (id.isEmpty() || id.equals("null")) return null;
        return id;
    }

    public static boolean hasUpgradeId(@Nullable ItemStack item) {
        return upgradeId(item) != null;
    }
}
