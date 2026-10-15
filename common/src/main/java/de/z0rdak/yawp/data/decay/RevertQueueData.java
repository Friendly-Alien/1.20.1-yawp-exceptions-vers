package de.z0rdak.yawp.data.decay;

import de.z0rdak.yawp.constants.Constants;
import de.z0rdak.yawp.core.decay.PendingRevert;
import de.z0rdak.yawp.core.decay.RevertQueue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public final class RevertQueueData extends SavedData {
    private static final String NAME = Constants.MOD_ID + "_block_reverts";
    private static final String REVERTS = "Reverts";

    private final RevertQueue queue = new RevertQueue();

    public static RevertQueueData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RevertQueueData::load, RevertQueueData::new, NAME);
    }

    public RevertQueue queue() {
        return queue;
    }

    public boolean promise(PendingRevert revert) {
        if (!queue.promise(revert)) {
            return false;
        }
        setDirty();
        return true;
    }

    public void forget(String dimension, long packedPos) {
        if (queue.forget(dimension, packedPos) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (PendingRevert revert : queue.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dim", revert.dimension());
            entry.putLong("Pos", revert.packedPos());
            entry.put("Expected", revert.expected());
            entry.put("RevertTo", revert.revertTo());
            entry.putLong("Due", revert.dueTick());
            list.add(entry);
        }
        tag.put(REVERTS, list);
        return tag;
    }

    public static RevertQueueData load(CompoundTag tag) {
        RevertQueueData data = new RevertQueueData();
        ListTag list = tag.getList(REVERTS, Tag.TAG_COMPOUND);
        List<PendingRevert> loaded = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String dimension = entry.getString("Dim");
            if (dimension.isBlank()) {
                Constants.LOGGER.warn("Skipping a pending block revert with no dimension");
                continue;
            }
            loaded.add(new PendingRevert(dimension, entry.getLong("Pos"),
                    entry.getCompound("Expected"), entry.getCompound("RevertTo"), entry.getLong("Due")));
        }
        data.queue.loadAll(loaded);
        return data;
    }
}
