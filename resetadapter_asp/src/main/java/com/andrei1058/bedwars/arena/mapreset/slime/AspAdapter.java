/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2023 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.arena.mapreset.slime;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.server.ISetupSession;
import com.andrei1058.bedwars.api.server.RestoreAdapter;
import com.andrei1058.bedwars.api.server.ServerType;
import com.andrei1058.bedwars.api.util.FileUtil;
import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Restore adapter for Advanced Slime Paper 4.x (Minecraft 26.x).
 * <p>
 * ASP 4 moved its API into the server ({@code com.infernalsuite.asp.api}) and no longer offers
 * named loaders, so arenas are read from {@code slime_worlds/<arena>.slime} with {@link SlimeFileLoader}.
 * Old SWM files (format v1-v9, pre-1.13 chunks) are upgraded by ASP when read.
 */
@SuppressWarnings({"CallToPrintStackTrace", "unused"})
public class AspAdapter extends RestoreAdapter {

    private final AdvancedSlimePaperAPI asp;
    private final SlimeLoader loader;
    private final BedWars api;

    public AspAdapter(Plugin plugin) {
        super(plugin);
        this.asp = AdvancedSlimePaperAPI.instance();
        this.loader = new SlimeFileLoader(new File(Bukkit.getWorldContainer(), "slime_worlds"));
        this.api = Objects.requireNonNull(Bukkit.getServer().getServicesManager().getRegistration(BedWars.class)).getProvider();
    }

    @Override
    public void onEnable(@NotNull IArena a) {
        if (api.getVersionSupport().getMainLevel().equalsIgnoreCase(a.getWorldName())) {
            if (!(api.getServerType() == ServerType.BUNGEE && api.getArenaUtil().getGamesBeforeRestart() == 1)) {
                FileUtil.setMainLevel("ignore_main_level", api.getVersionSupport());
                getOwner().getLogger().log(Level.SEVERE, "Cannot use level-name as arenas. Automatically creating a new void map for level-name.");
                getOwner().getLogger().log(Level.SEVERE, "The server is restarting...");
                Bukkit.getServer().spigot().restart();
                return;
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(getOwner(), () -> {
            if (Bukkit.getWorld(a.getWorldName()) != null) {
                Bukkit.getScheduler().runTask(getOwner(), () -> {
                    World w = Bukkit.getWorld(a.getWorldName());
                    a.init(w);
                });
                return;
            }

            String[] spawn = Objects.requireNonNull(a.getConfig().getString("waiting.Loc")).split(",");
            SlimePropertyMap spm = getCreateProperties(
                    (int) Double.parseDouble(spawn[0]),
                    (int) Double.parseDouble(spawn[1]),
                    (int) Double.parseDouble(spawn[2])
            );

            SlimeWorld world;
            try {
                // read-only template: the game copy is thrown away on restart
                world = asp.readWorld(loader, a.getArenaName(), true, spm);
                if (!world.getName().equals(a.getWorldName())) {
                    world = world.clone(a.getWorldName());
                }
            } catch (Exception ex) {
                api.getArenaUtil().removeFromEnableQueue(a);
                getOwner().getLogger().severe("Could not read slime world " + a.getArenaName() + ", removing it from the enable queue.");
                ex.printStackTrace();
                return;
            }

            SlimeWorld finalWorld = world;
            Bukkit.getScheduler().runTask(getOwner(), () -> {
                try {
                    // fires WorldLoadEvent, which initialises the arena
                    asp.loadWorld(finalWorld, true);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                if (Bukkit.getWorld(finalWorld.getName()) == null) {
                    api.getArenaUtil().removeFromEnableQueue(a);
                    getOwner().getLogger().severe("Something wrong... removing arena " + a.getArenaName() + " from queue.");
                }
            });
        });
    }

    @Override
    public void onRestart(IArena a) {
        if (api.getServerType() == ServerType.BUNGEE) {
            if (api.getArenaUtil().getGamesBeforeRestart() == 0) {
                if (api.getArenaUtil().getArenas().size() == 1 && api.getArenaUtil().getArenas().get(0).getStatus() == GameState.restarting) {
                    getOwner().getLogger().info("Dispatching command: " + api.getConfigs().getMainConfig().getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_RESTART_CMD));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), api.getConfigs().getMainConfig().getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_RESTART_CMD));
                }
            } else {
                if (api.getArenaUtil().getGamesBeforeRestart() != -1) {
                    api.getArenaUtil().setGamesBeforeRestart(api.getArenaUtil().getGamesBeforeRestart() - 1);
                }
                Bukkit.getScheduler().runTask(getOwner(), () -> {
                    Bukkit.unloadWorld(a.getWorldName(), false);
                    if (api.getArenaUtil().canAutoScale(a.getArenaName())) {
                        Bukkit.getScheduler().runTaskLater(getOwner(), () -> api.getArenaUtil().loadArena(a.getArenaName(), null), 80L);
                    }
                });
            }
        } else {
            Bukkit.getScheduler().runTask(getOwner(), () -> {
                Bukkit.unloadWorld(a.getWorldName(), false);
                Bukkit.getScheduler().runTaskLater(getOwner(), () -> api.getArenaUtil().loadArena(a.getArenaName(), null), 80L);
            });
        }
    }

    @Override
    public void onDisable(IArena a) {
        if (api.isShuttingDown()) {
            Bukkit.unloadWorld(a.getWorldName(), false);
            return;
        }
        Bukkit.getScheduler().runTask(getOwner(), () -> Bukkit.unloadWorld(a.getWorldName(), false));
    }

    @Override
    public void onSetupSessionStart(ISetupSession s) {
        Bukkit.getScheduler().runTaskAsynchronously(getOwner(), () -> {
            String[] spawn = new String[]{"0", "50", "0"};
            if (s.getConfig().getYml().getString("waiting.Loc") != null) {
                spawn = s.getConfig().getString("waiting.Loc").split(",");
            }
            SlimePropertyMap spm = getCreateProperties(
                    (int) Double.parseDouble(spawn[0]),
                    (int) Double.parseDouble(spawn[1]),
                    (int) Double.parseDouble(spawn[2])
            );

            try {
                if (Bukkit.getWorld(s.getWorldName()) != null) {
                    Bukkit.getScheduler().runTask(getOwner(), () -> Bukkit.unloadWorld(s.getWorldName(), false));
                }

                SlimeWorld world;
                if (loader.worldExists(s.getWorldName())) {
                    world = asp.readWorld(loader, s.getWorldName(), false, spm);
                    Bukkit.getScheduler().runTask(getOwner(), () -> s.getPlayer().sendMessage(ChatColor.GREEN + "Loading world from the slime_worlds container."));
                } else if (new File(Bukkit.getWorldContainer(), s.getWorldName() + "/level.dat").exists()) {
                    Bukkit.getScheduler().runTask(getOwner(), () -> s.getPlayer().sendMessage(ChatColor.GREEN + "Importing world to the slime_worlds container."));
                    world = asp.readVanillaWorld(new File(Bukkit.getWorldContainer(), s.getWorldName()), s.getWorldName().toLowerCase(), loader);
                    asp.saveWorld(world);
                } else {
                    Bukkit.getScheduler().runTask(getOwner(), () -> s.getPlayer().sendMessage(ChatColor.GREEN + "Creating a new void map."));
                    world = asp.createEmptyWorld(s.getWorldName(), false, spm, loader);
                    asp.saveWorld(world);
                }

                SlimeWorld sw = world;
                Bukkit.getScheduler().runTask(getOwner(), () -> {
                    asp.loadWorld(sw, true);
                    if (Bukkit.getWorld(sw.getName()) == null) {
                        return;
                    }
                    s.teleportPlayer();
                });
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(getOwner(), () -> s.getPlayer().sendMessage(ChatColor.RED + "An error occurred! Please check console."));
                ex.printStackTrace();
                Bukkit.getScheduler().runTask(getOwner(), s::close);
            }
        });
    }

    @Override
    public void onSetupSessionClose(ISetupSession s) {
        World world = Bukkit.getWorld(s.getWorldName());
        if (world != null) {
            world.save();
        }
        Bukkit.getScheduler().runTask(getOwner(), () -> Bukkit.unloadWorld(s.getWorldName(), true));
    }

    @Override
    public boolean isWorld(String name) {
        try {
            return loader.worldExists(name);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void deleteWorld(String name) {
        Bukkit.getScheduler().runTaskAsynchronously(getOwner(), () -> {
            try {
                loader.deleteWorld(name);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void cloneArena(String name1, String name2) {
        Bukkit.getScheduler().runTaskAsynchronously(getOwner(), () -> {
            try {
                SlimeWorld world = asp.readWorld(loader, name1, true, getCreateProperties(0, 118, 0));
                world.clone(name2, loader);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public List<String> getWorldsList() {
        try {
            return loader.listWorlds();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Convert vanilla arena worlds (a folder named after an arena without a slime file) to the slime format.
     */
    public void convertWorlds() {
        File dir = new File(getOwner().getDataFolder(), "/Arenas");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".yml")) {
                continue;
            }
            String name = file.getName().replace(".yml", "").toLowerCase();
            File worldFolder = new File(Bukkit.getWorldContainer(), file.getName().replace(".yml", ""));
            try {
                if (loader.worldExists(name) || !new File(worldFolder, "level.dat").isFile()) {
                    continue;
                }
                if (!file.getName().equals(name + ".yml") && !file.renameTo(new File(dir, name + ".yml"))) {
                    getOwner().getLogger().warning("Could not rename " + file.getName() + " to " + name + ".yml");
                }
                getOwner().getLogger().info("Converting " + name + " to the Slime format.");
                asp.saveWorld(asp.readVanillaWorld(worldFolder, name, loader));
            } catch (Exception e) {
                getOwner().getLogger().log(Level.WARNING, "Could not convert " + name + " to the Slime format.", e);
            }
        }
    }

    @Override
    public String getDisplayName() {
        return "Advanced Slime Paper 4 by InfernalSuite";
    }

    private @NotNull SlimePropertyMap getCreateProperties(int spawnX, int spawnY, int spawnZ) {
        SlimePropertyMap spm = new SlimePropertyMap();
        spm.setValue(SlimeProperties.WORLD_TYPE, "flat");
        spm.setValue(SlimeProperties.SPAWN_X, spawnX);
        spm.setValue(SlimeProperties.SPAWN_Y, spawnY);
        spm.setValue(SlimeProperties.SPAWN_Z, spawnZ);
        spm.setValue(SlimeProperties.ALLOW_ANIMALS, false);
        spm.setValue(SlimeProperties.ALLOW_MONSTERS, false);
        spm.setValue(SlimeProperties.DIFFICULTY, "easy");
        spm.setValue(SlimeProperties.PVP, true);
        return spm;
    }
}
