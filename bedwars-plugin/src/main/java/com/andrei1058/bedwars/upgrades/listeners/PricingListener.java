package com.andrei1058.bedwars.upgrades.listeners;

import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent;
import com.andrei1058.bedwars.upgrades.UpgradesManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Picks the upgrade price set when a game starts (teams are assigned before the state becomes playing).
 */
public class PricingListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStateChange(GameStateChangeEvent e) {
        if (e.getNewState() == GameState.playing) {
            UpgradesManager.decidePricing(e.getArena());
        } else if (e.getNewState() != GameState.restarting) {
            UpgradesManager.clearPricing(e.getArena());
        }
    }
}
