package com.andrei1058.bedwars.platform.paper.v26;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.shop.ShopHolo;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.bedwars.api.entity.Despawnable;
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.bedwars.support.version.common.VersionCommon;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/**
 * Version support for Minecraft 26.x on Paper / AdvancedSlimePaper.
 * <p>
 * Mostly Paper API; the few NMS calls use Mojang names, since 26.x servers are no longer obfuscated
 * (and Paper no longer remaps Spigot-mapped plugins, so the 1.21 modules cannot run there).
 */
public class V26VersionSupport extends VersionSupport {

    private static final Set<Material> PROJECTILES = EnumSet.of(
            Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW, Material.SNOWBALL, Material.EGG,
            Material.ENDER_PEARL, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.EXPERIENCE_BOTTLE,
            Material.TRIDENT, Material.FIRE_CHARGE, Material.WIND_CHARGE
    );

    private final ArmorHider armorHider;

    public V26VersionSupport(Plugin plugin, String name) {
        super(plugin, name);
        loadDefaultEffects();
        this.armorHider = new ArmorHider(plugin);
    }

    public void shutdown() {
        armorHider.shutdown();
    }

    @Override
    public void registerVersionListeners() {
        new VersionCommon(this);
        armorHider.register();
        Bukkit.getPluginManager().registerEvents(new PearlMomentumListener(), getPlugin());
        Bukkit.getPluginManager().registerEvents(new GeneratorItems(), getPlugin());
    }

    @Override
    public boolean setGameRule(@NotNull World world, @NotNull String rule, @NotNull String value) {
        return GameRules26.apply(world, rule, value, getPlugin().getLogger());
    }

    @Override
    public void setGeneratorItemTag(@NotNull Item item, @NotNull String value) {
        GeneratorItems.set(item, value);
    }

    @Override
    public @Nullable String getGeneratorItemTag(@NotNull Item item) {
        return GeneratorItems.get(item);
    }

    private static final Particle.DustOptions FOOTPRINT = new Particle.DustOptions(Color.fromRGB(0x2B, 0x2B, 0x2B), 0.7f);

    @Override
    public void showFootprint(@NotNull Player viewer, @NotNull Location location) {
        viewer.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0, FOOTPRINT);
    }

    @Override
    public void registerCommand(String name, Command cmd) {
        Bukkit.getCommandMap().register(name, cmd);
    }

    @Override
    public boolean isBukkitCommandRegistered(String name) {
        return Bukkit.getCommandMap().getCommand(name) != null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void sendTitle(@NotNull Player p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        p.sendTitle(title == null ? " " : title, subtitle == null ? " " : subtitle, fadeIn, stay, fadeOut);
    }

    @Override
    public void playAction(@NotNull Player p, String text) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', text)));
    }

    @Override
    public ItemStack getItemInHand(@NotNull Player p) {
        return p.getInventory().getItemInMainHand();
    }

    @Override
    public void hideEntity(@NotNull Entity e, Player p) {
        p.hideEntity(getPlugin(), e);
    }

    @Override
    public boolean isArmor(ItemStack itemStack) {
        if (itemStack == null) return false;
        Material type = itemStack.getType();
        return Tag.ITEMS_HEAD_ARMOR.isTagged(type) || Tag.ITEMS_CHEST_ARMOR.isTagged(type)
                || Tag.ITEMS_LEG_ARMOR.isTagged(type) || Tag.ITEMS_FOOT_ARMOR.isTagged(type)
                || type == Material.ELYTRA;
    }

    @Override
    public boolean isTool(ItemStack itemStack) {
        if (itemStack == null) return false;
        Material type = itemStack.getType();
        return Tag.ITEMS_PICKAXES.isTagged(type) || Tag.ITEMS_AXES.isTagged(type)
                || Tag.ITEMS_SHOVELS.isTagged(type) || Tag.ITEMS_HOES.isTagged(type);
    }

    @Override
    public boolean isSword(ItemStack itemStack) {
        return itemStack != null && Tag.ITEMS_SWORDS.isTagged(itemStack.getType());
    }

    @Override
    public boolean isAxe(ItemStack itemStack) {
        return itemStack != null && Tag.ITEMS_AXES.isTagged(itemStack.getType());
    }

    @Override
    public boolean isBow(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() == Material.BOW;
    }

    @Override
    public boolean isProjectile(ItemStack itemStack) {
        return itemStack != null && PROJECTILES.contains(itemStack.getType());
    }

    /**
     * Shop potions may carry the effect as a custom effect or as the base potion type.
     */
    @Override
    public boolean isInvisibilityPotion(@NotNull ItemStack itemStack) {
        if (itemStack.getType() != Material.POTION) return false;
        if (!(itemStack.getItemMeta() instanceof PotionMeta pm)) return false;
        if (pm.hasCustomEffect(PotionEffectType.INVISIBILITY)) return true;
        PotionType base = pm.getBasePotionType();
        return base != null && base.getPotionEffects().stream().anyMatch(effect -> effect.getType() == PotionEffectType.INVISIBILITY);
    }

    @Override
    public void registerEntities() {
    }

    @Override
    public void spawnShop(@NotNull Location loc, String name1, List<Player> players, IArena arena) {
        Location l = loc.clone();
        if (l.getWorld() == null) return;
        Villager vlg = (Villager) l.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        vlg.setAI(false);
        vlg.setRemoveWhenFarAway(false);
        vlg.setCollidable(false);
        vlg.setInvulnerable(true);
        vlg.setSilent(true);

        for (Player p : players) {
            String[] name = Language.getMsg(p, name1).split(",");
            if (name.length == 1) {
                ArmorStand a = createArmorStand(name[0], l.clone().add(0, 1.85, 0));
                new ShopHolo(Language.getPlayerLanguage(p).getIso(), a, null, l, arena);
            } else {
                ArmorStand a = createArmorStand(name[0], l.clone().add(0, 2.1, 0));
                ArmorStand b = createArmorStand(name[1], l.clone().add(0, 1.85, 0));
                new ShopHolo(Language.getPlayerLanguage(p).getIso(), a, b, l, arena);
            }
        }
        for (ShopHolo sh : ShopHolo.getShopHolo()) {
            if (sh.getA() == arena) {
                sh.update();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static ArmorStand createArmorStand(String name, Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        ArmorStand a = loc.getWorld().spawn(loc, ArmorStand.class);
        a.setGravity(false);
        a.setVisible(false);
        a.setCustomNameVisible(true);
        a.setCustomName(name);
        return a;
    }

    /**
     * Base attack damage of the item type (used to drop the default sword when a better one is bought).
     */
    @Override
    @SuppressWarnings("deprecation")
    public double getDamage(ItemStack i) {
        if (i == null) return -1;
        return i.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND).get(org.bukkit.attribute.Attribute.ATTACK_DAMAGE)
                .stream().mapToDouble(org.bukkit.attribute.AttributeModifier::getAmount).sum();
    }

    @Override
    public void spawnSilverfish(Location loc, ITeam bedWarsTeam, double speed, double health, int despawn, double damage) {
        var entity = Despawnables.spawnSilverfish(loc, bedWarsTeam, speed, health, damage, despawn, this);
        new Despawnable(entity, bedWarsTeam, despawn, Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME,
                PlayerKillEvent.PlayerKillCause.SILVERFISH_FINAL_KILL, PlayerKillEvent.PlayerKillCause.SILVERFISH);
    }

    @Override
    public void spawnIronGolem(Location loc, ITeam bedWarsTeam, double speed, double health, int despawn) {
        var entity = Despawnables.spawnIronGolem(loc, bedWarsTeam, speed, health, 4, despawn, this);
        new Despawnable(entity, bedWarsTeam, despawn, Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME,
                PlayerKillEvent.PlayerKillCause.IRON_GOLEM_FINAL_KILL, PlayerKillEvent.PlayerKillCause.IRON_GOLEM);
    }

    @Override
    public void minusAmount(Player p, @NotNull ItemStack i, int amount) {
        if (i.getAmount() - amount <= 0) {
            if (p.getInventory().getItemInOffHand().equals(i)) {
                p.getInventory().setItemInOffHand(null);
            } else {
                p.getInventory().removeItem(i);
            }
            return;
        }
        i.setAmount(i.getAmount() - amount);
        p.updateInventory();
    }

    @Override
    public void setSource(TNTPrimed tnt, Player owner) {
        tnt.setSource(owner);
    }

    @Override
    public void voidKill(Player p) {
        p.damage(1000, DamageSource.builder(DamageType.OUT_OF_WORLD).build());
    }

    @Override
    public void hideArmor(@NotNull Player victim, Player receiver) {
        if (receiver == null) return;
        armorHider.hide(victim, receiver);
    }

    @Override
    public void showArmor(@NotNull Player victim, Player receiver) {
        if (receiver == null) return;
        armorHider.show(victim, receiver);
    }

    @Override
    public void spawnDragon(Location l, ITeam bwt) {
        if (l == null || l.getWorld() == null) {
            getPlugin().getLogger().log(Level.WARNING, "Could not spawn Dragon. Location is null");
            return;
        }
        EnderDragon ed = (EnderDragon) l.getWorld().spawnEntity(l, EntityType.ENDER_DRAGON);
        ed.setPhase(EnderDragon.Phase.CIRCLING);
    }

    @Override
    public void colorBed(ITeam bwt) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockState bed = bwt.getBed().clone().add(x, 0, z).getBlock().getState();
                if (bed.getBlockData() instanceof Bed) {
                    bed.setType(bwt.getColor().bedMaterial());
                    bed.update();
                }
            }
        }
    }

    @Override
    public void registerTntWhitelist(float endStoneBlast, float glassBlast) {
        try {
            Field field = BlockBehaviour.class.getDeclaredField("explosionResistance");
            field.setAccessible(true);
            field.setFloat(CraftMagicNumbers.getBlock(Material.END_STONE), endStoneBlast);
            for (Material material : Material.values()) {
                if (material.isLegacy() || !material.isBlock()) continue;
                String name = material.name();
                if (name.equals("GLASS") || name.equals("TINTED_GLASS") || name.endsWith("_STAINED_GLASS")) {
                    field.setFloat(CraftMagicNumbers.getBlock(material), glassBlast);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            getPlugin().getLogger().log(Level.WARNING, "Could not change blast resistance of end stone and glass", e);
        }
    }

    @Override
    public void setBlockTeamColor(@NotNull Block block, TeamColor teamColor) {
        if (block.getType().toString().contains("STAINED_GLASS") || block.getType().toString().equals("GLASS")) {
            block.setType(teamColor.glassMaterial());
        } else if (block.getType().toString().contains("_TERRACOTTA")) {
            block.setType(teamColor.glazedTerracottaMaterial());
        } else if (block.getType().toString().contains("_WOOL")) {
            block.setType(teamColor.woolMaterial());
        }
    }

    @Override
    public void setCollide(@NotNull Player p, IArena a, boolean value) {
        p.setCollidable(value);
        if (a == null) return;
        a.updateSpectatorCollideRule(p, value);
    }

    @Override
    public ItemStack addCustomData(ItemStack i, String data) {
        return setTag(i, VersionSupport.PLUGIN_TAG_GENERIC_KEY, data);
    }

    public ItemStack addCustomData(ItemStack i, String key, String data) {
        return setTag(i, key, data);
    }

    @Override
    public ItemStack setTag(ItemStack itemStack, String key, String value) {
        var meta = itemStack.getItemMeta();
        if (null == meta) {
            return itemStack;
        }
        var tag = meta.getPersistentDataContainer();
        try {
            var namespaced = key(key);
            if (namespaced == null) {
                return itemStack;
            }
            tag.set(namespaced, PersistentDataType.STRING, value);
            itemStack.setItemMeta(meta);
        } catch (IllegalArgumentException e) {
            getPlugin().getLogger().severe("Cannot append item custom tag with key -> " + key);
            getPlugin().getLogger().severe("Reason:" + e.getMessage());
        }
        return itemStack;
    }

    /**
     * Keys such as the legacy "Potion" tag are not valid namespaced keys; store them lower-cased.
     */
    private static @Nullable NamespacedKey key(String key) {
        var namespaced = NamespacedKey.fromString(key);
        if (namespaced == null) {
            namespaced = NamespacedKey.fromString(key.toLowerCase(java.util.Locale.ROOT));
        }
        return namespaced;
    }

    @Override
    public String getTag(ItemStack itemStack, String key) {
        var tag = getDataContainer(itemStack);
        if (null == tag) {
            return null;
        }
        var namespaced = key(key);
        return namespaced == null ? null : tag.get(namespaced, PersistentDataType.STRING);
    }

    @Override
    public boolean isCustomBedWarsItem(ItemStack i) {
        var tag = getDataContainer(i);
        if (null == tag) {
            return false;
        }
        return tag.has(Objects.requireNonNull(NamespacedKey.fromString(VersionSupport.PLUGIN_TAG_GENERIC_KEY)));
    }

    @Override
    public String getCustomData(ItemStack i) {
        var tag = getDataContainer(i);
        if (null == tag) {
            return null;
        }
        return tag.get(Objects.requireNonNull(NamespacedKey.fromString(VersionSupport.PLUGIN_TAG_GENERIC_KEY)), PersistentDataType.STRING);
    }

    @Override
    public ItemStack colourItem(ItemStack itemStack, ITeam bedWarsTeam) {
        if (itemStack == null) return null;
        String type = itemStack.getType().toString();
        if (isBed(itemStack.getType())) {
            return new ItemStack(bedWarsTeam.getColor().bedMaterial(), itemStack.getAmount());
        } else if (type.contains("_STAINED_GLASS_PANE")) {
            return new ItemStack(bedWarsTeam.getColor().glassPaneMaterial(), itemStack.getAmount());
        } else if (type.contains("STAINED_GLASS") || type.equals("GLASS")) {
            return new ItemStack(bedWarsTeam.getColor().glassMaterial(), itemStack.getAmount());
        } else if (type.contains("_TERRACOTTA")) {
            return new ItemStack(bedWarsTeam.getColor().glazedTerracottaMaterial(), itemStack.getAmount());
        } else if (type.contains("_WOOL")) {
            return new ItemStack(bedWarsTeam.getColor().woolMaterial(), itemStack.getAmount());
        }
        return itemStack;
    }

    @Override
    public ItemStack createItemStack(String material, int amount, short data) {
        try {
            return new ItemStack(Material.valueOf(material), amount);
        } catch (Exception ex) {
            getPlugin().getLogger().log(Level.WARNING, material + " is not a valid " + getName() + " material!");
            return new ItemStack(Material.BEDROCK);
        }
    }

    @Override
    public Material materialFireball() {
        return Material.FIRE_CHARGE;
    }

    @Override
    public Material materialPlayerHead() {
        return Material.PLAYER_HEAD;
    }

    @Override
    public Material materialSnowball() {
        return Material.SNOWBALL;
    }

    @Override
    public Material materialGoldenHelmet() {
        return Material.GOLDEN_HELMET;
    }

    @Override
    public Material materialGoldenChestPlate() {
        return Material.GOLDEN_CHESTPLATE;
    }

    @Override
    public Material materialGoldenLeggings() {
        return Material.GOLDEN_LEGGINGS;
    }

    @Override
    public Material materialNetheriteHelmet() {
        return Material.NETHERITE_HELMET;
    }

    @Override
    public Material materialNetheriteChestPlate() {
        return Material.NETHERITE_CHESTPLATE;
    }

    @Override
    public Material materialNetheriteLeggings() {
        return Material.NETHERITE_LEGGINGS;
    }

    @Override
    public Material materialElytra() {
        return Material.ELYTRA;
    }

    @Override
    public Material materialCake() {
        return Material.CAKE;
    }

    @Override
    public Material materialCraftingTable() {
        return Material.CRAFTING_TABLE;
    }

    @Override
    public Material materialEnchantingTable() {
        return Material.ENCHANTING_TABLE;
    }

    @Override
    public Material woolMaterial() {
        return Material.WHITE_WOOL;
    }

    @Override
    public @Nullable String getShopUpgradeIdentifier(ItemStack itemStack) {
        return getTag(itemStack, VersionSupport.PLUGIN_TAG_TIER_KEY);
    }

    @Override
    public ItemStack setShopUpgradeIdentifier(ItemStack itemStack, String identifier) {
        return addCustomData(itemStack, VersionSupport.PLUGIN_TAG_TIER_KEY, identifier);
    }

    @Override
    public ItemStack getPlayerHead(Player player, @Nullable ItemStack copyTagFrom) {
        ItemStack head = copyTagFrom == null ? new ItemStack(Material.PLAYER_HEAD) : copyTagFrom.withType(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    public void sendPlayerSpawnPackets(Player respawned, IArena arena) {
        if (respawned == null || arena == null) return;
        if (!arena.isPlayer(respawned)) return;
        // still on the re-spawn screen
        if (arena.getRespawnSessions().containsKey(respawned)) return;

        ServerPlayer handle = handle(respawned);
        var show = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, handle);
        var velocity = new ClientboundSetEntityMotionPacket(handle);
        var head = new ClientboundRotateHeadPacket(handle, getCompressedAngle(respawned.getYaw()));
        Map<EquipmentSlot, ItemStack> equipment = equipment(respawned);
        boolean respawnedInvisible = respawned.hasPotionEffect(PotionEffectType.INVISIBILITY);

        for (Player p : arena.getPlayers()) {
            if (p == null || p.equals(respawned)) continue;
            if (arena.getRespawnSessions().containsKey(p)) continue;
            if (!p.getWorld().equals(respawned.getWorld())) continue;
            if (respawned.getLocation().distance(p.getLocation()) > arena.getRenderDistance()) continue;

            // respawned player to regular players
            send(p, show);
            send(p, head);
            send(p, velocity);
            p.sendEquipmentChange(respawned, equipment);
            if (respawnedInvisible) {
                hideArmor(respawned, p);
            }

            // nearby players to the respawned player
            if (p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                hideArmor(p, respawned);
            } else {
                ServerPlayer other = handle(p);
                send(respawned, new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, other));
                send(respawned, new ClientboundSetEntityMotionPacket(other));
                send(respawned, new ClientboundRotateHeadPacket(other, getCompressedAngle(p.getYaw())));
                showArmor(p, respawned);
            }
        }

        for (Player spectator : arena.getSpectators()) {
            if (spectator == null || spectator.equals(respawned)) continue;
            respawned.hidePlayer(getPlugin(), spectator);
            if (!spectator.getWorld().equals(respawned.getWorld())) continue;
            if (respawned.getLocation().distance(spectator.getLocation()) > arena.getRenderDistance()) continue;
            send(spectator, show);
            send(spectator, velocity);
            send(spectator, head);
            spectator.sendEquipmentChange(respawned, equipment);
            if (respawnedInvisible) {
                hideArmor(respawned, spectator);
            }
        }
    }

    private static Map<EquipmentSlot, ItemStack> equipment(Player player) {
        var inventory = player.getInventory();
        Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
        map.put(EquipmentSlot.HAND, orAir(inventory.getItemInMainHand()));
        map.put(EquipmentSlot.OFF_HAND, orAir(inventory.getItemInOffHand()));
        map.put(EquipmentSlot.HEAD, orAir(inventory.getHelmet()));
        map.put(EquipmentSlot.CHEST, orAir(inventory.getChestplate()));
        map.put(EquipmentSlot.LEGS, orAir(inventory.getLeggings()));
        map.put(EquipmentSlot.FEET, orAir(inventory.getBoots()));
        return map;
    }

    private static ItemStack orAir(@Nullable ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private static ServerPlayer handle(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    private static void send(Player player, Packet<?> packet) {
        handle(player).connection.send(packet);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getInventoryName(@NotNull InventoryEvent e) {
        return e.getView().getTitle();
    }

    @Override
    public void setUnbreakable(@NotNull ItemMeta itemMeta) {
        itemMeta.setUnbreakable(true);
    }

    @Override
    public String getMainLevel() {
        return ((DedicatedServer) ((CraftServer) Bukkit.getServer()).getServer()).getProperties().levelName;
    }

    /**
     * 25 was used by 1.21-1.21.4 and 26 by 1.21.11; checks in the plugin only compare against older values.
     */
    @Override
    public int getVersion() {
        return 27;
    }

    @Override
    public void setJoinSignBackground(@NotNull BlockState b, Material material) {
        if (b.getBlockData() instanceof WallSign) {
            b.getBlock().getRelative(((WallSign) b.getBlockData()).getFacing().getOppositeFace()).setType(material);
        }
    }

    @Override
    public void spigotShowPlayer(Player victim, @NotNull Player receiver) {
        receiver.showPlayer(getPlugin(), victim);
    }

    @Override
    public void spigotHidePlayer(Player victim, @NotNull Player receiver) {
        receiver.hidePlayer(getPlugin(), victim);
    }

    @Override
    public Fireball setFireballDirection(Fireball fireball, @NotNull Vector vector) {
        // same as the 1.21 NMS call assignDirectionalMovement(direction, 0.1)
        fireball.setAcceleration(vector.clone().normalize().multiply(0.1));
        return fireball;
    }

    @Override
    public void playRedStoneDot(@NotNull Player player) {
        Location location = player.getLocation().add(0, 2.6, 0);
        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1);
        for (Player inWorld : player.getWorld().getPlayers()) {
            if (inWorld.equals(player)) continue;
            inWorld.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0, dust, true);
        }
    }

    @Override
    public void clearArrowsFromPlayerBody(Player player) {
        // minecraft clears them on death on newer versions
    }

    @Nullable
    public PersistentDataContainer getDataContainer(@NotNull ItemStack itemStack) {
        if (null == itemStack.getItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer();
    }

    @Override
    public void placeTowerBlocks(@NotNull Block b, @NotNull IArena a, @NotNull TeamColor color, int x, int y, int z) {
        b.getRelative(x, y, z).setType(color.woolMaterial());
        a.addPlacedBlock(b.getRelative(x, y, z));
    }

    @Override
    public void placeLadder(@NotNull Block b, int x, int y, int z, @NotNull IArena a, int ladderData) {
        Block block = b.getRelative(x, y, z);
        block.setType(Material.LADDER);
        Ladder ladder = (Ladder) block.getBlockData();
        a.addPlacedBlock(block);
        BlockFace face = switch (ladderData) {
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.SOUTH;
            case 4 -> BlockFace.WEST;
            case 5 -> BlockFace.EAST;
            default -> null;
        };
        if (face != null) {
            ladder.setFacing(face);
            block.setBlockData(ladder);
        }
    }

    @Override
    public void playVillagerEffect(@NotNull Player player, Location location) {
        player.spawnParticle(Particle.HAPPY_VILLAGER, location, 1);
    }

    @Override
    public boolean hasInvisibility(@NotNull PotionEffect pe) {
        return pe.getType() == PotionEffectType.INVISIBILITY;
    }
}
