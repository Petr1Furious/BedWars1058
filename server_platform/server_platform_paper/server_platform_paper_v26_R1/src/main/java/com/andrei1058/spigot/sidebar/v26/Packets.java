package com.andrei1058.spigot.sidebar.v26;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.scores.Scoreboard;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Packet helpers for the 26.x sidebar implementation.
 * <p>
 * Objectives and teams are plain NMS objects bound to a private scoreboard that is never sent anywhere:
 * they only exist to build packets for the players receiving a sidebar.
 */
final class Packets {

    static final Scoreboard SCOREBOARD = new Scoreboard();

    private Packets() {
    }

    static void send(@NotNull Player player, @NotNull Packet<?> packet) {
        if (!player.isOnline()) {
            return;
        }
        ((CraftPlayer) player).getHandle().connection.send(packet);
    }

    static void send(@NotNull Collection<Player> players, @NotNull Packet<?> packet) {
        // copy: receivers may change while a refresh is running
        for (Player player : players.toArray(new Player[0])) {
            send(player, packet);
        }
    }

    /**
     * Legacy (§) formatted text: the client still renders section signs in literal components.
     */
    static @NotNull Component text(@NotNull String legacy) {
        return Component.literal(legacy);
    }
}
