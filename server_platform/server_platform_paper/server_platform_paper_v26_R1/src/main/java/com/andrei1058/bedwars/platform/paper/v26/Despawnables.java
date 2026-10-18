package com.andrei1058.bedwars.platform.paper.v26;

import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.server.VersionSupport;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Silverfish;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Team iron golems and silverfish (port of versionsupport_nms_cmn1's despawnable providers to Mojang names).
 */
final class Despawnables {

    private Despawnables() {
    }

    static IronGolem spawnIronGolem(@NotNull Location location, @NotNull ITeam team, double speed, double health,
                                    double damage, int despawn, VersionSupport api) {
        IronGolem golem = (IronGolem) Objects.requireNonNull(location.getWorld()).spawnEntity(location, EntityType.IRON_GOLEM);
        applyDefaults(golem, speed, health, damage,
                displayName(Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME, despawn, team));
        PathfinderMob mob = (PathfinderMob) ((CraftEntity) golem).getHandle();
        clearGoals(mob);
        mob.getGoalSelector().addGoal(1, new FloatGoal(mob));
        mob.getGoalSelector().addGoal(2, new MeleeAttackGoal(mob, 1.5D, false));
        mob.getGoalSelector().addGoal(3, new RandomStrollGoal(mob, 1D));
        mob.getGoalSelector().addGoal(4, new RandomLookAroundGoal(mob));
        mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob));
        mob.targetSelector.addGoal(2, targetGoal(mob, team, api));
        return golem;
    }

    static Silverfish spawnSilverfish(@NotNull Location location, @NotNull ITeam team, double speed, double health,
                                      double damage, int despawn, VersionSupport api) {
        Silverfish silverfish = (Silverfish) Objects.requireNonNull(location.getWorld()).spawnEntity(location, EntityType.SILVERFISH);
        applyDefaults(silverfish, speed, health, damage,
                displayName(Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME, despawn, team));
        PathfinderMob mob = (PathfinderMob) ((CraftEntity) silverfish).getHandle();
        clearGoals(mob);
        mob.getGoalSelector().addGoal(1, new FloatGoal(mob));
        mob.getGoalSelector().addGoal(2, new MeleeAttackGoal(mob, 1.9D, false));
        mob.getGoalSelector().addGoal(3, new RandomStrollGoal(mob, 2D));
        mob.getGoalSelector().addGoal(4, new RandomLookAroundGoal(mob));
        mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob));
        mob.targetSelector.addGoal(2, targetGoal(mob, team, api));
        return silverfish;
    }

    private static void clearGoals(PathfinderMob mob) {
        mob.getGoalSelector().removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
    }

    private static NearestAttackableTargetGoal<LivingEntity> targetGoal(PathfinderMob mob, ITeam team, VersionSupport api) {
        return new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 20, true, false,
                (target, level) -> {
                    if (target instanceof Player human) {
                        var player = human.getBukkitEntity();
                        return !player.isDead()
                                && !team.wasMember(player.getUniqueId())
                                && !team.getArena().isReSpawning(player.getUniqueId())
                                && !team.getArena().isSpectator(player.getUniqueId());
                    }
                    var despawnable = api.getDespawnablesList().get(target.getBukkitEntity().getUniqueId());
                    return despawnable == null || despawnable.getTeam() != team;
                });
    }

    private static String displayName(String message, int despawn, ITeam team) {
        Language lang = Language.getDefaultLanguage();
        return lang.m(message)
                .replace("{despawn}", String.valueOf(despawn))
                .replace("{health}", (lang.m(Messages.FORMATTING_DESPAWNABLE_UTILITY_NPC_HEALTH) + " ").repeat(10))
                .replace("{TeamColor}", team.getColor().chat().toString());
    }

    @SuppressWarnings("deprecation")
    private static void applyDefaults(org.bukkit.entity.LivingEntity entity, double speed, double health, double damage, String name) {
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.setCustomNameVisible(true);
        entity.setCustomName(name);
        setBase(entity.getAttribute(Attribute.MAX_HEALTH), health);
        setBase(entity.getAttribute(Attribute.MOVEMENT_SPEED), speed);
        setBase(entity.getAttribute(Attribute.ATTACK_DAMAGE), damage);
        entity.setHealth(Math.min(health, entity.getAttribute(Attribute.MAX_HEALTH) == null ? health
                : entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
    }

    private static void setBase(AttributeInstance attribute, double value) {
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }
}
