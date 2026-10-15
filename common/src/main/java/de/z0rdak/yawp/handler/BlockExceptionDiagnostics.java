package de.z0rdak.yawp.handler;

import de.z0rdak.yawp.constants.Constants;
import de.z0rdak.yawp.core.flag.BlockSelector;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.region.IProtectedRegion;
import de.z0rdak.yawp.data.region.LevelRegionData;
import de.z0rdak.yawp.data.region.RegionDataManager;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class BlockExceptionDiagnostics {
    private static boolean serverStarted = false;

    private BlockExceptionDiagnostics() {
    }

    public static void onServerStarted() {
        serverStarted = true;
        logUnresolvedExceptions();
    }

    public static void onServerStopped() {
        serverStarted = false;
    }

    public static void onServerTagsReloaded() {
        if (serverStarted) {
            logUnresolvedExceptions();
        }
    }

    public static void logUnresolvedExceptions() {
        if (RegionDataManager.getTrackedLevelData() == null) {
            return;
        }
        List<IProtectedRegion> regions = new ArrayList<>();
        regions.add(RegionDataManager.getGlobalRegion());
        for (ResourceLocation level : RegionDataManager.getLevels()) {
            RegionDataManager.getLevelRegionData(level).ifPresent(data -> addRegions(regions, data));
        }
        int exceptionCount = 0;
        for (IProtectedRegion region : regions) {
            for (IFlag flag : region.getFlags().flags()) {
                for (BlockSelector selector : flag.getBlockExceptions().selectors()) {
                    exceptionCount++;
                    if (!matchesAnyBlock(selector)) {
                        Constants.LOGGER.warn("Block exception '{}' of flag '{}' in region '{}' ({}) refers to {} and does not match any block",
                                selector.serialize(), flag.getName(), region.getName(), region.getDim().location(),
                                selector.isTag() ? "a block tag which is unknown or empty" : "an unknown block");
                    }
                }
            }
        }
        if (exceptionCount > 0) {
            Constants.LOGGER.info("Checked {} block exception(s) of {} region(s)", exceptionCount, regions.size());
        }
    }

    private static void addRegions(List<IProtectedRegion> regions, LevelRegionData data) {
        regions.add(data.getDim());
        regions.addAll(data.getLocalList());
    }

    private static boolean matchesAnyBlock(BlockSelector selector) {
        if (selector.isTag()) {
            return selector.tagKey()
                    .flatMap(BuiltInRegistries.BLOCK::getTag)
                    .map(HolderSet::size)
                    .orElse(0) > 0;
        }
        return BuiltInRegistries.BLOCK.containsKey(selector.id());
    }
}
