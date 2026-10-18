package com.andrei1058.bedwars.platform.paper.v26;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.mojang.datafixers.util.Pair;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hides the armour of invisible players.
 * <p>
 * Sending an empty equipment packet once is not enough: the server re-sends the real equipment whenever
 * a piece changes (durability loss on every hit, buying armour) and whenever the player comes back into
 * tracking range, which is why invisible players used to show their armour. This keeps a per-viewer
 * "hidden" set and rewrites every outgoing equipment packet for those viewers, so the armour stays hidden
 * until {@link #show} is called (potion ends, removed on damage, death, quit).
 * <p>
 * Teammates of the invisible player always see the armour, matching the plugin's drink handler.
 */
final class ArmorHider implements Listener {

    private static final String HANDLER_NAME = "bedwars1058_armor_hider";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Plugin plugin;
    // victim entity id -> viewers that must not see its armour
    private final Map<Integer, Set<UUID>> hidden = new ConcurrentHashMap<>();
    private BedWars api;

    ArmorHider(Plugin plugin) {
        this.plugin = plugin;
    }

    void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            inject(player);
        }
    }

    void shutdown() {
        hidden.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            uninject(player);
        }
    }

    void hide(@NotNull Player victim, @NotNull Player viewer) {
        if (victim.equals(viewer)) {
            return;
        }
        if (isTeammate(victim, viewer)) {
            // several call sites hide for everyone; teammates keep seeing the armour
            show(victim, viewer);
            return;
        }
        hidden.computeIfAbsent(victim.getEntityId(), id -> ConcurrentHashMap.newKeySet()).add(viewer.getUniqueId());
        Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> empty = new EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);
        org.bukkit.inventory.ItemStack air = new org.bukkit.inventory.ItemStack(Material.AIR);
        empty.put(org.bukkit.inventory.EquipmentSlot.HEAD, air);
        empty.put(org.bukkit.inventory.EquipmentSlot.CHEST, air);
        empty.put(org.bukkit.inventory.EquipmentSlot.LEGS, air);
        empty.put(org.bukkit.inventory.EquipmentSlot.FEET, air);
        viewer.sendEquipmentChange(victim, empty);
    }

    void show(@NotNull Player victim, @NotNull Player viewer) {
        Set<UUID> viewers = hidden.get(victim.getEntityId());
        if (viewers != null) {
            viewers.remove(viewer.getUniqueId());
            if (viewers.isEmpty()) {
                hidden.remove(victim.getEntityId(), viewers);
            }
        }
        if (victim.equals(viewer) || !viewer.canSee(victim)) {
            return;
        }
        PlayerInventory inventory = victim.getInventory();
        Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> armour = new EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);
        armour.put(org.bukkit.inventory.EquipmentSlot.HEAD, orAir(inventory.getHelmet()));
        armour.put(org.bukkit.inventory.EquipmentSlot.CHEST, orAir(inventory.getChestplate()));
        armour.put(org.bukkit.inventory.EquipmentSlot.LEGS, orAir(inventory.getLeggings()));
        armour.put(org.bukkit.inventory.EquipmentSlot.FEET, orAir(inventory.getBoots()));
        viewer.sendEquipmentChange(victim, armour);
    }

    void showAll(@NotNull Player victim) {
        Set<UUID> viewers = hidden.remove(victim.getEntityId());
        if (viewers == null) {
            return;
        }
        for (UUID uuid : viewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) {
                show(victim, viewer);
            }
        }
    }

    boolean isHidden(@NotNull Player victim, @NotNull Player viewer) {
        Set<UUID> viewers = hidden.get(victim.getEntityId());
        return viewers != null && viewers.contains(viewer.getUniqueId());
    }

    private static org.bukkit.inventory.ItemStack orAir(@Nullable org.bukkit.inventory.ItemStack item) {
        return item == null ? new org.bukkit.inventory.ItemStack(Material.AIR) : item;
    }

    private boolean isTeammate(Player victim, Player viewer) {
        if (api == null) {
            var registration = Bukkit.getServicesManager().getRegistration(BedWars.class);
            if (registration == null) {
                return false;
            }
            api = registration.getProvider();
        }
        IArena arena = api.getArenaUtil().getArenaByPlayer(victim);
        if (arena == null) {
            return false;
        }
        ITeam team = arena.getTeam(victim);
        return team != null && team.isMember(viewer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        inject(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hidden.remove(player.getEntityId());
        UUID uuid = player.getUniqueId();
        hidden.values().forEach(viewers -> viewers.remove(uuid));
    }

    /**
     * Safety net: whatever removes the potion (expiry, trap, death, milk), stop hiding the armour.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEnd(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getModifiedType() != PotionEffectType.INVISIBILITY) {
            return;
        }
        switch (event.getAction()) {
            case REMOVED, CLEARED -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    showAll(player);
                }
            });
            default -> {
            }
        }
    }

    private void inject(Player player) {
        Channel channel = channel(player);
        if (channel == null) {
            return;
        }
        UUID viewer = player.getUniqueId();
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                return;
            }
            Filter filter = new Filter(viewer);
            // after the unbundler (sees single packets), before the encoder (still packet objects)
            if (channel.pipeline().get("encoder") != null) {
                channel.pipeline().addAfter("encoder", HANDLER_NAME, filter);
            } else if (channel.pipeline().get("packet_handler") != null) {
                channel.pipeline().addBefore("packet_handler", HANDLER_NAME, filter);
            }
        });
    }

    private void uninject(Player player) {
        Channel channel = channel(player);
        if (channel == null) {
            return;
        }
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        });
    }

    private static @Nullable Channel channel(Player player) {
        try {
            var connection = ((CraftPlayer) player).getHandle().connection;
            return connection == null ? null : connection.connection.channel;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private @Nullable ClientboundSetEquipmentPacket rewrite(ClientboundSetEquipmentPacket packet, UUID viewer) {
        Set<UUID> viewers = hidden.get(packet.getEntity());
        if (viewers == null || !viewers.contains(viewer)) {
            return null;
        }
        boolean changed = false;
        List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>(packet.getSlots().size());
        for (Pair<EquipmentSlot, ItemStack> pair : packet.getSlots()) {
            if (isArmor(pair.getFirst()) && !pair.getSecond().isEmpty()) {
                slots.add(Pair.of(pair.getFirst(), ItemStack.EMPTY));
                changed = true;
            } else {
                slots.add(pair);
            }
        }
        return changed ? new ClientboundSetEquipmentPacket(packet.getEntity(), slots) : null;
    }

    private static boolean isArmor(EquipmentSlot slot) {
        for (EquipmentSlot armor : ARMOR) {
            if (armor == slot) {
                return true;
            }
        }
        return false;
    }

    private final class Filter extends ChannelDuplexHandler {
        private final UUID viewer;

        private Filter(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!hidden.isEmpty()) {
                if (msg instanceof ClientboundSetEquipmentPacket equipment) {
                    ClientboundSetEquipmentPacket rewritten = rewrite(equipment, viewer);
                    if (rewritten != null) {
                        msg = rewritten;
                    }
                } else if (msg instanceof ClientboundBundlePacket bundle) {
                    msg = rewriteBundle(bundle);
                }
            }
            super.write(ctx, msg, promise);
        }

        private Object rewriteBundle(ClientboundBundlePacket bundle) {
            boolean changed = false;
            List<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> packets = new ArrayList<>();
            for (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet : bundle.subPackets()) {
                if (packet instanceof ClientboundSetEquipmentPacket equipment) {
                    ClientboundSetEquipmentPacket rewritten = rewrite(equipment, viewer);
                    if (rewritten != null) {
                        packets.add(rewritten);
                        changed = true;
                        continue;
                    }
                }
                packets.add(packet);
            }
            return changed ? new ClientboundBundlePacket(packets) : bundle;
        }
    }
}
