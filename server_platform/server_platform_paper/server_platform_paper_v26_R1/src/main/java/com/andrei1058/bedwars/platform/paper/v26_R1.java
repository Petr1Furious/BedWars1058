package com.andrei1058.bedwars.platform.paper;

import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.bedwars.platform.paper.v26.V26VersionSupport;
import com.andrei1058.spigot.sidebar.PAPIAdapter;
import com.andrei1058.spigot.sidebar.PAPISupport;
import com.andrei1058.spigot.sidebar.SidebarManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper / AdvancedSlimePaper on Minecraft 26.x.
 */
@SuppressWarnings("unused")
public class v26_R1 extends PaperPlatform {

    private V26VersionSupport versionSupport;

    @SuppressWarnings("unused")
    public v26_R1() {
        var hasPAPI = Bukkit.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        SidebarManager.setInstance(new SidebarManager(
                new com.andrei1058.spigot.sidebar.v26.ProviderImpl(),
                hasPAPI ? new PAPIAdapter() : new PAPISupport() {
                    @Override
                    public String replacePlaceholders(Player player, String s) {
                        return s;
                    }

                    @Override
                    public boolean hasPlaceholders(String s) {
                        return false;
                    }
                }
        ));
    }

    @Override
    public VersionSupport getOldWrapper(JavaPlugin plugin) {
        if (versionSupport == null) {
            versionSupport = new V26VersionSupport(plugin, "v26_R1");
        }
        return versionSupport;
    }

    @Override
    public void onDisable(JavaPlugin plugin) {
        if (versionSupport != null) {
            versionSupport.shutdown();
        }
    }

    @Override
    public String getVersion() {
        return Bukkit.getMinecraftVersion();
    }
}
