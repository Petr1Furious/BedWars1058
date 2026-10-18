package com.andrei1058.spigot.sidebar.v26;

import com.andrei1058.spigot.sidebar.PlaceholderProvider;
import com.andrei1058.spigot.sidebar.PlayerTab;
import com.andrei1058.spigot.sidebar.SidebarLine;
import com.andrei1058.spigot.sidebar.VersionedTabGroup;
import com.andrei1058.spigot.sidebar.WrappedSidebar;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Tab-list / name-tag formatting group: a fake team sent to the receivers of one sidebar.
 * Members are kept in the team, so receivers added later get them with the create packet.
 */
public class PlayerListImpl implements VersionedTabGroup {

    private final WrappedSidebar sidebar;
    private final String identifier;
    private final SidebarLine prefix;
    private final SidebarLine suffix;
    private final Collection<PlaceholderProvider> placeholders;
    private final PlayerTeam team;
    private Player papiSubject;

    public PlayerListImpl(
            @NotNull WrappedSidebar sidebar,
            @NotNull String identifier,
            SidebarLine prefix,
            SidebarLine suffix,
            PlayerTab.PushingRule pushingRule,
            PlayerTab.NameTagVisibility nameTagVisibility,
            @Nullable Collection<PlaceholderProvider> placeholders
    ) {
        this.sidebar = sidebar;
        this.identifier = identifier;
        this.prefix = prefix;
        this.suffix = suffix;
        this.placeholders = placeholders;
        this.team = new PlayerTeam(Packets.SCOREBOARD, identifier);
        this.team.setPlayerPrefix(Packets.text(" "));
        this.team.setPlayerSuffix(Packets.text(" "));
        this.team.setCollisionRule(Team.CollisionRule.valueOf(pushingRule.name()));
        this.team.setNameTagVisibility(Team.Visibility.valueOf(nameTagVisibility.name()));
    }

    @Override
    public void sendCreateToPlayer(Player player) {
        Packets.send(player, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
    }

    @Override
    public void sendUserCreateToReceivers(@NotNull Player player) {
        add(player);
    }

    @Override
    public void sendUpdateToReceivers() {
        Packets.send(sidebar.getReceivers(), ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false));
    }

    @Override
    public void sendRemoveToReceivers() {
        Packets.send(sidebar.getReceivers(), ClientboundSetPlayerTeamPacket.createRemovePacket(team));
    }

    @Override
    public boolean refreshContent() {
        String newPrefix = prefix.getTrimReplacePlaceholders(papiSubject, 256, placeholders);
        String newSuffix = suffix.getTrimReplacePlaceholders(papiSubject, 256, placeholders);
        if (newPrefix.equals(team.getPlayerPrefix().getString()) && newSuffix.equals(team.getPlayerSuffix().getString())) {
            return false;
        }
        team.setPlayerPrefix(Packets.text(newPrefix));
        team.setPlayerSuffix(Packets.text(newSuffix));
        return true;
    }

    @Override
    public void add(@NotNull Player player) {
        team.getPlayers().add(player.getName());
        Packets.send(sidebar.getReceivers(), ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team, player.getName(), ClientboundSetPlayerTeamPacket.Action.ADD));
    }

    @Override
    public void remove(@NotNull Player player) {
        if (!team.getPlayers().remove(player.getName())) {
            return;
        }
        Packets.send(sidebar.getReceivers(), ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team, player.getName(), ClientboundSetPlayerTeamPacket.Action.REMOVE));
    }

    @Override
    public void setSubject(@Nullable Player player) {
        this.papiSubject = player;
    }

    @Override
    public @Nullable Player getSubject() {
        return papiSubject;
    }

    @Override
    public void setPushingRule(@NotNull PushingRule rule) {
        team.setCollisionRule(Team.CollisionRule.valueOf(rule.name()));
        sendUpdateToReceivers();
    }

    @Override
    public void setNameTagVisibility(@NotNull NameTagVisibility nameTagVisibility) {
        team.setNameTagVisibility(Team.Visibility.valueOf(nameTagVisibility.name()));
        sendUpdateToReceivers();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }
}
