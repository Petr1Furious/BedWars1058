package com.andrei1058.spigot.sidebar.v26;

import com.andrei1058.spigot.sidebar.*;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

@SuppressWarnings("unused")
public class ProviderImpl extends SidebarProvider {

    @Override
    public Sidebar createSidebar(SidebarLine title, Collection<SidebarLine> lines, Collection<PlaceholderProvider> placeholderProviders) {
        return new SidebarImpl(title, lines, placeholderProviders);
    }

    @Override
    public SidebarObjective createObjective(@NotNull WrappedSidebar sidebar, String name, boolean health, SidebarLine title, int type) {
        return ((SidebarImpl) sidebar).createObjective(name, health, title, type);
    }

    @Override
    public ScoreLine createScoreLine(WrappedSidebar sidebar, SidebarLine line, int score, String color) {
        return ((SidebarImpl) sidebar).createScore(line, score, color);
    }

    @Override
    public void sendScore(@NotNull WrappedSidebar sidebar, String playerName, int score) {
        if (sidebar.getHealthObjective() == null) return;
        Packets.send(sidebar.getReceivers(), new ClientboundSetScorePacket(
                playerName,
                sidebar.getHealthObjective().getName(),
                score,
                Optional.empty(),
                Optional.empty()
        ));
    }

    @Override
    public VersionedTabGroup createPlayerTab(WrappedSidebar sidebar, String identifier, SidebarLine prefix, SidebarLine suffix,
                                             PlayerTab.PushingRule pushingRule, PlayerTab.NameTagVisibility nameTagVisibility,
                                             @Nullable Collection<PlaceholderProvider> placeholders) {
        return new PlayerListImpl(sidebar, identifier, prefix, suffix, pushingRule, nameTagVisibility, placeholders);
    }

    @Override
    public void sendHeaderFooter(Player player, String header, String footer) {
        Packets.send(player, new ClientboundTabListPacket(Packets.text(header), Packets.text(footer)));
    }
}
