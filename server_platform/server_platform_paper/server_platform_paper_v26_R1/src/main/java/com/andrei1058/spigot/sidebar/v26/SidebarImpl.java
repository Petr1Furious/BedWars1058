package com.andrei1058.spigot.sidebar.v26;

import com.andrei1058.spigot.sidebar.*;
import net.md_5.bungee.api.ChatColor;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

/**
 * Packet based sidebar for Minecraft 26.x.
 * <p>
 * Each line is a fake team whose only entry is a unique colour code; the line text is the team prefix and
 * suffix. Since 1.20.3 the score number can be replaced by text ({@link FixedFormat}), which BedWars uses
 * for the right-hand team status column. Unlike the 1.21 implementation, that score text is re-sent
 * whenever it changes, otherwise bed/elimination/player-count statuses stay frozen at their first value.
 */
public class SidebarImpl extends WrappedSidebar {

    public SidebarImpl(@NotNull SidebarLine title, @NotNull Collection<SidebarLine> lines, Collection<PlaceholderProvider> placeholderProvider) {
        super(title, lines, placeholderProvider);
    }

    public ScoreLine createScore(SidebarLine line, int score, String color) {
        return new ScoreLineImpl(line, score, color);
    }

    public SidebarObjective createObjective(String name, boolean health, SidebarLine title, int type) {
        return new SidebarObjectiveImpl(name, health, title, type);
    }

    private Player subject() {
        return getReceivers().isEmpty() ? null : getReceivers().getFirst();
    }

    protected class SidebarObjectiveImpl implements SidebarObjective {

        private SidebarLine displayName;
        private final Objective handle;
        private final DisplaySlot slot;

        public SidebarObjectiveImpl(String name, boolean health, SidebarLine displayName, int type) {
            this.displayName = displayName;
            ObjectiveCriteria criteria = health ? ObjectiveCriteria.HEALTH : ObjectiveCriteria.DUMMY;
            this.handle = new Objective(Packets.SCOREBOARD, name, criteria, Packets.text(""),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
            this.slot = switch (type) {
                case 0 -> DisplaySlot.LIST;
                case 2 -> DisplaySlot.BELOW_NAME;
                default -> DisplaySlot.SIDEBAR;
            };
        }

        @Override
        public void setTitle(SidebarLine title) {
            this.displayName = title;
            refreshTitle();
        }

        @Override
        public SidebarLine getTitle() {
            return displayName;
        }

        @Override
        public void sendCreate(Player player) {
            Packets.send(player, new ClientboundSetObjectivePacket(handle, ClientboundSetObjectivePacket.METHOD_ADD));
            Packets.send(player, new ClientboundSetDisplayObjectivePacket(slot, handle));
            if (handle.getName().equalsIgnoreCase("health")) {
                Packets.send(player, new ClientboundSetDisplayObjectivePacket(DisplaySlot.LIST, handle));
            }
        }

        @Override
        public void sendUpdate() {
            Packets.send(getReceivers(), new ClientboundSetObjectivePacket(handle, ClientboundSetObjectivePacket.METHOD_CHANGE));
        }

        @Override
        public void sendRemove(Player player) {
            Packets.send(player, new ClientboundSetObjectivePacket(handle, ClientboundSetObjectivePacket.METHOD_REMOVE));
        }

        @Override
        public String getName() {
            return handle.getName();
        }

        @Override
        public boolean refreshTitle() {
            String newTitle = displayName.getTrimReplacePlaceholders(subject(), 256, getPlaceholders());
            if (newTitle.equals(handle.getDisplayName().getString())) {
                return false;
            }
            handle.setDisplayName(Packets.text(newTitle));
            return true;
        }
    }

    public class ScoreLineImpl implements ScoreLine {

        private int score;
        private SidebarLine text;
        private final String color;
        private final PlayerTeam team;
        private String prefix = " ";
        private String suffix = "";
        // right-hand text shown instead of the score number
        private String scoreText = "";
        private boolean scoreTextDirty = false;

        public ScoreLineImpl(@NotNull SidebarLine text, int score, @NotNull String color) {
            this.score = score;
            this.text = text;
            this.color = color.charAt(0) == ChatColor.COLOR_CHAR ? color : ChatColor.COLOR_CHAR + color;
            this.team = new PlayerTeam(Packets.SCOREBOARD, this.color);
            this.team.getPlayers().add(this.color);
            applyTeam();
        }

        private void applyTeam() {
            team.setPlayerPrefix(Packets.text(prefix));
            team.setPlayerSuffix(Packets.text(suffix));
        }

        private ClientboundSetScorePacket scorePacket() {
            return new ClientboundSetScorePacket(
                    getColor(),
                    getSidebarObjective().getName(),
                    score,
                    Optional.empty(),
                    Optional.of(new FixedFormat(Packets.text(scoreText)))
            );
        }

        @Override
        public SidebarLine getLine() {
            return text;
        }

        @Override
        public void setLine(SidebarLine line) {
            this.text = line;
        }

        @Override
        public int getScoreAmount() {
            return score;
        }

        @Override
        public void setScoreAmount(int score) {
            this.score = score;
            scoreTextDirty = false;
            Packets.send(getReceivers(), scorePacket());
        }

        @Override
        public void sendCreateToAllReceivers() {
            Packets.send(getReceivers(), ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            scoreTextDirty = false;
            Packets.send(getReceivers(), scorePacket());
        }

        @Override
        public void sendCreate(Player player) {
            Packets.send(player, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            Packets.send(player, scorePacket());
        }

        @Override
        public void sendRemove(Player player) {
            Packets.send(player, new ClientboundResetScorePacket(getColor(), getSidebarObjective().getName()));
            Packets.send(player, ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        }

        @Override
        public void sendRemoveToAllReceivers() {
            Packets.send(getReceivers(), new ClientboundResetScorePacket(getColor(), getSidebarObjective().getName()));
            Packets.send(getReceivers(), ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        }

        @Override
        public void sendUpdate(Player player) {
            Packets.send(player, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false));
            Packets.send(player, scorePacket());
        }

        @Override
        public void sendUpdateToAllReceivers() {
            Packets.send(getReceivers(), ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false));
            if (scoreTextDirty) {
                scoreTextDirty = false;
                Packets.send(getReceivers(), scorePacket());
            }
        }

        @Override
        public boolean setContent(@NotNull SidebarLine line) {
            String oldPrefix = prefix;
            String oldSuffix = suffix;
            String oldScoreText = scoreText;

            String content = line.getTrimReplacePlaceholders(subject(), null, getPlaceholders());
            if (content.length() > 256) {
                int cut = content.charAt(255) == ChatColor.COLOR_CHAR ? 255 : 256;
                prefix = content.substring(0, cut);
                String rest = org.bukkit.ChatColor.getLastColors(prefix) + content.substring(cut);
                suffix = rest.length() > 256 ? rest.substring(0, 256) : rest;
            } else {
                prefix = content;
                suffix = "";
            }

            String newScoreText = line.getTrimReplacePlaceholdersScore(subject(), null, getPlaceholders());
            scoreText = newScoreText == null ? "" : newScoreText;

            applyTeam();
            if (!oldScoreText.equals(scoreText)) {
                scoreTextDirty = true;
            }
            return !oldPrefix.equals(prefix) || !oldSuffix.equals(suffix) || scoreTextDirty;
        }

        @Override
        public String getColor() {
            return color;
        }

        @Override
        public boolean refreshContent() {
            return setContent(getLine());
        }

        @Override
        public int compareTo(@NotNull ScoreLine o) {
            return Integer.compare(score, o.getScoreAmount());
        }
    }
}
