package de.z0rdak.yawp.handler.flags;

import de.z0rdak.yawp.api.events.flag.FlagCheckResult;
import de.z0rdak.yawp.config.server.FlagConfig;
import de.z0rdak.yawp.constants.Constants;
import de.z0rdak.yawp.core.decay.PendingRevert;
import de.z0rdak.yawp.core.decay.RevertQueue;
import de.z0rdak.yawp.core.flag.BlockException;
import de.z0rdak.yawp.core.flag.BlockExceptions;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.data.decay.RevertQueueData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class BlockDecayHandler {
    private static int tickCounter;

    private BlockDecayHandler() {
    }

    public static void onBreakAllowed(FlagCheckResult result, Level level, BlockPos pos, BlockState broken) {
        promise(result, level, pos, Blocks.AIR.defaultBlockState(), broken);
    }

    public static void onPlaceAllowed(FlagCheckResult result, Level level, BlockPos pos,
                                      BlockState placed, BlockState replaced) {
        promise(result, level, pos, placed, replaced);
    }

    private static void promise(FlagCheckResult result, Level level, BlockPos pos,
                                BlockState expected, BlockState revertTo) {
        if (level.isClientSide || !(level instanceof ServerLevel server)) {
            return;
        }
        int seconds = decaySecondsOf(result);
        if (seconds <= BlockException.NEVER) {
            return;
        }
        RevertQueueData data = RevertQueueData.get(server);
        data.queue().setCapacity(FlagConfig.getBlockRevertLimit());

        String dimension = server.dimension().location().toString();
        long due = server.getGameTime() + (long) seconds * 20L;
        PendingRevert revert = new PendingRevert(dimension, pos.asLong(),
                NbtUtils.writeBlockState(expected), NbtUtils.writeBlockState(revertTo), due);

        if (!data.promise(revert)) {
            Constants.LOGGER.warn("Block revert queue for {} is full ({} entries); '{}' at {} will not "
                            + "revert. Raise block_exception_revert_limit or let the queue drain.",
                    dimension, data.queue().size(), revertTo.getBlock().getName().getString(), pos);
        }
    }

    private static int decaySecondsOf(FlagCheckResult result) {
        IFlag flag = result.getFlag();
        BlockState state = result.getFlagCheck().getBlockState();
        if (flag == null || state == null) {
            return BlockException.NEVER;
        }
        BlockExceptions exceptions = flag.getBlockExceptions();
        return exceptions.isEmpty() ? BlockException.NEVER
                : exceptions.decaySecondsFor(state.getBlockHolder());
    }

    public static void onServerTick(MinecraftServer server) {
        int interval = FlagConfig.getDecayScanInterval();
        if (++tickCounter < interval) {
            return;
        }
        tickCounter = 0;
        for (ServerLevel level : server.getAllLevels()) {
            scan(level);
        }
    }

    private static void scan(ServerLevel level) {
        RevertQueueData data = RevertQueueData.get(level);
        RevertQueue queue = data.queue();
        if (queue.isEmpty()) {
            return;
        }
        List<PendingRevert> due = queue.due(level.getGameTime());
        if (due.isEmpty()) {
            return;
        }
        HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        for (PendingRevert revert : due) {
            BlockPos pos = BlockPos.of(revert.packedPos());
            if (!level.isLoaded(pos)) {
                continue;
            }
            revert(level, blocks, revert, pos);
            data.forget(revert.dimension(), revert.packedPos());
        }
    }

    private static boolean revert(ServerLevel level, HolderGetter<Block> blocks,
                                  PendingRevert revert, BlockPos pos) {
        BlockState expected = read(blocks, revert.expected());
        BlockState revertTo = read(blocks, revert.revertTo());
        if (expected == null || revertTo == null) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        boolean matches = expected.isAir()
                ? current.canBeReplaced()
                : current == expected;
        if (!matches) {
            return false;
        }
        level.setBlockAndUpdate(pos, revertTo);
        return true;
    }

    @Nullable
    private static BlockState read(HolderGetter<Block> blocks, CompoundTag tag) {
        if (tag.isEmpty()) {
            return null;
        }
        try {
            return NbtUtils.readBlockState(blocks, tag);
        } catch (RuntimeException e) {
            Constants.LOGGER.warn("Could not read a pending block revert's state: {}", e.getMessage());
            return null;
        }
    }
}
