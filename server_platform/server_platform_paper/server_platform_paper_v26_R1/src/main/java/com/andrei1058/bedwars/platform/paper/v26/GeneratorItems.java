package com.andrei1058.bedwars.platform.paper.v26;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Generator drops carry a PersistentDataContainer tag on the item entity (not the stack, so a picked-up
 * ingot still stacks with any other ingot). The tag value is {@code <team name or empty>|<s|n>}, where
 * {@code n} means the generator does not stack its drops.
 */
final class GeneratorItems implements Listener {

    static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("bed_wars_1058:generator"));

    static void set(Item item, String value) {
        item.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, value);
    }

    static @Nullable String get(Item item) {
        return item.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent e) {
        String a = get(e.getEntity());
        String b = get(e.getTarget());
        if (a == null && b == null) return;
        // never mix generator drops with player drops (or another generator's), and keep
        // non-stacking generators' drops apart
        if (!Objects.equals(a, b) || a.endsWith("|n")) {
            e.setCancelled(true);
        }
    }
}
