package com.andrei1058.bedwars.platform.master.platforms;

import com.andrei1058.bedwars.platform.common.LoaderPriority;
import com.andrei1058.bedwars.platform.common.ServerPlatform;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

@LoaderPriority(order = 100)
public class PaperLoader implements IPlatformLoader {

    private final HashMap<BuildInfo, Object> buildInfos = getBuildInfo();
    private final HashMap<String, String> versionMappings = getVersionMappings();

    private HashMap<String, String> getVersionMappings() {
        HashMap<String, String> versionMappings = new HashMap<>();

        versionMappings.put("1.21", "v1_21_R1");
        versionMappings.put("1.21.1", "v1_21_R1");
        versionMappings.put("1.21.3", "v1_21_R2");
        versionMappings.put("1.21.4", "v1_21_R3");
        versionMappings.put("1.21.11", "v1_21_R4");

        return versionMappings;
    }

    @Override
    public boolean isPlatformSupported() {
        var brandId = buildInfos.get(BuildInfo.BRAND_ID).toString();
        var brandName = buildInfos.get(BuildInfo.BRAND_NAME).toString();
        // forks such as AdvancedSlimePaper report their own brand but declare themselves Paper-compatible
        return (brandId.equals("papermc:paper") && brandName.equals("Paper"))
                || Boolean.TRUE.equals(buildInfos.get(BuildInfo.PAPER_COMPATIBLE));
    }

    /**
     * Minecraft 26.x (the first year-based versions) all share one unobfuscated platform module.
     */
    private @org.jetbrains.annotations.Nullable String getMapping(String version) {
        if (version.startsWith("26.")) {
            return "v26_R1";
        }
        return versionMappings.get(version);
    }

    @Override
    public boolean isServerVersionSupported() {
        var mcVersionId = buildInfos.get(BuildInfo.MINECRAFT_VERSION_ID).toString();
        var mcVersionName = buildInfos.get(BuildInfo.MINECRAFT_VERSION_NAME).toString();

        return getMapping(mcVersionId) != null && getMapping(mcVersionName) != null;
    }

    @Override
    public ServerPlatform getSupport() {
        String classPath = "com.andrei1058.bedwars.platform.paper."+getMapping(
                buildInfos.get(BuildInfo.MINECRAFT_VERSION_ID).toString()
        );

        try {
            return (ServerPlatform)Class.forName(classPath).getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException ignored) {
        }

        return null;
    }

    private @NotNull HashMap<BuildInfo, Object> getBuildInfo() {
        HashMap<BuildInfo, Object> buildInfo = new HashMap<>();
        for (BuildInfo build : BuildInfo.values()) {
            buildInfo.put(build, "");
        }

        try {
            var buildInfoHolder = Class.forName("io.papermc.paper.ServerBuildInfo");
            var buildMethod = buildInfoHolder.getMethod("buildInfo");
            var build = buildMethod.invoke(null);

            var buildIdMethod = buildInfoHolder.getMethod("brandId");
            var brandId = buildIdMethod.invoke(build);
            buildInfo.put(BuildInfo.BRAND_ID, brandId);

            var brandNameMethod = buildInfoHolder.getMethod("brandName");
            var brandName = brandNameMethod.invoke(build);
            buildInfo.put(BuildInfo.BRAND_NAME, brandName);

            var minecraftVersionMethod = buildInfoHolder.getMethod("minecraftVersionId");
            var minecraftVersion = minecraftVersionMethod.invoke(build);
            buildInfo.put(BuildInfo.MINECRAFT_VERSION_ID, minecraftVersion);

            var minecraftVersionNameMethod = buildInfoHolder.getMethod("minecraftVersionName");
            var minecraftVersionName = minecraftVersionNameMethod.invoke(build);
            buildInfo.put(BuildInfo.MINECRAFT_VERSION_NAME, minecraftVersionName);

            try {
                var paperId = buildInfoHolder.getField("BRAND_PAPER_ID").get(null);
                var compatible = buildInfoHolder.getMethod("isBrandCompatible", Class.forName("net.kyori.adventure.key.Key"))
                        .invoke(build, paperId);
                buildInfo.put(BuildInfo.PAPER_COMPATIBLE, compatible);
            } catch (ReflectiveOperationException ignored) {
            }

        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException ignored) {
        }

        return buildInfo;
    }
}
